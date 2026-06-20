package io.shubham0204.startwithsmollm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.shubham0204.smollm.GGUFReader
import io.shubham0204.startwithsmollm.gpu.LlamaGPU
import io.shubham0204.startwithsmollm.gpu.KVCacheType
import io.shubham0204.startwithsmollm.data.AvailableModels
import io.shubham0204.startwithsmollm.data.DeviceCapabilities
import io.shubham0204.startwithsmollm.data.DeviceProfile
import io.shubham0204.startwithsmollm.data.DeviceTier
import io.shubham0204.startwithsmollm.data.DownloadState
import io.shubham0204.startwithsmollm.data.ModelDownloadManager
import io.shubham0204.startwithsmollm.data.ModelInfo
import io.shubham0204.startwithsmollm.ui.ModelSelectionUiState
import io.shubham0204.startwithsmollm.ui.InferenceMetrics
import io.shubham0204.startwithsmollm.ui.RagBenchmarkUiState
import io.shubham0204.startwithsmollm.benchmark.RagBenchmarkRunner
import io.shubham0204.startwithsmollm.data.ExpertMode
import io.shubham0204.startwithsmollm.rag.Document
import io.shubham0204.startwithsmollm.rag.RagConfig
import io.shubham0204.startwithsmollm.rag.RagEngine
import io.shubham0204.startwithsmollm.rag.profiling.Profiler
import io.shubham0204.startwithsmollm.voice.VoiceManager
import io.shubham0204.startwithsmollm.image.ImageQueryProcessor
import android.net.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

// Extension function for formatting doubles
private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

enum class UserRole {
    HUMAN,
    LLM
}

data class ChatMessage(
    val content: String,
    val userRole: UserRole,
    val citations: List<io.shubham0204.startwithsmollm.rag.Citation> = emptyList(),
    val imageUri: Uri? = null,
    val extractedImageText: String? = null
)

enum class ModelLoadingState {
    NOT_LOADED,
    LOADING,
    SUCCESS,
    FAILURE
}

enum class ModelInferenceState {
    IDLE,
    LOADING,
    SUCCESS,
    FAILURE
}

enum class AppScreen {
    MODEL_SELECTION,
    CHAT,
    BENCHMARK,
    RAG,
    RAG_BENCHMARK,
    PERFORMANCE_DASHBOARD
}

data class ChatUIState(
    val messages: ImmutableList<ChatMessage> = emptyList<ChatMessage>().toImmutableList(),
    val modelLoadingState: ModelLoadingState = ModelLoadingState.NOT_LOADED,
    val modelInferenceState: ModelInferenceState = ModelInferenceState.IDLE,
    val currentModelName: String = "",
    val contextUsagePercent: Int = 0,
    val toastMessage: String? = null,
    val ragEnabled: Boolean = false
)

