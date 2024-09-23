package com.dc16.wechat_video_call

object WechatData {
    var value: String = ""
    fun updateValue(newValue: String) {
        value = newValue
    }

    var index: Int = 0
    fun updateIndex(newValue: Int) {
        index = newValue
    }
}
