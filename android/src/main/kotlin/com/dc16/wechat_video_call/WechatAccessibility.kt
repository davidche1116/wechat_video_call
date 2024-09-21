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

    val TAG: String = "WechatAccessibilityTag"

    override fun onInterrupt() {
        WechatData.updateIndex(0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        android.util.Log.d(TAG, event?.toString() ?: "null")
        android.util.Log.d(TAG, String.format("%d", WechatData.index))
        if (WechatData.index == 1) {
            val currentActivity = event?.className
            if (currentActivity != null && currentActivity == "com.tencent.mm.ui.LauncherUI") {
                //1、底部导航栏有4个，到通讯录页面
                var tables =
                    rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/icon_tv")
                while (tables == null || tables.size == 0) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Thread.sleep(500)
                    tables =
                        rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/icon_tv")
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
                rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/jha")
            qq[0].click()
            Thread.sleep(500)
            WechatData.updateIndex(3)
        }
        if (WechatData.index == 3) {
            // 输入文字
            val tt =
                rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/d98")
            tt[0].input(WechatData.value)
            Thread.sleep(1000)
            WechatData.updateIndex(4)
        }
        if (WechatData.index == 4) {
            // 点击第一个
            val odf = rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/odf")
            odf[0].click()
            Thread.sleep(500)
            WechatData.updateIndex(5)
        }
        if (WechatData.index == 5) {
            // 点击更多
            val fq = rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/bjz")
            fq[0].click()
            Thread.sleep(1000)
            WechatData.updateIndex(6)
        }
        if (WechatData.index == 6) {
            // 点击视频通话
            val currentActivity = event?.className
            if (currentActivity != null && currentActivity == "com.tencent.mm.ui.chatting.ChattingUI") {
                val ff = rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/a1u")
                android.util.Log.d("视频通话=", ff[0].getChild(2).toString())
//                Thread.sleep(1500)
                var rect: Rect = Rect()
                ff[0].getChild(2).getBoundsInScreen(rect)
//                ff[0].getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                performClick(rect.left.toFloat(), rect.top.toFloat())
                Thread.sleep(500)
                WechatData.updateIndex(7)
            }
        }
        if (WechatData.index == 7) {
            // 点击视频通话
            val currentActivity = event?.className
            if (currentActivity != null && currentActivity == "yj4.o3") {
                val fq = rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/obc")
                fq[0].click()
                Thread.sleep(500)
                WechatData.updateIndex(0)
            }
        }
//        if (WechatData.index == 5) {
//            // 点击右上角菜单
//            val currentActivity = event?.className
//            if (currentActivity != null && currentActivity == "com.tencent.mm.ui.chatting.ChattingUI") {
//                val fq = rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/fq")
//                fq[0].click()
//                Thread.sleep(500)
//                WechatData.updateIndex(6)
//            }
//        }
//        if (WechatData.index == 6) {
//            // 点击头像
//            val currentActivity = event?.className
//            if (currentActivity != null && currentActivity == "com.tencent.mm.ui.SingleChatInfoUI") {
//                val fq = rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/m7g")
//                fq[0].click()
//                Thread.sleep(500)
//                WechatData.updateIndex(7)
//            }
//        }
//        if (WechatData.index == 7) {
//            // 点击音视频通话
//            val currentActivity = event?.className
//            if (currentActivity != null && currentActivity == "com.tencent.mm.plugin.profile.ui.ContactInfoUI") {
//                val fq = rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/o3b")
//                fq[1].click()
//                Thread.sleep(500)
//                WechatData.updateIndex(8)
//            }
//        }
//        if (WechatData.index == 8) {
//            // 点击视频通话
//            val currentActivity = event?.className
//            if (currentActivity != null && currentActivity == "yj4.o3") {
//                val fq = rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/obc")
//                fq[0].click()
//                Thread.sleep(500)
//                WechatData.updateIndex(0)
//            }
//        }
    }

    fun AccessibilityNodeInfo?.click(): Boolean {
        this ?: return false
        return if (isClickable) {
            performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            parent?.click() == true
        }
    }

    fun AccessibilityNodeInfo?.input(text: String): Boolean {
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
