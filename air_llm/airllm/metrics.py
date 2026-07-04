"""
Performance metrics collection for AirLLM.
"""
import time
from typing import Dict, Any, Optional, List
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class Metric:
    """Single metric data point."""
    name: str
    value: float
    timestamp: float
    tags: Dict[str, str] = field(default_factory=dict)


class MetricsCollector:
    """Collect and manage performance metrics."""
    
    def __init__(self):
        self.metrics: List[Metric] = []
        self.counters: Dict[str, int] = defaultdict(int)
        self.timers: Dict[str, float] = {}
    
    def increment(self, name: str, value: int = 1, tags: Optional[Dict[str, str]] = None) -> None:
        """
        Increment a counter metric.
        
        Args:
            name: Metric name
            value: Value to increment by
            tags: Optional tags for the metric
        """
        self.counters[name] += value
        self.metrics.append(Metric(
            name=name,
            value=self.counters[name],
            timestamp=time.time(),
            tags=tags or {}
        ))
    
    def timing(self, name: str, duration: float, tags: Optional[Dict[str, str]] = None) -> None:
        """
        Record a timing metric.
        
        Args:
            name: Metric name
            duration: Duration in seconds
            tags: Optional tags for the metric
        """
        self.metrics.append(Metric(
            name=name,
            value=duration,
            timestamp=time.time(),
            tags=tags or {}
        ))
    
    def gauge(self, name: str, value: float, tags: Optional[Dict[str, str]] = None) -> None:
        """
        Record a gauge metric.
        
        Args:
            name: Metric name
            value: Current value
            tags: Optional tags for the metric
        """
        self.metrics.append(Metric(
            name=name,
            value=value,
            timestamp=time.time(),
            tags=tags or {}
        ))
    
    def start_timer(self, name: str) -> None:
        """Start a timer for a given metric name."""
        self.timers[name] = time.time()
    
    def stop_timer(self, name: str, tags: Optional[Dict[str, str]] = None) -> None:
        """
        Stop a timer and record the timing metric.
        
        Args:
            name: Metric name
            tags: Optional tags for the metric
        """
        if name in self.timers:
            duration = time.time() - self.timers[name]
            self.timing(name, duration, tags)
            del self.timers[name]
    
    def get_metrics(self, name: Optional[str] = None, since: Optional[float] = None) -> List[Metric]:
        """
        Get metrics, optionally filtered by name and time.
        
        Args:
            name: Optional metric name filter
            since: Optional timestamp to filter from
            
        Returns:
            List of matching metrics
        """
        filtered = self.metrics
        
        if name:
            filtered = [m for m in filtered if m.name == name]
        
        if since:
            filtered = [m for m in filtered if m.timestamp >= since]
        
        return filtered
    
    def get_summary(self) -> Dict[str, Any]:
        """Get a summary of collected metrics."""
        if not self.metrics:
            return {"message": "No metrics collected"}
        
        summary = {
            "total_metrics": len(self.metrics),
            "metric_names": list(set(m.name for m in self.metrics)),
            "counters": dict(self.counters),
            "time_range": {
                "start": min(m.timestamp for m in self.metrics),
                "end": max(m.timestamp for m in self.metrics),
            }
        }
        
        # Calculate statistics for each metric
        for name in summary["metric_names"]:
            values = [m.value for m in self.metrics if m.name == name]
            summary[f"{name}_stats"] = {
                "count": len(values),
                "min": min(values),
                "max": max(values),
                "avg": sum(values) / len(values),
            }
        
        return summary
    
    def clear(self) -> None:
        """Clear all collected metrics."""
        self.metrics.clear()
        self.counters.clear()
        self.timers.clear()


# Global metrics collector instance
_global_collector = MetricsCollector()


def get_metrics_collector() -> MetricsCollector:
    """Get the global metrics collector instance."""
    return _global_collector


def record_inference_time(model_name: str, duration: float, num_tokens: int) -> None:
    """
    Record inference metrics.
    
    Args:
        model_name: Name of the model
        duration: Inference duration in seconds
        num_tokens: Number of tokens generated
    """
    collector = get_metrics_collector()
    
    collector.timing("inference_duration", duration, tags={"model": model_name})
    collector.gauge("tokens_per_second", num_tokens / duration, tags={"model": model_name})
    collector.increment("total_inferences", tags={"model": model_name})
    collector.increment("total_tokens", num_tokens, tags={"model": model_name})


def record_model_load_time(model_name: str, duration: float) -> None:
    """
    Record model loading metrics.
    
    Args:
        model_name: Name of the model
        duration: Load duration in seconds
    """
    collector = get_metrics_collector()
    
    collector.timing("model_load_duration", duration, tags={"model": model_name})
    collector.increment("total_model_loads", tags={"model": model_name})


def record_memory_usage(model_name: str, memory_gb: float) -> None:
    """
    Record memory usage metrics.
    
    Args:
        model_name: Name of the model
        memory_gb: Memory usage in GB
    """
    collector = get_metrics_collector()
    
    collector.gauge("memory_usage_gb", memory_gb, tags={"model": model_name})
