# Troubleshooting

Common problems and how to resolve them.

1) SDK location not found

Error:
```
SDK location not found. Define ANDROID_HOME or set sdk.dir in local.properties
```

Fix:
```bash
# point local.properties to your SDK
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
```

2) gradle-wrapper.jar missing or invalid

Symptom: `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`

Fix:
```bash
# If you have system gradle installed, regenerate wrapper
gradle wrapper --gradle-version 8.5
# or use a downloaded Gradle to run the wrapper generation
/tmp/gradle-8.5/bin/gradle wrapper --gradle-version 8.5
```

3) NoSuchMethodError on Compose runtime (e.g., KeyframesSpec)

Symptom: Crash referencing `androidx.compose.animation.core.KeyframesSpec` or `CircularProgressIndicator`.

Cause: Mismatched Compose / accompanist / material3 versions.

Fix:
 - Use a compatible Compose BOM and pin accompanist to a version compatible with that BOM.
 - Example used in this project:
   - `androidx.compose:compose-bom:2023.10.01`
   - `com.google.accompanist:accompanist-permissions:0.32.0`

4) App crashes but builds fine

Steps to debug:
```bash
adb logcat -c
adb shell am start -n com.weather.forecast/.MainActivity
adb logcat | grep -i "AndroidRuntime" -A 10
```

5) ADB device not visible

Make sure USB debugging is enabled on the device, accept the debugging prompt, and run:
```bash
adb kill-server
adb start-server
adb devices
```

If you need help resolving a specific error, paste the `adb logcat` crash output into an issue or ask for assistance.
