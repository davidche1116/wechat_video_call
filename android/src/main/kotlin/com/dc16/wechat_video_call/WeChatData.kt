package com.dc16.wechat_video_call

object WeChatData {
    @Volatile var value: String = ""
    /** 小写拼音/字母，用于键盘逐键键入（如 zhangsan）。 */
    @Volatile var pinyin: String = ""
    @Volatile var index: Int = 0
    @Volatile var video: Boolean = true

    /** 每次拨打递增，用于让已排期的延迟任务识别出过期会话。 */
    @Volatile var sessionId: Long = 0

    /** 当前会话开始时间（毫秒），用于超时看门狗。 */
    @Volatile var sessionStartAt: Long = 0

    /**
     * 运行时坐标覆盖: key(见 WeChatCoords.KEYS) -> Pair(fx, fy)，
     * fx/fy 为相对坐标，取值 0..1。
     */
    val customCoords: MutableMap<String, Pair<Float, Float>> = mutableMapOf()

    fun updateValue(newValue: String) {
        value = newValue
    }

    fun updateIndex(newValue: Int) {
        index = newValue
    }

    fun updateVideo(newValue: Boolean) {
        video = newValue
    }

    /** 开始一次新的拨打会话。 */
    fun startSession(name: String, pinyinText: String, isVideo: Boolean) {
        value = name
        pinyin = pinyinText.lowercase()
        video = isVideo
        sessionId++
        sessionStartAt = System.currentTimeMillis()
        index = 1
    }

    /** 取消当前会话（进行中的延迟步骤会因 sessionId 对不上而自动丢弃）。 */
    fun cancel() {
        index = 0
        sessionId++
    }

    /** 会话是否超时（超时后调用方应重置 index）。 */
    fun isExpired(timeoutMs: Long): Boolean {
        return index != 0 && System.currentTimeMillis() - sessionStartAt > timeoutMs
    }

    fun setCustomCoord(key: String, fx: Float, fy: Float): Boolean {
        if (key !in WeChatCoords.KEYS) return false
        if (fx < 0 || fx > 1 || fy < 0 || fy > 1) return false
        customCoords[key] = Pair(fx, fy)
        return true
    }
}
