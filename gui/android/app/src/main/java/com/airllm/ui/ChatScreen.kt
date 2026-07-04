package com.airllm.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airllm.viewmodel.ChatMessage
import com.airllm.viewmodel.ChatViewModel
import com.airllm.viewmodel.ModelState
import kotlinx.coroutines.launch

// ─── Colours ──────────────────────────────────────────────────────────────────

private val BgDark    = Color(0xFF070E18)
private val BgCard    = Color(0xFF0B1927)
private val BgInput   = Color(0xFF0F2035)
private val TealMain  = Color(0xFF39B1D1)
private val TealDim   = Color(0xFF2689A3)
private val LimeMain  = Color(0xFFE4FD85)
private val TextMain  = Color(0xFFEBF4F8)
private val TextMuted = Color(0xFF7A9BB0)
private val TextDim   = Color(0xFF4A6A80)

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToModels: () -> Unit,
) {
    val messages     by viewModel.messages.collectAsState()
    val modelState   by viewModel.modelState.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val maxTokens    by viewModel.maxTokens.collectAsState()
    val temperature  by viewModel.temperature.collectAsState()

    var inputText   by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            ChatTopBar(
                modelState   = modelState,
                isGenerating = isGenerating,
                onModels     = onNavigateToModels,
                onSettings   = { showSettings = !showSettings },
                onClear      = { viewModel.clearChat() },
                onStop       = { viewModel.stopGeneration() },
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText    = inputText,
                onTextChange = { inputText = it },
                isGenerating = isGenerating,
                modelReady   = modelState is ModelState.Ready,
                onSend       = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                        scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
                    }
                },
                onStop = { viewModel.stopGeneration() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Settings panel (collapsible)
            AnimatedVisibility(visible = showSettings) {
                SettingsPanel(
                    maxTokens   = maxTokens,
                    temperature = temperature,
                    onMaxTokens   = viewModel::setMaxTokens,
                    onTemperature = viewModel::setTemperature,
                )
            }

            // Messages or empty state
            if (messages.isEmpty()) {
                EmptyState(
                    modelState       = modelState,
                    onNavigateModels = onNavigateToModels,
                )
            } else {
                LazyColumn(
                    state           = listState,
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(msg)
                    }
                }
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    modelState: ModelState,
    isGenerating: Boolean,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onClear: () -> Unit,
    onStop: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BgCard,
            titleContentColor = TextMain,
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pulsing status dot
                val dotColor = when {
                    isGenerating              -> LimeMain
                    modelState is ModelState.Ready -> TealMain
                    else                      -> TextDim
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Column {
                    Text("AirLLM", fontSize = 16.sp, color = TextMain)
                    Text(
                        text = when (modelState) {
                            is ModelState.None    -> "No model loaded"
                            is ModelState.Loading -> "Loading ${modelState.modelName}…"
                            is ModelState.Ready   -> modelState.modelName
                            is ModelState.Error   -> "Error"
                        },
                        fontSize  = 11.sp,
                        color     = TextMuted,
                    )
                }
            }
        },
        actions = {
            if (isGenerating) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, "Stop", tint = Color(0xFFFF5A5A))
                }
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteOutline, "Clear chat", tint = TextMuted)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = TextMuted)
            }
            IconButton(onClick = onModels) {
                Icon(Icons.Default.FolderOpen, "Models", tint = TealMain)
            }
        }
    )
}

