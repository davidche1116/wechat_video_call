package com.dc16.wechat_video_call.config

import android.content.Context
import android.os.Build
import com.dc16.wechat_video_call.display.WeChatDisplay
import org.json.JSONArray
import org.json.JSONObject

data class StepCoord(
    val fx: Double,
    val fy: Double,
    val fySpace: String = "fullscreen",
    val pixelX: Int? = null,
    val pixelY: Int? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val density: Double? = null,
    val statusBarPx: Int? = null,
    val navigationBarPx: Int? = null,
    val rotation: Int? = null,
    val recordedAt: String? = null,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("mode", "relative")
            put("fx", fx)
            put("fy", fy)
            put("fySpace", fySpace)
            if (pixelX != null && pixelY != null) {
                put("pixelAtRecord", JSONObject().put("x", pixelX).put("y", pixelY))
            }
            if (widthPx != null) {
                put(
                    "displayAtRecord",
                    JSONObject().apply {
                        put("widthPx", widthPx)
                        put("heightPx", heightPx)
                        put("density", density)
                        put("statusBarPx", statusBarPx)
                        put("navigationBarPx", navigationBarPx)
                        put("rotation", rotation)
                    },
                )
            }
            if (recordedAt != null) put("recordedAt", recordedAt)
            put("confidence", "user")
        }
    }

    companion object {
        fun fromJson(o: JSONObject?): StepCoord? {
            if (o == null || !o.has("fx") || !o.has("fy")) return null
            val pixel = o.optJSONObject("pixelAtRecord")
            val display = o.optJSONObject("displayAtRecord")
            return StepCoord(
                fx = o.optDouble("fx"),
                fy = o.optDouble("fy"),
                fySpace = o.optString("fySpace", "fullscreen"),
                pixelX = pixel?.optInt("x"),
                pixelY = pixel?.optInt("y"),
                widthPx = display?.optInt("widthPx"),
                heightPx = display?.optInt("heightPx"),
                density = display?.optDouble("density"),
                statusBarPx = display?.optInt("statusBarPx"),
                navigationBarPx = display?.optInt("navigationBarPx"),
                rotation = display?.optInt("rotation"),
                recordedAt = if (o.has("recordedAt")) o.optString("recordedAt") else null,
            )
        }
    }
}

data class CallStep(
    val id: String,
    val action: String,
    val required: Boolean,
    val delayAfterMs: Int,
    val durationMs: Int? = null,
    val coord: StepCoord? = null,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("action", action)
            put("required", required)
            put("delayAfterMs", delayAfterMs)
            if (durationMs != null) put("durationMs", durationMs)
            if (coord != null) put("coord", coord.toJson())
        }
    }

    companion object {
        fun fromJson(o: JSONObject): CallStep {
            return CallStep(
                id = o.optString("id"),
                action = o.optString("action", "tap"),
                required = o.optBoolean("required", false),
                delayAfterMs = o.optInt("delayAfterMs", 400),
                durationMs = if (o.has("durationMs")) o.optInt("durationMs") else null,
                coord = StepCoord.fromJson(o.optJSONObject("coord")),
            )
        }
    }
}

