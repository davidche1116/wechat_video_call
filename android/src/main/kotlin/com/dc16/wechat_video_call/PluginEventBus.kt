package com.dc16.wechat_video_call

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel

/**
 * In-process event bus between native services and the Flutter EventChannel.
 */
object PluginEventBus {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var sink: EventChannel.EventSink? = null

    @Volatile
    var lastCalibrationStepId: String? = null

    @Volatile
    var calibrationActive: Boolean = false

    fun setSink(s: EventChannel.EventSink?) {
        sink = s
    }

    fun emit(type: String, data: Map<String, Any?> = emptyMap()) {
        val payload = HashMap<String, Any?>(data.size + 1)
        payload["type"] = type
        payload.putAll(data)
        mainHandler.post {
            sink?.success(payload)
        }
    }

    fun emitStepProgress(
        sessionId: Long,
        stepId: String,
        index: Int,
        status: String,
        message: String? = null,
    ) {
        emit(
            "stepProgress",
            mapOf(
                "sessionId" to sessionId,
                "stepId" to stepId,
                "index" to index,
                "status" to status,
                "message" to message,
            ),
        )
    }

    fun emitFailed(code: String, stepId: String?, message: String, sessionId: Long? = null) {
        emit(
            "sessionFailed",
            mapOf(
                "errorCode" to code,
                "stepId" to stepId,
                "message" to message,
                "sessionId" to sessionId,
            ),
        )
    }

    fun emitSuccess(sessionId: Long, video: Boolean) {
        emit(
            "sessionSuccess",
            mapOf(
                "sessionId" to sessionId,
                "video" to video,
                "dialedAt" to System.currentTimeMillis(),
            ),
        )
    }

    fun emitCalibration(phase: String, stepId: String?, extra: Map<String, Any?> = emptyMap()) {
        emit(
            "calibrationStep",
            mapOf(
                "phase" to phase,
                "stepId" to stepId,
            ) + extra,
        )
    }
}
