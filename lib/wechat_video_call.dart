import 'src/models/error_codes.dart';
import 'src/models/coordinate.dart';
import 'src/models/wechat_event.dart';
import 'src/wechat_video_call_platform.dart';

export 'src/models/coordinate.dart';
export 'src/models/error_codes.dart';
export 'src/models/wechat_event.dart';

/// Facade API for the wechat_video_call plugin (2.0).
///
/// Coordinates come from the user calibration wizard. Search input uses
/// clipboard + long-press + paste bubble only (no IME clipboard fallback).
///
/// Typical host flow:
/// 1. [requestAccessibilityPermission] / [requestOverlayPermission]
/// 2. [openCalibrationWizard] once per device / WeChat layout
/// 3. [videoCall] / [voiceCall] and listen to [events] for the outcome
class WeChatVideoCall {
  /// Private constructor — facade is static-only.
  WeChatVideoCall._();

  static WeChatVideoCallPlatform get _p => WeChatVideoCallPlatform.instance;

  /// Event stream from native: `stepProgress` / `sessionStarted` /
  /// `sessionSuccess` / `sessionFailed` / `sessionPlan` / `gesture` /
  /// `calibrationStep` / `calibrationProgress` / `permissionStatus` /
  /// `configMismatch` / `hangUpResult`.
  ///
  /// See [WvcEventTypes] for known [WeChatCallEvent.type] values.
  static Stream<WeChatCallEvent> get events => _p.events;

  /// Opens the system accessibility settings screen.
  ///
  /// Returns `true` if the service is already enabled; otherwise navigates to
  /// settings and returns `false` ("current state, not granted yet").
  /// Re-check with [isAccessibilityPermissionEnabled] on app resume.
  static Future<bool> requestAccessibilityPermission() {
    return _p.requestAccessibilityPermission();
  }

  /// Whether the plugin's accessibility service is currently enabled.
  static Future<bool> isAccessibilityPermissionEnabled() {
    return _p.isAccessibilityPermissionEnabled();
  }

  /// Whether the host has the overlay (draw-over-other-apps) permission.
  static Future<bool> isOverlayPermissionEnabled() {
    return _p.isOverlayPermissionEnabled();
  }

  /// Opens the system overlay-permission screen for this app.
  ///
  /// Returns `true` if already granted; otherwise navigates to settings and
  /// returns `false` ("current state, not granted yet"). Re-check with
  /// [isOverlayPermissionEnabled] on app resume. **Does not hang** — the
  /// Future completes as soon as the settings page is launched.
  static Future<bool> requestOverlayPermission() {
    return _p.requestOverlayPermission();
  }

  /// Whether WeChat (`com.tencent.mm`) is installed.
  static Future<bool> isWeChatInstalled() {
    return _p.isWeChatInstalled();
  }

  /// Unified native status: accessibility / overlay / wechat / calibrationActive.
  ///
  /// [WvcPermissionStatus.readyToDial] is true when accessibility is on and
  /// WeChat is installed (overlay is only required for the calibration wizard).
  static Future<WvcPermissionStatus> getPermissionStatus() async {
    final map = await _p.getPermissionStatus();
    return WvcPermissionStatus.fromJson(map);
  }

  /// Launch WeChat via plugin (used by hosts and the floating wizard).
  ///
  /// Returns `true` if the launch intent was delivered.
  static Future<bool> openWeChat() => _p.openWeChat();

  /// Whether the native floating calibration wizard is currently showing.
  static Future<bool> isCalibrationActive() => _p.isCalibrationActive();

  /// Opens the native floating calibration wizard.
  ///
  /// Requires overlay permission. Returns `false` (and emits `sessionFailed`
  /// with `WVC_0002`) if the overlay permission is missing or the service
  /// cannot be started.
  static Future<bool> openCalibrationWizard() {
    return _p.openCalibrationWizard();
  }

  /// Stops the floating calibration wizard if it is running.
  static Future<bool> stopCalibration() {
    return _p.stopCalibration();
  }

  /// Progress of recorded calibration points (required / optional steps).
  static Future<WvcCalibrationProgress> getCalibrationProgress() async {
    final map = await _p.getCalibrationProgress();
    return WvcCalibrationProgress.fromJson(map);
  }

  /// Loads the persisted calibration config, or `null` if none exists yet.
  static Future<WvcCallConfig?> loadConfig() async {
    final map = await _p.loadConfig();
    if (map == null) return null;
    return WvcCallConfig.fromJson(map);
  }

