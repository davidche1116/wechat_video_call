/// Error codes used by PlatformException and sessionFailed events.
///
/// Codes are stable (`WVC_xxxx`) and mirrored on the Kotlin side in
/// `config.ErrorCodes`.
class WvcErrorCodes {
  /// Accessibility service is off (`WVC_0001`).
  static const accessibilityDisabled = 'WVC_0001';

  /// Overlay / draw-over-apps permission missing (`WVC_0002`).
  static const overlayDisabled = 'WVC_0002';

  /// WeChat is not installed (`WVC_0003`).
  static const wechatNotInstalled = 'WVC_0003';

  /// Required calibration coordinates are missing (`WVC_0004`).
  static const configMissing = 'WVC_0004';

  /// Display fingerprint differs from calibration time (`WVC_0005`).
  static const configMismatch = 'WVC_0005';

  /// A dial step has no recorded coordinate (`WVC_0006`).
  static const stepCoordMissing = 'WVC_0006';

  /// Session exceeded its deadline (`WVC_0007`).
  static const sessionTimeout = 'WVC_0007';

  /// `dispatchGesture` failed (`WVC_0008`).
  static const gestureDispatchFailed = 'WVC_0008';

  /// Could not launch WeChat (`WVC_0009`).
  static const openWeChatFailed = 'WVC_0009';

  /// Clipboard / paste input failed (`WVC_0010`).
  static const inputFailed = 'WVC_0010';

  /// User cancelled the session (`WVC_0011`).
  static const userCancelled = 'WVC_0011';

  /// Accessibility service not connected (`WVC_0012`).
  static const serviceNotConnected = 'WVC_0012';

  /// Calibration wizard is currently active (`WVC_0013`).
  static const calibrationInProgress = 'WVC_0013';

  /// Contact name is blank (`WVC_0014`).
  static const blankName = 'WVC_0014';

  /// Invalid method-channel argument (`WVC_0015`).
  static const invalidArgument = 'WVC_0015';

  /// Hang-up coordinate missing (`WVC_0016`).
  static const hangupCoordMissing = 'WVC_0016';

  /// WeChat version does not support the action (`WVC_0017`).
  static const backLimit = 'WVC_0017';

  /// Feature not implemented (`WVC_0018`).
  static const notImplemented = 'WVC_0018';

  /// Localized (zh-CN) message for a code; falls back to the code itself.
  static String messageFor(String code) {
    switch (code) {
      case accessibilityDisabled:
        return '无障碍服务未开启';
      case overlayDisabled:
        return '悬浮窗权限未授予';
      case wechatNotInstalled:
        return '未安装微信';
      case configMissing:
        return '缺少校准配置，请先完成录点';
      case configMismatch:
        return '设备显示参数与校准时不一致';
      case stepCoordMissing:
        return '某步骤坐标缺失';
      case sessionTimeout:
        return '拨打会话超时';
      case gestureDispatchFailed:
        return '手势注入失败';
      case openWeChatFailed:
        return '无法打开微信';
      case inputFailed:
        return '粘贴输入失败';
      case userCancelled:
        return '已取消';
      case serviceNotConnected:
        return '无障碍服务未连接';
      case calibrationInProgress:
        return '校准进行中，请先完成或退出';
      case blankName:
        return '联系人名称为空';
      case invalidArgument:
        return '参数非法';
      case hangupCoordMissing:
        return '缺少挂断坐标';
      case backLimit:
        return '微信版本不支持该操作';
      case notImplemented:
        return '功能未实现';
      default:
        return code;
    }
  }
}

/// Well-known coordinate / step ids.
class WvcStepIds {
  /// Pseudo-step for "open WeChat" (not a tap target).
  static const openWeChat = 'openWeChat';

  /// Bottom "微信" tab on the conversation list.
  static const homeTab = 'homeTab';

  /// Search magnifier icon on the home page.
  static const searchIcon = 'searchIcon';

