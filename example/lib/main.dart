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

class _MyAppState extends State<MyApp> with WidgetsBindingObserver {
  final _controller = TextEditingController();
  final List<String> _nameList = [];
  bool _accessibilityPermissionEnabled = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshPermission();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _controller.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshPermission();
    }
  }

  Future<void> _refreshPermission() async {
    final res = await WeChatVideoCall.isAccessibilityPermissionEnabled();
    if (!mounted) return;
    setState(() {
      _accessibilityPermissionEnabled = res;
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
                    await WeChatVideoCall.requestAccessibilityPermission();
                debugPrint('requestAccessibilityPermission=$ret');
                await _refreshPermission();
              },
              child: const Text('requestAccessibilityPermission'),
            ),
            FilledButton(
              onPressed: () async {
                await _refreshPermission();
                debugPrint(
                    'isAccessibilityPermissionEnabled=$_accessibilityPermissionEnabled');
              },
              child: const Text('isAccessibilityPermissionEnabled'),
            ),
            TextField(
              controller: _controller,
              decoration: const InputDecoration(
                hintText: '备注/昵称 (中文直输优先, 拼音亦可)',
              ),
            ),
            FilledButton(
              onPressed: () async {
                final name = _controller.text.trim();
                if (name.isEmpty) {
                  debugPrint('videoCall: name is empty');
                  return;
                }
                bool ret = await WeChatVideoCall.videoCall(name);
                debugPrint('videoCall=$ret');
                if (ret && !_nameList.contains(name)) {
                  _nameList.add(name);
                  setState(() {});
                }
              },
              child: const Text('VideoCall'),
            ),
            FilledButton(
              onPressed: () async {
                final name = _controller.text.trim();
                if (name.isEmpty) {
                  debugPrint('voiceCall: name is empty');
                  return;
                }
                bool ret = await WeChatVideoCall.voiceCall(name);
                debugPrint('voiceCall=$ret');
                if (ret && !_nameList.contains(name)) {
                  _nameList.add(name);
                  setState(() {});
                }
              },
              child: const Text('VoiceCall'),
            ),
            FilledButton(
              onPressed: () async {
                bool ret = await WeChatVideoCall.cancel();
                debugPrint('cancel=$ret');
              },
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () async {
                bool ret = await WeChatVideoCall.hangUp();
                debugPrint('hangUp=$ret');
              },
              child: const Text('HangUp'),
            ),
            Wrap(spacing: 10, children: [
              for (String name in _nameList)
                OutlinedButton(
                  onPressed: () async {
                    setState(() {
                      _controller.text = name;
                    });
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
