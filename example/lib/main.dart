import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:wechat_video_call/wechat_video_call.dart';

const _intentChannel = MethodChannel('wechat_video_call_example/intent');

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
    _handleAutoCallIntent();
  }

  /// am start --es name <昵称> [--ez video true|false] 自动拨测。
  /// 冷启动走 getAutoCall；热启动走 onNewIntent → autoCall 回调。
  Future<void> _handleAutoCallIntent() async {
    _intentChannel.setMethodCallHandler((call) async {
      if (call.method == 'autoCall') {
        final args = Map<String, dynamic>.from(call.arguments as Map);
        await _runAutoCall(args);
      } else if (call.method == 'hangUp') {
        final ret = await WeChatVideoCall.hangUp();
        debugPrint('intent hangUp=$ret');
      }
    });
    try {
      final raw = await _intentChannel.invokeMethod<dynamic>('getAutoCall');
      if (raw is Map) {
        await _runAutoCall(Map<String, dynamic>.from(raw));
      }
    } catch (e) {
      debugPrint('autoCall skipped: $e');
    }
  }

  Future<void> _runAutoCall(Map<String, dynamic> args) async {
    final name = (args['name'] as String?)?.trim();
    if (name == null || name.isEmpty) return;
    final video = args['video'] != false;
    final delayScale = (args['delayScale'] as num?)?.toDouble() ?? 1.0;
    final pauseAfterStepMs = (args['pauseAfterStepMs'] as num?)?.toInt() ?? 0;
    _controller.text = name;
    debugPrint(
        'autoCall name=$name video=$video delayScale=$delayScale pause=$pauseAfterStepMs');
    final ret = video
        ? await WeChatVideoCall.videoCall(
            name,
            delayScale: delayScale,
            pauseAfterStepMs: pauseAfterStepMs,
          )
        : await WeChatVideoCall.voiceCall(
            name,
            delayScale: delayScale,
            pauseAfterStepMs: pauseAfterStepMs,
          );
    debugPrint('autoCall result=$ret');
    if (ret && !_nameList.contains(name)) {
      _nameList.add(name);
      if (mounted) setState(() {});
    }
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
