import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'wechat_video_call_method_channel.dart';

abstract class WechatVideoCallPlatform extends PlatformInterface {
  /// Constructs a WechatVideoCallPlatform.
  WechatVideoCallPlatform() : super(token: _token);

  static final Object _token = Object();

  static WechatVideoCallPlatform _instance = MethodChannelWechatVideoCall();

  /// The default instance of [WechatVideoCallPlatform] to use.
  ///
  /// Defaults to [MethodChannelWechatVideoCall].
  static WechatVideoCallPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [WechatVideoCallPlatform] when
  /// they register themselves.
  static set instance(WechatVideoCallPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<bool> requestAccessibilityPermission() {
    throw UnimplementedError(
        'requestAccessibilityPermission() has not been implemented.');
  }

  Future<bool> isAccessibilityPermissionEnabled() {
    throw UnimplementedError(
        'isAccessibilityPermissionEnabled() has not been implemented.');
  }

  Future<bool> videoCall(String name) {
    throw UnimplementedError('videoCall() has not been implemented.');
  }

  Future<bool> voiceCall(String name) {
    throw UnimplementedError('voiceCall() has not been implemented.');
  }
}
