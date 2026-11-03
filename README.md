# AirLLM Native GUI

Native desktop and Android applications for running large language models on consumer hardware — no cloud required.

- **Desktop (Windows & Linux):** Layer-wise inference via the [AirLLM](https://pypi.org/project/airllm/) Python package, wrapped in a Tauri v2 native app.
- **Android:** On-device GGUF inference via Google MediaPipe Tasks GenAI.

Maintained by [PSP-YN](https://github.com/PSP-YN). Download builds from [Releases](https://github.com/PSP-YN/airllm-gui/releases).

---

## Downloads

| Platform | File | Notes |
|----------|------|-------|
| Windows | `AirLLM_x64-setup.exe` | NSIS installer, no admin required |
| Linux | `AirLLM.AppImage` | Portable — `chmod +x` then run |
| Linux | `AirLLM_amd64.deb` | Debian/Ubuntu package |
| Android | `airllm-release.apk` | Sideload (enable Install from unknown sources) |

---

## Desktop App (Windows + Linux)

Built with **Tauri v2**, Rust, and a vanilla HTML/JS frontend.

### Features

- System analysis — detects CPU, RAM, GPU VRAM, and Python
- One-click install/upgrade of the AirLLM Python backend via pip
- Model preloading with real status feedback
- Interactive chat with streaming token output
- Multi-turn conversation history
- Configurable max tokens and compression ratio
- Estimated VRAM savings comparison chart
- Installers: `.exe` (NSIS), `.AppImage`, `.deb`, `.rpm`

### First Run

The app checks for Python 3.8+ and the `airllm` pip package on startup. If missing, click **Install AirLLM** on the Device Analysis tab.

### Build from Source

**Prerequisites:** Node.js 20+, Rust (stable), Python 3.8+ with pip

```bash
cd gui/desktop
npm install
npm run tauri dev      # development
npm run tauri build    # production
```

**Output:**
- Linux AppImage: `gui/desktop/src-tauri/target/release/bundle/appimage/`
- Linux deb: `gui/desktop/src-tauri/target/release/bundle/deb/`
- Windows installer: `gui/desktop/src-tauri/target/release/bundle/nsis/`

---

## Android App

Built with **Kotlin**, **Jetpack Compose**, and **Google MediaPipe Tasks GenAI**.

### Features

- On-device offline GGUF inference
- Built-in model downloader (HuggingFace)
- Streaming chat with per-model prompt templates
- Foreground service keeps generation alive in background
- Material 3 dark-mode UI

### Requirements

- Android 8.0+ (API 26+), ARM64 or x86_64
- 2–6 GB RAM depending on model
- Internet for initial model download

### Supported Models

| Model | Size | Min RAM | Best For |
|-------|------|---------|----------|
| Gemma 3 1B (recommended) | 0.8 GB | 3 GB | Most devices |
| TinyLlama 1.1B | 0.7 GB | 2 GB | Oldest/slowest devices |
| Gemma 2 2B | 1.5 GB | 4 GB | Mid-range devices |
| Phi-3 Mini 3.8B | 2.2 GB | 6 GB | High-end devices |
| Qwen2 1.5B | 1.0 GB | 3 GB | Multilingual use |

### Build from Source

**Prerequisites:** JDK 17, Android SDK 34+, NDK r26+

```bash
cd gui/android
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK
```

**Output:** `gui/android/app/build/outputs/apk/`

---

## Release Workflow

Tag a version to trigger GitHub Actions builds for all three platforms:

```bash
git tag v1.0.0
git push --tags
```

Then go to GitHub → Releases → find the draft → publish it.

CI builds Windows NSIS installer, Linux AppImage/deb/rpm, and signed Android APK automatically.

---

## Architecture

```
gui/
├── desktop/              Tauri v2 (Rust + HTML/JS)
│   ├── src/              Frontend UI (3 tabs)
│   ├── src-tauri/        Rust backend + Python scripts
│   │   └── scripts/      inference.py, preload.py
│   └── package.json
└── android/              Kotlin + Jetpack Compose
    └── app/              MediaPipe GenAI on-device inference
.github/
└── workflows/
    └── release.yml       Auto-build on git tag
```

**Desktop flow:** UI → Tauri commands → Python subprocess (`airllm` pip package) → streamed JSON tokens → UI

**Android flow:** UI → MediaPipe `LlmInference` → GGUF files downloaded from HuggingFace

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

---

## Acknowledgements

**Gavin Li ([@lyogavin](https://github.com/lyogavin))** — Original creator of AirLLM. All core layer-wise inference logic and the Python package ([lyogavin/airllm](https://github.com/lyogavin/airllm)) that powers the desktop app were developed by Gavin Li. This GUI repository builds upon that work.

**PSP-YN** — Native desktop (Tauri) and Android (Compose + MediaPipe) GUI applications, CI release pipeline, and ongoing maintenance.