// ─── Message Bubble ──────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.User
    Row(
        modifier          = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            // AI avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BgCard)
                    .border(1.dp, TealDim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("AI", fontSize = 10.sp, color = LimeMain, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier   = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd   = if (isUser) 4.dp else 16.dp,
                        )
                    )
                    .background(
                        if (isUser) Brush.linearGradient(listOf(TealMain, TealDim))
                        else        Brush.linearGradient(listOf(BgCard, BgInput))
                    )
                    .border(
                        width = 1.dp,
                        color = if (isUser) Color.Transparent else Color(0xFF152B3C),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (message.content.isEmpty() && message.isStreaming) {
                    // Blinking cursor while waiting for first token
                    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(500),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                        ),
                        label = "cursorAlpha",
                    )
                    Text("▍", color = TealMain.copy(alpha = alpha), fontSize = 14.sp)
                } else {
                    Row {
                        Text(
                            text      = message.content,
                            color     = if (isUser) BgDark else TextMain,
                            fontSize  = 14.sp,
                            lineHeight = 21.sp,
                        )
                        if (message.isStreaming) {
                            val infiniteTransition = rememberInfiniteTransition(label = "cursor2")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 1f, targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(500),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                                ),
                                label = "cursorAlpha2",
                            )
                            Text("▍", color = (if (isUser) BgDark else TealMain).copy(alpha = alpha), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(TealMain),
                contentAlignment = Alignment.Center,
            ) {
                Text("U", fontSize = 12.sp, color = BgDark)
            }
        }
    }
}

// ─── Input Bar ───────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    modelReady: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = BgCard, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (modelReady) "Message AirLLM…" else "Load a model first",
                        color = TextDim,
                        fontSize = 14.sp,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = TealMain,
                    unfocusedBorderColor = Color(0xFF152B3C),
                    focusedContainerColor   = BgInput,
                    unfocusedContainerColor = BgInput,
                    focusedTextColor     = TextMain,
                    unfocusedTextColor   = TextMain,
                    cursorColor          = TealMain,
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { if (modelReady && !isGenerating) onSend() }),
                maxLines = 5,
                enabled = modelReady,
            )

            // Send / Stop button
            FilledIconButton(
                onClick = if (isGenerating) onStop else onSend,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isGenerating) Color(0xFFFF5A5A) else TealMain,
                    contentColor   = BgDark,
                ),
                enabled = modelReady || isGenerating,
            ) {
                Icon(
                    imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.Send,
                    contentDescription = if (isGenerating) "Stop" else "Send",
                )
            }
        }
    }
}

// ─── Settings Panel ──────────────────────────────────────────────────────────

@Composable
private fun SettingsPanel(
    maxTokens: Int,
    temperature: Float,
    onMaxTokens: (Int) -> Unit,
    onTemperature: (Float) -> Unit,
) {
    Surface(color = BgCard, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Inference Settings", color = TextMuted, fontSize = 12.sp)
            // Max tokens
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Max Tokens", color = TextMain, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { onMaxTokens(it.toInt()) },
                    valueRange = 64f..2048f,
                    steps = 30,
                    colors = SliderDefaults.colors(thumbColor = TealMain, activeTrackColor = TealMain),
                    modifier = Modifier.weight(1f),
                )
                Text("$maxTokens", color = TealMain, fontSize = 13.sp, modifier = Modifier.width(40.dp))
            }
            // Temperature
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Temperature", color = TextMain, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                Slider(
                    value = temperature,
                    onValueChange = onTemperature,
                    valueRange = 0f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = LimeMain, activeTrackColor = LimeMain),
                    modifier = Modifier.weight(1f),
                )
                Text("%.2f".format(temperature), color = LimeMain, fontSize = 13.sp, modifier = Modifier.width(40.dp))
            }
        }
    }
}

// ─── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    modelState: ModelState,
    onNavigateModels: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⬡", fontSize = 64.sp, color = TealMain)
        Spacer(Modifier.height(16.dp))
        Text("AirLLM", fontSize = 24.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "On-device AI — no internet required\nonce a model is downloaded.",
            color = TextMuted,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(32.dp))
        when (modelState) {
            is ModelState.None -> {
                Button(
                    onClick = onNavigateModels,
                    colors = ButtonDefaults.buttonColors(containerColor = TealMain, contentColor = BgDark),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download a Model", fontFamily = FontFamily.Default)
                }
            }
            is ModelState.Loading -> {
                CircularProgressIndicator(color = TealMain)
                Spacer(Modifier.height(12.dp))
                Text("Loading ${modelState.modelName}…", color = TextMuted, fontSize = 13.sp)
            }
            is ModelState.Error -> {
                Text(modelState.message, color = Color(0xFFFF5A5A), fontSize = 13.sp)
            }
            else -> {}
        }
    }
}
