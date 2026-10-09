package com.hybridnavigationdemo.fabric

import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.HybridScreenManagerDelegate
import com.facebook.react.viewmanagers.HybridScreenManagerInterface

class HybridScreenManager :
    ViewGroupManager<HybridScreenView>(),
    HybridScreenManagerInterface<HybridScreenView> {

    private val delegate: ViewManagerDelegate<HybridScreenView> =
        HybridScreenManagerDelegate(this)

    override fun getName() = "HybridScreen"

    protected override fun getDelegate(): ViewManagerDelegate<HybridScreenView> = delegate

    override fun createViewInstance(reactContext: ThemedReactContext) =
        HybridScreenView(reactContext)

    override fun setRouteID(view: HybridScreenView, value: String?) {
        view.routeID = value
    }

    override fun setRoutePath(view: HybridScreenView, value: String?) {
        view.routePath = value
    }

    /** 事件名与 codegen EventEmitter 派发的名字保持一致（与 iOS 相同）。 */
    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
        HybridScreenEventEmitter.directEventTypeConstants().toMutableMap()

    // 注意：不能声明 needsCustomLayoutForChildren() = true。
    // Fabric (SurfaceMountingManager.updateLayout) 对「parent ViewManager 声明了 custom layout」的
    // children 会跳过 view.layout()，把布局完全交给容器 —— 而 HybridScreenView 自己不实现任何
    // 子树布局，结果 RN 内容整棵树无人 layout、全部 0 尺寸（页面白屏）。
    // 页面内容交给 Fabric 用 Yoga 绝对坐标布局；reparent 进 Fragment wrapper 后 Fabric 后续
    // commit 仍会持续按绝对坐标 override（react-native-screens 的 StackScreen 同样不声明）。
}
