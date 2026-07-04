"""
Run DeepSeek-Coder-33B-Instruct via AirLLM on RTX 3050 6GB
- Streams model layers from disk → GPU one-at-a-time
- Uses 4-bit compression to speed up disk loading
- No full model fits in VRAM needed
"""

from airllm import AutoModel

# ── Config ────────────────────────────────────────────────────────────────────
MODEL_ID = "deepseek-ai/deepseek-coder-33b-instruct"
MAX_INPUT_LENGTH = 256
MAX_NEW_TOKENS = 150
COMPRESSION = "4bit"   # '4bit' | '8bit' | None
# ──────────────────────────────────────────────────────────────────────────────

print(f"\n{'='*60}")
print(f"  Loading: {MODEL_ID}")
print(f"  Compression: {COMPRESSION}")
print(f"  (First run will download & shard the model — can take a while)")
print(f"{'='*60}\n")

model = AutoModel.from_pretrained(
    MODEL_ID,
    compression=COMPRESSION,
)

print("\n✅ Model loaded!\n")

# ── Coding prompt ─────────────────────────────────────────────────────────────
prompt = """You are a helpful coding assistant.

### Instruction:
Write a Python function that takes a list of integers and returns the two numbers that sum closest to zero. Include type hints and a docstring.

### Response:
"""
# ──────────────────────────────────────────────────────────────────────────────

print("📝 Prompt:\n")
print(prompt)
print("-" * 60)
print("🤖 Generating...\n")

input_tokens = model.tokenizer(
    [prompt],
    return_tensors="pt",
    return_attention_mask=False,
    truncation=True,
    max_length=MAX_INPUT_LENGTH,
    padding=False,
)

generation_output = model.generate(
    input_tokens["input_ids"].cuda(),
    max_new_tokens=MAX_NEW_TOKENS,
    use_cache=True,
    return_dict_in_generate=True,
)

output = model.tokenizer.decode(
    generation_output.sequences[0],
    skip_special_tokens=True,
)

print(output)
print("\n" + "="*60 + "\n")
