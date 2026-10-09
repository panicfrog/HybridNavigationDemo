require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "HybridNavigation"
  s.version      = package["version"]
  s.summary      = "React Native 混合导航：RN 与原生页面共用原生路由栈（YYPRouter）"
  s.description  = "HybridNavigation 让 RN 页面与原生页面共用同一个原生路由栈，push/pop、参数校验（JSON Schema）、转场时序全部由原生 YYPRouter 统一承载。"
  s.homepage     = "https://github.com/yeyongping/HybridNavigationDemo"
  s.license      = "MIT"
  s.author       = { "yeyongping" => "yeyongping@example.com" }
  s.source       = { :git => "https://github.com/yeyongping/HybridNavigationDemo.git" }
  s.ios.deployment_target = "17.0"
  s.swift_version = "5.0"
  s.requires_arc = true

  s.source_files       = "ios/HybridNavigation/**/*.{h,m,mm,swift}"
  # public headers：YYPRouterConstants 供消费方（AppDelegate 等 Swift）使用；
  # 含 C++ 的 .mm 对应头保持 project 级，不进 umbrella（避免 C++ 泄漏给 ObjC 消费方）
  s.public_header_files = [
    "ios/HybridNavigation/YYPRouterConstants.h",
    "ios/HybridNavigation/HybridScreenController.h",
  ]

  # JSON Schema 参数校验（Swift SPM 包，经 cocoapods-spm 插件集成；
  # 包来源在消费方 Podfile 用 spm_pkg 'swift-json-schema' 声明，见包 README）
  s.spm_dependency "swift-json-schema/JSONSchema"
  s.spm_dependency "swift-json-schema/JSONSchemaBuilder"
  s.spm_dependency "swift-json-schema/JSONSchemaConversion"
  s.spm_dependency "swift-json-schema/OrderedJSON"

  install_modules_dependencies(s)
end
