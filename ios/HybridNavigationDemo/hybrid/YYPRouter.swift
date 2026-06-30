import Foundation
import UIKit
import JSONSchema
import JSONSchemaBuilder

public enum YYPRouterPlatform {
  case iosNative
  case reactNative
}

public enum YYPRouterError: Error {
  /// 找不到 routePath/module 对应的注册项
  case noRegistration(routePath: String, module: String)
  /// 没有 active navController（push 无处可去）
  case noActiveNavigationController
  /// IOS_NATIVE 注册时没有 createNativeViewController factory（数据异常，理论上不该发生）
  case missingNativeFactory(routePath: String, module: String)
  /// 字符串版 registerScreen 构建 Schema 失败
  case schemaBuildFailed(schemaJSON: String, underlying: Error)
  /// [String: Any] / Encodable → JSON 字符串序列化失败
  case paramsSerializationFailed(underlying: Error)
  /// swift-json-schema Schema.validate 返回 isValid=false
  case paramsValidationFailed(errors: [ValidationError])
  /// Schema.validate throws（schema 本身有问题）
  case paramsValidationThrew(underlying: Error)
  /// Schemable.schema.parse 返回 .invalid（params 不符合 schema）
  case paramsParseFailed(issues: [ParseIssue])
  /// Schemable.schema.parse throws（JSON decode 失败）
  case paramsParseThrew(underlying: Error)
}

/// 原生页面 conform 这个 protocol。
/// associatedtype Params 必须是 Schemable（通常用 @Schemable struct 自动生成），YYPRouter 用它的 schema 验证 + 解析 params。
/// VC 的 router_createViewController 直接拿 strongly-typed Params 实例，不做手动字典操作。
public protocol YYPRouterScreenProvider {
  associatedtype Params: Schemable where Params.Schema.Output == Params
  static func router_createViewController(withParams params: Params) -> UIViewController
}

public final class YYPRouter {

  public static let shared = YYPRouter()

  // 字符串常量定义在 YYPRouterConstants.h/.m（Swift/ObjC 共用，避免 ObjC++ 反向依赖 Swift header）

  private struct Registration {
    let routePath: String
    let module: String
    let platform: YYPRouterPlatform
    let schema: Schema?
    /// IOS_NATIVE 用：接收 params JSON 字符串，返回 VC（throws 让 parse 错误冒泡）
    let createNativeViewController: ((String) throws -> UIViewController)?
  }

  // weak wrapper（替代 NSMapTable）
  private final class Weak<T: AnyObject> {
    weak var value: T?
    init(_ value: T) { self.value = value }
  }

  private var navControllers: [String: Weak<UINavigationController>] = [:]
  private var activeRoutePath: String?
  private var registrations: [String: Registration] = [:]
  private var eventObserver: NSObjectProtocol?

  // 统一操作队列（按 routePath 分组，FIFO 顺序执行；push 和 pop 入同一队列，按入队顺序一个一个跑）
  private enum PendingOperation {
    case push(UIViewController)
    case pop(UIViewController)
  }
  private var pendingQueue: [String: [PendingOperation]] = [:]

  private init() {
    eventObserver = NotificationCenter.default.addObserver(
      forName: NSNotification.Name(YYPRouterNotificationName),
      object: nil,
      queue: nil
    ) { [weak self] notification in
      self?.onEvent(notification)
    }
  }

  deinit {
    if let obs = eventObserver {
      NotificationCenter.default.removeObserver(obs)
    }
  }

  // MARK: - navController 注册表

  public func registerNavigationController(_ nav: UINavigationController, forKey key: String) {
    guard !key.isEmpty else { return }
    navControllers[key] = Weak(nav)
    if activeRoutePath == nil {
      activeRoutePath = key
    }
  }

  public func unregisterNavigationController(forKey key: String) {
    navControllers.removeValue(forKey: key)
    if activeRoutePath == key {
      activeRoutePath = nil
    }
  }

  public func navigationController(forKey key: String) -> UINavigationController? {
    guard !key.isEmpty else { return nil }
    return navControllers[key]?.value
  }

