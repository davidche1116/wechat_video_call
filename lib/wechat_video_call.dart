import 'wechat_video_call_platform_interface.dart';

class WechatVideoCall {
  static Future<String?> getPlatformVersion() {
    return WechatVideoCallPlatform.instance.getPlatformVersion();
  }

  /// request Accessibility Permission
  static Future<bool> requestAccessibilityPermission() async {
    return WechatVideoCallPlatform.instance.requestAccessibilityPermission();
  }

  /// check Accessibility Permission
  static Future<bool> isAccessibilityPermissionEnabled() async {
    return WechatVideoCallPlatform.instance.isAccessibilityPermissionEnabled();
  }

  /// WeChat video call with [name]
  static Future<bool> videoCall(String name) async {
    return WechatVideoCallPlatform.instance.videoCall(name);
  }
}
