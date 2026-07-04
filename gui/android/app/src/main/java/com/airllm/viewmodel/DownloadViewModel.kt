package com.airllm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airllm.model.ModelConfig
import com.airllm.model.ModelRegistry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ─── Download State ───────────────────────────────────────────────────────────

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Done(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _installedModels = MutableStateFlow<Set<String>>(emptySet())
    val installedModels: StateFlow<Set<String>> = _installedModels.asStateFlow()

    init {
        refreshInstalledModels()
    }

    fun refreshInstalledModels() {
        val installed = ModelRegistry.models
            .filter { getModelFile(it).exists() }
            .map { it.id }
            .toSet()
        _installedModels.value = installed
    }

    fun getModelFile(model: ModelConfig): File {
        val dir = File(getApplication<Application>().filesDir, "models")
        dir.mkdirs()
        return File(dir, model.fileName)
    }

    fun downloadModel(model: ModelConfig) {
        viewModelScope.launch {
            val destFile = getModelFile(model)

            // Set initial state
            updateDownload(model.id, DownloadState.Downloading(0f, 0L, 0L))

            try {
                val url = URL(model.huggingFaceUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                val totalBytes = connection.contentLengthLong
                var bytesRead = 0L

                connection.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                            updateDownload(
                                model.id,
                                DownloadState.Downloading(progress, bytesRead, totalBytes)
                            )
                        }
                    }
                }

                // Verify checksum if available
                if (model.checksum != null) {
                    val actualChecksum = calculateFileChecksum(destFile)
                    if (actualChecksum != model.checksum) {
                        destFile.delete()
                        updateDownload(model.id, DownloadState.Error("Checksum verification failed"))
                        return@launch
                    }
                }

                updateDownload(model.id, DownloadState.Done(destFile.absolutePath))
                _installedModels.value = _installedModels.value + model.id

            } catch (e: Exception) {
                destFile.delete() // clean up partial download
                updateDownload(model.id, DownloadState.Error(e.message ?: "Download failed"))
            }
        }
    }

    private fun calculateFileChecksum(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun deleteModel(model: ModelConfig) {
        getModelFile(model).delete()
        _installedModels.value = _installedModels.value - model.id
        updateDownload(model.id, DownloadState.Idle)
    }

    private fun updateDownload(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value + (modelId to state)
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576    -> "%.1f MB".format(bytes / 1_048_576.0)
            else                  -> "$bytes B"
        }
    }
}
