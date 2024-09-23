package com.dc16.wechat_video_call

enum class WechatActivity(val id: String) {
    INDEX("com.tencent.mm.ui.LauncherUI"),
    CHAT("com.tencent.mm.ui.chatting.ChattingUI"),
    SEARCH("com.tencent.mm.plugin.fts.ui.FTSMainUI"),
    DIALOG("com.tencent.mm.ui.widget.dialog.o3"),
    DIALOG_OLD("yj4.o3"),
}
