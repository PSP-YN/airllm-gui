# Code Signing Guide for AirLLM

## Overview

Code signing verifies the authenticity and integrity of distributed applications. This guide covers code signing for desktop (Tauri) and Android applications.

## Desktop App Signing (Tauri)

### Windows Code Signing

#### Prerequisites
1. Obtain a code signing certificate from a Certificate Authority (CA)
   - Recommended: DigiCert, Sectigo, GlobalSign
   - Certificate type: Code Signing Certificate

#### Configuration

1. Install the certificate on your build machine
2. Configure `tauri.conf.json`:

```json
{
  "bundle": {
    "windows": {
      "nsis": {
        "installMode": "currentUser",
        "certificateThumbprint": "YOUR_CERTIFICATE_THUMBPRINT",
        "timestampUrl": "http://timestamp.digicert.com"
      },
      "webviewInstallMode": {
        "type": "embedBootstrapper"
      }
    }
  }
}
```

3. Set environment variables (alternative method):
```bash
set WINDOWS_CERTIFICATE_FILE=path/to/certificate.pfx
set WINDOWS_CERTIFICATE_PASSWORD=your_password
```

#### Building Signed Installer

```bash
cd gui/desktop
npm run tauri build
```

The signed installer will be in:
`src-tauri/target/release/bundle/nsis/`

#### Verification

```powershell
# Verify signature
Get-AuthenticodeSignature .\AirLLM-setup.exe | Format-List *

# Check certificate details
certutil -verify .\AirLLM-setup.exe
```

### macOS Code Signing

#### Prerequisites
1. Apple Developer Account ($99/year)
2. Developer ID Application certificate

#### Configuration

1. Generate certificates in Apple Developer Portal
2. Import certificates to Keychain
3. Configure `tauri.conf.json`:

```json
{
  "bundle": {
    "macOS": {
      "minimumSystemVersion": "10.15",
      "signingIdentity": "Developer ID Application: Your Name (TEAM_ID)",
      "entitlements": null,
      "providerShortName": "TEAM_ID",
      "hardenedRuntime": true
    }
  }
}
```

#### Building Signed App

```bash
cd gui/desktop
npm run tauri build
```

#### Notarization (Required for Distribution)

```bash
# Notarize the app
xcrun notarytool submit src-tauri/target/release/bundle/macos/AirLLM.app.zip --wait --apple-id "your@email.com" --password "app-specific-password" --team-id "TEAM_ID"

# Staple the notarization
xcrun stapler staple src-tauri/target/release/bundle/macos/AirLLM.app
```

#### Verification

```bash
# Verify signature
codesign -dv --verbose=4 src-tauri/target/release/bundle/macos/AirLLM.app

# Verify notarization
xcrun stapler validate src-tauri/target/release/bundle/macos/AirLLM.app
```

### Linux Code Signing

Linux distributions typically use GPG signing for packages.

#### Generate GPG Key

```bash
gpg --full-generate-key
```

#### Sign Package

```bash
# Sign AppImage
gpg --detach-sign --output AirLLM.AppImage.sig AirLLM.AppImage

# Sign deb package
dpkg-sig --sign builder AirLLM_0.1.0_amd64.deb
```

#### Verification

```bash
# Verify signature
gpg --verify AirLLM.AppImage.sig AirLLM.AppImage
```

## Android App Signing

### Generate Keystore

```bash
cd gui/android
keytool -genkey -v -keystore airllm-release.keystore -alias airllm -keyalg RSA -keysize 2048 -validity 10000
```

### Configure Signing in build.gradle.kts

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("airllm-release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "airllm"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
}
```

### Set Environment Variables

```bash
export KEYSTORE_PASSWORD=your_keystore_password
export KEY_PASSWORD=your_key_password
```

### Build Signed APK

```bash
cd gui/android
./gradlew assembleRelease
```

The signed APK will be in:
`app/build/outputs/apk/release/`

### Build Signed AAB (Google Play)

```bash
./gradlew bundleRelease
```

The signed AAB will be in:
`app/build/outputs/bundle/release/`

### Verification

```bash
# Verify APK signature
apksigner verify --print-certs app/build/outputs/apk/release/*.apk

# Verify AAB signature
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/*.aab
```

## CI/CD Integration

### GitHub Actions for Windows Signing

```yaml
- name: Import Certificate
  run: |
    echo $WINDOWS_CERTIFICATE_BASE64 | base64 --decode > certificate.pfx
    certutil -importPFX certificate.pfx $WINDOWS_CERTIFICATE_PASSWORD

- name: Build with Signing
  run: |
    cd gui/desktop
    npm run tauri build
  env:
    WINDOWS_CERTIFICATE_FILE: certificate.pfx
    WINDOWS_CERTIFICATE_PASSWORD: ${{ secrets.WINDOWS_CERTIFICATE_PASSWORD }}
```

### GitHub Actions for Android Signing

```yaml
- name: Decode Keystore
  run: |
    echo $KEYSTORE_BASE64 | base64 --decode > gui/android/airllm-release.keystore

- name: Build Signed APK
  run: |
    cd gui/android
    ./gradlew assembleRelease
  env:
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
```

## Security Best Practices

1. **Never commit certificates** to version control
2. **Use environment variables** for secrets
3. **Rotate certificates** annually
4. **Use strong passwords** for keystore
5. **Backup certificates** securely
6. **Use hardware security modules** (HSM) for production
7. **Monitor certificate expiration**
8. **Use timestamp servers** for Windows signing

## Troubleshooting

### Windows Signing Fails

- Verify certificate is installed
- Check certificate thumbprint matches
- Ensure timestamp server is accessible
- Check certificate validity period

### macOS Notarization Fails

- Verify Apple Developer account
- Check app-specific password
- Ensure correct team ID
- Check network connectivity

### Android Signing Fails

- Verify keystore file exists
- Check password environment variables
- Ensure keystore alias matches
- Check key validity period

## Certificate Sources

### Windows
- DigiCert: https://www.digicert.com/
- Sectigo: https://sectigo.com/
- GlobalSign: https://www.globalsign.com/

### macOS
- Apple Developer: https://developer.apple.com/
- Cost: $99/year

### Android
- Self-signed (free, for testing)
- Google Play Console (for distribution)

## Open Source Distribution

For open-source projects without paid certificates:

1. **Windows**: Distribute unsigned with warning
2. **macOS**: Use ad-hoc signing (limited distribution)
3. **Linux**: Use GPG signing with community trust
4. **Android**: Self-signed keystore (warning on install)

Add disclaimer in README:
```
Note: This application is not code-signed. You may see security warnings.
For production use, consider building from source or obtaining from trusted sources.
```
