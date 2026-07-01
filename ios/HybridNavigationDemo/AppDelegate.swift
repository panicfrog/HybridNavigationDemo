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

    // 2. 注册需要被 push 的原生页面（root VC 不注册，直接 init 即可）
    YYPRouter.shared.registerScreen("C", module: "default",
        providerClass: NativeViewControllerC.self)

    // 3. 创建 A 作为 root（A 不走注册表，但需要注册 RouteEntry 让它出现在 currentRouteStack 中）
    let viewControllerA = NativeViewControllerA()
    YYPRouter.shared.registerRouteEntry(for: viewControllerA, routePath: "A", module: "default")
    let navController = YYPRouterNavigationController(rootViewController: viewControllerA)

    // 4. 向 YYPRouter 注册 navController，并设为 active（JS 主动 push/pop 时使用）
    YYPRouter.shared.registerNavigationController(navController, forKey: "default")
    YYPRouter.shared.setActiveNavigationControllerByKey("default")

    // 5. 设置 window，rootViewController 为原生 navController
    let appWindow = UIWindow(frame: UIScreen.main.bounds)
    appWindow.rootViewController = navController
    appWindow.makeKeyAndVisible()
    window = appWindow

    // 6. 创建 RN RootView（透明 overlay，渲染 App.tsx 里的 HybridContainer）
    //    必须在 window 可见之后再添加，否则 RN surface 尺寸为 0
    let rootView = factory.rootViewFactory.view(
      withModuleName: "HybridNavigationDemo",
      initialProperties: nil
    )
    rootView.backgroundColor = UIColor.clear
    rootView.frame = navController.view.bounds
    rootView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    // 不拦截触摸：RN 页面 push 后有自己的 RCTSurfaceTouchHandler，
    // 原生页面的触摸不经过这个 overlay（它在 navController.view 下层）
    rootView.isUserInteractionEnabled = false
    window?.addSubview(rootView)

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