  /// Import a config JSON previously produced by [exportConfig].
  ///
  /// Expects `schemaVersion == 2`. Incomplete configs are accepted so the
  /// host can continue calibrating.
  static Future<bool> importConfig(String json) => _p.importConfig(json);

  /// Resets all recorded coordinates (creates a fresh device profile).
  static Future<bool> resetConfig() => _p.resetConfig();

  /// Clears the coordinate for a single [stepId].
  static Future<bool> resetStep(String stepId) => _p.resetStep(stepId);

  /// Persist a single coordinate override (legacy-compatible key names).
  ///
  /// [key] is a step id or a legacy alias (`pastePopup` → `pasteBubble`,
  /// `searchBox` → `searchBoxLongPress`). [fx] / [fy] must be in `0..1`.
  static Future<bool> setCoordinate(String key, double fx, double fy) {
    return _p.setCoordinate(key, fx, fy);
  }

  /// Starts a video call session.
  ///
  /// **Returns `true` if the session was *started*, NOT whether the callee
  /// answered.** Listen to [events] for `sessionSuccess` / `sessionFailed`.
  ///
  /// [name] is the WeChat remark / nickname (must be non-blank).
  /// [toast] asks native to show a short status toast (currently reserved).
  /// [delayScale] scales inter-step delays (`> 0`).
  /// [pauseAfterStepMs] adds an extra pause after every step.
  static Future<bool> videoCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  }) {
    return _p.videoCall(
      name,
      toast: toast,
      delayScale: delayScale,
      pauseAfterStepMs: pauseAfterStepMs,
    );
  }

  /// Starts a voice call session.
  ///
  /// **Returns `true` if the session was *started*, NOT whether the callee
  /// answered.** Listen to [events] for the result. See [videoCall].
  static Future<bool> voiceCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  }) {
    return _p.voiceCall(
      name,
      toast: toast,
      delayScale: delayScale,
      pauseAfterStepMs: pauseAfterStepMs,
    );
  }

  /// Cancels the in-flight call session (emits `sessionFailed` / `WVC_0011`).
  ///
  /// Returns `false` if no service is connected (also emits `WVC_0012`).
  static Future<bool> cancel() => _p.cancel();

  /// Taps the recorded hang-up coordinate (optional step `hangUp`).
  ///
  /// Returns `false` if the hang-up coordinate is missing or the service is
  /// disconnected (emits the matching `sessionFailed` error code).
  static Future<bool> hangUp() => _p.hangUp();

  /// Debug helper: replays the recorded gesture for [stepId].
  ///
  /// Intended for calibration verification only.
  static Future<bool> debugTapStep(String stepId) => _p.debugTapStep(stepId);

  /// Exports the current config as pretty-printed JSON, or `null` if empty.
  static Future<String?> exportConfig() => _p.exportConfig();

  /// Step id constants.
  ///
  /// Search magnifier on the home page.
  static const String stepSearchIcon = WvcStepIds.searchIcon;

  /// Bottom "微信" tab.
  static const String stepHomeTab = WvcStepIds.homeTab;

  /// Legacy step id — not wizard-recordable and unused at dial time.
  /// Prefer [stepSearchBoxLongPress] (the actual long-press target).
  static const String stepSearchBox = WvcStepIds.searchBox;

  /// Long-press target on the search input field.
  static const String stepSearchBoxLongPress = WvcStepIds.searchBoxLongPress;

  /// The "粘贴" paste bubble after a long-press.
  static const String stepPasteBubble = WvcStepIds.pasteBubble;

  /// First search-result row (target friend).
  static const String stepSearchResult = WvcStepIds.searchResult;

  /// Chat page bottom-right "+" button.
  static const String stepPlusButton = WvcStepIds.plusButton;

  /// "视频通话" entry in the + panel.
  static const String stepVideoMenu = WvcStepIds.videoMenu;

  /// Confirm dialog button for video call.
  static const String stepVideoConfirm = WvcStepIds.videoConfirm;

  /// Confirm dialog button for voice call.
  static const String stepVoiceConfirm = WvcStepIds.voiceConfirm;

  /// Red hang-up button on the call page.
  static const String stepHangUp = WvcStepIds.hangUp;

  /// Wizard step definitions for custom host UIs.
  static List<CalibrationStepDef> get wizardSteps => WvcStepIds.wizardSteps;

  /// Localized message for a [WvcErrorCodes] value.
  static String errorMessage(String code) => WvcErrorCodes.messageFor(code);
}
