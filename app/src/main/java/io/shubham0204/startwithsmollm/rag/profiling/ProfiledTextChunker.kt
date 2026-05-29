package io.shubham0204.startwithsmollm.rag.profiling

import io.shubham0204.startwithsmollm.rag.TextChunker

/**
 * Wrapper for TextChunker that adds profiling
 * Measures chunking latency and strategy performance
 */
class ProfiledTextChunker(
    chunkSize: Int,
    overlap: Int,
    private val strategy: TextChunker.ChunkingStrategy,
    private val profiler: Profiler
) {
    
    companion object {
        private const val COMPONENT = "TextChunker"
    }
    
    private val textChunker = TextChunker(chunkSize, overlap, strategy)
    
    /**
     * Profile text chunking
     */
    fun chunk(text: String): List<TextChunker.ChunkInfo> {
        return profiler.profile("chunk", COMPONENT) {
            profiler.recordMemory("before_chunk", COMPONENT)
            
            val startTime = System.nanoTime()
            val chunks = textChunker.chunk(text)
            val duration = (System.nanoTime() - startTime) / 1_000_000
            
            // Record chunking metrics
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "chunk",
                metricName = "input_length",
                value = text.length
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "chunk",
                metricName = "chunk_count",
                value = chunks.size
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "chunk",
                metricName = "avg_chunk_size",
                value = if (chunks.isNotEmpty()) chunks.sumOf { it.text.length } / chunks.size else 0
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "chunk",
                metricName = "strategy",
                value = strategy.name
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "chunk",
                metricName = "throughput_chars_per_sec",
                value = if (duration > 0) (text.length * 1000.0 / duration).toInt() else 0
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "chunk",
                metricName = "latency_per_chunk_ms",
                value = if (chunks.isNotEmpty()) duration / chunks.size else 0
            ))
            
            profiler.recordMemory("after_chunk", COMPONENT)
            chunks
        }
    }
}
