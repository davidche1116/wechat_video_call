/// Events pushed from native via EventChannel `wechat_video_call/events`.
class WeChatCallEvent {
  /// Event type string (see [WvcEventTypes] for known values).
  final String type;

  /// Typed-ish payload; keys depend on [type].
  final Map<String, dynamic> data;

  /// Creates an event with [type] and optional [data] payload.
  const WeChatCallEvent(this.type, [this.data = const {}]);

  /// Target step id (stepProgress / calibrationStep / sessionFailed).
  String? get stepId => data['stepId'] as String?;

  /// `WVC_xxxx` error code on failure events.
  String? get errorCode => data['errorCode'] as String?;

  /// Human-readable message.
  String? get message => data['message'] as String?;

  /// Dial session id.
  String? get sessionId => data['sessionId']?.toString();

  /// Step status: `running` / `completed`.
  String? get status => data['status'] as String?;

  /// Zero-based step index in the dial plan.
  int? get index => (data['index'] as num?)?.toInt();

  /// Calibration phase (calibrationStep events).
  String? get phase => data['phase'] as String?;

  /// Builds an event from a raw EventChannel map (`type` + payload keys).
  factory WeChatCallEvent.fromMap(Map<dynamic, dynamic> map) {
    final data = <String, dynamic>{};
    map.forEach((k, v) {
      if (k != 'type') data[k.toString()] = v;
    });
    return WeChatCallEvent(map['type']?.toString() ?? 'unknown', data);
  }

  @override
  String toString() => 'WeChatCallEvent($type, $data)';
}

/// Known [WeChatCallEvent.type] values emitted by the plugin.
///
/// Unknown types are still delivered; hosts should ignore unrecognized values
/// for forward compatibility.
abstract final class WvcEventTypes {
  /// Session started (payload: sessionId, name, video, toast).
  static const sessionStarted = 'sessionStarted';

  /// Per-step dial progress (payload: stepId, index, status, message).
  static const stepProgress = 'stepProgress';

  /// Dial plan dump before gestures (payload: steps with resolvedX/Y).
  static const sessionPlan = 'sessionPlan';

  /// Session finished; callee state unknown (payload: sessionId, video).
  static const sessionSuccess = 'sessionSuccess';

  /// Session aborted (payload: errorCode, stepId, message).
  static const sessionFailed = 'sessionFailed';

  /// Raw gesture dispatch (payload: action, x, y, source, durationMs).
  static const gesture = 'gesture';

  /// Calibration wizard step lifecycle (payload: phase, stepId).
  static const calibrationStep = 'calibrationStep';

  /// Calibration progress snapshot (recordedStepIds, missing…).
  static const calibrationProgress = 'calibrationProgress';

  /// Permission state changed / requested (payload: keys per permission).
  static const permissionStatus = 'permissionStatus';

  /// Display fingerprint differs from calibration time (`WVC_0005`).
  static const configMismatch = 'configMismatch';

  /// Result of hangUp (payload: ok).
  static const hangUpResult = 'hangUpResult';
}
