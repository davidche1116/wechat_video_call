/// Relative coordinate recorded by the calibration wizard.
///
/// Primary fields are [fx] / [fy] (0..1, resolution-independent). Pixel and
/// display fields are a device fingerprint for debugging / fallback scaling.
class WvcCoordinate {
  /// Horizontal fraction of screen width (0..1).
  final double fx;

  /// Vertical fraction (0..1), space defined by [fySpace].
  final double fy;

  /// `fullscreen` or `contentBelowStatusBar`.
  final String fySpace;

  /// Pixel X at record time (debug / fallback).
  final int? pixelX;

  /// Pixel Y at record time (debug / fallback).
  final int? pixelY;

  /// Screen width when recorded (displayAtRecord).
  final int? widthPx;

  /// Screen height when recorded (displayAtRecord).
  final int? heightPx;

  /// Screen density when recorded.
  final double? density;

  /// Status bar height when recorded.
  final int? statusBarPx;

  /// Navigation bar height when recorded.
  final int? navigationBarPx;

  /// Display rotation when recorded.
  final int? rotation;

  /// ISO-8601 timestamp of the recording.
  final String? recordedAt;

  /// Creates a relative coordinate with optional pixel / display fingerprint.
  const WvcCoordinate({
    required this.fx,
    required this.fy,
    this.fySpace = 'fullscreen',
    this.pixelX,
    this.pixelY,
    this.widthPx,
    this.heightPx,
    this.density,
    this.statusBarPx,
    this.navigationBarPx,
    this.rotation,
    this.recordedAt,
  });

  /// Parses the native/JSON shape (`pixelAtRecord` / `displayAtRecord` nested).
  factory WvcCoordinate.fromJson(Map<String, dynamic> json) {
    final pixel = (json['pixelAtRecord'] as Map?)?.cast<String, dynamic>();
    final display = (json['displayAtRecord'] as Map?)?.cast<String, dynamic>();
    return WvcCoordinate(
      fx: (json['fx'] as num?)?.toDouble() ?? 0,
      fy: (json['fy'] as num?)?.toDouble() ?? 0,
      fySpace: json['fySpace'] as String? ?? 'fullscreen',
      pixelX: (pixel?['x'] as num?)?.toInt(),
      pixelY: (pixel?['y'] as num?)?.toInt(),
      widthPx: (display?['widthPx'] as num?)?.toInt(),
      heightPx: (display?['heightPx'] as num?)?.toInt(),
      density: (display?['density'] as num?)?.toDouble(),
      statusBarPx: (display?['statusBarPx'] as num?)?.toInt(),
      navigationBarPx: (display?['navigationBarPx'] as num?)?.toInt(),
      rotation: (display?['rotation'] as num?)?.toInt(),
      recordedAt: json['recordedAt'] as String?,
    );
  }

  /// Serializes to the nested JSON shape used by `ConfigStore`.
  Map<String, dynamic> toJson() {
    return {
      'mode': 'relative',
      'fx': fx,
      'fy': fy,
      'fySpace': fySpace,
      if (pixelX != null && pixelY != null)
        'pixelAtRecord': {'x': pixelX, 'y': pixelY},
      if (widthPx != null)
        'displayAtRecord': {
          'widthPx': widthPx,
          'heightPx': heightPx,
          'density': density,
          'statusBarPx': statusBarPx,
          'navigationBarPx': navigationBarPx,
          'rotation': rotation,
        },
      if (recordedAt != null) 'recordedAt': recordedAt,
      'confidence': 'user',
    };
  }
}

/// One dial/wizard step with optional recorded coordinate.
class WvcCallStep {
  /// Stable step id (see [WvcStepIds]).
  final String id;

  /// Gesture kind: `tap` / `longPress` / `hangUp`.
  final String action;

  /// Whether dialing requires this step.
  final bool required;

  /// Delay after the gesture before the next step (ms).
  final int delayAfterMs;

  /// Gesture hold duration for long-press (ms).
  final int? durationMs;

  /// Recorded coordinate, or null if not yet calibrated.
  final WvcCoordinate? coord;

