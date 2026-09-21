package com.dc16.wechat_video_call_example

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * 示例 Activity：Intent 自动拨测（MIUI 禁了 adb input 时用）。
 *
 * 冷启动:
 *   adb shell am start -n com.dc16.wechat_video_call_example/.MainActivity \
 *     --es name 张三 --ez video true
 * 热启动: 同上，onNewIntent 会再推一次给 Dart。
 */
class MainActivity : FlutterActivity() {
    private var channel: MethodChannel? = null
    private var pendingAutoCall: MutableMap<String, Any?>? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "wechat_video_call_example/intent",
        ).also { ch ->
            ch.setMethodCallHandler { call, result ->
                if (call.method == "getAutoCall") {
                    val pending = pendingAutoCall
                    pendingAutoCall = null
                    if (pending != null) {
                        result.success(pending)
                    } else {
                        result.success(readAutoCallFromIntent(intent, consume = true))
                    }
                } else {
                    result.notImplemented()
                }
            }
            // 引擎就绪时若有未消费的 onNewIntent，立刻下发
            pendingAutoCall?.let { ch.invokeMethod("autoCall", it) }
        }
        // 冷启动路径：先尝试读当前 intent，若 Dart 先调 getAutoCall 则走 pending
        readAutoCallFromIntent(intent, consume = false)?.let {
            pendingAutoCall = it.toMutableMap()
            readAutoCallFromIntent(intent, consume = true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("hangUp", false)) {
            intent.removeExtra("hangUp")
            channel?.invokeMethod("hangUp", null)
            return
        }
        val args = readAutoCallFromIntent(intent, consume = true) ?: return
        val ch = channel
        if (ch != null) {
            ch.invokeMethod("autoCall", args)
        } else {
            pendingAutoCall = args.toMutableMap()
        }
    }

    private fun readAutoCallFromIntent(intent: Intent?, consume: Boolean): MutableMap<String, Any?>? {
        if (intent == null) return null
        val name = intent.getStringExtra("name")
        if (name.isNullOrBlank()) return null
        val video = intent.getBooleanExtra("video", true)
        val delayScale = intent.getDoubleExtra("delayScale", 1.0)
        val pauseAfterStepMs = intent.getIntExtra("pauseAfterStepMs", 0)
        if (consume) {
            intent.removeExtra("name")
            intent.removeExtra("video")
            intent.removeExtra("delayScale")
            intent.removeExtra("pauseAfterStepMs")
        }
        return mutableMapOf(
            "name" to name,
            "video" to video,
            "delayScale" to delayScale,
            "pauseAfterStepMs" to pauseAfterStepMs,
        )
    }
}
