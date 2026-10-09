package com.hybridnavigationdemo.fabric

import android.view.View
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.EventDispatcher

/**
 * 与 iOS HybridScreenComponentView 的 emit* 一一对应。
 *
 * 事件名必须与 codegen（EventEmitters.cpp / HybridScreenEventEmitter）派发的名字一致，
 * 否则 JS 侧 onWillAppear 等回调收不到：willAppear / didAppear / willDisappear /
 * didDisappear / nativeDismiss，payload 都是 { routeID }。
 */
internal class HybridScreenEventEmitter(
    private val reactContext: ReactContext,
    private val view: View,
) {
    private val dispatcher: EventDispatcher? =
        UIManagerHelper.getEventDispatcherForReactTag(reactContext, view.id)

    fun emitWillAppear() = dispatch(ROUTE_WILL_APPEAR)

    fun emitDidAppear() = dispatch(ROUTE_DID_APPEAR)

    fun emitWillDisappear() = dispatch(ROUTE_WILL_DISAPPEAR)

    fun emitDidDisappear() = dispatch(ROUTE_DID_DISAPPEAR)

    fun emitNativeDismiss() = dispatch(ROUTE_NATIVE_DISMISS)

    private fun dispatch(eventName: String) {
        val target = dispatcher ?: return
        val routeID = (view as? HybridScreenView)?.routeID.orEmpty()
        target.dispatchEvent(
            ScreenLifecycleEvent(
                surfaceId = UIManagerHelper.getSurfaceId(view),
                viewTag = view.id,
                name = eventName,
                routeID = routeID,
            ),
        )
    }

    private class ScreenLifecycleEvent(
        surfaceId: Int,
        viewTag: Int,
        private val name: String,
        private val routeID: String,
    ) : Event<ScreenLifecycleEvent>(surfaceId, viewTag) {
        override fun getEventName(): String = name

        override fun canCoalesce(): Boolean = false

        override fun getEventData(): com.facebook.react.bridge.WritableMap =
            com.facebook.react.bridge.Arguments.createMap().apply {
                putString("routeID", routeID)
            }
    }

    companion object {
        const val ROUTE_WILL_APPEAR = "willAppear"
        const val ROUTE_DID_APPEAR = "didAppear"
        const val ROUTE_WILL_DISAPPEAR = "willDisappear"
        const val ROUTE_DID_DISAPPEAR = "didDisappear"
        const val ROUTE_NATIVE_DISMISS = "nativeDismiss"

        /** getExportedCustomDirectEventTypeConstants 需要的映射：事件名 → {registrationName} */
        fun directEventTypeConstants(): Map<String, Any> = mapOf(
            ROUTE_WILL_APPEAR to mapOf("registrationName" to "onWillAppear"),
            ROUTE_DID_APPEAR to mapOf("registrationName" to "onDidAppear"),
            ROUTE_WILL_DISAPPEAR to mapOf("registrationName" to "onWillDisappear"),
            ROUTE_DID_DISAPPEAR to mapOf("registrationName" to "onDidDisappear"),
            ROUTE_NATIVE_DISMISS to mapOf("registrationName" to "onNativeDismiss"),
        )
    }
}
