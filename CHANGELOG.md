## 1.0.4
* Support Android WeChat 8.0.78 (3180): dial via `dispatchGesture` coordinates instead of the blocked accessibility node tree.
* Adaptive coordinates using status bar / navigation bar structural anchors; runtime override via `setCoordinate`.
* Three-level search input fallback (ACTION_SET_TEXT → EditText → clipboard paste).
* Add `cancel()` and `hangUp()` APIs; harden dialing pre-checks and session cleanup.
* Remove deprecated node ID scheme; update GitHub Actions publish workflow.

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
