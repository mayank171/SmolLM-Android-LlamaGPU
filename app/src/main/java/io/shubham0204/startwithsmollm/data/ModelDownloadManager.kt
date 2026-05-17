package io.shubham0204.startwithsmollm.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int, val downloadedMB: Int, val totalMB: Int) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

class ModelDownloadManager(private val context: Context) {
    
    private val modelsDir: File
        get() = File(context.filesDir, "models").also { 
            if (!it.exists()) it.mkdirs() 
        }
    
    fun getModelPath(fileName: String): String {
        return File(modelsDir, fileName).absolutePath
    }
    
    fun isModelDownloaded(fileName: String): Boolean {
        return File(modelsDir, fileName).exists()
    }
    
    fun getDownloadedModels(): List<ModelInfo> {
        return AvailableModels.models.filter { isModelDownloaded(it.fileName) }
    }
    
    fun deleteModel(fileName: String): Boolean {
        return File(modelsDir, fileName).delete()
    }
    
    fun downloadModel(model: ModelInfo): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0, 0, model.sizeInMB))
        
        try {
            val outputFile = File(modelsDir, model.fileName)
            val tempFile = File(modelsDir, "${model.fileName}.tmp")
            
            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SmolLM-Android-App")
            
            // Handle redirects (HuggingFace uses them)
            var finalConnection = connection
            var responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308) {
                val redirectUrl = connection.getHeaderField("Location")
                finalConnection = URL(redirectUrl).openConnection() as HttpURLConnection
                finalConnection.setRequestProperty("User-Agent", "SmolLM-Android-App")
                responseCode = finalConnection.responseCode
            }
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Failed("Server returned HTTP $responseCode"))
                return@flow
            }
            
            val totalBytes = finalConnection.contentLengthLong
            var downloadedBytes = 0L
            
            finalConnection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastEmitTime = System.currentTimeMillis()
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Emit progress every 500ms to avoid too many updates
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastEmitTime > 500) {
                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt()
                            } else {
                                -1
                            }
                            val downloadedMB = (downloadedBytes / (1024 * 1024)).toInt()
                            emit(DownloadState.Downloading(progress, downloadedMB, model.sizeInMB))
                            lastEmitTime = currentTime
                        }
                    }
                }
            }
            
            // Rename temp file to final file
            if (tempFile.renameTo(outputFile)) {
                emit(DownloadState.Completed(outputFile.absolutePath))
            } else {
                tempFile.delete()
                emit(DownloadState.Failed("Failed to save model file"))
            }
            
        } catch (e: Exception) {
            emit(DownloadState.Failed(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)
}
