import 'wechat_video_call_platform_interface.dart';

class WeChatVideoCall {
  /// request Accessibility Permission
  static Future<bool> requestAccessibilityPermission() async {
    return WeChatVideoCallPlatform.instance.requestAccessibilityPermission();
  }

  /// check Accessibility Permission
  static Future<bool> isAccessibilityPermissionEnabled() async {
    return WeChatVideoCallPlatform.instance.isAccessibilityPermissionEnabled();
  }

  /// WeChat video call with [name]
  /// [toast] If true, the default toast will show
  static Future<bool> videoCall(String name, {bool toast = true}) async {
    return WeChatVideoCallPlatform.instance.videoCall(name, toast);
  }

  /// WeChat voice call with [name]
  /// [toast] If true, the default toast will show
  static Future<bool> voiceCall(String name, {bool toast = true}) async {
    return WeChatVideoCallPlatform.instance.voiceCall(name, toast);
  }
}
