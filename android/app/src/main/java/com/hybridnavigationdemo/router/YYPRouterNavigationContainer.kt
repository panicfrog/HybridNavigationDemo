package com.hybridnavigationdemo.router

import android.view.View
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.hybridnavigationdemo.R

/**
 * 对应 iOS 的 YYPRouterNavigationController：用 FragmentManager 承载路由栈。
 *
 * - 每个页面（原生或 RN）都是 YYPRouterScreenFragment；
 * - Fragment 事务用 replace + addToBackStack，动画用系统 slide 资源；
 * - FragmentManager 的 back stack 是唯一事实来源，pop（返回键/JS/native）都走它。
 */
class YYPRouterNavigationContainer(
    private val activity: FragmentActivity,
) : FrameLayout(activity) {

    /** 固定资源 id：fragment 事务与状态恢复都依赖它。 */
    private val hostId = R.id.router_navigation_container
    private val fragmentManager: FragmentManager = activity.supportFragmentManager
    private val backStackChangedListener = FragmentManager.OnBackStackChangedListener {
        onStackChanged?.invoke()
    }
    private var listenerRegistered = false

    /** 栈变化（push/pop/返回键）回调，由 YYPRouter 用来反向同步路由状态。 */
    var onStackChanged: (() -> Unit)? = null

    val fragments: List<YYPRouterScreenFragment>
        get() = fragmentManager.fragments.filterIsInstance<YYPRouterScreenFragment>()

    val viewCount: Int
        get() = fragments.size

    val topFragment: YYPRouterScreenFragment?
        get() = fragments.lastOrNull()

    init {
        id = hostId
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        registerBackStackListenerIfNeeded()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerBackStackListenerIfNeeded()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        Log.d("TouchProbe", "navContainer.dispatch action=${ev.action} handled=$handled")
        return handled
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (listenerRegistered) {
            fragmentManager.removeOnBackStackChangedListener(backStackChangedListener)
            listenerRegistered = false
        }
    }

    private fun registerBackStackListenerIfNeeded() {
        if (!listenerRegistered) {
            fragmentManager.addOnBackStackChangedListener(backStackChangedListener)
            listenerRegistered = true
        }
    }

    /** 把页面放到栈底，作为新的 Root（不对应 iOS 的 push）。 */
    fun setRoot(view: View, routeID: String?, routePath: String) {
        Log.d("YYPNavContainer", "setRoot path=$routePath view=${view.javaClass.simpleName}")
        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fragmentManager.fragments
            .filterIsInstance<YYPRouterScreenFragment>()
            .forEach { fragment ->
                fragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
            }
        replace(view, routeID, routePath, addToBackStack = false)
    }

    fun push(view: View, routeID: String?, routePath: String) {
        Log.d("YYPNavContainer", "push path=$routePath id=$routeID view=${view.javaClass.simpleName}")
        replace(view, routeID, routePath, addToBackStack = true)
    }

    fun pop() {
        if (fragmentManager.backStackEntryCount == 0) {
            return
        }
        fragmentManager.popBackStack()
    }

    private fun replace(
        view: View,
        routeID: String?,
        routePath: String,
        addToBackStack: Boolean,
    ) {
        val fragment = YYPRouterScreenFragment(view, routeID, routePath) { dismissed ->
            YYPRouter.onScreenFragmentDestroyed(dismissed)
        }
        val transaction = fragmentManager.beginTransaction()
        transaction.setReorderingAllowed(true)
        transaction.setCustomAnimations(
            R.anim.router_slide_in_right,
            R.anim.router_slide_out_left,
            R.anim.router_slide_in_left,
            R.anim.router_slide_out_right,
        )
        transaction.replace(hostId, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(fragment.routePath)
        }
        transaction.commit()
    }

    companion object {
        /** FragmentActivity.onCreate 里挂载容器用的 id，与 YYPRouterNavigationContainer 内部保持一致。 */
        val CONTAINER_ID: Int = R.id.router_navigation_container
    }
}
