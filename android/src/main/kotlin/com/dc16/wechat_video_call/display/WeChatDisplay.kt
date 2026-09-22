package com.dc16.wechat_video_call.display

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Runtime display metrics.
 *
 * On MIUI, `resources.displayMetrics.heightPixels` may be the app window height
 * rather than the physical screen. Always prefer realMetrics / maximumWindowMetrics.
 */
object WeChatDisplay {
    data class Snapshot(
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val statusBarPx: Int,
        val navigationBarPx: Int,
        val rotation: Int = 0,
    ) {
        val contentHeightPx: Int
            get() = (heightPx - statusBarPx).coerceAtLeast(1)
    }

    fun snapshot(context: Context): Snapshot {
        val resources = context.resources
        val appMetrics = resources.displayMetrics
        val real = realDisplayPixels(context)
        return Snapshot(
            widthPx = real.first.coerceAtLeast(1),
            heightPx = real.second.coerceAtLeast(1),
            density = density(appMetrics),
            statusBarPx = statusBarPx(resources),
            navigationBarPx = navigationBarPx(resources),
            rotation = currentRotation(context),
        )
    }

    fun realDisplayPixels(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
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
        } catch (_: Exception) {
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
        return fromRes.coerceAtLeast(0)
    }

    fun navigationBarPx(resources: Resources): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    private fun currentRotation(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return try {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.rotation
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Resolve relative coord to pixels.
     * fySpace=contentBelowStatusBar: y = statusBar + fy * (H - statusBar)
     * fySpace=fullscreen (default): y = fy * H
     */
    fun resolve(fx: Double, fy: Double, fySpace: String?, snap: Snapshot): Pair<Float, Float> {
        val x = (fx * snap.widthPx).toFloat()
        val y = if (fySpace == "contentBelowStatusBar") {
            (snap.statusBarPx + fy * snap.contentHeightPx).toFloat()
        } else {
            (fy * snap.heightPx).toFloat()
        }
        return Pair(x, y)
    }
}
