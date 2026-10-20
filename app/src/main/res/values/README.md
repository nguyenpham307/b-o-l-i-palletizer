# Palletizer Alarm Android (starter project)

Android app using CameraX + ML Kit Text Recognition to read an HMI screen and send detected alert text to Telegram.

## Features
- Bot Token and Chat ID entered in-app and stored using AndroidX EncryptedSharedPreferences.
- Camera OCR using ML Kit.
- Ignores text containing `No Current Alarms`.
- Alert candidate must be present in 3 consecutive OCR frames.
- Suppresses identical alert text for 30 minutes.
- Foreground service notification while monitoring.
- GitHub Actions builds a debug APK artifact.

## Build APK on GitHub (no Mac required)
1. Create/open repository `coiyeuem98-crypto/PalletizerAlarm`.
2. Upload the files in this project to the repository root (preserve folder structure).
3. Open Actions → `Build Palletizer Alarm APK` → Run workflow.
4. Open the completed run and download artifact `PalletizerAlarm-debug`.
5. Extract ZIP and install `app-debug.apk` on Android. Allow installing unknown apps if Android asks.

## Telegram setup
1. In Telegram, talk to `@BotFather`, run `/newbot`, and copy the token.
2. Open your new bot and press Start.
3. Obtain your Chat ID (for a group, add the bot to the group and use the group chat ID).
4. Enter both in the app and tap Save Configuration.
5. Test against a safe sample display before relying on alerts.

## Important limitations / next improvements
- This is a starter implementation, not a safety-rated machine alarm. OCR can miss or misread text; do not use as the sole safety system.
- Android camera foreground service restrictions apply. Start monitoring while the activity is visible and permissions are granted. The OS/OEM may stop services; force-stop stops monitoring. Test on the target phone.
- Keep the camera pointed at the HMI and provide adequate light / focus.
- Current trigger is a broad heuristic (known fault words OR a line of 28+ characters), so tune against real screenshots. Long non-error text can cause false alerts.
- Telegram network failures are logged, not yet retried. Do not put token in source code or GitHub Secrets for this client-side app.
- This project uses Android Gradle Plugin 8.7.3. It may need small compatibility fixes depending on build environment.
