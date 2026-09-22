import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:wechat_video_call/src/method_channel_wvc.dart';
import 'package:wechat_video_call/src/wechat_video_call_platform.dart';
import 'package:wechat_video_call/wechat_video_call.dart';

class _FakePlatform extends WeChatVideoCallPlatform
    with MockPlatformInterfaceMixin {
  bool a11y = true;
  bool overlay = true;
  bool wechat = true;
  final List<String> calls = [];

  @override
  Stream<WeChatCallEvent> get events => const Stream.empty();

  @override
  Future<bool> cancel() async {
    calls.add('cancel');
    return true;
  }

  @override
  Future<bool> debugTapStep(String stepId) async => true;

  @override
  Future<String?> exportConfig() async => '{}';

  @override
  Future<Map<String, dynamic>> getCalibrationProgress() async => {
    'recordedStepIds': <String>[],
    'missingRequiredStepIds': [WvcStepIds.searchIcon],
    'readyForVideo': false,
    'readyForVoice': false,
  };

  @override
  Future<bool> hangUp() async => true;

  @override
  Future<bool> isAccessibilityPermissionEnabled() async => a11y;

  @override
  Future<bool> isOverlayPermissionEnabled() async => overlay;

  @override
  Future<bool> isWeChatInstalled() async => wechat;

  @override
  Future<Map<String, dynamic>> getPermissionStatus() async => {
    'accessibility': a11y,
    'overlay': overlay,
    'wechatInstalled': wechat,
    'calibrationActive': false,
  };

  @override
  Future<bool> openWeChat() async {
    calls.add('openWeChat');
    return wechat;
  }

  @override
  Future<bool> isCalibrationActive() async => false;

  @override
  Future<Map<String, dynamic>?> loadConfig() async => null;

  @override
  Future<bool> importConfig(String json) async {
    calls.add('importConfig');
    return json.contains('schemaVersion');
  }

  @override
  Future<bool> openCalibrationWizard() async {
    calls.add('openCalibrationWizard');
    return true;
  }

  @override
  Future<bool> requestAccessibilityPermission() async => a11y;

  @override
  Future<bool> requestOverlayPermission() async => overlay;

  @override
  Future<bool> resetConfig() async => true;

  @override
  Future<bool> resetStep(String stepId) async => true;

  @override
  Future<bool> setCoordinate(String key, double fx, double fy) async {
    calls.add('setCoordinate:$key');
    return true;
  }

  @override
  Future<bool> stopCalibration() async => true;

  @override
  Future<bool> videoCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  }) async {
    calls.add('videoCall:$name');
    return name.trim().isNotEmpty;
  }

  @override
  Future<bool> voiceCall(
    String name, {
    bool toast = true,
    double delayScale = 1.0,
    int pauseAfterStepMs = 0,
  }) async {
    calls.add('voiceCall:$name');
    return name.trim().isNotEmpty;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('models', () {
    test('coordinate json roundtrip', () {
      const c = WvcCoordinate(
        fx: 0.5,
        fy: 0.25,
        fySpace: 'fullscreen',
        pixelX: 100,
        pixelY: 200,
        widthPx: 1080,
        heightPx: 2400,
        density: 2.75,
        statusBarPx: 100,
        navigationBarPx: 120,
        rotation: 0,
        recordedAt: '2026-09-22T00:00:00Z',
      );
      final parsed = WvcCoordinate.fromJson(c.toJson());
      expect(parsed.fx, 0.5);
      expect(parsed.fy, 0.25);
      expect(parsed.pixelX, 100);
      expect(parsed.widthPx, 1080);
      expect(parsed.recordedAt, '2026-09-22T00:00:00Z');
    });

    test('timing json roundtrip keeps stepCooldownMs', () {
      const t = WvcTiming(sessionTimeoutMs: 30000, stepCooldownMs: 2500);
      final parsed = WvcTiming.fromJson(t.toJson());
      expect(parsed.sessionTimeoutMs, 30000);
      expect(parsed.stepCooldownMs, 2500);
    });

    test('call config json roundtrip is idempotent', () {
      final cfg = const WvcCallConfig(
        updatedAt: '2026-09-22T00:00:00Z',
        timing: WvcTiming(stepCooldownMs: 2000),
        steps: [
          WvcCallStep(
            id: WvcStepIds.searchIcon,
            action: 'tap',
            required: true,
            coord: WvcCoordinate(fx: 0.9, fy: 0.1, pixelX: 9, pixelY: 1),
          ),
        ],
      );
      final once = WvcCallConfig.fromJson(cfg.toJson()).toJson();
      final twice = WvcCallConfig.fromJson(once).toJson();
      expect(once, twice);
      expect(once['timing'], containsPair('stepCooldownMs', 2000));
    });

    test('event fromMap parses type and payload', () {
      final e = WeChatCallEvent.fromMap({
        'type': 'sessionFailed',
        'errorCode': WvcErrorCodes.sessionTimeout,
        'stepId': 'searchIcon',
        'message': '会话超时',
      });
      expect(e.type, WvcEventTypes.sessionFailed);
      expect(e.errorCode, WvcErrorCodes.sessionTimeout);
      expect(e.stepId, 'searchIcon');
    });
  });

  group('error codes', () {
    test('all codes have messages', () {
      const codes = [
        WvcErrorCodes.accessibilityDisabled,
        WvcErrorCodes.overlayDisabled,
        WvcErrorCodes.wechatNotInstalled,
        WvcErrorCodes.configMissing,
        WvcErrorCodes.configMismatch,
        WvcErrorCodes.stepCoordMissing,
        WvcErrorCodes.sessionTimeout,
        WvcErrorCodes.gestureDispatchFailed,
        WvcErrorCodes.openWeChatFailed,
        WvcErrorCodes.inputFailed,
        WvcErrorCodes.userCancelled,
        WvcErrorCodes.serviceNotConnected,
        WvcErrorCodes.calibrationInProgress,
        WvcErrorCodes.blankName,
        WvcErrorCodes.invalidArgument,
        WvcErrorCodes.hangupCoordMissing,
        WvcErrorCodes.backLimit,
        WvcErrorCodes.notImplemented,
      ];
      for (final c in codes) {
        expect(WeChatVideoCall.errorMessage(c), isNot(c), reason: c);
      }
    });
  });

  group('wizard steps', () {
    test('wizard steps include paste bubble path', () {
      final ids = WeChatVideoCall.wizardSteps.map((e) => e.id).toList();
      expect(ids, contains(WvcStepIds.searchIcon));
      expect(ids, contains(WvcStepIds.searchBoxLongPress));
      expect(ids, contains(WvcStepIds.pasteBubble));
      expect(ids, contains(WvcStepIds.plusButton));
      expect(ids, contains(WvcStepIds.videoConfirm));
    });

    test('wizard step ids match Kotlin WIZARD_ORDER mirror', () {
      // Keep in sync with android/.../config/StepIds.kt WIZARD_ORDER.
      const kotlinMirror = [
        'homeTab',
        'searchIcon',
        'searchBoxLongPress',
        'pasteBubble',
        'searchResult',
        'plusButton',
        'videoMenu',
        'videoConfirm',
        'voiceConfirm',
        'hangUp',
      ];
      final ids = WvcStepIds.wizardSteps.map((e) => e.id).toList();
      expect(ids, kotlinMirror);
    });

    test('required set matches Kotlin videoRequiredIds mirror', () {
      const kotlinVideoRequired = [
        'searchIcon',
        'searchBoxLongPress',
        'pasteBubble',
        'searchResult',
        'plusButton',
        'videoMenu',
        'videoConfirm',
      ];
      final required = WvcStepIds.wizardSteps
          .where((e) => e.required)
          .map((e) => e.id)
          .toList();
      // voiceConfirm is required for voice but listed required in wizard defs
      expect(required, containsAll(kotlinVideoRequired));
    });

    test('searchBox is legacy-only (not in wizard)', () {
      final ids = WeChatVideoCall.wizardSteps.map((e) => e.id).toList();
      expect(ids, isNot(contains(WvcStepIds.searchBox)));
    });
  });

  group('facade', () {
    test('facade delegates to platform', () async {
      final fake = _FakePlatform();
      WeChatVideoCallPlatform.instance = fake;

      expect(await WeChatVideoCall.isAccessibilityPermissionEnabled(), true);
      expect(await WeChatVideoCall.openCalibrationWizard(), true);
      expect(await WeChatVideoCall.videoCall('Alice'), true);
      expect(await WeChatVideoCall.videoCall('  '), false);
      expect(fake.calls, contains('openCalibrationWizard'));
      expect(fake.calls, contains('videoCall:Alice'));

      final progress = await WeChatVideoCall.getCalibrationProgress();
      expect(progress.readyForVideo, false);

      final status = await WeChatVideoCall.getPermissionStatus();
      expect(status.accessibility, true);
      expect(status.wechatInstalled, true);
      expect(await WeChatVideoCall.openWeChat(), true);
      expect(await WeChatVideoCall.importConfig('{"schemaVersion":2}'), true);
      expect(fake.calls, contains('openWeChat'));
      expect(fake.calls, contains('importConfig'));
    });

    test('default platform is MethodChannelWvc', () {
      expect(MethodChannelWvc(), isA<WeChatVideoCallPlatform>());
    });

    test('videoCall/voiceCall forward timing args', () async {
      final fake = _FakePlatform();
      WeChatVideoCallPlatform.instance = fake;
      await WeChatVideoCall.videoCall(
        'Bob',
        toast: false,
        delayScale: 1.5,
        pauseAfterStepMs: 10,
      );
      await WeChatVideoCall.voiceCall('Bob', toast: true);
      expect(fake.calls, contains('videoCall:Bob'));
      expect(fake.calls, contains('voiceCall:Bob'));
    });
  });

  group('method channel', () {
    test('videoCall sends named args on wechat_video_call', () async {
      final log = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('wechat_video_call'), (
            call,
          ) async {
            log.add(call);
            return true;
          });
      final mc = MethodChannelWvc();
      final ok = await mc.videoCall(
        'Carol',
        toast: false,
        delayScale: 2.0,
        pauseAfterStepMs: 30,
      );
      expect(ok, true);
      expect(log.single.method, 'videoCall');
      final args = Map<Object?, Object?>.from(log.single.arguments as Map);
      expect(args['name'], 'Carol');
      expect(args['toast'], false);
      expect(args['delayScale'], 2.0);
      expect(args['pauseAfterStepMs'], 30);
    });

    test('setCoordinate sends key/fx/fy', () async {
      final log = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('wechat_video_call'), (
            call,
          ) async {
            log.add(call);
            return true;
          });
      final mc = MethodChannelWvc();
      await mc.setCoordinate('searchIcon', 0.5, 0.25);
      final args = Map<Object?, Object?>.from(log.single.arguments as Map);
      expect(log.single.method, 'setCoordinate');
      expect(args['key'], 'searchIcon');
      expect(args['fx'], 0.5);
      expect(args['fy'], 0.25);
    });
  });
}
