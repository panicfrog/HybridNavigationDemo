package com.hybridnavigationdemo

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.hybridnavigationdemo.fabric.HybridNavigationPackage

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // 本地包不会被 autolinking 收录，必须手动注册：
          // 否则 HybridContainer / HybridScreen 没有对应的 ViewManager，YYPRouter 也没有
          // YYPNavigationModule 可调用，RN 根视图直接渲染失败（表现为白屏）。
          add(HybridNavigationPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    loadReactNative(this)
  }
}
