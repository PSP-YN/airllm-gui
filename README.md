# AirLLM

**AirLLM** enables running massive large language models (70B+ parameters) on consumer-grade hardware with limited GPU memory (4GB-8GB VRAM) without quantization, distillation, or pruning.

## 🎯 Problem Statement

Running large language models (LLMs) typically requires expensive hardware with substantial GPU memory:
- 70B parameter models usually need 40GB+ VRAM
- 405B parameter models need hundreds of GB of VRAM
- This limits access to researchers, developers, and enthusiasts with consumer hardware

## 💡 Solution

AirLLM solves this problem through **layer-wise inference**:
- Model layers are streamed from disk to GPU one at a time
- Each layer is processed and then freed from GPU memory before loading the next
- This reduces peak GPU memory usage from tens of GB to just 4-8GB
- Optional 4-bit/8-bit compression provides 2-3x speedup with minimal accuracy loss

### Key Capabilities

- **Run 70B models on 4GB GPU** - No quantization required
- **Run 405B Llama3.1 on 8GB VRAM** - Largest models on consumer hardware
- **Support for multiple architectures** - Llama, Qwen, ChatGLM, Baichuan, Mistral, Mixtral, InternLM
- **Cross-platform** - Linux, Windows, macOS (Apple Silicon)
- **Model compression** - 4-bit/8-bit quantization for faster inference