data class RagUiState(
    val documents: List<Document> = emptyList(),
    val stats: RagEngine.RagStats = RagEngine.RagStats(0, 0, 0),
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

data class AppState(
    val currentScreen: AppScreen = AppScreen.MODEL_SELECTION,
    val modelSelectionState: ModelSelectionUiState = ModelSelectionUiState(),
    val chatState: ChatUIState = ChatUIState(),
    val ragState: RagUiState = RagUiState()
)

sealed interface AppEvent {
    data class DownloadModel(val model: ModelInfo) : AppEvent
    data class DeleteModel(val model: ModelInfo) : AppEvent
    data class StartChat(val model: ModelInfo) : AppEvent
    data object BackToModelSelection : AppEvent
    data class SubmitQuery(val query: String) : AppEvent
    data object ClearChat : AppEvent
    data object ClearToast : AppEvent
    data object OpenBenchmark : AppEvent
    data object BackFromBenchmark : AppEvent
    // RAG events
    data object OpenRag : AppEvent
    data object BackFromRag : AppEvent
    data class AddDocument(val uri: Uri) : AppEvent
    data class DeleteDocument(val documentId: String) : AppEvent
    data object DeleteAllDocuments : AppEvent
    data class SetRagEnabled(val enabled: Boolean) : AppEvent
    // Performance dashboard events
    data object OpenPerformanceDashboard : AppEvent
    data object BackFromPerformanceDashboard : AppEvent
    // RAG benchmark events
    data object OpenRagBenchmark : AppEvent
    data object BackFromRagBenchmark : AppEvent
    data class PickRagBenchmarkUri(val uri: Uri) : AppEvent
    data object StartRagBenchmark : AppEvent
    // Image input events
    data class ProcessImageQuery(val imageUri: Uri, val question: String, val preExtractedText: String? = null) : AppEvent
}

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // System prompt used when RAG mode is active. Each RAG query is treated as
        // an independent factual QA against the retrieved context, so we clear the
        // KV cache between queries and re-apply this prompt (mirrors the benchmark).
        private const val RAG_SYSTEM_PROMPT =
            "You are a factual QA assistant. Use ONLY the provided context to answer. " +
            "If the answer is not in the context, say 'Not in context.' " +
            "Be concise (1-3 sentences). Quote specific numbers and terms exactly as they appear in the context. " +
            "Do not invent acronyms, numbers, or facts. Do not repeat yourself."
    }

    private val downloadManager = ModelDownloadManager(application)
    private val deviceProfile: DeviceProfile = DeviceCapabilities.getDeviceProfile(application)
    
    private val _appStateFlow = MutableStateFlow(AppState())
    val appStateFlow: StateFlow<AppState> = _appStateFlow

    private val llamaGPU = LlamaGPU()
    private var summarizerGPU: LlamaGPU? = null  // Secondary tiny model for summarization
    private val ragEngine = RagEngine(application)
    private val voiceManager = VoiceManager(application)
    private val imageQueryProcessor = ImageQueryProcessor(application)
    private val profiler = if (Profiler.isInitialized()) Profiler.getInstance(application) else null
    private var currentModelPath: String = ""
    private var currentModel: ModelInfo? = null
    private var useDualModelSummarization = false  // True for models >1GB
    private var downloadJob: Job? = null
    private var inferenceJob: Job? = null
    private var summarizationJob: Job? = null  // Track summarization job for cancellation
    private var estimatedTokenCount: Int = 0
    private var maxContextSize: Int = deviceProfile.maxContextSize
    
    // Background summarization state
    private var isSummarizing = false
    private var currentSummary: String = ""  // Accumulates over time
    
    // Mutex to prevent concurrent access to llama context
    private val llamaMutex = Mutex()
    private val summarizerMutex = Mutex()  // Separate mutex for summarizer model
    
    // Background summarization - uses main model with tryLock OR dedicated summarizer
    private val summaryBuffer = mutableListOf<String>() // Stores incremental summaries
    private var isBackgroundSummarizing = false
    private val pendingSummarizationQueue = mutableListOf<Pair<String, String>>() // Queue of (userMsg, llmMsg) to summarize
    private var pendingKVRebuild: Pair<String, Int>? = null // (summary, recentMessagesToKeep) - deferred rebuild
    
    // Expert mode - inference insights
    val isExpertMode = ExpertMode.isExpertMode
    private val _inferenceMetrics = MutableStateFlow(InferenceMetrics())
    val inferenceMetrics: StateFlow<InferenceMetrics> = _inferenceMetrics
    
    // Track last inference timing
    private var lastTtftMs: Long = 0
    private var lastTotalTimeMs: Long = 0
    private var lastInputTokens: Int = 0
    private var lastOutputTokens: Int = 0

    init {
        refreshDownloadedModels()
        logDeviceInfo()
        initializeRag()
        initializeVoice()
    }
    
    override fun onCleared() {
        super.onCleared()
        android.util.Log.d("SmolLM", "🧹 ViewModel onCleared - cleaning up...")
        
        // Stop native inference first (before cancelling jobs)
        try {
            llamaGPU.stopInference()
        } catch (e: Exception) {
            android.util.Log.w("SmolLM", "Error stopping inference: ${e.message}")
        }
        
        summarizerGPU?.let {
            try {
                it.stopInference()
            } catch (e: Exception) {
                android.util.Log.w("SmolLM", "Error stopping summarizer: ${e.message}")
            }
        }
        
        // Cancel all jobs
        inferenceJob?.cancel()
        summarizationJob?.cancel()
        downloadJob?.cancel()
        ragBenchmarkJob?.cancel()
        
        // Reset state
        isBackgroundSummarizing = false
        isSummarizing = false
        
        // Cleanup models (stopInference already called, close should be safe)
        try {
            llamaGPU.close()
        } catch (e: Exception) {
            android.util.Log.w("SmolLM", "Error closing main model: ${e.message}")
        }
        
        summarizerGPU?.let {
            try {
                it.close()
            } catch (e: Exception) {
                android.util.Log.w("SmolLM", "Error closing summarizer: ${e.message}")
            }
        }
        
        android.util.Log.d("SmolLM", "✅ ViewModel cleanup complete")
    }
    
    private fun initializeVoice() {
        viewModelScope.launch(Dispatchers.IO) {
            // Initialize TTS immediately (lightweight)
            // Whisper will be loaded on-demand when user first uses voice input
            voiceManager.initialize(loadWhisper = false)
            android.util.Log.d("SmolLM", "Voice TTS initialized")
        }
    }
    
    private fun initializeRag() {
        viewModelScope.launch(Dispatchers.IO) {
            ragEngine.initialize()
            refreshRagState()
        }
    }
    
    private fun refreshRagState() {
        val documents = ragEngine.getDocuments()
        val stats = ragEngine.getStats()
        _appStateFlow.update { state ->
            state.copy(
                ragState = state.ragState.copy(
                    documents = documents,
                    stats = stats
                )
            )
        }
    }
    
    private fun logDeviceInfo() {
        android.util.Log.d("SmolLM", "Device Profile: RAM=${deviceProfile.totalRamGB}GB, " +
            "Cores=${deviceProfile.availableCores}, Tier=${deviceProfile.deviceTier}, " +
            "Threads=${deviceProfile.optimalThreads}, MaxContext=${deviceProfile.maxContextSize}")
    }

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.DownloadModel -> downloadModel(event.model)
            is AppEvent.DeleteModel -> deleteModel(event.model)
            is AppEvent.StartChat -> startChat(event.model)
            is AppEvent.BackToModelSelection -> backToModelSelection()
            is AppEvent.SubmitQuery -> submitQuery(event.query)
            is AppEvent.ClearChat -> clearChat()
            is AppEvent.ClearToast -> clearToast()
            is AppEvent.OpenBenchmark -> openBenchmark()
            is AppEvent.BackFromBenchmark -> backFromBenchmark()
            // RAG events
            is AppEvent.OpenRag -> openRag()
            is AppEvent.BackFromRag -> backFromRag()
            is AppEvent.AddDocument -> addDocument(event.uri)
            is AppEvent.DeleteDocument -> deleteDocument(event.documentId)
            is AppEvent.DeleteAllDocuments -> deleteAllDocuments()
            is AppEvent.SetRagEnabled -> setRagEnabled(event.enabled)
            // Performance dashboard events
            is AppEvent.OpenPerformanceDashboard -> openPerformanceDashboard()
            is AppEvent.BackFromPerformanceDashboard -> backFromPerformanceDashboard()
            // RAG benchmark
            is AppEvent.OpenRagBenchmark -> openRagBenchmark()
            is AppEvent.BackFromRagBenchmark -> backFromRagBenchmark()
            is AppEvent.PickRagBenchmarkUri -> pickRagBenchmarkUri(event.uri)
            is AppEvent.StartRagBenchmark -> startRagBenchmark()
            // Image input events
            is AppEvent.ProcessImageQuery -> processImageQuery(event.imageUri, event.question, event.preExtractedText)
        }
    }
    
    private fun processImageQuery(imageUri: Uri, question: String, preExtractedText: String? = null) {
        // Cancel any existing inference
        inferenceJob?.cancel()
        
        // Use Dispatchers.Default to match processQuery (CPU-bound for native llama.cpp calls)
        inferenceJob = viewModelScope.launch(Dispatchers.Default) {
            android.util.Log.d("SmolLM", "")
            android.util.Log.d("SmolLM", "╔═══════════════════════════════════════════════════════════════╗")
            android.util.Log.d("SmolLM", "║           📸 IMAGE QUERY STARTED                              ║")
            android.util.Log.d("SmolLM", "╚═══════════════════════════════════════════════════════════════╝")
            android.util.Log.d("SmolLM", "Image URI: $imageUri")
            android.util.Log.d("SmolLM", "Question: $question")
            android.util.Log.d("SmolLM", "Pre-extracted text: ${if (preExtractedText != null) "YES (${preExtractedText.length} chars) - SKIPPING OCR" else "NO"}")
            
            // Show loading state with user's image message
            withContext(Dispatchers.Main) {
                _appStateFlow.update { state ->
                    state.copy(
                        chatState = state.chatState.copy(
                            messages = state.chatState.messages.addChatMessage(
                                ChatMessage(
                                    content = question.ifBlank { "What's in this image?" },
                                    userRole = UserRole.HUMAN,
                                    imageUri = imageUri,
                                    extractedImageText = preExtractedText
                                )
                            ),
                            modelInferenceState = ModelInferenceState.LOADING
                        )
                    )
                }
            }
            
            // Fast path: if OCR was already run in preview dialog, skip it entirely
            if (!preExtractedText.isNullOrBlank()) {
                val prompt = imageQueryProcessor.buildPromptFromText(preExtractedText, question)
                android.util.Log.d("SmolLM", "⚡ Fast path: prompt built directly from pre-extracted text (${prompt.length} chars)")
                runImageInference(prompt, imageUri)
                return@launch
            }
            
            // Slow path: run OCR now (fallback if pre-extraction wasn't done)
            when (val result = imageQueryProcessor.processImageQuery(imageUri, question)) {
                is ImageQueryProcessor.ProcessResult.Success -> {
                    android.util.Log.d("SmolLM", "✅ OCR Success: ${result.extractedText.length} chars")
                    android.util.Log.d("SmolLM", "📝 Sending augmented prompt to LLM...")
                    
                    // Update the user message with extracted text
                    withContext(Dispatchers.Main) {
                        _appStateFlow.update { state ->
                            val messages = state.chatState.messages.toMutableList()
                            if (messages.isNotEmpty()) {
                                val lastMsg = messages.last()
                                if (lastMsg.userRole == UserRole.HUMAN && lastMsg.imageUri == imageUri) {
                                    messages[messages.lastIndex] = lastMsg.copy(
                                        extractedImageText = result.extractedText
                                    )
                                }
                            }
                            state.copy(
                                chatState = state.chatState.copy(
                                    messages = messages.toImmutableList()
                                )
                            )
                        }
                    }
                    
                    // Now run inference with the augmented prompt
                    runImageInference(result.augmentedPrompt, imageUri)
                }
                
                is ImageQueryProcessor.ProcessResult.NoTextFound -> {
                    android.util.Log.d("SmolLM", "⚠️ No text found in image")
                    withContext(Dispatchers.Main) {
                        _appStateFlow.update { state ->
                            state.copy(
                                chatState = state.chatState.copy(
                                    messages = state.chatState.messages.addChatMessage(
                                        ChatMessage(
                                            content = "I couldn't find any text in this image. The image might not contain readable text, or it may be too blurry.",
                                            userRole = UserRole.LLM
                                        )
                                    ),
                                    modelInferenceState = ModelInferenceState.IDLE,
                                    toastMessage = "No text found in image"
                                )
                            )
                        }
                    }
                }
                
                is ImageQueryProcessor.ProcessResult.Error -> {
                    android.util.Log.e("SmolLM", "❌ Image processing error: ${result.message}")
                    withContext(Dispatchers.Main) {
                        _appStateFlow.update { state ->
                            state.copy(
                                chatState = state.chatState.copy(
                                    messages = state.chatState.messages.addChatMessage(
                                        ChatMessage(
                                            content = "Sorry, I couldn't process this image: ${result.message}",
                                            userRole = UserRole.LLM
                                        )
                                    ),
                                    modelInferenceState = ModelInferenceState.IDLE,
                                    toastMessage = "Image processing failed"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun runImageInference(augmentedPrompt: String, imageUri: Uri) {
        val queryStartTime = System.currentTimeMillis()
        var ttft: Long? = null
        var lastTokenTime: Long = queryStartTime
        val itlTimes = mutableListOf<Long>()
        var tokenCount = 0
        val responseBuilder = StringBuilder()
        
        // 🔍 DIAGNOSTICS: Log prompt size — critical for understanding slow prefill
        val promptChars = augmentedPrompt.length
        val estimatedPromptTokens = promptChars / 4
        android.util.Log.d("SmolLM", "📏 IMAGE PROMPT SIZE: $promptChars chars (~$estimatedPromptTokens tokens)")
        if (estimatedPromptTokens > 500) {
            android.util.Log.w("SmolLM", "⚠️ LARGE PROMPT WARNING: ~$estimatedPromptTokens tokens may cause slow prefill!")
        }
        
        // Create placeholder message for streaming
        withContext(Dispatchers.Main) {
            _appStateFlow.update { state ->
                state.copy(
                    chatState = state.chatState.copy(
                        messages = state.chatState.messages.addChatMessage(
                            ChatMessage(content = "", userRole = UserRole.LLM)
                        )
                    )
                )
            }
        }
        
        try {
            // 🔍 DIAGNOSTICS: Check mutex state before locking
            val mutexWaitStart = System.currentTimeMillis()
            android.util.Log.d("SmolLM", "🔒 Mutex status: ${if (llamaMutex.isLocked) "LOCKED (will wait)" else "UNLOCKED"}")
            
            llamaMutex.withLock {
                val mutexWaitTime = System.currentTimeMillis() - mutexWaitStart
                if (mutexWaitTime > 100) {
                    android.util.Log.w("SmolLM", "⚠️ Mutex wait time: ${mutexWaitTime}ms (background work was running!)")
                } else {
                    android.util.Log.d("SmolLM", "✅ Mutex acquired in ${mutexWaitTime}ms")
                }
                
                // 🔍 DIAGNOSTICS + SAFETY: Check context length before inference
                val currentContextUsed = try { llamaGPU.getContextLengthUsed() } catch (e: Exception) { 0 }
                val projectedUsage = currentContextUsed + estimatedPromptTokens + 500
                android.util.Log.d("SmolLM", "📊 Context: $currentContextUsed used + ~$estimatedPromptTokens new = ~$projectedUsage / $maxContextSize")
                
                if (projectedUsage > maxContextSize * 0.95) {
                    android.util.Log.w("SmolLM", "⚠️ Context near limit, clearing KV cache to prevent slow prefill / crash")
                    llamaGPU.clearChat()
                    if (currentModel?.id?.contains("gemma") == false) {
                        llamaGPU.addSystemPrompt("You are a helpful and intelligent AI assistant. Answer questions clearly and concisely.")
                    }
                    estimatedTokenCount = 50
                }
                
                val llmStartTime = System.currentTimeMillis()
                android.util.Log.d("SmolLM", "🤖 Starting LLM inference for image query...")
                
                llamaGPU.getResponseAsFlow(augmentedPrompt).collect { piece ->
                    val now = System.currentTimeMillis()
                    if (ttft == null) {
                        ttft = now - llmStartTime
                        val totalTTFT = now - queryStartTime
                        android.util.Log.d("SmolLM", "⚡ First token received!")
                        android.util.Log.d("SmolLM", "   - Pure LLM TTFT: ${ttft}ms")
                        android.util.Log.d("SmolLM", "   - Total TTFT (with overhead): ${totalTTFT}ms")
                        android.util.Log.d("SmolLM", "   - Breakdown: Mutex=${mutexWaitTime}ms, LLM=${ttft}ms")
                    } else {
                        itlTimes.add(now - lastTokenTime)
                    }
                    lastTokenTime = now
                    tokenCount++
                    responseBuilder.append(piece)
                    
                    // Update UI with streaming response
                    withContext(Dispatchers.Main) {
                        _appStateFlow.update { state ->
                            val messages = state.chatState.messages.toMutableList()
                            if (messages.isNotEmpty() && messages.last().userRole == UserRole.LLM) {
                                messages[messages.lastIndex] = messages.last().copy(
                                    content = responseBuilder.toString()
                                )
                            }
                            state.copy(
                                chatState = state.chatState.copy(
                                    messages = messages.toImmutableList()
                                )
                            )
                        }
                    }
                }
            }
            
            val totalTime = System.currentTimeMillis() - queryStartTime
            val avgItl = if (itlTimes.isNotEmpty()) itlTimes.average() else 0.0
            val tokensPerSec = if (totalTime > 0) tokenCount / (totalTime / 1000.0) else 0.0
            
            android.util.Log.d("SmolLM", "✅ Image query complete: $tokenCount tokens in ${totalTime}ms")
            android.util.Log.d("SmolLM", "   TTFT: ${ttft}ms, Avg ITL: ${"%.1f".format(avgItl)}ms, Speed: ${"%.1f".format(tokensPerSec)} tok/s")
            
            withContext(Dispatchers.Main) {
                _appStateFlow.update { state ->
                    state.copy(
                        chatState = state.chatState.copy(
                            modelInferenceState = ModelInferenceState.IDLE,
                            contextUsagePercent = calculateContextUsage()
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SmolLM", "❌ Image inference failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _appStateFlow.update { state ->
                    val messages = state.chatState.messages.toMutableList()
                    if (messages.isNotEmpty() && messages.last().userRole == UserRole.LLM && messages.last().content.isBlank()) {
                        messages[messages.lastIndex] = messages.last().copy(
                            content = "Sorry, I encountered an error processing your image query."
                        )
                    }
                    state.copy(
                        chatState = state.chatState.copy(
                            messages = messages.toImmutableList(),
                            modelInferenceState = ModelInferenceState.IDLE
                        )
                    )
                }
            }
        }
    }
    
    private fun clearToast() {
        _appStateFlow.update { state ->
            state.copy(
                chatState = state.chatState.copy(toastMessage = null)
            )
        }
    }
    
    private fun showContextTrimmedMessage() {
        _appStateFlow.update { state ->
            state.copy(
                chatState = state.chatState.copy(
                    toastMessage = "Cleared old messages to continue conversation"
                )
            )
        }
    }
    
    private fun clearChat() {
        estimatedTokenCount = 50 // Reset to system prompt size
        // Reset ALL summarization state so old session summaries don't leak into new chat
        resetSummarizationState()
        _appStateFlow.update { state ->
            state.copy(
                chatState = state.chatState.copy(
                    messages = emptyList<ChatMessage>().toImmutableList(),
                    contextUsagePercent = calculateContextUsage()
                )
            )
        }
        // Reload model to clear internal chat history
        currentModel?.let { loadModel(it) }
    }

    private fun resetSummarizationState() {
        currentSummary = ""
        summaryBuffer.clear()
        synchronized(pendingSummarizationQueue) { pendingSummarizationQueue.clear() }
        pendingKVRebuild = null
        android.util.Log.d("SmolLM", "🧹 Cleared all summarization state (summary, buffer, queue, pending rebuild)")
    }
    
    private fun calculateContextUsage(): Int {
        // Use REAL token count from llama.cpp instead of estimation!
        return try {
            val actualTokensUsed = llamaGPU.getContextLengthUsed()
            ((actualTokensUsed.toFloat() / maxContextSize) * 100).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            // Fallback to estimation if model not loaded
            ((estimatedTokenCount.toFloat() / maxContextSize) * 100).toInt().coerceIn(0, 100)
        }
    }
    
    private fun getRealContextUsage(): Int {
        return try {
            llamaGPU.getContextLengthUsed()
        } catch (e: Exception) {
            estimatedTokenCount
        }
    }
    
    private fun estimateTokens(text: String): Int {
        // Fallback estimate when model not loaded
        return ((text.length / 2.5) + 30).toInt().coerceAtLeast(1)
    }
    
    // ==================== Voice Functions ====================
    
    /** Get voice state flow for UI */
    val voiceState = voiceManager.state
    
    /** Start voice input - loads Whisper if needed, then starts recording */
    fun startVoiceInput() {
        viewModelScope.launch {
            // Load Whisper on first use
            if (!voiceManager.state.value.isWhisperLoaded) {
                android.util.Log.d("SmolLM", "Loading Whisper model for first voice input...")
                val loaded = voiceManager.loadWhisperIfNeeded()
                if (!loaded) {
                    android.util.Log.e("SmolLM", "Failed to load Whisper model")
                    return@launch
                }
            }
            
            voiceManager.startListening()
        }
    }
    
    /** Stop voice input and transcribe, then submit to LLM */
    fun stopVoiceInputAndSubmit() {
        viewModelScope.launch {
            val transcription = voiceManager.stopListeningAndTranscribe()
            if (transcription.isNotBlank()) {
                android.util.Log.d("SmolLM", "Voice transcription: $transcription")
                submitQuery(transcription)
            }
        }
    }
    
    /** Cancel voice input without submitting */
    fun cancelVoiceInput() {
        voiceManager.cancelListening()
    }
    
    /** Speak text using TTS */
    fun speakText(text: String) {
        voiceManager.speak(text)
    }
    
    /** Stop TTS */
    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }
    
    /** Stop ongoing inference generation */
    fun stopGeneration() {
        inferenceJob?.cancel()
        inferenceJob = null
        _appStateFlow.update { state ->
            state.copy(
                chatState = state.chatState.copy(
                    modelInferenceState = ModelInferenceState.IDLE
                )
            )
        }
    }
    
    /** Toggle auto-speak for LLM responses */
    fun toggleAutoSpeak() {
        voiceManager.toggleAutoSpeak()
    }
    
    /** Toggle voice features on/off */
    fun toggleVoice() {
        voiceManager.toggleVoice()
    }
    
    // ==================== Expert Mode Functions ====================
    
    /** Handle tap on model name (7 taps to enable expert mode) */
    fun onModelNameTap(): Boolean {
        val activated = ExpertMode.onTriggerTap()
        if (activated) {
            addSystemMessage("🔓 Expert Mode enabled! Tap ⚡ to view Inference Insights.")
        }
        return activated
    }
    
    /** Get remaining taps for expert mode activation */
    fun getExpertModeTapsRemaining(): Int = ExpertMode.getRemainingTaps()
    
    /** Update inference metrics after each inference */
    private fun updateInferenceMetrics(
        inputTokens: Int,
        outputTokens: Int,
        ttftMs: Long,
        totalTimeMs: Long,
        tokensPerSecond: Float,
        contextUsed: Int,
        ramUsedMB: Int,
        avgItlMs: Float
    ) {
        val contextPercent = ((contextUsed.toFloat() / maxContextSize) * 100).toInt()
        
        // Estimate KV cache size (rough approximation)
        // KV cache ≈ 2 * num_layers * context_size * hidden_dim * 2 bytes (fp16)
        // For Qwen 1.5B: ~28 layers, 1536 hidden dim
        val kvCacheMB = (contextUsed * 28 * 1536 * 2 * 2 / 1024 / 1024).coerceAtLeast(1)
        
        _inferenceMetrics.value = InferenceMetrics(
            modelName = currentModel?.name ?: "",
            modelSize = currentModel?.parameters ?: "",
            quantization = currentModel?.quantization ?: "Q4_K_M",
            contextSize = maxContextSize,
            threads = deviceProfile.optimalThreads,
            flashAttention = true,
            kvCacheType = "Q8_0",
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            ttftMs = ttftMs,
            totalTimeMs = totalTimeMs,
            tokensPerSecond = tokensPerSecond,
            contextUsed = contextUsed,
            contextPercent = contextPercent,
            ramUsedMB = ramUsedMB,
            kvCacheMB = kvCacheMB,
            avgItlMs = avgItlMs,
            prefillTimeMs = ttftMs,
            decodeTimeMs = totalTimeMs - ttftMs
        )
    }

    private fun refreshDownloadedModels() {
        val downloadedIds = AvailableModels.models
            .filter { downloadManager.isModelDownloaded(it.fileName) }
            .map { it.id }
            .toSet()
        
        _appStateFlow.update { state ->
            state.copy(
                modelSelectionState = state.modelSelectionState.copy(
                    downloadedModelIds = downloadedIds,
                    deviceProfile = deviceProfile
                )
            )
        }
    }

    private fun downloadModel(model: ModelInfo) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _appStateFlow.update { state ->
                state.copy(
                    modelSelectionState = state.modelSelectionState.copy(
                        downloadingModelId = model.id,
                        downloadState = DownloadState.Downloading(0, 0, model.sizeInMB)
                    )
                )
            }
            
            downloadManager.downloadModel(model).collect { downloadState ->
                _appStateFlow.update { state ->
                    state.copy(
                        modelSelectionState = state.modelSelectionState.copy(
                            downloadState = downloadState
                        )
                    )
                }
                
                if (downloadState is DownloadState.Completed) {
                    _appStateFlow.update { state ->
                        state.copy(
                            modelSelectionState = state.modelSelectionState.copy(
                                downloadingModelId = null,
                                downloadState = DownloadState.Idle,
                                downloadedModelIds = state.modelSelectionState.downloadedModelIds + model.id
                            )
                        )
                    }
                } else if (downloadState is DownloadState.Failed) {
                    // Keep error visible for a moment, then reset
                    kotlinx.coroutines.delay(3000)
                    _appStateFlow.update { state ->
                        state.copy(
                            modelSelectionState = state.modelSelectionState.copy(
                                downloadingModelId = null,
                                downloadState = DownloadState.Idle
                            )
                        )
                    }
                }
            }
        }
    }

    private fun deleteModel(model: ModelInfo) {
        if (downloadManager.deleteModel(model.fileName)) {
            _appStateFlow.update { state ->
                state.copy(
                    modelSelectionState = state.modelSelectionState.copy(
                        downloadedModelIds = state.modelSelectionState.downloadedModelIds - model.id
                    )
                )
            }
        }
    }

    private fun startChat(model: ModelInfo) {
        currentModelPath = downloadManager.getModelPath(model.fileName)
        currentModel = model
        // Fresh session: clear any leftover summary state from a previous model/session
        resetSummarizationState()
        estimatedTokenCount = 50
        
        // Disable RAG if model doesn't support it
        val ragEnabled = if (model.supportsRag) _appStateFlow.value.chatState.ragEnabled else false
        
        _appStateFlow.update { state ->
            state.copy(
                currentScreen = AppScreen.CHAT,
                chatState = ChatUIState(
                    modelLoadingState = ModelLoadingState.LOADING,
                    currentModelName = model.name,
                    ragEnabled = ragEnabled
                )
            )
        }
        loadModel(model)
    }

    private fun backToModelSelection() {
        // Cancel all ongoing jobs to prevent crashes
        android.util.Log.d("SmolLM", "🔙 Back to model selection - stopping inference and cancelling jobs...")
        
        // Cancel the coroutine jobs first
        inferenceJob?.cancel()
        inferenceJob = null
        
        summarizationJob?.cancel()
        summarizationJob = null
        
        // Reset summarization state
        isBackgroundSummarizing = false
        isSummarizing = false
        synchronized(pendingSummarizationQueue) {
            pendingSummarizationQueue.clear()
        }
        
        // Stop inference and close models on IO thread to avoid blocking UI
        // The close() method will wait for inference to actually stop
        viewModelScope.launch(Dispatchers.IO) {
            // Stop and wait for inference to finish before closing
            android.util.Log.d("SmolLM", "⏳ Waiting for inference to stop...")
            val mainStopped = llamaGPU.stopInferenceAndWait(2000)
            if (!mainStopped) {
                android.util.Log.w("SmolLM", "⚠️ Main model inference didn't stop in time")
            }
            
            summarizerGPU?.let {
                val summarizerStopped = it.stopInferenceAndWait(1000)
                if (!summarizerStopped) {
                    android.util.Log.w("SmolLM", "⚠️ Summarizer inference didn't stop in time")
                }
            }
            
            // Now safe to close the models
            try {
                llamaGPU.close()
                android.util.Log.d("SmolLM", "✅ Main model closed")
            } catch (e: Exception) {
                android.util.Log.w("SmolLM", "Error closing model: ${e.message}")
            }
            
            // Close summarizer if loaded
            summarizerGPU?.let {
                try {
                    it.close()
                    android.util.Log.d("SmolLM", "✅ Summarizer model closed")
                } catch (e: Exception) {
                    android.util.Log.w("SmolLM", "Error closing summarizer: ${e.message}")
                }
                summarizerGPU = null
            }
            
            android.util.Log.d("SmolLM", "✅ All jobs cancelled, models closed")
        }
        
        _appStateFlow.update { state ->
            state.copy(
                currentScreen = AppScreen.MODEL_SELECTION,
                chatState = ChatUIState()
            )
        }
    }
    
    private fun openBenchmark() {
        _appStateFlow.update { state ->
            state.copy(currentScreen = AppScreen.BENCHMARK)
        }
    }
    
    private fun backFromBenchmark() {
        _appStateFlow.update { state ->
            // Go back to chat if model is loaded, otherwise go to model selection
            val targetScreen = if (state.chatState.modelLoadingState == ModelLoadingState.SUCCESS) {
                AppScreen.CHAT
            } else {
                AppScreen.MODEL_SELECTION
            }
            state.copy(currentScreen = targetScreen)
        }
    }
    
    // RAG Functions
    private fun openRag() {
        refreshRagState()
        _appStateFlow.update { state ->
            state.copy(currentScreen = AppScreen.RAG)
        }
    }
    
    private fun backFromRag() {
        _appStateFlow.update { state ->
            state.copy(currentScreen = AppScreen.CHAT)
        }
    }
    
    private fun openPerformanceDashboard() {
        _appStateFlow.update { state ->
            state.copy(currentScreen = AppScreen.PERFORMANCE_DASHBOARD)
        }
    }
    
    private fun backFromPerformanceDashboard() {
        _appStateFlow.update { state ->
            state.copy(currentScreen = AppScreen.CHAT)
        }
    }

    // ----- RAG Benchmark ---------------------------------------------------
    private val _ragBenchmarkState = MutableStateFlow(RagBenchmarkUiState())
    val ragBenchmarkState: StateFlow<RagBenchmarkUiState> = _ragBenchmarkState
    private var ragBenchmarkJob: Job? = null

    private fun openRagBenchmark() {
        _appStateFlow.update { it.copy(currentScreen = AppScreen.RAG_BENCHMARK) }
    }

    private fun backFromRagBenchmark() {
        if (_ragBenchmarkState.value.isRunning) {
            android.util.Log.d("SmolLM", "⏹ Cancelling RAG benchmark")
            ragBenchmarkJob?.cancel()
        }
        _appStateFlow.update {
            it.copy(currentScreen = AppScreen.RAG)
        }
    }

    private fun pickRagBenchmarkUri(uri: Uri) {
        _ragBenchmarkState.update { it.copy(pickedUri = uri, error = null) }
    }

    private fun startRagBenchmark() {
        val uri = _ragBenchmarkState.value.pickedUri
        if (uri == null) {
            _ragBenchmarkState.update { it.copy(error = "Pick a PDF first.") }
            return
        }
        if (_ragBenchmarkState.value.isRunning) return

        // Free the main model & summarizer so the benchmark has full RAM/GPU access.
        try {
            llamaGPU.close()
            summarizerGPU?.let { try { it.close() } catch (_: Exception) {} }
            summarizerGPU = null
            android.util.Log.d("SmolLM", "🔧 Closed main model(s) before RAG benchmark")
        } catch (e: Exception) {
            android.util.Log.w("SmolLM", "Close before benchmark failed: ${e.message}")
        }
        _appStateFlow.update {
            it.copy(chatState = it.chatState.copy(modelLoadingState = ModelLoadingState.NOT_LOADED))
        }

        _ragBenchmarkState.update {
            RagBenchmarkUiState(
                isRunning = true,
                status = "Starting...",
                logLines = emptyList(),
                pickedUri = uri
            )
        }

        val runner = RagBenchmarkRunner(getApplication())
        ragBenchmarkJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                runner.run(uri, ragEngine).collect { upd ->
                    when (upd) {
                        is RagBenchmarkRunner.BenchmarkUpdate.Log ->
                            _ragBenchmarkState.update { s -> s.copy(logLines = s.logLines + upd.line) }
                        is RagBenchmarkRunner.BenchmarkUpdate.Status ->
                            _ragBenchmarkState.update { s -> s.copy(status = upd.message) }
                        is RagBenchmarkRunner.BenchmarkUpdate.ModelStarted ->
                            _ragBenchmarkState.update { s ->
                                s.copy(
                                    status = "Model ${upd.index}/${upd.total}: ${upd.modelName}",
                                    currentModelIdx = upd.index,
                                    totalModels = upd.total,
                                    currentQuestionIdx = 0
                                )
                            }
                        is RagBenchmarkRunner.BenchmarkUpdate.QuestionStarted ->
                            _ragBenchmarkState.update { s ->
                                s.copy(
                                    status = "Q${upd.index}/${upd.total}: ${upd.qid}",
                                    currentQuestionIdx = upd.index,
                                    totalQuestions = upd.total
                                )
                            }
                        is RagBenchmarkRunner.BenchmarkUpdate.QuestionFinished -> { /* aggregated in ModelFinished */ }
                        is RagBenchmarkRunner.BenchmarkUpdate.ModelFinished ->
                            _ragBenchmarkState.update { s -> s.copy(modelResults = s.modelResults + upd.result) }
                        is RagBenchmarkRunner.BenchmarkUpdate.Done ->
                            _ragBenchmarkState.update { s ->
                                s.copy(
                                    isRunning = false,
                                    status = "Done. ${upd.results.size} models benchmarked.",
                                    reportPath = upd.reportPath,
                                    modelResults = upd.results
                                )
                            }
                        is RagBenchmarkRunner.BenchmarkUpdate.Failed ->
                            _ragBenchmarkState.update { s ->
                                s.copy(isRunning = false, status = "Failed", error = upd.message)
                            }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SmolLM", "RAG benchmark crashed", e)
                _ragBenchmarkState.update {
                    it.copy(isRunning = false, status = "Failed", error = e.message ?: "unknown")
                }
            }
        }
    }
    
    private fun addDocument(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _appStateFlow.update { state ->
                state.copy(ragState = state.ragState.copy(isProcessing = true))
            }
            
            when (val result = ragEngine.addDocument(uri)) {
                is RagEngine.AddDocumentResult.Success -> {
                    refreshRagState()
                    withContext(Dispatchers.Main) {
                        _appStateFlow.update { state ->
                            state.copy(
                                ragState = state.ragState.copy(isProcessing = false),
                                chatState = state.chatState.copy(
                                    toastMessage = "Added: ${result.document.name}"
                                )
                            )
                        }
                    }
                }
                is RagEngine.AddDocumentResult.Error -> {
                    withContext(Dispatchers.Main) {
                        _appStateFlow.update { state ->
                            state.copy(
                                ragState = state.ragState.copy(
                                    isProcessing = false,
                                    errorMessage = result.message
                                ),
                                chatState = state.chatState.copy(
                                    toastMessage = "Error: ${result.message}"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun deleteDocument(documentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ragEngine.deleteDocument(documentId)
            refreshRagState()
            
            // Disable RAG if no documents left
            if (!ragEngine.hasDocuments()) {
                withContext(Dispatchers.Main) {
                    _appStateFlow.update { state ->
                        state.copy(
                            chatState = state.chatState.copy(ragEnabled = false)
                        )
                    }
                }
            }
        }
    }
    
    private fun deleteAllDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            ragEngine.deleteAllDocuments()
            refreshRagState()
            
            withContext(Dispatchers.Main) {
                _appStateFlow.update { state ->
                    state.copy(
                        chatState = state.chatState.copy(
                            ragEnabled = false,
                            toastMessage = "All documents deleted"
                        )
                    )
                }
            }
        }
    }
    
    private fun setRagEnabled(enabled: Boolean) {
        // Check if current model supports RAG
        if (enabled && currentModel?.supportsRag == false) {
            _appStateFlow.update { state ->
                state.copy(
                    chatState = state.chatState.copy(
                        toastMessage = "This model doesn't support RAG. Use a larger model (0.5B+)."
                    )
                )
            }
            return
        }
        
        _appStateFlow.update { state ->
            state.copy(
                chatState = state.chatState.copy(ragEnabled = enabled)
            )
        }
    }
    
    fun getCurrentModelPath(): String? {
        return if (currentModelPath.isNotEmpty()) currentModelPath else null
    }

    private fun submitQuery(query: String) {
        // Check for expert mode secret phrases
        val expertResult = ExpertMode.checkSecretPhrase(query)
        if (expertResult != null) {
            val message = when (expertResult) {
                "enabled" -> "🔓 Expert Mode enabled! Inference Insights now available."
                "disabled" -> "🔒 Expert Mode disabled. Inference Insights hidden."
                else -> return
            }
            // Add system message instead of processing as query
            addSystemMessage(message)
            return
        }
        
        processQuery(query)
    }
    
    private fun addSystemMessage(message: String) {
        _appStateFlow.update { state ->
            val newMessage = ChatMessage(
                content = message,
                userRole = UserRole.LLM
            )
            state.copy(
                chatState = state.chatState.copy(
                    messages = (state.chatState.messages + newMessage).toImmutableList()
                )
            )
        }
    }
    
    private fun processQuery(query: String) {
        // Cancel any existing inference
        inferenceJob?.cancel()
        
        // For single-turn models (like Gemma), reset context each query
        if (currentModel?.supportsMultiTurn == false) {
            estimatedTokenCount = estimateTokens(query)
        } else {
            estimatedTokenCount += estimateTokens(query)
        }
        
        _appStateFlow.update { state ->
            state.copy(
                chatState = state.chatState.copy(
                    messages = state.chatState.messages.addChatMessage(
                        ChatMessage(content = query, userRole = UserRole.HUMAN)
                    ),
                    modelInferenceState = ModelInferenceState.LOADING,
                    contextUsagePercent = calculateContextUsage()
                )
            )
        }
        
        inferenceJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val queryStartTime = System.currentTimeMillis()
                android.util.Log.d("SmolLM", "")
                android.util.Log.d("SmolLM", "╔═══════════════════════════════════════════════════════════════╗")
                android.util.Log.d("SmolLM", "║           🚀 QUERY PROCESSING STARTED                         ║")
                android.util.Log.d("SmolLM", "╚═══════════════════════════════════════════════════════════════╝")
                
                // Check REAL context usage from llama.cpp - trim at 70% to leave room for response
                val realUsage = calculateContextUsage()
                val currentMessages = _appStateFlow.value.chatState.messages
                
                android.util.Log.d("SmolLM", "📊 Context before query: $realUsage% (${getRealContextUsage()}/${maxContextSize} tokens)")
                android.util.Log.d("SmolLM", "📝 Total messages: ${currentMessages.size}")
                android.util.Log.d("SmolLM", "🔒 Mutex status: ${if (llamaMutex.isLocked) "LOCKED (summarization in progress?)" else "UNLOCKED"}")
                
                // Check if RAG is enabled and use augmented prompt
                val ragStartTime = System.currentTimeMillis()
                val ragEnabled = _appStateFlow.value.chatState.ragEnabled
                var citations: List<io.shubham0204.startwithsmollm.rag.Citation> = emptyList()
                
                val finalQuery = if (ragEnabled && ragEngine.hasDocuments()) {
                    android.util.Log.d("SmolLM", "🔍 RAG retrieval starting...")
                    val ragResult = ragEngine.query(query)
                    val ragTime = System.currentTimeMillis() - ragStartTime
                    android.util.Log.d("SmolLM", "✅ RAG: Found ${ragResult.retrievedChunks.size} relevant chunks in ${ragTime}ms")
                    citations = ragResult.citations
                    
                    // DEBUG: Log prompt size (critical for understanding slow inference)
                    val promptChars = ragResult.augmentedPrompt.length
                    val estimatedTokens = promptChars / 4  // ~4 chars per token
                    android.util.Log.d("SmolLM", "📏 RAG PROMPT SIZE: $promptChars chars (~$estimatedTokens tokens)")
                    if (estimatedTokens > 500) {
                        android.util.Log.w("SmolLM", "⚠️ LARGE PROMPT WARNING: ${estimatedTokens} tokens may cause slow prefill!")
                    }
                    
                    ragResult.augmentedPrompt
                } else {
                    android.util.Log.d("SmolLM", "⏭️ RAG disabled or no documents")
                    query
                }
                
                // Track streaming metrics (initialize before LLM starts)
                var ttft: Long? = null
                var lastTokenTime: Long = 0
                val itlTimes = mutableListOf<Long>()
                var tokenCount = 0
                val responseBuilder = StringBuilder()
                
                // Track RAM before inference
                val runtime = Runtime.getRuntime()
                val ramBeforeMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                
                // Create placeholder message for streaming
                val streamingMessageId = withContext(Dispatchers.Main) {
                    _appStateFlow.update { state ->
                        state.copy(
                            chatState = state.chatState.copy(
                                messages = state.chatState.messages.addChatMessage(
                                    ChatMessage(
                                        content = "", 
                                        userRole = UserRole.LLM,
                                        citations = citations
                                    )
                                )
                            )
                        )
                    }
                    _appStateFlow.value.chatState.messages.size - 1
                }
                
                // Start timing right before LLM inference
                val inferenceStart = System.currentTimeMillis()
                lastTokenTime = inferenceStart
                
                val preInferenceTime = inferenceStart - queryStartTime
                android.util.Log.d("SmolLM", "⏱️ Pre-inference overhead: ${preInferenceTime}ms (RAG + setup)")
                android.util.Log.d("SmolLM", "")
                
                // Acquire mutex to prevent concurrent llama access (e.g., from summarization)
                val mutexWaitStart = System.currentTimeMillis()
                android.util.Log.d("SmolLM", "🔒 Waiting for mutex...")
                llamaMutex.withLock {
                val mutexWaitTime = System.currentTimeMillis() - mutexWaitStart
                if (mutexWaitTime > 100) {
                    android.util.Log.d("SmolLM", "⚠️ Mutex wait time: ${mutexWaitTime}ms (background summarization was running!)")
                } else {
                    android.util.Log.d("SmolLM", "✅ Mutex acquired in ${mutexWaitTime}ms")
                }
                
                // Execute pending KV rebuild if any (deferred from rolling summarization)
                pendingKVRebuild?.let { (summary, recentMessagesToKeep) ->
                    try {
                        android.util.Log.d("SmolLM", "🔧 Executing deferred KV rebuild...")
                        val rebuildStart = System.currentTimeMillis()
                        llamaGPU.rebuildCacheWithSummary(summary, recentMessagesToKeep)
                        val rebuildTime = System.currentTimeMillis() - rebuildStart
                        android.util.Log.d("SmolLM", "✅ KV cache rebuilt in ${rebuildTime}ms")
                    } catch (e: Exception) {
                        android.util.Log.e("SmolLM", "❌ KV rebuild failed, clearing cache: ${e.message}")
                        llamaGPU.clearChat()
                        if (currentModel?.id?.contains("gemma") == false) {
                            llamaGPU.addSystemPrompt("You are a helpful and intelligent AI assistant. Answer questions clearly and concisely.")
                        }
                    }
                    pendingKVRebuild = null
                }
                
                // RAG mode: each query is independent. Clear the KV cache and re-apply
                // the RAG system prompt to prevent context overflow across queries.
                // (Mirrors the approach proven in RagBenchmarkRunner.)
                if (ragEnabled && ragEngine.hasDocuments()) {
                    try {
                        android.util.Log.d("SmolLM", "🧹 RAG mode: clearing KV cache for independent query")
                        llamaGPU.clearChat()
                        if (currentModel?.id?.contains("gemma") == false) {
                            llamaGPU.addSystemPrompt(RAG_SYSTEM_PROMPT)
                        }
                        // Reset accounting so context-usage UI reflects fresh state
                        estimatedTokenCount = 50
                    } catch (e: Exception) {
                        android.util.Log.w("SmolLM", "⚠️ RAG clearChat failed: ${e.message}")
                    }
                }
                
                // Safety check: validate context before inference to prevent native crash
                val currentContextUsed = try { llamaGPU.getContextLengthUsed() } catch (e: Exception) { 0 }
                val queryTokenEstimate = estimateTokens(finalQuery)
                val projectedUsage = currentContextUsed + queryTokenEstimate + 500 // +500 for response buffer
                
                if (projectedUsage > maxContextSize * 0.95) {
                    android.util.Log.w("SmolLM", "⚠️ Context near limit: $currentContextUsed + ~$queryTokenEstimate > ${maxContextSize}. Clearing to prevent crash.")
                    llamaGPU.clearChat()
                    if (currentModel?.id?.contains("gemma") == false) {
                        llamaGPU.addSystemPrompt("You are a helpful and intelligent AI assistant. Answer questions clearly and concisely.")
                    }
                    estimatedTokenCount = 50
                }
                
                android.util.Log.d("SmolLM", "🤖 Starting LLM inference...")
                val llmStartTime = System.currentTimeMillis()
                // Stream tokens one by one
                llamaGPU.getResponseAsFlow(finalQuery).collect { token ->
                    val now = System.currentTimeMillis()
                    
                    // Track TTFT (Time To First Token)
                    if (ttft == null) {
                        ttft = now - llmStartTime
                        val totalTTFT = now - queryStartTime
                        android.util.Log.d("SmolLM", "⚡ First token received!")
                        android.util.Log.d("SmolLM", "   - Pure LLM TTFT: ${ttft}ms")
                        android.util.Log.d("SmolLM", "   - Total TTFT (with overhead): ${totalTTFT}ms")
                        android.util.Log.d("SmolLM", "   - Breakdown: Pre-inference=${preInferenceTime}ms, Mutex wait=${mutexWaitTime}ms, LLM=${ttft}ms")
                        profiler?.recordLatency("ttft", "LLM", ttft!!)
                    } else {
                        // Track ITL (Inter-Token Latency)
                        val itl = now - lastTokenTime
                        itlTimes.add(itl)
                        profiler?.recordLatency("itl", "LLM", itl)
                    }
                    
                    lastTokenTime = now
                    tokenCount++
                    responseBuilder.append(token)
                    
                    // Update UI with streaming token
                    withContext(Dispatchers.Main) {
                        _appStateFlow.update { state ->
                            val updatedMessages = state.chatState.messages.toMutableList()
                            if (streamingMessageId < updatedMessages.size) {
                                updatedMessages[streamingMessageId] = updatedMessages[streamingMessageId].copy(
                                    content = responseBuilder.toString()
                                )
                            }
                            state.copy(
                                chatState = state.chatState.copy(
                                    messages = updatedMessages.toImmutableList()
                                )
                            )
                        }
                    }
                }
                } // End of llamaMutex.withLock
                
                val totalTime = System.currentTimeMillis() - inferenceStart
                val totalQueryTime = System.currentTimeMillis() - queryStartTime
                android.util.Log.d("SmolLM", "")
                android.util.Log.d("SmolLM", "✅ Inference complete in ${totalTime}ms (total query time: ${totalQueryTime}ms)")
                
                // Track RAM after inference
                val ramAfterMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val ramUsedMB = ramAfterMB
                
                // Calculate average ITL
                val avgItl = if (itlTimes.isNotEmpty()) itlTimes.average() else 0.0
                
                // Calculate tokens per second
                val tokensPerSecond = if (totalTime > 0) (tokenCount / (totalTime / 1000.0)) else 0.0
                
                // Estimate battery drain
                val batteryPer1000Tokens = (tokenCount / 1000.0 * 5.0).toLong()
                
                // 8K context testing logs
                val contextUsed = getRealContextUsage()
                android.util.Log.d("SmolLM-8K-TEST", "=== Inference Complete ===")
                android.util.Log.d("SmolLM-8K-TEST", "Tokens generated: $tokenCount")
                android.util.Log.d("SmolLM-8K-TEST", "Context used: $contextUsed / $maxContextSize (${(contextUsed.toFloat() / maxContextSize * 100).toInt()}%)")
                android.util.Log.d("SmolLM-8K-TEST", "Speed: ${tokensPerSecond.format(1)} tok/s")
                android.util.Log.d("SmolLM-8K-TEST", "RAM used: ${ramUsedMB}MB")
                android.util.Log.d("SmolLM-8K-TEST", "TTFT: ${ttft}ms, Avg ITL: ${avgItl.format(0)}ms")
                android.util.Log.d("SmolLM-8K-TEST", "Est. battery: ~${batteryPer1000Tokens}% per 1K tokens")
                
                // Record final metrics
                profiler?.recordCustomMetric("LLM", "avg_itl", avgItl)
                profiler?.recordCustomMetric("LLM", "tokens_per_second", tokensPerSecond)
                profiler?.recordCustomMetric("LLM", "ram_usage_mb", ramUsedMB.toDouble())
                profiler?.recordCustomMetric("LLM", "battery_per_1k_tokens", batteryPer1000Tokens.toDouble())
                profiler?.recordCustomMetric("LLM", "total_tokens", tokenCount.toDouble())
                
                // Add tokens for assistant response
                estimatedTokenCount += tokenCount
                
                // Update inference metrics for expert mode
                updateInferenceMetrics(
                    inputTokens = estimateTokens(query),
                    outputTokens = tokenCount,
                    ttftMs = ttft ?: 0L,
                    totalTimeMs = totalTime,
                    tokensPerSecond = tokensPerSecond.toFloat(),
                    contextUsed = contextUsed,
                    ramUsedMB = ramUsedMB.toInt(),
                    avgItlMs = avgItl.toFloat()
                )
                
                // Don't auto-speak - user can click speak button on message
                
                withContext(Dispatchers.Main) {
                    _appStateFlow.update { state ->
                        state.copy(
                            chatState = state.chatState.copy(
                                modelInferenceState = ModelInferenceState.SUCCESS,
                                contextUsagePercent = calculateContextUsage()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Check if it's a context size error - try to auto-recover
                val isContextError = e.message?.lowercase()?.contains("context") == true
                
                if (isContextError && currentModel?.supportsMultiTurn == true) {
                    // Auto-recover: trim messages aggressively and retry
                    trimOldMessages()
                    trimOldMessages() // Trim twice for safety
                    showContextTrimmedMessage()
                    
                    // Retry the query after trimming
                    try {
                        val retryResponse = llamaGPU.getResponse(query)
                        estimatedTokenCount += estimateTokens(retryResponse)
                        
                        withContext(Dispatchers.Main) {
                            _appStateFlow.update { state ->
                                state.copy(
                                    chatState = state.chatState.copy(
                                        messages = state.chatState.messages.addChatMessage(
                                            ChatMessage(content = retryResponse, userRole = UserRole.LLM)
                                        ),
                                        modelInferenceState = ModelInferenceState.SUCCESS,
                                        contextUsagePercent = calculateContextUsage()
                                    )
                                )
                            }
                        }
                        return@launch
                    } catch (retryError: Exception) {
                        // Retry also failed, show error
                    }
                }
                
                withContext(Dispatchers.Main) {
                    _appStateFlow.update { state ->
                        state.copy(
                            chatState = state.chatState.copy(
                                messages = state.chatState.messages.addChatMessage(
                                    ChatMessage(content = "Error: ${e.message}", userRole = UserRole.LLM)
                                ),
                                modelInferenceState = ModelInferenceState.FAILURE,
                                contextUsagePercent = calculateContextUsage()
                            )
                        )
                    }
                }
            }
        }
        
        // Check for summarization AFTER the inference job completes
        inferenceJob?.invokeOnCompletion {
            // DISABLED: Background summarization causes native crashes in ggml_vec_dot
            // The llama.cpp context appears to have thread-safety issues when reused
            // for summarization shortly after inference completes.
            // TODO: Investigate if this is a llama.cpp bug or requires longer delay
            /*
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000) // Wait 2 seconds for user to start reading
                summarizeLastExchange()
            }
            */
            
            // Check if we need to rebuild KV cache
            checkAndTriggerSummarization()
        }
    }
    
    private suspend fun trimOldMessages() {
        val currentMessages = _appStateFlow.value.chatState.messages
        if (currentMessages.size <= 2) return // Keep at least 1 exchange
        
        val startTime = System.currentTimeMillis()
        
        // Calculate how many message pairs to remove to get to ~40% context
        // This gives headroom before next trim is needed
        val currentTokens = getRealContextUsage()
        val targetTokens = (maxContextSize * 0.40).toInt()
        val tokensToRemove = currentTokens - targetTokens
        
        // Estimate tokens per message pair and calculate how many pairs to remove
        val avgTokensPerMessage = if (currentMessages.isNotEmpty()) {
            currentTokens / currentMessages.size
        } else 100
        
        // Remove enough message pairs to reach target (minimum 2 messages = 1 exchange)
        val messagesToRemove = ((tokensToRemove / avgTokensPerMessage) + 1).coerceIn(2, currentMessages.size - 2)
        // Ensure we remove in pairs (user + assistant)
        val pairsToRemove = (messagesToRemove / 2).coerceAtLeast(1)
        val actualMessagesToRemove = pairsToRemove * 2
        
        android.util.Log.d("SmolLM", "Context shift: removing $actualMessagesToRemove messages ($pairsToRemove exchanges)")
        android.util.Log.d("SmolLM", "Current: $currentTokens tokens, target: $targetTokens tokens")
        
        // Calculate tokens to remove from KV cache
        // System prompt is ~50 tokens, keep it intact
        val systemPromptTokens = 50
        val tokensForRemovedMessages = currentMessages.take(actualMessagesToRemove)
            .sumOf { estimateTokens(it.content) }
        
        // Use fast context shifting instead of model reload!
        // This removes tokens from KV cache without reloading the model
        withContext(Dispatchers.IO) {
            try {
                val newContextSize = llamaGPU.shiftContext(
                    keepFirstN = systemPromptTokens,
                    removeNextN = tokensForRemovedMessages
                )
                
                if (newContextSize >= 0) {
                    // Also remove from llama.cpp's internal message list
                    llamaGPU.removeOldestMessages(actualMessagesToRemove)
                    
                    val shiftTime = System.currentTimeMillis() - startTime
                    android.util.Log.d("SmolLM", "Context shift completed in ${shiftTime}ms (vs ~8000ms for reload)")
                    android.util.Log.d("SmolLM", "New context size: $newContextSize tokens")
                } else {
                    android.util.Log.e("SmolLM", "Context shift failed, falling back to clear")
                    // Fallback: clear everything if shift fails
                    llamaGPU.clearChat()
                }
            } catch (e: Exception) {
                android.util.Log.e("SmolLM", "Error during context shift: ${e.message}")
                // Fallback: clear everything
                llamaGPU.clearChat()
            }
        }
        
        // Update UI message list
        val trimmedMessages = currentMessages.drop(actualMessagesToRemove).toImmutableList()
        estimatedTokenCount = 50 + trimmedMessages.sumOf { estimateTokens(it.content) }
        
        withContext(Dispatchers.Main) {
            _appStateFlow.update { state ->
                state.copy(
                    chatState = state.chatState.copy(
                        messages = trimmedMessages,
                        contextUsagePercent = calculateContextUsage()
                    )
                )
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        val newUsage = calculateContextUsage()
        android.util.Log.d("SmolLM", "Trim complete in ${totalTime}ms, context now at $newUsage%")
    }
    
    private suspend fun summarizeOldMessagesInBackground() {
        if (isSummarizing) return
        
        // Try to acquire lock - if already locked (inference running), skip summarization
        if (!llamaMutex.tryLock()) {
            android.util.Log.d("SmolLM", "⚠️ Llama context busy, skipping summarization")
            return
        }
        
        try {
            val messages = _appStateFlow.value.chatState.messages
        
        // Find where the summary message is (if it exists)
        val summaryIndex = messages.indexOfFirst { 
            it.userRole == UserRole.LLM && it.content.startsWith("📝")
        }
        
        // Determine what to summarize
        val startIdx: Int
        val messagesToSummarize: Int
        
        if (summaryIndex >= 0) {
            // We have a previous summary - summarize ALL messages after it
            startIdx = summaryIndex + 1
            messagesToSummarize = messages.size - startIdx // Keep 0 recent - summary has everything
        } else {
            // First summarization - summarize ALL messages
            startIdx = 0
            messagesToSummarize = messages.size // Keep 0 recent - summary has everything
        }
        
        if (messagesToSummarize < 2) {
            android.util.Log.d("SmolLM", "⚠️ Not enough messages to summarize yet")
            return
        }
        
        isSummarizing = true
        
        try {
            val totalMessages = messages.size
            val recentMessagesToKeep = totalMessages - startIdx - messagesToSummarize
            
            val contextBefore = getRealContextUsage()
            val contextPercentBefore = calculateContextUsage()
            
            android.util.Log.d("SmolLM", "╔═══════════════════════════════════════════════════════════════╗")
            android.util.Log.d("SmolLM", "║           🔄 ROLLING SUMMARIZATION STARTED                    ║")
            android.util.Log.d("SmolLM", "╚═══════════════════════════════════════════════════════════════╝")
            android.util.Log.d("SmolLM", "Total messages: $totalMessages")
            android.util.Log.d("SmolLM", "Context before: $contextPercentBefore% ($contextBefore/$maxContextSize tokens)")
            android.util.Log.d("SmolLM", "Previous summary exists: ${summaryIndex >= 0}")
            android.util.Log.d("SmolLM", "Messages to summarize: $messagesToSummarize (indices $startIdx-${startIdx + messagesToSummarize - 1})")
            android.util.Log.d("SmolLM", "Recent messages to keep: $recentMessagesToKeep")
            android.util.Log.d("SmolLM", "")
            
            // Log messages being summarized
            android.util.Log.d("SmolLM", "📋 MESSAGES BEING SUMMARIZED:")
            android.util.Log.d("SmolLM", "─────────────────────────────────────────────────────────────")
            for (i in startIdx until (startIdx + messagesToSummarize).coerceAtMost(messages.size)) {
                val msg = messages[i]
                val role = if (msg.userRole == UserRole.HUMAN) "👤 USER" else "🤖 ASSISTANT"
                val preview = msg.content.take(80) + if (msg.content.length > 80) "..." else ""
                android.util.Log.d("SmolLM", "[$i] $role: \"$preview\"")
            }
            android.util.Log.d("SmolLM", "─────────────────────────────────────────────────────────────")
            android.util.Log.d("SmolLM", "")
            
            val startTime = System.currentTimeMillis()
            
            // Use ALL pre-computed summaries from buffer and accumulate them
            android.util.Log.d("SmolLM", "📚 Buffer has ${summaryBuffer.size} pre-computed summaries available")
            val newSummary = if (summaryBuffer.isNotEmpty()) {
                // Use ALL summaries in buffer - each one covers one response
                android.util.Log.d("SmolLM", "📝 Using ALL ${summaryBuffer.size} summaries from buffer:")
                summaryBuffer.forEachIndexed { index, summary ->
                    android.util.Log.d("SmolLM", "  [$index] $summary")
                }
                val combinedSummary = summaryBuffer.joinToString("\n\n")
                summaryBuffer.clear() // Clear all used summaries
                android.util.Log.d("SmolLM", "📚 Buffer cleared (all summaries used)")
                combinedSummary
            } else {
                // Fallback: generate summary if buffer is empty
                android.util.Log.d("SmolLM", "⚠️ No pre-computed summaries, generating on-demand...")
                withContext(Dispatchers.IO) {
                    llamaGPU.summarizeMessages(startIdx, messagesToSummarize)
                }
            }
            
            val summaryTime = System.currentTimeMillis() - startTime
            
            // Accumulate: previous summary + new summary
            currentSummary = if (currentSummary.isEmpty()) {
                newSummary
            } else {
                "$currentSummary\n\nThen: $newSummary"
            }
            
            // Check if accumulated summary is too large (>4000 tokens ~= 16000 chars)
            val summaryTokenEstimate = estimateTokens(currentSummary)
            android.util.Log.d("SmolLM", "📊 Accumulated summary tokens: ~$summaryTokenEstimate")
            
            if (summaryTokenEstimate > 4000) {
                android.util.Log.d("SmolLM", "⚠️ Summary too large ($summaryTokenEstimate tokens), compressing...")
                
                // Summarize the summary itself to compress it
                val compressedSummary = withContext(Dispatchers.IO) {
                    val compressPrompt = """Compress this conversation summary into a shorter version (max 1500 tokens), keeping the most important information:

$currentSummary

Compressed summary:"""
                    val compressed = StringBuilder()
                    llamaGPU.getResponseAsFlow(compressPrompt).collect { token ->
                        compressed.append(token)
                    }
                    compressed.toString().trim()
                }
                
                currentSummary = compressedSummary
                val newTokenCount = estimateTokens(currentSummary)
                android.util.Log.d("SmolLM", "✅ Summary compressed: $summaryTokenEstimate → $newTokenCount tokens")
                android.util.Log.d("SmolLM", "📝 Compressed summary: $currentSummary")
            }
            
            android.util.Log.d("SmolLM", "✅ Summary prepared in ${summaryTime}ms (using pre-computed)")
            android.util.Log.d("SmolLM", "📝 New summary: $newSummary")
            android.util.Log.d("SmolLM", "📚 Accumulated summary: $currentSummary")
            android.util.Log.d("SmolLM", "")
            
            // Store pending rebuild - will be done when user query comes in
            // This avoids blocking the user while they're reading the response
            pendingKVRebuild = Pair(currentSummary, recentMessagesToKeep)
            android.util.Log.d("SmolLM", "📋 KV rebuild scheduled (will happen on next query)")
            android.util.Log.d("SmolLM", "")
            
            // UI: Keep all original messages visible to user (summary is invisible to user)
            // Internal KV cache: Only system + summary (no old messages) - handled by rebuildCacheWithSummary
            // Clean up any legacy summary markers from previous versions
            val cleanedMessages = messages.filter {
                !(it.userRole == UserRole.LLM && it.content.startsWith("📝 Conversation summary"))
            }
            
            android.util.Log.d("SmolLM", "📋 UI: Keeping all ${cleanedMessages.size} original messages (summary hidden)")
            android.util.Log.d("SmolLM", "📋 KV cache: Only summary (~${estimateTokens(currentSummary)} tokens)")
            android.util.Log.d("SmolLM", "")
            
            withContext(Dispatchers.Main) {
                _appStateFlow.update { state ->
                    state.copy(
                        chatState = state.chatState.copy(
                            messages = cleanedMessages.toImmutableList(),
                            contextUsagePercent = calculateContextUsage()
                        )
                    )
                }
            }
            
            // Update token estimate - only count what's actually in KV cache (system + summary)
            estimatedTokenCount = 50 + estimateTokens(currentSummary)
            
            // Get updated context after rebuild
            val contextAfter = getRealContextUsage()
            val contextPercentAfter = calculateContextUsage()
            val tokensSaved = contextBefore - contextAfter
            
            val totalTime = System.currentTimeMillis() - startTime
            
            android.util.Log.d("SmolLM", "╔═══════════════════════════════════════════════════════════════╗")
            android.util.Log.d("SmolLM", "║           ✅ ROLLING SUMMARIZATION PREPARED                   ║")
            android.util.Log.d("SmolLM", "╚═══════════════════════════════════════════════════════════════╝")
            android.util.Log.d("SmolLM", "⏱️  Prep time: ${totalTime}ms (KV rebuild deferred to next query)")
            android.util.Log.d("SmolLM", "📊 Messages: $totalMessages → ${cleanedMessages.size} (compressed $messagesToSummarize)")
            android.util.Log.d("SmolLM", "📚 Summary length: ${currentSummary.length} chars (~${estimateTokens(currentSummary)} tokens)")
            android.util.Log.d("SmolLM", "")
            
            } catch (e: Exception) {
                android.util.Log.e("SmolLM", "❌ Summarization failed: ${e.message}", e)
            } finally {
                isSummarizing = false
            }
        } finally {
            llamaMutex.unlock()
        }
    }
    
    private fun checkAndTriggerSummarization() {
        val contextUsage = calculateContextUsage()
        android.util.Log.d("SmolLM", "🔍 Checking summarization: context=$contextUsage%, isSummarizing=$isSummarizing, inferenceActive=${inferenceJob?.isActive}")
        
        // Don't summarize if there's an active inference or already summarizing
        if (contextUsage >= 60 && !isSummarizing && inferenceJob?.isActive != true) {
            android.util.Log.d("SmolLM", "🎯 Context at $contextUsage%, triggering rolling summarization")
            summarizationJob?.cancel()
            summarizationJob = viewModelScope.launch {
                summarizeOldMessagesInBackground()
            }
        } else {
            android.util.Log.d("SmolLM", "⏭️ Skipping summarization: context=$contextUsage%, threshold=60%")
        }
    }
    
    private suspend fun summarizeLastExchange() {
        val messages = _appStateFlow.value.chatState.messages
        if (messages.size < 2) return
        
        val lastLLMMessage = messages.lastOrNull { it.userRole == UserRole.LLM } ?: return
        val lastUserMessage = messages.dropLast(1).lastOrNull { it.userRole == UserRole.HUMAN } ?: return
        
        // Add to queue
        synchronized(pendingSummarizationQueue) {
            pendingSummarizationQueue.add(Pair(lastUserMessage.content, lastLLMMessage.content))
            android.util.Log.d("SmolLM", "📝 Added exchange to summarization queue (queue size: ${pendingSummarizationQueue.size})")
        }
        
        // If already summarizing, the queue will be processed
        if (isBackgroundSummarizing) {
            android.util.Log.d("SmolLM", "⏭️ Summarization in progress, exchange queued for later")
            return
        }
        
        // For dual-model setup, use dedicated summarizer (no mutex conflict!)
        if (useDualModelSummarization && summarizerGPU != null) {
            summarizeWithDedicatedModel()
            return
        }
        
        // Try to acquire lock - if user is querying, skip background summarization
        if (!llamaMutex.tryLock()) {
            android.util.Log.d("SmolLM", "⏭️ Skipping background summary - user query in progress")
            return
        }
        
        try {
            isBackgroundSummarizing = true
            
            // Process all items in the queue (same as dual-model path)
            while (true) {
                val exchange = synchronized(pendingSummarizationQueue) {
                    if (pendingSummarizationQueue.isEmpty()) null
                    else pendingSummarizationQueue.removeAt(0)
                } ?: break
                
                val (userContent, llmContent) = exchange
                
                android.util.Log.d("SmolLM", "")
                android.util.Log.d("SmolLM", "╔═══════════════════════════════════════════════════════════════╗")
                android.util.Log.d("SmolLM", "║     📝 BACKGROUND SUMMARIZATION (main model)                 ║")
                android.util.Log.d("SmolLM", "╚═══════════════════════════════════════════════════════════════╝")
                
                val queueSize = synchronized(pendingSummarizationQueue) { pendingSummarizationQueue.size }
                android.util.Log.d("SmolLM", "📋 Queue remaining: $queueSize exchanges")
                android.util.Log.d("SmolLM", "📚 Buffer size before: ${summaryBuffer.size} summaries")
                val startTime = System.currentTimeMillis()
                
                // Truncate long content to avoid huge summaries
                val userPreview = userContent.take(200)
                val llmPreview = llmContent.take(500)
                
                val prompt = """Write a ONE sentence summary (max 30 words) of this exchange:
Q: $userPreview
A: $llmPreview

One sentence summary:"""
                
                // Generate summary using main model with hard token limit
                val summary = StringBuilder()
                var tokenCount = 0
                val maxTokens = 60 // Hard limit on output tokens
                llamaGPU.getResponseAsFlow(prompt).collect { token ->
                    if (tokenCount < maxTokens) {
                        summary.append(token)
                        tokenCount++
                    }
                }
                
                // Clean up and truncate
                var summaryText = summary.toString().trim()
                val firstPeriod = summaryText.indexOf('.')
                if (firstPeriod > 20) {
                    summaryText = summaryText.substring(0, firstPeriod + 1)
                }
                if (summaryText.length > 200) {
                    summaryText = summaryText.take(200) + "..."
                }
                summaryBuffer.add(summaryText)
                
                val timeTaken = System.currentTimeMillis() - startTime
                android.util.Log.d("SmolLM", "✅ Summary generated in ${timeTaken}ms (using main model)")
                android.util.Log.d("SmolLM", "📝 Summary: $summaryText")
                android.util.Log.d("SmolLM", "📚 Buffer size after: ${summaryBuffer.size} summaries")
                android.util.Log.d("SmolLM", "")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SmolLM", "❌ Background summarization failed: ${e.message}", e)
        } finally {
            isBackgroundSummarizing = false
            llamaMutex.unlock()
        }
    }

    private fun loadModel(model: ModelInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reader = GGUFReader()
                reader.load(currentModelPath)
                val chatTemplate = reader.getChatTemplate()
                val ggufContextSize: Long? = reader.getContextSize()
                
                // Calculate optimal context size based on device and model
                val deviceOptimalContext = DeviceCapabilities.getContextSizeForModel(model, deviceProfile)
                val safeContextSize = minOf(
                    ggufContextSize ?: model.maxContextSize.toLong(),
                    model.maxContextSize.toLong(),
                    deviceOptimalContext.toLong()
                )
                
                // Set context tracking variables
                maxContextSize = safeContextSize.toInt()
                estimatedTokenCount = 50 // Start with system prompt estimate
                
                // Use device-optimized thread count
                val optimalThreads = deviceProfile.optimalThreads
                
                // Track memory before loading
                val runtime = Runtime.getRuntime()
                val ramBeforeLoadMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                
                android.util.Log.d("SmolLM", "▶▶▶ [MAIN MODEL] Loading ${model.name}: threads=$optimalThreads, context=$safeContextSize, kvCache=Q8_0, flashAttn=true")
                android.util.Log.d("SmolLM", "▶▶▶ Device tier: ${deviceProfile.deviceTier}, CPU cores: ${Runtime.getRuntime().availableProcessors()}")
                android.util.Log.d("SmolLM-8K-TEST", "RAM before load: ${ramBeforeLoadMB}MB")
                
                llamaGPU.load(
                    modelPath = currentModelPath,
                    params = LlamaGPU.InferenceParams(
                        minP = 0.05f,
                        temperature = 0.7f,
                        storeChats = model.supportsMultiTurn,
                        contextSize = safeContextSize,
                        chatTemplate = chatTemplate,
                        numThreads = optimalThreads,
                        useMmap = true,
                        useMlock = deviceProfile.deviceTier == DeviceTier.HIGH,
                        // Performance optimizations - key for larger context!
                        flashAttention = true,
                        kvCacheType = KVCacheType.Q8_0  // 50% memory savings, enables 2x context
                    )
                )
                // Gemma models don't support system prompts in the traditional way
                if (!model.id.contains("gemma")) {
                    llamaGPU.addSystemPrompt("You are a helpful and intelligent AI assistant. Answer questions clearly and concisely.")
                }
                
                // Track memory after loading
                val ramAfterLoadMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val ramIncreaseMB = ramAfterLoadMB - ramBeforeLoadMB
                android.util.Log.d("SmolLM-8K-TEST", "RAM after load: ${ramAfterLoadMB}MB (+${ramIncreaseMB}MB)")
                android.util.Log.d("SmolLM-8K-TEST", "Context size: $safeContextSize tokens")
                android.util.Log.d("SmolLM-8K-TEST", "Estimated KV cache: ~${(safeContextSize * 0.054).toInt()}MB (Q8_0)")
                
                // Update RAG config based on model size for optimal performance
                val ragConfig = RagConfig.forModel(model.parameters, safeContextSize.toInt())
                ragEngine.updateConfig(ragConfig)
                android.util.Log.d("SmolLM", "📚 RAG config updated for ${model.parameters} model:")
                android.util.Log.d("SmolLM", "   - Chunk size: ${ragConfig.chunkSize}, TopK: ${ragConfig.topK}, FinalTopK: ${ragConfig.finalTopK}")
                android.util.Log.d("SmolLM", "   - Similarity threshold: ${ragConfig.similarityThreshold}")
                
                // Load secondary summarizer model for large models (>1GB)
                val modelSizeMB = model.sizeInMB
                if (modelSizeMB > 1000) {
                    android.util.Log.d("SmolLM", "📦 Large model detected (${modelSizeMB}MB), loading secondary summarizer...")
                    loadSummarizerModel()
                } else {
                    android.util.Log.d("SmolLM", "📦 Small model (${modelSizeMB}MB), using main model for summarization")
                    useDualModelSummarization = false
                }
                
                withContext(Dispatchers.Main) {
                    _appStateFlow.update { state ->
                        state.copy(
                            chatState = state.chatState.copy(
                                modelLoadingState = ModelLoadingState.SUCCESS
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _appStateFlow.update { state ->
                        state.copy(
                            chatState = state.chatState.copy(
                                modelLoadingState = ModelLoadingState.FAILURE
                            )
                        )
                    }
                }
            }
        }
    }
    
    private suspend fun loadSummarizerModel() {
        try {
            // Find Qwen 2.5 0.5B Instruct model (smallest reliable INSTRUCT model - 491MB)
            // Note: SmolLM 135M is a base model and crashes, SmolLM 360M requires auth
            val summarizerModel = AvailableModels.models.find { 
                it.id.contains("qwen2.5-0.5b", ignoreCase = true) 
            }
            
            if (summarizerModel == null) {
                android.util.Log.w("SmolLM", "⚠️ Qwen 2.5 0.5B not found in available models, using main model")
                useDualModelSummarization = false
                return
            }
            
            val summarizerPath = File(File(getApplication<Application>().filesDir, "models"), summarizerModel.fileName).absolutePath
            
            // Check if Qwen 2.5 0.5B is already downloaded
            if (!File(summarizerPath).exists()) {
                android.util.Log.w("SmolLM", "⚠️ Qwen 2.5 0.5B Instruct not downloaded. Please download it from model selection for dual-model summarization.")
                android.util.Log.w("SmolLM", "💡 Falling back to main model for summarization (may block queries)")
                useDualModelSummarization = false
                return
            }
            
            android.util.Log.d("SmolLM", "🔧 Loading Qwen 2.5 0.5B Instruct summarizer model...")
            
            // Read chat template from GGUF (with fallback to empty)
            val chatTemplate = try {
                val reader = GGUFReader()
                reader.load(summarizerPath)
                reader.getChatTemplate()
            } catch (e: Exception) {
                android.util.Log.w("SmolLM", "Could not read chat template from Qwen 0.5B model, using empty template")
                ""
            }
            
            summarizerGPU = LlamaGPU()
            summarizerGPU?.load(
                modelPath = summarizerPath,
                params = LlamaGPU.InferenceParams(
                    minP = 0.05f,
                    temperature = 0.7f,
                    storeChats = true,  // Need this to avoid crash
                    contextSize = 2048,  // Small context is enough
                    chatTemplate = chatTemplate,
                    numThreads = 2,  // Use fewer threads to not interfere with main model
                    useMmap = true,
                    useMlock = false,
                    flashAttention = false,  // Not needed for small model
                    kvCacheType = KVCacheType.F16  // Use F16 for tiny model
                )
            )
            
            useDualModelSummarization = true
            android.util.Log.d("SmolLM", "✅ Summarizer model loaded successfully (dual-model mode enabled)")
            android.util.Log.d("SmolLM", "💡 Main model will never be blocked by summarization!")
            
        } catch (e: Exception) {
            android.util.Log.e("SmolLM", "❌ Failed to load summarizer model: ${e.message}", e)
            summarizerGPU = null
            useDualModelSummarization = false
        }
    }
    
    private suspend fun summarizeWithDedicatedModel() {
        // Use separate mutex for summarizer to avoid any deadlock
        summarizerMutex.withLock {
            try {
                isBackgroundSummarizing = true
                
                // Process all items in the queue
                while (true) {
                    val exchange = synchronized(pendingSummarizationQueue) {
                        if (pendingSummarizationQueue.isEmpty()) null
                        else pendingSummarizationQueue.removeAt(0)
                    } ?: break
                    
                    val (userContent, llmContent) = exchange
                    
                    android.util.Log.d("SmolLM", "")
                    android.util.Log.d("SmolLM", "╔═══════════════════════════════════════════════════════════════╗")
                    android.util.Log.d("SmolLM", "║     📝 BACKGROUND SUMMARIZATION (Qwen 0.5B model)           ║")
                    android.util.Log.d("SmolLM", "╚═══════════════════════════════════════════════════════════════╝")
                    
                    val queueSize = synchronized(pendingSummarizationQueue) { pendingSummarizationQueue.size }
                    android.util.Log.d("SmolLM", "📋 Queue remaining: $queueSize exchanges")
                    android.util.Log.d("SmolLM", "📚 Buffer size before: ${summaryBuffer.size} summaries")
                    val startTime = System.currentTimeMillis()
                    
                    // Create summarization prompt
                    // Truncate long content to avoid huge summaries
                    val userPreview = userContent.take(200)
                    val llmPreview = llmContent.take(500)
                    
                    val prompt = """Write a ONE sentence summary (max 30 words) of this exchange:
Q: $userPreview
A: $llmPreview

One sentence summary:"""
                    
                    // Generate summary using dedicated summarizer model (no mutex conflict!)
                    val summary = StringBuilder()
                    var tokenCount = 0
                    val maxTokens = 60 // Hard limit on output tokens
                    withContext(Dispatchers.IO) {
                        summarizerGPU?.getResponseAsFlow(prompt)?.collect { token ->
                            if (tokenCount < maxTokens) {
                                summary.append(token)
                                tokenCount++
                            }
                        }
                    }
                    
                    // Clean up and truncate if needed
                    var summaryText = summary.toString().trim()
                    // Stop at first period if we have one
                    val firstPeriod = summaryText.indexOf('.')
                    if (firstPeriod > 20) {
                        summaryText = summaryText.substring(0, firstPeriod + 1)
                    }
                    // Hard limit on characters
                    if (summaryText.length > 200) {
                        summaryText = summaryText.take(200) + "..."
                    }
                    summaryBuffer.add(summaryText)
                    
                    val timeTaken = System.currentTimeMillis() - startTime
                    android.util.Log.d("SmolLM", "✅ Summary generated in ${timeTaken}ms (using Qwen 0.5B model)")
                    android.util.Log.d("SmolLM", "📝 Summary: $summaryText")
                    android.util.Log.d("SmolLM", "📚 Buffer size after: ${summaryBuffer.size} summaries")
                    android.util.Log.d("SmolLM", "🎯 No mutex conflict - main model available for queries!")
                    android.util.Log.d("SmolLM", "")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SmolLM", "❌ Dedicated summarization failed: ${e.message}", e)
            } finally {
                isBackgroundSummarizing = false
            }
        }
    }

    private fun ImmutableList<ChatMessage>.addChatMessage(chatMessage: ChatMessage): ImmutableList<ChatMessage> {
        val mutableList = this.toMutableList()
        mutableList.add(chatMessage)
        return mutableList.toImmutableList()
    }
}