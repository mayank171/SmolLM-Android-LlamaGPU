package io.shubham0204.startwithsmollm.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * TTSManager - Handles Text-to-Speech using Android's built-in TTS
 * 
 * Uses Android's offline TTS engine (no additional download needed).
 * Quality is decent and works on all Android devices.
 */
class TTSManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TTSManager"
    }
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeakComplete: (() -> Unit)? = null
    
    private val _state = MutableStateFlow(TTSState())
    val state: StateFlow<TTSState> = _state
    
    data class TTSState(
        val isInitialized: Boolean = false,
        val isSpeaking: Boolean = false,
        val error: String? = null
    )
    
    /**
     * Initialize TTS engine
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported, trying default")
                    tts?.setLanguage(Locale.getDefault())
                }
                
                // Set speech rate (1.0 = normal, 0.5 = slow, 2.0 = fast)
                tts?.setSpeechRate(1.0f)
                
                // Set pitch (1.0 = normal)
                tts?.setPitch(1.0f)
                
                // Set up listener for speech completion
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(isSpeaking = true)
                        Log.d(TAG, "Speech started")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(isSpeaking = false)
                        onSpeakComplete?.invoke()
                        Log.d(TAG, "Speech completed")
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(isSpeaking = false, error = "TTS error")
                        Log.e(TAG, "Speech error")
                    }
                    
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = _state.value.copy(isSpeaking = false, error = "TTS error: $errorCode")
                        Log.e(TAG, "Speech error: $errorCode")
                    }
                })
                
                isInitialized = true
                _state.value = _state.value.copy(isInitialized = true, error = null)
                Log.d(TAG, "TTS initialized successfully")
            } else {
                _state.value = _state.value.copy(error = "TTS initialization failed")
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }
    
    /**
     * Speak the given text
     * @param text Text to speak
     * @param onComplete Callback when speech is complete
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized")
            onComplete?.invoke()
            return
        }
        
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }
        
        onSpeakComplete = onComplete
        
        // Clean text for better TTS (remove markdown, code blocks, etc.)
        val cleanText = cleanTextForTTS(text)
        
        // Split long text into chunks (TTS has limits)
        val chunks = splitTextIntoChunks(cleanText, 3000)
        
        chunks.forEachIndexed { index, chunk ->
            val utteranceId = "utterance_$index"
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            
            tts?.speak(chunk, queueMode, null, utteranceId)
        }
        
        Log.d(TAG, "Speaking ${chunks.size} chunks")
    }
    
    /**
     * Stop speaking
     */
    fun stop() {
        tts?.stop()
        _state.value = _state.value.copy(isSpeaking = false)
        Log.d(TAG, "Speech stopped")
    }
    
    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true
    
    /**
     * Set speech rate
     * @param rate 0.5 = slow, 1.0 = normal, 2.0 = fast
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }
    
    /**
     * Set language
     */
    fun setLanguage(locale: Locale): Boolean {
        val result = tts?.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }
    
    /**
     * Release TTS resources
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _state.value = TTSState()
        Log.d(TAG, "TTS released")
    }
    
    /**
     * Clean text for better TTS output
     * Removes code blocks, markdown formatting, and other non-speakable content
     */
    private fun cleanTextForTTS(text: String): String {
        var result = text
        
        // Remove fenced code blocks (```...```) - most important!
        // Use DOTALL flag to match across newlines
        result = result.replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```", RegexOption.DOT_MATCHES_ALL), " I've written the code for you. ")
        result = result.replace(Regex("```[\\s\\S]*?```", RegexOption.DOT_MATCHES_ALL), " code block ")
        
        // Remove any remaining triple backticks
        result = result.replace("```", "")
        
        // Remove inline code (single backticks)
        result = result.replace(Regex("`([^`]+)`")) { match ->
            // Keep short inline code as spoken text, skip long ones
            val code = match.groupValues[1]
            if (code.length <= 20) code else ""
        }
        
        // Remove markdown headers but keep the text
        result = result.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        
        // Remove bold/italic markers but keep text
        result = result.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        result = result.replace(Regex("\\*([^*]+)\\*"), "$1")
        result = result.replace(Regex("__([^_]+)__"), "$1")
        result = result.replace(Regex("_([^_]+)_"), "$1")
        
        // Remove links, keep display text
        result = result.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        
        // Remove bullet points
        result = result.replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
        
        // Remove numbered lists prefix
        result = result.replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")
        
        // Remove HTML tags
        result = result.replace(Regex("<[^>]+>"), "")
        
        // Remove special characters that sound weird when spoken
        result = result.replace(Regex("[{}\\[\\]<>|\\\\^~]"), " ")
        
        // Replace common programming symbols with words
        result = result.replace("&&", " and ")
        result = result.replace("||", " or ")
        result = result.replace("!=", " not equals ")
        result = result.replace("==", " equals ")
        result = result.replace(">=", " greater than or equal to ")
        result = result.replace("<=", " less than or equal to ")
        result = result.replace("->", " arrow ")
        result = result.replace("=>", " arrow ")
        
        // Remove emojis
        result = result.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "")
        
        // Collapse multiple spaces and newlines
        result = result.replace(Regex("\\s+"), " ")
        
        // Trim
        return result.trim()
    }
    
    /**
     * Split text into chunks for TTS
     */
    private fun splitTextIntoChunks(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)
        
        val chunks = mutableListOf<String>()
        var remaining = text
        
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLength) {
                chunks.add(remaining)
                break
            }
            
            // Find a good break point (sentence end, comma, space)
            var breakPoint = remaining.lastIndexOf(". ", maxLength)
            if (breakPoint == -1) breakPoint = remaining.lastIndexOf(", ", maxLength)
            if (breakPoint == -1) breakPoint = remaining.lastIndexOf(" ", maxLength)
            if (breakPoint == -1) breakPoint = maxLength
            
            chunks.add(remaining.substring(0, breakPoint + 1).trim())
            remaining = remaining.substring(breakPoint + 1).trim()
        }
        
        return chunks
    }
}
