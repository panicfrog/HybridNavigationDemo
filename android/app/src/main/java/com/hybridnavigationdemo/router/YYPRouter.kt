package com.hybridnavigationdemo.router

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.databind.ObjectMapper
import com.hybridnavigationdemo.fabric.HybridScreenView
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class YYPRouterPlatform { ANDROID_NATIVE, REACT_NATIVE }

class Registration(
    val routePath: String,
    val module: String,
    val platform: YYPRouterPlatform,
    val schema: JsonSchema?,
    val createNativeView: ((android.content.Context, String?) -> View)? = null,
    val isRoot: Boolean = false,
    /** 导航栏标题（对齐 iOS 的 UIViewController.title）。RN 页面默认用 routePath。 */
    val title: String? = null,
)

/**
 * Android 侧与 iOS YYPRouter.swift 对齐的路由中枢。
 *
 * 关键差异：iOS 用 UINavigationController + UIViewController 承载栈，
 * Android 用 YYPRouterNavigationContainer（FragmentManager）承载栈；
 * RN 页面的视图来源一样 —— Fabric 渲染出来的 HybridScreenView 本身。
 */
object YYPRouter {
    private val objectMapper = ObjectMapper()
    private val registrations = ConcurrentHashMap<String, Registration>()
    private val pendingPushContexts = ConcurrentHashMap<UUID, PendingPushContext>()
    private val pendingCommands = ArrayDeque<() -> Unit>()

    private var container: YYPRouterNavigationContainer? = null
    private var hostActivity: FragmentActivity? = null
    private var fabricReady = false
    private var listenerRegistered = false

    /** 已激活（在栈上）的 routeID，用于去重与 pop 同步。 */
    private val activeRouteIDs = mutableSetOf<String>()

    /** push/pop 的 Promise resolve，等在栈上真正就绪/退场后再回调。 */
    private var pendingPushCompletion: (() -> Unit)? = null
    private var pendingPopCompletion: (() -> Unit)? = null
    private var pendingPopTargetCount: Int = -1
    private var pendingRootView: View? = null
    private var pendingRootRouteID: String? = null
    private var pendingRootRoutePath: String? = null

    data class PendingPushContext(
        val id: UUID, val routePath: String, val module: String,
        val paramsJSON: String?, val completion: (() -> Unit)?
    )

    // region - 宿主接入

    /**
     * 由 MainActivity.onCreate 调用：把路由栈宿主挂到 Activity 的内容视图上，
     * 并注册返回键处理。必须在 super.onCreate 之前调用（此时内容视图尚未建立），
     * 因此这里只准备视图，真正的挂载由 [onContentRootCreated] 完成。
     */
    fun attachNavigationContainer(activity: FragmentActivity): YYPRouterNavigationContainer {
        ensureListenerRegistered()
        val existing = container
        if (existing != null && hostActivity === activity) return existing
        if (existing != null) {
            // Activity 重建（finish 后再次启动 / 进程复用）：旧容器绑定的 FragmentManager
            // 与父视图均已失效，直接复用会因「child already has a parent」崩溃。
            // 重置路由栈状态后挂新容器；页面由新的注册流程（MainActivity / RN 重挂载）重建。
            resetForNewHost()
        }
        hostActivity = activity
        val created = YYPRouterNavigationContainer(activity)
        created.onStackChanged = { handleStackChanged() }
        container = created
        return created
    }

    @Synchronized
    private fun resetForNewHost() {
        pendingCommands.clear()
        pendingPushContexts.clear()
        pendingPushCompletion = null
        pendingPopCompletion = null
        pendingPopTargetCount = -1
        pendingRootView = null
        pendingRootRouteID = null
        pendingRootRoutePath = null
        activeRouteIDs.clear()
    }

    val activeContainer: YYPRouterNavigationContainer? get() = container

    private fun ensureListenerRegistered() {
        if (listenerRegistered) return
        listenerRegistered = true
        YYPNavEventBus.addListener { event -> onEvent(event) }
    }

    // endregion

    // region - 注册

    /** 注册原生页面。与 iOS registerScreen(_:module:providerClass:) 对应。 */
    fun registerNativeScreen(
        routePath: String,
        module: String,
        createNativeView: (android.content.Context, String?) -> View,
        schemaJSON: String? = null,
        title: String? = null,
    ) {
        ensureListenerRegistered()
        val isRoot = routePath == YYPRouterConstants.ROOT_INDEX_PATH
        registrations[key(module, routePath)] = Registration(
            routePath = routePath,
            module = module,
            platform = YYPRouterPlatform.ANDROID_NATIVE,
            schema = parseSchema(schemaJSON),
            createNativeView = createNativeView,
            isRoot = isRoot,
            title = title,
        )
        if (isRoot) {
            val context = hostActivity ?: container?.context ?: return
            val view = createNativeView(context, null)
            setRootView(view, module, routePath)
        }
    }

