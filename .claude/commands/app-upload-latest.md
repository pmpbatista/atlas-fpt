---
description: Build the debug APK, copy it to Google Drive with the version appended, and install on the running emulator.
allowed-tools: Bash(./gradlew*), Bash(grep*), Bash(cp*), Bash(ls*), Bash(/Users/pedrobatista/Library/Android/sdk/platform-tools/adb*)
---

Build the debug APK, copy it to Google Drive with the app's versionName appended to the filename, and install/launch it on the running emulator. Do not ask for confirmation at any step.

The Drive folder keeps one APK per version (filename suffix handles this). If the version was not bumped since the last upload, the existing `app-debug-<version>.apk` in Drive is overwritten — that's intentional and the desired behavior.

## Steps

1. **Read versionName** from `app/build.gradle.kts`:
   ```
   grep -E "versionName" app/build.gradle.kts
   ```
   Extract the quoted value (e.g. `"1.1"`). If not found, stop and report.

2. **Build**:
   ```
   ./gradlew assembleDebug
   ```
   Gradle's `UP-TO-DATE` is fine — it means no source changed since the last build, so the existing APK already reflects current source. If the build fails, stop and surface the error.

3. **Copy to Drive** with the version appended:
   ```
   cp app/build/outputs/apk/debug/app-debug.apk "/Users/pedrobatista/Library/CloudStorage/GoogleDrive-pedromiguelbatista@gmail.com/My Drive/app-debug-<versionName>.apk"
   ```
   Then `ls -lh` the destination to confirm size and timestamp.

4. **Install on emulator** if one is running:
   ```
   /Users/pedrobatista/Library/Android/sdk/platform-tools/adb devices
   ```
   If at least one device is listed as `device` (not `offline` / `unauthorized`), install and launch:
   ```
   /Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   /Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
   ```
   If no emulator/device is connected, skip this step and note it in the final report (don't treat as a failure).

5. **Report** the destination path, file size, versionName, and emulator install status in a single short sentence.
