package com.hybridnavigationdemo.router

import android.view.View
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
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
        backPressCallback.isEnabled = fragmentManager.backStackEntryCount > 0
        onStackChanged?.invoke()
    }
    private var listenerRegistered = false

    /**
     * 接管系统返回键（虚拟键/手势），替代 FragmentActivity 的默认「直接 pop back stack」。
     * 与 iOS navigationBar(_:shouldPop:) 对齐：pending 操作中拒绝；栈底时让系统默认处理（退出）。
     */
    private val backPressCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (YYPRouter.hasPendingOperations()) return
            YYPRouter.popRoute()
        }
    }

    /** 栈变化（push/pop/返回键）回调，由 YYPRouter 用来反向同步路由状态。 */
    var onStackChanged: (() -> Unit)? = null

    val fragments: List<YYPRouterScreenFragment>
        get() = fragmentManager.fragments.filterIsInstance<YYPRouterScreenFragment>()

    /**
     * 逻辑栈深度 = back stack 条目数 + 1（Root 用 replace 不带 addToBackStack，不占条目）。
     * 不能用 fragments.size —— replace+addToBackStack 下 fragmentManager.fragments 只包含
     * 当前 added 的栈顶 Fragment，push 后恒为 1，popRoute 会误判「无可 pop」而空转
     * （Promise 照常 resolve 但页面不动）。
     */
    val viewCount: Int
        get() = if (fragmentManager.fragments.isEmpty()) 0
        else fragmentManager.backStackEntryCount + 1

    val topFragment: YYPRouterScreenFragment?
        get() = fragments.lastOrNull()

    init {
        id = hostId
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        registerBackStackListenerIfNeeded()
        activity.onBackPressedDispatcher.addCallback(activity, backPressCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerBackStackListenerIfNeeded()
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

    /** 把页面放到栈底，作为新的 Root（不对应 iOS 的 push）。Root 页不显示导航栏。 */
    fun setRoot(view: View, routeID: String?, routePath: String) {
        Log.d("YYPNavContainer", "setRoot path=$routePath view=${view.javaClass.simpleName}")
        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fragmentManager.fragments
            .filterIsInstance<YYPRouterScreenFragment>()
            .forEach { fragment ->
                fragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
            }
        replace(view, routeID, routePath, addToBackStack = false, showHeader = false, headerTitle = null)
    }

    fun push(view: View, routeID: String?, routePath: String, headerTitle: String? = null) {
        Log.d("YYPNavContainer", "push path=$routePath id=$routeID view=${view.javaClass.simpleName}")
        replace(view, routeID, routePath, addToBackStack = true, showHeader = true, headerTitle = headerTitle)
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
        showHeader: Boolean,
        headerTitle: String?,
    ) {
        val fragment = YYPRouterScreenFragment(view, routeID, routePath, showHeader, headerTitle) { dismissed ->
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
