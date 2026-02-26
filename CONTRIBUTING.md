# Contributing

Terima kasih atas minat Anda untuk berkontribusi pada Weather Forecast App! Panduan ini membantu menjaga kualitas dan konsistensi codebase.

---

## 📌 Prerequisites

- Android Studio Hedgehog (2023.1.1) atau lebih baru
- JDK 17
- Android SDK 34 + Build Tools 34.0.0
- Gradle 8.5 (via wrapper)
- `gh` CLI (opsional, untuk workflow dispatch)

---

## 🌳 Branching Strategy

```
main ──────────────────────── production-ready, CI wajib hijau
  ├── feature/nama-fitur ──── fitur baru
  ├── fix/nama-fix ────────── bug fix
  ├── ci/nama-perubahan ───── perubahan CI/CD
  └── docs/nama-doc ───────── perubahan dokumentasi
```

### Rules:
- Semua development di branch terpisah dari `main`
- PR wajib lulus CI (`android-ci.yml`) sebelum merge
- Gunakan **squash merge** untuk menjaga history bersih
- Hapus branch setelah merge

---

## 💬 Commit Conventions

Gunakan format [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <description>

[optional body]
```

### Types:

| Type | Deskripsi |
|------|-----------|
| `feat` | Fitur baru |
| `fix` | Bug fix |
| `docs` | Perubahan dokumentasi |
| `style` | Format code (bukan logic) |
| `refactor` | Refactoring tanpa perubahan behavior |
| `test` | Menambah/update test |
| `ci` | Perubahan CI/CD workflow |
| `chore` | Maintenance, dependencies |
| `build` | Perubahan build system |

### Contoh:
```
feat: add hourly precipitation chart
fix: correct temperature unit conversion
ci: cache gradle dependencies in release workflow
docs: update API reference for weather codes
```

---

## 🔄 Development Workflow

### 1. Fork & Clone
```bash
gh repo fork eltfd/weather-forecast-app --clone
cd weather-forecast-app
```

### 2. Create Branch
```bash
git checkout -b feature/your-feature main
```

### 3. Development
```bash
# Build & test locally
./gradlew assembleDebug
./gradlew test

# Lint check
./gradlew lintDebug
```

### 4. Commit & Push
```bash
git add -A
git commit -m "feat: your feature description"
git push origin feature/your-feature
```

### 5. Pull Request
```bash
gh pr create --title "feat: your feature" --body "Description of changes"
```

### 6. CI Check
- PR akan otomatis trigger `android-ci.yml`
- Pastikan build hijau sebelum request review

---

## 🚀 Release Process

Hanya maintainer yang dapat membuat release. Prosesnya:

### 1. Update Version
Di `app/build.gradle.kts`:
```kotlin
versionCode = 2          // increment
versionName = "1.1.0"    // semantic version
```

### 2. Update CHANGELOG.md
Tambahkan section baru di atas `[1.0.0]`:
```markdown
## [1.1.0] - YYYY-MM-DD

### Added
- ...

### Fixed
- ...
```

### 3. Commit & Push
```bash
git add app/build.gradle.kts CHANGELOG.md
git commit -m "chore: bump version to 1.1.0"
git push origin main
```

### 4. Dispatch Release Workflow
```bash
gh workflow run release-sign.yml -f tag=v1.1.0
```

### 5. Monitor & Verify
```bash
gh run list --workflow=release-sign.yml -L 1
gh release view v1.1.0
```

---

## 🔐 Secrets Management

Jika perlu update signing secrets:

| Secret | Cara Update |
|--------|-------------|
| `SIGNING_KEY` | `gh secret set SIGNING_KEY < <(base64 -i keystore.jks)` |
| `RELEASE_KEY_ALIAS` | `gh secret set RELEASE_KEY_ALIAS -b "alias"` |
| `RELEASE_KEY_PASSWORD` | `gh secret set RELEASE_KEY_PASSWORD -b "password"` |
| `RELEASE_STORE_PASSWORD` | `gh secret set RELEASE_STORE_PASSWORD -b "password"` |

> ⚠️ Jangan pernah commit keystore atau password ke repository!

---

## 📝 Code Style

- Kotlin code mengikuti [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Compose functions menggunakan PascalCase
- ViewModel functions menggunakan camelCase
- XML resources menggunakan snake_case
- Indentation: 4 spaces (no tabs)

---

## 📦 Dependency Updates

Saat mengubah library:
1. Test build lokal (`./gradlew assembleDebug`)
2. Test runtime di emulator/device
3. Perhatikan kompatibilitas Compose BOM ↔ Accompanist
4. Update `TROUBLESHOOTING.md` jika ada gotchas
5. Catat di `CHANGELOG.md`

---

## ❓ Butuh Bantuan?

- Buka [issue](https://github.com/eltfd/weather-forecast-app/issues) untuk bug report atau feature request
- Lihat [TROUBLESHOOTING.md](TROUBLESHOOTING.md) untuk solusi masalah umum
- Cek [README.md](README.md) untuk panduan lengkap

Terima kasih — kontribusi sangat dihargai! 🙏
