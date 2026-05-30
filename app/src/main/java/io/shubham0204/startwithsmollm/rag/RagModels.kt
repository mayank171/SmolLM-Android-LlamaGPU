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
 * Optimized for 4K token context window
 */
data class RagConfig(
    val chunkSize: Int = 1024,          // Characters per chunk (~256 tokens)
    val chunkOverlap: Int = 100,        // 10% overlap between chunks
    val topK: Int = 5,                  // Number of chunks to retrieve (~1280 tokens)
    val similarityThreshold: Float = 0.3f,  // Minimum similarity to include
    val embeddingDimension: Int = 384   // all-MiniLM-L6-v2 dimension
)

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
