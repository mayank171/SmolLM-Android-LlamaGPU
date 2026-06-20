package io.shubham0204.startwithsmollm.image

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages image input from camera and gallery
 * Handles temporary file creation for camera captures
 */
class ImageInputManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageInputManager"
        private const val TEMP_IMAGE_PREFIX = "camera_capture_"
        private const val TEMP_IMAGE_SUFFIX = ".jpg"
    }
    
    private var currentPhotoUri: Uri? = null
    
    /**
     * Create a temporary file URI for camera capture
     * Uses FileProvider for secure file sharing
     */
    fun createCameraImageUri(): Uri? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFileName = "${TEMP_IMAGE_PREFIX}${timeStamp}${TEMP_IMAGE_SUFFIX}"
            
            val storageDir = File(context.cacheDir, "camera_images").apply {
                if (!exists()) mkdirs()
            }
            
            val imageFile = File(storageDir, imageFileName)
            
            currentPhotoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            
            Log.d(TAG, "Created camera URI: $currentPhotoUri")
            currentPhotoUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera URI: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get the last created camera URI
     */
    fun getCurrentPhotoUri(): Uri? = currentPhotoUri
    
    /**
     * Clear the current photo URI after processing
     */
    fun clearCurrentPhotoUri() {
        currentPhotoUri = null
    }
    
    /**
     * Clean up old temporary camera files
     * Call periodically to prevent cache buildup
     */
    fun cleanupOldCameraFiles(maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        try {
            val storageDir = File(context.cacheDir, "camera_images")
            if (!storageDir.exists()) return
            
            val cutoffTime = System.currentTimeMillis() - maxAgeMs
            storageDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                    Log.d(TAG, "Deleted old camera file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup camera files: ${e.message}")
        }
    }
    
    /**
     * Check if a URI is valid and accessible
     */
    fun isUriValid(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "URI validation failed: ${e.message}")
            false
        }
    }
    
    /**
     * Get file size from URI
     */
    fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { 
                it.available().toLong() 
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
