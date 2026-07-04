"""
Health monitoring utilities for AirLLM.
"""
import os
import platform
import shutil
import torch
from typing import Dict, Any, Optional
from pathlib import Path


def get_system_health() -> Dict[str, Any]:
    """
    Get comprehensive system health information.
    
    Returns:
        Dictionary containing health metrics
    """
    health = {
        "status": "healthy",
        "timestamp": __import__("time").time(),
        "system": get_system_info(),
        "gpu": get_gpu_health(),
        "memory": get_memory_info(),
        "disk": get_disk_info(),
    }
    
    # Determine overall health status
    if health["gpu"]["status"] == "error" or health["memory"]["status"] == "error":
        health["status"] = "unhealthy"
    elif health["disk"]["free_percent"] < 10:
        health["status"] = "warning"
    
    return health


def get_system_info() -> Dict[str, str]:
    """Get basic system information."""
    return {
        "platform": platform.system(),
        "platform_release": platform.release(),
        "platform_version": platform.version(),
        "architecture": platform.machine(),
        "processor": platform.processor(),
        "python_version": platform.python_version(),
    }


def get_gpu_health() -> Dict[str, Any]:
    """Get GPU health information."""
    gpu_info = {
        "available": torch.cuda.is_available(),
        "status": "ok",
        "devices": [],
    }
    
    if gpu_info["available"]:
        try:
            device_count = torch.cuda.device_count()
            for i in range(device_count):
                props = torch.cuda.get_device_properties(i)
                memory_total = torch.cuda.get_device_properties(i).total_memory
                memory_allocated = torch.cuda.memory_allocated(i)
                memory_reserved = torch.cuda.memory_reserved(i)
                
                gpu_info["devices"].append({
                    "id": i,
                    "name": props.name,
                    "memory_total_gb": memory_total / 1e9,
                    "memory_allocated_gb": memory_allocated / 1e9,
                    "memory_reserved_gb": memory_reserved / 1e9,
                    "memory_utilization": memory_allocated / memory_total,
                })
        except Exception as e:
            gpu_info["status"] = "error"
            gpu_info["error"] = str(e)
    else:
        gpu_info["status"] = "warning"
        gpu_info["message"] = "CUDA not available - CPU only mode"
    
    return gpu_info


def get_memory_info() -> Dict[str, Any]:
    """Get system memory information."""
    try:
        import psutil
        mem = psutil.virtual_memory()
        
        return {
            "status": "ok",
            "total_gb": mem.total / 1e9,
            "available_gb": mem.available / 1e9,
            "used_gb": mem.used / 1e9,
            "percent": mem.percent,
        }
    except ImportError:
        # Fallback to platform-specific methods
        if platform.system() == "Linux":
            try:
                with open("/proc/meminfo") as f:
                    meminfo = dict((i.split()[0].rstrip(":"), int(i.split()[1]))
                            for i in f.readlines())
                total = meminfo.get("MemTotal", 0) * 1024
                free = meminfo.get("MemFree", 0) * 1024
                available = meminfo.get("MemAvailable", free) * 1024
                
                return {
                    "status": "ok",
                    "total_gb": total / 1e9,
                    "available_gb": available / 1e9,
                    "used_gb": (total - available) / 1e9,
                    "percent": ((total - available) / total) * 100,
                }
            except Exception:
                pass
        
        return {
            "status": "error",
            "message": "Unable to retrieve memory information",
        }


def get_disk_info(path: str = ".") -> Dict[str, Any]:
    """Get disk information for a given path."""
    try:
        disk = shutil.disk_usage(path)
        total = disk.total
        free = disk.free
        used = disk.used
        
        return {
            "status": "ok",
            "path": os.path.abspath(path),
            "total_gb": total / 1e9,
            "free_gb": free / 1e9,
            "used_gb": used / 1e9,
            "free_percent": (free / total) * 100,
            "used_percent": (used / total) * 100,
        }
    except Exception as e:
        return {
            "status": "error",
            "error": str(e),
        }


def check_model_cache_health(cache_dir: Optional[str] = None) -> Dict[str, Any]:
    """
    Check health of model cache directory.
    
    Args:
        cache_dir: Path to cache directory (default: HuggingFace cache)
    
    Returns:
        Dictionary with cache health information
    """
    if cache_dir is None:
        from huggingface_hub import constants
        cache_dir = constants.HF_HUB_CACHE
    
    cache_path = Path(cache_dir)
    
    if not cache_path.exists():
        return {
            "status": "warning",
            "message": "Cache directory does not exist",
            "path": str(cache_dir),
        }
    
    try:
        total_size = sum(f.stat().st_size for f in cache_path.rglob("*") if f.is_file())
        file_count = sum(1 for _ in cache_path.rglob("*") if _.is_file())
        
        return {
            "status": "ok",
            "path": str(cache_dir),
            "total_size_gb": total_size / 1e9,
            "file_count": file_count,
        }
    except Exception as e:
        return {
            "status": "error",
            "error": str(e),
        }


def print_health_report() -> None:
    """Print a formatted health report to console."""
    health = get_system_health()
    
    print("\n" + "="*60)
    print("AIRLLM HEALTH REPORT")
    print("="*60)
    
    print(f"\nStatus: {health['status'].upper()}")
    print(f"Timestamp: {__import__('datetime').datetime.fromtimestamp(health['timestamp'])}")
    
    print("\n--- System ---")
    sys = health['system']
    print(f"Platform: {sys['platform']} {sys['platform_release']}")
    print(f"Architecture: {sys['architecture']}")
    print(f"Python: {sys['python_version']}")
    
    print("\n--- GPU ---")
    gpu = health['gpu']
    print(f"Available: {gpu['available']}")
    if gpu['devices']:
        for device in gpu['devices']:
            print(f"  Device {device['id']}: {device['name']}")
            print(f"    Memory: {device['memory_allocated_gb']:.2f}GB / {device['memory_total_gb']:.2f}GB")
            print(f"    Utilization: {device['memory_utilization']*100:.1f}%")
    
    print("\n--- Memory ---")
    mem = health['memory']
    print(f"Available: {mem['available_gb']:.2f}GB / {mem['total_gb']:.2f}GB")
    print(f"Used: {mem['percent']:.1f}%")
    
    print("\n--- Disk ---")
    disk = health['disk']
    print(f"Free: {disk['free_gb']:.2f}GB / {disk['total_gb']:.2f}GB")
    print(f"Free: {disk['free_percent']:.1f}%")
    
    print("\n" + "="*60 + "\n")


if __name__ == "__main__":
    print_health_report()
