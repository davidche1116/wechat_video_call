package com.dc16.wechat_video_call

/**
 * 坐标配置：结构锚点（dp + 系统栏边缘），按当前设备显示度量换算。
 *
 * 基准机: 魅族 20，1080x2400，450dpi (density=2.8125)，状态栏 116px，手势导航 nav=0。
 * 微信 8.0.78 无法读无障碍节点树，因此不能“解析控件”，只能：
 *  - 顶栏控件：距左右边缘 + 距状态栏底 的 dp
 *  - 底栏控件：距左右边缘 + 距屏幕底（再扣导航栏）的 dp
 *  - 弹层/列表：相对内容区（状态栏底 → 屏底）的比例
 *
 * 换机后仍可用 Dart `setCoordinate(key, fx, fy)` 按全屏相对坐标覆盖。
 */
object WeChatCoords {
    const val KEY_SEARCH_ICON = "searchIcon"
    const val KEY_HOME_TAB = "homeTab"
    const val KEY_SEARCH_BOX = "searchBox"
    const val KEY_PASTE_POPUP = "pastePopup"
    const val KEY_SEARCH_RESULT = "searchResult"
    const val KEY_PLUS_BUTTON = "plusButton"
    const val KEY_VIDEO_MENU = "videoMenu"
    const val KEY_VIDEO_CONFIRM = "videoConfirm"
    const val KEY_VOICE_CONFIRM = "voiceConfirm"
    const val KEY_HANG_UP = "hangUp"

    val KEYS: List<String> = listOf(
        KEY_SEARCH_ICON,
        KEY_HOME_TAB,
        KEY_SEARCH_BOX,
        KEY_PASTE_POPUP,
        KEY_SEARCH_RESULT,
        KEY_PLUS_BUTTON,
        KEY_VIDEO_MENU,
        KEY_VIDEO_CONFIRM,
        KEY_VOICE_CONFIRM,
        KEY_HANG_UP,
    )

    private sealed class Anchor {
        /** 顶栏：距 start/end 边缘 dp + 距状态栏底 dp。 */
        data class TopBar(
            val fromStartDp: Float? = null,
            val fromEndDp: Float? = null,
            val fromStatusBarBottomDp: Float,
        ) : Anchor()

        /** 底栏：距 start/end 边缘 dp + 距屏幕底 dp（resolve 时再扣导航栏）。 */
        data class BottomBar(
            val fromStartDp: Float? = null,
            val fromEndDp: Float? = null,
            val fromBottomDp: Float,
        ) : Anchor()

        /** 内容区相对比例：x/宽度，y/(状态栏底..屏底)。 */
        data class ContentRelative(
            val fx: Float,
            val fyContent: Float,
        ) : Anchor()
    }

    // 由魅族 20 基准像素换算：px / 2.8125
    private val anchors: Map<String, Anchor> = mapOf(
        KEY_SEARCH_ICON to Anchor.TopBar(fromEndDp = 66.9f, fromStatusBarBottomDp = 26.7f),
        KEY_HOME_TAB to Anchor.BottomBar(fromStartDp = 48f, fromBottomDp = 39.1f),
        KEY_SEARCH_BOX to Anchor.TopBar(fromStartDp = 192f, fromStatusBarBottomDp = 29.9f),
        KEY_PASTE_POPUP to Anchor.TopBar(fromStartDp = 52.6f, fromStatusBarBottomDp = 85.3f),
        KEY_SEARCH_RESULT to Anchor.TopBar(fromStartDp = 160f, fromStatusBarBottomDp = 108.1f),
        KEY_PLUS_BUTTON to Anchor.BottomBar(fromEndDp = 23.8f, fromBottomDp = 29.9f),
        KEY_VIDEO_MENU to Anchor.ContentRelative(fx = 659f / 1080f, fyContent = 0.704f),
        KEY_VIDEO_CONFIRM to Anchor.ContentRelative(fx = 640f / 1080f, fyContent = 0.842f),
        KEY_VOICE_CONFIRM to Anchor.ContentRelative(fx = 640f / 1080f, fyContent = 0.724f),
        // 通话页红键截屏实测约 (540,2170)（2045 会点空）
        KEY_HANG_UP to Anchor.BottomBar(fromStartDp = 192f, fromBottomDp = 81.8f),
    )

    /**
     * 按当前显示度量解析绝对像素。
     * 自定义覆盖优先：全屏相对坐标 0..1。
     */
    fun resolve(key: String, snap: WeChatDisplay.Snapshot): Pair<Float, Float>? {
        val override = WeChatData.customCoords[key]
        if (override != null) {
            return Pair(override.first * snap.widthPx, override.second * snap.heightPx)
        }
        val anchor = anchors[key] ?: return null
        return when (anchor) {
            is Anchor.TopBar -> {
                val x = when {
                    anchor.fromEndDp != null ->
                        snap.widthPx - anchor.fromEndDp * snap.density
                    anchor.fromStartDp != null ->
                        anchor.fromStartDp * snap.density
                    else -> snap.widthPx / 2f
                }
                val y = snap.statusBarPx + anchor.fromStatusBarBottomDp * snap.density
                Pair(x, y)
            }
            is Anchor.BottomBar -> {
                val x = when {
                    anchor.fromStartDp != null -> anchor.fromStartDp * snap.density
                    anchor.fromEndDp != null ->
                        snap.widthPx - anchor.fromEndDp * snap.density
                    else -> snap.widthPx / 2f
                }
                val y = snap.heightPx - snap.navigationBarPx - anchor.fromBottomDp * snap.density
                Pair(x, y)
            }
            is Anchor.ContentRelative -> {
                val x = anchor.fx * snap.widthPx
                val y = snap.statusBarPx + anchor.fyContent * snap.contentHeightPx
                Pair(x, y)
            }
        }
    }
}
