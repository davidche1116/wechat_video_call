import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'wechat_video_call_method_channel.dart';

abstract class WeChatVideoCallPlatform extends PlatformInterface {
  /// Constructs a WechatVideoCallPlatform.
  WeChatVideoCallPlatform() : super(token: _token);

  static final Object _token = Object();

  static WeChatVideoCallPlatform _instance = MethodChannelWeChatVideoCall();

  /// The default instance of [WeChatVideoCallPlatform] to use.
  ///
  /// Defaults to [MethodChannelWeChatVideoCall].
  static WeChatVideoCallPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [WeChatVideoCallPlatform] when
  /// they register themselves.
  static set instance(WeChatVideoCallPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<bool> requestAccessibilityPermission() {
    throw UnimplementedError(
        'requestAccessibilityPermission() has not been implemented.');
  }

  Future<bool> isAccessibilityPermissionEnabled() {
    throw UnimplementedError(
        'isAccessibilityPermissionEnabled() has not been implemented.');
  }

  Future<bool> videoCall(String name, bool toast) {
    throw UnimplementedError('videoCall() has not been implemented.');
  }

  Future<bool> voiceCall(String name, bool toast) {
    throw UnimplementedError('voiceCall() has not been implemented.');
  }
}
