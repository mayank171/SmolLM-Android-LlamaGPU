package io.shubham0204.startwithsmollm.rag

import java.util.UUID

/**
 * Represents a document uploaded by the user
 */
data class Document(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val type: DocumentType,
    val addedAt: Long = System.currentTimeMillis(),
    val chunkCount: Int = 0,
    val sizeBytes: Long = 0
)

enum class DocumentType {
    PDF,
    TXT,
    MARKDOWN,
    IMAGE,
    UNKNOWN
}

/**
 * A chunk of text from a document with its embedding
 */
data class Chunk(
    val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val text: String,
    val position: Int,          // Position in document (0, 1, 2, ...)
    val startChar: Int,         // Character offset in original document
    val endChar: Int,
    val embedding: FloatArray   // Vector embedding
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Chunk
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Type of search used to find the chunk
 */
enum class SearchType {
    SEMANTIC,   // Embedding-based similarity
    BM25,       // Keyword-based BM25
    HYBRID      // Combined semantic + BM25
}

/**
 * Search result with similarity score
 */
data class ChunkSearchResult(
    val chunk: Chunk,
    val score: Float,           // Similarity score (0-1 normalized)
    val documentName: String,
    val searchType: SearchType = SearchType.SEMANTIC
)

/**
 * RAG configuration
 * Adaptive settings based on model size
 */
data class RagConfig(
    val chunkSize: Int = 768,           // Characters per chunk (~192 tokens) - balanced for quality & speed
    val chunkOverlap: Int = 150,        // ~20% overlap between chunks for better context continuity
    val topK: Int = 7,                  // Retrieve 7 chunks for re-ranking (balanced for context)
    val similarityThreshold: Float = 0.35f,  // Higher threshold = better quality chunks
    val embeddingDimension: Int = 384,  // all-MiniLM-L6-v2 dimension
    val enableReranking: Boolean = true,    // Re-rank retrieved chunks by relevance
    val finalTopK: Int = 3              // Return top 3 after re-ranking - optimized for small models
) {
    companion object {
        /**
         * Get optimized RAG config based on model parameters
         * @param modelParams Model size (e.g., "0.5B", "1.5B", "3B", "7B")
         * @param contextSize Model's max context size
         */
        fun forModel(modelParams: String, contextSize: Int = 4096): RagConfig {
            // Extract parameter count (e.g., "1.5B" -> 1.5)
            val paramCount = modelParams.replace("B", "").replace("M", "").toFloatOrNull() ?: 0.5f
            val isMillion = modelParams.contains("M")
            val actualParams = if (isMillion) paramCount / 1000f else paramCount
            
            return when {
                // Ultra-small models (< 1B): SmolLM 360M, Qwen 0.5B
                actualParams < 1.0f -> RagConfig(
                    chunkSize = 512,                    // Smaller chunks for limited capacity
                    chunkOverlap = 100,
                    topK = 5,                           // Fewer candidates
                    similarityThreshold = 0.40f,        // Very strict - only best matches
                    finalTopK = 2,                      // Only 2 chunks to avoid confusion
                    enableReranking = true
                )
                
                // Small models (1B - 2B): TinyLlama 1.1B, Qwen 1.5B, Gemma 2B
                actualParams < 2.5f -> RagConfig(
                    chunkSize = 768,                    // Standard chunks
                    chunkOverlap = 150,
                    topK = 7,
                    similarityThreshold = 0.35f,        // Strict threshold
                    finalTopK = 3,                      // 3 chunks - balanced
                    enableReranking = true
                )
                
                // Medium models (2.5B - 4B): Phi-3.5 Mini, Llama 3.2 3B, Qwen 3B
                actualParams < 4.5f -> RagConfig(
                    chunkSize = 1024,                   // Larger chunks for better context
                    chunkOverlap = 200,
                    topK = 10,                          // More candidates for re-ranking
                    similarityThreshold = 0.30f,        // Moderate threshold
                    finalTopK = 5,                      // 5 chunks - more context
                    enableReranking = true
                )
                
                // Large models (7B+): Mistral 7B, OpenHermes 7B
                else -> RagConfig(
                    chunkSize = 1536,                   // Large chunks for rich context
                    chunkOverlap = 300,
                    topK = 15,                          // Many candidates
                    similarityThreshold = 0.25f,        // Lower threshold - model can handle it
                    finalTopK = 7,                      // 7 chunks - maximum context
                    enableReranking = true
                )
            }
        }
        
        /**
         * Default config for small models (backward compatible)
         */
        fun default() = forModel("0.5B", 4096)
    }
}

/**
 * RAG query result
 */
data class RagResult(
    val query: String,
    val retrievedChunks: List<ChunkSearchResult>,
    val augmentedPrompt: String,
    val response: String? = null,
    val citations: List<Citation> = emptyList()
)

/**
 * Citation for a retrieved chunk
 */
data class Citation(
    val index: Int,                 // [1], [2], etc.
    val documentName: String,
    val chunkText: String,
    val score: Float,
    val chunkPosition: Int          // Position in original document
) {
    fun toShortString(): String = "[$index] $documentName"
    fun toDetailedString(): String = "[$index] $documentName (relevance: ${(score * 100).toInt()}%)"
}

/**
 * Profiling metrics for a RAG operation
 */
data class RagMetrics(
    val operation: String,
    val totalLatencyMs: Long,
    val embeddingLatencyMs: Long = 0,
    val searchLatencyMs: Long = 0,
    val parsingLatencyMs: Long = 0,
    val chunkingLatencyMs: Long = 0,
    val memoryUsedBytes: Long = 0,
    val chunksProcessed: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
