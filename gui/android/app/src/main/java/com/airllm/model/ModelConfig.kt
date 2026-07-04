package com.airllm.model

/**
 * Defines all supported models with their download URLs and hardware requirements.
 */
enum class ChatTemplate {
    Gemma,
    TinyLlama,
    Phi3,
    Qwen,
}

data class ModelConfig(
    val id: String,
    val displayName: String,
    val description: String,
    val parametersBillion: Float,
    val sizeGB: Float,
    val minRamGB: Int,
    val huggingFaceUrl: String,
    val fileName: String,
    val quantization: String = "Q4_K_M",
    val isRecommended: Boolean = false,
    val chatTemplate: ChatTemplate = ChatTemplate.Gemma,
    val checksum: String? = null, // Optional SHA-256 checksum for verification
)

object ModelRegistry {
    val models = listOf(
        ModelConfig(
            id = "gemma3-1b-q4",
            displayName = "Gemma 3 1B",
            description = "Google's smallest Gemma model. Very fast, runs on almost any Android device.",
            parametersBillion = 1.0f,
            sizeGB = 0.8f,
            minRamGB = 3,
            huggingFaceUrl = "https://huggingface.co/bartowski/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            isRecommended = true,
            chatTemplate = ChatTemplate.Gemma,
        ),
        ModelConfig(
            id = "tinyllama-1b-q4",
            displayName = "TinyLlama 1.1B",
            description = "Ultra-lightweight model. Fastest inference, great for older devices.",
            parametersBillion = 1.1f,
            sizeGB = 0.7f,
            minRamGB = 2,
            huggingFaceUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            chatTemplate = ChatTemplate.TinyLlama,
        ),
        ModelConfig(
            id = "gemma2-2b-q4",
            displayName = "Gemma 2 2B",
            description = "Improved Gemma architecture. Good quality with moderate resource use.",
            parametersBillion = 2.0f,
            sizeGB = 1.5f,
            minRamGB = 4,
            huggingFaceUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            chatTemplate = ChatTemplate.Gemma,
        ),
        ModelConfig(
            id = "phi3-mini-q4",
            displayName = "Phi-3 Mini 3.8B",
            description = "Microsoft's compact model. Excellent reasoning, requires 6 GB RAM.",
            parametersBillion = 3.8f,
            sizeGB = 2.2f,
            minRamGB = 6,
            huggingFaceUrl = "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q4_K_M.gguf",
            fileName = "Phi-3-mini-4k-instruct-Q4_K_M.gguf",
            chatTemplate = ChatTemplate.Phi3,
        ),
        ModelConfig(
            id = "qwen2-1.5b-q4",
            displayName = "Qwen2 1.5B",
            description = "Alibaba's multilingual model. Supports Chinese, English, and more.",
            parametersBillion = 1.5f,
            sizeGB = 1.0f,
            minRamGB = 3,
            huggingFaceUrl = "https://huggingface.co/Qwen/Qwen2-1.5B-Instruct-GGUF/resolve/main/qwen2-1_5b-instruct-q4_k_m.gguf",
            fileName = "qwen2-1_5b-instruct-q4_k_m.gguf",
            chatTemplate = ChatTemplate.Qwen,
        ),
    )

    fun findById(id: String): ModelConfig? = models.find { it.id == id }
}
