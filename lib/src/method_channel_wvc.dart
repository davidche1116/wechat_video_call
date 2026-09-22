import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'models/wechat_event.dart';
import 'wechat_video_call_platform.dart';

/// Default [WeChatVideoCallPlatform] backed by `wechat_video_call` MethodChannel
/// and `wechat_video_call/events` EventChannel.
class MethodChannelWvc extends WeChatVideoCallPlatform {
  /// Method channel handle (visible for tests).
  @visibleForTesting
  final methodChannel = const MethodChannel('wechat_video_call');

  /// Event channel handle (visible for tests).
  @visibleForTesting
  final eventChannel = const EventChannel('wechat_video_call/events');

  Stream<WeChatCallEvent>? _events;

  @override
  Stream<WeChatCallEvent> get events {
    _events ??= eventChannel.receiveBroadcastStream().map(
      (e) => WeChatCallEvent.fromMap(Map<dynamic, dynamic>.from(e as Map)),
    );
    return _events!;
  }

  Future<T?> _invoke<T>(String method, [Map<String, dynamic>? args]) {
    return methodChannel.invokeMethod<T>(method, args);
  }

  @override
  Future<bool> requestAccessibilityPermission() async {
    return await _invoke<bool>('requestAccessibilityPermission') ?? false;
  }

  @override
  Future<bool> isAccessibilityPermissionEnabled() async {
    return await _invoke<bool>('isAccessibilityPermissionEnabled') ?? false;
  }

  @override
  Future<bool> requestOverlayPermission() async {
    return await _invoke<bool>('requestOverlayPermission') ?? false;
  }

  @override
  Future<bool> isOverlayPermissionEnabled() async {
    return await _invoke<bool>('isOverlayPermissionEnabled') ?? false;
  }

  @override
  Future<bool> isWeChatInstalled() async {
    return await _invoke<bool>('isWeChatInstalled') ?? false;
  }

  @override
  Future<Map<String, dynamic>> getPermissionStatus() async {
    final map = await _invoke<Map>('getPermissionStatus');
    return map == null ? <String, dynamic>{} : Map<String, dynamic>.from(map);
  }

  @override
  Future<bool> openWeChat() async {
    return await _invoke<bool>('openWeChat') ?? false;
  }

  @override
  Future<bool> isCalibrationActive() async {
    return await _invoke<bool>('isCalibrationActive') ?? false;
  }

  @override
  Future<bool> openCalibrationWizard() async {
    return await _invoke<bool>('openCalibrationWizard') ?? false;
  }

  @override
  Future<bool> stopCalibration() async {
    return await _invoke<bool>('stopCalibration') ?? false;
  }

  @override
  Future<Map<String, dynamic>> getCalibrationProgress() async {
    final map = await _invoke<Map>('getCalibrationProgress');
    return map == null ? <String, dynamic>{} : Map<String, dynamic>.from(map);
  }

  @override
  Future<Map<String, dynamic>?> loadConfig() async {
    final map = await _invoke<Map>('loadConfig');
    return map == null ? null : Map<String, dynamic>.from(map);
  }

  @override
  Future<bool> importConfig(String json) async {
    return await _invoke<bool>('importConfig', {'json': json}) ?? false;
  }

  @override
  Future<bool> resetConfig() async {
    return await _invoke<bool>('resetConfig') ?? false;
  }

  @override
  Future<bool> resetStep(String stepId) async {
    return await _invoke<bool>('resetStep', {'stepId': stepId}) ?? false;
  }

  @override
  Future<bool> setCoordinate(String key, double fx, double fy) async {
    return await _invoke<bool>('setCoordinate', {
          'key': key,
          'fx': fx,
          'fy': fy,
        }) ??
        false;
  }

  @override
  Future<bool> videoCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  }) async {
    return await _invoke<bool>('videoCall', {
          'name': name,
          'toast': toast,
          'delayScale': delayScale,
          'pauseAfterStepMs': pauseAfterStepMs,
        }) ??
        false;
  }

  @override
  Future<bool> voiceCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  }) async {
    return await _invoke<bool>('voiceCall', {
          'name': name,
          'toast': toast,
          'delayScale': delayScale,
          'pauseAfterStepMs': pauseAfterStepMs,
        }) ??
        false;
  }

  @override
  Future<bool> cancel() async {
    return await _invoke<bool>('cancel') ?? false;
  }

  @override
  Future<bool> hangUp() async {
    return await _invoke<bool>('hangUp') ?? false;
  }

  @override
  Future<bool> debugTapStep(String stepId) async {
    return await _invoke<bool>('debugTapStep', {'stepId': stepId}) ?? false;
  }

  @override
  Future<String?> exportConfig() async {
    return _invoke<String>('exportConfig');
  }
}
