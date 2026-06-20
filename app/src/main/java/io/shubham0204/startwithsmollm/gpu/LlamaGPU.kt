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
    @Volatile private var isInferenceRunning = false
    @Volatile private var shouldStopInference = false
    
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
        // GPU (Vulkan backend is built into the .so but disabled by default).
        //
        // The ggml-vulkan backend crashes (SIGSEGV in vulkan tensor alloc) on
        // certain Adreno GPUs during ggml_backend_alloc_ctx_tensors_from_buft.
        // This is a driver-level bug, not in our code. Until a per-device
        // allowlist or working detection exists, ship with GPU off by default.
        //
        // To experiment on other devices: flip these to (true, -1).
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
     * Get response as a Flow for streaming.
     *
     * Performance: temporarily boosts the calling thread's priority to URGENT_DISPLAY
     * so the Android scheduler favors it (and keeps native llama.cpp worker threads,
     * which inherit niceness, on performance cores). Original priority is restored
     * in finally to avoid leaking the boost back to the IO thread pool.
     */
    fun getResponseAsFlow(query: String): Flow<String> = flow {
        verifyHandle()
        shouldStopInference = false
        isInferenceRunning = true
        val originalPriority = android.os.Process.getThreadPriority(android.os.Process.myTid())
        try {
            // Boost priority for the inference thread. URGENT_DISPLAY (-8) is the same
            // tier used by the Android UI compositor — strong scheduler preference without
            // requiring special permissions like URGENT_AUDIO.
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            } catch (e: SecurityException) {
                android.util.Log.w("LlamaGPU", "Could not boost thread priority: ${e.message}")
            }
            startCompletion(nativePtr, query)
            var piece = completionLoop(nativePtr)
            while (piece != "[EOG]" && !shouldStopInference) {
                if (piece.isNotEmpty()) {
                    emit(piece)
                }
                if (shouldStopInference) break
                piece = completionLoop(nativePtr)
            }
            stopCompletion(nativePtr)
        } finally {
            isInferenceRunning = false
            // Restore priority so we don't pollute the IO dispatcher thread pool
            try {
                android.os.Process.setThreadPriority(originalPriority)
            } catch (_: Exception) { }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get complete response (blocking)
     */
    fun getResponse(query: String): String {
        verifyHandle()
        shouldStopInference = false
        isInferenceRunning = true
        try {
            startCompletion(nativePtr, query)
            val response = StringBuilder()
            var piece = completionLoop(nativePtr)
            while (piece != "[EOG]" && !shouldStopInference) {
                response.append(piece)
                if (shouldStopInference) break
                piece = completionLoop(nativePtr)
            }
            stopCompletion(nativePtr)
            return response.toString()
        } finally {
            isInferenceRunning = false
        }
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
    
    /**
     * Check if inference is currently running
     */
    fun isRunning(): Boolean = isInferenceRunning
    
    /**
     * Stop any ongoing inference. Call this before close() if inference might be running.
     * This signals the native code to stop and waits for it to complete.
     */
    fun stopInference() {
        shouldStopInference = true
        if (nativePtr != 0L) {
            try {
                stopCompletion(nativePtr)
            } catch (e: Exception) {
                android.util.Log.w("LlamaGPU", "stopInference error: ${e.message}")
            }
        }
    }
    
    /**
     * Stop inference and wait for it to actually stop (blocking)
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if inference stopped, false if timeout
     */
    fun stopInferenceAndWait(timeoutMs: Long = 2000): Boolean {
        shouldStopInference = true
        if (nativePtr != 0L) {
            try {
                stopCompletion(nativePtr)
            } catch (e: Exception) {
                android.util.Log.w("LlamaGPU", "stopInference error: ${e.message}")
            }
        }
        
        // Wait for inference to actually stop
        val startTime = System.currentTimeMillis()
        while (isInferenceRunning && (System.currentTimeMillis() - startTime) < timeoutMs) {
            Thread.sleep(50)
        }
        
        return !isInferenceRunning
    }
    
    fun close() {
        if (nativePtr != 0L) {
            // Signal stop and wait for inference to finish
            shouldStopInference = true
            try {
                stopCompletion(nativePtr)
            } catch (e: Exception) {
                // Ignore - might not be running
            }
            
            // Wait for inference to stop (max 1 second)
            val startTime = System.currentTimeMillis()
            while (isInferenceRunning && (System.currentTimeMillis() - startTime) < 1000) {
                Thread.sleep(50)
            }
            
            if (isInferenceRunning) {
                android.util.Log.w("LlamaGPU", "Warning: closing while inference still running")
            }
            
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
    private external fun summarizeMessages(modelPtr: Long, startIdx: Int, count: Int): String
    private external fun rebuildCacheWithSummary(modelPtr: Long, summary: String, keepRecentN: Int)
    
    /**
     * Summarize a range of messages in the conversation history
     * @param startIdx Starting index of messages to summarize (0-based)
     * @param count Number of messages to summarize
     * @return Summary text
     */
    fun summarizeMessages(startIdx: Int, count: Int): String {
        return summarizeMessages(nativePtr, startIdx, count)
    }
    
    /**
     * Rebuild KV cache with a summary replacing old messages
     * This clears the KV cache and rebuilds it with: [System + Summary + Recent N messages]
     * @param summary The summary text to inject
     * @param keepRecentN Number of recent messages to keep
     */
    fun rebuildCacheWithSummary(summary: String, keepRecentN: Int) {
        rebuildCacheWithSummary(nativePtr, summary, keepRecentN)
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // SYSTEM PROMPT CACHING
    // Caches the KV state after processing system prompt for fast restore.
    // This significantly reduces TTFT for RAG queries where we clear chat
    // between queries but always use the same system prompt.
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Cache the current KV state as the system prompt cache.
     * Call this AFTER adding the system prompt and running one inference
     * (or after addSystemPrompt + a dummy completion to populate KV cache).
     * 
     * @return true if caching succeeded
     */
    fun cacheSystemPrompt(): Boolean {
        verifyHandle()
        return cacheSystemPrompt(nativePtr)
    }
    
    /**
     * Restore the KV cache to the cached system prompt state.
     * Use this instead of clearChat() when you want to start fresh
     * but keep the system prompt already encoded.
     * 
     * @return true if restore succeeded, false if no cache exists
     */
    fun restoreSystemPromptCache(): Boolean {
        verifyHandle()
        return restoreSystemPromptCache(nativePtr)
    }
    
    /**
     * Check if a system prompt cache exists
     */
    fun hasSystemPromptCache(): Boolean {
        verifyHandle()
        return hasSystemPromptCache(nativePtr)
    }
    
    /**
     * Get the number of tokens in the cached system prompt
     */
    fun getSystemPromptCacheSize(): Int {
        verifyHandle()
        return getSystemPromptCacheSize(nativePtr)
    }
    
    /**
     * Clear the system prompt cache (e.g., when changing models or system prompts)
     */
    fun clearSystemPromptCache() {
        verifyHandle()
        clearSystemPromptCache(nativePtr)
    }
    
    // Native methods for system prompt caching
    private external fun cacheSystemPrompt(modelPtr: Long): Boolean
    private external fun restoreSystemPromptCache(modelPtr: Long): Boolean
    private external fun hasSystemPromptCache(modelPtr: Long): Boolean
    private external fun getSystemPromptCacheSize(modelPtr: Long): Int
    private external fun clearSystemPromptCache(modelPtr: Long)
}
