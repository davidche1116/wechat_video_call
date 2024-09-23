package com.dc16.wechat_video_call

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WechatAccessibility : AccessibilityService() {

    private val tag: String = "WechatAccessibilityTag"

    override fun onInterrupt() {
        WechatData.updateIndex(0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentActivity = event?.className ?: return
        android.util.Log.d(tag, event.toString())
        android.util.Log.d(tag, String.format("%d", WechatData.index))
        if (WechatData.index == 1) {
            if (currentActivity == WechatActivity.INDEX.id) {
                // 底部导航栏有4个，到首页微信页面
                var tables =
                    rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.TABLES.id)
                while (tables.isEmpty()) {
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
            val searchIcon =
                rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.SEARCH.id)
            if (searchIcon.isNotEmpty()) {
                searchIcon.first().click()
                Thread.sleep(500)
                WechatData.updateIndex(3)
            }
        }
        if (WechatData.index == 3) {
            // 输入文字
            val input =
                rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.INPUT.id)
            if (input.isNotEmpty()) {
                input.first().input(WechatData.value)
                Thread.sleep(1000)
                WechatData.updateIndex(4)
            }
        }
        if (WechatData.index == 4) {
            // 点击搜到的第一个联系人
            if (currentActivity == WechatActivity.SEARCH.id) {
                val contact = rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.LIST.id)
                if (contact.isNotEmpty()) {
                    contact.first().click()
                    Thread.sleep(500)
                    WechatData.updateIndex(5)
                }
            }
        }
        if (WechatData.index == 5) {
            // 聊天界面点击更多
            val more = rootInActiveWindow.findAccessibilityNodeInfosByViewId(WechatId.MORE.id)
            if (more.isNotEmpty()) {
                more.first().click()
                Thread.sleep(1000)
                WechatData.updateIndex(6)
            }
        }
        if (WechatData.index == 6) {
            // 点击视频通话菜单
            if (currentActivity == WechatActivity.CHAT.id) {
                val menu = rootInActiveWindow.findAccessibilityNodeInfosByText("视频通话")
                if (menu.isNotEmpty()) {
                    val rect = Rect()
                    menu.first().getBoundsInScreen(rect)
                    performClick(rect.left.toFloat(), rect.top.toFloat())
                    Thread.sleep(500)
                    WechatData.updateIndex(7)
                }
            }
        }
        if (WechatData.index == 7) {
            // 点击视频通话选项
            if (currentActivity == WechatActivity.DIALOG.id || currentActivity == WechatActivity.DIALOG_OLD.id) {
                val options = rootInActiveWindow.findAccessibilityNodeInfosByText("视频通话")
                if (options.isNotEmpty()) {
                    options.first().click()
                    Thread.sleep(500)
                    WechatData.updateIndex(0)
                }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val gestureBuilder = GestureDescription.Builder()
            val path: Path = Path()
            path.moveTo(x, y)
            gestureBuilder.addStroke(StrokeDescription(path, 0, 1))
            val gestureDescription = gestureBuilder.build()
            dispatchGesture(gestureDescription, null, null)
        }
    }
}
