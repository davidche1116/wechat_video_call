package com.dc16.wechat_video_call.display

import kotlin.test.Test
import kotlin.test.assertEquals

class WeChatDisplayResolveTest {
    private val snap = WeChatDisplay.Snapshot(
        widthPx = 1080,
        heightPx = 2400,
        density = 2.75f,
        statusBarPx = 100,
        navigationBarPx = 120,
        rotation = 0,
    )

    @Test
    fun `fullscreen resolve scales fx fy by full height`() {
        val (x, y) = WeChatDisplay.resolve(0.5, 0.25, "fullscreen", snap)
        assertEquals(540f, x, 0.01f)
        assertEquals(600f, y, 0.01f)
    }

    @Test
    fun `contentBelowStatusBar resolve offsets y by status bar`() {
        val (x, y) = WeChatDisplay.resolve(0.5, 0.5, "contentBelowStatusBar", snap)
        assertEquals(540f, x, 0.01f)
        // statusBar + fy * (H - statusBar) = 100 + 0.5 * 2300 = 1250
        assertEquals(1250f, y, 0.01f)
    }

    @Test
    fun `null fySpace falls back to fullscreen`() {
        val (_, y) = WeChatDisplay.resolve(0.0, 1.0, null, snap)
        assertEquals(2400f, y, 0.01f)
    }
}
