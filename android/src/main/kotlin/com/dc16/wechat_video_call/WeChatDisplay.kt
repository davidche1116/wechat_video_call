package com.dc16.wechat_video_call

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * 运行时显示度量。
 *
 * 注意: MIUI 等系统上 `resources.displayMetrics.heightPixels` 可能是
 * **应用窗口高度**（Redmi 实测 2304）而非物理屏 2400。微信 Tab/挂断键
 * 按物理屏绝对坐标绘制，必须用 realMetrics / maximumWindowMetrics。
 */
object WeChatDisplay {

    data class Snapshot(
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val statusBarPx: Int,
        val navigationBarPx: Int,
    ) {
        val contentHeightPx: Int
            get() = (heightPx - statusBarPx).coerceAtLeast(1)
    }

    fun snapshot(context: Context): Snapshot {
        val resources = context.resources
        val appMetrics = resources.displayMetrics
        val real = realDisplayPixels(context)
        val status = statusBarPx(resources)
        val nav = navigationBarPx(resources)
        return Snapshot(
            widthPx = real.first.coerceAtLeast(1),
            heightPx = real.second.coerceAtLeast(1),
            density = density(appMetrics),
            statusBarPx = status,
            navigationBarPx = nav,
        )
    }

    /** 物理屏宽高（含系统栏）。 */
    fun realDisplayPixels(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
                // maximumWindowMetrics 在部分 ROM 仍是窗口；再与 defaultDisplay 对比取大
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                val w = maxOf(bounds.width(), dm.widthPixels)
                val h = maxOf(bounds.height(), dm.heightPixels)
                Pair(w, h)
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                Pair(dm.widthPixels, dm.heightPixels)
            }
        } catch (e: Exception) {
            val dm = context.resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    fun density(metrics: DisplayMetrics): Float {
        val d = metrics.density
        return if (d > 0f) d else metrics.densityDpi / 160f
    }

    fun statusBarPx(resources: Resources): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val fromRes = if (resId > 0) resources.getDimensionPixelSize(resId) else 0
        return fromRes.coerceAtLeast(24)
    }

    fun navigationBarPx(resources: Resources): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    fun describe(snap: Snapshot): String {
        return "w=${snap.widthPx} h=${snap.heightPx} density=${snap.density} " +
            "sb=${snap.statusBarPx} nav=${snap.navigationBarPx}"
    }
}
