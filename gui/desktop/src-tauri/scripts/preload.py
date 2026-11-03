#!/usr/bin/env python3
"""Preload an AirLLM model to verify it can be loaded. Reads JSON config from stdin."""

import json
import sys


def emit(obj: dict) -> None:
    print(json.dumps(obj), flush=True)


def main() -> None:
    try:
        config = json.load(sys.stdin)
        model_name = config["model_name"]
        compression_ratio = config.get("compression_ratio")

        from airllm import AutoModel

        model_kwargs: dict = {}
        if compression_ratio is not None:
            ratio = int(compression_ratio)
            if ratio > 1:
                model_kwargs["compression"] = f"{ratio}bit"

        AutoModel.from_pretrained(model_name, **model_kwargs)
        emit({"type": "ready", "model_name": model_name})
    except Exception as exc:
        emit({"type": "error", "text": str(exc)})


if __name__ == "__main__":
    main()
