package com.dc16.wechat_video_call.config

/** Well-known step / coordinate ids (mirrors Dart `WvcStepIds`). */
object StepIds {
    const val OPEN_WECHAT = "openWeChat"
    const val HOME_TAB = "homeTab"
    const val SEARCH_ICON = "searchIcon"

    /** Legacy id only — not wizard-recordable and not used at dial time. */
    const val SEARCH_BOX = "searchBox"
    const val SEARCH_BOX_LONG_PRESS = "searchBoxLongPress"
    const val PASTE_BUBBLE = "pasteBubble"
    const val SEARCH_RESULT = "searchResult"
    const val PLUS_BUTTON = "plusButton"
    const val VIDEO_MENU = "videoMenu"
    const val VIDEO_CONFIRM = "videoConfirm"
    const val VOICE_CONFIRM = "voiceConfirm"
    const val CALL_CANCEL = "callCancel"
    const val HANG_UP = "hangUp"

    /** Wizard order (mirrors Dart WvcStepIds.wizardSteps). */
    val WIZARD_ORDER: List<String> = listOf(
        HOME_TAB,
        SEARCH_ICON,
        SEARCH_BOX_LONG_PRESS,
        PASTE_BUBBLE,
        SEARCH_RESULT,
        PLUS_BUTTON,
        VIDEO_MENU,
        VIDEO_CONFIRM,
        VOICE_CONFIRM,
        HANG_UP,
    )

    /** Legacy setCoordinate key mapping. */
    fun mapLegacyKey(key: String): String = when (key) {
        "pastePopup" -> PASTE_BUBBLE
        "searchBox" -> SEARCH_BOX_LONG_PRESS
        else -> key
    }
}
