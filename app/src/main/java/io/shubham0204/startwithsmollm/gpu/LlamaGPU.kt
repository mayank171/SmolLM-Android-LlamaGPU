package io.shubham0204.startwithsmollm.gpu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * LlamaGPU - GPU-accelerated LLM inference using Vulkan
 * 
 * This is a separate implementation from SmolLM that adds:
 * - Vulkan GPU acceleration support
 * - Fallback to CPU if GPU not available
 * - GPU device selection
 * 
 * Usage:
 * ```kotlin
 * val llamaGPU = LlamaGPU()
 * 
 * // Check if GPU is available
 * if (LlamaGPU.isVulkanAvailable()) {
 *     llamaGPU.load(modelPath, params, useGPU = true)
 * } else {
 *     // Fall back to original SmolLM
 * }
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
        val minP: Float = 0.05f,
        val temperature: Float = 0.7f,
        val storeChats: Boolean = true,
        val contextSize: Long? = null,
        val chatTemplate: String? = null,
        val numThreads: Int = 4,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
        val useGPU: Boolean = false,  // Disabled - Vulkan crashes on Adreno GPUs
        val gpuLayers: Int = 0
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
            params.minP,
            params.temperature,
            params.storeChats,
            params.contextSize ?: 2048L,
            params.chatTemplate ?: "",
            params.numThreads,
            params.useMmap,
            params.useMlock,
            params.useGPU,
            params.gpuLayers
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
        minP: Float,
        temperature: Float,
        storeChats: Boolean,
        contextSize: Long,
        chatTemplate: String,
        nThreads: Int,
        useMmap: Boolean,
        useMlock: Boolean,
        useGPU: Boolean,
        gpuLayers: Int
    ): Long
    
    private external fun isUsingGPU(modelPtr: Long): Boolean
    private external fun addChatMessage(modelPtr: Long, message: String, role: String)
    private external fun getResponseGenerationSpeed(modelPtr: Long): Float
    private external fun getContextSizeUsed(modelPtr: Long): Int
    private external fun close(modelPtr: Long)
    private external fun startCompletion(modelPtr: Long, prompt: String)
    private external fun completionLoop(modelPtr: Long): String
    private external fun stopCompletion(modelPtr: Long)
}
