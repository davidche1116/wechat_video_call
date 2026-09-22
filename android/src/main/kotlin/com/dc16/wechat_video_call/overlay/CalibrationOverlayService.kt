package com.dc16.wechat_video_call.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dc16.wechat_video_call.PluginEventBus
import com.dc16.wechat_video_call.a11y.WeChatCallService
import com.dc16.wechat_video_call.config.CallConfig
import com.dc16.wechat_video_call.config.ConfigStore
import com.dc16.wechat_video_call.config.StepCoord
import com.dc16.wechat_video_call.config.StepIds
import com.dc16.wechat_video_call.display.WeChatDisplay

/**
 * Calibration floating UI.
 *
 * - No floating ball (removed as useless).
 * - Draggable step card on the right.
 * - Record controls auto-position opposite to the tapped point so bottom
 *   WeChat chrome (Tab / +) stays clickable; panel itself is draggable.
 */
class CalibrationOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.dc16.wechat_video_call.action.START_CALIBRATION"
        const val ACTION_STOP = "com.dc16.wechat_video_call.action.STOP_CALIBRATION"

        private const val GREEN = "#07C160"
        private const val GREEN_DARK = "#05A050"
        private const val INK = "#1A1A1A"
        private const val SUB = "#6B7280"
        private const val CARD_BG = "#F7FFFA"
        private const val LINE = "#D9EDE3"
        private const val CARD_WIDTH_DP = 300
        private const val PANEL_WIDTH_DP = 280
        private const val DEFAULT_CARD_X_DP = 8
        private const val DEFAULT_CARD_Y_DP = 72
        private const val PASTE_PREPARE_NAV_MS = 300L
        private const val PASTE_LONGPRESS_MS = 2000L
        private const val PASTE_RECORD_MS = 2800L
        private const val PREPARE_NAV_MS = 400L
        private const val TEST_TAP_MS = 900L

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var wm: WindowManager
    private lateinit var store: ConfigStore
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cardView: View? = null
    private var cardParams: WindowManager.LayoutParams? = null
    private var cardPosX: Int? = null
    private var cardPosY: Int? = null
    private var recordLayer: View? = null

    private val wizardOrder = StepIds.WIZARD_ORDER
    private var wizardIndex = 0
    private var pendingFx = 0.0
    private var pendingFy = 0.0
    private var pendingPx = 0
    private var pendingPy = 0
    private var recordArmed = false
    private var recordRoot: FrameLayout? = null
    private var controlPanel: LinearLayout? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var crosshairView: View? = null
    private var coordTextView: TextView? = null
    private var hintText: TextView? = null
    private var nudgeRowView: LinearLayout? = null
    private var recordSnap: WeChatDisplay.Snapshot? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        store = ConfigStore(this)
        isRunning = true
        PluginEventBus.calibrationActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
                    stopSelfSafely()
                    return START_NOT_STICKY
                }
                startAsForeground()
                store.loadOrInit()
                wizardIndex = firstIncompleteIndex()
                showCard()
                emitProgress("wizardOpen", currentStepId())
            }
            ACTION_STOP -> stopSelfSafely()
            else -> if (!isRunning) stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    // ───────────────────────── Internal actions (direct calls) ─────────────────────────

    private fun jumpToStep(id: String) {
        val idx = wizardOrder.indexOf(id)
        if (idx >= 0) {
            wizardIndex = idx
            emitProgress("jump", id)
            showCard()
        }
    }

    private fun goPrevStep() {
        if (wizardIndex > 0) wizardIndex -= 1
        emitProgress("prev", currentStepId())
        showCard()
    }

    private fun goNextStep() {
        if (wizardIndex < wizardOrder.lastIndex) {
            wizardIndex += 1
            emitProgress("next", currentStepId())
        } else {
            val cfg = store.loadOrInit()
            val missing = CallConfig.videoRequiredIds().filter { !cfg.hasCoord(it) }
            toast(
                if (missing.isEmpty()) "已到最后一步，视频必录点已齐"
                else "仍缺: ${missing.joinToString()}",
            )
        }
        showCard()
    }

    private fun openWeChatAction() {
        openWeChatUi()
    }

    private fun prepareCurrentStep() {
        val stepId = currentStepId()
        val svc = WeChatCallService.instance
        if (svc == null) {
            toast("请先开启无障碍服务")
            return
        }
        openWeChatUi()
        mainHandler.postDelayed({
            svc.prepareForStep(stepId)
            toast("已尝试定位「${titleFor(stepId)}」")
        }, PREPARE_NAV_MS)
    }

    private fun pasteLongPressAction() {
        val svc = WeChatCallService.instance
        if (svc == null) {
            toast("请先开启无障碍服务")
            return
        }
        val cfg = store.loadOrInit()
        if (!cfg.hasCoord(StepIds.SEARCH_BOX_LONG_PRESS)) {
            toast("请先录好「长按搜索框」")
            showCard()
            return
        }
        hideCard()
        openWeChatUi()
        toast("正在长按搜索框…")
        // Chain: navigate → long-press → arm record. debugTap must not re-open WeChat.
        mainHandler.postDelayed({
            svc.prepareForStep(StepIds.PASTE_BUBBLE)
        }, PASTE_PREPARE_NAV_MS)
        mainHandler.postDelayed({ svc.longPressSearchBoxNow() }, PASTE_LONGPRESS_MS)
        mainHandler.postDelayed({ beginRecord(fromPastePrepare = true) }, PASTE_RECORD_MS)
    }

    private fun clearCurrentStep() {
        val id = currentStepId()
        val cfg = store.loadOrInit()
        store.save(cfg.clearCoord(id))
        toast("已清除「${titleFor(id)}」")
        emitProgress("cleared", id)
        showCard()
    }

    private fun testCurrentStep() {
        val id = currentStepId()
        val svc = WeChatCallService.instance
        val cfg = store.loadOrInit()
        when {
            svc == null -> toast("请先开启无障碍服务")
            !cfg.hasCoord(id) -> toast("当前点尚未录制")
            else -> {
                val c = cfg.coord(id)
                toast("测试「${titleFor(id)}」 (${c?.pixelX},${c?.pixelY})")
                openWeChatUi()
                mainHandler.postDelayed(
                    { svc.debugTap(id, openWeChatFirst = false) },
                    TEST_TAP_MS,
                )
            }
        }
    }

    private fun beginRecordAction() = beginRecord(false)

    private fun confirmRecordAction() = confirmRecord()

    private fun retryRecordAction() {
        recordArmed = false
        hideRecordUi()
        showCard()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun openWeChatUi(): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                setPackage("com.tencent.mm")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun stopSelfSafely() {
        mainHandler.removeCallbacksAndMessages(null)
        hideRecordUi()
        hideCard()
        PluginEventBus.calibrationActive = false
        isRunning = false
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun startAsForeground() {
        try {
            val channelId = "wvc_calibration"
            val notification: Notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(channelId, "坐标校准", NotificationManager.IMPORTANCE_LOW),
                )
                notification = Notification.Builder(this, channelId)
                    .setContentTitle("微信坐标校准")
                    .setContentText("请在悬浮窗中录点")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setOngoing(true)
                    .build()
            } else {
                notification = Notification.Builder(this)
                    .setContentTitle("微信坐标校准")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .build()
            }
            startForeground(40721, notification)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        hideRecordUi()
        hideCard()
        PluginEventBus.calibrationActive = false
        isRunning = false
        super.onDestroy()
    }

    private fun currentStepId(): String = wizardOrder[wizardIndex]

    private fun firstIncompleteIndex(): Int {
        val cfg = store.loadOrInit()
        val idx = wizardOrder.indexOfFirst { id ->
            val required = cfg.step(id)?.required ?: CallConfig.defaultRequired(id)
            required && !cfg.hasCoord(id)
        }
        return idx.coerceAtLeast(0)
    }

    private fun guideFor(id: String): String = when (id) {
        StepIds.HOME_TAB -> "会话列表底部「微信」Tab"
        StepIds.SEARCH_ICON -> "首页右上角搜索放大镜"
        StepIds.SEARCH_BOX_LONG_PRESS -> "搜索页输入框，长按出「粘贴」"
        StepIds.PASTE_BUBBLE -> "长按后弹出的「粘贴」项"
        StepIds.SEARCH_RESULT -> "搜索结果第一个好友"
        StepIds.PLUS_BUTTON -> "聊天页右下角「+」"
        StepIds.VIDEO_MENU -> "+ 面板「视频通话」"
        StepIds.VIDEO_CONFIRM -> "确认框「视频通话」"
        StepIds.VOICE_CONFIRM -> "确认框「语音通话」"
        StepIds.HANG_UP -> "通话页红色挂断"
        else -> "目标位置"
    }

    private fun titleFor(id: String): String = when (id) {
        StepIds.HOME_TAB -> "微信Tab"
        StepIds.SEARCH_ICON -> "搜索按钮"
        StepIds.SEARCH_BOX_LONG_PRESS -> "长按搜索框"
        StepIds.PASTE_BUBBLE -> "粘贴气泡"
        StepIds.SEARCH_RESULT -> "搜索结果"
        StepIds.PLUS_BUTTON -> "聊天页+号"
        StepIds.VIDEO_MENU -> "视频通话菜单"
        StepIds.VIDEO_CONFIRM -> "确认视频"
        StepIds.VOICE_CONFIRM -> "确认语音"
        StepIds.HANG_UP -> "挂断"
        else -> id
    }

    private fun coordLabel(id: String): String {
        val c = store.loadOrInit().coord(id) ?: return "未录制"
        val px = c.pixelX
        val py = c.pixelY
        return if (px != null && py != null) "($px,$py)" else "fx=${"%.3f".format(c.fx)}"
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics)
            .toInt()

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun cardFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun bg(fill: String, stroke: String? = null, radius: Int = 12): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(Color.parseColor(fill))
            if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
        }
    }

    private fun pill(
        text: String,
        fill: String,
        textColor: String = "#FFFFFF",
        contentDesc: String = text,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor(textColor))
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            contentDescription = contentDesc
            setPadding(dp(12), 0, dp(12), 0)
            background = bg(fill, radius = 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34),
            )
            lp.marginEnd = dp(6)
            lp.bottomMargin = dp(6)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    // ───────────────────────── Step card (draggable) ─────────────────────────

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun showCard() {
        if (wizardOrder.isEmpty()) {
            stopSelfSafely()
            return
        }
        val stepId = currentStepId()
        PluginEventBus.lastCalibrationStepId = stepId
        val config = store.loadOrInit()
        val recorded = config.hasCoord(stepId)
        val required = config.step(stepId)?.required ?: CallConfig.defaultRequired(stepId)
        val recordedCount = wizardOrder.count { config.hasCoord(it) }
        val total = wizardOrder.size

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(CARD_BG, LINE, 16)
            elevation = dp(6).toFloat()
        }

        // Drag handle / header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(12), dp(8))
            background = bg(GREEN, radius = 16)
            // clip only top corners visually via full green header
        }
        header.addView(TextView(this).apply {
            text = "坐标校准"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        })
        header.addView(TextView(this).apply {
            text = "$recordedCount/$total"
            setTextColor(Color.parseColor("#C8F5DC"))
            textSize = 12f
        })
        header.addView(TextView(this).apply {
            text = "  ✕"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(8), 0, 0, 0)
            contentDescription = "关闭校准"
            setOnClickListener { stopSelfSafely() }
        })

        // Drag whole card by header
        var dragRawX = 0f
        var dragRawY = 0f
        var dragStartX = 0
        var dragStartY = 0
        header.setOnTouchListener { _, event ->
            val p = cardParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragRawX = event.rawX
                    dragRawY = event.rawY
                    dragStartX = p.x
                    dragStartY = p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val snap = WeChatDisplay.snapshot(this)
                    val maxX = (snap.widthPx - dp(CARD_WIDTH_DP)).coerceAtLeast(0)
                    val maxY = (snap.heightPx - dp(80)).coerceAtLeast(0)
                    // gravity TOP|END → x is distance from right
                    p.x = (dragStartX - (event.rawX - dragRawX).toInt()).coerceIn(0, maxX)
                    p.y = (dragStartY + (event.rawY - dragRawY).toInt()).coerceIn(0, maxY)
                    cardPosX = p.x
                    cardPosY = p.y
                    runCatching { wm.updateViewLayout(cardView, p) }
                    true
                }
                else -> false
            }
        }

        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }

        body.addView(TextView(this).apply {
            text = "【${wizardIndex + 1}/$total】${titleFor(stepId)}" +
                if (required) " · 必录" else " · 可选"
            setTextColor(Color.parseColor(INK))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })

        body.addView(TextView(this).apply {
            text = guideFor(stepId)
            setTextColor(Color.parseColor(SUB))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(6))
        })

        body.addView(TextView(this).apply {
            text = if (recorded) "当前点 ${coordLabel(stepId)}" else "当前点 未录制"
            setTextColor(Color.parseColor(if (recorded) GREEN_DARK else "#C45C26"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        })

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        nav.addView(pill("‹ 上一步", "#E8F5EE", GREEN_DARK, onClick = ::goPrevStep))
        nav.addView(pill("下一步 ›", GREEN, "#FFFFFF", onClick = ::goNextStep))
        body.addView(nav)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            pill(
                if (recorded) "重录" else "● 录制此点",
                if (recorded) "#E8F5EE" else GREEN,
                if (recorded) GREEN_DARK else "#FFFFFF",
                onClick = ::beginRecordAction,
            ),
        )
        if (recorded) {
            actions.addView(pill("测试", "#EEF2FF", "#3B5BDB", onClick = ::testCurrentStep))
            actions.addView(pill("清除", "#FFF1E8", "#C45C26", onClick = ::clearCurrentStep))
        }
        if (stepId == StepIds.PASTE_BUBBLE) {
            actions.addView(
                pill("自动长按", "#E8F5EE", GREEN_DARK, onClick = ::pasteLongPressAction),
            )
        }
        body.addView(actions)

        val utils = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utils.addView(pill("打开微信", "#F3F4F6", "#374151", onClick = ::openWeChatAction))
        utils.addView(pill("定位到此页", "#F3F4F6", "#374151", onClick = ::prepareCurrentStep))
        body.addView(utils)

        body.addView(TextView(this).apply {
            text = "坐标点列表 · 点选切换"
            setTextColor(Color.parseColor(SUB))
            textSize = 11f
            setPadding(0, dp(6), 0, dp(4))
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wizardOrder.forEachIndexed { index, id ->
            val has = config.hasCoord(id)
            val isCurrent = index == wizardIndex
            val req = config.step(id)?.required ?: CallConfig.defaultRequired(id)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = bg(
                    when {
                        isCurrent -> "#E5F8EE"
                        has -> "#FFFFFF"
                        else -> "#FFF8F1"
                    },
                    if (isCurrent) GREEN else "#EEF2F0",
                    10,
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = dp(4)
                layoutParams = lp
                setOnClickListener { jumpToStep(id) }
            }
            row.addView(TextView(this).apply {
                text = if (has) "●" else if (req) "○" else "·"
                setTextColor(
                    Color.parseColor(
                        if (has) GREEN else if (req) "#E6A23C" else "#C0C0C0",
                    ),
                )
                textSize = 12f
                val lp = LinearLayout.LayoutParams(dp(18), LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            })
            row.addView(TextView(this).apply {
                text = "${index + 1}. ${titleFor(id)}${if (req) " *" else ""}"
                setTextColor(Color.parseColor(if (isCurrent) GREEN_DARK else INK))
                textSize = 12f
                typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            })
            row.addView(TextView(this).apply {
                text = if (has) coordLabel(id) else "未录"
                setTextColor(Color.parseColor(if (has) GREEN_DARK else "#C45C26"))
                textSize = 11f
                gravity = Gravity.END
            })
            list.addView(row)
        }

        body.addView(
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                addView(list)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)),
        )

        root.addView(body)

        val params = WindowManager.LayoutParams(
            dp(CARD_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            cardFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = cardPosX ?: dp(DEFAULT_CARD_X_DP)
            y = cardPosY ?: dp(DEFAULT_CARD_Y_DP)
        }

        hideCard()
        try {
            wm.addView(root, params)
            cardView = root
            cardParams = params
        } catch (e: Exception) {
            toast("步骤卡失败: ${e.message}")
        }
        emitProgress(if (recorded) "confirmed" else "pending", stepId)
    }

    private fun hideCard() {
        val v = cardView ?: return
        runCatching { wm.removeView(v) }
        cardView = null
        cardParams = null
    }

    // ───────────────────────── Record mode ─────────────────────────

    private fun nudge(dx: Int, dy: Int) {
        // Fine-tune counts as an intentional edit (arms confirm).
        recordArmed = true
        val snap = recordSnap ?: WeChatDisplay.snapshot(this)
        pendingPx = (pendingPx + dx).coerceIn(0, snap.widthPx - 1)
        pendingPy = (pendingPy + dy).coerceIn(0, snap.heightPx - 1)
        pendingFx = pendingPx.toDouble() / snap.widthPx
        pendingFy = pendingPy.toDouble() / snap.heightPx
        updateCrosshair(pendingPx, pendingPy)
        coordTextView?.text = coordLine()
        nudgeRowView?.visibility = View.VISIBLE
        // Re-place controls if point crossed mid-screen
        placeControlPanel(pendingFy)
    }

    private fun coordLine(): String {
        val warn = sanityWarn(currentStepId(), pendingFy)
        return "(${pendingPx},${pendingPy})  fy=${"%.3f".format(pendingFy)}" +
            if (warn != null) "  ⚠$warn" else ""
    }

    private fun sanityWarn(stepId: String, fy: Double): String? = when (stepId) {
        StepIds.SEARCH_ICON, StepIds.SEARCH_BOX_LONG_PRESS,
        StepIds.PASTE_BUBBLE, StepIds.SEARCH_RESULT,
        -> if (fy > 0.40) "偏下?" else null
        StepIds.PLUS_BUTTON, StepIds.VIDEO_CONFIRM,
        StepIds.VOICE_CONFIRM, StepIds.HANG_UP,
        -> if (fy < 0.55) "偏上?" else null
        StepIds.HOME_TAB -> if (fy < 0.80) "Tab通常在底部" else null
        else -> null
    }

    private fun updateCrosshair(screenX: Int, screenY: Int) {
        val root = recordRoot ?: return
        val cross = crosshairView ?: return
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val lp = cross.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = (screenX - loc[0] - dp(16)).coerceAtLeast(0)
        lp.topMargin = (screenY - loc[1] - dp(16)).coerceAtLeast(0)
        cross.layoutParams = lp
        cross.visibility = View.VISIBLE
    }

    /**
     * Place the confirm/nudge panel on the OPPOSITE side of the target:
     * bottom targets → panel on top; top targets → panel on bottom.
     * Panel is a compact floating window and can be dragged.
     */
    private fun placeControlPanel(fy: Double) {
        val panel = controlPanel ?: return
        val p = controlParams ?: return
        val snap = recordSnap ?: WeChatDisplay.snapshot(this)
        // If target is in lower 55% of screen, put panel near top.
        val placeTop = fy >= 0.45
        p.gravity = if (placeTop) (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        else (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        p.x = 0
        p.y = if (placeTop) dp(56) else dp(20)
        runCatching { wm.updateViewLayout(panel, p) }
        hintText?.text = if (placeTop) {
            "点目标位置（面板在上方，不挡底部）"
        } else {
            "点目标位置（面板在下方，不挡顶部）"
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun beginRecord(fromPastePrepare: Boolean) {
        val stepId = currentStepId()
        if (!Settings.canDrawOverlays(this)) {
            toast("悬浮窗权限丢失")
            return
        }
        hideCard()
        recordArmed = false
        val snap = WeChatDisplay.snapshot(this)
        recordSnap = snap
        val cfg = store.loadOrInit()
        val existing = cfg.coord(stepId)
        val hasExisting = existing?.pixelX != null && existing.pixelY != null
        if (hasExisting) {
            // Prefill for fine-tune only — require a tap or nudge before confirm
            // so the previous coordinates cannot be saved by accident.
            pendingPx = existing.pixelX!!
            pendingPy = existing.pixelY!!
            pendingFx = existing.fx
            pendingFy = existing.fy
            recordArmed = false
        }

        // Full-screen transparent capture layer (crosshair only)
        val crosshair = View(this).apply {
            visibility = if (hasExisting || recordArmed) View.VISIBLE else View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#55FF3B30"))
                setStroke(dp(2), Color.WHITE)
            }
        }
        crosshairView = crosshair

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(if (fromPastePrepare) "#10000000" else "#1A000000"))
            addView(
                crosshair,
                FrameLayout.LayoutParams(dp(32), dp(32)).apply {
                    gravity = Gravity.TOP or Gravity.START
                },
            )
            setOnTouchListener { _, event ->
                if (event.actionMasked != MotionEvent.ACTION_DOWN) return@setOnTouchListener true
                val screenX = event.rawX.toInt().coerceIn(0, snap.widthPx - 1)
                val screenY = event.rawY.toInt().coerceIn(0, snap.heightPx - 1)
                pendingPx = screenX
                pendingPy = screenY
                pendingFx = screenX.toDouble() / snap.widthPx
                pendingFy = screenY.toDouble() / snap.heightPx
                recordArmed = true
                val lp = crosshair.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin = (event.x - dp(16)).toInt().coerceAtLeast(0)
                lp.topMargin = (event.y - dp(16)).toInt().coerceAtLeast(0)
                crosshair.layoutParams = lp
                crosshair.visibility = View.VISIBLE
                coordTextView?.text = coordLine()
                nudgeRowView?.visibility = View.VISIBLE
                hintText?.text = "确认「${titleFor(stepId)}」？面板可拖动"
                placeControlPanel(pendingFy)
                PluginEventBus.emitCalibration(
                    "recorded",
                    stepId,
                    mapOf(
                        "fx" to pendingFx,
                        "fy" to pendingFy,
                        "pixelX" to screenX,
                        "pixelY" to screenY,
                    ),
                )
                true
            }
        }

        val layerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            width = snap.widthPx
            height = snap.heightPx
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) layerParams.fitInsetsTypes = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layerParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        // Clear any previous record UI first (do not clear snap).
        val keepArmed = recordArmed
        val keepPx = pendingPx
        val keepPy = pendingPy
        val keepFx = pendingFx
        val keepFy = pendingFy
        hideRecordUi(keepSnap = true)
        recordArmed = keepArmed
        pendingPx = keepPx
        pendingPy = keepPy
        pendingFx = keepFx
        pendingFy = keepFy
        recordSnap = snap
        crosshairView = crosshair
        recordRoot = root

        val panel = buildControlPanel(stepId, fromPastePrepare, hasExisting)
        controlPanel = panel
        val panelParams = WindowManager.LayoutParams(
            dp(PANEL_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            cardFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(20)
        }
        controlParams = panelParams
        placeControlPanel(if (recordArmed) pendingFy else 0.2)

        try {
            wm.addView(root, layerParams)
            recordLayer = root
            wm.addView(panel, panelParams)
            controlPanel = panel
            if (recordArmed) updateCrosshair(pendingPx, pendingPy)
            PluginEventBus.lastCalibrationStepId = stepId
            emitProgress("recording", stepId)
        } catch (e: Exception) {
            toast("录点层失败: ${e.message}")
            hideRecordUi()
            showCard()
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun buildControlPanel(
        stepId: String,
        fromPastePrepare: Boolean,
        hasExisting: Boolean = false,
    ): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg("#F0FFFFFF", "#CCD9E8", 14)
            elevation = dp(8).toFloat()
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        // Drag bar
        val dragBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
            addView(TextView(this@CalibrationOverlayService).apply {
                text = "⋮⋮ 拖动面板"
                setTextColor(Color.parseColor("#9CA3AF"))
                textSize = 11f
            })
            var sx = 0f
            var sy = 0f
            var ox = 0
            var oy = 0
            setOnTouchListener { _, event ->
                val p = controlParams ?: return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = event.rawX
                        sy = event.rawY
                        ox = p.x
                        oy = p.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - sx).toInt()
                        val dy = (event.rawY - sy).toInt()
                        // Switch gravity to TOP|START while dragging for free placement
                        if (p.gravity != (Gravity.TOP or Gravity.START)) {
                            val loc = IntArray(2)
                            controlPanel?.getLocationOnScreen(loc)
                            p.gravity = Gravity.TOP or Gravity.START
                            p.x = loc[0] + dx
                            p.y = loc[1] + dy
                        } else {
                            p.x = ox + dx
                            p.y = oy + dy
                        }
                        runCatching { wm.updateViewLayout(controlPanel, p) }
                        true
                    }
                    else -> false
                }
            }
        }
        panel.addView(dragBar)

        hintText = TextView(this).apply {
            text = if (fromPastePrepare) {
                "请点击「粘贴」出现过的位置"
            } else if (hasExisting && !recordArmed) {
                "已载入上次坐标 · 点微信目标处重新选点"
            } else {
                "录制「${titleFor(stepId)}」· 点微信目标处"
            }
            setTextColor(Color.parseColor(INK))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        panel.addView(hintText)

        coordTextView = TextView(this).apply {
            text = when {
                recordArmed -> coordLine()
                hasExisting -> "已载入上次坐标 (${pendingPx},${pendingPy})，点击重选或微调后确认"
                else -> "尚未点击"
            }
            setTextColor(Color.parseColor(SUB))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }
        panel.addView(coordTextView)

        fun mini(
            label: String,
            fill: String,
            textColor: String,
            contentDesc: String = label,
            onClick: () -> Unit,
        ): Button {
            return Button(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor(textColor))
                minWidth = 0
                minimumWidth = 0
                contentDescription = contentDesc
                setPadding(dp(10), 0, dp(10), 0)
                background = bg(fill, radius = 16)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(32),
                )
                lp.marginEnd = dp(4)
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        nudgeRowView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = if (recordArmed || hasExisting) View.VISIBLE else View.GONE
            addView(mini("←", "#F3F4F6", "#374151", "左移") { nudge(-4, 0) })
            addView(mini("↑", "#F3F4F6", "#374151", "上移") { nudge(0, -4) })
            addView(mini("↓", "#F3F4F6", "#374151", "下移") { nudge(0, 4) })
            addView(mini("→", "#F3F4F6", "#374151", "右移") { nudge(4, 0) })
            addView(mini("+16", "#E5E7EB", "#374151", "下移16像素") { nudge(0, 16) })
        }
        panel.addView(nudgeRowView)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            addView(
                mini("确认保存", GREEN, "#FFFFFF", "确认保存坐标", onClick = ::confirmRecordAction),
            )
            addView(
                mini("重新点选", "#FFF7ED", "#C45C26", "重新点选", onClick = ::retryRecordAction),
            )
            addView(mini("取消", "#F3F4F6", "#6B7280", "取消校准") { stopSelfSafely() })
        }
        panel.addView(btnRow)
        return panel
    }

    private fun hideRecordUi(keepSnap: Boolean = false) {
        recordLayer?.let { runCatching { wm.removeView(it) } }
        controlPanel?.let { runCatching { wm.removeView(it) } }
        recordLayer = null
        controlPanel = null
        controlParams = null
        recordRoot = null
        crosshairView = null
        coordTextView = null
        hintText = null
        nudgeRowView = null
        recordArmed = false
        if (!keepSnap) recordSnap = null
    }

    private fun confirmRecord() {
        val stepId = currentStepId()
        if (!recordArmed) {
            toast("请先点击目标位置")
            return
        }
        val snap = recordSnap ?: WeChatDisplay.snapshot(this)
        val config = store.loadOrInit()
        val coord = StepCoord(
            fx = pendingFx,
            fy = pendingFy,
            fySpace = "fullscreen",
            pixelX = pendingPx,
            pixelY = pendingPy,
            widthPx = snap.widthPx,
            heightPx = snap.heightPx,
            density = snap.density.toDouble(),
            statusBarPx = snap.statusBarPx,
            navigationBarPx = snap.navigationBarPx,
            rotation = snap.rotation,
            recordedAt = CallConfig.nowIso(),
        )
        val saved = store.save(config.upsertCoord(stepId, coord))
        hideRecordUi()
        PluginEventBus.emitCalibration(
            "confirmed",
            stepId,
            mapOf(
                "fx" to pendingFx, "fy" to pendingFy,
                "pixelX" to pendingPx, "pixelY" to pendingPy,
                "saved" to saved,
            ),
        )
        PluginEventBus.emit("calibrationProgress", store.progress(stepId))
        toast(if (saved) "已保存 ${titleFor(stepId)} ($pendingPx,$pendingPy)" else "保存失败")
        showCard()
    }

    private fun emitProgress(phase: String, stepId: String?) {
        val progress = store.progress(stepId)
        PluginEventBus.emitCalibration(phase, stepId, progress)
        PluginEventBus.emit("calibrationProgress", progress)
    }
}
