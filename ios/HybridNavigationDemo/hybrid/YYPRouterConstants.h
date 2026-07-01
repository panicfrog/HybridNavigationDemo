#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// YYPRouter 用到的所有字符串常量。Swift 和 ObjC/ObjC++ 共用，避免 ObjC++ 反向依赖 Swift header。
/// YYPRouter.swift 内部也通过 bridging header 引用这些常量（不再用 @objc static let）。

// 通知名（字符串值，名字故意不以 Notification 结尾，避免 Swift 自动当 NSNotification.Name 处理）
extern NSString *const YYPRouterNotificationName;

// 事件类型（userInfo[YYPRouterEventTypeUserInfoKey]）
extern NSString *const YYPRouterEventScreenDidMount;
extern NSString *const YYPRouterEventScreenDidUnmount;
extern NSString *const YYPRouterEventFabricTransactionDidMount;
extern NSString *const YYPRouterEventPushRoute;

// userInfo keys
extern NSString *const YYPRouterEventTypeUserInfoKey;
extern NSString *const YYPRouterRouteIDUserInfoKey;       // NSString (UUID uuidString)，跨 native↔JS 主键
extern NSString *const YYPRouterRoutePathUserInfoKey;
extern NSString *const YYPRouterModuleUserInfoKey;
extern NSString *const YYPRouterParamsUserInfoKey;
extern NSString *const YYPRouterControllerUserInfoKey;

NS_ASSUME_NONNULL_END
