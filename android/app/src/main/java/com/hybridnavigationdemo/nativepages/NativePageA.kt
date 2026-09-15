package com.hybridnavigationdemo.nativepages

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import com.hybridnavigationdemo.router.YYPRouter

class NativePageA(context: Context) : LinearLayout(context) {
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        Log.d("TouchProbe", "NativePageA.dispatch action=${ev.action} handled=$handled")
        return handled
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(26, 102, 230))
        addView(TextView(context).apply {
            text = "Native A"; setTextColor(Color.WHITE); textSize = 24f
        }, LayoutParams(-2, -2))
        addView(Button(context).apply {
            text = "Push B →"
            setOnClickListener { YYPRouter.pushRoute("B", "default", null) }
        }, LayoutParams(-2, -2).apply { topMargin = 24 })
    }
    companion object { fun create(ctx: Context, params: String?): View = NativePageA(ctx) }
}
