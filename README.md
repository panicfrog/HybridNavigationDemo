# HybridNavigationDemo

React Native **混合导航** Demo：RN 页面与原生页面（iOS UIViewController / Android View+Fragment）共用**同一个原生路由栈**，由各端原生侧的 YYPRouter 统一承载 push/pop、参数校验与转场时序。

核心思路：**路由栈在原生，页面渲染按需混合**。

- RN 根视图只是一个透明 overlay（不参与触摸分发），仅承载 `HybridContainer` / `HybridScreen` 的 Fabric 声明；
- RN 页面 push 时，原生分配 UUID → 通知 JS 按需挂载组件 → Fabric 渲染出 `HybridScreenView` → 原生把它当作一个普通「页面」压入原生导航栈；
- 原生页面与 RN 页面对调用方完全同构：同一个 `pushRoute(routePath, params)`，栈、转场、返回手势、导航栏全部由原生处理。

## 架构总览

```
┌────────────────────── JS (React Native) ──────────────────────┐
│ HybridNavigationRoot                                          │
│   ├─ registerScreen(routePath, { screen, schema(zod), title })│
│   ├─ 监听 YYPNavPushRN 事件 → 按 routeID 动态挂载 HybridScreen │
│   └─ HybridContainer                                          │
│        └─ HybridScreen (Fabric) × N  ←→  YYPNavigationModule   │
└────────────────────────┬───────────────────────────────────────┘
                         │ TurboModule/Bridge（registerScreen / pushRoute / pop）
┌────────────────────────▼───────────────────────────────────────┐
│ 原生 SDK 层（每端一套，接口对齐）                                │
│  iOS:  YYPRouter.swift + YYPNavigationModule                   │
│  Android: YYPRouter.kt + YYPNavigationModule.kt                │
│  - 注册表 module/routePath → {platform, JSON Schema, factory}  │
│  - push/pop 统一操作队列（转场完成链式执行）                     │
│  - UUID 串联 native → JS → Fabric mount → onEvent 全链路       │
│  - JSON Schema 参数校验（原生统一，JS 不校验）                   │
└────────────────────────────────────────────────────────────────┘
```

### 一次 RN 页面 push 的完整链路

1. JS 调 `pushRoute('B', params)` → 原生用注册时的 JSON Schema 校验 params；
2. 原生分配 UUID，暂存 `PendingPushContext`，发 `pushRoute` 事件给 JS；
3. JS 收到 `YYPNavPushRN`，按 routeID 挂载 `HybridScreen` → Fabric 渲染；
4. 原生收到 `ScreenDidMount(routeID)`，取出 context，关联 RouteEntry（routePath/params/title），压入操作队列；
5. 队列执行 `pushViewController` / Fragment `replace`，转场动画完成后 resolve JS 侧 Promise。

pop 同理入队；系统返回（iOS 侧滑/back button、Android 返回手势）在 `hasPendingOperations()` 时被拦截，避免与在途操作竞争。

## 目录结构（SDK 层 vs Demo 层）

**每端一个 SDK 层**（可整体搬走的混合导航核心，目前住在 app target 内，尚未抽成独立 Pod / Gradle module）：

| 端 | SDK 代码 | 职责 |
|---|---|---|
| iOS | `ios/HybridNavigationDemo/hybrid/` | `YYPRouter.swift`（路由中枢）、`YYPRouterNavigationController.swift`（栈宿主 + 返回拦截）、`YYPNavigationModule.swift`（JS 桥）、`HybridScreenComponentView.mm` / `HybridContainerView.mm`（Fabric 组件）、`YYPRouterConstants`（双端共用常量） |
| Android | `android/app/src/main/java/com/hybridnavigationdemo/{router,bridge,fabric}/` | `router/YYPRouter.kt`（路由中枢）、`router/YYPRouterNavigationContainer.kt` + `YYPRouterScreenFragment.kt`（Fragment 栈宿主）、`router/YYPNavEventBus.kt`（事件总线）、`bridge/YYPNavigationModule.kt`（JS 桥）、`fabric/`（Fabric 组件） |
| JS | `src/`（HybridNavigationRoot / HybridContainer / HybridScreen / HybridNavigation.ts / screens.ts） | 注册、动态挂载、Promise 化的 push/pop |

**Demo 层**（使用 SDK 的示例业务代码）：

