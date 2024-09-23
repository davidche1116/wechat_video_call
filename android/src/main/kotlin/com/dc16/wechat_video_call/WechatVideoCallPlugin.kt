package com.dc16.wechat_video_call

import android.app.Activity
import android.content.BroadcastReceiver
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

/** WechatVideoCallPlugin */
class WechatVideoCallPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
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
        pendingResult = result
        if (call.method == "getPlatformVersion") {
            result.success("Android ${android.os.Build.VERSION.RELEASE}")
        } else if (call.method == "requestAccessibilityPermission") {
            val intent: Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            activity.startActivityForResult(intent, REQUEST_CODE_FOR_ACCESSIBILITY)
        } else if (call.method == "isAccessibilityPermissionEnabled") {
            result.success(isAccessibilitySettingsOn(context))
        } else if (call.method == "videoCall") {
            val name: String? = call.argument("name")
            if (name != null) {
                result.success(videoCall(name))
            } else {
                result.error("UNAVAILABLE", "WechatVideoCall not available.", null)
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
    }

    override fun onDetachedFromActivityForConfigChanges() {

    }

    override fun onReattachedToActivityForConfigChanges(p0: ActivityPluginBinding) {

    }

    override fun onDetachedFromActivity() {

    }

    private val actionsReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {
            pendingResult.success(intent.getIntegerArrayListExtra("actions"))
        }
    }

    override fun onActivityResult(p0: Int, p1: Int, p2: Intent?): Boolean {
        if (p0 == REQUEST_CODE_FOR_ACCESSIBILITY) {
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

    fun isAccessibilitySettingsOn(mContext: Context): Boolean {
        var accessibilityEnabled: Int = 0
        val service: String = mContext.packageName + "/" + WechatAccessibility::class.java.getCanonicalName()
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                mContext.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            return false
        }
        val mStringColonSplitter: SimpleStringSplitter = SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue: String? = Settings.Secure.getString(
                mContext.applicationContext.contentResolver,
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

    fun videoCall(name: String): Boolean {
        Toast.makeText(context, name, Toast.LENGTH_SHORT).show()
        WechatData.updateValue(name)
        WechatData.updateIndex(1)
        val intent = Intent()
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
        intent.setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
        activity.startActivity(intent)
        return true
    }

}
