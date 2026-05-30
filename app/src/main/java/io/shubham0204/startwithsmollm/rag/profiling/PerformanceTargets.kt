package io.shubham0204.startwithsmollm.rag.profiling

/**
 * Performance targets for RAG and LLM inference
 * Based on industry best practices and user experience research
 */
object PerformanceTargets {
    
    /**
     * RAG Performance Targets
     */
    object RAG {
        // Document processing (parsing + extraction)
        const val DOCUMENT_PROCESSING_GOOD_MS = 2000L      // < 2s = Good
        const val DOCUMENT_PROCESSING_ACCEPTABLE_MS = 5000L // 2-5s = Acceptable
        // Why: Users tolerate 2-3 seconds for file uploads (Nielsen Norman Group)
        
        // Embedding generation per chunk
        const val EMBEDDING_GOOD_MS = 30L                  // < 30ms = Good
        const val EMBEDDING_ACCEPTABLE_MS = 50L            // 30-50ms = Acceptable
        // Why: MiniLM-L6 on mobile typically runs at 20-30ms per chunk
        
        // Search performance (vector + BM25)
        const val SEARCH_GOOD_MS = 100L                    // < 100ms = Good
        const val SEARCH_ACCEPTABLE_MS = 200L              // 100-200ms = Acceptable
        // Why: < 100ms feels instant (Google's search latency target)
        
        // Total RAG query time (embed + search)
        const val TOTAL_QUERY_GOOD_MS = 150L               // < 150ms = Good
        const val TOTAL_QUERY_ACCEPTABLE_MS = 300L         // 150-300ms = Acceptable
        // Why: Embedding (30ms) + Search (100ms) + overhead (20ms) = 150ms
    }
    
    /**
     * LLM Inference Targets (Mobile On-Device)
     */
    object LLM {
        // Time to first token (TTFT) - Mobile realistic targets
        const val TTFT_GOOD_MS = 3000L                     // < 3s = Good
        const val TTFT_ACCEPTABLE_MS = 15000L              // 3-15s = Acceptable
        // Why: On-device 1-2B models need to process prompt + KV cache setup
        //      With RAG context (~500-1000 tokens), 3-15s is realistic on mobile
        
        // Inter-token latency (ITL) - Mobile realistic targets
        const val ITL_GOOD_MS = 150L                       // < 150ms = Good (~7 tokens/sec)
        const val ITL_ACCEPTABLE_MS = 300L                 // 150-300ms = Acceptable (~3-7 tokens/sec)
        // Why: Mobile GPUs/NPUs achieve 3-10 tokens/sec for 1-2B models
        //      7 tok/s = ~28 words/sec (still faster than reading speed)
        
        // Total generation time for typical response (100 tokens)
        const val GENERATION_GOOD_MS = 10000L              // < 10s = Good
        const val GENERATION_ACCEPTABLE_MS = 20000L        // 10-20s = Acceptable
        // Why: 100 tokens at 100ms/token = 10 seconds
        
        // RAM usage during inference (MB)
        const val RAM_GOOD_MB = 200L                       // < 200MB = Good
        const val RAM_ACCEPTABLE_MB = 400L                 // 200-400MB = Acceptable
        // Why: Mobile devices typically have 4-8GB RAM, app should use < 5%
        
        // Battery drain per 1000 tokens (mAh estimated)
        const val BATTERY_GOOD_MAH = 5L                    // < 5mAh/1000 tokens = Good
        const val BATTERY_ACCEPTABLE_MAH = 10L             // 5-10mAh/1000 tokens = Acceptable
        // Why: Typical phone battery ~4000mAh, should support 400K+ tokens per charge
    }
    
    /**
     * Get target description for display
     */
    fun getTargetDescription(metric: String): String {
        return when (metric) {
            "document_processing" -> "< ${RAG.DOCUMENT_PROCESSING_GOOD_MS}ms"
            "embedding" -> "< ${RAG.EMBEDDING_GOOD_MS}ms"
            "search" -> "< ${RAG.SEARCH_GOOD_MS}ms"
            "total_rag_query" -> "< ${RAG.TOTAL_QUERY_GOOD_MS}ms"
            "ttft" -> "< ${LLM.TTFT_GOOD_MS}ms"
            "itl" -> "< ${LLM.ITL_GOOD_MS}ms"
            "ram" -> "< ${LLM.RAM_GOOD_MB}MB"
            "battery" -> "< ${LLM.BATTERY_GOOD_MAH}mAh/1K"
            else -> "—"
        }
    }
    
    /**
     * Evaluate performance status
     */
    fun evaluateStatus(value: Double, goodThreshold: Long, acceptableThreshold: Long): MetricStatus {
        return when {
            value <= 0 -> MetricStatus.UNKNOWN
            value < goodThreshold -> MetricStatus.GOOD
            value < acceptableThreshold -> MetricStatus.ACCEPTABLE
            else -> MetricStatus.SLOW
        }
    }
}

enum class MetricStatus {
    GOOD,       // Meeting target (green)
    ACCEPTABLE, // Close to target (orange)
    SLOW,       // Below target (red)
    UNKNOWN     // No data yet (gray)
}
