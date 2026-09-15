package com.hybridnavigationdemo.fabric

import android.view.View
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.views.view.ReactViewGroup
import com.hybridnavigationdemo.router.YYPNavEvent
import com.hybridnavigationdemo.router.YYPNavEventBus
import com.hybridnavigationdemo.router.YYPRouterConstants

/**
 * 透明容器，对应 iOS HybridContainerView：只负责观察 Fabric 子视图的挂载并广播事件，
 * 真正的路由执行由 YYPRouter 完成。
 */
class HybridContainerView(
    private val reactContext: ThemedReactContext,
) : ReactViewGroup(reactContext) {

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child is HybridScreenView) {
            android.util.Log.d(
                "YYPNavContainer",
                "onViewAdded screen=${child.routePath} bounds=" +
                    "${child.left},${child.top}-${child.right},${child.bottom}",
            )
            child.container = this
            postEvent(YYPRouterConstants.EVENT_TYPE_SCREEN_DID_MOUNT, child)
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child is HybridScreenView && child.container === this) {
            child.container = null
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
