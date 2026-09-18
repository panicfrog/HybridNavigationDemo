//
//  YYPNavigationModule.swift
//  HybridNavigationDemo
//
//  Created by yeyongping on 2026/6/29.
//

import Foundation
import React

fileprivate let kPushRNEvent = "YYPNavPushRN"

@objc(YYPNavigationModule)
public class YYPNavigationModule: RCTEventEmitter {
  public override func supportedEvents() -> [String]! {
    return [kPushRNEvent]
  }
  
  public override func startObserving() {
    NotificationCenter.default.addObserver(self,
                                           selector: #selector(onRouterEvent(notification:)),
                                           name: NSNotification.Name(YYPRouterNotificationName),
                                           object: nil)
  }
  
  public override func stopObserving() {
    NotificationCenter.default.removeObserver(self)
  }
  
//  - (void)onRouterEvent:(NSNotification *)n
//  {
//    NSString *type = n.userInfo[YYPRouterEventTypeUserInfoKey];
//    // 只转发 pushRoute 事件给 JS（其他事件由 YYPRouter 内部消化）
//    if (![type isEqualToString:YYPRouterEventPushRoute]) return;
//    [self sendEventWithName:kPushRNEvent body:n.userInfo];
//  }
  
  @objc func onRouterEvent(notification: Notification) {
    
    guard let type = notification.userInfo?[YYPRouterEventTypeUserInfoKey] as? String,
          type == YYPRouterEventPushRoute else { return }
    sendEvent(withName: kPushRNEvent, body: notification.userInfo)
  }
  
  public override class func requiresMainQueueSetup() -> Bool {
    true
  }
    
  // MARK: JS -> 原生
  @objc(registerScreen:module:schemaJSON:title:)
  public func registerScreen(with routePath: String, module: String, schemaJson: String, title: String) {
    do {
      try YYPRouter.shared.registerScreen(routePath,
                                          module: module,
                                          platform: .reactNative,
                                          schemaJSON: schemaJson.isEmpty ? nil : schemaJson,
                                          title: title.isEmpty ? nil : title)
    } catch {
      debugPrint("[YYPNavigationModule] registerScreen \(routePath) failed: \(error)")
    }
  }

  @objc(pushRoute:module:paramsJSON:resolver:rejecter:)
  public func pushRoute(with routePath: String,
                      module: String,
                      paramsJson: String,
                      resolver resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard let jsonData = paramsJson.data(using: .utf8) else {
      reject("invalid_utf8", "paramsJSON is not valid UTF8", nil)
      return
    }
    let params: [String: Any]?
    do {
      params = try JSONSerialization.jsonObject(with: jsonData) as? [String: Any]
    } catch {
      reject("params_decode_failed", "Failed to decode paramsJSON: \(error.localizedDescription)", error)
      return
    }
    DispatchQueue.main.async {
      do {
        try YYPRouter.shared.pushRoute(routePath, module: module, params: params) {
          resolve(nil)
        }
      } catch {
        reject("push_failed", error.localizedDescription, error)
      }
    }
  }

  @objc(pop:rejecter:)
  public func pop(resolver resolve: @escaping RCTPromiseResolveBlock,
                rejecter reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.main.async {
      do {
        try YYPRouter.shared.popRoute {
          resolve(nil)
        }
      } catch {
        reject("pop_failed", error.localizedDescription, error)
      }
    }
  }
}
