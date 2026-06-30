#pragma once

#import <React/RCTViewComponentView.h>

NS_ASSUME_NONNULL_BEGIN

@class HybridScreenController;
@class HybridContainerView;

typedef NS_ENUM(int, HybridScreenActivityMode) {
  HybridScreenActivityModeDetached = 0,
  HybridScreenActivityModeAttached = 1,
};

@interface HybridScreenComponentView : RCTViewComponentView

@property (nonatomic, weak, nullable) HybridContainerView *container;
@property (nonatomic, strong, readonly) HybridScreenController *controller;
@property (nonatomic, strong, readonly, nullable) NSString *screenKey;
@property (nonatomic, readonly) HybridScreenActivityMode activityMode;
@property (nonatomic) BOOL isNativelyDismissed;

- (void)emitWillAppear;
- (void)emitDidAppear;
- (void)emitWillDisappear;
- (void)emitDidDisappear;
- (void)emitNativeDismiss;

@end

NS_ASSUME_NONNULL_END
