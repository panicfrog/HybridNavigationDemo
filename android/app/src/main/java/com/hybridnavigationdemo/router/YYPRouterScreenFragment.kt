package com.hybridnavigationdemo.router

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.hybridnavigationdemo.fabric.HybridScreenView

private const val TAG = "YYPNavFragment"

/**
 * 路由栈的单个节点，对应 iOS 里「以 RN/native 视图为 rootView 的 UIViewController」。
 *
 * - screen view 由 Fragment 的 wrapper 承载（Fabric 仍持有所有权，tag 不变）；
 * - Fragment 生命周期回调 → HybridScreenView 的 emit*（RN 页面才会转发给 JS）；
 * - onDestroy（被 pop）→ emitNativeDismiss，让 JS 按 routeID 精确移除对应实例。
 */
class YYPRouterScreenFragment(
    private val screenView: View,
    val routeID: String?,
    val routePath: String,
    private val onNativeDismiss: (YYPRouterScreenFragment) -> Unit,
) : Fragment() {

    private var wrapper: FrameLayout? = null

    val hybridScreen: HybridScreenView?
        get() = screenView as? HybridScreenView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "onCreateView path=$routePath id=$routeID screen=${screenView.javaClass.simpleName}")
        val host = FrameLayout(requireContext())
        wrapper = host
        (screenView.parent as? ViewGroup)?.removeView(screenView)
        host.addView(
            screenView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return host
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart path=$routePath")
        hybridScreen?.emitWillAppear()
    }

    override fun onResume() {
        super.onResume()
        Log.d(
            TAG,
            "onResume path=$routePath screenBounds=" +
                "${screenView.left},${screenView.top}-${screenView.right},${screenView.bottom} " +
                "screenChildren=${(screenView as? ViewGroup)?.childCount}",
        )
        hybridScreen?.emitDidAppear()
        YYPRouter.onScreenFragmentAppeared(this)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause path=$routePath")
        hybridScreen?.emitWillDisappear()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop path=$routePath")
        hybridScreen?.emitDidDisappear()
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView path=$routePath")
        wrapper?.removeView(screenView)
        wrapper = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy path=$routePath")
        hybridScreen?.emitNativeDismiss()
        onNativeDismiss(this)
    }
}
