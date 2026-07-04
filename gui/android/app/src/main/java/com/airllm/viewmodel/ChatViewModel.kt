package com.airllm.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airllm.model.ChatTemplate
import com.airllm.service.InferenceService
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Chat state ───────────────────────────────────────────────────────────────

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false,
) {
    enum class Role { User, Assistant }
}

sealed class ModelState {
    object None : ModelState()
    data class Loading(val modelName: String) : ModelState()
    data class Ready(val modelName: String, val modelPath: String, val chatTemplate: ChatTemplate) : ModelState()
    data class Error(val message: String) : ModelState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private var llmInference: LlmInference? = null

    // ID of the currently streaming message (so the listener knows which bubble to update)
    @Volatile private var activeAssistantId: Long = -1

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _modelState = MutableStateFlow<ModelState>(ModelState.None)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _maxTokens = MutableStateFlow(512)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    // ── Model loading ──────────────────────────────────────────────────────────

    fun loadModel(modelPath: String, modelName: String, chatTemplate: ChatTemplate = ChatTemplate.Gemma) {
        viewModelScope.launch(Dispatchers.IO) {
            _modelState.value = ModelState.Loading(modelName)

            // Close any existing model
            runCatching { llmInference?.close() }
            llmInference = null

            try {
                val ctx = getApplication<Application>()

                // Build options — the result listener is registered here for streaming
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(_maxTokens.value)
                    .setTopK(40)
                    .setTemperature(_temperature.value)
                    .setResultListener { partialResult: String?, done: Boolean ->
                        val id = activeAssistantId
                        if (id == -1L) return@setResultListener

                        viewModelScope.launch(Dispatchers.Main) {
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == id) {
                                    msg.copy(
                                        content = msg.content + (partialResult ?: ""),
                                        isStreaming = !done,
                                    )
                                } else msg
                            }
                            if (done) {
                                activeAssistantId = -1L
                                _isGenerating.value = false
                                ctx.stopService(Intent(ctx, InferenceService::class.java))
                            }
                        }
                    }
                    .build()

                llmInference = LlmInference.createFromOptions(ctx, options)
                _modelState.value = ModelState.Ready(modelName, modelPath, chatTemplate)
            } catch (e: Exception) {
                _modelState.value = ModelState.Error("Failed to load model: ${e.message}")
            }
        }
    }

    fun unloadModel() {
        stopGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { llmInference?.close() }
            llmInference = null
            _modelState.value = ModelState.None
        }
    }

    // ── Inference ──────────────────────────────────────────────────────────────

    fun sendMessage(userText: String) {
        if (_isGenerating.value) return
        val state = _modelState.value
        if (state !is ModelState.Ready) return
        val inference = llmInference ?: return

        // Append user message
        val userMsg = ChatMessage(role = ChatMessage.Role.User, content = userText)
        _messages.value = _messages.value + userMsg

        // Append empty streaming placeholder for assistant
        val assistantId = System.currentTimeMillis() + 1L
        _messages.value = _messages.value + ChatMessage(
            id = assistantId,
            role = ChatMessage.Role.Assistant,
            content = "",
            isStreaming = true,
        )
        activeAssistantId = assistantId
        _isGenerating.value = true

        // Start foreground service so Android won't kill us during inference
        val ctx = getApplication<Application>()
        val svcIntent = Intent(ctx, InferenceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(svcIntent)
        } else {
            ctx.startService(svcIntent)
        }

        // Dispatch inference on IO thread — result listener fires on its own thread
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(userText)
                // generateResponseAsync fires result listener for each token
                inference.generateResponseAsync(prompt)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantId) {
                            msg.copy(content = "[Error: ${e.message}]", isStreaming = false)
                        } else msg
                    }
                    activeAssistantId = -1L
                    _isGenerating.value = false
                    ctx.stopService(Intent(ctx, InferenceService::class.java))
                }
            }
        }
    }

    fun stopGeneration() {
        activeAssistantId = -1L
        _isGenerating.value = false
        _messages.value = _messages.value.map { it.copy(isStreaming = false) }
        runCatching {
            getApplication<Application>().stopService(
                Intent(getApplication(), InferenceService::class.java)
            )
        }
    }

    fun clearChat() {
        stopGeneration()
        _messages.value = emptyList()
    }

    fun setMaxTokens(value: Int) { _maxTokens.value = value }
    fun setTemperature(value: Float) { _temperature.value = value }

    // ── Prompt formatting (Gemma instruct template) ───────────────────────────

    private fun buildPrompt(userMessage: String): String {
        val state = _modelState.value
        val template = if (state is ModelState.Ready) state.chatTemplate else ChatTemplate.Gemma

        val history = _messages.value
            .dropLast(1)
            .takeLast(10)

        return when (template) {
            ChatTemplate.Gemma -> buildGemmaPrompt(history, userMessage)
            ChatTemplate.TinyLlama -> buildTinyLlamaPrompt(history, userMessage)
            ChatTemplate.Phi3 -> buildPhi3Prompt(history, userMessage)
            ChatTemplate.Qwen -> buildQwenPrompt(history, userMessage)
        }
    }

    private fun buildGemmaPrompt(history: List<ChatMessage>, userMessage: String): String {
        val sb = StringBuilder()
        sb.append("<start_of_turn>user\n")
        sb.append("You are AirLLM, a helpful AI assistant running entirely on-device. No data leaves your device.\n")
        sb.append("<end_of_turn>\n")
        for (msg in history) {
            when (msg.role) {
                ChatMessage.Role.User ->
                    sb.append("<start_of_turn>user\n${msg.content}<end_of_turn>\n")
                ChatMessage.Role.Assistant ->
                    sb.append("<start_of_turn>model\n${msg.content}<end_of_turn>\n")
            }
        }
        sb.append("<start_of_turn>user\n$userMessage<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun buildTinyLlamaPrompt(history: List<ChatMessage>, userMessage: String): String {
        val sb = StringBuilder("<|system|>\nYou are AirLLM, a helpful on-device assistant.\n")
        for (msg in history) {
            when (msg.role) {
                ChatMessage.Role.User -> sb.append("<|user|>\n${msg.content}\n")
                ChatMessage.Role.Assistant -> sb.append("<|assistant|>\n${msg.content}\n")
            }
        }
        sb.append("<|user|>\n$userMessage\n<|assistant|>\n")
        return sb.toString()
    }

    private fun buildPhi3Prompt(history: List<ChatMessage>, userMessage: String): String {
        val sb = StringBuilder("<|system|>\nYou are AirLLM, a helpful on-device assistant.<|end|>\n")
        for (msg in history) {
            when (msg.role) {
                ChatMessage.Role.User -> sb.append("<|user|>\n${msg.content}<|end|>\n")
                ChatMessage.Role.Assistant -> sb.append("<|assistant|>\n${msg.content}<|end|>\n")
            }
        }
        sb.append("<|user|>\n$userMessage<|end|>\n<|assistant|>\n")
        return sb.toString()
    }

    private fun buildQwenPrompt(history: List<ChatMessage>, userMessage: String): String {
        val sb = StringBuilder("<|im_start|>system\nYou are AirLLM, a helpful on-device assistant.\n")
        for (msg in history) {
            when (msg.role) {
                ChatMessage.Role.User -> sb.append("<|im_start|>user\n${msg.content}\n")
                ChatMessage.Role.Assistant -> sb.append("<|im_start|>assistant\n${msg.content}\n")
            }
        }
        sb.append("<|im_start|>user\n$userMessage\n<|im_start|>assistant\n")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { llmInference?.close() }
    }
}