## 📋 Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [CLI Usage](#cli-usage)
- [Python API](#python-api)
- [GUI Applications](#gui-applications)
- [Model Compression](#model-compression)
- [Configuration Options](#configuration-options)
- [Supported Models](#supported-models)
- [MacOS Support](#macos-support)
- [Troubleshooting](#troubleshooting)
- [Acknowledgements](#acknowledgements)

## Installation

### From Source

```bash
# Clone the repository
git clone <your-repo-url>
cd airllm-mymodel

# Install the package
cd air_llm
pip install -e .
```

### Dependencies

```bash
pip install -r requirements.txt
```

Required packages:
- torch
- transformers
- accelerate
- safetensors
- optimum
- huggingface-hub
- tqdm
- scipy
- bitsandbytes (optional, for compression)

## Quick Start

### Python API

```python
from airllm import AutoModel

# Initialize model with HuggingFace repo ID
model = AutoModel.from_pretrained("garage-bAInd/Platypus2-70B-instruct")

# Or use local path
# model = AutoModel.from_pretrained("/path/to/local/model")

# Prepare input
input_text = ['What is the capital of United States?']
input_tokens = model.tokenizer(
    input_text,
    return_tensors="pt", 
    return_attention_mask=False, 
    truncation=True, 
    max_length=128, 
    padding=False
)

# Generate response
generation_output = model.generate(
    input_tokens['input_ids'].cuda(), 
    max_new_tokens=20,
    use_cache=True,
    return_dict_in_generate=True
)

# Decode output
output = model.tokenizer.decode(generation_output.sequences[0])
print(output)
```

**Note:** During first run, the model will be downloaded and split into layer-wise shards. Ensure sufficient disk space in the HuggingFace cache directory.
 

## CLI Usage

### Running the Example Script

```bash
python run_coding_model.py
```

This script demonstrates running DeepSeek-Coder-33B-Instruct with 4-bit compression on a 6GB GPU.

## GUI Applications

AirLLM includes native desktop and Android applications for running LLMs with a graphical interface.

### Desktop Application (Windows & Linux)

Built with **Tauri v2**, Rust, and vanilla HTML/JS.

#### Features
- System analysis - detects CPU, RAM, GPU VRAM, and Python
- One-click install/upgrade of AirLLM Python backend via pip
- Model preloading with real status feedback
- Interactive chat with streaming token output
- Multi-turn conversation history
- Configurable max tokens and compression ratio
- Estimated VRAM savings comparison chart

#### Building from Source

**Prerequisites:** Node.js 20+, Rust (stable), Python 3.8+ with pip

```bash
cd gui/desktop
npm install
npm run tauri dev      # development
npm run tauri build    # production
```

**Output:**
- Linux AppImage: `gui/desktop/src-tauri/target/release/bundle/appimage/`
- Linux deb: `gui/desktop/src-tauri/target/release/bundle/deb/`
- Windows installer: `gui/desktop/src-tauri/target/release/bundle/nsis/`

### Android Application

Built with **Kotlin**, **Jetpack Compose**, and **Google MediaPipe Tasks GenAI**.

#### Features
- On-device offline GGUF inference
- Built-in model downloader (HuggingFace)
- Streaming chat with per-model prompt templates
- Foreground service keeps generation alive in background
- Material 3 dark-mode UI

#### Requirements
- Android 8.0+ (API 26+), ARM64 or x86_64
- 2-6 GB RAM depending on model
- Internet for initial model download

#### Building from Source

**Prerequisites:** JDK 17, Android SDK 34+, NDK r26+

```bash
cd gui/android
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK
```

**Output:** `gui/android/app/build/outputs/apk/`

## Python API

### Basic Usage

```python
from airllm import AutoModel

model = AutoModel.from_pretrained("model-name")
input_tokens = model.tokenizer(["Your prompt here"], return_tensors="pt")
output = model.generate(input_tokens['input_ids'].cuda(), max_new_tokens=100)
print(model.tokenizer.decode(output[0]))
```

### With Compression

```python
model = AutoModel.from_pretrained(
    "model-name",
    compression='4bit'  # or '8bit'
)
```

### Custom Save Path

```python
model = AutoModel.from_pretrained(
    "model-name",
    layer_shards_saving_path="/custom/path"
)
```

## Model Compression

AirLLM supports block-wise quantization for faster inference:

### Benefits
- **2-3x speedup** in inference time
- **Minimal accuracy loss** (weights-only quantization)
- **Smaller disk footprint** for model shards

### How to Enable

1. Install bitsandbytes:
```bash
pip install bitsandbytes
```

2. Use compression parameter:
```python
model = AutoModel.from_pretrained(
    "model-name",
    compression='4bit'  # 4-bit quantization
)
# or
model = AutoModel.from_pretrained(
    "model-name",
    compression='8bit'  # 8-bit quantization
)
```

### Compression vs Traditional Quantization

Traditional quantization quantizes both weights and activations, which can impact accuracy across diverse inputs. AirLLM only quantizes weights since the bottleneck is disk loading, not compute. This maintains better accuracy while still providing significant speedup.

## Configuration Options

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `compression` | str | None | Model compression: '4bit', '8bit', or None |
| `profiling_mode` | bool | False | Enable timing profiling for performance analysis |
| `layer_shards_saving_path` | str | None | Custom path to save layer-wise model shards |
| `prefetching` | bool | True | Overlap model loading with compute (Llama2 only) |
| `delete_original` | bool | False | Delete original model after splitting to save disk space |
| `device` | str | "cuda:0" | Device to run inference on |
| `dtype` | torch.dtype | torch.float16 | Data type for model weights |
| `max_seq_len` | int | 512 | Maximum sequence length |

### Example with All Options

```python
model = AutoModel.from_pretrained(
    "model-name",
    compression='4bit',
    profiling_mode=True,
    layer_shards_saving_path="/custom/path",
    prefetching=True,
    delete_original=True,
    device="cuda:0",
    dtype=torch.float16,
    max_seq_len=1024
)
``` 

## Supported Models

AirLLM supports the following model architectures:

- **Llama/Llama2/Llama3** - Meta's Llama family
- **Qwen/Qwen2** - Alibaba's Qwen models
- **ChatGLM** - Tsinghua's ChatGLM series
- **Baichuan** - Baichuan Inc's models
- **Mistral** - Mistral AI's models
- **Mixtral** - Mixture of Experts models
- **InternLM** - Shanghai AI Laboratory's models

### Example: Different Model Types

```python
# Llama
model = AutoModel.from_pretrained("meta-llama/Llama-2-7b-hf")

# Qwen
model = AutoModel.from_pretrained("Qwen/Qwen-7B")

# ChatGLM
model = AutoModel.from_pretrained("THUDM/chatglm3-6b-base")

# Baichuan
model = AutoModel.from_pretrained("baichuan-inc/Baichuan2-7B-Base")

# Mistral
model = AutoModel.from_pretrained("mistralai/Mistral-7B-Instruct-v0.1")

# Mixtral
model = AutoModel.from_pretrained("mistralai/Mixtral-8x7B-Instruct-v0.1")
```

## MacOS Support

AirLLM supports running on Apple Silicon (M1/M2/M3) Macs using MLX.

### Requirements
- Apple Silicon Mac (M1, M2, or M3)
- MLX framework
- PyTorch
- Python 3.8+

### Installation

```bash
pip install mlx torch
```

### Usage

The API remains the same - AirLLM automatically detects macOS and uses the MLX backend:

```python
from airllm import AutoModel
model = AutoModel.from_pretrained("model-name")
# Works automatically on Apple Silicon
```


## Troubleshooting

### MetadataIncompleteBuffer Error

```
safetensors_rust.SafetensorError: Error while deserializing header: MetadataIncompleteBuffer
```

**Cause:** Insufficient disk space during model splitting.

**Solution:** 
- Free up disk space
- Clear HuggingFace cache: `rm -rf ~/.cache/huggingface/hub`
- Re-run the inference

### ValueError: max() arg is an empty sequence

**Cause:** Loading QWen or ChatGLM with wrong model class.

**Solution:** Use `AutoModel` instead of specific model classes:
```python
from airllm import AutoModel
model = AutoModel.from_pretrained("model-name")
```

### ValueError: Asking to pad but the tokenizer does not have a padding token

**Cause:** Tokenizer doesn't have padding token configured.

**Solution:** Disable padding:
```python
input_tokens = model.tokenizer(
    input_text,
    return_tensors="pt", 
    return_attention_mask=False, 
    truncation=True, 
    max_length=MAX_LENGTH, 
    padding=False  # Disable padding
)
```

### Out of Memory Errors

**Cause:** GPU doesn't have enough memory even with layer-wise loading.

**Solution:**
- Use compression: `compression='4bit'`
- Reduce `max_seq_len`
- Use a smaller model
- Close other GPU-intensive applications

## Performance Tips

1. **Use compression** for 2-3x speedup with minimal accuracy loss
2. **Enable prefetching** (default on) to overlap I/O and compute
3. **Use SSD storage** for faster model loading
4. **Delete original model** with `delete_original=True` to save disk space
5. **Profile mode** helps identify bottlenecks: `profiling_mode=True`

## How It Works

### Layer-Wise Inference Architecture

```
Traditional Approach:
┌─────────────────────────────────────┐
│  Load Entire Model (40GB+)          │
│  ┌───────────────────────────────┐  │
│  │ Layer 1 │ Layer 2 │ ... │ Layer N │  │
│  └───────────────────────────────┘  │
│  Process All Layers                 │
└─────────────────────────────────────┘
Requires: 40GB+ VRAM

AirLLM Approach:
┌─────────────────────────────────────┐
│  Load Layer 1 → Process → Free      │
│  Load Layer 2 → Process → Free      │
│  Load Layer 3 → Process → Free      │
│  ...                                  │
│  Load Layer N → Process → Free      │
└─────────────────────────────────────┘
Requires: 4-8GB VRAM
```

### Memory Optimization Techniques

1. **Layer streaming**: Only one layer in GPU at a time
2. **Activation caching**: Intermediate results stored in RAM
3. **Weight compression**: 4-bit/8-bit quantization reduces memory footprint
4. **Prefetching**: Overlap disk I/O with GPU computation
5. **Memory cleanup**: Aggressive garbage collection between layers

## Acknowledgements

This project builds upon the excellent work by:

- **Gavin Li** - Original creator of AirLLM. The core layer-wise inference logic and Python package were developed by Gavin Li. The original AirLLM repository can be found at: https://github.com/lyogavin/airllm

- **SimJeg** - Innovative approaches to memory-efficient inference demonstrated in the Kaggle LLM Science Exam competition

This repository is a cleaned and documented version of the original AirLLM project, with added GUI applications and comprehensive documentation for easier use.

## License

This project is provided as-is for educational and research purposes.
