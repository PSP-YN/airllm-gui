# AirLLM Native GUI 🚀

Welcome to the **Native Desktop & Android GUI** for [AirLLM](https://github.com/lyogavin/airllm) (originally created by Gavin Li / @lyogavin).

This repository contains the standalone graphical interfaces that allow anyone to run large language models on consumer hardware without the command line, leveraging AirLLM's powerful layer-wise inference on the desktop, and MediaPipe on Android.

## Updates by PSP-YN ✨

I (PSP-YN) have added native desktop and mobile applications to make AirLLM accessible to everyone without needing complex terminal setups.

### 🖥️ Desktop (Windows & Linux)
Built with Tauri v2, Rust, and WebKitGTK.
- **System Analysis:** Automatically detects CPU, System RAM, and GPU VRAM availability.
- **AirLLM Integration:** Automatically installs/upgrades the AirLLM Python backend if missing.
- **Layer-wise Inference:** Runs models like Llama-3.1 70B on 4GB VRAM!
- **Interactive Chat:** Streaming output with syntax highlighting.
- **Performance Metrics:** Real-time VRAM savings visualization.
- **Easy Installers:** `.exe` NSIS installer for Windows, `.AppImage` / `.deb` / `.rpm` for Linux.

### 📱 Android
Built with Kotlin, Jetpack Compose, and Google MediaPipe Tasks GenAI.
- **On-Device Inference:** Run small GGUF models directly on your phone completely offline.
- **Model Downloader:** Built-in download manager for Gemma, Qwen, Phi-3, and TinyLlama.
- **Background Execution:** Foreground service keeps generation alive even if the screen turns off.
- **Material3 Design:** Modern, sleek, dark-mode native Android experience.

## Downloads ⬇️

Check the [Releases](https://github.com/PSP-YN/airllm-gui/releases) page for the latest builds:
- `AirLLM_x64-setup.exe` (Windows)
- `AirLLM.AppImage` (Linux Portable)
- `airllm-release.apk` (Android)

## Building from Source 🛠️

### Desktop (Linux/Windows)
Ensure Node.js 20+ and Rust are installed.
```bash
cd gui/desktop
npm install
npm run tauri build
```

### Android
Ensure JDK 17 and Android SDK/NDK are installed.
```bash
cd gui/android
./gradlew assembleRelease
```

## Credits & Acknowledgements 🙏
- **AirLLM Core:** All core layer-wise inference logic and the original Python package were created by [Gavin Li (@lyogavin)](https://github.com/lyogavin/airllm). This GUI repository builds upon that incredible work.
- **GUI & Native Apps:** Developed and maintained by PSP-YN.

## License 📜
Apache-2.0 License. See [LICENSE](LICENSE) for details.
