// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::process::Stdio;
use std::sync::{Arc, Mutex};
use tauri::{AppHandle, Emitter, State};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::{Child, Command};

// ─── State ───────────────────────────────────────────────────────────────────

struct InferenceProcess(Arc<Mutex<Option<Child>>>);

// ─── Tauri Commands ──────────────────────────────────────────────────────────

/// Start AirLLM inference. Streams output lines back as "inference-output" events.
#[tauri::command]
async fn run_inference(
    app: AppHandle,
    model_name: String,
    prompt: String,
    max_tokens: u32,
    process_state: State<'_, InferenceProcess>,
) -> Result<(), String> {
    // Kill any existing process
    stop_inference(process_state.clone()).await?;

    // Sanitise inputs before injecting into the Python script
    let safe_model = model_name.replace('\\', "\\\\").replace('"', "\\\"");
    let safe_prompt = prompt.replace('\\', "\\\\").replace('"', "\\\"");

    // Build the inline Python script
    let script = format!(
        r#"
import sys, json
try:
    from airllm import AutoModel
    model = AutoModel.from_pretrained("{model_name}")
    output = model.generate(
        ["{prompt}"],
        max_new_tokens={max_tokens},
        do_sample=True,
        temperature=0.7,
    )
    for token in output:
        text = token if isinstance(token, str) else str(token)
        print(json.dumps({{"type": "token", "text": text}}), flush=True)
    print(json.dumps({{"type": "done"}}), flush=True)
except Exception as e:
    print(json.dumps({{"type": "error", "text": str(e)}}), flush=True)
"#,
        model_name = safe_model,
        prompt = safe_prompt,
        max_tokens = max_tokens
    );

    let python = find_python();
    let mut child = Command::new(&python)
        .args(["-c", &script])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("Failed to start Python: {e}"))?;

    let stdout = child.stdout.take().ok_or("Could not capture stdout")?;

    // Store child handle so we can kill it
    {
        let mut lock = process_state.0.lock().unwrap();
        *lock = Some(child);
    }

    // Stream stdout lines → Tauri events
    let app_clone = app.clone();
    tokio::spawn(async move {
        let reader = BufReader::new(stdout);
        let mut lines = reader.lines();
        while let Ok(Some(line)) = lines.next_line().await {
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(&line) {
                let _ = app_clone.emit("inference-output", json);
            }
        }
        // Ensure "done" is always emitted even if process exits silently
        let _ = app_clone.emit("inference-output", serde_json::json!({"type": "done"}));
    });

    Ok(())
}

/// Stop any running inference process.
#[tauri::command]
async fn stop_inference(process_state: State<'_, InferenceProcess>) -> Result<(), String> {
    // Take the child out of the mutex FIRST, then kill — avoids holding MutexGuard across await
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
        .args(["-m", "pip", "install", "airllm", "--upgrade", "--progress-bar", "off"])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("pip error: {e}"))?;

    let stdout = child.stdout.take().ok_or("no stdout")?;
    let stderr = child.stderr.take().ok_or("no stderr")?;
    let app_clone = app.clone();

    // Stream stdout
    tokio::spawn(async move {
        let reader = BufReader::new(stdout);
        let mut lines = reader.lines();
        while let Ok(Some(line)) = lines.next_line().await {
            let _ = app_clone.emit("install-output", serde_json::json!({"text": line}));
        }
    });

    // Stream stderr (pip writes progress to stderr)
    let app_clone2 = app.clone();
    tokio::spawn(async move {
        let reader = BufReader::new(stderr);
        let mut lines = reader.lines();
        while let Ok(Some(line)) = lines.next_line().await {
            let _ = app_clone2.emit("install-output", serde_json::json!({"text": line}));
        }
    });

    // Wait for pip to finish
    let status = child.wait().await.map_err(|e| format!("pip wait error: {e}"))?;
    if status.success() {
        let _ = app.emit("install-output", serde_json::json!({"type": "done"}));
        Ok(())
    } else {
        Err(format!("pip exited with status {}", status.code().unwrap_or(-1)))
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fn find_python() -> String {
    // On Windows try py launcher first (installed by Python installer)
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
                            return (kb as f64) / 1_048_576.0; // KB → GB
                        }
                    }
                }
            }
        }
    }
    #[cfg(target_os = "windows")]
    {
        // Use PowerShell — wmic is deprecated on Windows 11
        if let Ok(out) = std::process::Command::new("powershell")
            .args(["-NoProfile", "-Command",
                "(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory"])
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
    // Try nvidia-smi (works on Linux and Windows)
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

    // Try rocm-smi for AMD (Linux)
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

    serde_json::json!([{"name": "CPU only (no dedicated GPU detected)", "vram": "N/A", "type": "cpu"}])
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
        // Use PowerShell — wmic is deprecated on Windows 11+
        if let Ok(out) = std::process::Command::new("powershell")
            .args(["-NoProfile", "-Command",
                "(Get-CimInstance Win32_Processor).Name"])
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
    // Python prints version to stderr for old versions, stdout for new
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
            run_inference,
            stop_inference,
            get_system_info,
            check_airllm_installed,
            install_airllm,
        ])
        .run(tauri::generate_context!())
        .expect("error while running AirLLM application");
}
