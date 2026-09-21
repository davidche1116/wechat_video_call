package com.dc16.wechat_video_call

/**
 * 微信 8.0.78 资源 ID（已废弃）
 * 
 * 微信 8.0.78 完全封锁了无障碍树遍历，以下 ID 已无法使用:
 * - rootInActiveWindow 返回空树
 * - findAccessibilityNodeInfosByViewId 返回的节点 bounds 为 (0,0,0,0)
 * - performAction 返回 true 但不执行
 * 
 * 当前方案: dispatchGesture 结构锚点坐标 + 剪贴板长按粘贴
 * 坐标配置请参见 WeChatCoords.kt / WeChatDisplay.kt
 */
enum class WeChatId(val id: String) {
    TABLES("com.tencent.mm:id/icon_tv"),
    SEARCH("com.tencent.mm:id/jha"),
    INPUT("com.tencent.mm:id/d98"),
    LIST("com.tencent.mm:id/odf"),
    MORE("com.tencent.mm:id/bjz"),
    DIALOG("com.tencent.mm:id/obc"),
    CHAT_MENU("com.tencent.mm:id/a1u"),
}
