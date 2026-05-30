package io.shubham0204.startwithsmollm.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WhisperManager - Handles Speech-to-Text using Whisper.cpp
 * 
 * Uses whisper-tiny model (~75MB) for offline speech recognition.
 * 
 * Flow:
 * 1. Record audio from microphone
 * 2. Convert to 16kHz mono WAV (Whisper's required format)
 * 3. Run Whisper inference
 * 4. Return transcribed text
 */
class WhisperManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WhisperManager"
        private const val SAMPLE_RATE = 16000  // Whisper requires 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MODEL_FILENAME = "whisper-tiny.bin"
        
        init {
            try {
                System.loadLibrary("whisper_jni")
                Log.d(TAG, "Whisper JNI library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load whisper_jni library: ${e.message}")
            }
        }
    }
    
    private var nativePtr: Long = 0
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = mutableListOf<Short>()
    
    private val _state = MutableStateFlow(WhisperState())
    val state: StateFlow<WhisperState> = _state
    
    data class WhisperState(
        val isModelLoaded: Boolean = false,
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val lastTranscription: String = "",
        val error: String? = null
    )
    
    /**
     * Initialize Whisper model
     * Call this once when the app starts or when voice feature is enabled
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = _state.value.copy(error = null)
            
            // Check if model exists, if not, copy from assets
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            if (!modelFile.exists()) {
                Log.d(TAG, "Copying Whisper model from assets...")
                copyModelFromAssets(modelFile)
            }
            
            // Load the model
            Log.d(TAG, "Loading Whisper model...")
            nativePtr = loadModel(modelFile.absolutePath)
            
            if (nativePtr != 0L) {
                _state.value = _state.value.copy(isModelLoaded = true)
                Log.d(TAG, "Whisper model loaded successfully")
                true
            } else {
                _state.value = _state.value.copy(error = "Failed to load Whisper model")
                Log.e(TAG, "Failed to load Whisper model")
                false
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message)
            Log.e(TAG, "Error initializing Whisper: ${e.message}")
            false
        }
    }
    
    private fun copyModelFromAssets(destFile: File) {
        context.assets.open(MODEL_FILENAME).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * Start recording audio from microphone
     */
    fun startRecording(): Boolean {
        if (isRecording) return false
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                _state.value = _state.value.copy(error = "Microphone initialization failed")
                return false
            }
            
            audioBuffer.clear()
            audioRecord?.startRecording()
            isRecording = true
            _state.value = _state.value.copy(isRecording = true, error = null)
            
            // Start recording thread
            Thread {
                val buffer = ShortArray(bufferSize / 2)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        synchronized(audioBuffer) {
                            audioBuffer.addAll(buffer.take(read))
                        }
                    }
                }
            }.start()
            
            Log.d(TAG, "Recording started")
            return true
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(error = "Microphone permission denied")
            Log.e(TAG, "Microphone permission denied: ${e.message}")
            return false
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message)
            Log.e(TAG, "Error starting recording: ${e.message}")
            return false
        }
    }
    
    /**
     * Stop recording and transcribe the audio
     */
    suspend fun stopRecordingAndTranscribe(): String = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext ""
        
        // Stop recording
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _state.value = _state.value.copy(isRecording = false, isTranscribing = true)
        
        Log.d(TAG, "Recording stopped, ${audioBuffer.size} samples captured")
        
        // Convert to float array for Whisper
        val samples: FloatArray
        synchronized(audioBuffer) {
            samples = FloatArray(audioBuffer.size) { i ->
                audioBuffer[i] / 32768.0f  // Normalize to [-1, 1]
            }
        }
        
        if (samples.isEmpty()) {
            _state.value = _state.value.copy(isTranscribing = false, error = "No audio recorded")
            return@withContext ""
        }
        
        // Transcribe
        try {
            Log.d(TAG, "Transcribing ${samples.size} samples...")
            val startTime = System.currentTimeMillis()
            
            val transcription = transcribe(nativePtr, samples)
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Transcription completed in ${elapsed}ms: $transcription")
            
            _state.value = _state.value.copy(
                isTranscribing = false,
                lastTranscription = transcription
            )
            
            transcription
        } catch (e: Exception) {
            _state.value = _state.value.copy(isTranscribing = false, error = e.message)
            Log.e(TAG, "Transcription error: ${e.message}")
            ""
        }
    }
    
    /**
     * Cancel recording without transcribing
     */
    fun cancelRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioBuffer.clear()
        _state.value = _state.value.copy(isRecording = false)
        Log.d(TAG, "Recording cancelled")
    }
    
    /**
     * Release resources
     */
    fun release() {
        cancelRecording()
        if (nativePtr != 0L) {
            freeModel(nativePtr)
            nativePtr = 0
        }
        _state.value = WhisperState()
        Log.d(TAG, "WhisperManager released")
    }
    
    // Native methods - implemented in whisper_jni.cpp
    private external fun loadModel(modelPath: String): Long
    private external fun transcribe(contextPtr: Long, samples: FloatArray): String
    private external fun freeModel(contextPtr: Long)
}
