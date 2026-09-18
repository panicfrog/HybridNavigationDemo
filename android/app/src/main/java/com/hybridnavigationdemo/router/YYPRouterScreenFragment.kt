package com.hybridnavigationdemo.router

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.hybridnavigationdemo.fabric.HybridScreenView

private const val TAG = "YYPNavFragment"

/**
 * 路由栈的单个节点，对应 iOS 里「以 RN/native 视图为 rootView 的 UIViewController」。
 *
 * - screen view 由 Fragment 的 wrapper 承载（Fabric 仍持有所有权，tag 不变）；
 * - Fragment 生命周期回调 → HybridScreenView 的 emit*（RN 页面才会转发给 JS）；
 * - onDestroy（被 pop）→ emitNativeDismiss，让 JS 按 routeID 精确移除对应实例。
 *
 * 顶部导航栏（对齐 iOS UINavigationBar）：
 * - 非根页面（push 进栈的）显示 header：返回按钮 + 标题；
 * - 根页面（setRoot）不显示，与 iOS Root 无返回按钮一致；
 * - 返回按钮走 YYPRouter.popRoute()（pending/转场中有保护），而不是直接操作 FragmentManager。
 */
class YYPRouterScreenFragment(
    private val screenView: View,
    val routeID: String?,
    val routePath: String,
    private val showHeader: Boolean,
    private val headerTitle: String?,
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
        if (!showHeader) return host

        val column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                buildHeader(requireContext()),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                host,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
        }
        return column
    }

    /** 顶部导航栏：返回按钮（走 YYPRouter.popRoute）+ 居中标题。 */
    private fun buildHeader(context: Context): View {
        val density = resources.displayMetrics.density
        val heightPx = (56 * density).toInt()
        val hPad = (16 * density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#F2F2F7"))

            addView(
                TextView(context).apply {
                    text = "返回"
                    textSize = 17f
                    setTextColor(Color.parseColor("#007AFF"))
                    setPadding(hPad, heightPx / 4, hPad, heightPx / 4)
                    isClickable = true
                    setOnClickListener {
                        // 与 iOS navigationBar(_:shouldPop:) 对齐：pending/转场中拒绝
                        if (YYPRouter.hasPendingOperations()) return@setOnClickListener
                        YYPRouter.popRoute()
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                TextView(context).apply {
                    text = headerTitle ?: routePath
                    textSize = 17f
                    setTextColor(Color.BLACK)
                    maxLines = 1
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                    gravity = android.view.Gravity.CENTER
                },
            )
            // 占位：让标题视觉居中（补偿左侧返回按钮宽度）
            addView(
                View(context),
                LinearLayout.LayoutParams(2 * hPad, LinearLayout.LayoutParams.MATCH_PARENT),
            )

            // 状态栏 inset（edge-to-edge 下 header 顶到状态栏）
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
                WindowInsets.CONSUMED
            }
        }
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
