package com.hybridnavigationdemo

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.hybridnavigationdemo.nativepages.NativePageA
import com.hybridnavigationdemo.nativepages.NativePageC
import com.hybridnavigationdemo.router.YYPRouter
import com.hybridnavigationdemo.router.YYPRouterConstants

class MainActivity : ReactActivity() {

  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    val handled = super.dispatchTouchEvent(ev)
    Log.d(TAG_TOUCH, "MainActivity.dispatch action=${ev.action} handled=$handled")
    // 调试：每次 DOWN（限流 2s）dump 一次完整视图树，观察 Fragment/页面挂载情况
    if (ev.action == MotionEvent.ACTION_DOWN && ev.eventTime - lastDumpAt > 2000) {
      lastDumpAt = ev.eventTime
      Log.d(TAG_TOUCH, "=== 视图树(DOWN 触发) ===")
      dump(window.decorView, 0)
    }
    return handled
  }

  private var lastDumpAt = 0L

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // ReactActivityDelegate 已经把 ReactRootView 设为 contentView。
    // 这里再套一层：路由栈容器在 0 号位（下层、负责承载所有页面），
    // ReactRootView 作为上层透明 overlay（只渲染 HybridContainer / HybridScreen 声明）。
    val contentView = findViewById<ViewGroup>(android.R.id.content)
    val reactRootView = contentView.getChildAt(0)

    val root = FrameLayout(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
      setBackgroundColor(Color.TRANSPARENT)
    }
    val probe = object : FrameLayout(this) {
      override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        Log.d(TAG_TOUCH, "root.dispatch action=${ev.action} handled=$handled")
        return handled
      }
    }
    val actualRoot = probe.apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
      setBackgroundColor(Color.TRANSPARENT)
      setOnHierarchyChangeListener(
        object : ViewGroup.OnHierarchyChangeListener {
          override fun onChildViewAdded(parent: View?, child: View?) {
            Log.d(
              TAG_TOUCH,
              "root.childAdded ${child?.javaClass?.name} enabled=${child?.isEnabled} " +
                "clickable=${child?.isClickable} listener=${child?.hasOnTouchListener()}",
            )
          }
          override fun onChildViewRemoved(parent: View?, child: View?) {}
        },
      )
    }

    val navigationContainer = YYPRouter.attachNavigationContainer(this)
    actualRoot.addView(navigationContainer)

    contentView.removeAllViews()
    // 对照实验开关：adb shell setprop debug.hybridnav.droprn 1 时不挂 RN 根视图
    val dropReactRoot = systemProp("debug.hybridnav.droprn") == "1"
    Log.d(TAG_TOUCH, "dropReactRoot=$dropReactRoot")
    if (!dropReactRoot) {
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
    }
    setContentView(actualRoot)

    // 从 content 开始逐层找真正的触摸宿主；用 requestDisallowInterceptTouchEvent 自底向上探测
    actualRoot.postDelayed({
      var node: View? = actualRoot
      while (node != null) {
        Log.d(
          TAG_TOUCH,
          "chain: ${node.javaClass.name} enabled=${node.isEnabled} " +
            "clickable=${node.isClickable} listener=${node.hasOnTouchListener()}",
        )
        node = node.parent as? View
      }
    }, 2000)

    Log.d(TAG_TOUCH, "reactRootView=$reactRootView parent=${reactRootView.parent?.javaClass?.name}")

    actualRoot.postDelayed({
      Log.d(TAG_TOUCH, "=== 视图树 ===")
      dump(actualRoot, 0)
    }, 3000)

    registerDemoScreens()
  }

  private fun dump(view: View, depth: Int) {
    val indent = "  ".repeat(depth)
    Log.d(
      TAG_TOUCH,
      "$indent${view.javaClass.simpleName} enabled=${view.isEnabled} clickable=${view.isClickable} " +
        "listener=${view.hasOnTouchListener()} bounds=${view.left},${view.top}-${view.right},${view.bottom}",
    )
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) dump(view.getChildAt(i), depth + 1)
    }
  }

  private fun View.hasOnTouchListener(): Boolean {
    return try {
      val field = View::class.java.getDeclaredField("mOnTouchListener")
      field.isAccessible = true
      field.get(this) != null
    } catch (e: Exception) {
      false
    }
  }

  private fun systemProp(name: String): String? = try {
    val clazz = Class.forName("android.os.SystemProperties")
    clazz.getMethod("get", String::class.java).invoke(null, name) as? String
  } catch (e: Exception) {
    null
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
    )
    YYPRouter.registerNativeScreen(
      routePath = YYPRouterConstants.ROOT_INDEX_PATH,
      module = "default",
      createNativeView = { context, params -> NativePageA.create(context, params) },
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
    const val TAG_TOUCH = "TouchProbe"
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
