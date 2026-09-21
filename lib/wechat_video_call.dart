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

  /// WeChat video call with [name].
  /// [name] is the remark/nickname. It is converted to pinyin for keyboard
  /// typing (e.g. "张三" -> "zhangsan"); non-Chinese characters are kept
  /// as-is. Note: polyphonic Chinese characters may convert incorrectly.
  /// [toast] If true, the default toast will show.
  /// Note: only the call initiation is automated; there is no result
  /// callback for whether the call is answered.
  static Future<bool> videoCall(String name, {bool toast = true}) async {
    return WeChatVideoCallPlatform.instance.videoCall(name, _toPinyin(name), toast);
  }

  /// WeChat voice call with [name].
  /// See [videoCall] for [name] format.
  /// [toast] If true, the default toast will show
  static Future<bool> voiceCall(String name, {bool toast = true}) async {
    return WeChatVideoCallPlatform.instance.voiceCall(name, _toPinyin(name), toast);
  }

  /// Chinese -> pinyin (letters/digits only, lowercase) for keyboard typing.
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

  /// Tap the hang-up button of the ongoing call (coordinate based,
  /// see TESTING_NOTES.md; may need calibration per device).
  static Future<bool> hangUp() async {
    return WeChatVideoCallPlatform.instance.hangUp();
  }

  /// Coordinate keys for [setCoordinate].
  static const String coordSearchIcon = 'searchIcon';
  static const String coordHomeTab = 'homeTab';
  static const String coordSearchBox = 'searchBox';
  static const String coordPastePopup = 'pastePopup';
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
