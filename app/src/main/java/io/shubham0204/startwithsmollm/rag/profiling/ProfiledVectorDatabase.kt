package io.shubham0204.startwithsmollm.rag.profiling

import android.content.Context
import io.shubham0204.startwithsmollm.rag.Chunk
import io.shubham0204.startwithsmollm.rag.ChunkSearchResult
import io.shubham0204.startwithsmollm.rag.Document
import io.shubham0204.startwithsmollm.rag.VectorDatabase

/**
 * Wrapper for VectorDatabase that adds profiling
 * Measures search latency, index operations, and memory usage
 */
class ProfiledVectorDatabase(
    context: Context,
    private val profiler: Profiler
) {
    
    companion object {
        private const val COMPONENT = "VectorDatabase"
    }
    
    val vectorDatabase = VectorDatabase(context)
    
    /**
     * Profile adding a document
     */
    fun addDocument(document: Document, chunks: List<Chunk>): Boolean {
        return profiler.profile("addDocument", COMPONENT) {
            profiler.recordMemory("before_add", COMPONENT)
            val result = vectorDatabase.addDocument(document, chunks)
            profiler.recordMemory("after_add", COMPONENT)
            
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "addDocument",
                metricName = "chunk_count",
                value = chunks.size
            ))
            result
        }
    }
    
    /**
     * Profile semantic search
     */
    fun searchSemantic(
        queryEmbedding: FloatArray,
        topK: Int = 10,
        threshold: Float = 0.0f
    ): List<ChunkSearchResult> {
        return profiler.profile("searchSemantic", COMPONENT) {
            val results = vectorDatabase.searchSemantic(queryEmbedding, topK, threshold)
            
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "searchSemantic",
                metricName = "results_found",
                value = results.size
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "searchSemantic",
                metricName = "top_score",
                value = results.firstOrNull()?.score ?: 0f
            ))
            
            profiler.recordMemory("after_semantic_search", COMPONENT)
            results
        }
    }
    
    /**
     * Profile BM25 search
     */
    fun searchBM25(
        query: String,
        topK: Int = 10
    ): List<ChunkSearchResult> {
        return profiler.profile("searchBM25", COMPONENT) {
            val results = vectorDatabase.searchBM25(query, topK)
            
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "searchBM25",
                metricName = "results_found",
                value = results.size
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "searchBM25",
                metricName = "query_terms",
                value = query.split("\\s+".toRegex()).size
            ))
            
            profiler.recordMemory("after_bm25_search", COMPONENT)
            results
        }
    }
    
    /**
     * Profile hybrid search
     */
    fun searchHybrid(
        query: String,
        queryEmbedding: FloatArray,
        topK: Int = 5,
        semanticWeight: Float = 0.6f,
        bm25Weight: Float = 0.4f
    ): List<ChunkSearchResult> {
        return profiler.profile("searchHybrid", COMPONENT) {
            profiler.recordMemory("before_hybrid", COMPONENT)
            
            val startTime = System.nanoTime()
            val results = vectorDatabase.searchHybrid(query, queryEmbedding, topK, semanticWeight, bm25Weight)
            val duration = (System.nanoTime() - startTime) / 1_000_000
            
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "searchHybrid",
                metricName = "results_found",
                value = results.size
            ))
            profiler.notify(ProfilingEvent.CustomMetric(
                operation = "searchHybrid",
                metricName = "total_latency_ms",
                value = duration
            ))
            
            profiler.recordMemory("after_hybrid", COMPONENT)
            results
        }
    }
    
}
