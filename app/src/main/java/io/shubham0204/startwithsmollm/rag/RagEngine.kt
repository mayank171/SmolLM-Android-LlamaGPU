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
     * Query the RAG system using adaptive retrieval
     * Returns relevant chunks and builds an augmented prompt with citations
     */
    suspend fun query(userQuery: String): RagResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "╔═══════════════════════════════════════════════════════════════╗")
            Log.d(TAG, "║           🔍 RAG QUERY (Adaptive Retrieval)                   ║")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
            Log.d(TAG, "Query: $userQuery")
            Log.d(TAG, "Search mode: $searchMode")
            
            // 1. Embed the query
            val queryEmbedding = profiledEmbeddingModel?.embed(userQuery)
                ?: embeddingModel.embed(userQuery)
            
            // 2. STAGE 1: Quick retrieval with small topK
            Log.d(TAG, "▶ STAGE 1: Quick retrieval (topK=${config.quickTopK})")
            val quickResults = performSearch(userQuery, queryEmbedding, config.quickTopK)
            
            // 3. STAGE 2: Analyze retrieved chunks
            val analysis = analyzeRetrievedChunks(quickResults)
            Log.d(TAG, "▶ STAGE 2: Analysis - tables=${analysis.tableCount}, topScore=${String.format("%.3f", analysis.topScore)}, avgScore=${String.format("%.3f", analysis.avgScore)}")
            
            // 4. STAGE 3: Decide optimal topK based on analysis
            val optimalTopK = determineOptimalTopK(analysis)
            Log.d(TAG, "▶ STAGE 3: Optimal topK determined: $optimalTopK (reason: ${analysis.reason})")
            
            // 5. STAGE 4: Retrieve with optimal topK if needed
            val results = if (optimalTopK > config.quickTopK) {
                Log.d(TAG, "▶ STAGE 4: Expanding retrieval to topK=$optimalTopK")
                performSearch(userQuery, queryEmbedding, optimalTopK)
            } else {
                Log.d(TAG, "▶ STAGE 4: Using quick results (sufficient)")
                quickResults
            }
            
            Log.d(TAG, "Found ${results.size} relevant chunks")
            
            // 3. Re-rank results if enabled
            val finalResults = if (config.enableReranking && results.size > config.finalTopK) {
                Log.d(TAG, "Re-ranking top ${config.topK} chunks to select best ${config.finalTopK}...")
                rerankChunks(userQuery, results).take(config.finalTopK)
            } else {
                results.take(config.finalTopK)
            }
            
            Log.d(TAG, "Final ${finalResults.size} chunks after re-ranking:")
            for ((i, result) in finalResults.withIndex()) {
                Log.d(TAG, "  [${i+1}] Score: ${"%.3f".format(result.score)} | ${result.searchType} | ${result.documentName}")
                Log.d(TAG, "      Preview: ${result.chunk.text.take(80)}...")
            }
            
            // 4. Generate citations
            val citations = finalResults.mapIndexed { index, result ->
                Citation(
                    index = index + 1,
                    documentName = result.documentName,
                    chunkText = result.chunk.text.take(200) + if (result.chunk.text.length > 200) "..." else "",
                    score = result.score,
                    chunkPosition = result.chunk.position
                )
            }
            
            // 5. Build augmented prompt
            val augmentedPrompt = buildAugmentedPrompt(userQuery, finalResults)
            
            Log.d(TAG, "Augmented prompt length: ${augmentedPrompt.length} chars")
            
            RagResult(
                query = userQuery,
                retrievedChunks = finalResults,
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
     * Re-rank chunks by query relevance using multiple signals
     * Combines: keyword overlap, position diversity, and original scores
     */
    private fun rerankChunks(query: String, chunks: List<ChunkSearchResult>): List<ChunkSearchResult> {
        val queryTokens = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        
        return chunks.map { result ->
            val chunkTokens = result.chunk.text.lowercase().split(Regex("\\W+")).toSet()
            
            // 1. Keyword overlap score (Jaccard similarity)
            val overlap = queryTokens.intersect(chunkTokens).size.toFloat()
            val union = queryTokens.union(chunkTokens).size.toFloat()
            val keywordScore = if (union > 0) overlap / union else 0f
            
            // 2. Query term frequency in chunk
            val termFrequency = queryTokens.sumOf { token ->
                chunkTokens.count { it == token }
            }.toFloat() / chunkTokens.size.coerceAtLeast(1)
            
            // 3. Position diversity bonus (prefer chunks from different positions)
            val positionScore = 1.0f / (1.0f + result.chunk.position * 0.1f)
            
            // 4. Combined re-ranking score
            val rerankScore = (
                result.score * 0.4f +           // Original retrieval score
                keywordScore * 0.3f +            // Keyword overlap
                termFrequency * 0.2f +           // Term frequency
                positionScore * 0.1f             // Position diversity
            ).coerceIn(0f, 1f)
            
            result.copy(score = rerankScore)
        }.sortedByDescending { it.score }
    }
    
    /**
     * Build the augmented prompt with retrieved context
     */
    private fun buildAugmentedPrompt(query: String, chunks: List<ChunkSearchResult>): String {
        if (chunks.isEmpty()) {
            return query
        }
        
        val contextBuilder = StringBuilder()
        
        // Check if context contains tables
        val hasTables = chunks.any { it.chunk.text.contains("STRUCTURED TABLE DATA") }
        
        // Base instructions
        contextBuilder.append("Use the following context to answer the question. ")
        contextBuilder.append("Cite sources using [1], [2], etc. when referencing information.\n\n")
        
        // Add table-specific instructions if tables are present
        if (hasTables) {
            contextBuilder.append("⚠️ IMPORTANT - TABLES IN CONTEXT:\n")
            contextBuilder.append("The context contains STRUCTURED TABLES with precise data.\n")
            contextBuilder.append("When reading tables:\n")
            contextBuilder.append("• Pay close attention to column headers and row labels\n")
            contextBuilder.append("• Numbers in tables are EXACT values - do not approximate\n")
            contextBuilder.append("• Do NOT confuse different columns or rows\n")
            contextBuilder.append("• Read table captions to understand what the table shows\n")
            contextBuilder.append("• If a specific value is not in the table, say 'I cannot find this information'\n")
            contextBuilder.append("• Do NOT make up numbers or trends not shown in the tables\n\n")
        }
        
        contextBuilder.append("Context:\n")
        
        for ((index, result) in chunks.withIndex()) {
            contextBuilder.append("---\n")
            contextBuilder.append("[${index + 1}] Source: ${result.documentName}\n")
            contextBuilder.append(result.chunk.text)
            contextBuilder.append("\n")
        }
        
        contextBuilder.append("---\n\n")
        contextBuilder.append("Question: $query\n\n")
        
        // Reinforce for tables
        if (hasTables) {
            contextBuilder.append("Remember: Read tables carefully. Use exact numbers from the tables. ")
            contextBuilder.append("If the answer is not in the tables, say so.\n\n")
        }
        
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
     * Perform search with specified topK
     */
    private suspend fun performSearch(
        query: String,
        queryEmbedding: FloatArray,
        topK: Int
    ): List<ChunkSearchResult> {
        return when (searchMode) {
            SearchMode.SEMANTIC -> {
                profiledVectorDatabase?.searchSemantic(
                    queryEmbedding = queryEmbedding,
                    topK = topK,
                    threshold = config.similarityThreshold
                ) ?: vectorDatabase.searchSemantic(
                    queryEmbedding = queryEmbedding,
                    topK = topK,
                    threshold = config.similarityThreshold
                )
            }
            SearchMode.BM25 -> {
                profiledVectorDatabase?.searchBM25(
                    query = query,
                    topK = topK
                ) ?: vectorDatabase.searchBM25(
                    query = query,
                    topK = topK
                )
            }
            SearchMode.HYBRID -> {
                profiledVectorDatabase?.searchHybrid(
                    query = query,
                    queryEmbedding = queryEmbedding,
                    topK = topK
                ) ?: vectorDatabase.searchHybrid(
                    query = query,
                    queryEmbedding = queryEmbedding,
                    topK = topK
                )
            }
        }
    }
    
    /**
     * Analysis result for retrieved chunks
     */
    private data class ChunkAnalysis(
        val tableCount: Int,
        val topScore: Float,
        val avgScore: Float,
        val hasMultipleTables: Boolean,
        val hasSingleTable: Boolean,
        val hasNoTables: Boolean,
        val isHighConfidence: Boolean,
        val isLowConfidence: Boolean,
        val reason: String
    )
    
    /**
     * Analyze retrieved chunks to determine optimal retrieval strategy
     */
    private fun analyzeRetrievedChunks(chunks: List<ChunkSearchResult>): ChunkAnalysis {
        if (chunks.isEmpty()) {
            return ChunkAnalysis(
                tableCount = 0,
                topScore = 0f,
                avgScore = 0f,
                hasMultipleTables = false,
                hasSingleTable = false,
                hasNoTables = true,
                isHighConfidence = false,
                isLowConfidence = true,
                reason = "No results found"
            )
        }
        
        val tableChunks = chunks.filter { 
            it.chunk.text.contains("STRUCTURED TABLE DATA") 
        }
        val tableCount = tableChunks.size
        val topScore = chunks.firstOrNull()?.score ?: 0f
        val avgScore = chunks.map { it.score }.average().toFloat()
        
        val hasMultipleTables = tableCount >= 2
        val hasSingleTable = tableCount == 1
        val hasNoTables = tableCount == 0
        val isHighConfidence = topScore >= config.highConfidenceThreshold
        val isLowConfidence = topScore < config.lowConfidenceThreshold
        
        // Determine reason
        val reason = when {
            hasMultipleTables -> "Multiple tables detected"
            hasSingleTable -> "Single table detected"
            isLowConfidence -> "Low confidence score"
            isHighConfidence && hasNoTables -> "High confidence, no tables"
            else -> "Normal retrieval"
        }
        
        return ChunkAnalysis(
            tableCount = tableCount,
            topScore = topScore,
            avgScore = avgScore,
            hasMultipleTables = hasMultipleTables,
            hasSingleTable = hasSingleTable,
            hasNoTables = hasNoTables,
            isHighConfidence = isHighConfidence,
            isLowConfidence = isLowConfidence,
            reason = reason
        )
    }
    
    /**
     * Determine optimal topK based on chunk analysis
     */
    private fun determineOptimalTopK(analysis: ChunkAnalysis): Int {
        return when {
            // High confidence, no tables = simple question
            analysis.isHighConfidence && analysis.hasNoTables -> {
                config.simpleTopK
            }
            
            // Multiple tables = complex data question
            analysis.hasMultipleTables -> {
                config.tableTopK
            }
            
            // Single table = moderate data question
            analysis.hasSingleTable -> {
                (config.tableTopK + config.topK) / 2  // Average of table and default
            }
            
            // Low confidence = need more context
            analysis.isLowConfidence -> {
                config.lowConfidenceTopK
            }
            
            // Default
            else -> {
                config.topK
            }
        }
    }
    
    /**
     * Update RAG configuration
     */
    fun updateConfig(newConfig: RagConfig) {
        config = newConfig
    }
    
    /**
     * Update RAG configuration based on loaded model parameters
     * Adjusts retrieval settings based on model size and context window
     */
    fun updateModelConfig(modelParameters: String, contextSize: Int) {
        Log.d(TAG, "Updating RAG config for model: $modelParameters, context: $contextSize")
        
        // Adjust topK based on context size
        // Larger context = can handle more chunks
        val adjustedTopK = when {
            contextSize >= 8000 -> 8  // Large context, can use more chunks
            contextSize >= 4000 -> 6  // Medium context
            else -> 4  // Small context, use fewer chunks
        }
        
        // Adjust chunk sizes based on model size
        val (chunkSize, overlap) = when {
            modelParameters.contains("135M") -> Pair(384, 50)   // Smaller model, smaller chunks
            modelParameters.contains("360M") -> Pair(512, 64)   // Medium model
            modelParameters.contains("1.7B") -> Pair(640, 80)   // Larger model, larger chunks
            else -> Pair(512, 64)  // Default
        }
        
        // Update configuration
        config = config.copy(
            topK = adjustedTopK,
            chunkSize = chunkSize,
            chunkOverlap = overlap
        )
        
        Log.d(TAG, "RAG config updated: topK=$adjustedTopK, chunkSize=$chunkSize, overlap=$overlap")
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
