import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'wechat_video_call_platform_interface.dart';

/// An implementation of [WechatVideoCallPlatform] that uses method channels.
class MethodChannelWechatVideoCall extends WechatVideoCallPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('wechat_video_call');

  @override
  Future<String?> getPlatformVersion() async {
    final version =
        await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<bool> requestAccessibilityPermission() async {
    try {
      return await methodChannel.invokeMethod('requestAccessibilityPermission');
    } on PlatformException catch (error) {
      debugPrint("$error");
      return Future.value(false);
    }
  }

  @override
  Future<bool> isAccessibilityPermissionEnabled() async {
    try {
      return await methodChannel
          .invokeMethod('isAccessibilityPermissionEnabled');
    } on PlatformException catch (error) {
      debugPrint("$error");
      return false;
    }
  }

  @override
  Future<bool> videoCall(String name) async {
    try {
      return await methodChannel
          .invokeMethod('videoCall', {'name': name, 'video': true});
    } on PlatformException catch (error) {
      debugPrint("$error");
      return false;
    }
  }

  @override
  Future<bool> voiceCall(String name) async {
    try {
      return await methodChannel
          .invokeMethod('videoCall', {'name': name, 'video': false});
    } on PlatformException catch (error) {
      debugPrint("$error");
      return false;
    }
  }
}
