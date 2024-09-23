import 'wechat_video_call_platform_interface.dart';

class WechatVideoCall {

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

  /// WeChat voice call with [name]
  static Future<bool> voiceCall(String name) async {
    return WechatVideoCallPlatform.instance.voiceCall(name);
  }
}
