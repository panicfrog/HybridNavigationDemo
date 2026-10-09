#import "HybridScreenController.h"
#import "HybridScreenComponentView.h"

@implementation HybridScreenController {
  HybridScreenComponentView *_screenView;
}

- (instancetype)initWithComponentView:(HybridScreenComponentView *)componentView
{
  if (self = [super initWithNibName:nil bundle:nil]) {
    _screenView = componentView;
    // 核心：将 RN 渲染的 UIView 直接作为此 VC 的根视图
    self.view = componentView;
  }
  return self;
}

- (void)viewWillLayoutSubviews
{
  [super viewWillLayoutSubviews];
  // 确保 RN 视图跟随 UINavigationController 提供的 frame
  _screenView.frame = self.view.bounds;
}

#pragma mark - Lifecycle → 转发给 JS

- (void)viewWillAppear:(BOOL)animated
{
  [super viewWillAppear:animated];
  [_screenView emitWillAppear];
}

- (void)viewDidAppear:(BOOL)animated
{
  [super viewDidAppear:animated];
  [_screenView emitDidAppear];
}

- (void)viewWillDisappear:(BOOL)animated
{
  [super viewWillDisappear:animated];
  [_screenView emitWillDisappear];
}

- (void)viewDidDisappear:(BOOL)animated
{
  [super viewDidDisappear:animated];
  [_screenView emitDidDisappear];
}

- (void)didMoveToParentViewController:(nullable UIViewController *)parent
{
  [super didMoveToParentViewController:parent];
  if (parent == nil) {
    // VC 被 pop（原生手势或返回键），通知 JS 同步状态
    [_screenView emitNativeDismiss];
  }
}

@end
