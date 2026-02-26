# Troubleshooting

Panduan solusi masalah umum saat development dan CI/CD.

---

## 🔧 Local Development Issues

### 1. SDK location not found

**Error:**
```
SDK location not found. Define ANDROID_HOME or set sdk.dir in local.properties
```

**Fix:**
```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
```

---

### 2. Gradle wrapper JAR missing or invalid

**Symptom:**
```
Could not find or load main class org.gradle.wrapper.GradleWrapperMain
```

**Cause:** `gradle-wrapper.jar` not committed to repository (excluded by `.gitignore`).

**Fix (local):**
```bash
# If you have system gradle installed:
gradle wrapper --gradle-version 8.5

# Or download Gradle manually:
curl -sL https://services.gradle.org/distributions/gradle-8.5-bin.zip -o /tmp/gradle.zip
unzip /tmp/gradle.zip -d /tmp/
/tmp/gradle-8.5/bin/gradle wrapper --gradle-version 8.5
```

**Fix (CI):** Ensure `gradle/wrapper/gradle-wrapper.jar` is in the repo:
```bash
git add -f gradle/wrapper/gradle-wrapper.jar
git commit -m "chore: add gradle-wrapper.jar to repo"
git push
```

---

### 3. Compose runtime crash — NoSuchMethodError

**Symptom:**
```
java.lang.NoSuchMethodError: 
  androidx.compose.animation.core.KeyframesSpec...
```

**Cause:** Mismatched Compose / Accompanist / Material3 versions.

**Fix:** Pin compatible versions in `app/build.gradle.kts`:
```kotlin
// BOM & Accompanist versions used in this project:
implementation(platform("androidx.compose:compose-bom:2023.10.01"))
implementation("com.google.accompanist:accompanist-permissions:0.32.0")
```

> ⚠️ When upgrading Compose BOM, always check [accompanist compatibility](https://google.github.io/accompanist/#compose-versions).

---

### 4. App crashes but builds fine

**Debug steps:**
```bash
adb logcat -c
adb shell am start -n com.weather.forecast/.MainActivity
adb logcat | grep -i "AndroidRuntime" -A 10
```

---

### 5. ADB device not visible

```bash
adb kill-server
adb start-server
adb devices
```

Pastikan USB debugging aktif di device dan sudah accept debugging prompt.

---

## 🤖 CI/CD Issues

### 6. CI: Keystore file not found for signing config 'release'

**Symptom (runner log):**
```
Keystore file '/home/runner/.../app/app/keystore.jks' not found
```

**Cause:** Relative path in `RELEASE_KEYSTORE_FILE` env var gets resolved relative to the Gradle module directory (`app/`), causing double-path (`app/app/keystore.jks`).

**Fix:** Use absolute workspace path in workflow:
```yaml
env:
  RELEASE_KEYSTORE_FILE: ${{ github.workspace }}/app/keystore.jks
```

---

### 7. CI: "GitHub Releases requires a tag"

**Symptom:**
```
⚠️ GitHub Releases requires a tag
```

**Cause:** `softprops/action-gh-release` needs `tag_name` when triggered via `workflow_dispatch` (no git tag exists automatically).

**Fix:** Pass the tag from dispatch input:
```yaml
- uses: softprops/action-gh-release@v1
  with:
    tag_name: ${{ github.event.inputs.tag }}
    files: app/build/outputs/apk/release/app-release.apk
```

---

### 8. CI: Release action 403 Forbidden

**Symptom:**
```
⚠️ GitHub release failed with status: 403
```

**Cause:** Default `GITHUB_TOKEN` has `contents: read` only. Creating releases requires `contents: write`.

**Fix:** Add permissions at workflow level:
```yaml
permissions:
  contents: write
```

---

### 9. CI: SIGNING_KEY secret is empty

**Symptom:**
```
SIGNING_KEY secret is missing
```

**Fix:** Ensure the base64-encoded keystore is set:
```bash
# Encode
base64 -i release-keystore.jks | tr -d '\n' > keystore_b64.txt

# Set secret
gh secret set SIGNING_KEY < keystore_b64.txt
```

---

## 📋 CI Fix History Reference

Untuk traceability, berikut urutan fix yang dilakukan selama setup awal CI/CD:

| # | Problem | Root Cause | Solution | Commit |
|---|---------|------------|----------|--------|
| 1 | GradleWrapperMain not found | `gradle-wrapper.jar` gitignored | Force-add JAR to repo | `chore: add gradle-wrapper.jar` |
| 2 | Keystore not found (single path) | Decoded to wrong location | Decode to `app/keystore.jks` | `ci: decode keystore into app/` |
| 3 | Keystore not found (double path) | Relative path resolved under `app/` module | Use `${{ github.workspace }}` absolute path | `ci: use absolute keystore path` |
| 4 | "Releases requires a tag" | No `tag_name` in gh-release action | Pass `${{ github.event.inputs.tag }}` | `ci: pass dispatched tag` |
| 5 | Release 403 Forbidden | `GITHUB_TOKEN` read-only | Add `permissions: contents: write` | `ci: grant contents:write` |

---

## ❓ Masih Error?

1. Cek [GitHub Actions logs](https://github.com/eltfd/Cuacaku/actions) untuk CI errors
2. Cek `adb logcat` untuk runtime crashes
3. Buka [issue](https://github.com/eltfd/Cuacaku/issues) dengan log error
4. Lihat [README.md](README.md) untuk setup lengkap
