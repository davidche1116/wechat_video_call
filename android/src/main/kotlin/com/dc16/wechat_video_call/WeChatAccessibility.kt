package com.dc16.wechat_video_call

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * 微信自动拨打无障碍服务。
 *
 * 背景: 微信 8.0.78 起封锁了无障碍节点树（root 无子节点、bounds 全 0、
 * performAction 返回 true 但不执行），因此点击全部走 [dispatchGesture]
 * 坐标手势（无障碍服务自带手势注入权限，无需 root / adb）。
 *
 * 注意: 切勿使用 `Runtime.exec("input tap ...")` —— `input` 命令需要
 * INJECT_EVENTS 权限，只有 shell/root 才有，App 进程内调用必然失败，
 * 这就是上一版坐标方案没调通的根本原因（adb shell 下验证通过不代表
 * App 内可用）。
 *
 * 流程（WeChatData.index 状态机）:
 * 1 回到微信主页 -> 2 点搜索图标 -> 3 输入名字 -> 4 点第一个搜索结果
 * -> 5 点聊天页 + 号 -> 6 点视频通话菜单 -> 7 点确认对话框 -> 0 完成
 */
class WeChatAccessibility : AccessibilityService() {

    companion object {
        const val TAG = "WechatAccessibilityTag"

        /** 会话整体超时，超时后自动重置，避免卡死导致后续调用错乱。 */
        const val SESSION_TIMEOUT_MS = 45_000L

        /** 同一步骤的重复事件聚合冷却，避免事件洪泛导致重复点击。 */
        const val STEP_COOLDOWN_MS = 1_500L

        @Volatile
        var instance: WeChatAccessibility? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    /** 按步骤独立冷却：步骤 1 的返回键不能挡住步骤 2 的点击。 */
    private val lastActionAt = mutableMapOf<Int, Long>()
    private var lastClassName: String = ""
    private var nudgeSession: Long = -1
    private var lastMmEventAt: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        android.util.Log.i(TAG, "Service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        WeChatData.cancel()
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
            android.util.Log.w(TAG, "Session timeout, reset index. name=${WeChatData.value}")
            WeChatData.cancel()
            return
        }

        // 注意：部分设备（如魅族）全局丢弃 Log.d，关键链路日志必须用 Log.i
        android.util.Log.i(
            TAG,
            "Event: type=$eventType class=$className index=${WeChatData.index} session=${WeChatData.sessionId}"
        )
        lastClassName = className
        lastMmEventAt = System.currentTimeMillis()
        ensureNudge()

        when (WeChatData.index) {
            1 -> handleStep1ReturnToMain(className)
            3 -> handleStep3InputName(className)
            5 -> handleStep5ClickPlus(className)
        }
    }

    /**
     * 兜底推进器：微信停在某页不再发窗口事件时（如已在主页、无弹窗变化），
     * 每 2s 用最后一次的页面类名重放当前步骤，保证状态机不饿死。
     * 各步骤自带独立冷却，重复重放不会连点。
     */
    private val nudgeRunnable = object : Runnable {
        override fun run() {
            val session = WeChatData.sessionId
            if (WeChatData.index == 0 || session != nudgeSession) {
                nudgeSession = -1
                return
            }
            if (WeChatData.isExpired(SESSION_TIMEOUT_MS)) {
                android.util.Log.w(TAG, "Nudge: session timeout, reset")
                WeChatData.cancel()
                nudgeSession = -1
                return
            }
            android.util.Log.i(TAG, "Nudge: replay index=${WeChatData.index} class=$lastClassName")
            if (WeChatData.index == 1 && System.currentTimeMillis() - lastMmEventAt > 5000) {
                // 兜底：如果微信被退到了后台（无微信事件超 5s），自己把它再拉起来
                android.util.Log.i(TAG, "Nudge: no WeChat event for a while, relaunch")
                relaunchWeChat()
                lastMmEventAt = System.currentTimeMillis()
            }
            when (WeChatData.index) {
                1 -> handleStep1ReturnToMain(lastClassName)
                2 -> handleStep2ClickSearch(lastClassName)
                3 -> handleStep3InputName(lastClassName)
                5 -> handleStep5ClickPlus(lastClassName)
            }
            handler.postDelayed(this, 2000L)
        }
    }

    private fun ensureNudge() {
        if (nudgeSession == WeChatData.sessionId) return
        nudgeSession = WeChatData.sessionId
        handler.postDelayed(nudgeRunnable, 2000L)
    }

    // ---------- 步骤 ----------

