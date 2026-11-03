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
        model_name = config["model_name"]
        prompt = config["prompt"]
        max_tokens = int(config.get("max_tokens", 512))
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
    except Exception as exc:
        emit({"type": "error", "text": str(exc)})


if __name__ == "__main__":
    main()
