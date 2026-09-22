# wechat_video_call

[![Pub](https://img.shields.io/pub/v/wechat_video_call)](https://pub.dev/packages/wechat_video_call)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](/LICENSE)
[![Platform Flutter](https://img.shields.io/badge/platform-Flutter-blue.svg)](https://flutter.dev)

## [English](README.md) | 中文

Flutter 插件：在 Android 上通过**用户校准的手势坐标**自动拨打微信视频 / 语音通话。

新版微信会封锁无障碍节点树，系统粘贴也不一定可用。本插件 **不内置硬编码坐标**：悬浮向导引导用户在本机微信界面录制点击点，再用 `dispatchGesture` 回放。

## 平台支持

| Android | iOS | MacOS | Web | Linux | Windows |
| :-----: | :-: | :---: | :-: | :---: | :-----: |
|   ✅    | ❌  |  ❌  | ❌  |  ❌  |   ❌   |

## 环境要求

- Flutter >= 3.24（开发于 3.47 stable）
- Dart >= 3.13
- Android `minSdk` 24
- 已安装并登录微信
- 无障碍服务 + 悬浮窗权限

## 输入策略（固定）

1. 将联系人备注 / 昵称写入剪贴板  
2. 长按微信搜索框（已校准坐标）  
3. 点击「粘贴」气泡（已校准坐标）  

没有 IME-剪贴板回退。若系统粘贴 UI 不同，请重新录制相关坐标。

## 快速开始

```dart
// 权限（返回的是*当前*授权状态；回前台后请重新查询）
await WeChatVideoCall.requestAccessibilityPermission();
await WeChatVideoCall.requestOverlayPermission();

// 每台设备 / 每种微信布局校准一次
await WeChatVideoCall.openCalibrationWizard();

// 拨打 —— true 表示会话已启动，不代表对方已接听
WeChatVideoCall.events.listen((e) => print(e));
await WeChatVideoCall.videoCall('Alice');
await WeChatVideoCall.voiceCall('Alice');

await WeChatVideoCall.cancel();
await WeChatVideoCall.hangUp();
```

> **重要：** `videoCall` / `voiceCall` 返回 `true` 表示自动化会话*已启动*。  
> 对方是否接听只能在微信里看。请监听 `events` 中的 `sessionSuccess` / `sessionFailed`。

## 校准步骤（向导顺序）

| Step id | 必录 | 操作 |
|---------|------|------|
| homeTab | 可选 | tap |
| searchIcon | 是 | tap |
| searchBoxLongPress | 是 | longPress |
| pasteBubble | 是 | tap |
| searchResult | 是 | tap |
| plusButton | 是 | tap |
| videoMenu | 是 | tap |
| videoConfirm | 是 | tap |
| voiceConfirm | 是（语音） | tap |
| hangUp | 推荐 | hangUp |

## 架构：插件 vs 示例

| 能力 | 位置 |
|------|------|
| 无障碍 / 悬浮窗权限申请 | **插件**（`WeChatVideoCallPlugin`） |
| 悬浮校准向导（球、步骤卡、录点层） | **插件**（`CalibrationOverlayService`） |
| 配置读写 / 重置 / 导入导出 | **插件**（`ConfigStore`） |
| 拨打状态机 + `dispatchGesture` | **插件**（`WeChatCallService`） |
| 宿主 UI | **示例只调 API** — 无校准 UI 或配置逻辑 |

```dart
await WeChatVideoCall.getPermissionStatus();
await WeChatVideoCall.requestAccessibilityPermission();
await WeChatVideoCall.requestOverlayPermission();
await WeChatVideoCall.openCalibrationWizard(); // 原生悬浮 UI
await WeChatVideoCall.loadConfig();
await WeChatVideoCall.videoCall('Alice');
```

## 示例

```bash
cd example
flutter run
```

示例是薄宿主：只调用插件 API（权限、向导、配置、拨打）。

## 警告

- Android 无障碍能力很强、也很敏感。请仅为此用途开启。  
  服务只申请 `canPerformGestures`（不读取窗口内容树）。
- 坐标与机型相关；微信大版本 UI 改版或分辨率 / 显示模式变化后请重新校准。
- 插件只负责自动拨打，**不**报告对方是否接听。

## Play Store / 前台服务说明

校准向导以 `specialUse` 前台服务运行  
（`user_guided_coordinate_calibration_overlay`），以便悬浮录点 UI 在微信上保持可见。  
上架时请准备 `specialUse` 策略说明，并保持 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 值不变。

## 贡献

欢迎 Issue 与 PR。

## 许可证

MIT