  /// Creates a dial/wizard step.
  const WvcCallStep({
    required this.id,
    required this.action,
    required this.required,
    this.delayAfterMs = 1200,
    this.durationMs,
    this.coord,
  });

  /// Parses from config JSON.
  factory WvcCallStep.fromJson(Map<String, dynamic> json) {
    final coordJson = json['coord'];
    return WvcCallStep(
      id: json['id'] as String,
      action: json['action'] as String? ?? 'tap',
      required: json['required'] as bool? ?? false,
      delayAfterMs: (json['delayAfterMs'] as num?)?.toInt() ?? 1200,
      durationMs: (json['durationMs'] as num?)?.toInt(),
      coord: coordJson is Map
          ? WvcCoordinate.fromJson(coordJson.cast<String, dynamic>())
          : null,
    );
  }

  /// Serializes to config JSON.
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'action': action,
      'required': required,
      'delayAfterMs': delayAfterMs,
      if (durationMs != null) 'durationMs': durationMs,
      if (coord != null) 'coord': coord!.toJson(),
    };
  }

  /// Returns a copy with a replaced [coord].
  WvcCallStep copyWith({WvcCoordinate? coord}) {
    return WvcCallStep(
      id: id,
      action: action,
      required: required,
      delayAfterMs: delayAfterMs,
      durationMs: durationMs,
      coord: coord ?? this.coord,
    );
  }
}

/// Timing knobs for a dial session (persisted under config `timing`).
class WvcTiming {
  /// Hard session deadline (ms).
  final int sessionTimeoutMs;

  /// Minimum gap between steps (ms).
  final int stepCooldownMs;

  /// Fallback per-step delay (ms).
  final int defaultDelayAfterMs;

  /// Multiplier applied to every delay (`> 0`).
  final double delayScale;

  /// Extra pause appended to every scaled delay (ms).
  final int pauseAfterStepMs;

  /// Post search-result delay (ms).
  final int searchResultDelayMs;

  /// Post `+` button delay (ms).
  final int plusButtonDelayMs;

  /// Post video-menu delay (ms).
  final int videoMenuDelayMs;

  /// Post confirm-dialog delay (ms).
  final int confirmDelayMs;

  /// Long-press hold duration (ms).
  final int longPressDurationMs;

  /// Creates timing knobs (defaults match Kotlin `CallTiming`).
  const WvcTiming({
    this.sessionTimeoutMs = 45000,
    this.stepCooldownMs = 1500,
    this.defaultDelayAfterMs = 1200,
    this.delayScale = 1.0,
    this.pauseAfterStepMs = 0,
    this.searchResultDelayMs = 3000,
    this.plusButtonDelayMs = 2500,
    this.videoMenuDelayMs = 2000,
    this.confirmDelayMs = 2000,
    this.longPressDurationMs = 600,
  });

  /// Parses from config JSON.
  factory WvcTiming.fromJson(Map<String, dynamic> json) {
    return WvcTiming(
      sessionTimeoutMs: (json['sessionTimeoutMs'] as num?)?.toInt() ?? 45000,
      stepCooldownMs: (json['stepCooldownMs'] as num?)?.toInt() ?? 1500,
      defaultDelayAfterMs:
          (json['defaultDelayAfterMs'] as num?)?.toInt() ?? 1200,
      delayScale: (json['delayScale'] as num?)?.toDouble() ?? 1.0,
      pauseAfterStepMs: (json['pauseAfterStepMs'] as num?)?.toInt() ?? 0,
      searchResultDelayMs:
          (json['searchResultDelayMs'] as num?)?.toInt() ?? 3000,
      plusButtonDelayMs: (json['plusButtonDelayMs'] as num?)?.toInt() ?? 2500,
      videoMenuDelayMs: (json['videoMenuDelayMs'] as num?)?.toInt() ?? 2000,
      confirmDelayMs: (json['confirmDelayMs'] as num?)?.toInt() ?? 2000,
      longPressDurationMs:
          (json['longPressDurationMs'] as num?)?.toInt() ?? 600,
    );
  }

