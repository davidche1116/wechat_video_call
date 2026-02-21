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

class WeChatAccessibility : AccessibilityService() {

    private val tag: String = "WechatAccessibilityTag"

    override fun onInterrupt() {
        WeChatData.updateIndex(0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentActivity = event?.className ?: return
        android.util.Log.d(tag, event.toString())
        android.util.Log.d(tag, String.format("%d", WeChatData.index))

        if (WeChatData.index == 1) {
            if (currentActivity == WeChatActivity.INDEX.id) {
                // 底部导航栏有4个，到首页微信页面
                var tables =
                    rootInActiveWindow.findAccessibilityNodeInfosByViewId(WeChatId.TABLES.id)
                while (tables.isEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Thread.sleep(500)
                    tables =
                        rootInActiveWindow.findAccessibilityNodeInfosByViewId(WeChatId.TABLES.id)
                }
                Thread.sleep(100)
                tables[0].click()
                WeChatData.updateIndex(2)
            } else if (currentActivity.contains("dialog")) {
                // 有弹窗，返回2次
                performGlobalAction(GLOBAL_ACTION_BACK)
                Thread.sleep(500)
                performGlobalAction(GLOBAL_ACTION_BACK)
                Thread.sleep(500)
            } else {
                // 不在页面，全局返回
                performGlobalAction(GLOBAL_ACTION_BACK)
                Thread.sleep(500)
            }
        }

        if (WeChatData.index == 2) {
            // 点击搜索
            val searchIcon =
                rootInActiveWindow.findAccessibilityNodeInfosByViewId(WeChatId.SEARCH.id)
            if (searchIcon.isNotEmpty()) {
                searchIcon.first().click()
                Thread.sleep(500)
                WeChatData.updateIndex(3)
            }
        }

        if (WeChatData.index == 3) {
            // 输入文字
            val input =
                rootInActiveWindow.findAccessibilityNodeInfosByViewId(WeChatId.INPUT.id)
            if (input.isNotEmpty()) {
                input.first().input(WeChatData.value)
                Thread.sleep(1000)
                WeChatData.updateIndex(4)
            }
        }

        if (WeChatData.index == 4) {
            // 点击搜到的第一个联系人
            if (currentActivity == WeChatActivity.SEARCH.id) {
                val contact = rootInActiveWindow.findAccessibilityNodeInfosByViewId(WeChatId.LIST.id)
                if (contact.isNotEmpty()) {
                    contact.first().click()
                    Thread.sleep(500)
                    WeChatData.updateIndex(5)
                }
            }
        }

        // ==================== 下面是重点修改的部分（适配微信 8.0.0.69） ====================

        if (WeChatData.index == 5) {
            // 聊天界面点击更多（+按钮） - 优化版
            var more = rootInActiveWindow.findAccessibilityNodeInfosByViewId(WeChatId.MORE.id)
            if (more.isEmpty()) more = rootInActiveWindow.findAccessibilityNodeInfosByText("+")
            if (more.isEmpty()) more = rootInActiveWindow.findAccessibilityNodeInfosByText("更多")
            if (more.isNotEmpty()) {
                more.first().click()
                Thread.sleep(1500)  // 增加等待，让菜单完全弹出
                WeChatData.updateIndex(6)
            }
        }

        if (WeChatData.index == 6) {
            // 点击视频通话菜单 - 优化版（专治8.0.0.69卡在列表）
            if (currentActivity == WeChatActivity.CHAT.id) {
                var menu = rootInActiveWindow.findAccessibilityNodeInfosByText(WeChatData.findText(false))
                if (menu.isEmpty()) menu = rootInActiveWindow.findAccessibilityNodeInfosByText("视频通话")
                if (menu.isEmpty()) menu = rootInActiveWindow.findAccessibilityNodeInfosByText("视频")
                if (menu.isEmpty()) {
                    menu = rootInActiveWindow.findAccessibilityNodeInfosByText("").filter {
                        it.text?.contains("视频通话") == true ||
                        it.contentDescription?.contains("视频通话") == true
                    }
                }
                if (menu.isNotEmpty()) {
                    val rect = Rect()
                    menu.first().getBoundsInScreen(rect)
                    performClick(rect.exactCenterX(), rect.exactCenterY())
                    Thread.sleep(1000)
                    WeChatData.updateIndex(7)
                } else {
                    Thread.sleep(600) // 没找到就继续等待下次事件
                }
            }
        }

        if (WeChatData.index == 7) {
            // 点击视频/语音通话选项 - 优化版
            if (currentActivity.contains(WeChatActivity.DIALOG.id)
                || currentActivity == WeChatActivity.DIALOG_OLD.id
            ) {
                var options = rootInActiveWindow.findAccessibilityNodeInfosByText(WeChatData.findText(true))
                if (options.isEmpty()) options = rootInActiveWindow.findAccessibilityNodeInfosByText("视频通话")
                if (options.isEmpty()) {
                    options = rootInActiveWindow.findAccessibilityNodeInfosByText("").filter {
                        it.text?.contains("视频通话") == true ||
                        it.contentDescription?.contains("视频通话") == true
                    }
                }
                if (options.isNotEmpty()) {
                    options.first().click()
                    Thread.sleep(500)
                    WeChatData.updateIndex(0)
                }
            }
        }
    }

    // ==================== 下面是原来的辅助函数，不用改 ====================
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
