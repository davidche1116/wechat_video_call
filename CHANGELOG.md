## 2.0.2

### Fix

- **Paste bubble often unclickable after long-pressing the search box.**
  2.0.1 scheduled the paste tap `delayAfterMs` (default 400ms) from gesture
  *dispatch start*, while the long-press hold itself is 600ms — the paste tap
  fired before the hold finished and before the system paste menu appeared.
  Next-step scheduling now waits for the gesture hold to complete, then a
  post-long-press settle floor of 800ms (applied even to old configs that
  stored 400ms, and after `delayScale` so fast mode cannot starve the menu).
- `CallConfig.defaultDelay(searchBoxLongPress)` raised 400 → 800.

## 2.0.1

### Performance

- P0 timing: post-step waits capped at 200–1000ms (was 1500–3500ms hardcoded).
- Dial plan now uses `CallStep.delayAfterMs` / `CallTiming` instead of longer
  hardcoded `buildPlan` values.
- New `CallTiming.launchSettleMs` (default 500) replaces the fixed 2200ms
  first-gesture wait after opening WeChat.

## 2.0.0

### Breaking migration from 1.x

- **Calibration is mandatory.** `videoCall` / `voiceCall` now return `false` and
  emit `sessionFailed` / `WVC_0004` (`CONFIG_MISSING`) when required points have
  not been recorded. Run `openCalibrationWizard()` once per device / WeChat layout
  before dialing.
- **`setCoordinate` semantics changed.** It now persists a single step override
  into the device config (and creates a config if needed) instead of an in-memory
  runtime patch. Key names accept legacy aliases (`pastePopup` → `pasteBubble`,
  `searchBox` → `searchBoxLongPress`). Prefer the floating wizard.
- **Input strategy is fixed** to clipboard + long-press search box + paste bubble.
  The 1.x IME-clipboard / `SET_TEXT` fallback chain was removed. If paste UI
  differs by ROM, re-record `searchBoxLongPress` and `pasteBubble`.
- **`requestOverlayPermission` / `requestAccessibilityPermission` return the
  *current* grant state** (`false` after opening settings). Re-query on
  `AppLifecycleState.resumed`.
- API surface is facade-based (`WeChatVideoCall.*`) with a federated
  platform-interface layout. The 1.x method-channel helpers were removed.

### Features

* Rewrite plugin: user-calibrated coordinates via floating wizard overlay.
* Input path fixed to clipboard + long-press search box + paste bubble (no IME clipboard).
* Accessibility gestures via `dispatchGesture` (node tree not required).
* New APIs: overlay permission, calibration wizard, event stream, config load/reset.
* Example demo covers full flow: permissions → calibrate → dial → hang up.

## 1.0.4
* Support Android WeChat 8.0.78 (3180): dial via dispatchGesture coordinates.
* Adaptive coordinates; cancel()/hangUp() APIs.

## 1.0.3
* Test support Android WeChat 8.0.69 (3040).

## 1.0.2
* Add support Android WeChat 8.0.54 (2760).

## 1.0.1
* Update dependency

## 1.0.0
* Fix: requestAccessibilityPermission is no return value
* Fix: WeChat spelling
* Optimize WeChat return to home page

## 0.0.3
* Configure automated publishing through GitHub Actions

## 0.0.2
* Add support Android WeChat 8.0.51 (2720).
* Code formatting
* Support voice call

## 0.0.1
* Initial implementation of functions, support Android WeChat 8.0.50 (2701).
