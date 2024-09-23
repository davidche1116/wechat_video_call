import 'package:flutter/material.dart';
import 'package:wechat_video_call/wechat_video_call.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _controller = TextEditingController();
  final List<String> _nameList = [];
  bool _accessibilityPermissionEnabled = false;

  @override
  void initState() {
    super.initState();
    WechatVideoCall.isAccessibilityPermissionEnabled().then((res) {
      setState(() {
        _accessibilityPermissionEnabled = res;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('WeChat Video Call'),
        ),
        body: ListView(
          padding: const EdgeInsets.all(10),
          children: [
            ListTile(
              title: const Text('AccessibilityPermission'),
              trailing: Text(_accessibilityPermissionEnabled ? 'On' : 'Off'),
            ),
            FilledButton(
              onPressed: () async {
                bool ret =
                    await WechatVideoCall.requestAccessibilityPermission();
                debugPrint('requestAccessibilityPermission=$ret');
              },
              child: const Text('requestAccessibilityPermission'),
            ),
            FilledButton(
              onPressed: () async {
                bool ret =
                    await WechatVideoCall.isAccessibilityPermissionEnabled();
                debugPrint('isAccessibilityPermissionEnabled=$ret');
                setState(() {
                  _accessibilityPermissionEnabled = ret;
                });
              },
              child: const Text('isAccessibilityPermissionEnabled'),
            ),
            TextField(
              controller: _controller,
              decoration: const InputDecoration(
                hintText: 'WeChat Nickname',
              ),
            ),
            FilledButton(
              onPressed: () async {
                String name = _controller.text;
                bool ret = await WechatVideoCall.videoCall(name);
                debugPrint('videoCall=$ret');
                if (!_nameList.contains(name)) {
                  _nameList.add(name);
                  setState(() {});
                }
              },
              child: const Text('VideoCall'),
            ),
            Wrap(spacing: 10, children: [
              for (String name in _nameList)
                OutlinedButton(
                  onPressed: () async {
                    bool ret = await WechatVideoCall.videoCall(name);
                    debugPrint('videoCall=$ret');
                  },
                  onLongPress: () async {
                    _nameList.remove(name);
                    setState(() {});
                  },
                  child: Text(name),
                ),
            ]),
          ],
        ),
      ),
    );
  }
}
