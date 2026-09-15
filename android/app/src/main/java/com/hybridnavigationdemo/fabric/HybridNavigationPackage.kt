package com.hybridnavigationdemo.fabric

import android.view.View
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ReactShadowNode
import com.facebook.react.uimanager.ViewManager
import com.hybridnavigationdemo.bridge.YYPNavigationModule

@Suppress("UNCHECKED_CAST")
class HybridNavigationPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(YYPNavigationModule(reactContext))
    }
    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<View, ReactShadowNode<*>>> {
        return listOf(
            HybridContainerManager() as ViewManager<View, ReactShadowNode<*>>,
            HybridScreenManager() as ViewManager<View, ReactShadowNode<*>>
        )
    }
}