- iOS：`AppDelegate.swift`（接入 + 注册页面）、`native/NativeViewControllerA|C.swift`
- Android：`MainActivity.kt`（接入 + 注册页面）、`nativepages/NativePageA|C.kt`
- JS：`ScreenB.tsx` / `ScreenD.tsx` / `ScreenRoot.tsx`

## 使用方式

### JS 侧注册 / 导航

```ts
// src/screens.ts —— 新增 RN 页面在这里加一行
export const screens: Record<string, ScreenDefinition> = {
  B: {
    screen: ScreenB,
    schema: z.object({ title: z.string().optional() }), // zod → JSON Schema 发给原生校验
    title: 'RN Screen B',                                 // 原生导航栏标题
  },
};

// 导航（转场动画完成后 resolve）
import { pushRoute, pop } from './HybridNavigation';
await pushRoute('B', { title: 'hello' });
await pop();
```

### 原生侧注册（iOS）

```swift
// AppDelegate
YYPRouter.shared.attachWindow(window, rootView: rootView)   // Root 页换 window.rootViewController 用
try! YYPRouter.shared.registerScreen("C", module: "default",
    providerClass: NativeViewControllerC.self)              // 原生页：Params 用 @Schemable 生成 schema
try! YYPRouter.shared.registerScreen(YYPRouterRootIndexPath,
    module: "default", providerClass: NativeViewControllerA.self)  // Root 页：立即接管 window
```

### 原生侧注册（Android）

```kotlin
// MainActivity.onCreate
val navigationContainer = YYPRouter.attachNavigationContainer(this)
YYPRouter.registerNativeScreen(
    routePath = "C", module = "default",
    createNativeView = { ctx, params -> NativePageC.create(ctx, params) },
    schemaJSON = """{...}""",   // 手写 JSON Schema 字符串
    title = "Native Screen C",
)
```

### Root 页机制（`__index__`）

`routePath == "__index__"` 的注册项被识别为该 module 的 Root（每 module 至多一个）。启动期主动 mount 后原生接管根视图：iOS 换 `window.rootViewController`，Android 置为 Fragment 栈底。Root 不允许被 `pushRoute`。Demo 默认走**原生 Root**（A 页）；要验证 RN Root，取消 `screens.ts` 中 `[INDEX_PATH]` 的注释并去掉 AppDelegate / MainActivity 的 `__index__` 注册。

## 关键机制速查

- **UUID 串联**：每次 push 分配业务 UUID，贯穿 原生 → JS 挂载 → Fabric mount → ScreenDidMount，解决「同 routePath push 多次」的实例区分（React key = routeID）。
- **统一操作队列**：push/pop 一律入队（iOS 按 nav 分组 FIFO；Android 全局队列），转场完成（iOS `transitionCoordinator` / Android Fragment 动画结束 + onDestroy）后链式执行，completion 各自持有。
- **返回拦截**：iOS 重写 nav 的 pop 决策；Android 接管返回键/手势 —— 队列忙时直接拒绝，保证栈一致性。
- **参数校验**：zod（JS）→ JSON Schema → 原生校验（iOS `swift-json-schema`、Android `networknt/json-schema-validator`）。原生页 iOS 用 `@Schemable` 宏自动生成 schema，Android 手写 JSON 字符串。
- **触摸分发**：RN overlay 整体不参与触摸（iOS `isUserInteractionEnabled = false`；Android `dispatchTouchEvent` 恒 false 的包裹容器），页面内触摸由各自页面自己处理。
- **看门狗**（Android）：转场 2s 强制完成、RN mount 15s 超时清理，防止异常路径卡死队列。

## 运行

```sh
npm start                 # Metro
npm run android
npm run ios               # iOS 用 XcodeGen（xcodegen generate）+ bundle exec pod install
```

构建环境注意：Node 22、npm 镜像顺序、Fabric 宏校验等详见项目内备忘。

## Roadmap / 已知边界

- [ ] **SDK 独立化**：iOS 抽成 Pod、Android 抽成 Gradle library module、JS 侧抽成 npm 包（当前代码已按 SDK 边界组织，但仍在 app target 内）
- [ ] 多 module / 多 container 场景（当前 demo 单 container）
- [ ] present/dismiss 模态路由
- [ ] Android 原生页 schema 自动生成（对齐 iOS `@Schemable`）
