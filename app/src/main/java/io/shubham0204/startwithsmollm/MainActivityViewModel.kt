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
import io.shubham0204.startwithsmollm.data.ExpertMode
import io.shubham0204.startwithsmollm.rag.Document
import io.shubham0204.startwithsmollm.rag.RagEngine
import io.shubham0204.startwithsmollm.rag.profiling.Profiler
import io.shubham0204.startwithsmollm.voice.VoiceManager
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

// Extension function for formatting doubles
private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

enum class UserRole {
    HUMAN,
    LLM
}

data class ChatMessage(
    val content: String,
    val userRole: UserRole,
    val citations: List<io.shubham0204.startwithsmollm.rag.Citation> = emptyList()
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
    // Bluetooth events
    data class ReceiveBluetoothResponse(val response: String) : AppEvent
}

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = ModelDownloadManager(application)
    private val deviceProfile: DeviceProfile = DeviceCapabilities.getDeviceProfile(application)
    
    private val _appStateFlow = MutableStateFlow(AppState())
    val appStateFlow: StateFlow<AppState> = _appStateFlow

    private val llamaGPU = LlamaGPU()
    private val ragEngine = RagEngine(application)
    private val voiceManager = VoiceManager(application)
    private val profiler = if (Profiler.isInitialized()) Profiler.getInstance(application) else null
    private var currentModelPath: String = ""
    private var currentModel: ModelInfo? = null
    private var downloadJob: Job? = null
    private var inferenceJob: Job? = null
    private var estimatedTokenCount: Int = 0
    private var maxContextSize: Int = deviceProfile.maxContextSize
    
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
            // Bluetooth events
            is AppEvent.ReceiveBluetoothResponse -> receiveBluetoothResponse(event.response)
        }
    }
    
    private fun receiveBluetoothResponse(response: String) {
        _appStateFlow.update { state ->
            val newMessages = state.chatState.messages + ChatMessage(
                content = response,
                userRole = UserRole.LLM
            )
            state.copy(
                chatState = state.chatState.copy(
                    messages = newMessages.toImmutableList(),
                    modelInferenceState = ModelInferenceState.IDLE
                )
            )
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
        _appStateFlow.update { state ->
            state.copy(
                currentScreen = AppScreen.CHAT,
                chatState = ChatUIState(
                    modelLoadingState = ModelLoadingState.LOADING,
                    currentModelName = model.name
                )
            )
        }
        loadModel(model)
    }

    private fun backToModelSelection() {
        // Close the model to free resources
        llamaGPU.close()
        
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
                // Check REAL context usage from llama.cpp - trim at 70% to leave room for response
                val realUsage = calculateContextUsage()
                val currentMessages = _appStateFlow.value.chatState.messages
                
                android.util.Log.d("SmolLM", "Context before query: $realUsage% (${getRealContextUsage()}/${maxContextSize} tokens)")
                
                if (realUsage >= 70 && currentModel?.supportsMultiTurn == true && currentMessages.size >= 2) {
                    android.util.Log.d("SmolLM", "Triggering context trim at $realUsage%")
                    trimOldMessages()
                    showContextTrimmedMessage()
                }
                
                // Check if RAG is enabled and use augmented prompt
                val ragEnabled = _appStateFlow.value.chatState.ragEnabled
                var citations: List<io.shubham0204.startwithsmollm.rag.Citation> = emptyList()
                
                val finalQuery = if (ragEnabled && ragEngine.hasDocuments()) {
                    val ragResult = ragEngine.query(query)
                    android.util.Log.d("SmolLM", "RAG: Found ${ragResult.retrievedChunks.size} relevant chunks")
                    citations = ragResult.citations
                    ragResult.augmentedPrompt
                } else {
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
                
                // Stream tokens one by one
                llamaGPU.getResponseAsFlow(finalQuery).collect { token ->
                    val now = System.currentTimeMillis()
                    
                    // Track TTFT (Time To First Token)
                    if (ttft == null) {
                        ttft = now - inferenceStart
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
                
                val totalTime = System.currentTimeMillis() - inferenceStart
                
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
        
        android.util.Log.d("SmolLM", "╔═══════════════════════════════════════════════════════════════╗")
        android.util.Log.d("SmolLM", "║           🗑️  CONTEXT TRIMMING STARTED                        ║")
        android.util.Log.d("SmolLM", "╚═══════════════════════════════════════════════════════════════╝")
        android.util.Log.d("SmolLM", "Total messages before trim: ${currentMessages.size}")
        android.util.Log.d("SmolLM", "Messages to remove: $actualMessagesToRemove ($pairsToRemove exchanges)")
        android.util.Log.d("SmolLM", "Current tokens: $currentTokens, Target tokens: $targetTokens")
        android.util.Log.d("SmolLM", "")
        
        // Log the messages that will be removed
        val messagesToBeRemoved = currentMessages.take(actualMessagesToRemove)
        android.util.Log.d("SmolLM", "📋 MESSAGES BEING REMOVED:")
        android.util.Log.d("SmolLM", "─────────────────────────────────────────────────────────────")
        messagesToBeRemoved.forEachIndexed { index, message ->
            val role = if (message.userRole == UserRole.HUMAN) "👤 USER" else "🤖 ASSISTANT"
            val preview = message.content.take(100).replace("\n", " ")
            val suffix = if (message.content.length > 100) "..." else ""
            val tokens = estimateTokens(message.content)
            android.util.Log.d("SmolLM", "[$index] $role (~$tokens tokens)")
            android.util.Log.d("SmolLM", "    \"$preview$suffix\"")
        }
        android.util.Log.d("SmolLM", "─────────────────────────────────────────────────────────────")
        android.util.Log.d("SmolLM", "")
        
        // Calculate tokens to remove from KV cache
        // System prompt is ~50 tokens, keep it intact
        val systemPromptTokens = 50
        val tokensForRemovedMessages = messagesToBeRemoved.sumOf { estimateTokens(it.content) }
        
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
        
        android.util.Log.d("SmolLM", "📋 MESSAGES REMAINING (${trimmedMessages.size} total):")
        android.util.Log.d("SmolLM", "─────────────────────────────────────────────────────────────")
        trimmedMessages.forEachIndexed { index, message ->
            val role = if (message.userRole == UserRole.HUMAN) "👤 USER" else "🤖 ASSISTANT"
            val preview = message.content.take(80).replace("\n", " ")
            val suffix = if (message.content.length > 80) "..." else ""
            val tokens = estimateTokens(message.content)
            android.util.Log.d("SmolLM", "[$index] $role (~$tokens tokens): \"$preview$suffix\"")
        }
        android.util.Log.d("SmolLM", "─────────────────────────────────────────────────────────────")
        android.util.Log.d("SmolLM", "")
        
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
        android.util.Log.d("SmolLM", "╔═══════════════════════════════════════════════════════════════╗")
        android.util.Log.d("SmolLM", "║           ✅ CONTEXT TRIMMING COMPLETE                        ║")
        android.util.Log.d("SmolLM", "╚═══════════════════════════════════════════════════════════════╝")
        android.util.Log.d("SmolLM", "⏱️  Time taken: ${totalTime}ms (vs ~8000ms for full reload)")
        android.util.Log.d("SmolLM", "📊 Messages: ${currentMessages.size} → ${trimmedMessages.size} (removed $actualMessagesToRemove)")
        android.util.Log.d("SmolLM", "🎯 Context usage: ${(currentTokens.toFloat() / maxContextSize * 100).toInt()}% → $newUsage%")
        android.util.Log.d("SmolLM", "💾 Tokens: $currentTokens → ${getRealContextUsage()} (freed ${currentTokens - getRealContextUsage()} tokens)")
        android.util.Log.d("SmolLM", "")
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
                
                android.util.Log.d("SmolLM", "Loading ${model.name}: threads=$optimalThreads, context=$safeContextSize, kvCache=Q8_0, flashAttn=true")
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

    private fun ImmutableList<ChatMessage>.addChatMessage(chatMessage: ChatMessage): ImmutableList<ChatMessage> {
        val mutableList = this.toMutableList()
        mutableList.add(chatMessage)
        return mutableList.toImmutableList()
    }
}