  /// Serializes to config JSON (round-trip safe with [fromJson]).
  Map<String, dynamic> toJson() => {
    'sessionTimeoutMs': sessionTimeoutMs,
    'stepCooldownMs': stepCooldownMs,
    'defaultDelayAfterMs': defaultDelayAfterMs,
    'delayScale': delayScale,
    'pauseAfterStepMs': pauseAfterStepMs,
    'searchResultDelayMs': searchResultDelayMs,
    'plusButtonDelayMs': plusButtonDelayMs,
    'videoMenuDelayMs': videoMenuDelayMs,
    'confirmDelayMs': confirmDelayMs,
    'longPressDurationMs': longPressDurationMs,
  };
}

/// Device fingerprint captured when the config was created / calibrated.
class WvcDeviceMeta {
  /// `Build.MODEL`.
  final String? model;

  /// `Build.MANUFACTURER`.
  final String? manufacturer;

  /// Android version string.
  final String? androidVersion;

  /// Android API level.
  final int? apiLevel;

  /// Screen width (px).
  final int? widthPx;

  /// Screen height (px).
  final int? heightPx;

  /// Screen density.
  final double? density;

  /// Status bar height (px).
  final int? statusBarPx;

  /// Navigation bar height (px).
  final int? navigationBarPx;

  /// Display rotation (0..3).
  final int? rotation;

  /// Creates a device fingerprint record.
  const WvcDeviceMeta({
    this.model,
    this.manufacturer,
    this.androidVersion,
    this.apiLevel,
    this.widthPx,
    this.heightPx,
    this.density,
    this.statusBarPx,
    this.navigationBarPx,
    this.rotation,
  });

  /// Parses from config JSON.
  factory WvcDeviceMeta.fromJson(Map<String, dynamic> json) {
    return WvcDeviceMeta(
      model: json['model'] as String?,
      manufacturer: json['manufacturer'] as String?,
      androidVersion: json['androidVersion'] as String?,
      apiLevel: (json['apiLevel'] as num?)?.toInt(),
      widthPx: (json['widthPx'] as num?)?.toInt(),
      heightPx: (json['heightPx'] as num?)?.toInt(),
      density: (json['density'] as num?)?.toDouble(),
      statusBarPx: (json['statusBarPx'] as num?)?.toInt(),
      navigationBarPx: (json['navigationBarPx'] as num?)?.toInt(),
      rotation: (json['rotation'] as num?)?.toInt(),
    );
  }

  /// Serializes to config JSON.
  Map<String, dynamic> toJson() => {
    if (model != null) 'model': model,
    if (manufacturer != null) 'manufacturer': manufacturer,
    if (androidVersion != null) 'androidVersion': androidVersion,
    if (apiLevel != null) 'apiLevel': apiLevel,
    if (widthPx != null) 'widthPx': widthPx,
    if (heightPx != null) 'heightPx': heightPx,
    if (density != null) 'density': density,
    if (statusBarPx != null) 'statusBarPx': statusBarPx,
    if (navigationBarPx != null) 'navigationBarPx': navigationBarPx,
    if (rotation != null) 'rotation': rotation,
  };
}

/// Full calibration config (schemaVersion 2).
class WvcCallConfig {
  /// Config schema version (currently 2).
  final int schemaVersion;

  /// Profile id (usually `device-<model>`).
  final String configId;

  /// Last update timestamp (ISO-8601).
  final String? updatedAt;

  /// Device fingerprint at config creation.
  final WvcDeviceMeta? device;

  /// Timing knobs.
  final WvcTiming timing;

  /// Ordered steps with optional coordinates.
  final List<WvcCallStep> steps;

  /// Creates a calibration config (schemaVersion 2).
  const WvcCallConfig({
    this.schemaVersion = 2,
    this.configId = 'default',
    this.updatedAt,
    this.device,
    this.timing = const WvcTiming(),
    this.steps = const [],
  });

