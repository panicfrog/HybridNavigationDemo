package com.hybridnavigationdemo.fabric

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.facebook.react.ReactRootView
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.JSTouchDispatcher
import com.facebook.react.uimanager.RootView
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.views.view.ReactViewGroup
import com.hybridnavigationdemo.router.YYPRouterConstants

/**
 * RN 页面在 Android 侧的落点，对应 iOS 的 HybridScreenComponentView。
 *
 * iOS 上这个 view 被直接当作 UIViewController 的 rootView 交给 UINavigationController；
 * Android 上它会被塞进 YYPRouterScreenFragment 的 wrapper 里，由 FragmentManager 承载。
 * 两个平台都保留 Fabric 对它的所有权（tag 不变），因此事件仍能按 tag 派发回 JS。
 *
 * 触摸链路（对齐 iOS 的 RCTSurfaceTouchHandler 处理）：
 * RN 的触摸默认由 ReactRootView.onInterceptTouchEvent 统一收走并转给 JS。页面被移出
 * ReactRootView 子树后就不再有这个通道，于是这里自建一个 JSTouchDispatcher
 * （react-native-screens 的 FormSheetContentView 用的是同一套做法），
 * 由被 pop/push 的页面自己接管触摸并转发给 JS。
 */
@OptIn(UnstableReactNativeAPI::class)
class HybridScreenView(
    private val reactContext: ThemedReactContext,
) : ReactViewGroup(reactContext),
    RootView {

    var routeID: String? = null
    var routePath: String? = null
    var container: HybridContainerView? = null

    private val jsTouchDispatcher = JSTouchDispatcher(this)

    private val eventDispatcher
        get() = UIManagerHelper.getEventDispatcher(reactContext, UIManagerType.FABRIC)

    private val emitter: HybridScreenEventEmitter by lazy {
        HybridScreenEventEmitter(reactContext, this)
    }

    val isRoot: Boolean
        get() = routePath == YYPRouterConstants.ROOT_INDEX_PATH

    fun emitWillAppear() = emitter.emitWillAppear()

    fun emitDidAppear() = emitter.emitDidAppear()

    fun emitWillDisappear() = emitter.emitWillDisappear()

    fun emitDidDisappear() = emitter.emitDidDisappear()

    fun emitNativeDismiss() = emitter.emitNativeDismiss()

    /** 从当前父容器（HybridContainer）摘下，准备转交给 Fragment 的 wrapper。 */
    fun detachFromCurrentParent() {
        (parent as? ViewGroup)?.removeView(this)
    }

    // region - JS 触摸转发

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        Log.d(
            "YYPNavScreen",
            "onLayout $l,$t-$r,$b changed=$changed parent=${parent?.javaClass?.simpleName}",
        )
        super.onLayout(changed, l, t, r, b)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        dispatchTouchToJS(event)
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        dispatchTouchToJS(event)
        super.onTouchEvent(event)
        // 没有子视图处理时也要吃掉手势，才能收到 MOVE/UP
        return true
    }

    private fun dispatchTouchToJS(event: MotionEvent) {
        // 仍在 RN 根视图子树里时由 ReactRootView 统一转发，避免同一事件被消费两次
        if (isMountedUnderReactRoot()) return
        val dispatcher = eventDispatcher ?: return
        jsTouchDispatcher.handleTouchEvent(event, dispatcher, reactContext)
    }

    override fun onChildStartedNativeGesture(childView: View?, ev: MotionEvent) {
        if (isMountedUnderReactRoot()) return
        val dispatcher = eventDispatcher ?: return
        jsTouchDispatcher.onChildStartedNativeGesture(ev, dispatcher, reactContext)
    }

    override fun onChildStartedNativeGesture(ev: MotionEvent) {
        onChildStartedNativeGesture(null, ev)
    }

    override fun onChildEndedNativeGesture(childView: View, ev: MotionEvent) {
        if (isMountedUnderReactRoot()) return
        val dispatcher = eventDispatcher ?: return
        jsTouchDispatcher.onChildEndedNativeGesture(ev, dispatcher)
    }

    override fun handleException(t: Throwable) {
        reactContext.reactApplicationContext.handleException(RuntimeException(t))
    }

    /** 与 iOS isMountedUnderReactRoot 等价的判据。 */
    private fun isMountedUnderReactRoot(): Boolean {
        var parent = this.parent
        while (parent != null) {
            if (parent is ReactRootView) return true
            parent = parent.parent
        }
        return false
    }

    // endregion
}
