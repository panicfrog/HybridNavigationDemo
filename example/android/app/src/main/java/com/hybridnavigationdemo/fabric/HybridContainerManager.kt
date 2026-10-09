package com.hybridnavigationdemo.fabric

import android.view.View
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.HybridContainerManagerDelegate
import com.facebook.react.viewmanagers.HybridContainerManagerInterface

class HybridContainerManager :
    ViewGroupManager<HybridContainerView>(),
    HybridContainerManagerInterface<HybridContainerView> {

    private val delegate: ViewManagerDelegate<HybridContainerView> =
        HybridContainerManagerDelegate(this)

    override fun getName() = "HybridContainer"

    protected override fun getDelegate(): ViewManagerDelegate<HybridContainerView> = delegate

    override fun createViewInstance(reactContext: ThemedReactContext) =
        HybridContainerView(reactContext)

    // 对齐 react-native-screens ScreenContainerViewManager 的做法：
    // addView/removeViewAt 只做逻辑记账，不做物理视图插入；getChildCount/getChildAt
    // 返回逻辑孩子。容器从不真实持有 HybridScreenView（视图归 Fragment wrapper），
    // 否则 Fabric 按逻辑索引 INSERT/DELETE 时会与物理子视图错位（见 HybridContainerView 注释）。
    override fun addView(parent: HybridContainerView, child: View, index: Int) {
        require(child is HybridScreenView) {
            "HybridContainer only accepts HybridScreen children, got ${child.javaClass.simpleName}"
        }
        parent.addScreen(child, index)
    }

    override fun removeViewAt(parent: HybridContainerView, index: Int) {
        parent.removeScreenAt(index)
    }

    override fun removeView(parent: HybridContainerView, view: View) {
        if (view is HybridScreenView) {
            parent.removeScreen(view)
        }
    }

    override fun getChildCount(parent: HybridContainerView): Int = parent.screenCount

    override fun getChildAt(parent: HybridContainerView, index: Int): View = parent.getScreenAt(index)

    // 与 HybridScreenManager 同理：不声明 needsCustomLayoutForChildren()。
    // HybridScreenView 之后会被 reparent 进 Fragment wrapper（由 wrapper 布局），
    // 声明 custom layout 只会让 Fabric 跳过布局、又没有人接管，页面 0 尺寸。
}
