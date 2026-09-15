package com.hybridnavigationdemo.bridge

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.hybridnavigationdemo.router.YYPNavEvent
import com.hybridnavigationdemo.router.YYPNavEventBus
import com.hybridnavigationdemo.router.YYPRouter
import com.hybridnavigationdemo.router.YYPRouterConstants

class YYPNavigationModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var listenerCount = 0

    init {
        YYPNavEventBus.addListener { event -> onRouterEvent(event) }
    }

    override fun getName(): String = "YYPNavigationModule"

    private fun onRouterEvent(event: YYPNavEvent) {
        if (event.type != YYPRouterConstants.EVENT_TYPE_PUSH_ROUTE) return
        // 与 iOS startObserving/stopObserving 对齐：没人监听时不推
        if (listenerCount == 0) return
        val params = Arguments.createMap()
        event.userInfo.forEach { (key, value) ->
            when (value) {
                is String -> params.putString(key, value)
                is Int -> params.putInt(key, value)
                is Boolean -> params.putBoolean(key, value)
            }
        }
        mainHandler.post {
            if (reactContext.hasActiveReactInstance()) {
                reactContext
                    .getJSModule(RCTDeviceEventEmitter::class.java)
                    .emit(PUSH_RN_EVENT, params)
            }
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        listenerCount++
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount = (listenerCount - count).coerceAtLeast(0)
    }

    @ReactMethod
    fun registerScreen(routePath: String, module: String, schemaJSON: String) {
        YYPRouter.registerScreen(routePath, module, schemaJSON.ifBlank { null })
    }

    @ReactMethod
    fun pushRoute(routePath: String, module: String, paramsJSON: String, promise: Promise) {
        mainHandler.post {
            try {
                YYPRouter.pushRoute(routePath, module, paramsJSON.ifBlank { null }) {
                    mainHandler.post { promise.resolve(null) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "pushRoute $routePath failed: ${e.message}", e)
                promise.reject("PUSH_FAILED", e.message, e)
            }
        }
    }

    @ReactMethod
    fun pop(promise: Promise) {
        mainHandler.post {
            try {
                YYPRouter.popRoute { mainHandler.post { promise.resolve(null) } }
            } catch (e: Exception) { promise.reject("POP_FAILED", e.message, e) }
        }
    }

    private companion object {
        const val PUSH_RN_EVENT = "YYPNavPushRN"
        const val TAG = "YYPNavigationModule"
    }
}
