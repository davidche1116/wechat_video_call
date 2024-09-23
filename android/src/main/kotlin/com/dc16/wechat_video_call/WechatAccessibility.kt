package com.dc16.wechat_video_call

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WechatAccessibility : AccessibilityService() {

    private val tag: String = "WechatAccessibilityTag"

    override fun onInterrupt() {
        WechatData.updateIndex(0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        android.util.Log.d(tag, event?.toString() ?: "null")
        android.util.Log.d(tag, String.format("%d", WechatData.index))
        if (WechatData.index == 1) {
            val currentActivity = event?.className
            if (currentActivity != null && currentActivity == WechatActivity.INDEX.id) {
                //1、底部导航栏有4个，到通讯录页面
                var tables =
                    rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.TABLES.id)
                while (tables == null || tables.size == 0) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Thread.sleep(500)
                    tables =
                        rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.TABLES.id)
                }
                Thread.sleep(100)
                tables[0].click()
                WechatData.updateIndex(2)
            } else {
                // 不在页面，全局返回
                performGlobalAction(GLOBAL_ACTION_BACK)
                Thread.sleep(500)
            }
        }
        if (WechatData.index == 2) {
            // 点击搜索
            val qq =
                rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.SEARCH.id)
            qq[0].click()
            Thread.sleep(500)
            WechatData.updateIndex(3)
        }
        if (WechatData.index == 3) {
            // 输入文字
            val tt =
                rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.INPUT.id)
            tt[0].input(WechatData.value)
            Thread.sleep(1000)
            WechatData.updateIndex(4)
        }
        if (WechatData.index == 4) {
            // 点击第一个
            val odf = rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.LIST.id)
            odf[0].click()
            Thread.sleep(500)
            WechatData.updateIndex(5)
        }
        if (WechatData.index == 5) {
            // 点击更多
            val fq = rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.MORE.id)
            fq[0].click()
            Thread.sleep(1000)
            WechatData.updateIndex(6)
        }
        if (WechatData.index == 6) {
            // 点击视频通话
            val currentActivity = event?.className
            if (currentActivity != null && currentActivity == WechatActivity.CHAT.id) {
                val ff = rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.CHAT_MENU.id)
                android.util.Log.d("视频通话=", ff[0].getChild(2).toString())
                var rect: Rect = Rect()
                ff[0].getChild(2).getBoundsInScreen(rect)
                performClick(rect.left.toFloat(), rect.top.toFloat())
                Thread.sleep(500)
                WechatData.updateIndex(7)
            }
        }
        if (WechatData.index == 7) {
            // 点击视频通话
            val currentActivity = event?.className
            if (currentActivity != null && (currentActivity == WechatActivity.DIALOG.id || currentActivity == WechatActivity.DIALOG_OLD.id)) {
                val fq = rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.DIALOG.id)
                fq[0].click()
                Thread.sleep(500)
                WechatData.updateIndex(0)
            }
        }
    }

    private fun AccessibilityNodeInfo?.click(): Boolean {
        this ?: return false
        return if (isClickable) {
            performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            parent?.click() == true
        }
    }

    private fun AccessibilityNodeInfo?.input(text: String): Boolean {
        this ?: return false
        return if (isEditable) {

            val arguments: Bundle = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            parent?.input(text) == true
        }
    }

    private fun performClick(x: Float, y: Float) {
        val gestureBuilder = GestureDescription.Builder()
        val path: Path = Path()
        path.moveTo(x, y)
        gestureBuilder.addStroke(StrokeDescription(path, 0, 1))
        val gestureDescription = gestureBuilder.build()
        dispatchGesture(gestureDescription, null, null)
    }
}
