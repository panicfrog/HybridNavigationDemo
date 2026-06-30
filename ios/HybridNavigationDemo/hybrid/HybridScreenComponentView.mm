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
  BOOL _hasUpdatedActivityMode;
  RCTSurfaceTouchHandler *_touchHandler;
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    [self initState];
  }
  return self;
}

- (void)initState
{
  _activityMode = HybridScreenActivityModeDetached;
  _hasUpdatedActivityMode = NO;
  _isNativelyDismissed = NO;
  _screenKey = nil;

  // 创建 UIViewController，将自身（UIView）作为其根视图
  _controller = [[HybridScreenController alloc] initWithComponentView:self];
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
  // 只有脱离 RN surface 树（被 push 到外部 navController）后才需要独立的 touch handler
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

  if (oldP.activityMode != newP.activityMode) {
    _activityMode = (HybridScreenActivityMode)newP.activityMode;
    _hasUpdatedActivityMode = YES;
  }

  if (oldP.screenKey != newP.screenKey) {
    _screenKey = RCTNSStringFromStringNilIfEmpty(newP.screenKey);
  }

  [super updateProps:props oldProps:oldProps];
}

- (void)finalizeUpdates:(RNComponentViewUpdateMask)updateMask
{
  if (_hasUpdatedActivityMode) {
    _hasUpdatedActivityMode = NO;
    // 通知容器，activityMode 发生了变化
    [self.container screenChangedActivityMode:self];
  }
  [super finalizeUpdates:updateMask];
}

+ (react::ComponentDescriptorProvider)componentDescriptorProvider
{
  return react::concreteComponentDescriptorProvider<react::HybridScreenComponentDescriptor>();
}

+ (BOOL)shouldBeRecycled
{
  return NO;
}

#pragma mark - Events → 转发给 JS

- (void)emitWillAppear
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onWillAppear({});
  }
}

- (void)emitDidAppear
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onDidAppear({});
  }
}

- (void)emitWillDisappear
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onWillDisappear({});
  }
}

- (void)emitDidDisappear
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onDidDisappear({});
  }
}

- (void)emitNativeDismiss
{
  if (_eventEmitter) {
    std::static_pointer_cast<const react::HybridScreenEventEmitter>(_eventEmitter)
        ->onNativeDismiss({});
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