  public var activeNavigationController: UINavigationController? {
    guard let key = activeRoutePath else { return nil }
    return navControllers[key]?.value
  }

  public func setActiveNavigationControllerByKey(_ key: String) {
    activeRoutePath = key
  }

  // MARK: - 页面注册表

  /// 原生页面注册：泛型，从 Provider.Params.schema 自动拿 schema 和 parse
  public func registerScreen<Provider: YYPRouterScreenProvider>(
    _ routePath: String,
    module: String,
    providerClass: Provider.Type
  ) {
    guard !routePath.isEmpty, !module.isEmpty else { return }

    let schema = Provider.Params.schema.definition()
    let create: (String) throws -> UIViewController = { json in
      let parsed: Parsed<Provider.Params, ParseIssue>
      do {
        parsed = try Provider.Params.schema.parse(instance: json)
      } catch {
        throw YYPRouterError.paramsParseThrew(underlying: error)
      }
      guard case .valid(let params) = parsed else {
        throw YYPRouterError.paramsParseFailed(issues: parsed.errors ?? [])
      }
      return Provider.router_createViewController(withParams: params)
    }

    registrations["\(module)/\(routePath)"] = Registration(
      routePath: routePath,
      module: module,
      platform: .iosNative,
      schema: schema,
      createNativeViewController: create)
  }

  /// RN 页面注册：从 JS 上来的 JSON Schema 字符串构建（throws：schema 构建可能失败）
  public func registerScreen(
    _ routePath: String,
    module: String,
    platform: YYPRouterPlatform = .reactNative,
    schemaJSON: String?
  ) throws {
    guard !routePath.isEmpty, !module.isEmpty else { return }

    let schema: Schema?
    if let jsonString = schemaJSON, !jsonString.isEmpty {
      do {
        schema = try Schema(instance: jsonString)
      } catch {
        throw YYPRouterError.schemaBuildFailed(schemaJSON: jsonString, underlying: error)
      }
    } else {
      schema = nil
    }

    registrations["\(module)/\(routePath)"] = Registration(
      routePath: routePath,
      module: module,
      platform: platform,
      schema: schema,
      createNativeViewController: nil)
  }

  public func unregisterScreen(_ routePath: String, module: String) {
    registrations.removeValue(forKey: "\(module)/\(routePath)")
  }

  // MARK: - 统一 push 入口

  /// 字典版（兼容 JS Bridge / 旧调用方）
  public func pushRoute(
    _ routePath: String,
    module: String,
    params: [String: Any]?
  ) throws {
    let paramsJSON = try toJSONString(fromDictionary: params)
    try pushRouteInternal(routePath, module: module, paramsJSON: paramsJSON)
  }

  /// Encodable 版（原生强类型调用方，比如直接传 @Schemable struct + Encodable）
  public func pushRoute<E: Encodable>(
    _ routePath: String,
    module: String,
    params: E
  ) throws {
    let paramsJSON = try toJSONString(fromEncodable: params)
    try pushRouteInternal(routePath, module: module, paramsJSON: paramsJSON)
  }

  private func pushRouteInternal(
    _ routePath: String,
    module: String,
    paramsJSON: String?
  ) throws {
    guard let reg = registrations["\(module)/\(routePath)"] else {
      throw YYPRouterError.noRegistration(routePath: routePath, module: module)
    }

    switch reg.platform {
    case .iosNative:
      guard let nav = activeNavigationController else {
        throw YYPRouterError.noActiveNavigationController
      }
      guard let create = reg.createNativeViewController else {
        throw YYPRouterError.missingNativeFactory(routePath: routePath, module: module)
      }

      let json = paramsJSON ?? "{}"

      // Schema 验证（若有）
      if let schema = reg.schema {
        let result: ValidationResult
        do {
          result = try schema.validate(instance: json)
        } catch {
          throw YYPRouterError.paramsValidationThrew(underlying: error)
        }
        if !result.isValid {
          throw YYPRouterError.paramsValidationFailed(errors: result.errors ?? [])
        }
      }

      // type-safe 创建（factory throws 让 parse 错误冒泡）
      let vc = try create(json)
      nav.pushViewController(vc, animated: true)

    case .reactNative:
      var userInfo: [String: Any] = [
        YYPRouterEventTypeUserInfoKey: YYPRouterEventPushRoute,
        YYPRouterRoutePathUserInfoKey: routePath,
        YYPRouterModuleUserInfoKey: module,
      ]
      if let json = paramsJSON {
        userInfo[YYPRouterParamsUserInfoKey] = json
      }
      NotificationCenter.default.post(
        name: NSNotification.Name(YYPRouterNotificationName),
        object: self,
        userInfo: userInfo)
    }
  }

