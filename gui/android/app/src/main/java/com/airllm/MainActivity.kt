package com.airllm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airllm.ui.ChatScreen
import com.airllm.ui.ModelManagerScreen
import com.airllm.viewmodel.ChatViewModel
import com.airllm.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val downloadViewModel: DownloadViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for foreground service (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            AirLLMTheme {
                AirLLMApp(chatViewModel, downloadViewModel)
            }
        }
    }
}

// ─── Navigation ───────────────────────────────────────────────────────────────

@Composable
fun AirLLMApp(
    chatViewModel: ChatViewModel,
    downloadViewModel: DownloadViewModel,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                viewModel         = chatViewModel,
                onNavigateToModels = { navController.navigate("models") },
            )
        }
        composable("models") {
            ModelManagerScreen(
                chatViewModel     = chatViewModel,
                downloadViewModel = downloadViewModel,
                onBack            = { navController.popBackStack() },
            )
        }
    }
}

// ─── Theme ────────────────────────────────────────────────────────────────────

private val BgDark  = Color(0xFF070E18)
private val BgCard  = Color(0xFF0B1927)
private val TealMain = Color(0xFF39B1D1)
private val LimeMain = Color(0xFFE4FD85)

@Composable
fun AirLLMTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary         = TealMain,
        secondary       = LimeMain,
        background      = BgDark,
        surface         = BgCard,
        onPrimary       = BgDark,
        onBackground    = Color(0xFFEBF4F8),
        onSurface       = Color(0xFFEBF4F8),
        primaryContainer = Color(0xFF152B3C),
        onPrimaryContainer = TealMain,
        outline         = Color(0xFF152B3C),
    )
    MaterialTheme(
        colorScheme = darkColorScheme,
        content     = content,
    )
}
