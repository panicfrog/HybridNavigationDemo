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
  @objc(registerScreen:module:schemaJSON:)
  public func registerScreen(with routePath: String, module: String, schemaJson: String) {
    do {
      try YYPRouter.shared.registerScreen(routePath,
                                          module: module,
                                          platform: .reactNative,
                                          schemaJSON: schemaJson.isEmpty ? nil : schemaJson)
    } catch {
      debugPrint("[YYPNavigationModule] registerScreen \(routePath) failed: \(error)")
    }
  }

  @objc(pushRoute:module:paramsJSON:)
  public func pushRoute(with routePath: String,
                      module: String,
                      paramsJson: String) {
    guard let jsonData = paramsJson.data(using: .utf8) else {
      debugPrint("[YYPNavigationModule] pushRoute \(routePath): invalid UTF8 paramsJSON")
      return
    }
    let params: [String: Any]?
    do {
      params = try JSONSerialization.jsonObject(with: jsonData) as? [String: Any]
    } catch {
      debugPrint("[YYPNavigationModule] pushRoute \(routePath): paramsJSON decode failed: \(error)")
      return
    }
    DispatchQueue.main.async {
      do {
        try YYPRouter.shared.pushRoute(routePath, module: module, params: params)
      } catch {
        debugPrint("[YYPNavigationModule] pushRoute \(routePath) failed: \(error)")
      }
    }
  }
  
  @objc
  public func pop() {
    DispatchQueue.main.async {
      YYPRouter.shared.activeNavigationController?.popViewController(animated: true)
    }
  }
}
