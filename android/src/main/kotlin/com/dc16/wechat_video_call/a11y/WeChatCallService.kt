package com.dc16.wechat_video_call.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dc16.wechat_video_call.PluginEventBus
import com.dc16.wechat_video_call.config.CallConfig
import com.dc16.wechat_video_call.config.ConfigStore
import com.dc16.wechat_video_call.config.ErrorCodes
import com.dc16.wechat_video_call.config.StepCoord
import com.dc16.wechat_video_call.config.StepIds
import com.dc16.wechat_video_call.display.WeChatDisplay
import java.util.concurrent.atomic.AtomicLong

/**
 * Accessibility service that dials WeChat using user-calibrated coordinates.
 *
 * Search input is fixed to: write clipboard -> long-press search box -> tap paste bubble.
 * No IME clipboard, no a11y SET_TEXT fallback.
 */
class WeChatCallService : AccessibilityService() {

    companion object {
        private const val TAG = "WvcCallService"
        private val sessionSeq = AtomicLong(0)

        @Volatile
        var instance: WeChatCallService? = null
            private set

        const val WECHAT_PKG = "com.tencent.mm"
        const val LAUNCHER_UI = "com.tencent.mm/.ui.LauncherUI"

        // Prepare-navigation delays (absolute offsets from prepareForStep start).
        private const val PREPARE_STEP_DELAY_MS = 500L
        private const val PREPARE_SEARCH_TO_LP_MS = 1_200L
        private const val PREPARE_PASTE_CHAIN_MS = 2_200L

        /** Floor used only when a planned step has no delay. */
        private const val DEFAULT_STEP_DELAY_MS = 200
        private const val LONG_PRESS_MIN_MS = 400

        /**
         * Extra settle after a long-press gesture *completes* so the system
         * paste/context menu can appear before the next tap. 2.0.1 only waited
         * delayAfterMs from dispatch start (400ms), which raced the 600ms hold
         * and missed the paste bubble.
         */
        private const val LONG_PRESS_SETTLE_MIN_MS =
            com.dc16.wechat_video_call.config.CallTiming.LONG_PRESS_SETTLE_MIN_MS.toLong()
    }

    private val handler = Handler(Looper.getMainLooper())

    private data class Planned(
        val id: String,
        val action: String,
        val delayAfterMs: Int,
        val coord: StepCoord?,
        val durationMs: Int?,
    )

    @Volatile private var sessionId = 0L
    @Volatile private var active = false
    @Volatile private var videoMode = true
    @Volatile private var contactName = ""
    @Volatile private var delayScale = 1.0
    @Volatile private var pauseAfterStepMs = 0
    @Volatile private var startedAt = 0L
    @Volatile private var stepIndex = 0
    @Volatile private var sessionTimeoutMs = 45_000L
    private var plan: List<Planned> = emptyList()

    private val timeoutRunnable = Runnable {
        if (!active || sessionId != sessionSeq.get()) return@Runnable
        // One-shot deadline check: posted once at session start to sessionTimeoutMs.
        if (System.currentTimeMillis() - startedAt < sessionTimeoutMs) return@Runnable
        Log.w(TAG, "session timeout sid=$sessionId")
        fail(ErrorCodes.SESSION_TIMEOUT, currentStepId(), "会话超时")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "service connected")
        PluginEventBus.emit(
            "permissionStatus",
            mapOf("accessibility" to true, "overlay" to null),
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clearWork()
        instance = null
        active = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        cancelSession(emitUserCancel = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window events are optional hints only; execution is timer-driven.
        if (!active) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != WECHAT_PKG) return
        // reserved for future className hints
    }

    private fun currentStepId(): String? =
        plan.getOrNull(stepIndex)?.id

    private fun clearWork() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun scaled(ms: Int): Long {
        val s = if (delayScale > 0) delayScale else 1.0
        val clamped = ms.coerceIn(
            com.dc16.wechat_video_call.config.CallTiming.MIN_STEP_DELAY_MS,
            com.dc16.wechat_video_call.config.CallTiming.MAX_STEP_DELAY_MS,
        )
        return (clamped * s).toLong().coerceAtLeast(0L) + pauseAfterStepMs.toLong()
    }

    /** Hold time of the gesture just dispatched (ms). */
    private fun gestureHoldMs(action: String, durationMs: Int?): Long {
        val raw = (durationMs ?: if (action == "longPress") 600 else 80).toLong()
        return raw.coerceAtLeast(40L)
    }

