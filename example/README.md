# wechat_video_call_example

Minimal host for the `wechat_video_call` plugin. The demo only calls plugin APIs —
permissions, the native floating calibration wizard, config import/export, and dialing all live inside the plugin.

## Run

```bash
cd example
flutter run
```

Requires an Android device/emulator with WeChat installed and logged in.

## Feature tour

1. **Status chips** — accessibility / overlay / WeChat (health) and calibration state (neutral).
2. **Permissions** — open accessibility & overlay settings (re-checked on app resume).
3. **Calibration wizard** — launches the plugin's floating calibration UI over WeChat.
4. **Config IO** — export / import schemaVersion-2 JSON, reset one step or all.
5. **Dial flow** — `videoCall` / `voiceCall` / `cancel` / `hangUp`.
6. **Event log** — live EventChannel stream (`sessionStarted`, `stepProgress`, `sessionFailed`, …).

## Notes

- `videoCall` returns whether the session *started*, not whether the callee answered.
- After granting permissions in system settings, return to the app — the status chips refresh on resume.
- See the plugin [README](../README.md) for calibration step ids and API docs.
