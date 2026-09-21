package com.dc16.wechat_video_call

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
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
class WeChatVideoCallPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var activity: Activity
    private lateinit var context: Context
    private val REQUEST_CODE_FOR_ACCESSIBILITY: Int = 167
    private lateinit var pendingResult: Result

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "wechat_video_call")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "requestAccessibilityPermission") {
            pendingResult = result
            val intent: Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            activity.startActivityForResult(intent, REQUEST_CODE_FOR_ACCESSIBILITY)
        } else if (call.method == "isAccessibilityPermissionEnabled") {
            result.success(isAccessibilitySettingsOn(context))
        } else if (call.method == "videoCall") {
            val name: String? = call.argument("name")
            val pinyin: String? = call.argument("pinyin")
            val video: Boolean? = call.argument("video")
            val toast: Boolean? = call.argument("toast")
            if (name != null && video != null && toast != null) {
                result.success(videoCall(name, pinyin, video, toast))
            } else {
                result.error("ERROR", "invalid parameter.", null)
            }
        } else if (call.method == "cancel") {
            WeChatData.cancel()
            result.success(true)
        } else if (call.method == "hangUp") {
            val ok = WeChatAccessibility.instance?.requestHangUp() ?: false
            result.success(ok)
        } else if (call.method == "setCoordinate") {
            val key: String? = call.argument("key")
            val fx: Double? = call.argument("fx")
            val fy: Double? = call.argument("fy")
            if (key != null && fx != null && fy != null) {
                result.success(WeChatData.setCustomCoord(key, fx.toFloat(), fy.toFloat()))
            } else {
                result.error("ERROR", "invalid parameter.", null)
            }
        } else {
            result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(p0: ActivityPluginBinding) {
        activity = p0.activity
        p0.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(p0: ActivityPluginBinding) {
        onAttachedToActivity(p0)
    }

    override fun onDetachedFromActivity() {
    }

    override fun onActivityResult(p0: Int, p1: Int, p2: Intent?): Boolean {
        if (p0 == REQUEST_CODE_FOR_ACCESSIBILITY) {
            if (!this::pendingResult.isInitialized) return true
            if (p1 == Activity.RESULT_OK) {
                pendingResult.success(true)
            } else if (p1 == Activity.RESULT_CANCELED) {
                pendingResult.success(isAccessibilitySettingsOn(context))
            } else {
                pendingResult.success(false)
            }
            return true
        }
        return false
    }

    fun isAccessibilitySettingsOn(p0: Context): Boolean {
        var accessibilityEnabled: Int = 0
        val service: String = p0.packageName + "/" + WeChatAccessibility::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                p0.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            return false
        }
        val mStringColonSplitter: SimpleStringSplitter = SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue: String? = Settings.Secure.getString(
                p0.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService: String = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun videoCall(name: String, pinyin: String?, video: Boolean, toast: Boolean): Boolean {
        val query = name.trim()
        if (query.isBlank()) return false
        // 拼音为空（如纯表情昵称）则退化用原文小写；键盘方案只支持 a-z0-9
        val typing = (pinyin ?: "").trim().lowercase().ifEmpty { query.lowercase() }
        if (toast) {
            Toast.makeText(context, query, Toast.LENGTH_SHORT).show()
        }
        android.util.Log.i(
            "WechatAccessibilityTag",
            "videoCall query=[$query] typing=[$typing] video=$video"
        )
        WeChatData.startSession(query, typing, video)
        return try {
            val intent = Intent()
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
            intent.setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            // 微信未安装 / 无法启动：终止会话，避免状态机空转
            WeChatData.cancel()
            false
        }
    }
}
