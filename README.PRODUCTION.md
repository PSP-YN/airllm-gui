# Production Deployment Guide

## Environment Setup

1. Copy environment template:
```bash
cp .env.example .env
```

2. Edit `.env` with your configuration:
```bash
AIRLLM_AUTO_UPGRADE_TRANSFORMERS=false
AIRLLM_CACHE_DIR=~/.cache/huggingface
AIRLLM_LOG_LEVEL=INFO
```

## Python Backend Deployment

### Using Docker (Recommended)

```bash
# Build image
docker build -t airllm:latest .

# Run with GPU support
docker-compose up -d

# Run without GPU (CPU-only)
docker run -it --rm \
  -v $(pwd)/models:/app/models \
  -v $(pwd)/cache:/app/.cache/huggingface \
  airllm:latest
```

### Using pip

```bash
cd air_llm
pip install -e .
```

## Desktop App Deployment

### Build for Production

```bash
cd gui/desktop
npm install
npm run tauri build
```

### Output Locations
- **Linux AppImage**: `src-tauri/target/release/bundle/appimage/`
- **Linux deb**: `src-tauri/target/release/bundle/deb/`
- **Windows installer**: `src-tauri/target/release/bundle/nsis/`

### Security Notes
- CSP is enabled by default in production builds
- All Python scripts validate input
- No auto-upgrade of dependencies unless explicitly enabled

## Android App Deployment

### Build Release APK

```bash
cd gui/android
./gradlew assembleRelease
```

### Output Location
- **Release APK**: `app/build/outputs/apk/release/`

### Signing
Configure signing in `app/build.gradle.kts` before release builds.

## Monitoring

### Logs
- Python: Check `AIRLLM_LOG_LEVEL` in `.env`
- Desktop: Check Tauri console output
- Android: Use `adb logcat | grep airllm`

### Health Checks
- Desktop: System analysis tab shows hardware status
- Android: Model manager shows installed models and disk space

## Security Checklist

- [ ] Environment variables configured
- [ ] CSP enabled in Tauri config
- [ ] Input validation in all scripts
- [ ] No hardcoded secrets
- [ ] Dependencies up to date
- [ ] Firewall rules configured (if running as server)

## Performance Tuning

### Python Backend
- Use `compression='4bit'` for 2-3x speedup
- Enable `prefetching=True` (default)
- Use SSD storage for model files
- Set `delete_original=True` to save disk space

### Desktop App
- Close other GPU-intensive applications
- Use appropriate model size for your VRAM
- Monitor VRAM usage in Device Analysis tab

### Android App
- Use models appropriate for your RAM
- Close other apps during inference
- Enable foreground service for long runs

## Troubleshooting

### Out of Memory
- Reduce `max_tokens`
- Use 4-bit compression
- Use smaller model
- Close other applications

### Model Download Failures
- Check internet connection
- Verify HuggingFace URL
- Ensure sufficient disk space
- Check firewall settings

### Build Failures
- Ensure Node.js 20+ installed
- Ensure Rust stable installed
- Ensure JDK 17+ installed (Android)
- Clear cache: `rm -rf node_modules/`
