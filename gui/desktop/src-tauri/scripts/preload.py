#!/usr/bin/env python3
"""Preload an AirLLM model to verify it can be loaded. Reads JSON config from stdin."""

import json
import sys


def emit(obj: dict) -> None:
    print(json.dumps(obj), flush=True)


def main() -> None:
    try:
        config = json.load(sys.stdin)
        
        # Input validation
        if not isinstance(config, dict):
            raise ValueError("Invalid config: expected JSON object")
        
        model_name = config.get("model_name")
        if not model_name or not isinstance(model_name, str):
            raise ValueError("Invalid or missing model_name")
        
        compression_ratio = config.get("compression_ratio")
        if compression_ratio is not None:
            try:
                ratio = int(compression_ratio)
                if ratio not in [4, 8]:
                    raise ValueError("compression_ratio must be 4 or 8")
            except (ValueError, TypeError):
                raise ValueError("compression_ratio must be an integer (4 or 8)")

        from airllm import AutoModel

        model_kwargs: dict = {}
        if compression_ratio is not None:
            ratio = int(compression_ratio)
            if ratio > 1:
                model_kwargs["compression"] = f"{ratio}bit"

        AutoModel.from_pretrained(model_name, **model_kwargs)
        emit({"type": "ready", "model_name": model_name})
    except ImportError as exc:
        emit({"type": "error", "text": f"Import error: {exc}"})
    except ValueError as exc:
        emit({"type": "error", "text": f"Invalid input: {exc}"})
    except RuntimeError as exc:
        emit({"type": "error", "text": f"Runtime error: {exc}"})
    except Exception as exc:
        emit({"type": "error", "text": f"Unexpected error: {exc}"})


if __name__ == "__main__":
    main()
