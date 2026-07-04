#!/usr/bin/env python3
"""Run AirLLM inference with token streaming. Reads JSON config from stdin."""

import json
import sys
import threading


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
        
        prompt = config.get("prompt")
        if not prompt or not isinstance(prompt, str):
            raise ValueError("Invalid or missing prompt")
        
        max_tokens = int(config.get("max_tokens", 512))
        if max_tokens < 1 or max_tokens > 8192:
            raise ValueError("max_tokens must be between 1 and 8192")
        
        compression_ratio = config.get("compression_ratio")

        from airllm import AutoModel

        model_kwargs: dict = {}
        if compression_ratio is not None:
            # AirLLM accepts compression as a string like "4bit" or layer count depending on version
            ratio = int(compression_ratio)
            if ratio > 1:
                model_kwargs["compression"] = f"{ratio}bit"

        model = AutoModel.from_pretrained(model_name, **model_kwargs)

        try:
            from transformers import TextIteratorStreamer

            streamer = TextIteratorStreamer(
                model.tokenizer,
                skip_prompt=True,
                skip_special_tokens=True,
            )

            gen_kwargs = {
                "max_new_tokens": max_tokens,
                "do_sample": True,
                "temperature": 0.7,
                "streamer": streamer,
            }

            thread = threading.Thread(
                target=lambda: model.generate([prompt], **gen_kwargs),
                daemon=True,
            )
            thread.start()

            for text in streamer:
                if text:
                    emit({"type": "token", "text": text})

            thread.join()
        except (ImportError, TypeError, AttributeError):
            # Fallback when streaming is unavailable
            output = model.generate(
                [prompt],
                max_new_tokens=max_tokens,
                do_sample=True,
                temperature=0.7,
            )
            for item in output:
                text = item if isinstance(item, str) else str(item)
                if text:
                    emit({"type": "token", "text": text})

        emit({"type": "done"})
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
