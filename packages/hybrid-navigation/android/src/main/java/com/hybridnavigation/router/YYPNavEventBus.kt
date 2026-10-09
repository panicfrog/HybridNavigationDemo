package com.hybridnavigation.router

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

data class YYPNavEvent(
    val type: String,
    val userInfo: Map<String, Any?> = emptyMap()
)

object YYPNavEventBus {
    private val listeners = CopyOnWriteArraySet<(YYPNavEvent) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addListener(listener: (YYPNavEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (YYPNavEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun post(event: YYPNavEvent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.forEach { it(event) }
        } else {
            mainHandler.post { listeners.forEach { it(event) } }
        }
    }
}
