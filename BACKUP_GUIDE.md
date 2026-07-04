# Backup Guide for AirLLM

## Overview

AirLLM stores large model files and cache data that should be backed up regularly. This guide provides procedures for backing up and restoring AirLLM data.

## What to Backup

### Essential
- **Model Cache**: `~/.cache/huggingface/` (contains downloaded models)
- **Custom Models**: Any models downloaded outside the cache
- **Configuration**: `.env` file (if customized)
- **Chat History**: If your application stores chat history

### Optional
- **Split Model Shards**: `splitted_model/` directories (can be regenerated)
- **Logs**: Application logs (if enabled)

## Backup Procedures

### Linux/macOS

#### Automated Backup Script

```bash
#!/bin/bash
# backup_airllm.sh

BACKUP_DIR="/path/to/backup/airllm"
CACHE_DIR="$HOME/.cache/huggingface"
DATE=$(date +%Y%m%d_%H%M%S)

# Create backup directory
mkdir -p "$BACKUP_DIR"

# Backup HuggingFace cache
echo "Backing up HuggingFace cache..."
tar -czf "$BACKUP_DIR/huggingface_cache_$DATE.tar.gz" -C "$HOME" .cache/huggingface

# Backup environment file (if exists)
if [ -f ".env" ]; then
    echo "Backing up .env file..."
    cp .env "$BACKUP_DIR/.env_$DATE"
fi

# Keep last 7 days of backups
find "$BACKUP_DIR" -name "*.tar.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_DIR"
```

#### Manual Backup

```bash
# Backup HuggingFace cache
tar -czf airllm_backup_$(date +%Y%m%d).tar.gz ~/.cache/huggingface

# Backup to external drive
tar -czf /external/drive/airllm_backup.tar.gz ~/.cache/huggingface
```

### Windows

#### PowerShell Backup Script

```powershell
# backup_airllm.ps1

$backupDir = "C:\Backups\AirLLM"
$cacheDir = "$env:USERPROFILE\.cache\huggingface"
$date = Get-Date -Format "yyyyMMdd_HHmmss"

# Create backup directory
New-Item -ItemType Directory -Force -Path $backupDir

# Backup HuggingFace cache
Write-Host "Backing up HuggingFace cache..."
Compress-Archive -Path $cacheDir -DestinationPath "$backupDir\huggingface_cache_$date.zip"

# Keep last 7 days of backups
Get-ChildItem $backupDir -Filter "*.zip" | Where-Object {
    $_.LastWriteTime -lt (Get-Date).AddDays(-7)
} | Remove-Item

Write-Host "Backup completed: $backupDir"
```

### Android

Android models are stored in the app's private directory. Use ADB to backup:

```bash
# Backup app data
adb backup -f airllm_backup.ab com.airllm

# Restore app data
adb restore airllm_backup.ab
```

## Restore Procedures

### Linux/macOS

```bash
# Restore HuggingFace cache
tar -xzf airllm_backup_YYYYMMDD.tar.gz -C $HOME

# Restore environment file
cp .env_YYYYMMDD .env
```

### Windows

```powershell
# Restore HuggingFace cache
Expand-Archive -Path huggingface_cache_YYYYMMDD.zip -DestinationPath $env:USERPROFILE
```

### Android

```bash
# Restore app data
adb restore airllm_backup.ab
```

## Cloud Backup Options

### Using rclone (Recommended)

```bash
# Install rclone
# Configure cloud storage (Google Drive, Dropbox, etc.)

# Backup to cloud
rclone copy ~/.cache/huggingface remote:airllm-backup/huggingface

# Restore from cloud
rclone copy remote:airllm-backup/huggingface ~/.cache/huggingface
```

### Using rsync

```bash
# Backup to remote server
rsync -avz ~/.cache/huggingface user@remote:/backups/airllm/

# Restore from remote server
rsync -avz user@remote:/backups/airllm/ ~/.cache/huggingface
```

## Automated Scheduling

### Linux (cron)

```bash
# Edit crontab
crontab -e

# Add daily backup at 2 AM
0 2 * * * /path/to/backup_airllm.sh >> /var/log/airllm_backup.log 2>&1
```

### Windows (Task Scheduler)

1. Open Task Scheduler
2. Create Basic Task
3. Set trigger to Daily
4. Set action to Start a Program
5. Program: `powershell.exe`
6. Arguments: `-ExecutionPolicy Bypass -File "C:\path\to\backup_airllm.ps1"`

## Verification

After backup, verify integrity:

```bash
# Check backup file size
ls -lh airllm_backup_YYYYMMDD.tar.gz

# Test restore (dry run)
tar -tzf airllm_backup_YYYYMMDD.tar.gz | head -20
```

## Storage Requirements

- **Small models (1-2B)**: ~1-2 GB per model
- **Medium models (7B)**: ~4-8 GB per model
- **Large models (70B)**: ~40-140 GB per model
- **Cache overhead**: ~20% additional space

Plan backup storage accordingly.

## Best Practices

1. **3-2-1 Rule**: Keep 3 copies, 2 different media, 1 offsite
2. **Regular Backups**: Daily for active use, weekly for occasional use
3. **Test Restores**: Periodically test restore procedures
4. **Version Control**: Keep multiple backup versions
5. **Encryption**: Encrypt backups if storing sensitive data
6. **Monitoring**: Monitor backup success/failure
7. **Documentation**: Document backup locations and procedures

## Troubleshooting

### Backup Fails

- Check disk space on backup destination
- Verify source directory exists
- Check file permissions
- Review backup logs

### Restore Fails

- Verify backup file integrity
- Check destination disk space
- Ensure correct file paths
- Verify file permissions

### Large File Handling

For very large models (>50GB), consider:
- Splitting backups into chunks
- Using incremental backups
- Compressing with different algorithms
- Using specialized backup tools (borg, restic)
