import UIKit

/// 自定义 UINavigationController：
/// - 在 YYPRouter pending / 转场中拒绝系统 back button 和侧滑（避免跟队列冲突）
/// - 非 pending 状态让 UIKit 正常处理（保留侧滑跟手体验）
/// - 转场完成（didShow）时通知 YYPRouter 同步 activeRouteIDs，覆盖 push/pop/back button/侧滑 所有路径
public final class YYPRouterNavigationController: UINavigationController, UIGestureRecognizerDelegate {

  public override func viewDidLoad() {
    super.viewDidLoad()
    interactivePopGestureRecognizer?.delegate = self
    delegate = self
  }

  // MARK: - 侧滑拦截

  public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
    // pending 或转场中：拒绝（保护队列）
    if YYPRouter.shared.hasPendingOperations {
      debugPrint("[YYPRouterNav] 侧滑拒绝（pending）")
      return false
    }
    if transitionCoordinator != nil {
      debugPrint("[YYPRouterNav] 侧滑拒绝（转场中）")
      return false
    }
    // root 不能 pop
    return viewControllers.count > 1
  }

  // MARK: - back button 拦截（UINavigationBarDelegate 协议方法，UINavigationController 隐式 conform）

  public func navigationBar(_ navigationBar: UINavigationBar, shouldPop item: UINavigationItem) -> Bool {
    // pending 或转场中：拒绝默认 pop
    if YYPRouter.shared.hasPendingOperations {
      debugPrint("[YYPRouterNav] back button 拒绝（pending）")
      return false
    }
    if transitionCoordinator != nil {
      debugPrint("[YYPRouterNav] back button 拒绝（转场中）")
      return false
    }
    // 否则让 UIKit 正常 pop（didMoveToParentViewController → emitNativeDismiss → JS 自动同步）
    debugPrint("[YYPRouterNav] back button 允许默认 pop")
    return true
  }
}

extension YYPRouterNavigationController: UINavigationControllerDelegate {
  public func navigationController(_ navigationController: UINavigationController, didShow viewController: UIViewController, animated: Bool) {
    // 任何转场完成（push/pop/back button/侧滑）都触发，同步 activeRouteIDs
    let stack = navigationController.viewControllers.compactMap { $0.yyprouter_routeEntry?.routePath }
    debugPrint("[YYPRouterNav] didShow stack=\(stack)")
    YYPRouter.shared.syncStackAfterTransition(navigationController.viewControllers)
  }
}
