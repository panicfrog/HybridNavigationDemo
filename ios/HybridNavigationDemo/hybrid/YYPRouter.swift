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

/// 路由栈的单个节点。每次 push 创建一个实例（含唯一 UUID）。
/// 通过 associated object 关联到对应的 UIViewController，currentRouteStack 派生自 nav.viewControllers。
public struct RouteEntry: Equatable {
  public let id: UUID
  public let routePath: String
  public let module: String
  public let paramsJSON: String?
  public weak var viewController: UIViewController?
}

private var routeEntryKey: UInt8 = 0

extension UIViewController {
  /// 关联到 VC 的 RouteEntry（YYPRouter.push 时分配，包含唯一 id）
  public var yyprouter_routeEntry: RouteEntry? {
    get { objc_getAssociatedObject(self, &routeEntryKey) as? RouteEntry }
    set { objc_setAssociatedObject(self, &routeEntryKey, newValue, .OBJC_ASSOCIATION_RETAIN_NONATOMIC) }
  }
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

  /// 互斥锁（递归，允许同线程重入）：所有访问内部状态的方法通过它序列化，保证路由栈一致性（任意线程调用都安全）。
  /// NSRecursiveLock 而非 NSLock，因为 public API 内部会调用 private helper（同样加锁），需要避免死锁。
  private let lock = NSRecursiveLock()

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

  // 统一操作队列（按 routePath 分组，FIFO 顺序执行；push 和 pop 入同一队列）
  private enum PendingOperation {
    case push(UIViewController, completion: (() -> Void)?)
    case pop(UIViewController, completion: (() -> Void)?)
  }
  private var pendingQueue: [String: [PendingOperation]] = [:]

  /// RN push 的上下文暂存：UUID → 单个 context（每次 push 一个 UUID，主键）
  /// REACT_NATIVE 路径 pushRoute 入口分配 UUID + 发 pushRoute 通知给 JS（含 routeID）+ 暂存 context；
  /// JS 用 routeID 渲染 HybridScreen → Fabric mount → onEvent ScreenDidMount 携带 routeID → 这里 removeValue(forKey: id) 取出 + 关联 RouteEntry
  private struct PendingPushContext {
    let id: UUID                // 业务路由 UUID（pushRoute 时分配，串联整个链路）
    let routePath: String       // 业务 routePath（B/C/D）
    let module: String
    let paramsJSON: String?
    let completion: (() -> Void)?
  }
  private var pendingPushContexts: [UUID: PendingPushContext] = [:]

