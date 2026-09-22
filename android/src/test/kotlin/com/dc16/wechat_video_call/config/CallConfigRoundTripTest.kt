package com.dc16.wechat_video_call.config

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallConfigRoundTripTest {
    @Test
    fun `timing json roundtrip keeps stepCooldownMs`() {
        val timing = CallTiming(sessionTimeoutMs = 30_000, stepCooldownMs = 2_500)
        val parsed = CallTiming.fromJson(JSONObject(timing.toJson().toString()))
        assertEquals(30_000, parsed.sessionTimeoutMs)
        assertEquals(2_500, parsed.stepCooldownMs)
        assertEquals(timing, parsed)
    }

    @Test
    fun `timing scaled multiplies and adds pause`() {
        val timing = CallTiming(delayScale = 2.0, pauseAfterStepMs = 100)
        assertEquals(1100L, timing.scaled(500, 2.0))
        // invalid scale falls back to 1.0; ms clamped into 200..1000
        assertEquals(200L, timing.scaled(0, 0.0))
    }

    @Test
    fun `timing scaled clamps to min and max`() {
        val timing = CallTiming(pauseAfterStepMs = 50)
        assertEquals(250L, timing.scaled(-10, 1.0)) // MIN=200
        assertEquals(1050L, timing.scaled(5_000, 1.0)) // MAX=1000
    }

    @Test
    fun `stepCoord json roundtrip keeps displayAtRecord and recordedAt`() {
        val coord = StepCoord(
            fx = 0.5,
            fy = 0.25,
            pixelX = 100,
            pixelY = 200,
            widthPx = 1080,
            heightPx = 2400,
            density = 2.75,
            statusBarPx = 100,
            navigationBarPx = 120,
            rotation = 0,
            recordedAt = "2026-09-22T00:00:00Z",
        )
        val parsed = StepCoord.fromJson(JSONObject(coord.toJson().toString()))
        assertEquals(coord, parsed)
    }

    @Test
    fun `upsertCoord and clearCoord manipulate steps`() {
        var cfg = CallConfig.emptyIdOnly()
        cfg = cfg.upsertCoord(
            StepIds.SEARCH_ICON,
            StepCoord(fx = 0.9, fy = 0.1, pixelX = 900, pixelY = 100),
        )
        assertTrue(cfg.hasCoord(StepIds.SEARCH_ICON))
        cfg = cfg.clearCoord(StepIds.SEARCH_ICON)
        assertTrue(!cfg.hasCoord(StepIds.SEARCH_ICON))
    }

    @Test
    fun `config json roundtrip keeps steps and timing`() {
        var cfg = CallConfig.emptyIdOnly()
        cfg = cfg.upsertCoord(
            StepIds.PASTE_BUBBLE,
            StepCoord(fx = 0.5, fy = 0.3, widthPx = 1080, heightPx = 2400),
        )
        val parsed = CallConfig.fromJson(JSONObject(cfg.toJson().toString()))
        assertEquals(cfg.steps.map { it.id }, parsed.steps.map { it.id })
        assertEquals(cfg.timing, parsed.timing)
        assertEquals(
            cfg.coord(StepIds.PASTE_BUBBLE)?.widthPx,
            parsed.coord(StepIds.PASTE_BUBBLE)?.widthPx,
        )
        assertNull(parsed.deviceJson) // emptyIdOnly has no device block
    }
}

/** Pure-data CallConfig factory that avoids Android Context (unit-test safe). */
private fun CallConfig.Companion.emptyIdOnly(): CallConfig {
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
            action = CallConfig.defaultAction(id),
            required = CallConfig.defaultRequired(id),
            delayAfterMs = CallConfig.defaultDelay(id),
            durationMs = if (CallConfig.defaultAction(id) == "longPress") {
                timing.longPressDurationMs
            } else null,
            coord = null,
        )
    }
    return CallConfig(
        configId = "test",
        updatedAt = CallConfig.nowIso(),
        timing = timing,
        steps = steps,
    )
}
