import 'package:lpinyin/lpinyin.dart';

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

  /// WeChat video call with [name] (remark/nickname in the friends list).
  ///
  /// Native side pastes [name] into WeChat search via clipboard + long-press,
  /// so Chinese names work directly. [pinyin] is still sent as a fallback
  /// query string (e.g. "张三" -> "zhangsan") when clipboard typing is not used.
  /// Returns false when the accessibility service is off, WeChat is missing,
  /// or [name] is blank. This only automates dialing — there is no callback
  /// for whether the callee answers.
  /// [delayScale] multiplies native step delays (>1 = slow debug).
  /// [pauseAfterStepMs] extra wait after each step for screenshot observation.
  static Future<bool> videoCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  }) async {
    return WeChatVideoCallPlatform.instance.videoCall(
      name,
      _toPinyin(name),
      toast,
      delayScale: delayScale,
      pauseAfterStepMs: pauseAfterStepMs,
    );
  }

  /// WeChat voice call with [name]. See [videoCall].
  static Future<bool> voiceCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  }) async {
    return WeChatVideoCallPlatform.instance.voiceCall(
      name,
      _toPinyin(name),
      toast,
      delayScale: delayScale,
      pauseAfterStepMs: pauseAfterStepMs,
    );
  }

  /// Chinese -> pinyin (letters/digits only, lowercase) fallback query.
  static String _toPinyin(String name) {
    final trimmed = name.trim();
    if (trimmed.isEmpty) return '';
    String pinyin;
    try {
      pinyin = PinyinHelper.getPinyinE(trimmed);
    } catch (_) {
      pinyin = trimmed;
    }
    final clean = pinyin.replaceAll(RegExp(r'[^a-zA-Z0-9]'), '').toLowerCase();
    return clean.isEmpty ? trimmed.toLowerCase() : clean;
  }

  /// Cancel an ongoing automated call flow.
  static Future<bool> cancel() async {
    return WeChatVideoCallPlatform.instance.cancel();
  }

  /// Tap the hang-up button of the ongoing call (coordinate based;
  /// may need calibration via [setCoordinate] on some devices).
  static Future<bool> hangUp() async {
    return WeChatVideoCallPlatform.instance.hangUp();
  }

  /// Coordinate keys for [setCoordinate].
  static const String coordSearchIcon = 'searchIcon';
  static const String coordHomeTab = 'homeTab';
  static const String coordSearchBox = 'searchBox';
  static const String coordPastePopup = 'pastePopup';
  static const String coordImeClipboard = 'imeClipboard';
  static const String coordImeClipboardFirst = 'imeClipboardFirst';
  static const String coordSearchResult = 'searchResult';
  static const String coordPlusButton = 'plusButton';
  static const String coordVideoMenu = 'videoMenu';
  static const String coordVideoConfirm = 'videoConfirm';
  static const String coordVoiceConfirm = 'voiceConfirm';
  static const String coordHangUp = 'hangUp';

  /// Override a coordinate point at runtime for device adaptation.
  /// [fx]/[fy] are relative fractions (0..1) of screen width/height.
  static Future<bool> setCoordinate(String key, double fx, double fy) async {
    return WeChatVideoCallPlatform.instance.setCoordinate(key, fx, fy);
  }
}
