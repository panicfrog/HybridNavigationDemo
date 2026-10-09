# @yyp/hybrid-navigation

React Native **混合导航**：RN 页面与原生页面（iOS UIViewController / Android View+Fragment）共用**同一个原生路由栈**，由各端原生侧的 YYPRouter 统一承载 push/pop、参数校验（JSON Schema）与转场时序。路由栈在原生，页面渲染按需混合。

一个 npm 包同时携带三端 SDK：

| 维度 | 内容 |
|---|---|
| JS | `src/`（HybridNavigationRoot / HybridContainer / HybridScreen / registerScreens / pushRoute / pop） |
| iOS | `ios/HybridNavigation`（YYPRouter、Fabric 组件、YYPNavigationModule）+ `HybridNavigation.podspec` |
| Android | `android/`（com.hybridnavigation：router / bridge / fabric + library module） |

双端原生代码经 RN autolinking 自动收录，业务方只需安装本包。

## 安装

```sh
npm install @yyp/hybrid-navigation
```

依赖要求：`react-native >= 0.85`（新架构 Fabric）、`zod ^3`、`zod-to-json-schema ^3`（peerDependencies）。

### iOS 额外一步（CocoaPods + SPM 依赖）

本库的 JSON Schema 校验依赖 SPM 包 [swift-json-schema](https://github.com/ajevans99/swift-json-schema)（含 Swift 宏，无法用 CocoaPods 原生表达），经 [cocoapods-spm](https://github.com/trinhngocthuyen/cocoapods-spm) 插件集成。在 app 的 Podfile 加：

```ruby
gem install cocoapods-spm   # 一次性（≥0.1.20）

# Podfile 顶部
plugin 'cocoapods-spm'

target 'YourApp' do
  config = use_native_modules!

  spm_pkg 'swift-json-schema',
          :git => 'https://github.com/ajevans99/swift-json-schema',
          :branch => 'main',
          :products => ['JSONSchema', 'JSONSchemaBuilder', 'JSONSchemaConversion', 'OrderedJSON']

  use_react_native!(...)
end
```

并固定 `platform :ios, '17.0'`（宏与 SDK 要求）。

### Android

无需额外配置（autolinking 自动收录 library module）。

## 使用

### 1. 定义页面注册表（业务侧）

```ts
// screens.ts
import { z } from 'zod';
import { type ScreenRegistry } from '@yyp/hybrid-navigation';
import ScreenB from './ScreenB';

export const screens: ScreenRegistry = {
  B: {
    screen: ScreenB,
    schema: z.object({ title: z.string().optional() }), // zod → JSON Schema，原生校验
    title: 'RN Screen B',                                // 原生导航栏标题
  },
};
```

### 2. 挂载根容器

```tsx
import { HybridNavigationRoot } from '@yyp/hybrid-navigation';
import { screens } from './screens';

export default function App() {
  return <HybridNavigationRoot screens={screens} />;
}
```

### 3. 导航（转场完成后 Promise resolve）

```ts
import { pushRoute, pop } from '@yyp/hybrid-navigation';
await pushRoute('B', { title: 'hello' });
await pop();
```

### 4. 注册原生页面

iOS（AppDelegate）：

```swift
import HybridNavigation

YYPRouter.shared.attachWindow(window, rootView: rootView)
try! YYPRouter.shared.registerScreen("C", module: "default",
    providerClass: NativeViewControllerC.self)   // Params 用 @Schemable 生成 schema
```

Android（MainActivity.onCreate）：

```kotlin
val navigationContainer = YYPRouter.attachNavigationContainer(this)
YYPRouter.registerNativeScreen(
    routePath = "C", module = "default",
    createNativeView = { ctx, params -> NativePageC.create(ctx, params) },
    schemaJSON = """{"type":"object","properties":{"message":{"type":"string"}}}""",
    title = "Native Screen C",
)
```

### Root 页机制（`__index__`）

`routePath == "__index__"` 的注册项被识别为该 module 的 Root（每 module 至多一个），启动期 mount 后原生接管根视图（iOS 换 `window.rootViewController`，Android 置为 Fragment 栈底）。Root 不允许被 `pushRoute`。

## 关键机制

- **UUID 串联**：每次 push 分配业务 UUID，贯穿 原生 → JS 挂载 → Fabric mount → ScreenDidMount；同 routePath push 多次时用 routeID 区分实例（React key = routeID）
- **统一操作队列**：push/pop 一律入队，转场完成（iOS `transitionCoordinator` / Android Fragment 动画结束）后链式执行；系统返回手势在队列忙时被拦截
- **参数校验**：zod（JS）→ JSON Schema → 原生校验（iOS swift-json-schema / Android networknt json-schema-validator）
- **看门狗**（Android）：转场 2s 强制完成、RN mount 15s 超时清理

完整设计与 demo 见仓库根 README 及 `example/`。
