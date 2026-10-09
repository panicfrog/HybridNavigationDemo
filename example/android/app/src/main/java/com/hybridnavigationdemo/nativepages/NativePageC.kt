package com.hybridnavigationdemo.nativepages

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.hybridnavigation.router.YYPRouter
import org.json.JSONObject

class NativePageC(context: Context, message: String) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(26, 153, 77))
        addView(TextView(context).apply {
            text = "Native C: $message"; setTextColor(Color.WHITE); textSize = 20f
        }, LayoutParams(-2, -2))
        addView(Button(context).apply {
            text = "Push D →"
            setOnClickListener { try { YYPRouter.pushRoute("D", "default", null) } catch (e: Exception) { } }
        }, LayoutParams(-2, -2).apply { topMargin = 24 })
    }
    companion object {
        fun create(ctx: Context, params: String?): View {
            val msg = try { JSONObject(params ?: "{}").optString("message", "") } catch (e: Exception) { "" }
            return NativePageC(ctx, msg)
        }
    }
}
