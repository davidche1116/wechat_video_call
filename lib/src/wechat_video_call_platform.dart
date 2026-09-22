import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'method_channel_wvc.dart';
import 'models/wechat_event.dart';

/// Platform interface for wechat_video_call.
///
/// Third-party platform implementations must extend this class with
/// `MockPlatformInterfaceMixin` (tests) or a real `PlatformInterface` token.
abstract class WeChatVideoCallPlatform extends PlatformInterface {
  /// Base constructor — subclasses must not expose a different token.
  WeChatVideoCallPlatform() : super(token: _token);

  static final Object _token = Object();

  static WeChatVideoCallPlatform _instance = MethodChannelWvc();

  /// The active platform implementation.
  static WeChatVideoCallPlatform get instance => _instance;

  /// Replaces the active implementation (verified against the platform token).
  static set instance(WeChatVideoCallPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Broadcast stream of native events.
  Stream<WeChatCallEvent> get events;

  /// Opens accessibility settings; true if already enabled.
  Future<bool> requestAccessibilityPermission();

  /// Whether the accessibility service is enabled.
  Future<bool> isAccessibilityPermissionEnabled();

  /// Opens overlay settings; true if already granted. Completes immediately.
  Future<bool> requestOverlayPermission();

  /// Whether overlay permission is granted.
  Future<bool> isOverlayPermissionEnabled();

  /// Whether WeChat is installed.
  Future<bool> isWeChatInstalled();

  /// Unified status from native plugin.
  Future<Map<String, dynamic>> getPermissionStatus();

  /// Launches WeChat.
  Future<bool> openWeChat();

  /// Whether the calibration wizard is active.
  Future<bool> isCalibrationActive();

  /// Opens the native floating calibration wizard.
  Future<bool> openCalibrationWizard();

  /// Stops the calibration wizard.
  Future<bool> stopCalibration();

  /// Calibration progress map (recorded / missing step ids, readiness).
  Future<Map<String, dynamic>> getCalibrationProgress();

  /// Raw config JSON map, or null when uncalibrated.
  Future<Map<String, dynamic>?> loadConfig();

  /// Imports a schemaVersion-2 config JSON string.
  Future<bool> importConfig(String json);

  /// Resets the whole config.
  Future<bool> resetConfig();

  /// Clears one step coordinate.
  Future<bool> resetStep(String stepId);

  /// Persists a single coordinate override.
  Future<bool> setCoordinate(String key, double fx, double fy);

  /// Starts a video call session (true = session started, not answered).
  Future<bool> videoCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  });

  /// Starts a voice call session (true = session started, not answered).
  Future<bool> voiceCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  });

  /// Cancels the active session.
  Future<bool> cancel();

  /// Taps the hang-up coordinate.
  Future<bool> hangUp();

  /// Replays a recorded step gesture (debug).
  Future<bool> debugTapStep(String stepId);

  /// Exports config JSON.
  Future<String?> exportConfig();
}