  /// Legacy id only — not wizard-recordable and unused at dial time.
  /// Prefer [searchBoxLongPress].
  static const searchBox = 'searchBox';

  /// Long-press target on the search input field.
  static const searchBoxLongPress = 'searchBoxLongPress';

  /// The "粘贴" paste bubble after a long-press.
  static const pasteBubble = 'pasteBubble';

  /// First search-result row (target friend).
  static const searchResult = 'searchResult';

  /// Chat page bottom-right "+" button.
  static const plusButton = 'plusButton';

  /// "视频通话" entry in the + panel.
  static const videoMenu = 'videoMenu';

  /// Confirm dialog button for video call.
  static const videoConfirm = 'videoConfirm';

  /// Confirm dialog button for voice call.
  static const voiceConfirm = 'voiceConfirm';

  /// Legacy cancel button id (unused in wizard).
  static const callCancel = 'callCancel';

  /// Red hang-up button on the call page.
  static const hangUp = 'hangUp';

  /// Calibration order for the wizard (user-facing).
  ///
  /// Mirrors Kotlin `StepIds.WIZARD_ORDER` — keep both lists identical.
  static const List<CalibrationStepDef> wizardSteps = [
    CalibrationStepDef(
      id: homeTab,
      title: '微信 Tab',
      guide: '若微信不在会话列表页，请点击底部第一个 Tab「微信」。',
      required: false,
      action: 'tap',
    ),
    CalibrationStepDef(
      id: searchIcon,
      title: '搜索按钮',
      guide: '请点击微信首页右上角的「搜索」放大镜。',
      required: true,
      action: 'tap',
    ),
    CalibrationStepDef(
      id: searchBoxLongPress,
      title: '长按搜索输入框',
      guide: '请长按搜索页顶部的输入框（约 0.5 秒），直到出现「粘贴」气泡。',
      required: true,
      action: 'longPress',
    ),
    CalibrationStepDef(
      id: pasteBubble,
      title: '粘贴气泡',
      guide: '请点击长按后弹出的「粘贴」菜单项。',
      required: true,
      action: 'tap',
    ),
    CalibrationStepDef(
      id: searchResult,
      title: '搜索结果',
      guide: '搜索出联系人后，请点击第一个目标好友。',
      required: true,
      action: 'tap',
    ),
    CalibrationStepDef(
      id: plusButton,
      title: '聊天页 + 号',
      guide: '请在好友聊天页点击右下角的「+」。',
      required: true,
      action: 'tap',
    ),
    CalibrationStepDef(
      id: videoMenu,
      title: '视频通话菜单',
      guide: '请点击 + 面板里的「视频通话」图标。',
      required: true,
      action: 'tap',
    ),
    CalibrationStepDef(
      id: videoConfirm,
      title: '确认：视频通话',
      guide: '若弹出确认框，请点击「视频通话」。',
      required: true,
      action: 'tap',
    ),
    CalibrationStepDef(
      id: voiceConfirm,
      title: '确认：语音通话',
      guide: '若弹出确认框，请点击「语音通话」。',
      required: true,
      action: 'tap',
    ),
    CalibrationStepDef(
      id: hangUp,
      title: '挂断按钮',
      guide: '通话界面底部红色挂断键（建议校准）。',
      required: false,
      action: 'hangUp',
    ),
  ];
}

/// One wizard step definition (id + copy + required flag + gesture).
class CalibrationStepDef {
  /// Stable step id (see [WvcStepIds]).
  final String id;

  /// Short Chinese title for host UIs.
  final String title;

  /// User-facing instruction shown in the wizard.
  final String guide;

  /// Whether dialing fails without this point.
  final bool required;

  /// Gesture kind: `tap` / `longPress` / `hangUp`.
  final String action;

  /// Creates a wizard step definition.
  const CalibrationStepDef({
    required this.id,
    required this.title,
    required this.guide,
    required this.required,
    required this.action,
  });
}
