package com.hybridnavigation.fabric

import android.view.View
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.views.view.ReactViewGroup
import com.hybridnavigation.router.YYPNavEvent
import com.hybridnavigation.router.YYPNavEventBus
import com.hybridnavigation.router.YYPRouterConstants

/**
 * 透明容器，对应 iOS HybridContainerView：只负责观察 Fabric 子视图的挂载并广播事件，
 * 真正的路由执行由 YYPRouter 完成。
 *
 * 对齐 iOS 的关键设计（也与 react-native-screens 的 ScreenContainer 一致）：
 * 容器**从不真实持有** HybridScreenView —— 子视图由 HybridContainerManager 的
 * addView/removeViewAt 做纯记账，屏幕视图创建后直接交给 Fragment wrapper 承载
 * （iOS 侧等价物是 mountChildComponentView 不调 super）。
 *
 * 为什么必须这样：若屏幕视图曾被物理插入容器、又被原生摘走进 Fragment，容器的物理子视图数
 * 与 Fabric 的逻辑索引就会错位（React 认为孩子是 [B1@0, B2@1]，物理上却是空的）。
 * 下一个 RN 页面 push 时 Fabric 发出 INSERT @1，ViewGroup.addView(child, 1) 在
 * count=0 时抛 IndexOutOfBoundsException，整批 mount 中止 —— 页面永远挂不上、
 * ScreenDidMount 不触发、push 的 Promise 不 resolve。「第一个 push 成功、后续全失败」
 * 的根因即在此（原生页不受影响，因为不经过 Fabric 挂载）。
 */
class HybridContainerView(
    private val reactContext: ThemedReactContext,
) : ReactViewGroup(reactContext) {

    /** 逻辑孩子（仅记账，不挂到视图树上）。索引空间与 Fabric 的 shadow tree 一致。 */
    private val screens = mutableListOf<HybridScreenView>()

    val screenCount: Int
        get() = screens.size

    fun getScreenAt(index: Int): HybridScreenView = screens[index]

    fun addScreen(screen: HybridScreenView, index: Int) {
        screens.add(index, screen)
        screen.container = this
        postEvent(YYPRouterConstants.EVENT_TYPE_SCREEN_DID_MOUNT, screen)
    }

    fun removeScreenAt(index: Int) {
        if (index !in screens.indices) return
        val screen = screens.removeAt(index)
        screen.container = null
    }

    fun removeScreen(screen: HybridScreenView) {
        if (screens.remove(screen)) {
            screen.container = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postEvent(YYPRouterConstants.EVENT_TYPE_FABRIC_TRANSACTION_DID_MOUNT, null)
    }

    private fun postEvent(type: String, screen: HybridScreenView?) {
        val userInfo = mutableMapOf<String, Any?>(YYPRouterConstants.KEY_TYPE to type)
        screen?.let {
            userInfo[YYPRouterConstants.KEY_CONTROLLER] = it
            it.routeID?.let { id -> userInfo[YYPRouterConstants.KEY_ROUTE_ID] = id }
            it.routePath?.let { p -> userInfo[YYPRouterConstants.KEY_ROUTE_PATH] = p }
        }
        YYPNavEventBus.post(YYPNavEvent(type, userInfo))
    }
}