data class CallTiming(
    val sessionTimeoutMs: Int = 45_000,
    val stepCooldownMs: Int = 1_500,
    /** Fallback when a step has no explicit delay. */
    val defaultDelayAfterMs: Int = 400,
    val delayScale: Double = 1.0,
    val pauseAfterStepMs: Int = 0,
    val searchResultDelayMs: Int = 1_000,
    val plusButtonDelayMs: Int = 800,
    val videoMenuDelayMs: Int = 600,
    val confirmDelayMs: Int = 300,
    val longPressDurationMs: Int = 600,
    /** Wait after openWeChat before the first gesture. */
    val launchSettleMs: Int = 500,
) {
    /** Absolute floor / ceiling for any post-step wait (P0 speed budget). */
    fun clampDelay(ms: Int): Int = ms.coerceIn(MIN_STEP_DELAY_MS, MAX_STEP_DELAY_MS)

    fun scaled(ms: Int, scale: Double): Long {
        val s = if (scale > 0) scale else 1.0
        val clamped = clampDelay(ms)
        return (clamped * s).toLong().coerceAtLeast(0L) + pauseAfterStepMs.toLong()
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("sessionTimeoutMs", sessionTimeoutMs)
            put("stepCooldownMs", stepCooldownMs)
            put("defaultDelayAfterMs", defaultDelayAfterMs)
            put("delayScale", delayScale)
            put("pauseAfterStepMs", pauseAfterStepMs)
            put("searchResultDelayMs", searchResultDelayMs)
            put("plusButtonDelayMs", plusButtonDelayMs)
            put("videoMenuDelayMs", videoMenuDelayMs)
            put("confirmDelayMs", confirmDelayMs)
            put("longPressDurationMs", longPressDurationMs)
            put("launchSettleMs", launchSettleMs)
        }
    }

    companion object {
        /** Fastest post-step wait (ms). */
        const val MIN_STEP_DELAY_MS = 200

        /** Hard cap so P0 stays snappy; tune via delayScale for slow devices. */
        const val MAX_STEP_DELAY_MS = 1_000

        /**
         * Minimum settle after a long-press so the system paste/context menu
         * can finish appearing. Applied even when a stored step delay is lower
         * (2.0.1 configs baked in 400ms).
         */
        const val LONG_PRESS_SETTLE_MIN_MS = 800

        fun fromJson(o: JSONObject?): CallTiming {
            if (o == null) return CallTiming()
            return CallTiming(
                sessionTimeoutMs = o.optInt("sessionTimeoutMs", 45_000),
                stepCooldownMs = o.optInt("stepCooldownMs", 1_500),
                defaultDelayAfterMs = o.optInt("defaultDelayAfterMs", 400),
                delayScale = o.optDouble("delayScale", 1.0),
                pauseAfterStepMs = o.optInt("pauseAfterStepMs", 0),
                searchResultDelayMs = o.optInt("searchResultDelayMs", 1_000),
                plusButtonDelayMs = o.optInt("plusButtonDelayMs", 800),
                videoMenuDelayMs = o.optInt("videoMenuDelayMs", 600),
                confirmDelayMs = o.optInt("confirmDelayMs", 300),
                longPressDurationMs = o.optInt("longPressDurationMs", 600),
                launchSettleMs = o.optInt("launchSettleMs", 500),
            )
        }
    }
}

