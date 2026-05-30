package io.shubham0204.startwithsmollm.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * VoiceManager - Unified interface for voice input (STT) and output (TTS)
 * 
 * Combines WhisperManager (STT) and TTSManager (TTS) into a single interface.
 * 
 * Usage:
 * ```kotlin
 * val voiceManager = VoiceManager(context)
 * voiceManager.initialize()
 * 
 * // Voice input
 * voiceManager.startListening()
 * // ... user speaks ...
 * val text = voiceManager.stopListeningAndTranscribe()
 * 
 * // Voice output
 * voiceManager.speak("Hello, how can I help?")
 * ```
 */
class VoiceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceManager"
    }
    
    private val whisperManager = WhisperManager(context)
    private val ttsManager = TTSManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state
    
    data class VoiceState(
        val isWhisperLoaded: Boolean = false,
        val isTTSReady: Boolean = false,
        val isListening: Boolean = false,
        val isTranscribing: Boolean = false,
        val isSpeaking: Boolean = false,
        val voiceEnabled: Boolean = true,
        val autoSpeak: Boolean = true,  // Auto-speak LLM responses
        val lastTranscription: String = "",
        val error: String? = null
    )
    
    init {
        // Combine states from both managers
        scope.launch {
            combine(
                whisperManager.state,
                ttsManager.state
            ) { whisperState, ttsState ->
                _state.value.copy(
                    isWhisperLoaded = whisperState.isModelLoaded,
                    isTTSReady = ttsState.isInitialized,
                    isListening = whisperState.isRecording,
                    isTranscribing = whisperState.isTranscribing,
                    isSpeaking = ttsState.isSpeaking,
                    lastTranscription = whisperState.lastTranscription,
                    error = whisperState.error ?: ttsState.error
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }
    
    /**
     * Initialize both STT and TTS
     * @param loadWhisper Whether to load Whisper model (can be deferred to save memory)
     */
    suspend fun initialize(loadWhisper: Boolean = true): Boolean {
        Log.d(TAG, "Initializing VoiceManager (loadWhisper=$loadWhisper)")
        
        // Always initialize TTS (lightweight)
        ttsManager.initialize()
        
        // Optionally load Whisper (heavy)
        return if (loadWhisper) {
            whisperManager.initialize()
        } else {
            true
        }
    }
    
    /**
     * Load Whisper model if not already loaded
     */
    suspend fun loadWhisperIfNeeded(): Boolean {
        if (_state.value.isWhisperLoaded) return true
        return whisperManager.initialize()
    }
    
    // ==================== STT (Speech-to-Text) ====================
    
    /**
     * Start listening for voice input
     * @return true if recording started successfully
     */
    fun startListening(): Boolean {
        if (!_state.value.isWhisperLoaded) {
            _state.value = _state.value.copy(error = "Whisper not loaded")
            Log.e(TAG, "Cannot start listening - Whisper not loaded")
            return false
        }
        
        // Stop TTS if speaking
        if (_state.value.isSpeaking) {
            ttsManager.stop()
        }
        
        return whisperManager.startRecording()
    }
    
    /**
     * Stop listening and transcribe the recorded audio
     * @return Transcribed text
     */
    suspend fun stopListeningAndTranscribe(): String {
        return whisperManager.stopRecordingAndTranscribe()
    }
    
    /**
     * Cancel listening without transcribing
     */
    fun cancelListening() {
        whisperManager.cancelRecording()
    }
    
    // ==================== TTS (Text-to-Speech) ====================
    
    /**
     * Speak the given text
     * @param text Text to speak
     * @param onComplete Callback when speech is complete
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!_state.value.voiceEnabled || !_state.value.autoSpeak) {
            onComplete?.invoke()
            return
        }
        
        ttsManager.speak(text, onComplete)
    }
    
    /**
     * Stop speaking
     */
    fun stopSpeaking() {
        ttsManager.stop()
    }
    
    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean = ttsManager.isSpeaking()
    
    /**
     * Set speech rate
     * @param rate 0.5 = slow, 1.0 = normal, 2.0 = fast
     */
    fun setSpeechRate(rate: Float) {
        ttsManager.setSpeechRate(rate)
    }
    
    // ==================== Settings ====================
    
    /**
     * Enable/disable voice features
     */
    fun setVoiceEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(voiceEnabled = enabled)
        if (!enabled) {
            cancelListening()
            stopSpeaking()
        }
    }
    
    /**
     * Enable/disable auto-speak for LLM responses
     */
    fun setAutoSpeak(enabled: Boolean) {
        _state.value = _state.value.copy(autoSpeak = enabled)
    }
    
    /**
     * Toggle voice on/off
     */
    fun toggleVoice() {
        setVoiceEnabled(!_state.value.voiceEnabled)
    }
    
    /**
     * Toggle auto-speak on/off
     */
    fun toggleAutoSpeak() {
        setAutoSpeak(!_state.value.autoSpeak)
    }
    
    // ==================== Lifecycle ====================
    
    /**
     * Release all resources
     */
    fun release() {
        whisperManager.release()
        ttsManager.release()
        _state.value = VoiceState()
        Log.d(TAG, "VoiceManager released")
    }
}
