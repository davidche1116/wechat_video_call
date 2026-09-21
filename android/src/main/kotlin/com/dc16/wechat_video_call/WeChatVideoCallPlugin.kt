package com.dc16.wechat_video_call

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils.SimpleStringSplitter
import android.widget.Toast
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/** WeChatVideoCallPlugin */
class WeChatVideoCallPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener {

    private var channel: MethodChannel? = null
    private var activity: Activity? = null
    private lateinit var context: Context
    private val requestAccessibilityCode = 167
    private var pendingAccessibilityResult: Result? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "wechat_video_call").also {
            it.setMethodCallHandler(this)
        }
        context = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "requestAccessibilityPermission" -> requestAccessibilityPermission(result)
            "isAccessibilityPermissionEnabled" ->
                result.success(isAccessibilitySettingsOn(context))
            "videoCall" -> {
                val name = call.argument<String>("name")
                val pinyin = call.argument<String>("pinyin")
                val video = call.argument<Boolean>("video")
                val toast = call.argument<Boolean>("toast")
                val delayScale = call.argument<Double>("delayScale")
                val pauseAfterStepMs = call.argument<Int>("pauseAfterStepMs")
                if (delayScale != null) {
                    WeChatData.delayScale = delayScale.toFloat().coerceIn(0.5f, 10f)
                }
                if (pauseAfterStepMs != null) {
                    WeChatData.pauseAfterStepMs = pauseAfterStepMs.toLong().coerceIn(0L, 15_000L)
                }
                if (name == null || video == null || toast == null) {
                    result.error("ERROR", "invalid parameter.", null)
                } else {
                    result.success(videoCall(name, pinyin, video, toast))
                }
            }
            "cancel" -> {
                WeChatAccessibility.instance?.clearPendingWork()
                WeChatData.cancel()
                result.success(true)
            }
            "hangUp" -> {
                val ok = WeChatAccessibility.instance?.requestHangUp() ?: false
                result.success(ok)
            }
            "setCoordinate" -> {
                val key = call.argument<String>("key")
                val fx = call.argument<Double>("fx")
                val fy = call.argument<Double>("fy")
                if (key == null || fx == null || fy == null) {
                    result.error("ERROR", "invalid parameter.", null)
                } else {
                    result.success(WeChatData.setCustomCoord(key, fx.toFloat(), fy.toFloat()))
                }
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
        finishPendingAccessibility(false)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != requestAccessibilityCode) return false
        val pending = pendingAccessibilityResult ?: return true
        pendingAccessibilityResult = null
        when (resultCode) {
            Activity.RESULT_OK -> pending.success(true)
            Activity.RESULT_CANCELED -> pending.success(isAccessibilitySettingsOn(context))
            else -> pending.success(false)
        }
        return true
    }

    private fun requestAccessibilityPermission(result: Result) {
        val act = activity
        if (act == null) {
            result.success(isAccessibilitySettingsOn(context))
            return
        }
        finishPendingAccessibility(isAccessibilitySettingsOn(context))
        pendingAccessibilityResult = result
        try {
            act.startActivityForResult(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                requestAccessibilityCode,
            )
        } catch (e: Exception) {
            finishPendingAccessibility(isAccessibilitySettingsOn(context))
        }
    }

    private fun finishPendingAccessibility(value: Boolean) {
        val pending = pendingAccessibilityResult ?: return
        pendingAccessibilityResult = null
        pending.success(value)
    }

    fun isAccessibilitySettingsOn(ctx: Context): Boolean {
        val service = ctx.packageName + "/" + WeChatAccessibility::class.java.canonicalName
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                ctx.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
            )
        } catch (e: Settings.SettingNotFoundException) {
            return false
        }
        if (accessibilityEnabled != 1) return false
        val settingValue = Settings.Secure.getString(
            ctx.applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = SimpleStringSplitter(':')
        splitter.setString(settingValue)
        while (splitter.hasNext()) {
            if (splitter.next().equals(service, true)) return true
        }
        return false
    }

    private fun isWeChatInstalled(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo("com.tencent.mm", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun videoCall(
        name: String,
        pinyin: String?,
        video: Boolean,
        toast: Boolean,
    ): Boolean {
        val query = name.trim()
        if (query.isBlank()) {
            android.util.Log.w(WeChatAccessibility.TAG, "videoCall: blank name")
            return false
        }
        if (!isAccessibilitySettingsOn(context)) {
            android.util.Log.w(WeChatAccessibility.TAG, "videoCall: accessibility service not enabled")
            return false
        }
        if (!isWeChatInstalled(context)) {
            android.util.Log.w(WeChatAccessibility.TAG, "videoCall: WeChat not installed")
            return false
        }
        val typing = (pinyin ?: "").trim().lowercase().ifEmpty { query.lowercase() }
        if (toast) {
            Toast.makeText(context, query, Toast.LENGTH_SHORT).show()
        }
        android.util.Log.i(
            WeChatAccessibility.TAG,
            "videoCall query=[$query] typing=[$typing] video=$video",
        )
        // 会话开始即写入剪贴板，进搜索页时粘贴源已就绪
        WeChatData.startSession(query, typing, video)
        WeChatAccessibility.instance?.prepareNewSession(query)
        return try {
            val intent = Intent().apply {
                // 必须让微信到前台，否则 dispatchGesture 会点到调用方 App
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e(WeChatAccessibility.TAG, "videoCall: launch WeChat failed: ${e.message}")
            WeChatAccessibility.instance?.clearPendingWork()
            WeChatData.cancel()
            false
        }
    }
}
