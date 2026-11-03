// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::path::PathBuf;
use std::process::Stdio;
use std::sync::{Arc, Mutex};
use tauri::{AppHandle, Emitter, State};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::process::{Child, Command};

// ─── State ───────────────────────────────────────────────────────────────────

struct InferenceProcess(Arc<Mutex<Option<Child>>>);

// ─── Tauri Commands ──────────────────────────────────────────────────────────

/// Preload a model via the bundled Python script to verify it can be loaded.
#[tauri::command]
async fn preload_model(model_name: String, compression_ratio: u32) -> Result<(), String> {
    let script = script_path("preload.py");
    let python = find_python();

    let config = serde_json::json!({
        "model_name": model_name,
        "compression_ratio": compression_ratio,
    });

    let mut child = Command::new(&python)
        .arg(&script)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("Failed to start Python: {e}"))?;

    if let Some(mut stdin) = child.stdin.take() {
        let payload = serde_json::to_string(&config).map_err(|e| e.to_string())?;
        stdin
            .write_all(payload.as_bytes())
            .await
            .map_err(|e| format!("Failed to send config: {e}"))?;
    }

    let stdout = child.stdout.take().ok_or("Could not capture stdout")?;
    let mut reader = BufReader::new(stdout);
    let mut line = String::new();

    reader
        .read_line(&mut line)
        .await
        .map_err(|e| format!("Failed to read preload output: {e}"))?;

    let status = child
        .wait()
        .await
        .map_err(|e| format!("Preload process error: {e}"))?;

    if let Ok(json) = serde_json::from_str::<serde_json::Value>(&line) {
        if json.get("type").and_then(|v| v.as_str()) == Some("ready") {
            return Ok(());
        }
        if let Some(text) = json.get("text").and_then(|v| v.as_str()) {
            return Err(text.to_string());
        }
    }

    if !status.success() {
        return Err(format!("Preload failed with status {}", status.code().unwrap_or(-1)));
    }

    Err("Unknown preload error".to_string())
}

/// Start AirLLM inference. Streams output lines back as "inference-output" events.
#[tauri::command]
async fn run_inference(
    app: AppHandle,
    model_name: String,
    prompt: String,
    max_tokens: u32,
    compression_ratio: u32,
    process_state: State<'_, InferenceProcess>,
) -> Result<(), String> {
    stop_inference(process_state.clone()).await?;

    let script = script_path("inference.py");
    let python = find_python();

    let config = serde_json::json!({
        "model_name": model_name,
        "prompt": prompt,
        "max_tokens": max_tokens,
        "compression_ratio": compression_ratio,
    });

    let mut child = Command::new(&python)
        .arg(&script)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("Failed to start Python: {e}"))?;

    if let Some(mut stdin) = child.stdin.take() {
        let payload = serde_json::to_string(&config).map_err(|e| e.to_string())?;
        stdin
            .write_all(payload.as_bytes())
            .await
            .map_err(|e| format!("Failed to send config: {e}"))?;
    }

    let stdout = child.stdout.take().ok_or("Could not capture stdout")?;

    {
        let mut lock = process_state.0.lock().unwrap();
        *lock = Some(child);
    }

    let app_clone = app.clone();
    tokio::spawn(async move {
        let reader = BufReader::new(stdout);
        let mut lines = reader.lines();
        while let Ok(Some(line)) = lines.next_line().await {
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(&line) {
                let _ = app_clone.emit("inference-output", json);
            }
        }
        let _ = app_clone.emit("inference-output", serde_json::json!({"type": "done"}));
    });

    Ok(())
}

/// Stop any running inference process.
#[tauri::command]
async fn stop_inference(process_state: State<'_, InferenceProcess>) -> Result<(), String> {
    let child_opt = {
        let mut lock = process_state.0.lock().unwrap();
        lock.take()
    };
    if let Some(mut child) = child_opt {
        let _ = child.kill().await;
    }
    Ok(())
}

/// Gather system info: CPU, total RAM, GPU.
#[tauri::command]
fn get_system_info() -> serde_json::Value {
    let ram_gb = get_total_ram_gb();
    let gpu_info = get_gpu_info();
    let cpu_info = get_cpu_info();
    let python_info = get_python_version();

    serde_json::json!({
        "ram_gb": ram_gb,
        "gpu": gpu_info,
        "cpu": cpu_info,
        "python": python_info,
    })
}

/// Check if airllm Python package is installed.
#[tauri::command]
async fn check_airllm_installed() -> bool {
    let python = find_python();
    Command::new(&python)
        .args(["-c", "import airllm; print(airllm.__version__)"])
        .output()
        .await
        .map(|o| o.status.success())
        .unwrap_or(false)
}

/// Install airllm via pip, streaming pip output to the frontend.
#[tauri::command]
async fn install_airllm(app: AppHandle) -> Result<(), String> {
    let python = find_python();
    let mut child = Command::new(&python)
        .args([
            "-m",
            "pip",
            "install",
            "airllm",
            "--upgrade",
            "--progress-bar",
            "off",
        ])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("pip error: {e}"))?;

    let stdout = child.stdout.take().ok_or("no stdout")?;
    let stderr = child.stderr.take().ok_or("no stderr")?;
    let app_clone = app.clone();

    tokio::spawn(async move {
        let reader = BufReader::new(stdout);
        let mut lines = reader.lines();
        while let Ok(Some(line)) = lines.next_line().await {
            let _ = app_clone.emit("install-output", serde_json::json!({"text": line}));
        }
    });

    let app_clone2 = app.clone();
    tokio::spawn(async move {
        let reader = BufReader::new(stderr);
        let mut lines = reader.lines();
        while let Ok(Some(line)) = lines.next_line().await {
            let _ = app_clone2.emit("install-output", serde_json::json!({"text": line}));
        }
    });

    let status = child
        .wait()
        .await
        .map_err(|e| format!("pip wait error: {e}"))?;
    if status.success() {
        let _ = app.emit("install-output", serde_json::json!({"type": "done"}));
        Ok(())
    } else {
        Err(format!(
            "pip exited with status {}",
            status.code().unwrap_or(-1)
        ))
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fn script_path(name: &str) -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("scripts").join(name)
}

