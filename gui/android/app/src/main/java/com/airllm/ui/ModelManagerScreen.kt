package com.airllm.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airllm.model.ModelConfig
import com.airllm.model.ModelRegistry
import com.airllm.viewmodel.ChatViewModel
import com.airllm.viewmodel.DownloadState
import com.airllm.viewmodel.DownloadViewModel
import com.airllm.viewmodel.ModelState

private val BgDark    = Color(0xFF070E18)
private val BgCard    = Color(0xFF0B1927)
private val BgInput   = Color(0xFF0F2035)
private val TealMain  = Color(0xFF39B1D1)
private val TealDim   = Color(0xFF2689A3)
private val LimeMain  = Color(0xFFE4FD85)
private val TextMain  = Color(0xFFEBF4F8)
private val TextMuted = Color(0xFF7A9BB0)
private val TextDim   = Color(0xFF4A6A80)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    chatViewModel: ChatViewModel,
    downloadViewModel: DownloadViewModel,
    onBack: () -> Unit,
) {
    val installedModels by downloadViewModel.installedModels.collectAsState()
    val downloadStates  by downloadViewModel.downloadStates.collectAsState()
    val modelState      by chatViewModel.modelState.collectAsState()
    val context          = LocalContext.current
    val deviceRam        = remember { getDeviceRamGB(context) }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCard, titleContentColor = TextMain),
                title  = { Text("Models", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextMain)
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Device RAM info card
                Surface(color = BgCard, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Memory, null, tint = TealMain, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Your device RAM: ${deviceRam} GB", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Models marked ✓ are compatible", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Available Models", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            }

            items(ModelRegistry.models) { model ->
                val isInstalled     = model.id in installedModels
                val downloadState   = downloadStates[model.id] ?: DownloadState.Idle
                val isLoaded        = modelState is ModelState.Ready &&
                        (modelState as ModelState.Ready).modelPath == downloadViewModel.getModelFile(model).absolutePath
                val isCompatible    = deviceRam >= model.minRamGB

                ModelCard(
                    model          = model,
                    isInstalled    = isInstalled,
                    isLoaded       = isLoaded,
                    isCompatible   = isCompatible,
                    downloadState  = downloadState,
                    onDownload     = { downloadViewModel.downloadModel(model) },
                    onLoad         = {
                        chatViewModel.loadModel(
                            modelPath  = downloadViewModel.getModelFile(model).absolutePath,
                            modelName  = model.displayName,
                            chatTemplate = model.chatTemplate,
                        )
                        onBack()
                    },
                    onDelete       = { downloadViewModel.deleteModel(model) },
                    formatBytes    = downloadViewModel::formatBytes,
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelConfig,
    isInstalled: Boolean,
    isLoaded: Boolean,
    isCompatible: Boolean,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    formatBytes: (Long) -> String,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color  = BgCard,
        shape  = RoundedCornerShape(14.dp),
        border = if (isLoaded) BorderStroke(1.dp, TealMain)
                 else BorderStroke(1.dp, Color(0xFF152B3C)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(model.displayName, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (model.isRecommended) {
                            Surface(color = LimeMain.copy(alpha = .15f), shape = RoundedCornerShape(4.dp)) {
                                Text("Recommended", color = LimeMain, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        if (isLoaded) {
                            Surface(color = TealMain.copy(alpha = .15f), shape = RoundedCornerShape(4.dp)) {
                                Text("Active", color = TealMain, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(model.description, color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = TextDim,
                    )
                }
            }

            // Metadata chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Chip("${model.parametersBillion}B params", TealMain)
                Chip("${model.sizeGB} GB", TextMuted)
                Chip("${model.quantization}", TextMuted)
                Chip(
                    if (isCompatible) "✓ Compatible" else "Needs ${model.minRamGB} GB RAM",
                    if (isCompatible) TealMain else Color(0xFFFF5A5A),
                )
            }

            // Download progress
            AnimatedVisibility(visible = downloadState is DownloadState.Downloading) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    val dl = downloadState as? DownloadState.Downloading
                    LinearProgressIndicator(
                        progress = { dl?.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)),
                        color    = TealMain,
                        trackColor = Color(0xFF152B3C),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatBytes(dl?.bytesDownloaded ?: 0)} / ${formatBytes(dl?.totalBytes ?: 0)}",
                        color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    downloadState is DownloadState.Downloading -> {
                        Text("Downloading…", color = TealMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                    isInstalled -> {
                        Button(
                            onClick = onLoad,
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = if (isLoaded) TealDim else TealMain,
                                contentColor   = BgDark,
                            ),
                            shape   = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isLoaded) "Loaded" else "Load", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5A5A)),
                            border  = BorderStroke(1.dp, Color(0xFF3A1515)),
                            shape   = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    else -> {
                        Button(
                            onClick  = onDownload,
                            enabled  = isCompatible,
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF152B3C), contentColor = TealMain),
                            shape    = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download ${model.sizeGB} GB", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Expanded: HuggingFace URL
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = Color(0xFF152B3C))
                    Spacer(Modifier.height(8.dp))
                    Text("Source:", color = TextDim, fontSize = 11.sp)
                    Text(model.huggingFaceUrl, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(color = color.copy(alpha = .1f), shape = RoundedCornerShape(6.dp)) {
        Text(text, color = color, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

private fun getDeviceRamGB(context: Context): Int {
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actManager.getMemoryInfo(memInfo)
    return (memInfo.totalMem / 1_073_741_824L).toInt()
}
