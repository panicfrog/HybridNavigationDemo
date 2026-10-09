package com.hybridnavigationdemo

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    // HybridNavigationPackage 由 autolinking 自动收录
    // （来自 @yyp/hybrid-navigation 的 com.hybridnavigation 库模块）。
    getDefaultReactHost(applicationContext, PackageList(this).packages)
  }

  override fun onCreate() {
    super.onCreate()
    loadReactNative(this)
  }
}
