package io.shubham0204.startwithsmollm.rag

import android.content.Context
import android.net.Uri
import android.util.Log
import io.shubham0204.startwithsmollm.rag.profiling.Profiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main RAG (Retrieval-Augmented Generation) engine
 * Coordinates document processing, embedding, storage, and retrieval
 */
class RagEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "RagEngine"
    }
    
    // Configuration
    private var config = RagConfig()
    private var searchMode = SearchMode.HYBRID  // Default to hybrid search
    
    // Profiler instance (lazy initialized, null if not available)
    private val profiler by lazy { 
        if (Profiler.isInitialized()) {
            Profiler.getInstance(context)
        } else null
    }
    
    // Core components
    private val documentParser = DocumentParser(context)
    private val textChunker = TextChunker(
        chunkSize = config.chunkSize,
        overlap = config.chunkOverlap,
        strategy = TextChunker.ChunkingStrategy.SEMANTIC  // Groups related sentences by topic
    )
    private val structuredChunker = StructuredChunker(chunkSize = 512, chunkOverlap = 50)
    private val embeddingModel = EmbeddingModel(context)
    private val vectorDatabase = VectorDatabase(context)
    
    // Profiled wrappers (when profiler is available)
    private val profiledDocumentParser by lazy {
        profiler?.let { io.shubham0204.startwithsmollm.rag.profiling.ProfiledDocumentParser(context, it) }
    }
    private val profiledTextChunker by lazy {
        profiler?.let {
            io.shubham0204.startwithsmollm.rag.profiling.ProfiledTextChunker(
                chunkSize = config.chunkSize,
                overlap = config.chunkOverlap,
                strategy = TextChunker.ChunkingStrategy.SEMANTIC,  // Groups related sentences by topic
                profiler = it
            )
        }
    }
    private val profiledEmbeddingModel by lazy {
        profiler?.let { io.shubham0204.startwithsmollm.rag.profiling.ProfiledEmbeddingModel(embeddingModel, it) }
    }
    private val profiledVectorDatabase by lazy {
        profiler?.let { io.shubham0204.startwithsmollm.rag.profiling.ProfiledVectorDatabase(context, it) }
    }
    
    enum class SearchMode {
        SEMANTIC,  // Only embedding similarity
        BM25,      // Only keyword search
        HYBRID     // Combined (recommended)
    }
    
    /**
     * Initialize the RAG engine
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Use profiled version if available
            val success = profiledEmbeddingModel?.initialize() ?: embeddingModel.initialize()
            Log.d(TAG, "RAG engine initialized")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RAG engine: ${e.message}")
            false
        }
    }
    
    /**
     * Add a document from URI
     * Parses, chunks, embeds, and stores the document
     */
    suspend fun addDocument(uri: Uri): AddDocumentResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "╔═══════════════════════════════════════════════════════════════╗")
            Log.d(TAG, "║           📥 RAG ENGINE: ADD DOCUMENT                         ║")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
            Log.d(TAG, "URI: $uri")
            
            // 1. Parse document with enhanced extraction (tables, images, OCR)
            Log.d(TAG, "")
            Log.d(TAG, "▶ STEP 1: PARSING DOCUMENT (Enhanced Mode)...")
            // Use profiled version if available
            val parseResult = profiledDocumentParser?.let {
                it.parse(uri)
            } ?: documentParser.parsePdfEnhanced(uri, documentParser.getFileName(uri))
            
            when (parseResult) {
                is DocumentParser.ParseResult.Error -> {
                    Log.e(TAG, "❌ PARSE FAILED: ${parseResult.message}")
                    return@withContext AddDocumentResult.Error(parseResult.message)
                }
                is DocumentParser.ParseResult.NeedsOcr -> {
                    Log.e(TAG, "❌ OCR REQUIRED BUT FAILED")
                    return@withContext AddDocumentResult.Error("Document requires OCR but OCR failed")
                }
                is DocumentParser.ParseResult.Success -> { /* continue */ }
            }
            
            val parsed = parseResult as DocumentParser.ParseResult.Success
            Log.d(TAG, "✅ PARSE SUCCESS")
            Log.d(TAG, "   File: ${parsed.fileName}")
            Log.d(TAG, "   Type: ${parsed.type}")
            Log.d(TAG, "   Size: ${parsed.sizeBytes / 1024} KB")
            Log.d(TAG, "   Text length: ${parsed.text.length} chars")
            Log.d(TAG, "   Tables extracted: ${parsed.tables.size}")
            Log.d(TAG, "   Images extracted: ${parsed.images.size}")
            Log.d(TAG, "   Used OCR: ${parsed.usedOcr}")
            
            // 2. Chunk the text using structure-aware chunker
            // Use combined text that includes tables and images
            Log.d(TAG, "")
            Log.d(TAG, "▶ STEP 2: CHUNKING TEXT (with tables & images)...")
            val combinedText = parsed.getCombinedText()
            Log.d(TAG, "   Combined text length: ${combinedText.length} chars")
            val structuredChunks = structuredChunker.chunk(combinedText, parsed.fileName)
            
            if (structuredChunks.isEmpty()) {
                Log.e(TAG, "❌ CHUNKING PRODUCED 0 CHUNKS!")
                Log.e(TAG, "   This usually means the text was blank or too short")
                return@withContext AddDocumentResult.Error("Document produced no chunks")
            }
            
            // Log chunk type distribution
            val typeDistribution = structuredChunks.groupBy { it.type }.mapValues { it.value.size }
            Log.d(TAG, "✅ CHUNKING SUCCESS: ${structuredChunks.size} chunks")
            Log.d(TAG, "   Distribution: $typeDistribution")
            
            // 3. Create document record
            val document = Document(
                name = parsed.fileName,
                path = uri.toString(),
                type = parsed.type,
                chunkCount = structuredChunks.size,
                sizeBytes = parsed.sizeBytes
            )
            
            // 4. Generate embeddings and create chunk records
            Log.d(TAG, "")
            Log.d(TAG, "▶ STEP 3: GENERATING EMBEDDINGS...")
            val startTime = System.currentTimeMillis()
            
            val chunks = structuredChunks.mapIndexed { index, structuredChunk ->
                val textForEmbedding = structuredChunk.getContextualText()
                // Use profiled version if available
                val embedding = profiledEmbeddingModel?.embed(textForEmbedding) 
                    ?: embeddingModel.embed(textForEmbedding)
                if (index == 0) {
                    Log.d(TAG, "   Embedding dimension: ${embedding.size}")
                }
                Chunk(
                    documentId = document.id,
                    text = structuredChunk.text,  // Store original text
                    position = structuredChunk.position,
                    startChar = index * 512,  // Approximate
                    endChar = (index + 1) * 512,
                    embedding = embedding
                )
            }
            
            val embeddingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ EMBEDDINGS GENERATED: ${chunks.size} embeddings in ${embeddingTime}ms")
            Log.d(TAG, "   Avg time per chunk: ${embeddingTime / chunks.size}ms")
            
            // 5. Store in database
            Log.d(TAG, "")
            Log.d(TAG, "▶ STEP 4: STORING IN DATABASE...")
            // Use profiled version if available
            val success = profiledVectorDatabase?.addDocument(document, chunks)
                ?: vectorDatabase.addDocument(document, chunks)
            if (!success) {
                Log.e(TAG, "❌ DATABASE STORAGE FAILED")
                return@withContext AddDocumentResult.Error("Failed to store document in database")
            }
            
            Log.d(TAG, "╔═══════════════════════════════════════════════════════════════╗")
            Log.d(TAG, "║  ✅ DOCUMENT ADDED SUCCESSFULLY                               ║")
            Log.d(TAG, "║  Name: ${document.name.take(50)}")
            Log.d(TAG, "║  Chunks: ${document.chunkCount}")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
            
            AddDocumentResult.Success(document)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION: ${e.message}", e)
            AddDocumentResult.Error("Error processing document: ${e.message}")
        }
    }
    
    /**
     * Query the RAG system using the configured search mode
     * Returns relevant chunks and builds an augmented prompt with citations
     */
    suspend fun query(userQuery: String): RagResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "╔═══════════════════════════════════════════════════════════════╗")
            Log.d(TAG, "║           🔍 RAG QUERY                                        ║")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
            Log.d(TAG, "Query: $userQuery")
            Log.d(TAG, "Search mode: $searchMode")
            
            // 1. Embed the query
            // Use profiled version if available
            val queryEmbedding = profiledEmbeddingModel?.embed(userQuery)
                ?: embeddingModel.embed(userQuery)
            
            // 2. Search using configured mode
            val results = when (searchMode) {
                SearchMode.SEMANTIC -> {
                    Log.d(TAG, "Using SEMANTIC search (embeddings only)")
                    // Use profiled version if available
                    profiledVectorDatabase?.searchSemantic(
                        queryEmbedding = queryEmbedding,
                        topK = config.topK,
                        threshold = config.similarityThreshold
                    ) ?: vectorDatabase.searchSemantic(
                        queryEmbedding = queryEmbedding,
                        topK = config.topK,
                        threshold = config.similarityThreshold
                    )
                }
                SearchMode.BM25 -> {
                    Log.d(TAG, "Using BM25 search (keywords only)")
                    // Use profiled version if available
                    profiledVectorDatabase?.searchBM25(
                        query = userQuery,
                        topK = config.topK
                    ) ?: vectorDatabase.searchBM25(
                        query = userQuery,
                        topK = config.topK
                    )
                }
                SearchMode.HYBRID -> {
                    Log.d(TAG, "Using HYBRID search (semantic + BM25 with RRF)")
                    // Use profiled version if available
                    profiledVectorDatabase?.searchHybrid(
                        query = userQuery,
                        queryEmbedding = queryEmbedding,
                        topK = config.topK
                    ) ?: vectorDatabase.searchHybrid(
                        query = userQuery,
                        queryEmbedding = queryEmbedding,
                        topK = config.topK
                    )
                }
            }
            
            Log.d(TAG, "Found ${results.size} relevant chunks")
            for ((i, result) in results.withIndex()) {
                Log.d(TAG, "  [${i+1}] Score: ${"%.3f".format(result.score)} | ${result.searchType} | ${result.documentName}")
                Log.d(TAG, "      Preview: ${result.chunk.text.take(80)}...")
            }
            
            // 3. Generate citations
            val citations = results.mapIndexed { index, result ->
                Citation(
                    index = index + 1,
                    documentName = result.documentName,
                    chunkText = result.chunk.text.take(200) + if (result.chunk.text.length > 200) "..." else "",
                    score = result.score,
                    chunkPosition = result.chunk.position
                )
            }
            
            // 4. Build augmented prompt
            val augmentedPrompt = buildAugmentedPrompt(userQuery, results)
            
            Log.d(TAG, "Augmented prompt length: ${augmentedPrompt.length} chars")
            
            RagResult(
                query = userQuery,
                retrievedChunks = results,
                augmentedPrompt = augmentedPrompt,
                citations = citations
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in RAG query: ${e.message}", e)
            RagResult(
                query = userQuery,
                retrievedChunks = emptyList(),
                augmentedPrompt = userQuery  // Fallback to original query
            )
        }
    }
    
    /**
     * Set the search mode
     */
    fun setSearchMode(mode: SearchMode) {
        searchMode = mode
        Log.d(TAG, "Search mode set to: $mode")
    }
    
    /**
     * Get current search mode
     */
    fun getSearchMode(): SearchMode = searchMode
    
    /**
     * Build the augmented prompt with retrieved context
     */
    private fun buildAugmentedPrompt(query: String, chunks: List<ChunkSearchResult>): String {
        if (chunks.isEmpty()) {
            return query
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("Use the following context to answer the question. ")
        contextBuilder.append("Cite sources using [1], [2], etc. when referencing information.\n\n")
        contextBuilder.append("Context:\n")
        
        for ((index, result) in chunks.withIndex()) {
            contextBuilder.append("---\n")
            contextBuilder.append("[${index + 1}] Source: ${result.documentName}\n")
            // SEMANTIC chunks are already focused and complete - use them as-is
            contextBuilder.append(result.chunk.text)
            contextBuilder.append("\n")
        }
        
        contextBuilder.append("---\n\n")
        contextBuilder.append("Question: $query\n\n")
        contextBuilder.append("Answer:")
        
        return contextBuilder.toString()
    }
    
    /**
     * Check if RAG has any documents
     */
    fun hasDocuments(): Boolean {
        return vectorDatabase.getTotalChunkCount() > 0
    }
    
    /**
     * Get all stored documents
     */
    fun getDocuments(): List<Document> {
        return vectorDatabase.getAllDocuments()
    }
    
    /**
     * Delete a specific document
     */
    fun deleteDocument(documentId: String): Boolean {
        return vectorDatabase.deleteDocument(documentId)
    }
    
    /**
     * Delete all documents
     */
    fun deleteAllDocuments(): Int {
        return vectorDatabase.deleteAllDocuments()
    }
    
    /**
     * Get RAG statistics
     */
    fun getStats(): RagStats {
        val documents = vectorDatabase.getAllDocuments()
        return RagStats(
            documentCount = documents.size,
            totalChunks = vectorDatabase.getTotalChunkCount(),
            databaseSizeBytes = vectorDatabase.getDatabaseSize(),
            usingNeuralEmbeddings = embeddingModel.isUsingNeuralEmbeddings()
        )
    }
    
    /**
     * Check if using neural embeddings
     */
    fun isUsingNeuralEmbeddings(): Boolean = embeddingModel.isUsingNeuralEmbeddings()
    
    /**
     * Update RAG configuration
     */
    fun updateConfig(newConfig: RagConfig) {
        config = newConfig
    }
    
    fun close() {
        embeddingModel.close()
        vectorDatabase.close()
    }
    
    sealed class AddDocumentResult {
        data class Success(val document: Document) : AddDocumentResult()
        data class Error(val message: String) : AddDocumentResult()
    }
    
    data class RagStats(
        val documentCount: Int,
        val totalChunks: Int,
        val databaseSizeBytes: Long,
        val usingNeuralEmbeddings: Boolean = false
    ) {
        val databaseSizeMB: Float get() = databaseSizeBytes / (1024f * 1024f)
        val embeddingType: String get() = if (usingNeuralEmbeddings) "Neural (MiniLM)" else "TF-IDF"
    }
}
