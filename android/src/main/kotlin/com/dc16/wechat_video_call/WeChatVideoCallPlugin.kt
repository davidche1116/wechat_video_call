package com.dc16.wechat_video_call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.dc16.wechat_video_call.a11y.WeChatCallService
import com.dc16.wechat_video_call.config.CallConfig
import com.dc16.wechat_video_call.config.ConfigStore
import com.dc16.wechat_video_call.config.ErrorCodes
import com.dc16.wechat_video_call.config.StepCoord
import com.dc16.wechat_video_call.config.StepIds
import com.dc16.wechat_video_call.overlay.CalibrationOverlayService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/** Flutter plugin entry: MethodChannel + EventChannel. */
class WeChatVideoCallPlugin :
    FlutterPlugin,
    MethodChannel.MethodCallHandler,
    ActivityAware,
    EventChannel.StreamHandler {
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var context: Context? = null
    private var activityBinding: ActivityPluginBinding? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        methodChannel = MethodChannel(binding.binaryMessenger, "wechat_video_call")
        methodChannel.setMethodCallHandler(this)
        eventChannel = EventChannel(binding.binaryMessenger, "wechat_video_call/events")
        eventChannel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        context = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        activityBinding = null
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        PluginEventBus.setSink(events)
    }

    override fun onCancel(arguments: Any?) {
        PluginEventBus.setSink(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val ctx = context
        if (ctx == null) {
            result.error(ErrorCodes.SERVICE_NOT_CONNECTED, "plugin context null", null)
            return
        }
        when (call.method) {
            "getPlatformVersion" -> result.success("Android ${Build.VERSION.RELEASE}")
            "getPermissionStatus" -> {
                result.success(
                    mapOf(
                        "accessibility" to isAccessibilityEnabled(),
                        "overlay" to isOverlayPermissionEnabled(),
                        "wechatInstalled" to isWeChatInstalled(ctx),
                        "calibrationActive" to PluginEventBus.calibrationActive,
                    ),
                )
            }
            "isAccessibilityPermissionEnabled" -> result.success(isAccessibilityEnabled())
            "requestAccessibilityPermission" -> requestAccessibility(result)
            "isOverlayPermissionEnabled" -> result.success(isOverlayPermissionEnabled())
            "requestOverlayPermission" -> requestOverlay(result)
            "isWeChatInstalled" -> result.success(isWeChatInstalled(ctx))
            "openWeChat" -> result.success(openWeChatPackage(ctx))
            "isCalibrationActive" -> result.success(PluginEventBus.calibrationActive)
            "openCalibrationWizard" -> openCalibration(ctx, result)
            "stopCalibration" -> {
                val i = Intent(ctx, CalibrationOverlayService::class.java).apply {
                    action = CalibrationOverlayService.ACTION_STOP
                }
                runCatching { ctx.startService(i) }
                result.success(true)
            }
            "getCalibrationProgress" -> {
                val store = ConfigStore(ctx)
                result.success(HashMap(store.progress(PluginEventBus.lastCalibrationStepId)))
            }
            "loadConfig" -> {
                val cfg = ConfigStore(ctx).load()
                if (cfg == null) result.success(null)
                else result.success(jsonToMap(cfg.toJson().toString()))
            }
            "importConfig" -> importConfig(ctx, call, result)
            "resetConfig" -> result.success(ConfigStore(ctx).reset())
            "resetStep" -> {
                val stepId = call.argument<String>("stepId")
                if (stepId.isNullOrBlank()) {
                    result.error(ErrorCodes.INVALID_ARGUMENT, "stepId required", null)
                    return
                }
                val store = ConfigStore(ctx)
                val cfg = store.loadOrInit()
                result.success(store.save(cfg.clearCoord(stepId)))
            }
            "setCoordinate" -> setCoordinate(ctx, call, result)
            "videoCall" -> startCall(ctx, call, result, video = true)
            "voiceCall" -> startCall(ctx, call, result, video = false)
            "cancel" -> {
                val svc = WeChatCallService.instance
                if (svc == null) {
                    PluginEventBus.emitFailed(
                        ErrorCodes.SERVICE_NOT_CONNECTED,
                        null,
                        "无障碍服务未连接",
                    )
                    result.success(false)
                } else {
                    svc.cancelSession(emitUserCancel = true)
                    result.success(true)
                }
            }
            "hangUp" -> {
                val svc = WeChatCallService.instance
                if (svc == null) {
                    PluginEventBus.emitFailed(
                        ErrorCodes.SERVICE_NOT_CONNECTED,
                        null,
                        "无障碍服务未连接",
                    )
                    result.success(false)
                } else result.success(svc.hangUpNow())
            }
            "debugTapStep" -> {
                val stepId = call.argument<String>("stepId")
                val svc = WeChatCallService.instance
                if (stepId.isNullOrBlank() || svc == null) {
                    result.success(false)
                } else {
                    // Bring WeChat up, then tap after a short delay so it's foreground.
                    openWeChatPackage(ctx)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        { svc.debugTap(stepId) },
                        900L,
                    )
                    result.success(true)
                }
            }
            "exportConfig" -> result.success(ConfigStore(ctx).exportJson())
            else -> result.notImplemented()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val ctx = context ?: return false
        val enabled = Settings.Secure.getInt(
            ctx.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) return false
        val expected = "${ctx.packageName}/com.dc16.wechat_video_call.a11y.WeChatCallService"
        val enabledServices = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return WeChatCallService.instance != null
        return enabledServices.split(':').any {
            it.equals(expected, ignoreCase = true)
        } || WeChatCallService.instance != null
    }

    private fun isOverlayPermissionEnabled(): Boolean {
        val ctx = context ?: return false
        return Settings.canDrawOverlays(ctx)
    }

    private fun isWeChatInstalled(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(WeChatCallService.WECHAT_PKG, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun requestAccessibility(result: MethodChannel.Result) {
        val ctx = context ?: run {
            result.success(false)
            return
        }
        if (isAccessibilityEnabled()) {
            result.success(true)
            return
        }
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityBinding?.activity?.startActivity(intent)
                ?: ctx.startActivity(intent)
            PluginEventBus.emit(
                "permissionStatus",
                mapOf("accessibility" to false, "requested" to true),
            )
            result.success(false)
        } catch (e: Exception) {
            result.error(ErrorCodes.ACCESSIBILITY_DISABLED, e.message, null)
        }
    }

    /**
     * Opens the system overlay-settings page and returns immediately.
     *
     * Like [requestAccessibility], the Future resolves to the *current* grant
     * state (false when we only navigated to settings). Hosts should re-query
     * [isOverlayPermissionEnabled] on `AppLifecycleState.resumed`.
     */
    private fun requestOverlay(result: MethodChannel.Result) {
        val ctx = context ?: run {
            result.success(false)
            return
        }
        if (isOverlayPermissionEnabled()) {
            result.success(true)
            return
        }
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            activityBinding?.activity?.startActivity(intent)
                ?: ctx.startActivity(intent)
            PluginEventBus.emit(
                "permissionStatus",
                mapOf("overlay" to false, "requested" to true),
            )
            result.success(false)
        } catch (e: Exception) {
            result.error(ErrorCodes.OVERLAY_DISABLED, e.message, null)
        }
    }

    private fun openWeChatPackage(ctx: Context): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(
                    WeChatCallService.WECHAT_PKG,
                    "com.tencent.mm.ui.LauncherUI",
                )
                setPackage(WeChatCallService.WECHAT_PKG)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = ctx.packageManager
                    .getLaunchIntentForPackage(WeChatCallService.WECHAT_PKG)
                if (fallback == null) return false
                fallback.setPackage(WeChatCallService.WECHAT_PKG)
                fallback.component = android.content.ComponentName(
                    WeChatCallService.WECHAT_PKG,
                    "com.tencent.mm.ui.LauncherUI",
                )
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun importConfig(ctx: Context, call: MethodCall, result: MethodChannel.Result) {
        val json = call.argument<String>("json")
        if (json.isNullOrBlank()) {
            result.error(ErrorCodes.INVALID_ARGUMENT, "json required", null)
            return
        }
        try {
            val obj = org.json.JSONObject(json)
            val schema = obj.optInt("schemaVersion", 0)
            if (schema != 2) {
                result.error(ErrorCodes.INVALID_ARGUMENT, "unsupported schemaVersion=$schema", null)
                return
            }
            val stepsArr = obj.optJSONArray("steps")
            if (stepsArr == null || stepsArr.length() == 0) {
                result.error(ErrorCodes.CONFIG_MISSING, "steps empty", null)
                return
            }
            val config = com.dc16.wechat_video_call.config.CallConfig.fromJson(obj)
            val missingVideo = CallConfig.videoRequiredIds().filter { !config.hasCoord(it) }
            val missingVoice = CallConfig.voiceRequiredIds().filter { !config.hasCoord(it) }
            // Allow import even if incomplete; host can continue calibrating.
            val store = ConfigStore(ctx)
            val ok = store.save(config)
            PluginEventBus.emit(
                "calibrationProgress",
                store.progress(null) + mapOf(
                    "imported" to ok,
                    "missingVideo" to missingVideo,
                    "missingVoice" to missingVoice,
                ),
            )
            result.success(ok)
        } catch (e: Exception) {
            result.error(ErrorCodes.INVALID_ARGUMENT, e.message, null)
        }
    }

    private fun openCalibration(ctx: Context, result: MethodChannel.Result) {
        if (!isOverlayPermissionEnabled()) {
            PluginEventBus.emitFailed(
                ErrorCodes.OVERLAY_DISABLED,
                null,
                "需要悬浮窗权限",
            )
            result.success(false)
            return
        }
        requestPostNotificationsIfNeeded()
        val i = Intent(ctx, CalibrationOverlayService::class.java).apply {
            action = CalibrationOverlayService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
            result.success(true)
        } catch (e: Exception) {
            val fallbackOk = runCatching { ctx.startService(i) }.isSuccess
            if (fallbackOk) {
                result.success(true)
            } else {
                PluginEventBus.emitFailed(
                    ErrorCodes.SERVICE_NOT_CONNECTED,
                    null,
                    "无法启动校准悬浮窗: ${e.message}",
                )
                result.success(false)
            }
        }
    }

    /** Android 13+ requires a runtime grant before the FGS notification is visible. */
    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val activity = activityBinding?.activity ?: return
        runCatching {
            activity.requestPermissions(
                arrayOf("android.permission.POST_NOTIFICATIONS"),
                REQ_POST_NOTIFICATIONS,
            )
        }
    }

    private fun setCoordinate(ctx: Context, call: MethodCall, result: MethodChannel.Result) {
        val key = call.argument<String>("key")
        val fx = call.argument<Double>("fx")
        val fy = call.argument<Double>("fy")
        if (key.isNullOrBlank() || fx == null || fy == null) {
            result.error(ErrorCodes.INVALID_ARGUMENT, "key/fx/fy required", null)
            return
        }
        if (fx !in 0.0..1.0 || fy !in 0.0..1.0) {
            result.error(ErrorCodes.INVALID_ARGUMENT, "fx/fy must be 0..1", null)
            return
        }
        val store = ConfigStore(ctx)
        val existing = store.load()
        val base = existing ?: store.loadOrInit()
        val stepId = StepIds.mapLegacyKey(key)
        val next = base.upsertCoord(
            stepId,
            StepCoord(fx = fx, fy = fy, fySpace = "fullscreen"),
        )
        result.success(store.save(next))
    }

    private fun startCall(
        ctx: Context,
        call: MethodCall,
        result: MethodChannel.Result,
        video: Boolean,
    ) {
        val name = call.argument<String>("name") ?: ""
        if (name.isBlank()) {
            result.error(ErrorCodes.BLANK_NAME, "name is blank", null)
            return
        }
        val toast = call.argument<Boolean>("toast") ?: true
        val delayScale = call.argument<Double>("delayScale") ?: 1.0
        val pauseAfterStepMs = call.argument<Int>("pauseAfterStepMs") ?: 0
        val svc = WeChatCallService.instance
        if (svc == null) {
            PluginEventBus.emitFailed(
                ErrorCodes.SERVICE_NOT_CONNECTED,
                null,
                "无障碍服务未连接，请先开启",
            )
            result.success(false)
            return
        }
        val started = svc.startCall(
            name = name,
            video = video,
            toast = toast,
            delayScale = delayScale,
            pauseAfterStepMs = pauseAfterStepMs,
        )
        result.success(started)
    }

    private fun jsonToMap(json: String): Map<String, Any?> {
        return jsonObjectToMap(org.json.JSONObject(json))
    }

    private fun jsonObjectToMap(obj: org.json.JSONObject): Map<String, Any?> {
        val map = HashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.get(k)
            map[k] = when (v) {
                is org.json.JSONObject -> jsonObjectToMap(v)
                is org.json.JSONArray -> jsonArrayToList(v)
                org.json.JSONObject.NULL -> null
                else -> v
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: org.json.JSONArray): List<Any?> {
        val list = ArrayList<Any?>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.get(i)
            list.add(
                when (v) {
                    is org.json.JSONObject -> jsonObjectToMap(v)
                    is org.json.JSONArray -> jsonArrayToList(v)
                    org.json.JSONObject.NULL -> null
                    else -> v
                },
            )
        }
        return list
    }

    companion object {
        private const val REQ_POST_NOTIFICATIONS = 0x5745
    }
}
