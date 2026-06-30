#import "HybridContainerView.h"
#import "HybridScreenController.h"
#import "YYPRouterConstants.h"

#import <React/RCTConversions.h>
#import <React/RCTMountingTransactionObserving.h>
#import <react/renderer/components/hybridnavigationdemo/ComponentDescriptors.h>
#import <react/renderer/components/hybridnavigationdemo/Props.h>
#import <react/renderer/components/hybridnavigationdemo/RCTComponentViewHelpers.h>

namespace react = facebook::react;

@interface HybridContainerView () <RCTMountingTransactionObserving>
@end

@implementation HybridContainerView

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
  }
  return self;
}

#pragma mark - RCTComponentViewProtocol

- (void)updateProps:(const react::Props::Shared &)props
           oldProps:(const react::Props::Shared &)oldProps
{
  const auto &newP = *std::static_pointer_cast<const react::HybridContainerProps>(props);
  const auto &oldP = *std::static_pointer_cast<const react::HybridContainerProps>(_props);

  if (oldP.routePath != newP.routePath) {
    _routePath = RCTNSStringFromStringNilIfEmpty(newP.routePath);
  }

  [super updateProps:props oldProps:oldProps];
}

#pragma mark - 发送 router 事件

- (void)postRouterEvent:(NSString *)type controller:(nullable UIViewController *)controller
{
  NSMutableDictionary *userInfo = [NSMutableDictionary dictionary];
  userInfo[YYPRouterEventTypeUserInfoKey] = type;
  userInfo[YYPRouterRoutePathUserInfoKey] = _routePath ?: @"";
  if (controller != nil) {
    userInfo[YYPRouterControllerUserInfoKey] = controller;
    // 把 activityMode / isNativelyDismissed 作为 primitive 放进 userInfo，
    // 避免 YYPRouter.swift 反向依赖 HybridScreenComponentView（Fabric 类型，含 C++）
    HybridScreenComponentView *screen = (HybridScreenComponentView *)controller.view;
    userInfo[YYPRouterActivityModeUserInfoKey] = @(screen.activityMode);
    userInfo[YYPRouterIsNativelyDismissedUserInfoKey] = @(screen.isNativelyDismissed);
  }
  [[NSNotificationCenter defaultCenter] postNotificationName:YYPRouterNotificationName
                                                      object:self
                                                    userInfo:userInfo];
}

#pragma mark - RCTComponentViewProtocol — 子视图 mount/unmount

- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childView
                          index:(NSInteger)index
{
  RCTAssert([childView isKindOfClass:HybridScreenComponentView.class],
            @"[HybridNav] HybridContainerView only accepts HybridScreenComponentView children");

  HybridScreenComponentView *screen = (HybridScreenComponentView *)childView;
  screen.container = self;
  [self postRouterEvent:YYPRouterEventScreenDidMount controller:screen.controller];
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childView
                            index:(NSInteger)index
{
  HybridScreenComponentView *screen = (HybridScreenComponentView *)childView;
  screen.container = nil;
  [self postRouterEvent:YYPRouterEventScreenDidUnmount controller:screen.controller];
}

#pragma mark - Communication from HybridScreen

- (void)screenChangedActivityMode:(nonnull HybridScreenComponentView *)screen
{
  [self postRouterEvent:YYPRouterEventScreenActivityModeDidChange controller:screen.controller];
}

#pragma mark - RCTMountingTransactionObserving — 时序控制核心

- (void)mountingTransactionWillMount:(const facebook::react::MountingTransaction &)transaction
                 withSurfaceTelemetry:(const facebook::react::SurfaceTelemetry &)telemetry
{
  // 预处理（暂不需要）
}

- (void)mountingTransactionDidMount:(const facebook::react::MountingTransaction &)transaction
                withSurfaceTelemetry:(const facebook::react::SurfaceTelemetry &)telemetry
{
  // Fabric 渲染树提交完成，通知 router 可以 flush pending push/pop
  [self postRouterEvent:YYPRouterEventFabricTransactionDidMount controller:nil];
}

#pragma mark - Codegen

+ (react::ComponentDescriptorProvider)componentDescriptorProvider
{
  return react::concreteComponentDescriptorProvider<react::HybridContainerComponentDescriptor>();
}

+ (BOOL)shouldBeRecycled
{
  return NO;
}

#ifdef RCT_DYNAMIC_FRAMEWORKS
+ (void)load
{
  [super load];
}
#endif

@end

Class<RCTComponentViewProtocol> HybridContainerCls(void)
{
  return HybridContainerView.class;
}
