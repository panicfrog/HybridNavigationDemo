#pragma once

#import <React/RCTViewComponentView.h>

NS_ASSUME_NONNULL_BEGIN

@class HybridScreenController;
@class HybridContainerView;

@interface HybridScreenComponentView : RCTViewComponentView

@property (nonatomic, weak, nullable) HybridContainerView *container;
@property (nonatomic, strong, readonly) HybridScreenController *controller;
@property (nonatomic, copy, nullable) NSString *routeID;

- (void)emitWillAppear;
- (void)emitDidAppear;
- (void)emitWillDisappear;
- (void)emitDidDisappear;
- (void)emitNativeDismiss;

@end

NS_ASSUME_NONNULL_END