    /**
     * Delay from gesture start until the next step.
     * Must cover the hold itself — dispatchGesture is async and returns before
     * the stroke finishes — plus post-step UI settle.
     */
    private fun nextStepDelayMs(planned: Planned): Long {
        val holdMs = gestureHoldMs(planned.action, planned.durationMs)
        val settle = scaled(planned.delayAfterMs)
        // Floor after scale so fast delayScale cannot starve the paste menu.
        val settleFloored = if (planned.action == "longPress") {
            settle.coerceAtLeast(LONG_PRESS_SETTLE_MIN_MS)
        } else {
            settle
        }
        return holdMs + settleFloored
    }

    private fun writeClipboard(text: String): Boolean {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("wechat_search", text))
            true
        } catch (e: Exception) {
            Log.e(TAG, "clipboard write failed: ${e.message}")
            PluginEventBus.emitFailed(
                ErrorCodes.INPUT_FAILED,
                StepIds.SEARCH_BOX_LONG_PRESS,
                "剪贴板写入失败（Android 10+ 限制后台写入）",
            )
            false
        }
    }

    fun cancelSession(emitUserCancel: Boolean = false) {
        if (!active && sessionId == 0L) return
        active = false
        clearWork()
        if (emitUserCancel) {
            PluginEventBus.emitFailed(
                ErrorCodes.USER_CANCELLED,
                currentStepId(),
                "用户取消",
                sessionId,
            )
        }
        sessionId = 0L
    }

    fun hangUpNow(): Boolean {
        val config = ConfigStore(this).loadOrInit()
        val coord = config.coord(StepIds.HANG_UP)
        if (coord == null) {
            PluginEventBus.emitFailed(
                ErrorCodes.HANGUP_COORD_MISSING,
                StepIds.HANG_UP,
                "缺少挂断坐标",
            )
            return false
        }
        val ok = dispatchCoord(coord, "hangUp", durationMs = 80)
        PluginEventBus.emit("hangUpResult", mapOf("ok" to ok))
        return ok
    }

    fun debugTap(stepId: String, openWeChatFirst: Boolean = true): Boolean {
        val config = ConfigStore(this).loadOrInit()
        val step = config.step(stepId) ?: return false
        val coord = step.coord ?: return false
        // When chained from prepareForStep, WeChat is already foreground —
        // re-opening it with RESET_TASK_IF_NEEDED would bounce back to home.
        if (openWeChatFirst) openWeChat()
        return dispatchCoord(coord, step.action, step.durationMs ?: if (step.action == "longPress") 600 else 80)
    }

    /**
     * Navigate/prepare UI before recording a wizard step.
     * Uses already-recorded coordinates only.
     */
    fun prepareForStep(stepId: String): Boolean {
        val config = ConfigStore(this).loadOrInit()
        if (!openWeChat()) return false
        val h = handler
        when (stepId) {
            StepIds.SEARCH_ICON -> {
                // Just ensure WeChat is foreground.
            }
            StepIds.SEARCH_BOX_LONG_PRESS -> {
                if (config.hasCoord(StepIds.SEARCH_ICON)) {
                    h.postDelayed(
                        { debugTap(StepIds.SEARCH_ICON, openWeChatFirst = false) },
                        PREPARE_STEP_DELAY_MS,
                    )
                }
            }
            StepIds.PASTE_BUBBLE -> {
                if (config.hasCoord(StepIds.SEARCH_ICON)) {
                    h.postDelayed(
                        { debugTap(StepIds.SEARCH_ICON, openWeChatFirst = false) },
                        PREPARE_STEP_DELAY_MS,
                    )
                }
                val lpDelay = if (config.hasCoord(StepIds.SEARCH_ICON)) PREPARE_SEARCH_TO_LP_MS else PREPARE_STEP_DELAY_MS
                if (config.hasCoord(StepIds.SEARCH_BOX_LONG_PRESS)) {
                    h.postDelayed(
                        { debugTap(StepIds.SEARCH_BOX_LONG_PRESS, openWeChatFirst = false) },
                        lpDelay,
                    )
                }
            }
            StepIds.SEARCH_RESULT -> {
                if (config.hasCoord(StepIds.SEARCH_ICON)) {
                    h.postDelayed(
                        { debugTap(StepIds.SEARCH_ICON, openWeChatFirst = false) },
                        PREPARE_STEP_DELAY_MS,
                    )
                }
                if (config.hasCoord(StepIds.SEARCH_BOX_LONG_PRESS)) {
                    h.postDelayed(
                        { debugTap(StepIds.SEARCH_BOX_LONG_PRESS, openWeChatFirst = false) },
                        PREPARE_SEARCH_TO_LP_MS,
                    )
                }
                if (config.hasCoord(StepIds.PASTE_BUBBLE)) {
                    h.postDelayed(
                        { debugTap(StepIds.PASTE_BUBBLE, openWeChatFirst = false) },
                        PREPARE_PASTE_CHAIN_MS,
                    )
                }
            }
            StepIds.PLUS_BUTTON, StepIds.VIDEO_MENU, StepIds.VIDEO_CONFIRM,
            StepIds.VOICE_CONFIRM, StepIds.HANG_UP,
            -> {
                // Too deep to auto-navigate safely; user should open the page.
            }
        }
        return true
    }

    /** Dispatch long-press on recorded search box so paste bubble can appear. */
    fun longPressSearchBoxNow(): Boolean {
        val config = ConfigStore(this).loadOrInit()
        val coord = config.coord(StepIds.SEARCH_BOX_LONG_PRESS) ?: return false
        val duration = config.step(StepIds.SEARCH_BOX_LONG_PRESS)?.durationMs
            ?: config.timing.longPressDurationMs
        return dispatchCoord(coord, "longPress", duration)
    }

    fun startCall(
        name: String,
        video: Boolean,
        toast: Boolean,
        delayScale: Double,
        pauseAfterStepMs: Int,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            PluginEventBus.emitFailed(ErrorCodes.BLANK_NAME, null, "联系人名称为空")
            return false
        }
        if (PluginEventBus.calibrationActive) {
            PluginEventBus.emitFailed(
                ErrorCodes.CALIBRATION_IN_PROGRESS,
                null,
                "校准进行中",
            )
            return false
        }
        if (!isAccessibilityEnabledInternal()) {
            PluginEventBus.emitFailed(
                ErrorCodes.ACCESSIBILITY_DISABLED,
                null,
                "无障碍未开启",
            )
            return false
        }
        if (!isWeChatInstalled()) {
            PluginEventBus.emitFailed(ErrorCodes.WECHAT_NOT_INSTALLED, null, "未安装微信")
            return false
        }

        val store = ConfigStore(this)
        val config = store.load()
        if (config == null) {
            PluginEventBus.emitFailed(ErrorCodes.CONFIG_MISSING, null, "请先完成坐标校准")
            return false
        }

        val required = if (video) CallConfig.videoRequiredIds() else CallConfig.voiceRequiredIds()
        val missing = required.filter { !config.hasCoord(it) }
        if (missing.isNotEmpty()) {
            PluginEventBus.emitFailed(
                ErrorCodes.CONFIG_MISSING,
                missing.first(),
                "缺少坐标: ${missing.joinToString()}",
            )
            return false
        }

        val confirmId = if (video) StepIds.VIDEO_CONFIRM else StepIds.VOICE_CONFIRM
        val built = buildPlan(config, confirmId)
        if (built.isEmpty()) {
            PluginEventBus.emitFailed(ErrorCodes.CONFIG_MISSING, null, "计划步骤为空")
            return false
        }

        clearWork()
        sessionId = sessionSeq.incrementAndGet()
        active = true
        videoMode = video
        contactName = trimmed
        this.delayScale = if (delayScale > 0) delayScale else 1.0
        this.pauseAfterStepMs = pauseAfterStepMs.coerceAtLeast(0)
        startedAt = System.currentTimeMillis()
        sessionTimeoutMs = (config.timing.sessionTimeoutMs * this.delayScale).toLong()
        stepIndex = 0
        plan = built

        if (!writeClipboard(trimmed)) {
            fail(ErrorCodes.INPUT_FAILED, StepIds.SEARCH_BOX_LONG_PRESS, "剪贴板写入失败")
            return false
        }
        PluginEventBus.emit(
            "sessionStarted",
            mapOf(
                "sessionId" to sessionId,
                "name" to trimmed,
                "video" to video,
                "toast" to toast,
            ),
        )

        if (!openWeChat()) {
            fail(ErrorCodes.OPEN_WECHAT_FAILED, StepIds.OPEN_WECHAT, "无法打开微信")
            return false
        }

        val snap = WeChatDisplay.snapshot(this)
        emitConfigMismatchIfNeeded(config, snap)

        // Dump resolved coordinates so hosts can see exactly what will be tapped.
        // Uses the same resolve path as dispatchCoord (relative-first).
        val resolved = built.map { p ->
            val c = p.coord
            val xy = if (c != null) resolveDispatchXY(c, snap) else null
            mapOf(
                "stepId" to p.id,
                "action" to p.action,
                "fx" to c?.fx,
                "fy" to c?.fy,
                "pixelX" to c?.pixelX,
                "pixelY" to c?.pixelY,
                "resolvedX" to xy?.x,
                "resolvedY" to xy?.y,
                "resolveSource" to xy?.source,
            )
        }
        PluginEventBus.emit(
            "sessionPlan",
            mapOf(
                "sessionId" to sessionId,
                "screen" to mapOf(
                    "w" to snap.widthPx,
                    "h" to snap.heightPx,
                    "statusBar" to snap.statusBarPx,
                ),
                "steps" to resolved,
            ),
        )

        // One-shot deadline: fires once at sessionTimeoutMs.
        handler.postDelayed(timeoutRunnable, sessionTimeoutMs)
        // Wait for WeChat to fully come to foreground before first gesture.
        handler.postDelayed({ runCurrent() }, scaled(config.timing.launchSettleMs))
        return true
    }

    private fun emitConfigMismatchIfNeeded(config: CallConfig, snap: WeChatDisplay.Snapshot) {
        val device = config.deviceJson ?: return
        val recW = device.optInt("widthPx", -1)
        val recH = device.optInt("heightPx", -1)
        if (recW <= 0 || recH <= 0) return
        if (recW == snap.widthPx && recH == snap.heightPx) return
        PluginEventBus.emit(
            "configMismatch",
            mapOf(
                "errorCode" to ErrorCodes.CONFIG_MISMATCH,
                "recordedWidthPx" to recW,
                "recordedHeightPx" to recH,
                "currentWidthPx" to snap.widthPx,
                "currentHeightPx" to snap.heightPx,
                "message" to "设备显示参数与校准时不一致，点击可能偏移",
            ),
        )
    }

    private fun isAccessibilityEnabledInternal(): Boolean = instance != null

    fun isWeChatInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(WECHAT_PKG, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openWeChat(): Boolean {
        return try {
            // Explicit component avoids dual-WeChat app chooser on some ROMs.
            val intent = Intent().apply {
                setClassName(WECHAT_PKG, "com.tencent.mm.ui.LauncherUI")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                setPackage(WECHAT_PKG)
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallback = packageManager.getLaunchIntentForPackage(WECHAT_PKG)
                if (fallback != null) {
                    fallback.setPackage(WECHAT_PKG)
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(fallback)
                    true
                } else {
                    Log.e(TAG, "open wechat failed: ${e.message}")
                    false
                }
            } catch (e2: Exception) {
                Log.e(TAG, "open wechat failed: ${e2.message}")
                false
            }
        }
    }

    private fun buildPlan(config: CallConfig, confirmId: String): List<Planned> {
        val ids = mutableListOf<String>()
        // homeTab only if user recorded it — helps settle on 会话列表 page.
        if (config.hasCoord(StepIds.HOME_TAB)) {
            ids.add(StepIds.HOME_TAB)
        }
        ids.add(StepIds.SEARCH_ICON)
        ids.add(StepIds.SEARCH_BOX_LONG_PRESS)
        ids.add(StepIds.PASTE_BUBBLE)
        ids.add(StepIds.SEARCH_RESULT)
        ids.add(StepIds.PLUS_BUTTON)
        ids.add(StepIds.VIDEO_MENU)
        ids.add(confirmId)

        return ids.mapNotNull { id ->
            val step = config.step(id) ?: return@mapNotNull null
            // Prefer per-step delay; fall back to CallTiming named fields (P0:
            // no hardcoded long waits — budget 200..1000ms after clamp).
            val delay = step.delayAfterMs.takeIf { it > 0 }
                ?: when (id) {
                    StepIds.SEARCH_RESULT -> config.timing.searchResultDelayMs
                    StepIds.PLUS_BUTTON -> config.timing.plusButtonDelayMs
                    StepIds.VIDEO_MENU -> config.timing.videoMenuDelayMs
                    StepIds.VIDEO_CONFIRM, StepIds.VOICE_CONFIRM -> config.timing.confirmDelayMs
                    else -> config.timing.defaultDelayAfterMs
                }
            Planned(
                id = id,
                action = step.action,
                delayAfterMs = config.timing.clampDelay(
                    if (delay > 0) delay else DEFAULT_STEP_DELAY_MS,
                ),
                coord = step.coord,
                durationMs = step.durationMs
                    ?: if (step.action == "longPress") {
                        config.timing.longPressDurationMs.coerceAtLeast(LONG_PRESS_MIN_MS)
                    } else {
                        80
                    },
            )
        }
    }

    private fun runCurrent() {
        if (!active) return
        val sid = sessionId
        if (sid == 0L || sid != sessionSeq.get()) return
        if (System.currentTimeMillis() - startedAt > sessionTimeoutMs) {
            fail(ErrorCodes.SESSION_TIMEOUT, currentStepId(), "会话超时")
            return
        }
        val planned = plan.getOrNull(stepIndex)
        if (planned == null) {
            active = false
            clearWork()
            PluginEventBus.emitSuccess(sid, videoMode)
            sessionId = 0L
            return
        }

        val coord = planned.coord
        if (coord == null) {
            fail(ErrorCodes.STEP_COORD_MISSING, planned.id, "步骤缺少坐标")
            return
        }

        val snap = WeChatDisplay.snapshot(this)
        val resolved = resolveDispatchXY(coord, snap)
        PluginEventBus.emitStepProgress(
            sid,
            planned.id,
            stepIndex,
            "running",
            message = "${planned.action} (${resolved.x.toInt()},${resolved.y.toInt()}) " +
                "from fx=${coord.fx} fy=${coord.fy} src=${resolved.source}",
        )

        val ok = when (planned.action) {
            "longPress" -> dispatchCoord(coord, planned.action, planned.durationMs ?: 600)
            else -> dispatchCoord(coord, "tap", planned.durationMs ?: 80)
        }
        if (!ok) {
            fail(ErrorCodes.GESTURE_DISPATCH_FAILED, planned.id, "手势注入失败")
            return
        }

        PluginEventBus.emitStepProgress(
            sid,
            planned.id,
            stepIndex,
            "completed",
            message = "tapped (${resolved.x.toInt()},${resolved.y.toInt()})",
        )
        val delay = nextStepDelayMs(planned)
        stepIndex += 1
        handler.postDelayed({ runCurrent() }, delay)
    }

    /** Resolved pixel used for both telemetry and dispatch (must stay in sync). */
    private data class DispatchXY(val x: Float, val y: Float, val source: String)

    /**
     * Relative coords (fx/fy) are primary — resolution-independent by design.
     * Recorded pixels are a scaled fallback when relative values are missing.
     */
    private fun resolveDispatchXY(
        coord: StepCoord,
        snap: WeChatDisplay.Snapshot,
    ): DispatchXY {
        val hasRelative = coord.fx != 0.0 || coord.fy != 0.0 ||
            (coord.pixelX == null && coord.pixelY == null)
        if (hasRelative) {
            val (rx, ry) = WeChatDisplay.resolve(coord.fx, coord.fy, coord.fySpace, snap)
            return DispatchXY(rx, ry, "relative")
        }
        val recW = (coord.widthPx ?: snap.widthPx).coerceAtLeast(1)
        val recH = (coord.heightPx ?: snap.heightPx).coerceAtLeast(1)
        val x = (coord.pixelX ?: 0) * snap.widthPx.toFloat() / recW
        val y = (coord.pixelY ?: 0) * snap.heightPx.toFloat() / recH
        return DispatchXY(x, y, "pixel")
    }

    private fun dispatchCoord(coord: StepCoord, action: String, durationMs: Int?): Boolean {
        val snap = WeChatDisplay.snapshot(this)
        val resolved = resolveDispatchXY(coord, snap)
        val x = resolved.x
        val y = resolved.y
        val duration = (durationMs ?: if (action == "longPress") 600 else 80).toLong()
            .coerceAtLeast(40L)
        Log.i(
            TAG,
            "dispatch $action ($x,$y) dur=$duration src=${resolved.source} " +
                "screen=${snap.widthPx}x${snap.heightPx} sb=${snap.statusBarPx}",
        )
        PluginEventBus.emit(
            "gesture",
            mapOf(
                "action" to action,
                "x" to x,
                "y" to y,
                "source" to resolved.source,
                "durationMs" to duration,
                "screenW" to snap.widthPx,
                "screenH" to snap.heightPx,
            ),
        )
        return dispatchGestureAt(x, y, duration)
    }

    private fun dispatchGestureAt(x: Float, y: Float, durationMs: Long): Boolean {
        return try {
            if (instance == null) return false
            val path = Path().apply { moveTo(x, y) }
            // Keep keepAlive=false for taps; long-press uses longer duration.
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "gesture cancelled at $x,$y")
                    }
                },
                handler,
            )
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture error: ${e.message}")
            false
        }
    }

    private fun fail(code: String, stepId: String?, message: String) {
        active = false
        clearWork()
        PluginEventBus.emitFailed(code, stepId, message, sessionId)
        sessionId = 0L
    }
}