fn find_python() -> String {
    #[cfg(target_os = "windows")]
    let candidates = &["py", "python", "python3"];
    #[cfg(not(target_os = "windows"))]
    let candidates = &["python3", "python", "python3.12", "python3.11", "python3.10"];

    for candidate in candidates.iter() {
        if std::process::Command::new(candidate)
            .arg("--version")
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false)
        {
            return candidate.to_string();
        }
    }
    "python3".to_string()
}

fn get_total_ram_gb() -> f64 {
    #[cfg(target_os = "linux")]
    {
        if let Ok(content) = std::fs::read_to_string("/proc/meminfo") {
            for line in content.lines() {
                if line.starts_with("MemTotal:") {
                    if let Some(kb_str) = line.split_whitespace().nth(1) {
                        if let Ok(kb) = kb_str.parse::<u64>() {
                            return (kb as f64) / 1_048_576.0;
                        }
                    }
                }
            }
        }
    }
    #[cfg(target_os = "windows")]
    {
        if let Ok(out) = std::process::Command::new("powershell")
            .args([
                "-NoProfile",
                "-Command",
                "(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory",
            ])
            .output()
        {
            let text = String::from_utf8_lossy(&out.stdout);
            if let Ok(bytes) = text.trim().parse::<u64>() {
                return (bytes as f64) / 1_073_741_824.0;
            }
        }
    }
    #[cfg(target_os = "macos")]
    {
        if let Ok(out) = std::process::Command::new("sysctl")
            .args(["-n", "hw.memsize"])
            .output()
        {
            let text = String::from_utf8_lossy(&out.stdout);
            if let Ok(bytes) = text.trim().parse::<u64>() {
                return (bytes as f64) / 1_073_741_824.0;
            }
        }
    }
    0.0
}

fn get_gpu_info() -> serde_json::Value {
    if let Ok(out) = std::process::Command::new("nvidia-smi")
        .args(["--query-gpu=name,memory.total", "--format=csv,noheader"])
        .output()
    {
        if out.status.success() {
            let text = String::from_utf8_lossy(&out.stdout);
            let gpus: Vec<serde_json::Value> = text
                .lines()
                .filter(|l| !l.trim().is_empty())
                .map(|line| {
                    let parts: Vec<&str> = line.splitn(2, ',').collect();
                    serde_json::json!({
                        "name": parts.first().map(|s| s.trim()).unwrap_or("Unknown"),
                        "vram": parts.get(1).map(|s| s.trim()).unwrap_or("Unknown"),
                        "type": "nvidia"
                    })
                })
                .collect();
            if !gpus.is_empty() {
                return serde_json::json!(gpus);
            }
        }
    }

    #[cfg(target_os = "linux")]
    if let Ok(out) = std::process::Command::new("rocm-smi")
        .args(["--showproductname"])
        .output()
    {
        if out.status.success() {
            let text = String::from_utf8_lossy(&out.stdout);
            return serde_json::json!([{
                "name": text.lines()
                    .find(|l| l.contains("GPU"))
                    .unwrap_or("AMD GPU")
                    .trim(),
                "vram": "Unknown",
                "type": "amd"
            }]);
        }
    }

    serde_json::json!([{
        "name": "CPU only (no dedicated GPU detected)",
        "vram": "N/A",
        "type": "cpu"
    }])
}

fn get_cpu_info() -> String {
    #[cfg(target_os = "linux")]
    {
        if let Ok(content) = std::fs::read_to_string("/proc/cpuinfo") {
            for line in content.lines() {
                if line.starts_with("model name") {
                    if let Some(name) = line.split(':').nth(1) {
                        return name.trim().to_string();
                    }
                }
            }
        }
    }
    #[cfg(target_os = "windows")]
    {
        if let Ok(out) = std::process::Command::new("powershell")
            .args([
                "-NoProfile",
                "-Command",
                "(Get-CimInstance Win32_Processor).Name",
            ])
            .output()
        {
            let text = String::from_utf8_lossy(&out.stdout);
            let name = text.trim();
            if !name.is_empty() {
                return name.to_string();
            }
        }
    }
    #[cfg(target_os = "macos")]
    {
        if let Ok(out) = std::process::Command::new("sysctl")
            .args(["-n", "machdep.cpu.brand_string"])
            .output()
        {
            return String::from_utf8_lossy(&out.stdout).trim().to_string();
        }
    }
    "Unknown CPU".to_string()
}

fn get_python_version() -> String {
    let python = find_python();
    std::process::Command::new(&python)
        .arg("--version")
        .output()
        .map(|o| {
            let out = String::from_utf8_lossy(&o.stdout).trim().to_string();
            let err = String::from_utf8_lossy(&o.stderr).trim().to_string();
            if out.is_empty() { err } else { out }
        })
        .unwrap_or_else(|_| "Python not found — install from python.org".to_string())
}

// ─── Entry Point ─────────────────────────────────────────────────────────────

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .manage(InferenceProcess(Arc::new(Mutex::new(None))))
        .invoke_handler(tauri::generate_handler![
            preload_model,
            run_inference,
            stop_inference,
            get_system_info,
            check_airllm_installed,
            install_airllm,
        ])
        .run(tauri::generate_context!())
        .expect("error while running AirLLM application");
}
