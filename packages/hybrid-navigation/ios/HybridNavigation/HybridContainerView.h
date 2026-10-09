#pragma once

#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>
#import "HybridScreenComponentView.h"

NS_ASSUME_NONNULL_BEGIN

/// HybridContainer：透明容器，承担 Fabric mount 观察职责，把所有挂载状态通过 NSNotification
/// 通知出去（发送方），路由执行由 YYPRouter 完成。
@interface HybridContainerView : RCTViewComponentView

@end

NS_ASSUME_NONNULL_END
