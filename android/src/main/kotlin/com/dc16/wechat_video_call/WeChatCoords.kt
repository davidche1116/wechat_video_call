package com.dc16.wechat_video_call

/**
 * 微信手势点击坐标。
 *
 * 微信 8.0.78 封锁无障碍节点树，无法读控件 bounds，只能标定 + 换算。
 * 真机结论（魅族 20 / Redmi Note 同为 1080x2400）:
 *  - 顶栏图标跟「状态栏高度」走，用「距状态栏底的内容区比例」比纯 dp 更稳
 *  - 底栏 Tab / 挂断键按**全屏绝对坐标**绘制，绝不能再减 navigationBar
 *    （Redmi 手势导航 inset ~140px，减完会点到会话列表）
 *  - 系统 density 在 MIUI 上有 override，与微信布局不完全线性，勿当唯一依据
 *
 * 基准: 魅族 20 1080x2400 实测；Redmi chopin 截屏校验顶栏/底栏。
 * 仍可用 Dart `setCoordinate(key, fx, fy)` 按全屏相对坐标覆盖。
 */
object WeChatCoords {
    const val KEY_SEARCH_ICON = "searchIcon"
    const val KEY_HOME_TAB = "homeTab"
    const val KEY_SEARCH_BOX = "searchBox"
    const val KEY_PASTE_POPUP = "pastePopup"
    const val KEY_IME_CLIPBOARD = "imeClipboard"
    const val KEY_IME_CLIPBOARD_FIRST = "imeClipboardFirst"
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
        KEY_IME_CLIPBOARD,
        KEY_IME_CLIPBOARD_FIRST,
        KEY_SEARCH_RESULT,
        KEY_PLUS_BUTTON,
        KEY_VIDEO_MENU,
        KEY_VIDEO_CONFIRM,
        KEY_VOICE_CONFIRM,
        KEY_HANG_UP,
    )

    /**
     * @param fx 全屏宽度比例 x/W
     * @param fy 全屏高度比例 y/H（底栏/通话页用）
     * @param fyFromStatusBarTop 顶栏用： (y - statusBar) / (H - statusBar)
     */
    private data class Rel(
        val fx: Float,
        val fy: Float,
        val fyFromStatusBarTop: Float? = null,
    )

    // 由魅族 20 像素换算；顶栏 fyFromStatusBarTop = (y-116)/(2400-116)
    // Redmi 校验: searchIcon 预测 y≈96+0.033*(2400-96)≈172，截屏约 165
    //             homeTab  预测 y≈0.954*2400≈2290，截屏底栏中心约 2320（落在 Tab 热区）
    private val anchors: Map<String, Rel> = mapOf(
        // 892,191
        KEY_SEARCH_ICON to Rel(fx = 0.8259f, fy = 0.0796f, fyFromStatusBarTop = 0.0328f),
        // 底栏「微信」Tab：魅族实测 y=2290 (0.954)，Redmi 截屏中心约 2320 (0.967)
        // 取略偏下的 0.965，配合物理屏高 2400 → y≈2316，两台都落在 Tab 热区
        KEY_HOME_TAB to Rel(fx = 0.125f, fy = 0.965f),
        // 540,200
        KEY_SEARCH_BOX to Rel(fx = 0.5f, fy = 0.0833f, fyFromStatusBarTop = 0.0368f),
        // 148,356 长按后「粘贴」气泡（各机型差异大，仅作最后回退）
        KEY_PASTE_POPUP to Rel(fx = 0.137f, fy = 0.1483f, fyFromStatusBarTop = 0.1051f),
        // 输入法工具栏剪贴板（搜狗等）：Redmi 截屏约 (1000,2300)
        KEY_IME_CLIPBOARD to Rel(fx = 0.926f, fy = 0.958f),
        // 剪贴板面板第一项（写入姓名后通常在顶部）
        KEY_IME_CLIPBOARD_FIRST to Rel(fx = 0.5f, fy = 0.62f),
        // 450,420 第一个搜索结果
        KEY_SEARCH_RESULT to Rel(fx = 0.4167f, fy = 0.175f, fyFromStatusBarTop = 0.1331f),
        // 1013,2316 聊天页 +
        KEY_PLUS_BUTTON to Rel(fx = 0.938f, fy = 0.965f),
        // 659,1723 + 面板「视频通话」
        KEY_VIDEO_MENU to Rel(fx = 0.6102f, fy = 0.7179f, fyFromStatusBarTop = 0.7036f),
        // 640,2038 确认「视频通话」
        KEY_VIDEO_CONFIRM to Rel(fx = 0.5926f, fy = 0.8492f, fyFromStatusBarTop = 0.8415f),
        // 640,1770 确认「语音通话」
        KEY_VOICE_CONFIRM to Rel(fx = 0.5926f, fy = 0.7375f, fyFromStatusBarTop = 0.7242f),
        // 540,2170 通话页红键（截屏实测）
        KEY_HANG_UP to Rel(fx = 0.5f, fy = 0.9042f),
    )

    fun resolve(key: String, snap: WeChatDisplay.Snapshot): Pair<Float, Float>? {
        val override = WeChatData.customCoords[key]
        if (override != null) {
            return Pair(override.first * snap.widthPx, override.second * snap.heightPx)
        }
        val rel = anchors[key] ?: return null
        val x = rel.fx * snap.widthPx
        val contentH = snap.contentHeightPx.toFloat()
        val y = if (rel.fyFromStatusBarTop != null) {
            snap.statusBarPx + rel.fyFromStatusBarTop * contentH
        } else {
            rel.fy * snap.heightPx
        }
        return Pair(x, y)
    }

    /** 调试：把当前设备解析结果打出来，便于换机标定。 */
    fun resolveAll(snap: WeChatDisplay.Snapshot): Map<String, Pair<Float, Float>> {
        return KEYS.mapNotNull { key ->
            resolve(key, snap)?.let { key to it }
        }.toMap()
    }
}
