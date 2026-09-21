package com.dc16.wechat_video_call

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * 微信自动拨打无障碍服务。
 *
 * 微信 8.0.78 起封锁无障碍节点树，点击全部走 [dispatchGesture] 坐标手势。
 * 勿用 `Runtime.exec("input ...")`（进程内无 INJECT_EVENTS，必失败）。
 *
 * 流程（WeChatData.index）:
 * 1 回微信主页 → 2 搜索图标 → 3 剪贴板长按粘贴 → 4 第一个结果
 * → 5 聊天页 + 号 → 6 视频通话菜单 → 7 确认框 → 0 完成
 */
class WeChatAccessibility : AccessibilityService() {

    companion object {
        const val TAG = "WechatAccessibilityTag"
        const val SESSION_TIMEOUT_MS = 45_000L
        const val STEP_COOLDOWN_MS = 1_500L
        const val NUDGE_INTERVAL_MS = 2_000L

        @Volatile
        var instance: WeChatAccessibility? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val lastActionAt = mutableMapOf<Int, Long>()
    private var lastClassName: String = ""
    private var nudgeSession: Long = -1
    private var lastMmEventAt: Long = 0
    private var seenMainSession: Long = -1
    private var seenMain: Boolean = false
    private var backPressSession: Long = -1
    private var backPressCount: Int = 0
    private var plusScheduledFor: Long = -1

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        android.util.Log.i(TAG, "Service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        clearPendingWork()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        clearPendingWork()
        WeChatData.cancel()
    }

    /** 新会话前：清掉旧定时任务，并预写剪贴板。 */
    fun prepareNewSession(query: String) {
        clearPendingWork()
        writeClipboard(query)
    }

    /** 取消/结束会话时清掉 handler 上未执行的步骤与 nudge。 */
    fun clearPendingWork() {
        handler.removeCallbacksAndMessages(null)
        nudgeSession = -1
        lastClassName = ""
    }

    private fun writeClipboard(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("wechat_search", text))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "clipboard write failed: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (WeChatData.index == 0) return
        val eventType = event?.eventType ?: return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.tencent.mm") return
        val className = event.className?.toString().orEmpty()

        if (WeChatData.isExpired(SESSION_TIMEOUT_MS)) {
            android.util.Log.w(TAG, "Session timeout, name=${WeChatData.value} lastClass=$lastClassName")
            clearPendingWork()
            WeChatData.cancel()
            return
        }

        lastClassName = className
        lastMmEventAt = System.currentTimeMillis()
        ensureNudge()

        when (WeChatData.index) {
            1 -> handleStep1ReturnToMain(className)
            3 -> handleStep3InputName(className)
            5 -> handleStep5ClickPlus(className)
        }
    }

    private val nudgeRunnable = object : Runnable {
        override fun run() {
            val session = WeChatData.sessionId
            if (WeChatData.index == 0 || session != nudgeSession) {
                nudgeSession = -1
                return
            }
            if (WeChatData.isExpired(SESSION_TIMEOUT_MS)) {
                android.util.Log.w(TAG, "Nudge: session timeout, reset")
                clearPendingWork()
                WeChatData.cancel()
                return
            }
            android.util.Log.i(TAG, "Nudge: replay index=${WeChatData.index} class=$lastClassName")
            if (WeChatData.index == 1 && System.currentTimeMillis() - lastMmEventAt > 5000) {
                android.util.Log.i(TAG, "Nudge: relaunch WeChat")
                relaunchWeChat()
                lastMmEventAt = System.currentTimeMillis()
            }
            when (WeChatData.index) {
                1 -> handleStep1ReturnToMain(lastClassName)
                2 -> handleStep2ClickSearch(lastClassName)
                3 -> handleStep3InputName(lastClassName)
                5 -> handleStep5ClickPlus(lastClassName)
            }
            handler.postDelayed(this, NUDGE_INTERVAL_MS)
        }
    }

    private fun ensureNudge() {
        if (nudgeSession == WeChatData.sessionId) return
        nudgeSession = WeChatData.sessionId
        handler.postDelayed(nudgeRunnable, NUDGE_INTERVAL_MS)
    }

    // ---------- 步骤 ----------

