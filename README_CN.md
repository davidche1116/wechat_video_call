[![Pub](https://img.shields.io/pub/v/wechat_video_call)](https://pub.dev/packages/wechat_video_call)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](/LICENSE)
[![Platform Flutter](https://img.shields.io/badge/platform-Flutter-blue.svg)](https://flutter.dev)

## [English](https://github.com/davidche1116/wechat_video_call/blob/main/README.md) | 中文
# 微信视频电话

利用Android无障碍服务一键自动拨打微信视频通话、语音通话的Flutter插件。

要使用该功能必须:
- 在系统设置【无障碍】中将APP无障碍服务开启。
- 微信必须是登录状态，并且拨打视频通话的昵称或备注已经在好友列表中。

---

### 平台支持

| Android | iOS | MacOS | Web | Linux | Windows |
| :-----: | :-: | :---: | :-: | :---: | :-----: |
|   ✅    |   |     |   |     |       |

### 环境要求

- Flutter >=3.24.0
- Dart >=3.5.0
- Android `minSdk` 24
- Java 17
- Android Gradle Plugin >=8.1.0
- Gradle wrapper >=8.3

### 插件使用
请求无障碍权限
```dart
bool ret = await WeChatVideoCall.requestAccessibilityPermission();
```

检查无障碍权限
```dart
bool ret = await WeChatVideoCall.isAccessibilityPermissionEnabled();
```

通过昵称或备注拨打微信视频通话
```dart
bool ret = await WeChatVideoCall.videoCall(nickname);
```

### 警告说明

Android无障碍权限在APP被KILL后会自动失效，需要再次开启！

### 欢迎贡献
一个人的维护是孤独的。如果你有好的建议和改动，欢迎贡献你的代码。

__感谢所有的贡献者!__

<a href="https://github.com/davidche1116/wechat_video_call/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=davidche1116/wechat_video_call" />
</a>

### 示例APP
[打电话](https://github.com/davidche1116/CallApp)
[下载](https://github.com/davidche1116/CallApp/releases)

### 感谢
[flutter_accessibility_service](https://pub.dev/packages/flutter_accessibility_service)  
[ssss-yao/xiangjian](https://github.com/ssss-yao/xiangjian)  
