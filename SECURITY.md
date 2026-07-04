# Security Policy

## Supported Versions

| Version | Supported Until |
|---------|----------------|
| 2.11.0  | Current         |

## Reporting Vulnerabilities

If you discover a security vulnerability, please report it privately:

1. **Do not** create a public issue
2. Email: gavinli@animaai.cloud
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if known)

## Security Features

### Desktop App (Tauri)
- Content Security Policy (CSP) enabled
- Input validation on all Python scripts
- No auto-upgrade of dependencies
- Sandboxed execution environment

### Python Backend
- No arbitrary code execution
- Input validation on model names and parameters
- Optional compression (bitsandbytes) not required
- Safe model loading via HuggingFace Hub

### Android App
- On-device inference only (no network after download)
- Foreground service for background operations
- Permission checks for storage and notifications
- Model downloads from trusted HuggingFace URLs

## Known Security Considerations

### Model Downloads
- Models are downloaded from HuggingFace (third-party)
- Verify model checksums if possible
- Only download from trusted repositories

### GPU Access
- Application requires GPU access for inference
- Ensure GPU drivers are up to date
- Monitor GPU memory usage

### File Permissions
- Models stored in user directory
- Ensure proper file permissions
- Cache directory may contain large files

## Best Practices

1. **Environment Variables**: Use `.env` file for sensitive configuration
2. **Dependencies**: Keep dependencies updated via `pip install --upgrade`
3. **Network**: Use firewall to restrict outbound connections
4. **Storage**: Monitor disk usage (models can be 10GB+)
5. **GPU**: Monitor VRAM usage to prevent OOM errors

## Dependency Updates

Critical security updates will be released as patch versions:
- Format: `2.11.X`
- Monitor releases for security advisories

## License

This project is provided as-is for educational and research purposes.
