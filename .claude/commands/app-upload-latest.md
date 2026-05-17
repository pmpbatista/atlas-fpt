---
description: Freshly build the debug APK and copy it to Google Drive with the version number appended.
allowed-tools: Bash(./gradlew*), Bash(grep*), Bash(cp*), Bash(ls*), Bash(rm*)
---

Build a fresh debug APK and copy it to Google Drive with the app's versionName appended to the filename. Do not ask for confirmation at any step.

## Steps

1. **Read versionName** from `app/build.gradle.kts`:
   ```
   grep -E "versionName" app/build.gradle.kts
   ```
   Extract the quoted value (e.g. `"1.1"`). If not found, stop and report.

2. **Force a fresh build** — delete the existing APK so Gradle doesn't short-circuit as `UP-TO-DATE`:
   ```
   rm -f app/build/outputs/apk/debug/app-debug.apk
   ./gradlew assembleDebug
   ```
   If the build fails, stop and surface the error.

3. **Copy to Drive** with the version appended:
   ```
   cp app/build/outputs/apk/debug/app-debug.apk "/Users/pedrobatista/Library/CloudStorage/GoogleDrive-pedromiguelbatista@gmail.com/My Drive/app-debug-<versionName>.apk"
   ```
   Then `ls -lh` the destination to confirm size and timestamp.

4. **Report** the destination path, file size, and versionName in a single short sentence.
