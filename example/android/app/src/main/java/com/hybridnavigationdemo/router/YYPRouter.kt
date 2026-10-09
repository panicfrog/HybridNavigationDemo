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

private const val TAG = "YYPRouter"

/** 看门狗：动画时长 300ms，超过此窗口强制完成当前操作（防止 in-flight 卡死阻塞整个队列）。 */
private const val IN_FLIGHT_WATCHDOG_MS = 2000L

/** RN push 的 Fabric mount 超时：超过则认为挂载链路异常，清理 context 并告警。 */
private const val PUSH_MOUNT_TIMEOUT_MS = 15_000L

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

    private var container: YYPRouterNavigationContainer? = null
    private var hostActivity: FragmentActivity? = null
    private var fabricReady = false
    private var listenerRegistered = false

    /** 已激活（在栈上）的 routeID，用于去重与 pop 同步。 */
    private val activeRouteIDs = mutableSetOf<String>()

    private var pendingRootView: View? = null
    private var pendingRootRouteID: String? = null
    private var pendingRootRoutePath: String? = null

    data class PendingPushContext(
        val id: UUID, val routePath: String, val module: String,
        val paramsJSON: String?, val title: String?, val completion: (() -> Unit)?
    )

    /**
     * 统一操作队列（对齐 iOS pendingQueue）：push/pop 一律入队串行执行，
     * 当前一个转场完成（新 Fragment onResume / 旧 Fragment onDestroy）后链式执行下一项。
     * 系统返回手势在 hasPendingOperations() 时直接拒绝（对齐 iOS shouldPop 拦截），
     * JS/原生主动调用则排队等待，不丢失、completion 各自持有不互相覆盖。
     */
    private sealed class PendingOperation {
        data class Push(
            val view: View,
            val routeID: String,
            val routePath: String,
            val headerTitle: String?,
            val completion: (() -> Unit)?,
        ) : PendingOperation()

        data class Pop(val completion: (() -> Unit)?) : PendingOperation()
    }

    private val operationQueue = ArrayDeque<PendingOperation>()

    /** 当前有一个 push/pop 转场在执行、尚未完成。 */
    private var operationInFlight = false
    private var inFlightCompletion: (() -> Unit)? = null

    /** 看门狗代际：每次 resolve 递增，防止陈旧定时器提前打断后续正常转场。 */
    private var inFlightGeneration = 0

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
        operationQueue.clear()
        operationInFlight = false
        inFlightCompletion = null
        pendingPushContexts.clear()
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
    fun registerScreen(routePath: String, module: String, schemaJSON: String?, title: String? = null) {
        ensureListenerRegistered()
        val isRoot = routePath == YYPRouterConstants.ROOT_INDEX_PATH
        registrations[key(module, routePath)] = Registration(
            routePath = routePath,
            module = module,
            platform = YYPRouterPlatform.REACT_NATIVE,
            schema = parseSchema(schemaJSON),
            createNativeView = null,
            isRoot = isRoot,
            title = title,
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

    // 注意：不要提供「完整栈」API —— replace+addToBackStack 下 fragmentManager.fragments
    // 只包含当前 added 的栈顶 Fragment。栈深用 YYPRouterNavigationContainer.viewCount。

    fun hasPendingOperations(): Boolean =
        operationInFlight || operationQueue.isNotEmpty() || pendingPushContexts.isNotEmpty()

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
                // 与 iOS 对齐：始终入队，前一个转场完成后链式执行
                operationQueue.addLast(
                    PendingOperation.Push(view, id.toString(), routePath, registration.title, completion))
                flushOperations()
            }

            YYPRouterPlatform.REACT_NATIVE -> {
                val id = UUID.randomUUID()
                // completion 存在 context 里，等 Fabric mount → 入栈 → 转场完成后统一 resolve
                val pushId = id
                pendingPushContexts[pushId] = PendingPushContext(
                    id = id,
                    routePath = routePath,
                    module = module,
                    paramsJSON = paramsJSON,
                    title = registration.title,
                    completion = completion,
                )
                // 泄漏看门狗：Fabric mount 一直不来时（异常路径）告警并清理，Promise 不至于永久挂死
                mainHandler.postDelayed({
                    synchronized(this) {
                        val leaked = pendingPushContexts.remove(pushId)
                        if (leaked != null) {
                            android.util.Log.e(
                                TAG,
                                "RN push mount timeout: ${routePath} id=$pushId 的 ScreenDidMount 未到达，" +
                                    "已清理 context 并 resolve completion（页面未入栈）",
                            )
                            leaked.completion?.invoke()
                        }
                    }
                }, PUSH_MOUNT_TIMEOUT_MS)
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
        if (host.viewCount <= 1) {
            completion?.invoke()
            return
        }
        // 与 iOS 对齐：始终入队（统一管理 push/pop 顺序）
        operationQueue.addLast(PendingOperation.Pop(completion))
        flushOperations()
    }

    /** 队列空闲则执行下一项；转场中（operationInFlight）则等待完成回调链式触发。 */
    @Synchronized
    private fun flushOperations() {
        val host = container ?: return
        if (operationInFlight) return
        when (val op = operationQueue.firstOrNull() ?: return) {
            is PendingOperation.Push -> {
                operationQueue.removeFirst()
                markOperationInFlight(op.completion)
                host.push(op.view, op.routeID, op.routePath, op.headerTitle)
            }

            is PendingOperation.Pop -> {
                if (host.viewCount <= 1) {
                    // 已到栈底：立即完成并继续链（对齐 iOS 队列语义）
                    operationQueue.removeFirst()
                    op.completion?.invoke()
                    flushOperations()
                } else {
                    operationQueue.removeFirst()
                    markOperationInFlight(op.completion)
                    host.pop()
                }
            }
        }
    }

    private fun markOperationInFlight(completion: (() -> Unit)?) {
        operationInFlight = true
        inFlightCompletion = completion
        // 看门狗：动画结束信号异常丢失时强制完成，避免队列永久阻塞
        val generation = inFlightGeneration
        mainHandler.postDelayed({
            synchronized(this) {
                if (operationInFlight && inFlightGeneration == generation) {
                    resolveInFlightOperation()
                }
            }
        }, IN_FLIGHT_WATCHDOG_MS)
    }

    /** 当前转场完成：resolve 并链式执行下一项。幂等，可被多个信号安全触发。 */
    @Synchronized
    private fun resolveInFlightOperation() {
        if (!operationInFlight) return
        operationInFlight = false
        inFlightGeneration++ // 使所有在途看门狗失效
        val completion = inFlightCompletion
        inFlightCompletion = null
        completion?.invoke()
        flushOperations()
    }

    @Synchronized
    private fun handleStackChanged() {
        syncActiveRouteIDs()
        flushOperations()
    }

    private fun syncActiveRouteIDs() {
        val currentIDs = container?.fragments.orEmpty().mapNotNull { it.routeID }.toSet()
        activeRouteIDs.retainAll(currentIDs)
        activeRouteIDs.addAll(currentIDs)
    }

    /** Fragment onDestroy（被 pop 掉，退场动画已结束）时调用：通知 JS 并同步路由状态。 */
    @Synchronized
    fun onScreenFragmentDestroyed(fragment: YYPRouterScreenFragment) {
        if (fragment.routeID != null) {
            activeRouteIDs.remove(fragment.routeID)
        }
        // pop 的兜底完成信号（退场动画结束时 onDestroy 紧随其后）；
        // push 进行中被替换页面不会 onDestroy（在 back stack 里），不会误触发
        resolveInFlightOperation()
    }

    /**
     * Fragment 入场动画结束（对齐 iOS transitionCoordinator 完成时机）时调用。
     * push 看新页入场动画、pop 看露出页的 popEnter 动画，二者结束即当前转场完成。
     *
     * 只认当前栈顶 Fragment 的信号：被顶替的旧页面动画被取消时同样会回调 onAnimationEnd，
     * 那种信号会把刚入栈的 push 提前误判为「转场完成」，导致返回拦截窗口失效。
     */
    @Synchronized
    fun onScreenFragmentAnimationEnded(fragment: YYPRouterScreenFragment) {
        if (container?.topFragment === fragment) {
            resolveInFlightOperation()
        }
    }

    /** Fragment 已 onResume：仅同步 routeID。转场完成以动画结束为准（见 onCreateAnimation）。 */
    @Synchronized
    fun onScreenFragmentAppeared(fragment: YYPRouterScreenFragment) {
        if (fragment.routeID != null) {
            activeRouteIDs.add(fragment.routeID!!)
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
                flushOperations()
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

        // RN 页面：入统一操作队列，等前一个转场完成后入栈
        operationQueue.addLast(
            PendingOperation.Push(view, routeIDString, context.routePath, context.title, context.completion))
        flushOperations()
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
