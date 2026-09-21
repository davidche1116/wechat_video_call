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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
    private var searchFailStreak: Int = 0
    private var lastSearchTapAt: Long = 0
    /** Step3 粘贴链进行中：禁止 nudge 恢复抢跑。 */
    @Volatile private var pasteChainActive: Boolean = false

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
        pasteChainActive = false
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

        if (WeChatData.isExpired(sessionTimeoutMs())) {
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
            if (WeChatData.isExpired(sessionTimeoutMs())) {
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
            // 搜索步骤未进搜索页：可能是手势打在调用方 App 上，先把微信拉回前台
            if (WeChatData.index == 3 && !pasteChainActive) {
                val onSearch = lastClassName.contains("Search") || lastClassName.contains("FTS") ||
                    lastClassName == WeChatActivity.SEARCH.id
                val sinceSearchTap = System.currentTimeMillis() - lastSearchTapAt
                if (!onSearch && sinceSearchTap > 4_000L) {
                    android.util.Log.w(
                        TAG,
                        "Nudge: step3 not on search class=$lastClassName, relaunch WeChat",
                    )
                    relaunchWeChat()
                    lastMmEventAt = System.currentTimeMillis()
                    WeChatData.updateIndex(1)
                    seenMain = false
                    lastSearchTapAt = System.currentTimeMillis()
                }
            }
            when (WeChatData.index) {
                1 -> handleStep1ReturnToMain(lastClassName)
                2 -> handleStep2ClickSearch(lastClassName)
                3 -> handleStep3InputName(lastClassName)
                5 -> handleStep5ClickPlus(lastClassName)
            }
            handler.postDelayed(this, WeChatData.delay(NUDGE_INTERVAL_MS))
        }
    }

    private fun ensureNudge() {
        if (nudgeSession == WeChatData.sessionId) return
        nudgeSession = WeChatData.sessionId
        handler.postDelayed(nudgeRunnable, WeChatData.delay(NUDGE_INTERVAL_MS))
    }

    // ---------- 步骤 ----------

    private fun handleStep1ReturnToMain(className: String) {
        if (seenMainSession != WeChatData.sessionId) {
            seenMainSession = WeChatData.sessionId
            seenMain = false
        }
        if (className == WeChatActivity.INDEX.id) {
            seenMain = true
            searchFailStreak = 0
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
                lastSearchTapAt = System.currentTimeMillis()
                WeChatData.updateIndex(3)
            }
        }, WeChatData.delay(1_200L) + observePause())
    }

    private fun handleStep2ClickSearch(className: String) {
        if (className != WeChatActivity.INDEX.id) return
        if (!consumeCooldown(2)) return
        android.util.Log.i(TAG, "Step2: tap search icon (nudge)")
        if (tap(WeChatCoords.KEY_SEARCH_ICON)) {
            lastSearchTapAt = System.currentTimeMillis()
            WeChatData.updateIndex(3)
        }
    }

    /**
     * 搜索页输入姓名。
     * 优先系统粘贴（焦点节点 ACTION_SET_TEXT / ACTION_PASTE），
     * 再尝试输入法剪贴板，最后才长按微信粘贴气泡（各机型气泡位置差异极大）。
     */
    private fun handleStep3InputName(className: String) {
        if (!className.contains("Search") && !className.contains("FTS") &&
            className != WeChatActivity.SEARCH.id
        ) return
        attemptStep3()
    }

    private fun attemptStep3() {
        if (!consumeCooldown(3)) return
        val session = WeChatData.sessionId
        val query = WeChatData.value
        pasteChainActive = true
        android.util.Log.i(TAG, "Step3: input query=$query")
        writeClipboard(query)
        tap(WeChatCoords.KEY_SEARCH_BOX)
        handler.postDelayed({
            if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
            val systemOk = trySystemPasteToFocused(query)
            android.util.Log.i(TAG, "Step3: system paste result=$systemOk")
            if (systemOk) {
                handler.postDelayed({
                    if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
                    android.util.Log.i(TAG, "Step3: after system paste -> contact chain")
                    startContactChain()
                }, WeChatData.delay(3_000L) + observePause())
                return@postDelayed
            }
            android.util.Log.i(TAG, "Step3: try IME clipboard")
            tap(WeChatCoords.KEY_IME_CLIPBOARD)
            handler.postDelayed({
                if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
                tap(WeChatCoords.KEY_IME_CLIPBOARD_FIRST)
                handler.postDelayed({
                    if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
                    if (searchResultsLikelyPresent()) {
                        android.util.Log.i(TAG, "Step3: IME clipboard ok -> contact chain")
                        startContactChain()
                        return@postDelayed
                    }
                    android.util.Log.i(TAG, "Step3: fallback long-press paste bubble")
                    longPress(WeChatCoords.KEY_SEARCH_BOX, 600)
                    handler.postDelayed({
                        if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
                        tap(WeChatCoords.KEY_PASTE_POPUP)
                        handler.postDelayed({
                            if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
                            android.util.Log.i(TAG, "Step3: after bubble paste -> contact chain")
                            startContactChain()
                        }, WeChatData.delay(2_500L) + observePause())
                    }, WeChatData.delay(1_200L))
                }, WeChatData.delay(2_000L) + observePause())
            }, WeChatData.delay(1_000L))
        }, WeChatData.delay(1_200L) + observePause())
    }

    /**
     * 系统粘贴：对当前焦点输入框尝试 ACTION_SET_TEXT / ACTION_PASTE。
     * 微信可能 performAction 返回 true 但实际无效，因此只作优先路径，失败要有回退。
     */
    private fun trySystemPasteToFocused(text: String): Boolean {
        val focused = findFocusedEdit() ?: run {
            android.util.Log.i(TAG, "Step3: no focused EditText node")
            return false
        }
        val cls = focused.className?.toString().orEmpty()
        val pkg = focused.packageName?.toString().orEmpty()
        android.util.Log.i(TAG, "Step3: focused node class=$cls pkg=$pkg editable=${focused.isEditable}")
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text,
        )
        val setOk = try {
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Step3: ACTION_SET_TEXT error ${e.message}")
            false
        }
        android.util.Log.i(TAG, "Step3: ACTION_SET_TEXT returned $setOk")
        if (setOk && text.isNotEmpty()) {
            // 微信可能假成功；稍后由 searchResultsLikelyPresent / 后续步骤兜底
            return true
        }
        val pasteOk = try {
            focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            false
        }
        android.util.Log.i(TAG, "Step3: ACTION_PASTE returned $pasteOk")
        return pasteOk
    }

    /** 在已知被阉割的节点树里尽量找焦点输入框。 */
    private fun findFocusedEdit(): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        try {
            windows?.forEach { w ->
                try {
                    w.root?.let { roots.add(it) }
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "windows() failed: ${e.message}")
        }
        for (root in roots) {
            var focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            if (focus != null && isLikelyEditText(focus)) return focus
            val edit = findEditTextInTree(root, 0)
            if (edit != null) return edit
        }
        return null
    }

    private fun isLikelyEditText(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString().orEmpty()
        return node.isEditable || cls.contains("EditText") || cls.contains("TextView")
    }

    private fun findEditTextInTree(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 8) return null
        val cls = node.className?.toString().orEmpty()
        if (node.isEditable || cls.contains("EditText")) return node
        val count = try {
            node.childCount
        } catch (_: Exception) {
            0
        }
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            val found = findEditTextInTree(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    /** 粗判：搜索列表是否已出现（节点/最近窗口类名）。 */
    private fun searchResultsLikelyPresent(): Boolean {
        val cls = lastClassName
        if (cls.contains("ListView") || cls.contains("RecyclerView")) return true
        val root = rootInActiveWindow ?: return false
        return try {
            val texts = collectTexts(root, 0)
            texts.any { it.contains(WeChatData.value) || it.contains("联系人") || it.contains("聊天记录") }
        } catch (_: Exception) {
            false
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<String> = mutableListOf()): List<String> {
        if (node == null || depth > 6) return out
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) out.add(t)
        val count = try {
            node.childCount
        } catch (_: Exception) {
            0
        }
        for (i in 0 until minOf(count, 30)) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            collectTexts(child, depth + 1, out)
        }
        return out
    }

    private fun startContactChain() {
        val session = WeChatData.sessionId
        pasteChainActive = false
        WeChatData.updateIndex(4)
        android.util.Log.i(TAG, "Step4: scheduled, wait for results settle")
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
            }, WeChatData.delay(3_500L) + observePause())
        }, WeChatData.delay(3_000L) + observePause())
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
        }, WeChatData.delay(2_500L) + observePause())
    }

    private fun startMenuChain() {
        val session = WeChatData.sessionId
        WeChatData.updateIndex(6)
        handler.postDelayed({
            if (!isSessionAlive(session)) return@postDelayed
            android.util.Log.i(TAG, "Step6: tap video-call menu")
            tap(WeChatCoords.KEY_VIDEO_MENU)
        }, WeChatData.delay(2_000L) + observePause())
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
            WeChatData.cancel()
            clearPendingWork()
        }, WeChatData.delay(4_000L) + observePause())
    }

    private fun isSessionAlive(session: Long): Boolean {
        return WeChatData.sessionId == session && WeChatData.index != 0 &&
            !WeChatData.isExpired(sessionTimeoutMs())
    }

    private fun sessionTimeoutMs(): Long {
        return (SESSION_TIMEOUT_MS * WeChatData.delayScale).toLong().coerceAtLeast(SESSION_TIMEOUT_MS)
    }

    /** 慢速模式：每步之间的观察停顿。 */
    private fun observePause(): Long = WeChatData.pauseAfterStepMs

    private fun postStep(session: Long, ms: Long, block: () -> Unit) {
        val wait = WeChatData.delay(ms) + observePause()
        handler.postDelayed({
            if (!isSessionAlive(session)) return@postDelayed
            block()
        }, wait)
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
        val snap = WeChatDisplay.snapshot(this)
        val point = WeChatCoords.resolve(key, snap)
        if (point == null) {
            android.util.Log.e(TAG, "gesture: unknown coord key=$key ${WeChatDisplay.describe(snap)}")
            return false
        }
        android.util.Log.i(TAG, "gesture[$key] ${WeChatDisplay.describe(snap)} -> (${point.first.toInt()},${point.second.toInt()})")
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
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
            })
            android.util.Log.i(TAG, "relaunch WeChat requested")
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
