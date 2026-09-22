package com.dc16.wechat_video_call.config

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Thread-safe JSON file store for [CallConfig]. */
class ConfigStore(private val context: Context) {
    private val lock = Any()

    private fun file(): File {
        val dir = File(context.filesDir, CallConfig.DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, CallConfig.FILE_NAME)
    }

    fun load(): CallConfig? {
        synchronized(lock) {
            val f = file()
            if (!f.exists()) return null
            return try {
                CallConfig.fromJson(JSONObject(f.readText()))
            } catch (_: Exception) {
                null
            }
        }
    }

    fun loadOrInit(): CallConfig {
        synchronized(lock) {
            return load() ?: CallConfig.empty(context).also { save(it) }
        }
    }

    fun save(config: CallConfig): Boolean {
        synchronized(lock) {
            return try {
                val json = config.toJson()
                json.put("updatedAt", CallConfig.nowIso())
                file().writeText(json.toString(2))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun reset(): Boolean {
        synchronized(lock) {
            return try {
                save(CallConfig.empty(context))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun exportJson(): String? {
        val config = load() ?: return null
        return config.toJson().toString(2)
    }

    fun progress(currentStepId: String? = null): Map<String, Any?> {
        val config = loadOrInit()
        val recorded = config.steps.filter { it.coord != null }.map { it.id }
        val missingVideo = CallConfig.videoRequiredIds().filter { !config.hasCoord(it) }
        val missingVoice = CallConfig.voiceRequiredIds().filter { !config.hasCoord(it) }
        return mapOf(
            "recordedStepIds" to recorded,
            "missingRequiredStepIds" to missingVideo,
            "readyForVideo" to missingVideo.isEmpty(),
            "readyForVoice" to missingVoice.isEmpty(),
            "currentStepId" to currentStepId,
        )
    }
}
