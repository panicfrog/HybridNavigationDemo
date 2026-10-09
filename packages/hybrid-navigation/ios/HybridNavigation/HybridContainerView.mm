#import "HybridContainerView.h"
#import "HybridScreenController.h"
#import "YYPRouterConstants.h"

#import <React/RCTConversions.h>
#import <React/RCTMountingTransactionObserving.h>
#import <react/renderer/components/hybridnavigation/ComponentDescriptors.h>
#import <react/renderer/components/hybridnavigation/Props.h>
#import <react/renderer/components/hybridnavigation/RCTComponentViewHelpers.h>

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
  // HybridContainerProps 已无可读字段（routePath prop 已删），保留方法供未来扩展
  [super updateProps:props oldProps:oldProps];
}

#pragma mark - 发送 router 事件

- (void)postRouterEvent:(NSString *)type
              controller:(nullable UIViewController *)controller
                 routeID:(nullable NSString *)routeID
               routePath:(nullable NSString *)routePath
{
  NSMutableDictionary *userInfo = [NSMutableDictionary dictionary];
  userInfo[YYPRouterEventTypeUserInfoKey] = type;
  if (controller != nil) {
    userInfo[YYPRouterControllerUserInfoKey] = controller;
  }
  if (routeID != nil && routeID.length > 0) {
    userInfo[YYPRouterRouteIDUserInfoKey] = routeID;
  }
  if (routePath != nil && routePath.length > 0) {
    userInfo[YYPRouterRoutePathUserInfoKey] = routePath;
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
  [self postRouterEvent:YYPRouterEventScreenDidMount
             controller:screen.controller
                routeID:screen.routeID
             routePath:screen.routePath];
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childView
                            index:(NSInteger)index
{
  HybridScreenComponentView *screen = (HybridScreenComponentView *)childView;
  screen.container = nil;
  [self postRouterEvent:YYPRouterEventScreenDidUnmount
             controller:screen.controller
                routeID:screen.routeID
             routePath:screen.routePath];
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
  [self postRouterEvent:YYPRouterEventFabricTransactionDidMount controller:nil routeID:nil routePath:nil];
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
