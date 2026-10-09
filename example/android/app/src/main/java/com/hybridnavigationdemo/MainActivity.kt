package com.hybridnavigationdemo

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.hybridnavigationdemo.nativepages.NativePageA
import com.hybridnavigationdemo.nativepages.NativePageC
import com.hybridnavigation.router.YYPRouter
import com.hybridnavigation.router.YYPRouterConstants

class MainActivity : ReactActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // ReactActivityDelegate 已经把 ReactRootView 设为 contentView。
    // 这里再套一层：路由栈容器在 0 号位（下层、负责承载所有页面），
    // ReactRootView 作为上层透明 overlay（只渲染 HybridContainer / HybridScreen 声明，
    // HybridScreen 的视图由 Fragment 承载，容器对它只做记账不做物理插入）。
    val contentView = findViewById<ViewGroup>(android.R.id.content)
    val reactRootView = contentView.getChildAt(0)

    val actualRoot = FrameLayout(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }

    val navigationContainer = YYPRouter.attachNavigationContainer(this)
    actualRoot.addView(navigationContainer)

    contentView.removeAllViews()

    // RN 根视图必须整体不参与触摸分发（对应 iOS 的 rootView.isUserInteractionEnabled = false）。
    // 注意不能靠 OnTouchListener 返回 true：那是「消费事件」，全屏 overlay 会成为 touch target，
    // 整条手势被它吃掉，下层兄弟（路由栈容器）一个事件都收不到——Android 的 ViewGroup 只在
    // child 的 dispatchTouchEvent 返回 false 时才会继续尝试下一个 child。
    // 因此正确等价物是包一层 dispatchTouchEvent 恒 false 的容器：DOWN 时父容器发现这棵子树
    // 不处理，自然落到 0 号位的路由栈容器上；被 Fragment 接管的 RN 页面有自己的 JSTouchDispatcher。
    actualRoot.addView(
      NonTouchOverlay(this).apply {
        addView(
          reactRootView,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          ),
        )
      },
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      ),
    )
    setContentView(actualRoot)

    registerDemoScreens()
  }

  /**
   * 与 iOS AppDelegate 的注册顺序对齐：
   * 先注册普通页，再注册原生 Root（__index__ 会立即成为栈底）。
   */
  private fun registerDemoScreens() {
    YYPRouter.registerNativeScreen(
      routePath = "C",
      module = "default",
      createNativeView = { context, params -> NativePageC.create(context, params) },
      schemaJSON = NATIVE_C_SCHEMA,
      title = "Native Screen C", // 与 iOS NativeViewControllerC.title 对齐
    )
    YYPRouter.registerNativeScreen(
      routePath = YYPRouterConstants.ROOT_INDEX_PATH,
      module = "default",
      createNativeView = { context, params -> NativePageA.create(context, params) },
      title = "Native Screen A01", // 与 iOS NativeViewControllerA.title 对齐（Root 不显示导航栏）
    )
  }

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "HybridNavigationDemo"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  /** RN 根视图的宿主：整棵子树不参与触摸分发（iOS isUserInteractionEnabled = false 的 Android 等价物）。 */
  private class NonTouchOverlay(context: Context) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false
  }

  private companion object {
    /** 与 JS 侧 zod schema 等价的最小校验样例：message 必须为 string。 */
    const val NATIVE_C_SCHEMA = """
      {
        "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "properties": { "message": { "type": "string" } },
        "required": ["message"],
        "additionalProperties": false
      }
    """
  }
}