  /// 当前已激活的 RouteEntry.id 集合。push 时加入；nav 转场完成（didShow）后用 nav.viewControllers 反向同步。
  /// 主要用于 onEvent ScreenDidUnmount 去重判断（如果 id 已不在 activeRouteIDs，说明已经被 pop，
  /// 后续 Fabric unmount 触发的 ScreenDidUnmount 是 pop 的副作用，不重复处理）。
  private var activeRouteIDs: Set<UUID> = []

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
    lock.lock(); defer { lock.unlock() }
    guard !key.isEmpty else { return }
    navControllers[key] = Weak(nav)
    if activeRoutePath == nil {
      activeRoutePath = key
    }
  }

  public func unregisterNavigationController(forKey key: String) {
    lock.lock(); defer { lock.unlock() }
    navControllers.removeValue(forKey: key)
    if activeRoutePath == key {
      activeRoutePath = nil
    }
  }

  public func navigationController(forKey key: String) -> UINavigationController? {
    lock.lock(); defer { lock.unlock() }
    guard !key.isEmpty else { return nil }
    return navControllers[key]?.value
  }

  public var activeNavigationController: UINavigationController? {
    lock.lock(); defer { lock.unlock() }
    guard let key = activeRoutePath else { return nil }
    return navControllers[key]?.value
  }

  public func setActiveNavigationControllerByKey(_ key: String) {
    lock.lock(); defer { lock.unlock() }
    activeRoutePath = key
  }

  // MARK: - 路由栈（派生）

  /// 当前路由栈，派生自 active nav 的 viewControllers（每个 VC 通过 associated object 拿 RouteEntry）
  public var currentRouteStack: [RouteEntry] {
    lock.lock(); defer { lock.unlock() }
    guard let nav = activeNavigationController else { return [] }
    return nav.viewControllers.compactMap { $0.yyprouter_routeEntry }
  }

  /// 给 root VC 注册 RouteEntry（root 不经过 pushRoute，但需要出现在 currentRouteStack 中）
  @discardableResult
  public func registerRouteEntry(
    for viewController: UIViewController,
    routePath: String,
    module: String,
    paramsJSON: String? = nil
  ) -> RouteEntry {
    lock.lock(); defer { lock.unlock() }
    let entry = RouteEntry(id: UUID(), routePath: routePath, module: module, paramsJSON: paramsJSON, viewController: viewController)
    viewController.yyprouter_routeEntry = entry
    activeRouteIDs.insert(entry.id)
    debugPrint("[YYPRouter] registerRouteEntry routePath=\(routePath) id=\(entry.id)")
    return entry
  }

  /// nav 转场完成（push/pop/back button/侧滑 都会触发）后由 YYPRouterNavigationController 调用，
  /// 用当前 nav.viewControllers 反向同步 activeRouteIDs（被 pop 的 VC id 自动移除）
  public func syncStackAfterTransition(_ currentVCs: [UIViewController]) {
    lock.lock(); defer { lock.unlock() }
    let currentIDs = Set(currentVCs.compactMap { $0.yyprouter_routeEntry?.id })
    let removed = activeRouteIDs.subtracting(currentIDs)
    if !removed.isEmpty {
      debugPrint("[YYPRouter] syncStackAfterTransition removed \(removed.count) id(s) (被 pop)")
    }
    activeRouteIDs = activeRouteIDs.intersection(currentIDs)
  }

  /// pending / 转场中（用于拦截系统 back button / 侧滑）
  public var hasPendingOperations: Bool {
    lock.lock(); defer { lock.unlock() }
    if pendingQueue.values.contains(where: { !$0.isEmpty }) { return true }
    if !pendingPushContexts.isEmpty { return true }
    if let nav = activeNavigationController, nav.transitionCoordinator != nil { return true }
    return false
  }

  // MARK: - 页面注册表

  /// 原生页面注册：泛型，从 Provider.Params.schema 自动拿 schema 和 parse
  public func registerScreen<Provider: YYPRouterScreenProvider>(
    _ routePath: String,
    module: String,
    providerClass: Provider.Type
  ) {
    lock.lock(); defer { lock.unlock() }
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
    lock.lock(); defer { lock.unlock() }
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
    lock.lock(); defer { lock.unlock() }
    registrations.removeValue(forKey: "\(module)/\(routePath)")
  }

  // MARK: - 统一 push 入口

  /// 字典版（兼容 JS Bridge / 旧调用方）
  public func pushRoute(
    _ routePath: String,
    module: String,
    params: [String: Any]?,
    animated: Bool = true,
    completion: (() -> Void)? = nil
  ) throws {
    lock.lock(); defer { lock.unlock() }
    let paramsJSON = try toJSONString(fromDictionary: params)
    try pushRouteInternal(routePath, module: module, paramsJSON: paramsJSON, animated: animated, completion: completion)
  }

  /// Encodable 版（原生强类型调用方，比如直接传 @Schemable struct + Encodable）
  public func pushRoute<E: Encodable>(
    _ routePath: String,
    module: String,
    params: E,
    animated: Bool = true,
    completion: (() -> Void)? = nil
  ) throws {
    lock.lock(); defer { lock.unlock() }
    let paramsJSON = try toJSONString(fromEncodable: params)
    try pushRouteInternal(routePath, module: module, paramsJSON: paramsJSON, animated: animated, completion: completion)
  }

  private func pushRouteInternal(
    _ routePath: String,
    module: String,
    paramsJSON: String?,
    animated: Bool,
    completion: (() -> Void)?
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

      // type-safe 创建 + 关联 RouteEntry
      let vc = try create(json)
      let entry = RouteEntry(id: UUID(), routePath: routePath, module: module, paramsJSON: json, viewController: vc)
      vc.yyprouter_routeEntry = entry
      activeRouteIDs.insert(entry.id)
      debugPrint("[YYPRouter] push (IOS_NATIVE) routePath=\(routePath) id=\(entry.id) → stack=\(currentRouteStack.map { $0.routePath })")

      nav.pushViewController(vc, animated: animated)
      if let completion = completion {
        invokeOnTransitionCompleted(nav: nav, completion: completion)
      }

    case .reactNative:
      // 分配 UUID 作为本次 push 的唯一标识，串联 native→JS→Fabric mount→onEvent 全链路
      let id = UUID()
      var userInfo: [String: Any] = [
        YYPRouterEventTypeUserInfoKey: YYPRouterEventPushRoute,
        YYPRouterRouteIDUserInfoKey: id.uuidString,
        YYPRouterRoutePathUserInfoKey: routePath,
        YYPRouterModuleUserInfoKey: module,
      ]
      if let json = paramsJSON {
        userInfo[YYPRouterParamsUserInfoKey] = json
      }
      debugPrint("[YYPRouter] push (REACT_NATIVE) routePath=\(routePath) id=\(id) → 发 pushRoute 通知给 JS（真正 push 等 Fabric mount）")
      NotificationCenter.default.post(
        name: NSNotification.Name(YYPRouterNotificationName),
        object: self,
        userInfo: userInfo)
      // context 暂存到 pendingPushContexts[UUID]，等 onEvent ScreenDidMount 携带同 routeID 时取出 + 关联 RouteEntry
      pendingPushContexts[id] = PendingPushContext(
        id: id, routePath: routePath, module: module, paramsJSON: paramsJSON, completion: completion)
    }
  }

  // MARK: - 统一 pop 入口（始终入队）

  public func popRoute(animated: Bool = true, completion: (() -> Void)? = nil) throws {
    lock.lock(); defer { lock.unlock() }
    guard let nav = activeNavigationController else {
      throw YYPRouterError.noActiveNavigationController
    }
    guard nav.viewControllers.count > 1 else {
      completion?()
      return
    }
    guard let routePath = activeRoutePath else {
      throw YYPRouterError.noActiveNavigationController
    }

    // 始终入队（统一管理 push/pop 顺序）
    let topVC = nav.viewControllers.last!
    let topRoutePath = topVC.yyprouter_routeEntry?.routePath ?? "?"
    let topID = topVC.yyprouter_routeEntry?.id ?? UUID()
    debugPrint("[YYPRouter] popRoute enqueued routePath=\(topRoutePath) id=\(topID)")
    pendingQueue[routePath, default: []].append(.pop(topVC, completion: completion))

    // 队列空闲则立即执行（不等 FabricTransactionDidMount）
    if nav.transitionCoordinator == nil {
      executePendingOperations(forRoutePath: routePath)
    }
  }

  /// 用 transitionCoordinator 拿转场动画完成时机（UIKit 标准做法）
  /// 无 coordinator（animated: false 等）→ fallback 到 DispatchQueue.main.async
  /// 完成后链式触发 executePendingOperations（保证队列连续执行）
  /// 注意：transitionCoordinator 的 completion 异步执行，调用方加锁在 invokeOnTransitionCompleted 返回时已经释放；
  /// completion 里调 executePendingOperations（自己加锁）重新持锁。
  private func invokeOnTransitionCompleted(nav: UINavigationController, completion: @escaping () -> Void) {
    let chainNext: () -> Void = { [weak self] in
      if let routePath = self?.activeRoutePath {
        self?.executePendingOperations(forRoutePath: routePath)
      }
    }
    if let coordinator = nav.transitionCoordinator {
      coordinator.animate(alongsideTransition: nil) { _ in
        completion()
        chainNext()
      }
    } else {
      DispatchQueue.main.async {
        completion()
        chainNext()
      }
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
    lock.lock(); defer { lock.unlock() }
    let info = n.userInfo ?? [:]
    let type = info[YYPRouterEventTypeUserInfoKey] as? String ?? ""

    // navKey 用 activeRoutePath（当前 demo 单 container 单 nav，足够）
    if type == YYPRouterEventFabricTransactionDidMount {
      if let navKey = activeRoutePath {
        executePendingOperations(forRoutePath: navKey)
      }
      return
    }
    if type == YYPRouterEventPushRoute {
      return  // 由 YYPNavigationModule 转发到 JS
    }

    guard let controller = info[YYPRouterControllerUserInfoKey] as? UIViewController,
          let navKey = activeRoutePath else {
      return
    }

    if type == YYPRouterEventScreenDidMount {
      // 用 routeID（业务 UUID）从 pendingPushContexts 取本次 push 的上下文（routePath/module/params/completion）
      guard let routeIDString = info[YYPRouterRouteIDUserInfoKey] as? String,
            !routeIDString.isEmpty,
            let routeID = UUID(uuidString: routeIDString) else {
        debugPrint("[YYPRouter] ScreenDidMount 缺少 routeID，忽略")
        return
      }
      guard let ctx = pendingPushContexts.removeValue(forKey: routeID) else {
        debugPrint("[YYPRouter] ScreenDidMount routeID=\(routeID) 在 pendingPushContexts 找不到（已 cancel 或重复），忽略")
        return
      }

      // 关联 RouteEntry（业务 routePath 来自 ctx，UUID 跟 push 时分配的相同）
      let entry = RouteEntry(id: routeID, routePath: ctx.routePath, module: ctx.module, paramsJSON: ctx.paramsJSON, viewController: controller)
      controller.yyprouter_routeEntry = entry
      activeRouteIDs.insert(routeID)
      debugPrint("[YYPRouter] ScreenDidMount → 关联 RouteEntry routePath=\(ctx.routePath) id=\(routeID)")

      pendingQueue[navKey, default: []].append(.push(controller, completion: ctx.completion))
    } else if type == YYPRouterEventScreenDidUnmount {
      // 用 RouteEntry.id 去重：如果 id 已不在 activeRouteIDs（说明已被 nav pop，syncStackAfterTransition 移除过），
      // 则这是 pop 的副作用 Fabric unmount 通知，忽略不处理
      let routePathOfController = controller.yyprouter_routeEntry?.routePath ?? "?"
      let idOfController = controller.yyprouter_routeEntry?.id
      if let id = idOfController, !activeRouteIDs.contains(id) {
        debugPrint("[YYPRouter] ScreenDidUnmount routePath=\(routePathOfController) id=\(id) 命中去重（已 pop），忽略")
        return
      }
      // 否则 activeRouteIDs 仍含该 id（说明 onEvent 比同步先到），手动移除避免后续重复
      if let id = idOfController {
        activeRouteIDs.remove(id)
        debugPrint("[YYPRouter] ScreenDidUnmount routePath=\(routePathOfController) id=\(id) 正常处理（activeRouteIDs 移除）")
      }
      // 不再加 PendingOperation.pop（系统 pop 由 UIKit 完成或 YYPRouter.popRoute 入队处理）
    }
  }

  private func executePendingOperations(forRoutePath routePath: String) {
    lock.lock(); defer { lock.unlock() }
    guard let nav = navigationController(forKey: routePath) else {
      debugPrint("[YYPRouter] WARNING: no navController for routePath=\(routePath), skip pending")
      pendingQueue.removeValue(forKey: routePath)
      return
    }

    // FIFO：按入队顺序一个一个执行（push/pop 混合，统一顺序）
    let operations = pendingQueue[routePath] ?? []
    for op in operations {
      switch op {
      case .push(let controller, let completion):
        let rp = controller.yyprouter_routeEntry?.routePath ?? "?"
        let id = controller.yyprouter_routeEntry?.id ?? UUID()
        debugPrint("[YYPRouter] executePendingOperations push routePath=\(rp) id=\(id) → stack=\(currentRouteStack.map { $0.routePath })")
        nav.pushViewController(controller, animated: true)
        if let completion = completion {
          invokeOnTransitionCompleted(nav: nav, completion: completion)
        }
      case .pop(let controller, let completion):
        if nav.topViewController === controller {
          let rp = controller.yyprouter_routeEntry?.routePath ?? "?"
          let id = controller.yyprouter_routeEntry?.id ?? UUID()
          debugPrint("[YYPRouter] executePendingOperations pop routePath=\(rp) id=\(id)")
          nav.popViewController(animated: true)
          if let completion = completion {
            invokeOnTransitionCompleted(nav: nav, completion: completion)
          }
        }
      }
    }
    pendingQueue.removeValue(forKey: routePath)
  }
}
