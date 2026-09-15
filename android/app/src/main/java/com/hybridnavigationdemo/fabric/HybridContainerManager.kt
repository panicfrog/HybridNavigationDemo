package com.hybridnavigationdemo.fabric

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

    // 与 HybridScreenManager 同理：不声明 needsCustomLayoutForChildren()。
    // HybridScreenView 之后会被 reparent 进 Fragment wrapper（由 wrapper 布局），
    // 声明 custom layout 只会让 Fabric 跳过布局、又没有人接管，页面 0 尺寸。
}
