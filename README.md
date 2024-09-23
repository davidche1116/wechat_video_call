[![Pub](https://img.shields.io/pub/v/wechat_video_call)](https://pub.dev/packages/wechat_video_call)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](/LICENSE)
[![Platform Flutter](https://img.shields.io/badge/platform-Flutter-blue.svg)](https://flutter.dev)

## English | [中文](https://github.com/davidche1116/wechat_video_call/blob/main/README_CN.md)
# wechat_vdeio_call

The Flutter plugin that uses Android accessibility service to automatically dial wechat video calls with one click.

Note:
- Enable the APP accessibility service in the system setting [Accessibility].
- Wechat must be logged in, and the nickname or note for the video call is already in the friends list.

---

### Using plugins
Request accessibility
```dart
bool ret = await WechatVideoCall.requestAccessibilityPermission();
```

Check accessibility
```dart
bool ret = await WechatVideoCall.isAccessibilityPermissionEnabled();
```

Make a wechat video call by nickname or remarks
```dart
bool ret = await WechatVideoCall.videoCall(nickname);
```

### Warning

Android Accessibility permissions will be automatically disabled after the APP is killed, you need to enable it again!

### Feel free to contribute
One's maintenance is lonely. If you have good suggestions and changes, feel free to contribute your code.

#### Thanks to all the people who already contributed!

<a href="https://github.com/davidche1116/wechat_video_call/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=davidche1116/wechat_video_call" />
</a>

### Thanks
[flutter_accessibility_service](https://pub.dev/packages/flutter_accessibility_service)  
[ssss-yao/xiangjian](https://github.com/ssss-yao/xiangjian)  
