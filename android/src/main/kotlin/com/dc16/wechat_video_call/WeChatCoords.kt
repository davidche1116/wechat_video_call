package com.dc16.wechat_video_call

/**
 * 坐标配置（相对坐标，取值 0..1，会按当前屏幕宽高自动换算成像素）。
 *
 * 基准: 魅族 20 (1080x2400)，微信 8.0.78 (versionCode 3180)。
 * 换机/换微信版本后如有偏移，可通过 Dart 侧 setCoordinate(key, fx, fy) 运行时覆盖，
 * 无需重新编译（key 见 [KEYS]）。
 */
object WeChatCoords {
    const val KEY_SEARCH_ICON = "searchIcon"
    const val KEY_HOME_TAB = "homeTab"
    const val KEY_SEARCH_BOX = "searchBox"
    /** 长按搜索框后弹出的“粘贴”按钮（需按机型截屏校准，见 TESTING_NOTES）。 */
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

    // 基准绝对坐标 (1080x2400) -> 相对坐标
    private val defaults: Map<String, Pair<Float, Float>> = mapOf(
        KEY_SEARCH_ICON to Pair(892f / 1080, 191f / 2400),
        // 底部导航第一个 Tab“微信”，1080x2400 下约 (135, 2290)
        KEY_HOME_TAB to Pair(135f / 1080, 2290f / 2400),
        KEY_SEARCH_BOX to Pair(540f / 1080, 200f / 2400),
        // “粘贴”按钮：程序实测笔画包围盒 x112-184 y337-371，中心 (148,356)；
        // 之前凭肉眼估的 (312,295)/(275,295) 全打在 ⋮ 上
        KEY_PASTE_POPUP to Pair(148f / 1080, 356f / 2400),
        KEY_SEARCH_RESULT to Pair(450f / 1080, 420f / 2400),
        KEY_PLUS_BUTTON to Pair(1013f / 1080, 2316f / 2400),
        KEY_VIDEO_MENU to Pair(659f / 1080, 1723f / 2400),
        KEY_VIDEO_CONFIRM to Pair(640f / 1080, 2038f / 2400),
        KEY_VOICE_CONFIRM to Pair(640f / 1080, 1770f / 2400),
        // 挂断红键：通话中界面截屏实测约 (540, 2045)
        KEY_HANG_UP to Pair(540f / 1080, 2045f / 2400),
    )

    /**
     * 按当前屏幕尺寸换算成绝对像素。
     * @return Pair(xPx, yPx)，未知 key 返回 null。
     */
    fun resolve(key: String, widthPx: Int, heightPx: Int): Pair<Float, Float>? {
        val override = WeChatData.customCoords[key]
        val (fx, fy) = override ?: defaults[key] ?: return null
        return Pair(fx * widthPx, fy * heightPx)
    }
}