data class CallConfig(
    val schemaVersion: Int = 2,
    val configId: String = "default",
    val updatedAt: String? = null,
    val deviceJson: JSONObject? = null,
    val timing: CallTiming = CallTiming(),
    val steps: List<CallStep> = emptyList(),
) {
    fun step(id: String): CallStep? = steps.find { it.id == id }

    fun coord(id: String): StepCoord? = step(id)?.coord

    fun hasCoord(id: String): Boolean = coord(id) != null

    fun upsertCoord(stepId: String, coord: StepCoord): CallConfig {
        val existing = step(stepId)
        val action = existing?.action ?: defaultAction(stepId)
        val required = existing?.required ?: defaultRequired(stepId)
        val delay = existing?.delayAfterMs ?: defaultDelay(stepId)
        val duration = existing?.durationMs
            ?: if (action == "longPress") timing.longPressDurationMs else null
        val newStep = CallStep(stepId, action, required, delay, duration, coord)
        val next = steps.filterNot { it.id == stepId } + newStep
        return copy(steps = next.sortedBy { stepOrder(it.id) }, updatedAt = nowIso())
    }

    fun clearCoord(stepId: String): CallConfig {
        val existing = step(stepId) ?: return this
        val cleared = existing.copy(coord = null)
        val next = steps.map { if (it.id == stepId) cleared else it }
        return copy(steps = next, updatedAt = nowIso())
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("configId", configId)
            if (updatedAt != null) put("updatedAt", updatedAt)
            if (deviceJson != null) put("device", deviceJson)
            put("timing", timing.toJson())
            put(
                "input",
                JSONObject()
                    .put("mode", "pasteBubbleOnly")
                    .put("clipboardWriteOnSessionStart", true),
            )
            val arr = JSONArray()
            steps.forEach { arr.put(it.toJson()) }
            put("steps", arr)
        }
    }

    companion object {
        const val FILE_NAME = "config.json"
        const val DIR_NAME = "wechat_video_call"

        fun nowIso(): String =
            java.time.Instant.now().toString()

        fun defaultAction(id: String): String = when (id) {
            StepIds.SEARCH_BOX_LONG_PRESS -> "longPress"
            StepIds.HANG_UP -> "hangUp"
            else -> "tap"
        }

        fun defaultRequired(id: String): Boolean = when (id) {
            StepIds.SEARCH_ICON,
            StepIds.SEARCH_BOX_LONG_PRESS,
            StepIds.PASTE_BUBBLE,
            StepIds.SEARCH_RESULT,
            StepIds.PLUS_BUTTON,
            StepIds.VIDEO_MENU,
            StepIds.VIDEO_CONFIRM,
            StepIds.VOICE_CONFIRM,
            -> true
            else -> false
        }

        /**
         * Default post-step waits (ms). Budget: 200–1000ms only —
         * heavier UI transitions get more, never above 1000.
         * Long-press needs extra settle so the paste menu is actually visible.
         */
        fun defaultDelay(id: String): Int = when (id) {
            StepIds.HOME_TAB -> 200
            StepIds.SEARCH_ICON -> 300
            StepIds.SEARCH_BOX_LONG_PRESS -> CallTiming.LONG_PRESS_SETTLE_MIN_MS
            StepIds.PASTE_BUBBLE -> 800
            StepIds.SEARCH_RESULT -> 1_000
            StepIds.PLUS_BUTTON -> 800
            StepIds.VIDEO_MENU -> 600
            StepIds.VIDEO_CONFIRM -> 300
            StepIds.VOICE_CONFIRM -> 300
            StepIds.HANG_UP -> 200
            else -> 400
        }

        fun stepOrder(id: String): Int = when (id) {
            StepIds.HOME_TAB -> 1
            StepIds.SEARCH_ICON -> 2
            StepIds.SEARCH_BOX -> 3
            StepIds.SEARCH_BOX_LONG_PRESS -> 4
            StepIds.PASTE_BUBBLE -> 5
            StepIds.SEARCH_RESULT -> 6
            StepIds.PLUS_BUTTON -> 7
            StepIds.VIDEO_MENU -> 8
            StepIds.VIDEO_CONFIRM -> 9
            StepIds.VOICE_CONFIRM -> 10
            StepIds.CALL_CANCEL -> 11
            StepIds.HANG_UP -> 12
            else -> 99
        }

        fun fromJson(o: JSONObject): CallConfig {
            val stepsArr = o.optJSONArray("steps")
            val steps = mutableListOf<CallStep>()
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    val item = stepsArr.optJSONObject(i) ?: continue
                    steps.add(CallStep.fromJson(item))
                }
            }
            return CallConfig(
                schemaVersion = o.optInt("schemaVersion", 2),
                configId = o.optString("configId", "default"),
                updatedAt = if (o.has("updatedAt")) o.optString("updatedAt") else null,
                deviceJson = o.optJSONObject("device"),
                timing = CallTiming.fromJson(o.optJSONObject("timing")),
                steps = steps,
            )
        }

        fun empty(context: Context): CallConfig {
            val snap = WeChatDisplay.snapshot(context)
            val device = JSONObject().apply {
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("androidVersion", Build.VERSION.RELEASE)
                put("apiLevel", Build.VERSION.SDK_INT)
                put("widthPx", snap.widthPx)
                put("heightPx", snap.heightPx)
                put("density", snap.density.toDouble())
                put("statusBarPx", snap.statusBarPx)
                put("navigationBarPx", snap.navigationBarPx)
                put("rotation", snap.rotation)
            }
            val timing = CallTiming()
            val steps = listOf(
                StepIds.HOME_TAB,
                StepIds.SEARCH_ICON,
                StepIds.SEARCH_BOX_LONG_PRESS,
                StepIds.PASTE_BUBBLE,
                StepIds.SEARCH_RESULT,
                StepIds.PLUS_BUTTON,
                StepIds.VIDEO_MENU,
                StepIds.VIDEO_CONFIRM,
                StepIds.VOICE_CONFIRM,
                StepIds.HANG_UP,
            ).map { id ->
                CallStep(
                    id = id,
                    action = defaultAction(id),
                    required = defaultRequired(id),
                    delayAfterMs = defaultDelay(id),
                    durationMs = if (defaultAction(id) == "longPress") {
                        timing.longPressDurationMs
                    } else null,
                    coord = null,
                )
            }
            return CallConfig(
                configId = "device-${Build.MODEL?.replace(' ', '_') ?: "unknown"}",
                updatedAt = nowIso(),
                deviceJson = device,
                timing = timing,
                steps = steps,
            )
        }

        fun videoRequiredIds(): List<String> = listOf(
            StepIds.SEARCH_ICON,
            StepIds.SEARCH_BOX_LONG_PRESS,
            StepIds.PASTE_BUBBLE,
            StepIds.SEARCH_RESULT,
            StepIds.PLUS_BUTTON,
            StepIds.VIDEO_MENU,
            StepIds.VIDEO_CONFIRM,
        )

        fun voiceRequiredIds(): List<String> = listOf(
            StepIds.SEARCH_ICON,
            StepIds.SEARCH_BOX_LONG_PRESS,
            StepIds.PASTE_BUBBLE,
            StepIds.SEARCH_RESULT,
            StepIds.PLUS_BUTTON,
            StepIds.VIDEO_MENU,
            StepIds.VOICE_CONFIRM,
        )
    }
}