    /** 注册 RN 页面（由 JS 通过 YYPNavigationModule 调用）。与 iOS registerScreen(platform:.reactNative) 对应。 */
    fun registerScreen(routePath: String, module: String, schemaJSON: String?) {
        ensureListenerRegistered()
        val isRoot = routePath == YYPRouterConstants.ROOT_INDEX_PATH
        registrations[key(module, routePath)] = Registration(
            routePath = routePath,
            module = module,
            platform = YYPRouterPlatform.REACT_NATIVE,
            schema = parseSchema(schemaJSON),
            createNativeView = null,
            isRoot = isRoot,
        )
    }

    private fun key(module: String, routePath: String) = "$module/$routePath"

    private fun parseSchema(json: String?): JsonSchema? {
        if (json.isNullOrBlank()) return null
        return try {
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json)
        } catch (e: Exception) {
            null
        }
    }

    // endregion

    // region - 路由栈

    val currentStack: List<YYPRouterScreenFragment>
        get() = container?.fragments.orEmpty()

    fun hasPendingOperations(): Boolean =
        pendingCommands.isNotEmpty() || pendingPushContexts.isNotEmpty()

    @Synchronized
    fun pushRoute(
        routePath: String,
        module: String,
        paramsJSON: String?,
        completion: (() -> Unit)? = null,
    ) {
        if (routePath == YYPRouterConstants.ROOT_INDEX_PATH) {
            throw IllegalStateException(
                "cannot push __index__ as a normal route; Root page is mounted only via startup")
        }
        val host = container ?: throw IllegalStateException("Router container is not attached")
        val registration = registrations[key(module, routePath)]
            ?: throw IllegalStateException("No registration: $module/$routePath")
        validateParams(registration.schema, paramsJSON)

        when (registration.platform) {
            YYPRouterPlatform.ANDROID_NATIVE -> {
                val context = hostActivity ?: host.context
                val view = registration.createNativeView?.invoke(context, paramsJSON)
                    ?: throw IllegalStateException(
                        "No native view factory for $module/$routePath")
                val id = UUID.randomUUID()
                // 原生页面视图同步可用，Promise 等新 Fragment onResume（转场结束）再 resolve
                pendingPushCompletion = completion
                host.push(view, id.toString(), routePath, registration.title)
            }

            YYPRouterPlatform.REACT_NATIVE -> {
                val id = UUID.randomUUID()
                // completion 存在 context 里，等 Fabric mount → 入栈 → onResume 后统一 resolve
                pendingPushContexts[id] = PendingPushContext(
                    id = id,
                    routePath = routePath,
                    module = module,
                    paramsJSON = paramsJSON,
                    completion = completion,
                )
                YYPNavEventBus.post(
                    YYPNavEvent(
                        YYPRouterConstants.EVENT_TYPE_PUSH_ROUTE,
                        mapOf(
                            YYPRouterConstants.KEY_TYPE to YYPRouterConstants.EVENT_TYPE_PUSH_ROUTE,
                            YYPRouterConstants.KEY_ROUTE_ID to id.toString(),
                            YYPRouterConstants.KEY_ROUTE_PATH to routePath,
                            YYPRouterConstants.KEY_MODULE to module,
                            YYPRouterConstants.KEY_PARAMS to paramsJSON,
                        ),
                    ),
                )
            }
        }
    }

    @Synchronized
    fun popRoute(completion: (() -> Unit)? = null) {
        val host = container ?: throw IllegalStateException("Router container is not attached")
        if (host.viewCount <= 1 || host.viewCount == 0) {
            completion?.invoke()
            return
        }
        pendingPopCompletion = completion
        pendingPopTargetCount = host.viewCount - 1
        host.pop()
    }

    @Synchronized
    private fun handleStackChanged() {
        syncActiveRouteIDs()
        flushPendingCommands()
    }

    private fun syncActiveRouteIDs() {
        val currentIDs = container?.fragments.orEmpty().mapNotNull { it.routeID }.toSet()
        activeRouteIDs.retainAll(currentIDs)
        activeRouteIDs.addAll(currentIDs)
    }

    /** Fragment onDestroy（被 pop 掉）时调用：通知 JS 并同步路由状态。 */
    @Synchronized
    fun onScreenFragmentDestroyed(fragment: YYPRouterScreenFragment) {
        if (fragment.routeID != null) {
            activeRouteIDs.remove(fragment.routeID)
        }
        resolvePopIfNeeded()
    }

    /** Fragment 已 onResume（转场动画结束）时调用：push 的 Promise 在这里 resolve。 */
    @Synchronized
    fun onScreenFragmentAppeared(fragment: YYPRouterScreenFragment) {
        if (fragment.routeID != null) {
            activeRouteIDs.add(fragment.routeID!!)
        }
        // 只有栈顶 Fragment 的 onResume 才代表本次 push 的转场结束
        if (container?.topFragment === fragment) {
            resolvePushIfNeeded()
        }
    }

    private fun resolvePushIfNeeded() {
        val completion = pendingPushCompletion ?: return
        pendingPushCompletion = null
        completion()
    }

    private fun resolvePopIfNeeded() {
        val completion = pendingPopCompletion ?: return
        val currentCount = container?.viewCount ?: return
        if (pendingPopTargetCount >= 0 && currentCount <= pendingPopTargetCount) {
            pendingPopCompletion = null
            pendingPopTargetCount = -1
            completion()
        }
    }

    // endregion

    // region - Root

    private fun setRootView(view: View, routeID: String?, routePath: String) {
        val host = container
        if (host == null) {
            pendingRootView = view
            pendingRootRouteID = routeID
            pendingRootRoutePath = routePath
            return
        }
        host.setRoot(view, routeID, routePath)
        if (routeID != null) {
            activeRouteIDs.add(routeID)
        }
    }

    private fun flushRootIfPending() {
        val view = pendingRootView ?: return
        val routePath = pendingRootRoutePath ?: return
        pendingRootView = null
        pendingRootRoutePath = null
        val routeID = pendingRootRouteID
        pendingRootRouteID = null
        setRootView(view, routeID, routePath)
    }

    // endregion

    // region - Fabric 事件

    private fun onEvent(event: YYPNavEvent) {
        when (event.type) {
            YYPRouterConstants.EVENT_TYPE_SCREEN_DID_MOUNT -> {
                val routePath = event.userInfo[YYPRouterConstants.KEY_ROUTE_PATH] as? String
                if (routePath == YYPRouterConstants.ROOT_INDEX_PATH) {
                    handleRootScreenDidMount(event)
                } else {
                    handleScreenDidMount(event)
                }
            }

            YYPRouterConstants.EVENT_TYPE_FABRIC_TRANSACTION_DID_MOUNT -> {
                fabricReady = true
                flushPendingCommands()
            }
        }
    }

    /**
     * RN Root 页 mount（HybridNavigationRoot 里 routeID == module 名）。
     * 与 iOS handleRootScreenDidMount 对齐：已存在原生 Root 时不覆盖。
     */
    @Synchronized
    private fun handleRootScreenDidMount(event: YYPNavEvent) {
        val moduleKey = event.userInfo[YYPRouterConstants.KEY_ROUTE_ID] as? String ?: return
        val view = event.userInfo[YYPRouterConstants.KEY_CONTROLLER] as? View ?: return
        val host = container ?: return
        if (host.viewCount > 0) {
            // 原生 Root 已接管，忽略 RN Root mount
            (view as? HybridScreenView)?.detachFromCurrentParent()
            return
        }
        setRootView(view, moduleKey, YYPRouterConstants.ROOT_INDEX_PATH)
    }

    /**
     * RN 页面 mount（Fabric 已渲染 HybridScreenView）。
     * 与 iOS onEvent 的 ScreenDidMount 分支对齐：用 routeID 从 pendingPushContexts
     * 取出本次 push 的上下文，然后把视图交给 FragmentManager 入栈。
     */
    @Synchronized
    private fun handleScreenDidMount(event: YYPNavEvent) {
        val routeIDString = event.userInfo[YYPRouterConstants.KEY_ROUTE_ID] as? String
        val view = event.userInfo[YYPRouterConstants.KEY_CONTROLLER] as? View
        if (routeIDString.isNullOrEmpty() || view == null) return
        val routeID = try {
            UUID.fromString(routeIDString)
        } catch (e: Exception) {
            return
        }
        val context = pendingPushContexts.remove(routeID) ?: return
        if (activeRouteIDs.contains(routeIDString)) return

        // RN 页面：completion 从 pending context 转存到 push 完成队列
        pendingPushCompletion = context.completion

        pendingCommands.addLast {
            container?.push(view, routeIDString, context.routePath)
        }
        flushPendingCommands()
    }

    @Synchronized
    private fun flushPendingCommands() {
        val host = container
        if (host == null || !fabricReady) return
        while (pendingCommands.isNotEmpty()) {
            pendingCommands.removeFirst().invoke()
        }
    }

    // endregion

    // region - 参数校验

    private fun validateParams(schema: JsonSchema?, paramsJSON: String?) {
        if (schema == null) return
        val instance = try {
            objectMapper.readTree(paramsJSON ?: "{}")
        } catch (e: Exception) {
            throw IllegalStateException("Invalid params JSON: ${e.message}")
        }
        val errors = schema.validate(instance)
        if (!errors.isNullOrEmpty()) {
            throw IllegalStateException(
                "Params validation failed: ${errors.joinToString { it.message }}")
        }
    }

    // endregion

    /** 供 YYPNavigationModule 判断是否需要挂到某个 Activity。 */
    fun activityOrNull(): Activity? = hostActivity
}
