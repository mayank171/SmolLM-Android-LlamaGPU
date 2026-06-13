package io.shubham0204.startwithsmollm.gpu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * KV Cache quantization types for memory optimization
 * Lower precision = less memory, minimal quality impact
 */
enum class KVCacheType(val ggmlType: Int) {
    F16(1),    // GGML_TYPE_F16 - Default, best quality
    Q8_0(8),   // GGML_TYPE_Q8_0 - 50% memory savings, minimal quality loss
    Q4_0(2),   // GGML_TYPE_Q4_0 - 75% memory savings, slight quality loss
}

/**
 * LlamaGPU - Optimized LLM inference using llama.cpp
 * 
 * Features:
 * - Flash Attention for memory efficiency
 * - KV Cache quantization (F16/Q8_0/Q4_0)
 * - Configurable threading
 * - Streaming responses via Kotlin Flow
 * 
 * Usage:
 * ```kotlin
 * val llamaGPU = LlamaGPU()
 * llamaGPU.load(modelPath, LlamaGPU.InferenceParams(
 *     flashAttention = true,
 *     kvCacheType = KVCacheType.Q8_0  // For low-memory devices
 * ))
 * ```
 */
class LlamaGPU {
    
    private var nativePtr: Long = 0
    
    companion object {
        init {
            try {
                System.loadLibrary("llamavulkan")
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e("LlamaGPU", "Failed to load llamavulkan library: ${e.message}")
            }
        }
        
        /**
         * Check if Vulkan GPU acceleration is available on this device
         */
        fun isVulkanAvailable(): Boolean {
            return try {
                isVulkanAvailableNative()
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Get information about available GPU backends
         */
        fun getGPUInfo(): String {
            return try {
                getGPUInfoNative()
            } catch (e: Exception) {
                "GPU info unavailable: ${e.message}"
            }
        }
        
        @JvmStatic
        private external fun isVulkanAvailableNative(): Boolean
        
        @JvmStatic
        private external fun getGPUInfoNative(): String
    }
    
    data class InferenceParams(
        // Sampling parameters - affect answer quality
        val temperature: Float = 0.7f,      // Randomness (0.1=focused, 1.5=creative)
        val topK: Int = 40,                 // Keep top K tokens (0=disabled)
        val topP: Float = 0.9f,             // Keep tokens covering P probability mass
        val minP: Float = 0.05f,            // Remove tokens < minP * top_prob
        val repeatPenalty: Float = 1.1f,    // Penalize repetition (1.0=off, 1.2=strong)
        // Context settings
        val storeChats: Boolean = true,
        val contextSize: Long? = null,
        val chatTemplate: String? = null,
        // Threading
        val numThreads: Int = 4,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
        // GPU (disabled - Vulkan crashes on Adreno)
        val useGPU: Boolean = false,
        val gpuLayers: Int = 0,
        // Performance optimizations
        val flashAttention: Boolean = true,
        val kvCacheType: KVCacheType = KVCacheType.F16
    )
    
    /**
     * Load model with optional GPU acceleration
     */
    suspend fun load(
        modelPath: String,
        params: InferenceParams = InferenceParams()
    ) = withContext(Dispatchers.IO) {
        nativePtr = loadModel(
            modelPath,
            params.temperature,
            params.topK,
            params.topP,
            params.minP,
            params.repeatPenalty,
            params.storeChats,
            params.contextSize ?: 2048L,
            params.chatTemplate ?: "",
            params.numThreads,
            params.useMmap,
            params.useMlock,
            params.useGPU,
            params.gpuLayers,
            params.flashAttention,
            params.kvCacheType.ggmlType
        )
    }
    
    /**
     * Check if the model is currently using GPU
     */
    fun isUsingGPU(): Boolean {
        verifyHandle()
        return isUsingGPU(nativePtr)
    }
    
    fun addUserMessage(message: String) {
        verifyHandle()
        addChatMessage(nativePtr, message, "user")
    }
    
    fun addSystemPrompt(prompt: String) {
        verifyHandle()
        addChatMessage(nativePtr, prompt, "system")
    }
    
    fun addAssistantMessage(message: String) {
        verifyHandle()
        addChatMessage(nativePtr, message, "assistant")
    }
    
    fun getResponseGenerationSpeed(): Float {
        verifyHandle()
        return getResponseGenerationSpeed(nativePtr)
    }
    
    fun getContextLengthUsed(): Int {
        verifyHandle()
        return getContextSizeUsed(nativePtr)
    }
    
    /**
     * Get response as a Flow for streaming
     */
    fun getResponseAsFlow(query: String): Flow<String> = flow {
        verifyHandle()
        startCompletion(nativePtr, query)
        var piece = completionLoop(nativePtr)
        while (piece != "[EOG]") {
            if (piece.isNotEmpty()) {
                emit(piece)
            }
            piece = completionLoop(nativePtr)
        }
        stopCompletion(nativePtr)
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get complete response (blocking)
     */
    fun getResponse(query: String): String {
        verifyHandle()
        startCompletion(nativePtr, query)
        val response = StringBuilder()
        var piece = completionLoop(nativePtr)
        while (piece != "[EOG]") {
            response.append(piece)
            piece = completionLoop(nativePtr)
        }
        stopCompletion(nativePtr)
        return response.toString()
    }
    
    /**
     * Run a simple benchmark and return results
     * @return Pair of (prompt processing tokens/sec, text generation tokens/sec)
     */
    suspend fun benchmark(testPrompt: String = "Write a short poem about AI."): BenchmarkResult = 
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            
            startCompletion(nativePtr, testPrompt)
            var piece = completionLoop(nativePtr)
            while (piece != "[EOG]") {
                tokenCount++
                piece = completionLoop(nativePtr)
            }
            stopCompletion(nativePtr)
            
            val endTime = System.currentTimeMillis()
            val durationSec = (endTime - startTime) / 1000.0
            val tokensPerSec = if (durationSec > 0) tokenCount / durationSec else 0.0
            
            BenchmarkResult(
                tokensGenerated = tokenCount,
                durationMs = endTime - startTime,
                tokensPerSecond = tokensPerSec,
                usingGPU = isUsingGPU(),
                backends = getGPUInfo()
            )
        }
    
    data class BenchmarkResult(
        val tokensGenerated: Int,
        val durationMs: Long,
        val tokensPerSecond: Double,
        val usingGPU: Boolean,
        val backends: String
    ) {
        override fun toString(): String {
            return """
                |=== LlamaGPU Benchmark ===
                |Backends: $backends
                |Using GPU: $usingGPU
                |Tokens generated: $tokensGenerated
                |Duration: ${durationMs}ms
                |Speed: ${"%.2f".format(tokensPerSecond)} tokens/sec
            """.trimMargin()
        }
    }
    
    
    /**
     * Clear chat history and KV cache
     */
    fun clearChat() {
        verifyHandle()
        clearChat(nativePtr)
    }
    
    /**
     * Shift context by removing tokens from the middle of the KV cache.
     * This is MUCH faster than reloading the model (~50ms vs ~8 seconds).
     * 
     * @param keepFirstN Number of tokens to keep at the start (e.g., system prompt tokens)
     * @param removeNextN Number of tokens to remove after keepFirstN
     * @return New context size used, or -1 on error
     * 
     * Example: If you have 2000 tokens and want to remove the first conversation
     * while keeping the system prompt (50 tokens):
     *   shiftContext(50, 400) // Keep first 50, remove next 400
     */
    fun shiftContext(keepFirstN: Int, removeNextN: Int): Int {
        verifyHandle()
        return shiftContext(nativePtr, keepFirstN, removeNextN)
    }
    
    /**
     * Get the number of messages in the internal chat history
     */
    fun getMessageCount(): Int {
        verifyHandle()
        return getMessageCount(nativePtr)
    }
    
    /**
     * Remove oldest N messages from the internal chat history.
     * Call this after shiftContext to keep the message list in sync with KV cache.
     * 
     * @param count Number of messages to remove (typically 2 for one user+assistant exchange)
     */
    fun removeOldestMessages(count: Int) {
        verifyHandle()
        removeOldestMessages(nativePtr, count)
    }
    
    fun close() {
        if (nativePtr != 0L) {
            close(nativePtr)
            nativePtr = 0
        }
    }
    
    private fun verifyHandle() {
        if (nativePtr == 0L) {
            throw IllegalStateException("Model not loaded. Call load() first.")
        }
    }
    
    // Native methods
    private external fun loadModel(
        modelPath: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Float,
        repeatPenalty: Float,
        storeChats: Boolean,
        contextSize: Long,
        chatTemplate: String,
        nThreads: Int,
        useMmap: Boolean,
        useMlock: Boolean,
        useGPU: Boolean,
        gpuLayers: Int,
        flashAttention: Boolean,
        kvCacheType: Int
    ): Long
    
    private external fun isUsingGPU(modelPtr: Long): Boolean
    private external fun addChatMessage(modelPtr: Long, message: String, role: String)
    private external fun getResponseGenerationSpeed(modelPtr: Long): Float
    private external fun getContextSizeUsed(modelPtr: Long): Int
    private external fun close(modelPtr: Long)
    private external fun startCompletion(modelPtr: Long, prompt: String)
    private external fun completionLoop(modelPtr: Long): String
    private external fun stopCompletion(modelPtr: Long)
    private external fun saveState(modelPtr: Long, path: String): Boolean
    private external fun loadState(modelPtr: Long, path: String): Boolean
    private external fun clearChat(modelPtr: Long)
    private external fun shiftContext(modelPtr: Long, keepFirstN: Int, removeNextN: Int): Int
    private external fun getMessageCount(modelPtr: Long): Int
    private external fun removeOldestMessages(modelPtr: Long, count: Int)
}
