import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'wechat_video_call_platform_interface.dart';

/// An implementation of [WeChatVideoCallPlatform] that uses method channels.
class MethodChannelWeChatVideoCall extends WeChatVideoCallPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('wechat_video_call');

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
  Future<bool> videoCall(String name, String pinyin, bool toast) async {
    try {
      return await methodChannel.invokeMethod('videoCall',
          {'name': name, 'pinyin': pinyin, 'video': true, 'toast': toast});
    } on PlatformException catch (error) {
      debugPrint("$error");
      return false;
    }
  }

  @override
  Future<bool> voiceCall(String name, String pinyin, bool toast) async {
    try {
      return await methodChannel.invokeMethod('videoCall',
          {'name': name, 'pinyin': pinyin, 'video': false, 'toast': toast});
    } on PlatformException catch (error) {
      debugPrint("$error");
      return false;
    }
  }

  @override
  Future<bool> cancel() async {
    try {
      return await methodChannel.invokeMethod('cancel');
    } on PlatformException catch (error) {
      debugPrint("$error");
      return false;
    }
  }

  @override
  Future<bool> hangUp() async {
    try {
      return await methodChannel.invokeMethod('hangUp');
    } on PlatformException catch (error) {
      debugPrint("$error");
      return false;
    }
  }

  @override
  Future<bool> setCoordinate(String key, double fx, double fy) async {
    try {
      return await methodChannel
          .invokeMethod('setCoordinate', {'key': key, 'fx': fx, 'fy': fy});
    } on PlatformException catch (error) {
      debugPrint("$error");
      return false;
    }
  }
}
