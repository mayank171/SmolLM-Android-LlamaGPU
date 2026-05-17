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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UserRole {
    HUMAN,
    LLM
}

data class ChatMessage(
    val content: String,
    val userRole: UserRole
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
    BENCHMARK
}

data class ChatUIState(
    val messages: ImmutableList<ChatMessage> = emptyList<ChatMessage>().toImmutableList(),
    val modelLoadingState: ModelLoadingState = ModelLoadingState.NOT_LOADED,
    val modelInferenceState: ModelInferenceState = ModelInferenceState.IDLE,
    val currentModelName: String = "",
    val contextUsagePercent: Int = 0,
    val toastMessage: String? = null
)

data class AppState(
    val currentScreen: AppScreen = AppScreen.MODEL_SELECTION,
    val modelSelectionState: ModelSelectionUiState = ModelSelectionUiState(),
    val chatState: ChatUIState = ChatUIState()
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
}

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = ModelDownloadManager(application)
    private val deviceProfile: DeviceProfile = DeviceCapabilities.getDeviceProfile(application)
    
    private val _appStateFlow = MutableStateFlow(AppState())
    val appStateFlow: StateFlow<AppState> = _appStateFlow

    private val llamaGPU = LlamaGPU()
    private var currentModelPath: String = ""
    private var currentModel: ModelInfo? = null
    private var downloadJob: Job? = null
    private var estimatedTokenCount: Int = 0
    private var maxContextSize: Int = deviceProfile.maxContextSize

    init {
        refreshDownloadedModels()
        logDeviceInfo()
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
            state.copy(currentScreen = AppScreen.CHAT)
        }
    }
    
    fun getCurrentModelPath(): String? {
        return if (currentModelPath.isNotEmpty()) currentModelPath else null
    }

    private fun submitQuery(query: String) {
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
        
        viewModelScope.launch(Dispatchers.Default) {
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
                
                // getResponse handles adding user message internally
                val llmResponse = llamaGPU.getResponse(query)
                
                // Add tokens for assistant response
                estimatedTokenCount += estimateTokens(llmResponse)
                
                withContext(Dispatchers.Main) {
                    _appStateFlow.update { state ->
                        state.copy(
                            chatState = state.chatState.copy(
                                messages = state.chatState.messages.addChatMessage(
                                    ChatMessage(content = llmResponse, userRole = UserRole.LLM)
                                ),
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
        
        // Remove oldest 2 messages (1 user + 1 assistant exchange)
        val trimmedMessages = currentMessages.drop(2).toImmutableList()
        
        // Recalculate token count from remaining messages
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
        
        // CRITICAL: Reload model to clear llama.cpp internal KV cache
        // This is the only way to actually free the context memory
        currentModel?.let { model ->
            withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d("SmolLM", "Reloading model to clear context...")
                    
                    val reader = GGUFReader()
                    reader.load(currentModelPath)
                    val chatTemplate = reader.getChatTemplate()
                    val deviceOptimalContext = DeviceCapabilities.getContextSizeForModel(model, deviceProfile)
                    val safeContextSize = minOf(model.maxContextSize.toLong(), deviceOptimalContext.toLong())
                    
                    llamaGPU.load(
                        modelPath = currentModelPath,
                        params = LlamaGPU.InferenceParams(
                            minP = 0.05f,
                            temperature = 0.7f,
                            storeChats = model.supportsMultiTurn,
                            contextSize = safeContextSize,
                            chatTemplate = chatTemplate,
                            numThreads = deviceProfile.optimalThreads,
                            useMmap = true,
                            useMlock = deviceProfile.deviceTier == DeviceTier.HIGH,
                            // Performance optimizations
                            flashAttention = true,
                            kvCacheType = KVCacheType.Q8_0  // 50% memory savings!
                        )
                    )
                    
                    // Re-add system prompt for non-Gemma models
                    if (!model.id.contains("gemma")) {
                        llamaGPU.addSystemPrompt("You are a helpful and intelligent AI assistant. Answer questions clearly and concisely.")
                    }
                    
                    // Re-add the last exchange to maintain some context continuity
                    // This helps the model understand the conversation flow
                    if (trimmedMessages.size >= 2) {
                        val lastUserMsg = trimmedMessages.lastOrNull { it.userRole == UserRole.HUMAN }
                        val lastAssistantMsg = trimmedMessages.lastOrNull { it.userRole == UserRole.LLM }
                        
                        lastUserMsg?.let { llamaGPU.addUserMessage(it.content) }
                        lastAssistantMsg?.let { llamaGPU.addAssistantMessage(it.content) }
                    }
                    
                    val newUsage = calculateContextUsage()
                    android.util.Log.d("SmolLM", "Model reloaded, context now at $newUsage%")
                } catch (e: Exception) {
                    android.util.Log.e("SmolLM", "Error reloading model: ${e.message}")
                }
            }
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
                
                android.util.Log.d("SmolLM", "Loading ${model.name}: threads=$optimalThreads, context=$safeContextSize, kvCache=Q8_0, flashAttn=true")
                
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