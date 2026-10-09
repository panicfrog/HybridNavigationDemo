#import "HybridScreenComponentView.h"
#import "HybridContainerView.h"
#import "HybridScreenController.h"

#import <React/RCTConversions.h>
#import <React/RCTSurfaceTouchHandler.h>
#import <react/renderer/components/hybridnavigationdemo/ComponentDescriptors.h>
#import <react/renderer/components/hybridnavigationdemo/EventEmitters.h>
#import <react/renderer/components/hybridnavigationdemo/Props.h>
#import <react/renderer/components/hybridnavigationdemo/RCTComponentViewHelpers.h>

namespace react = facebook::react;

@implementation HybridScreenComponentView {
  RCTSurfaceTouchHandler *_touchHandler;
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    _routeID = nil;
    _controller = [[HybridScreenController alloc] initWithComponentView:self];
  }
  return self;
}

- (BOOL)isMountedUnderReactRoot
{
  for (UIView *parent = self.superview; parent != nil; parent = parent.superview) {
    if ([parent isKindOfClass:NSClassFromString(@"RCTRootComponentView")]) {
      return YES;
    }
  }
  return NO;
}

- (void)didMoveToWindow
{
  [super didMoveToWindow];
  if (self.window != nil && ![self isMountedUnderReactRoot]) {
    if (_touchHandler == nil) {
      _touchHandler = [RCTSurfaceTouchHandler new];
    }
    [_touchHandler attachToView:self];
  } else {
    [_touchHandler detachFromView:self];
  }
}

#pragma mark - RCTComponentViewProtocol

- (void)updateProps:(const react::Props::Shared &)props
           oldProps:(const react::Props::Shared &)oldProps
{
  const auto &newP = *std::static_pointer_cast<const react::HybridScreenProps>(props);
  const auto &oldP = *std::static_pointer_cast<const react::HybridScreenProps>(_props);

  if (oldP.routeID != newP.routeID) {
    _routeID = RCTNSStringFromStringNilIfEmpty(newP.routeID);
  }

  if (oldP.routePath != newP.routePath) {
    _routePath = RCTNSStringFromStringNilIfEmpty(newP.routePath);
  }

  [super updateProps:props oldProps:oldProps];
}

+ (react::ComponentDescriptorProvider)componentDescriptorProvider
{
  return react::concreteComponentDescriptorProvider<react::HybridScreenComponentDescriptor>();
}

+ (BOOL)shouldBeRecycled
{
  return NO;
}

#pragma mark - Events → 转发给 JS（payload 带 routeID 让 JS 用 UUID 区分实例）

- (void)emitWillAppear
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onWillAppear({.routeID = RCTStringFromNSString(_routeID ?: @"")});
  }
}

- (void)emitDidAppear
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onDidAppear({.routeID = RCTStringFromNSString(_routeID ?: @"")});
  }
}

- (void)emitWillDisappear
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onWillDisappear({.routeID = RCTStringFromNSString(_routeID ?: @"")});
  }
}

- (void)emitDidDisappear
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onDidDisappear({.routeID = RCTStringFromNSString(_routeID ?: @"")});
  }
}

- (void)emitNativeDismiss
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onNativeDismiss({.routeID = RCTStringFromNSString(_routeID ?: @"")});
  }
}

#pragma mark - Dynamic frameworks support

#ifdef RCT_DYNAMIC_FRAMEWORKS
+ (void)load
{
  [super load];
}
#endif

@end

Class<RCTComponentViewProtocol> HybridScreenCls(void)
{
  return HybridScreenComponentView.class;
}