  private func toJSONString(fromDictionary dict: [String: Any]?) throws -> String? {
    guard let dict = dict, !dict.isEmpty else { return nil }
    guard JSONSerialization.isValidJSONObject(dict) else {
      throw YYPRouterError.paramsSerializationFailed(
        underlying: NSError(
          domain: "YYPRouter", code: -1,
          userInfo: [NSLocalizedDescriptionKey: "Dictionary is not a valid JSON object"]))
    }
    do {
      let data = try JSONSerialization.data(withJSONObject: dict)
      return String(data: data, encoding: .utf8)
    } catch {
      throw YYPRouterError.paramsSerializationFailed(underlying: error)
    }
  }

  private func toJSONString<E: Encodable>(fromEncodable value: E) throws -> String? {
    do {
      let data = try JSONEncoder().encode(value)
      return String(data: data, encoding: .utf8)
    } catch {
      throw YYPRouterError.paramsSerializationFailed(underlying: error)
    }
  }

  // MARK: - 通知分发（HybridContainer 事件）

  private func onEvent(_ n: Notification) {
    let info = n.userInfo ?? [:]
    let type = info[YYPRouterEventTypeUserInfoKey] as? String ?? ""
    let routePath = info[YYPRouterRoutePathUserInfoKey] as? String ?? ""

    if type == YYPRouterEventFabricTransactionDidMount {
      if !routePath.isEmpty {
        executePendingOperations(forRoutePath: routePath)
      }
      return
    }
    if type == YYPRouterEventPushRoute {
      return  // 由 YYPNavigationModule 转发到 JS
    }

    guard let controller = info[YYPRouterControllerUserInfoKey] as? UIViewController,
          !routePath.isEmpty else {
      return
    }

    // activityMode / isNativelyDismissed 由 HybridContainerView.mm 直接放进 userInfo
    //（避免 Swift 依赖 Fabric 类型）
    let activityMode = (info[YYPRouterActivityModeUserInfoKey] as? NSNumber)?.intValue ?? 0
    let isNativelyDismissed = (info[YYPRouterIsNativelyDismissedUserInfoKey] as? NSNumber)?.boolValue ?? false
    let isAttached = (activityMode == 1)

    if type == YYPRouterEventScreenDidMount || type == YYPRouterEventScreenActivityModeDidChange {
      if isAttached {
        pendingQueue[routePath, default: []].append(.push(controller))
      }
    } else if type == YYPRouterEventScreenDidUnmount {
      if isAttached && !isNativelyDismissed {
        pendingQueue[routePath, default: []].append(.pop(controller))
      }
    }
  }

  private func executePendingOperations(forRoutePath routePath: String) {
    guard let nav = navigationController(forKey: routePath) else {
      debugPrint("[YYPRouter] WARNING: no navController for routePath=\(routePath), skip pending")
      pendingQueue.removeValue(forKey: routePath)
      return
    }

    // FIFO：按入队顺序一个一个执行（push/pop 混合，不再先 pop 再 push）
    let operations = pendingQueue[routePath] ?? []
    for op in operations {
      switch op {
      case .push(let controller):
        nav.pushViewController(controller, animated: true)
      case .pop(let controller):
        if nav.topViewController === controller {
          nav.popViewController(animated: true)
        }
      }
    }
    pendingQueue.removeValue(forKey: routePath)
  }
}
