import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
  var window: UIWindow?

  fileprivate var reactNativeDelegate: ReactNativeDelegate?
  var reactNativeFactory: RCTReactNativeFactory?

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {

    // 1. 初始化 RN factory
    let rnDelegate = ReactNativeDelegate()
    let factory = RCTReactNativeFactory(delegate: rnDelegate)
    rnDelegate.dependencyProvider = RCTAppDependencyProvider()
    reactNativeDelegate = rnDelegate
    reactNativeFactory = factory

    // 2. 占位 rootVC(iOS 要求 window 必须有 rootVC,等会儿被 registerScreen(__index__) 替换)
    let placeholderVC = UIViewController()
    placeholderVC.view.backgroundColor = .white
    let placeholderNav = YYPRouterNavigationController(rootViewController: placeholderVC)
    YYPRouter.shared.registerNavigationController(placeholderNav, forKey: "default")
    YYPRouter.shared.setActiveNavigationControllerByKey("default")

    // 3. 设置 window,先挂占位 nav(makeKeyAndVisible 必须在 rootView addSubview 之前)
    let appWindow = UIWindow(frame: UIScreen.main.bounds)
    appWindow.rootViewController = placeholderNav
    appWindow.makeKeyAndVisible()
    window = appWindow

    // 4. 创建 RN RootView(透明 overlay,渲染 App.tsx 里的 HybridContainer)
    //    必须在 window 可见之后再添加,否则 RN surface 尺寸为 0
    let rootView = factory.rootViewFactory.view(
      withModuleName: "HybridNavigationDemo",
      initialProperties: nil
    )
    rootView.backgroundColor = UIColor.clear
    rootView.frame = appWindow.bounds
    rootView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    // 不拦截触摸:RN 页面 push 后有自己的 RCTSurfaceTouchHandler,
    // 原生页面的触摸不经过这个 overlay(它在 navController.view 下层)
    rootView.isUserInteractionEnabled = false
    window?.addSubview(rootView)

    // 5. attachWindow(必须在原生 Root 注册之前,让 attachRootController 能拿到 window)
    YYPRouter.shared.attachWindow(appWindow, rootView: rootView)

    // 6. 注册普通 push 页
    try! YYPRouter.shared.registerScreen("C", module: "default",
        providerClass: NativeViewControllerC.self)

    // 7. 注册原生 Root(立即换 window.rootViewController,不等 mount)
    //    try!:AppDelegate 是 process entry,配置错应该暴露而非静默
    try! YYPRouter.shared.registerScreen(
      YYPRouterRootIndexPath,
      module: "default",
      providerClass: NativeViewControllerA.self)

    return true
  }
}

fileprivate class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    bundleURL()
  }

  override func bundleURL() -> URL? {
#if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
  }
}
