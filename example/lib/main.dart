import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
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
  String _platformVersion = 'Unknown';
  final _controller = TextEditingController();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await WechatVideoCall.getPlatformVersion() ?? 'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            children: [
              Text('Running on: $_platformVersion\n'),
              FilledButton(onPressed: () async {
                bool ret = await WechatVideoCall.requestAccessibilityPermission();
                debugPrint('requestAccessibilityPermission=$ret');
              }, child: const Text('requestAccessibilityPermission')),
              FilledButton(onPressed: () async {
                bool ret = await WechatVideoCall.isAccessibilityPermissionEnabled();
                debugPrint('isAccessibilityPermissionEnabled=$ret');
              }, child: const Text('isAccessibilityPermissionEnabled')),
            TextField(
                controller: _controller,
              decoration: InputDecoration(
                border: OutlineInputBorder(),
                hintText: '拨打视频的微信',
              ),
            ),
              FilledButton(onPressed: () async {
                bool ret = await WechatVideoCall.videoCall(_controller.text);
                debugPrint('videoCall=$ret');
              }, child: const Text('videoCall')),
            ],
          ),
        ),
      ),
    );
  }
}
