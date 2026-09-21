package com.dc16.wechat_video_call

import android.content.res.Resources
import android.util.DisplayMetrics

/**
 * 运行时屏幕/系统栏尺寸。
 *
 * 微信 8.0.78 封锁了无障碍节点树，无法读控件 bounds；
 * 坐标自适应只能依赖显示度量（宽高、density、状态栏/导航栏高度）+ 结构锚点。
 */
object WeChatDisplay {

    data class Snapshot(
        val widthPx: Int,
        val heightPx: Int,
        /** dpi/160，魅族 20 约 2.8125 */
        val density: Float,
        val statusBarPx: Int,
        val navigationBarPx: Int,
    ) {
        /** 状态栏底到屏幕底的内容高度（手势导航时 navBar=0）。 */
        val contentHeightPx: Int
            get() = (heightPx - statusBarPx - navigationBarPx).coerceAtLeast(1)
    }

    fun snapshot(resources: Resources): Snapshot {
        val metrics = resources.displayMetrics
        val status = statusBarPx(resources)
        val nav = navigationBarPx(resources)
        return Snapshot(
            widthPx = metrics.widthPixels.coerceAtLeast(1),
            heightPx = metrics.heightPixels.coerceAtLeast(1),
            density = density(metrics),
            statusBarPx = status,
            navigationBarPx = nav,
        )
    }

    fun density(metrics: DisplayMetrics): Float {
        val d = metrics.density
        return if (d > 0f) d else metrics.densityDpi / 160f
    }

    fun statusBarPx(resources: Resources): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val fromRes = if (resId > 0) resources.getDimensionPixelSize(resId) else 0
        // 与魅族基准一致：资源读不到时至少给一个合理顶栏高度
        return fromRes.coerceAtLeast(24)
    }

    fun navigationBarPx(resources: Resources): Int {
        // 手势导航时 navigation_bar_height 可能为 0 或很小，保持原样参与底部锚点
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    /** 日志用。 */
    fun describe(snap: Snapshot): String {
        return "w=${snap.widthPx} h=${snap.heightPx} density=${snap.density} " +
            "sb=${snap.statusBarPx} nav=${snap.navigationBarPx}"
    }
}
