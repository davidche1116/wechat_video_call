package com.dc16.wechat_video_call.config

/** Shared error codes (mirrors Dart `WvcErrorCodes`). */
object ErrorCodes {
    const val ACCESSIBILITY_DISABLED = "WVC_0001"
    const val OVERLAY_DISABLED = "WVC_0002"
    const val WECHAT_NOT_INSTALLED = "WVC_0003"
    const val CONFIG_MISSING = "WVC_0004"
    const val CONFIG_MISMATCH = "WVC_0005"
    const val STEP_COORD_MISSING = "WVC_0006"
    const val SESSION_TIMEOUT = "WVC_0007"
    const val GESTURE_DISPATCH_FAILED = "WVC_0008"
    const val OPEN_WECHAT_FAILED = "WVC_0009"
    const val INPUT_FAILED = "WVC_0010"
    const val USER_CANCELLED = "WVC_0011"
    const val SERVICE_NOT_CONNECTED = "WVC_0012"
    const val CALIBRATION_IN_PROGRESS = "WVC_0013"
    const val BLANK_NAME = "WVC_0014"
    const val INVALID_ARGUMENT = "WVC_0015"
    const val HANGUP_COORD_MISSING = "WVC_0016"
    const val BACK_LIMIT = "WVC_0017"
    const val NOT_IMPLEMENTED = "WVC_0018"
}
