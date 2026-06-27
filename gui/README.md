# AirLLM — Native Apps

Native desktop and Android apps for [AirLLM](https://github.com/lyogavin/Anima) — run large language models on any GPU using layer-wise inference.

## Downloads

Go to the **[Releases](../../releases)** page and download for your platform:

| Platform | File | Notes |
|---|---|---|
| 🪟 Windows | `AirLLM_x64-setup.exe` | Run the installer |
| 🐧 Linux | `AirLLM.AppImage` | `chmod +x` then run |
| 🤖 Android | `airllm-release.apk` | Enable "Install from unknown sources" |

---

## Desktop App (Windows + Linux)

**Requirements:** Python 3.8+, pip (the app will offer to install AirLLM automatically)

### Run from source

```bash
# Prerequisites: Rust, Node.js 18+
cd gui/desktop
npm install
npm run tauri dev       # dev mode
npm run tauri build     # production build
```

**Output:**
- Linux: `gui/desktop/src-tauri/target/release/bundle/appimage/AirLLM.AppImage`
- Windows: `gui/desktop/src-tauri/target/release/bundle/nsis/AirLLM_x64-setup.exe`

---

## Android App

**Supports:** Android 8.0+ (API 26+), ARM64 or x86_64 devices

**Models (downloaded on first use):**
| Model | Size | Min RAM | Best For |
|---|---|---|---|
| Gemma 3 1B ⭐ | 0.8 GB | 3 GB | Most devices |
| TinyLlama 1.1B | 0.7 GB | 2 GB | Oldest/slowest devices |
| Gemma 2 2B | 1.5 GB | 4 GB | Mid-range devices |
| Phi-3 Mini 3.8B | 2.2 GB | 6 GB | High-end devices |
| Qwen2 1.5B | 1.0 GB | 3 GB | Multilingual use |

### Build from source

```bash
# Prerequisites: Android Studio, SDK 34+, NDK r26+, JDK 17
cd gui/android
./gradlew assembleDebug           # debug APK
./gradlew assembleRelease         # release APK (unsigned)
```

**Output:** `gui/android/app/build/outputs/apk/`

---

## Release a New Version

```bash
# Tag and push — GitHub Actions builds all 3 platforms automatically
git tag v1.0.0
git push --tags
```

Then go to GitHub → Releases → find the draft → publish it.

---

## Architecture

```
gui/
├── desktop/          ← Tauri v2 (Rust + HTML/JS)
│   ├── src/          ← Frontend UI (3 tabs)
│   └── src-tauri/    ← Rust backend (spawns Python AirLLM)
└── android/          ← Kotlin + Jetpack Compose
    └── app/          ← llama.cpp JNI, on-device inference
.github/
└── workflows/
    └── release.yml   ← Auto-build all 3 platforms on git tag
```
