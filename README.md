# wechat_video_call

[![Pub](https://img.shields.io/pub/v/wechat_video_call)](https://pub.dev/packages/wechat_video_call)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](/LICENSE)
[![Platform Flutter](https://img.shields.io/badge/platform-Flutter-blue.svg)](https://flutter.dev)

## English | [中文](README_CN.md)

Flutter plugin that dials WeChat video/voice calls on Android using **user-calibrated accessibility gestures**.

WeChat modern versions block accessibility node trees and system paste may be unavailable. This 2.0 design does **not** ship hardcoded coordinates: a floating wizard guides the user to record tap points, then the plugin replays them with `dispatchGesture`.

## Platform

| Android | iOS | MacOS | Web | Linux | Windows |
| :-----: | :-: | :---: | :-: | :---: | :-----: |
|   ✅    | ❌  |  ❌  | ❌  |  ❌  |   ❌   |

## Requirements

- Flutter >= 3.24 (developed on 3.47 stable)
- Dart >= 3.13
- Android `minSdk` 24
- WeChat installed and logged in
- Accessibility service + Overlay permission

## Input strategy (fixed)

1. Write contact name to clipboard
2. Long-press WeChat search box (calibrated point)
3. Tap paste bubble (calibrated point)

No IME-clipboard fallback. If paste UI differs by ROM, re-record those points.

## Quick start

```dart
// Permissions (both return the *current* grant state; re-check on resume)
await WeChatVideoCall.requestAccessibilityPermission();
await WeChatVideoCall.requestOverlayPermission();

// Calibrate once per device / WeChat layout
await WeChatVideoCall.openCalibrationWizard();

// Dial — **true means the session started, NOT that the callee answered**
WeChatVideoCall.events.listen((e) => print(e));
await WeChatVideoCall.videoCall('Alice');
await WeChatVideoCall.voiceCall('Alice');

await WeChatVideoCall.cancel();
await WeChatVideoCall.hangUp();
```

> **Important:** `videoCall` / `voiceCall` return `true` when the automation
> session *starts*. Whether the call is answered is only visible in WeChat.
> Listen to `sessionSuccess` / `sessionFailed` on `events` for the session result.

## Calibration steps (wizard order)

| Step id | Required | Action |
|---------|----------|--------|
| homeTab | optional | tap |
| searchIcon | yes | tap |
| searchBoxLongPress | yes | longPress |
| pasteBubble | yes | tap |
| searchResult | yes | tap |
| plusButton | yes | tap |
| videoMenu | yes | tap |
| videoConfirm | yes | tap |
| voiceConfirm | yes (voice) | tap |
| hangUp | recommended | hangUp |

## Architecture: plugin vs demo

| Capability | Where it lives |
|------------|----------------|
| Accessibility / overlay permission request | **Plugin** (`WeChatVideoCallPlugin`) |
| Floating wizard (ball, step card, record layer) | **Plugin** (`CalibrationOverlayService`) |
| Config read/write/reset/import/export | **Plugin** (`ConfigStore`) |
| Dial state machine + `dispatchGesture` | **Plugin** (`WeChatCallService`) |
| Host UI | **Demo only calls APIs** — no calibration UI or config logic |

```dart
await WeChatVideoCall.getPermissionStatus();
await WeChatVideoCall.requestAccessibilityPermission();
await WeChatVideoCall.requestOverlayPermission();
await WeChatVideoCall.openCalibrationWizard(); // native floating UI
await WeChatVideoCall.loadConfig();
await WeChatVideoCall.videoCall('Alice');
```

## Example

```bash
cd example
flutter run
```

Demo is a thin host: it only calls plugin APIs (permissions, wizard, config, dial).

## Warning

- Android accessibility is powerful and sensitive. Enable only for this purpose.
  The service requests `canPerformGestures` only (no window-content retrieval).
- Coordinates are device-specific; re-calibrate after major WeChat UI changes or resolution/display mode changes.
- The plugin only automates dialing. It does not report whether the callee answers.

## Play Store / foreground service note

The calibration wizard runs as a `specialUse` foreground service
(`user_guided_coordinate_calibration_overlay`) so the floating record UI can stay
visible while the user taps in WeChat. Prepare a `specialUse` policy declaration
and keep the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value unchanged when publishing.

## Contributing

Issues and pull requests are welcome.

## License

MIT
