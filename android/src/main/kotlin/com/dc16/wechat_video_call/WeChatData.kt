package com.dc16.wechat_video_call

object WeChatData {
    var value: String = ""
    fun updateValue(newValue: String) {
        value = newValue
    }

    var index: Int = 0
    fun updateIndex(newValue: Int) {
        index = newValue
    }

    var video: Boolean = true
    fun updateVideo(newValue: Boolean) {
        video = newValue
    }

    fun findText(options: Boolean): String {
        if (video || !options) {
            return "视频通话"
        } else {
            return "语音通话"
        }
    }
}
