import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:wechat_video_call/wechat_video_call.dart';

/// Thin demo host: ONLY calls [WeChatVideoCall] plugin APIs.
/// Permissions, calibration wizard UI, config IO, floating windows all live
/// inside the plugin (native overlay + accessibility service).
void main() {
  runApp(const WvcExampleApp());
}

class WvcExampleApp extends StatelessWidget {
  const WvcExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'wechat_video_call demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF07C160)),
        useMaterial3: true,
      ),
      home: const DemoHomePage(),
    );
  }
}

class DemoHomePage extends StatefulWidget {
  const DemoHomePage({super.key});

  @override
  State<DemoHomePage> createState() => _DemoHomePageState();
}

class _DemoHomePageState extends State<DemoHomePage>
    with WidgetsBindingObserver {
  final _nameCtrl = TextEditingController();
  final _importCtrl = TextEditingController();
  final _logs = <String>[];
  StreamSubscription<WeChatCallEvent>? _sub;

  WvcPermissionStatus _status = const WvcPermissionStatus();
  WvcCallConfig? _config;
  WvcCalibrationProgress _progress = const WvcCalibrationProgress();
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _sub = WeChatVideoCall.events.listen((e) {
      _appendLog('[${e.type}] ${e.data}');
    });
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _sub?.cancel();
    _nameCtrl.dispose();
    _importCtrl.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refresh();
    }
  }

  Future<void> _refresh() async {
    try {
      final status = await WeChatVideoCall.getPermissionStatus();
      final config = await WeChatVideoCall.loadConfig();
      final progress = await WeChatVideoCall.getCalibrationProgress();
      if (!mounted) return;
      setState(() {
        _status = status;
        _config = config;
        _progress = progress;
      });
    } on PlatformException catch (e) {
      _appendLog('refresh error ${e.code}: ${e.message}');
    } on MissingPluginException catch (e) {
      _appendLog('refresh MissingPluginException: $e');
    }
  }

  void _appendLog(String line) {
    final now = DateTime.now();
    final hh = now.hour.toString().padLeft(2, '0');
    final mm = now.minute.toString().padLeft(2, '0');
    final ss = now.second.toString().padLeft(2, '0');
    setState(() {
      _logs.insert(0, '$hh:$mm:$ss $line');
      if (_logs.length > 80) _logs.removeLast();
    });
  }

  Future<void> _run(String label, Future<Object?> Function() action) async {
    setState(() => _busy = true);
    try {
      final ok = await action();
      _appendLog('$label -> $ok');
    } on PlatformException catch (e) {
      _appendLog('$label error ${e.code}: ${e.message}');
    } on MissingPluginException catch (e) {
      _appendLog('$label MissingPluginException: $e');
    } catch (e) {
      _appendLog('$label error: $e');
    } finally {
      if (mounted) {
        setState(() => _busy = false);
        await _refresh();
      }
    }
  }

  /// Status chips are neutral (not pass/fail); health chips use check/cancel.
  Widget _statusChip(String label, bool active) {
    return Chip(
      avatar: Icon(
        active ? Icons.toggle_on : Icons.toggle_off,
        size: 18,
        color: active ? Colors.green.shade700 : Colors.grey.shade600,
      ),
      label: Text('$label：${active ? "是" : "否"}'),
      backgroundColor: Colors.grey.shade100,
    );
  }

  Widget _chip(String label, bool ok) {
    return Chip(
      avatar: Icon(ok ? Icons.check_circle : Icons.cancel, size: 18),
      label: Text(label),
      backgroundColor: ok ? Colors.green.shade50 : Colors.red.shade50,
    );
  }

  @override
  Widget build(BuildContext context) {
    final name = _nameCtrl.text.trim();

    return Scaffold(
      appBar: AppBar(
        title: const Text('wechat_video_call Demo'),
        actions: [
          IconButton(onPressed: _refresh, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Demo 仅调用插件接口；权限页、录点小窗、配置读写均在插件内完成。',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              // Health (pass/fail)
              _chip('无障碍', _status.accessibility),
              _chip('悬浮窗', _status.overlay),
              _chip('微信', _status.wechatInstalled),
              // Status (neutral on/off)
              _statusChip('校准中', _status.calibrationActive),
              // Readiness (pass/fail)
              _chip('视频可拨', _progress.readyForVideo),
              _chip('语音可拨', _progress.readyForVoice),
            ],
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('权限与校准', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Text(
                    '已录 ${_progress.recordedStepIds.length} 步'
                    '${_progress.missingRequiredStepIds.isEmpty ? '' : ' · 缺 ${_progress.missingRequiredStepIds.join(',')}'}',
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      FilledButton(
                        onPressed: _busy
                            ? null
                            : () => _run(
                                'requestAccessibilityPermission',
                                WeChatVideoCall.requestAccessibilityPermission,
                              ),
                        child: const Text('无障碍权限'),
                      ),
                      FilledButton.tonal(
                        onPressed: _busy
                            ? null
                            : () => _run(
                                'requestOverlayPermission',
                                WeChatVideoCall.requestOverlayPermission,
                              ),
                        child: const Text('悬浮窗权限'),
                      ),
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () => _run(
                                'openWeChat',
                                WeChatVideoCall.openWeChat,
                              ),
                        child: const Text('打开微信'),
                      ),
                      FilledButton.icon(
                        onPressed: _busy
                            ? null
                            : () => _run(
                                'openCalibrationWizard',
                                WeChatVideoCall.openCalibrationWizard,
                              ),
                        icon: const Icon(Icons.my_location),
                        label: const Text('坐标向导（插件小窗）'),
                      ),
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () => _run(
                                'stopCalibration',
                                WeChatVideoCall.stopCalibration,
                              ),
                        child: const Text('结束向导'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '配置读写（插件内持久化）',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _config == null
                        ? '尚无配置，请先打开坐标向导。'
                        : 'schema ${_config!.schemaVersion} · '
                              '${_config!.steps.where((s) => s.coord != null).length} 个坐标点',
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () async {
                                final json =
                                    await WeChatVideoCall.exportConfig();
                                if (!mounted) return;
                                setState(() {
                                  _importCtrl.text = json ?? '';
                                });
                                _appendLog(
                                  'exportConfig len=${json?.length ?? 0}',
                                );
                              },
                        child: const Text('导出配置'),
                      ),
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () => _run(
                                'importConfig',
                                () => WeChatVideoCall.importConfig(
                                  _importCtrl.text,
                                ),
                              ),
                        child: const Text('导入配置'),
                      ),
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () => _run(
                                'resetStep(homeTab)',
                                () => WeChatVideoCall.resetStep(
                                  WeChatVideoCall.stepHomeTab,
                                ),
                              ),
                        child: const Text('重置 homeTab'),
                      ),
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () => _run(
                                'resetConfig',
                                WeChatVideoCall.resetConfig,
                              ),
                        child: const Text('重置全部'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (_config != null)
                    Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      children: [
                        for (final s in _config!.steps.where(
                          (e) => e.coord != null,
                        ))
                          ActionChip(
                            label: Text(
                              '${s.id} (${s.coord!.pixelX},${s.coord!.pixelY})',
                              style: const TextStyle(fontSize: 11),
                            ),
                            onPressed: _busy
                                ? null
                                : () => _run(
                                    'debugTapStep(${s.id})',
                                    () => WeChatVideoCall.debugTapStep(s.id),
                                  ),
                          ),
                      ],
                    ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _importCtrl,
                    maxLines: 3,
                    decoration: const InputDecoration(
                      border: OutlineInputBorder(),
                      labelText: '配置 JSON（export → import）',
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('拨打流程', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _nameCtrl,
                    decoration: const InputDecoration(
                      labelText: '好友备注 / 昵称',
                      border: OutlineInputBorder(),
                      helperText: '插件内：剪贴板 → 长按搜索框 → 点粘贴气泡',
                    ),
                    onChanged: (_) => setState(() {}),
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      FilledButton.icon(
                        onPressed: _busy || name.isEmpty
                            ? null
                            : () => _run(
                                'videoCall($name)',
                                () => WeChatVideoCall.videoCall(name),
                              ),
                        icon: const Icon(Icons.videocam),
                        label: const Text('视频通话'),
                      ),
                      FilledButton.tonalIcon(
                        onPressed: _busy || name.isEmpty
                            ? null
                            : () => _run(
                                'voiceCall($name)',
                                () => WeChatVideoCall.voiceCall(name),
                              ),
                        icon: const Icon(Icons.call),
                        label: const Text('语音通话'),
                      ),
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () => _run('cancel', WeChatVideoCall.cancel),
                        child: const Text('取消'),
                      ),
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () => _run('hangUp', WeChatVideoCall.hangUp),
                        child: const Text('挂断'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        '事件日志',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const Spacer(),
                      TextButton(
                        onPressed: () => setState(() => _logs.clear()),
                        child: const Text('清空'),
                      ),
                    ],
                  ),
                  if (_logs.isEmpty)
                    const Text('暂无事件。')
                  else
                    ..._logs.asMap().entries.map(
                      (e) => Padding(
                        key: ValueKey('${e.key}-${e.value}'),
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: Text(
                          e.value,
                          style: const TextStyle(fontSize: 12),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
