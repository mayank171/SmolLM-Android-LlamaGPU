package io.shubham0204.startwithsmollm.rag.profiling

import android.content.Context
import io.shubham0204.startwithsmollm.rag.EmbeddingModel

/**
 * Wrapper for EmbeddingModel that adds profiling
 * Measures initialization time, embedding generation latency, and memory usage
 */
class ProfiledEmbeddingModel(
    private val embeddingModel: EmbeddingModel,
    private val profiler: Profiler
) {
    
    companion object {
        private const val COMPONENT = "EmbeddingModel"
    }
    
    /**
     * Profile initialization
     */
    suspend fun initialize(): Boolean {
        return profiler.profile("initialize", COMPONENT) {
            profiler.recordMemory("before_init", COMPONENT)
            val result = embeddingModel.initialize()
            profiler.recordMemory("after_init", COMPONENT)
            result
        }
    }
    
    /**
     * Profile single embedding generation
     */
    suspend fun embed(text: String): FloatArray {
        return profiler.profile("embed", COMPONENT) {
            profiler.recordMemory("before_embed", COMPONENT)
            
            val startTime = System.nanoTime()
            val result = embeddingModel.embed(text)
            val duration = (System.nanoTime() - startTime) / 1_000_000
            
            // Record detailed metrics
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "embed",
                metricName = "text_length",
                value = text.length
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "embed",
                metricName = "embedding_dim",
                value = result.size
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "embed",
                metricName = "throughput_chars_per_sec",
                value = if (duration > 0) (text.length * 1000.0 / duration).toInt() else 0
            ))
            
            profiler.recordMemory("after_embed", COMPONENT)
            result
        }
    }
    
    /**
     * Profile batch embedding generation
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        return profiler.profile("embedBatch", COMPONENT) {
            profiler.recordMemory("before_batch", COMPONENT)
            
            val startTime = System.nanoTime()
            val results = texts.map { embed(it) }
            val duration = (System.nanoTime() - startTime) / 1_000_000
            
            // Record batch metrics
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "embedBatch",
                metricName = "batch_size",
                value = texts.size
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "embedBatch",
                metricName = "avg_latency_per_item",
                value = if (texts.isNotEmpty()) duration / texts.size else 0
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "embedBatch",
                metricName = "total_chars",
                value = texts.sumOf { it.length }
            ))
            
            profiler.recordMemory("after_batch", COMPONENT)
            results
        }
    }
}