    private fun handleStep1ReturnToMain(className: String) {
        if (seenMainSession != WeChatData.sessionId) {
            seenMainSession = WeChatData.sessionId
            seenMain = false
        }
        if (className == WeChatActivity.INDEX.id) {
            seenMain = true
            // LauncherUI 会恢复上次的 Tab（微信/通讯录/发现/我），搜索图标坐标
            // 只在“微信”Tab 下有效，所以先点底部第一个 Tab 再走定时链点搜索
            android.util.Log.i(TAG, "Step1: on main page, tap home tab")
            WeChatData.updateIndex(2)
            tap(WeChatCoords.KEY_HOME_TAB)
            startSearchChain()
            return
        }
        // 非主页：只有“确定是子页面”才按返回。刚启动时收到的常常是
        // FrameLayout 之类的通用类名，此时可能本来就在主页，盲按返回会
        // 直接退出微信回到调用方 App（seenMain 避免的就是这个）。
        val knownSubPage = className.contains("Search") ||
            className.contains("FTS") ||
            className.contains("Chat") ||
            className.contains("chatting") ||
            (className.startsWith("com.tencent.mm.") && className != WeChatActivity.INDEX.id)
        val shouldBack = className.isNotEmpty() && (seenMain || knownSubPage)
        if (!shouldBack) {
            android.util.Log.i(TAG, "Step1: waiting for definitive page, class=$className")
            return
        }
        if (consumeCooldown(1) && consumeBackPress()) {
            android.util.Log.i(TAG, "Step1: not on main page, back")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private var seenMainSession: Long = -1
    private var seenMain: Boolean = false

    /**
     * 步骤 1 的返回键限流：微信子页面的内容事件类名常常是 FrameLayout 等通用名，
     * 盲目连按返回可能一路退到桌面。每会话最多按 3 次，之后只等明确的页面事件。
     */
    private var backPressSession: Long = -1
    private var backPressCount: Int = 0

    private fun consumeBackPress(): Boolean {
        if (backPressSession != WeChatData.sessionId) {
            backPressSession = WeChatData.sessionId
            backPressCount = 0
        }
        if (backPressCount >= 3) {
            android.util.Log.w(TAG, "Step1: back press budget exhausted, waiting")
            return false
        }
        backPressCount++
        return true
    }

    /** 定时链：切到微信 Tab 后点搜索图标（事件驱动的步骤 2 只做 nudge 重试）。 */
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
        android.util.Log.i(TAG, "Step2: tap search icon")
        if (tap(WeChatCoords.KEY_SEARCH_ICON)) {
            WeChatData.updateIndex(3)
        }
    }

    /**
     * 步骤 3：搜索框输入。节点写入全灭，改剪贴板 + 纯手势：
     * 剪贴板写入中文 → 点搜索框聚焦 → 长按调出“粘贴”气泡 → 点气泡粘贴，
     * 然后进“点第一个结果”定时链。
     */
    private fun handleStep3InputName(className: String) {
        if (!className.contains("Search") && !className.contains("FTS") &&
            className != WeChatActivity.SEARCH.id
        ) return
        attemptStep3()
    }

    /** true = 只做到长按就停机（截屏标定“粘贴”坐标用），false = 跑全流程。 */
    private val CALIBRATE_PASTE = false

    private fun attemptStep3() {
        if (!consumeCooldown(3)) return
        val session = WeChatData.sessionId
        val query = WeChatData.value
        android.util.Log.i(TAG, "Step3: clipboard paste query=$query")
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("wechat_search", query))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Step3: clipboard failed: ${e.message}")
            return
        }
        tap(WeChatCoords.KEY_SEARCH_BOX)
        handler.postDelayed({
            if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
            android.util.Log.i(TAG, "Step3: long-press search box")
            longPress(WeChatCoords.KEY_SEARCH_BOX, 600)
            if (CALIBRATE_PASTE) {
                android.util.Log.i(TAG, "Step3: calibration stop, check paste popup now")
                WeChatData.cancel()
                return@postDelayed
            }
            handler.postDelayed({
                if (!isSessionAlive(session) || WeChatData.index != 3) return@postDelayed
                android.util.Log.i(TAG, "Step3: tap paste popup")
                tap(WeChatCoords.KEY_PASTE_POPUP)
                startContactChain()
            }, 1100L)
        }, 700L)
    }

    /**
     * 定时链：点第一个搜索结果 → 直接定时点聊天页 + 号（不等聊天页事件，
     * ChatUI 的窗口事件可能被微信屏蔽，等事件会饿死）。
     * 事件驱动的步骤 5 保留做后备：谁先跑到就把 index 置 6，另一路自动跳过。
     */
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

    /**
     * + 号点击只排期一次：聊天页刚打开时有转场动画，立刻点手势会被吞；
     * 延时 1.5s 等页面落定。事件驱动和定时链谁先到谁排期，另一个自动跳过。
     */
    private var plusScheduledFor: Long = -1

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

    /**
     * 定时链: + 菜单弹出后点“视频通话”，确认框弹出后点“视频/语音通话”。
     * 延迟按 8.0.78 实测时序放宽（菜单约 800ms，确认框约 800ms）。
     */
    private fun startMenuChain() {
        val session = WeChatData.sessionId
        handler.postDelayed({
            if (!isSessionAlive(session)) return@postDelayed
            android.util.Log.i(TAG, "Step6: tap video-call menu item")
            tap(WeChatCoords.KEY_VIDEO_MENU)
        }, 1_200L)
        handler.postDelayed({
            if (!isSessionAlive(session)) return@postDelayed
            android.util.Log.i(TAG, "Step7: tap confirm video=${WeChatData.video}")
            val key = if (WeChatData.video) {
                WeChatCoords.KEY_VIDEO_CONFIRM
            } else {
                WeChatCoords.KEY_VOICE_CONFIRM
            }
            if (tap(key)) {
                android.util.Log.i(TAG, "Call initiated successfully")
            }
            WeChatData.cancel()
        }, 2_500L)
        // 先占住 index，防止后续微信事件误触发步骤 1~5；定时链结束时 cancel()
        WeChatData.updateIndex(6)
    }

    private fun isSessionAlive(session: Long): Boolean {
        return WeChatData.sessionId == session && WeChatData.index != 0 &&
            !WeChatData.isExpired(SESSION_TIMEOUT_MS)
    }

    // ---------- 基础能力 ----------

    /**
     * 按 key 点击相对坐标点。经 dispatchGesture 注入，无需节点树。
     * @return 手势是否已下发（true 不代表微信已响应）。
     */
    fun tap(key: String): Boolean {
        val snap = WeChatDisplay.snapshot(resources)
        val point = WeChatCoords.resolve(key, snap)
        if (point == null) {
            android.util.Log.e(TAG, "tap: unknown coord key=$key display=${WeChatDisplay.describe(snap)}")
            return false
        }
        android.util.Log.i(TAG, "tap[$key] resolve=${WeChatDisplay.describe(snap)} -> (${point.first},${point.second})")
        return dispatchTap(point.first, point.second, key)
    }

    /** 从服务侧把微信主页拉起来（NEW_TASK），用于微信被意外退到后台时自救。 */
    private fun relaunchWeChat() {
        try {
            val intent = android.content.Intent().apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "relaunch failed: ${e.message}")
        }
    }

    /** 通话中挂断（一次性手势，不依赖状态机；下发结果见 logcat）。 */
    fun requestHangUp(): Boolean {
        handler.post { tap(WeChatCoords.KEY_HANG_UP) }
        android.util.Log.i(TAG, "HangUp requested")
        return true
    }

    private fun dispatchTap(x: Float, y: Float, label: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            android.util.Log.e(TAG, "tap[$label]: dispatchGesture requires API 24+")
            return false
        }
        return try {
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(StrokeDescription(path, 0, 60))
                .build()
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        android.util.Log.i(TAG, "tap[$label] completed at ($x,$y)")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        android.util.Log.i(TAG, "tap[$label] cancelled at ($x,$y)")
                    }
                },
                null
            )
            if (!dispatched) {
                android.util.Log.e(TAG, "tap[$label] dispatchGesture returned false")
            }
            dispatched
        } catch (e: Exception) {
            android.util.Log.e(TAG, "tap[$label] failed: ${e.message}")
            false
        }
    }

    /** 按 key 长按（用于调出“粘贴”菜单等），纯手势，不依赖节点树。 */
    fun longPress(key: String, durationMs: Long): Boolean {
        val snap = WeChatDisplay.snapshot(resources)
        val point = WeChatCoords.resolve(key, snap)
        if (point == null) {
            android.util.Log.e(TAG, "longPress: unknown coord key=$key")
            return false
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            android.util.Log.e(TAG, "longPress: dispatchGesture requires API 24+")
            return false
        }
        return try {
            val path = Path().apply { moveTo(point.first, point.second) }
            val gesture = GestureDescription.Builder()
                .addStroke(StrokeDescription(path, 0, durationMs))
                .build()
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        android.util.Log.i(TAG, "longPress[$key] completed")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        android.util.Log.i(TAG, "longPress[$key] cancelled")
                    }
                },
                null
            )
            if (!dispatched) android.util.Log.e(TAG, "longPress[$key] dispatch returned false")
            dispatched
        } catch (e: Exception) {
            android.util.Log.e(TAG, "longPress[$key] failed: ${e.message}")
            false
        }
    }

    /** 某步骤冷却通过才返回 true 并记录该步骤的动作时间。 */
    private fun consumeCooldown(step: Int): Boolean {
        val now = System.currentTimeMillis()
        if (now - (lastActionAt[step] ?: 0L) < STEP_COOLDOWN_MS) return false
        lastActionAt[step] = now
        return true
    }
}