  /// Parses from config JSON.
  factory WvcCallConfig.fromJson(Map<String, dynamic> json) {
    final stepsRaw = json['steps'];
    return WvcCallConfig(
      schemaVersion: (json['schemaVersion'] as num?)?.toInt() ?? 2,
      configId: json['configId'] as String? ?? 'default',
      updatedAt: json['updatedAt'] as String?,
      device: json['device'] is Map
          ? WvcDeviceMeta.fromJson(
              (json['device'] as Map).cast<String, dynamic>(),
            )
          : null,
      timing: json['timing'] is Map
          ? WvcTiming.fromJson((json['timing'] as Map).cast<String, dynamic>())
          : const WvcTiming(),
      steps: stepsRaw is List
          ? stepsRaw
                .whereType<Map>()
                .map((e) => WvcCallStep.fromJson(e.cast<String, dynamic>()))
                .toList()
          : const [],
    );
  }

  /// Serializes to config JSON (round-trip safe with [fromJson]).
  Map<String, dynamic> toJson() => {
    'schemaVersion': schemaVersion,
    'configId': configId,
    if (updatedAt != null) 'updatedAt': updatedAt,
    if (device != null) 'device': device!.toJson(),
    'timing': timing.toJson(),
    'steps': steps.map((e) => e.toJson()).toList(),
    'input': {'mode': 'pasteBubbleOnly', 'clipboardWriteOnSessionStart': true},
  };

  /// Looks up a step by [id].
  WvcCallStep? stepById(String id) {
    for (final s in steps) {
      if (s.id == id) return s;
    }
    return null;
  }

  /// Whether [stepId] already has a recorded coordinate.
  bool hasCoord(String stepId) => stepById(stepId)?.coord != null;

  /// Returns a copy with replaced [steps] / [timing].
  WvcCallConfig copyWith({List<WvcCallStep>? steps, WvcTiming? timing}) {
    return WvcCallConfig(
      schemaVersion: schemaVersion,
      configId: configId,
      updatedAt: updatedAt,
      device: device,
      timing: timing ?? this.timing,
      steps: steps ?? this.steps,
    );
  }
}

/// Snapshot of wizard progress returned by `getCalibrationProgress`.
class WvcCalibrationProgress {
  /// Step ids that already have coordinates.
  final List<String> recordedStepIds;

  /// Required step ids still missing (video set).
  final List<String> missingRequiredStepIds;

  /// True when every video-required point is recorded.
  final bool readyForVideo;

  /// True when every voice-required point is recorded.
  final bool readyForVoice;

  /// Step the wizard is currently on (if any).
  final String? currentStepId;

  /// Creates a calibration-progress snapshot.
  const WvcCalibrationProgress({
    this.recordedStepIds = const [],
    this.missingRequiredStepIds = const [],
    this.readyForVideo = false,
    this.readyForVoice = false,
    this.currentStepId,
  });

  /// Parses from the platform channel map.
  factory WvcCalibrationProgress.fromJson(Map<String, dynamic> json) {
    return WvcCalibrationProgress(
      recordedStepIds:
          (json['recordedStepIds'] as List?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      missingRequiredStepIds:
          (json['missingRequiredStepIds'] as List?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      readyForVideo: json['readyForVideo'] as bool? ?? false,
      readyForVoice: json['readyForVoice'] as bool? ?? false,
      currentStepId: json['currentStepId'] as String?,
    );
  }
}

/// Native permission / environment status reported by the plugin.
class WvcPermissionStatus {
  /// Accessibility service enabled.
  final bool accessibility;

  /// Overlay (draw-over-apps) granted.
  final bool overlay;

  /// WeChat package present.
  final bool wechatInstalled;

  /// Floating calibration wizard currently running.
  final bool calibrationActive;

  /// Creates a permission / environment status snapshot.
  const WvcPermissionStatus({
    this.accessibility = false,
    this.overlay = false,
    this.wechatInstalled = false,
    this.calibrationActive = false,
  });

  /// True when dialing can start (accessibility + WeChat).
  ///
  /// Overlay is only required for the calibration wizard, not for dialing.
  bool get readyToDial => accessibility && wechatInstalled;

  /// Parses from the platform channel map.
  factory WvcPermissionStatus.fromJson(Map<String, dynamic> json) {
    return WvcPermissionStatus(
      accessibility: json['accessibility'] as bool? ?? false,
      overlay: json['overlay'] as bool? ?? false,
      wechatInstalled: json['wechatInstalled'] as bool? ?? false,
      calibrationActive: json['calibrationActive'] as bool? ?? false,
    );
  }
}