    private fun handleStep1ReturnToMain(className: String) {
        if (seenMainSession != WeChatData.sessionId) {
            seenMainSession = WeChatData.sessionId
            seenMain = false
        }
        if (className == WeChatActivity.INDEX.id) {
            seenMain = true
            // LauncherUI 会恢复上次 Tab，先点底部“微信”再搜
            android.util.Log.i(TAG, "Step1: main page, tap home tab")
            WeChatData.updateIndex(2)
            tap(WeChatCoords.KEY_HOME_TAB)
            startSearchChain()
            return
        }
        val knownSubPage = className.contains("Search") ||
            className.contains("FTS") ||
            className.contains("Chat") ||
            className.contains("chatting") ||
            (className.startsWith("com.tencent.mm.") && className != WeChatActivity.INDEX.id)
        val shouldBack = className.isNotEmpty() && (seenMain || knownSubPage)
        if (!shouldBack) return
        if (consumeCooldown(1) && consumeBackPress()) {
            android.util.Log.i(TAG, "Step1: back from $className")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /** 每会话最多连按返回 3 次，避免退到桌面。 */
    private fun consumeBackPress(): Boolean {
        if (backPressSession != WeChatData.sessionId) {
            backPressSession = WeChatData.sessionId
            backPressCount = 0
        }
        if (backPressCount >= 3) return false
        backPressCount++
        return true
    }

    private fun startSearchChain() {
        val session = WeChatData.sessionId
        handler.postDelayed({
            if (!isSessionAlive(session) || WeChatData.index != 2) return@postDelayed
            android.util.Log.i(TAG, "Step2: tap search icon")
            if (tap(WeChatCoords.KEY_SEARCH_ICON)) {
                WeChatData.updateIndex(3)
            }
        }, 900L)
    }

    private fun handleStep2ClickSearch(className: String) {
        if (className != WeChatActivity.INDEX.id) return
        if (!consumeCooldown(2)) return
        android.util.Log.i(TAG, "Step2: tap search icon (nudge)")
        if (tap(WeChatCoords.KEY_SEARCH_ICON)) {
            WeChatData.updateIndex(3)
        }
    }

    /** 搜索页：点框 → 长按出粘贴气泡 → 点粘贴（剪贴板已在会话开始时写入）。 */
    private fun handleStep3InputName(className: String) {
        if (!className.contains("Search") && !className.contains("FTS") &&
            className != WeChatActivity.SEARCH.id
        ) return
        attemptStep3()
    }

    private fun attemptStep3() {
        if (!consumeCooldown(3)) return
        val session = WeChatData.sessionId
        android.util.Log.i(TAG, "Step3: paste flow query=${WeChatData.value}")
        writeClipboard(WeChatData.value)
        tap(WeChatCoords.KEY_SEARCH_BOX)
        handler.postDelayed({
            if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
            android.util.Log.i(TAG, "Step3: long-press search box")
            longPress(WeChatCoords.KEY_SEARCH_BOX, 600)
            handler.postDelayed({
                if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
                android.util.Log.i(TAG, "Step3: tap paste popup")
                tap(WeChatCoords.KEY_PASTE_POPUP)
                startContactChain()
            }, 1100L)
        }, 700L)
    }

    private fun startContactChain() {
        val session = WeChatData.sessionId
        WeChatData.updateIndex(4)
        handler.postDelayed({
            if (!isSessionAlive(session)) return@postDelayed
            android.util.Log.i(TAG, "Step4: tap first search result")
            tap(WeChatCoords.KEY_SEARCH_RESULT)
            if (WeChatData.sessionId != session || WeChatData.index == 0) return@postDelayed
            WeChatData.updateIndex(5)
            handler.postDelayed({
                if (!isSessionAlive(session) || WeChatData.index != 5) return@postDelayed
                android.util.Log.i(TAG, "Step5: schedule plus tap (timed)")
                schedulePlusTap(session)
            }, 2_500L)
        }, 2_000L)
    }

    private fun handleStep5ClickPlus(className: String) {
        if (!className.contains("Chat") && !className.contains("chatting") &&
            className != WeChatActivity.CHAT.id
        ) return
        if (!consumeCooldown(5)) return
        android.util.Log.i(TAG, "Step5: chat page seen, schedule plus tap")
        schedulePlusTap(WeChatData.sessionId)
    }

    private fun schedulePlusTap(session: Long) {
        if (plusScheduledFor == session) return
        plusScheduledFor = session
        handler.postDelayed({
            if (!isSessionAlive(session) || WeChatData.index != 5) return@postDelayed
            android.util.Log.i(TAG, "Step5: tap plus button")
            if (tap(WeChatCoords.KEY_PLUS_BUTTON)) {
                startMenuChain()
            }
        }, 1_500L)
    }

    private fun startMenuChain() {
        val session = WeChatData.sessionId
        // 先占 index，防止微信事件误触发步骤 1~5
        WeChatData.updateIndex(6)
        handler.postDelayed({
            if (!isSessionAlive(session)) return@postDelayed
            android.util.Log.i(TAG, "Step6: tap video-call menu")
            tap(WeChatCoords.KEY_VIDEO_MENU)
        }, 1_200L)
        handler.postDelayed({
            if (!isSessionAlive(session)) return@postDelayed
            val key = if (WeChatData.video) {
                WeChatCoords.KEY_VIDEO_CONFIRM
            } else {
                WeChatCoords.KEY_VOICE_CONFIRM
            }
            android.util.Log.i(TAG, "Step7: tap confirm key=$key video=${WeChatData.video}")
            val dispatched = tap(key)
            android.util.Log.i(
                TAG,
                if (dispatched) "Step7: confirm gesture dispatched" else "Step7: confirm gesture FAILED",
            )
            // 手势下发成功 ≠ 对方已收到呼叫；此处只保证自动化链路结束
            WeChatData.cancel()
            clearPendingWork()
        }, 2_500L)
    }

    private fun isSessionAlive(session: Long): Boolean {
        return WeChatData.sessionId == session && WeChatData.index != 0 &&
            !WeChatData.isExpired(SESSION_TIMEOUT_MS)
    }

    // ---------- 手势 ----------

    fun tap(key: String): Boolean = dispatchGestureForKey(key, 60L)

    fun longPress(key: String, durationMs: Long): Boolean =
        dispatchGestureForKey(key, durationMs.coerceAtLeast(1L))

    fun requestHangUp(): Boolean {
        handler.post { tap(WeChatCoords.KEY_HANG_UP) }
        android.util.Log.i(TAG, "HangUp requested")
        return true
    }

    private fun dispatchGestureForKey(key: String, durationMs: Long): Boolean {
        val snap = WeChatDisplay.snapshot(resources)
        val point = WeChatCoords.resolve(key, snap)
        if (point == null) {
            android.util.Log.e(TAG, "gesture: unknown coord key=$key")
            return false
        }
        return dispatchGestureAt(point.first, point.second, durationMs, key)
    }

    private fun dispatchGestureAt(x: Float, y: Float, durationMs: Long, label: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            android.util.Log.e(TAG, "gesture[$label]: requires API 24+")
            return false
        }
        return try {
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(StrokeDescription(path, 0, durationMs))
                .build()
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        android.util.Log.i(TAG, "gesture[$label] ok (${x.toInt()},${y.toInt()})")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        android.util.Log.w(TAG, "gesture[$label] cancelled (${x.toInt()},${y.toInt()})")
                    }
                },
                null,
            )
            if (!dispatched) {
                android.util.Log.e(TAG, "gesture[$label] dispatch returned false at (${x.toInt()},${y.toInt()})")
            }
            dispatched
        } catch (e: Exception) {
            android.util.Log.e(TAG, "gesture[$label] failed: ${e.message}")
            false
        }
    }

    private fun relaunchWeChat() {
        try {
            startActivity(Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
            })
        } catch (e: Exception) {
            android.util.Log.e(TAG, "relaunch failed: ${e.message}")
        }
    }

    private fun consumeCooldown(step: Int): Boolean {
        val now = System.currentTimeMillis()
        if (now - (lastActionAt[step] ?: 0L) < STEP_COOLDOWN_MS) return false
        lastActionAt[step] = now
        return true
    }
}
