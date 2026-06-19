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
    private val embeddingModel = EmbeddingModel(context)  // Neural embeddings with ONNX
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
                    // Store the CONTEXTUAL text (with section header prefix) so BM25
                    // keyword search, re-ranking, and the LLM prompt all benefit from
                    // the section context, not just the semantic embedding.
                    text = structuredChunk.getContextualText(),
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
            
            // 1a. Expand the query with synonyms to improve retrieval recall.
            //     The expanded query is used for embedding and BM25 search only;
            //     the original userQuery is still used in the augmented prompt.
            val expandedQuery = expandQuery(userQuery)
            if (expandedQuery != userQuery) {
                Log.d(TAG, "Expanded query: $expandedQuery")
            }
            
            // 1b. Embed the expanded query
            // Use profiled version if available
            val queryEmbedding = profiledEmbeddingModel?.embed(expandedQuery)
                ?: embeddingModel.embed(expandedQuery)
            
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
                        query = expandedQuery,
                        topK = config.topK
                    ) ?: vectorDatabase.searchBM25(
                        query = expandedQuery,
                        topK = config.topK
                    )
                }
                SearchMode.HYBRID -> {
                    Log.d(TAG, "Using HYBRID search (semantic + BM25 with RRF)")
                    // Use profiled version if available
                    profiledVectorDatabase?.searchHybrid(
                        query = expandedQuery,
                        queryEmbedding = queryEmbedding,
                        topK = config.topK
                    ) ?: vectorDatabase.searchHybrid(
                        query = expandedQuery,
                        queryEmbedding = queryEmbedding,
                        topK = config.topK
                    )
                }
            }
            
            Log.d(TAG, "Found ${results.size} relevant chunks")
            
            // 3. Re-rank results if enabled
            val rerankedResults = if (config.enableReranking && results.size > config.finalTopK) {
                Log.d(TAG, "Re-ranking top ${config.topK} chunks to select best ${config.finalTopK}...")
                rerankChunks(userQuery, results)
            } else {
                results
            }
            
            // 4. MMR-style deduplication: ensure diversity in final chunks
            // Avoid picking near-duplicate chunks (from overlap or same paragraph region)
            val finalResults = deduplicateChunks(rerankedResults, config.finalTopK)
            
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
     * Combines: keyword overlap, position diversity, original scores, and content type
     * For small models: prioritizes exact matches more heavily
     */
    private fun rerankChunks(query: String, chunks: List<ChunkSearchResult>): List<ChunkSearchResult> {
        val queryTokens = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val queryLower = query.lowercase()
        
        // For small models, we need to be more aggressive about finding exact matches
        val isSmallModelConfig = config.finalTopK <= 2
        
        // Detect if query is asking about tabular/numeric data
        val isTableQuery = queryLower.contains(Regex("(table|row|column|total|sum|average|count|how many|list|compare|versus|vs)"))
        val isNumericQuery = queryLower.contains(Regex("(number|amount|price|cost|value|percentage|%|\\d+)"))
        // Detect causal questions ("why did X happen", "how does Y work")
        val isCausalQuery = queryLower.contains(Regex("\\b(why|how does|how do|reason|cause|because)\\b"))
        
        // For queries like "week 4", "month 2", etc., extract the specific identifier
        val specificIdentifier = extractSpecificIdentifier(queryLower)
        
        Log.d(TAG, "Re-ranking ${chunks.size} chunks for query: $queryLower")
        Log.d(TAG, "  Specific identifier detected: $specificIdentifier")
        Log.d(TAG, "  Small model config: $isSmallModelConfig")
        
        return chunks.map { result ->
            val chunkText = result.chunk.text
            val chunkLower = chunkText.lowercase()
            val chunkTokens = chunkLower.split(Regex("\\W+")).toSet()
            
            // 1. Keyword overlap score (Jaccard similarity)
            val overlap = queryTokens.intersect(chunkTokens).size.toFloat()
            val union = queryTokens.union(chunkTokens).size.toFloat()
            val keywordScore = if (union > 0) overlap / union else 0f
            
            // 2. Exact phrase matching bonus
            val exactMatchBonus = if (queryTokens.size >= 2) {
                val phrases = queryTokens.toList().windowed(2).map { it.joinToString(" ") }
                val matchCount = phrases.count { chunkLower.contains(it) }
                matchCount.toFloat() / phrases.size.coerceAtLeast(1)
            } else 0f
            
            // 3. Table content boost - if query asks about tables and chunk contains table data
            val isTableChunk = chunkText.contains("[TABLE DATA") || chunkText.contains("| ") && chunkText.count { it == '|' } > 4
            val tableBoost = when {
                isTableQuery && isTableChunk -> 0.15f
                isNumericQuery && chunkText.contains(Regex("\\d+")) -> 0.05f
                else -> 0f
            }
            
            // 3b. Causal-language boost - for "why"/"how" queries, prefer chunks
            //     that contain explanatory phrasing ("because", "due to", "reason"...)
            val causalBoost = if (isCausalQuery &&
                Regex("\\b(because|due to|so that|in order to|reason|cause|overhead|bound|bottleneck|since|therefore)\\b")
                    .containsMatchIn(chunkLower)) 0.10f else 0f
            
            // 4. Specific identifier boost (e.g., "week 4" -> boost chunks containing "week 4")
            // Check for exact match or close variations (week4, week 4, Week 4)
            val hasIdentifierMatch = if (specificIdentifier != null) {
                val normalizedChunk = chunkLower.replace(Regex("\\s+"), " ")
                val variations = listOf(
                    specificIdentifier,                           // "week 4"
                    specificIdentifier.replace(" ", ""),          // "week4"
                    specificIdentifier.replace(Regex("(\\D)(\\d)"), "$1 $2")  // ensure space
                )
                variations.any { normalizedChunk.contains(it) }
            } else false
            
            val identifierBoost = if (hasIdentifierMatch) {
                if (isSmallModelConfig) 0.40f else 0.20f  // Much higher boost for small models
            } else 0f
            
            // Log identifier matches for debugging
            if (specificIdentifier != null) {
                Log.d(TAG, "  Chunk ${result.chunk.position}: identifier match=$hasIdentifierMatch, boost=$identifierBoost")
            }
            
            // 5. Position score (slight preference for earlier content, but not too strong)
            val positionScore = 1.0f / (1.0f + result.chunk.position * 0.05f)
            
            // 6. Combined re-ranking score
            // CRITICAL: If identifier matches, prioritize it heavily
            val rerankScore = if (hasIdentifierMatch && specificIdentifier != null) {
                // Identifier match is the PRIMARY signal - boost significantly
                (0.70f + result.score * 0.20f + keywordScore * 0.10f).coerceIn(0f, 1f)
            } else {
                // No identifier match - use standard scoring
                (
                    result.score * 0.50f +           // Original retrieval score
                    keywordScore * 0.25f +           // Keyword overlap
                    exactMatchBonus * 0.15f +        // Exact phrase matching
                    tableBoost +                     // Table/numeric content boost
                    causalBoost +                    // Causal-language boost (why/how)
                    positionScore * 0.05f            // Position
                ).coerceIn(0f, 1f)
            }
            
            Log.d(TAG, "  Chunk ${result.chunk.position}: final score=${"%.3f".format(rerankScore)}, idMatch=$hasIdentifierMatch")
            
            result.copy(score = rerankScore)
        }.sortedByDescending { it.score }
    }
    
    /**
     * Expand the user query with synonyms / related terms to improve both semantic
     * embedding recall and BM25 keyword recall. Rule-based, no LLM call.
     *
     * Example: "main objective" -> "main objective goal purpose abstract aim"
     */
    private fun expandQuery(query: String): String {
        val lower = query.lowercase()
        val additions = mutableSetOf<String>()
        
        // Question-word -> related-section terms
        if (Regex("\\bobjective(s)?\\b|\\bgoal\\b|\\bpurpose\\b|\\baim\\b").containsMatchIn(lower)) {
            additions += listOf("objective", "goal", "purpose", "abstract", "aim", "introduction")
        }
        if (Regex("\\bmethod(s|ology)?\\b|\\bapproach\\b").containsMatchIn(lower)) {
            additions += listOf("method", "methodology", "approach", "procedure")
        }
        if (Regex("\\bwhy\\b|\\breason\\b|\\bcause\\b").containsMatchIn(lower)) {
            additions += listOf("because", "reason", "cause", "due to", "explanation")
        }
        if (Regex("\\bhow\\s+many\\b|\\bcount\\b|\\bnumber\\s+of\\b").containsMatchIn(lower)) {
            additions += listOf("count", "number", "total", "quantity")
        }
        if (Regex("\\bbest\\b|\\boptimal\\b|\\bhighest\\b|\\bmaximum\\b").containsMatchIn(lower)) {
            additions += listOf("best", "optimal", "highest", "maximum", "peak", "top")
        }
        if (Regex("\\bcompil(e|ed|ing|ation)\\b").containsMatchIn(lower)) {
            additions += listOf("compiled", "compilation", "torch.compile", "cuda graph")
        }
        if (Regex("\\bperformance\\b|\\bspeed\\b|\\bfast(er)?\\b").containsMatchIn(lower)) {
            additions += listOf("performance", "speed", "latency", "throughput")
        }
        if (Regex("\\bprediction(s)?\\b|\\bforecast\\b|\\bvalidat(e|ed|ion)\\b").containsMatchIn(lower)) {
            additions += listOf("prediction", "forecast", "validated", "verified", "seven")
        }
        
        // Remove tokens already present in the query
        val present = lower.split(Regex("\\W+")).toSet()
        val finalAdditions = additions.filter { it.split(Regex("\\W+")).none { tok -> present.contains(tok) } }
        
        return if (finalAdditions.isEmpty()) query
        else "$query ${finalAdditions.joinToString(" ")}"
    }
    
    /**
     * MMR-style deduplication: greedily pick highest-scoring chunks that aren't
     * too similar to already-picked chunks. Prevents returning near-duplicate
     * chunks caused by overlap or the same paragraph region.
     */
    private fun deduplicateChunks(
        ranked: List<ChunkSearchResult>,
        targetCount: Int,
        similarityThreshold: Float = 0.6f
    ): List<ChunkSearchResult> {
        if (ranked.size <= targetCount) return ranked.take(targetCount)
        
        val selected = mutableListOf<ChunkSearchResult>()
        val candidates = ranked.toMutableList()
        
        // Always take the top-ranked chunk first
        selected.add(candidates.removeAt(0))
        
        while (selected.size < targetCount && candidates.isNotEmpty()) {
            // Find the next candidate that is sufficiently different from all selected chunks
            val nextIdx = candidates.indexOfFirst { candidate ->
                selected.none { isNearDuplicate(candidate.chunk.text, it.chunk.text, similarityThreshold) }
            }
            if (nextIdx >= 0) {
                selected.add(candidates.removeAt(nextIdx))
            } else {
                // No diverse candidate found; just take the next-best (fallback)
                selected.add(candidates.removeAt(0))
            }
        }
        
        Log.d(TAG, "Deduplication: ${ranked.size} -> ${selected.size} (filtered ${ranked.size - selected.size} near-duplicates)")
        return selected
    }
    
    /**
     * Check if two chunk texts are near-duplicates using Jaccard similarity on word tokens.
     */
    private fun isNearDuplicate(a: String, b: String, threshold: Float): Boolean {
        val tokensA = a.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val tokensB = b.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return false
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        val jaccard = intersection.toFloat() / union
        return jaccard >= threshold
    }
    
    /**
     * Extract specific identifiers from queries like "week 4", "month 2", "chapter 3"
     * Returns the identifier string if found, null otherwise
     */
    private fun extractSpecificIdentifier(query: String): String? {
        // Patterns for common document references
        val patterns = listOf(
            Regex("""(week\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(month\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(chapter\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(section\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(step\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(part\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(day\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(lesson\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(unit\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(phase\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(stage\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(run\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(experiment\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(table\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(figure\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(year\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(q\s*\d+)""", RegexOption.IGNORE_CASE),  // Q1, Q2, etc.
            Regex("""(item\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(point\s*\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(version\s*\d+)""", RegexOption.IGNORE_CASE),
        )
        
        for (pattern in patterns) {
            val match = pattern.find(query)
            if (match != null) {
                // Normalize: "week4" -> "week 4", "week  4" -> "week 4"
                return match.value.replace(Regex("""\s+"""), " ").trim()
            }
        }
        
        return null
    }
    
    /**
     * Build the augmented prompt with retrieved context
     * Uses ULTRA-compact format for small models (0.5B) to reduce confusion
     * Small models need: short context, direct questions, no complex instructions
     */
    private fun buildAugmentedPrompt(query: String, chunks: List<ChunkSearchResult>): String {
        if (chunks.isEmpty()) {
            return query
        }
        
        val contextBuilder = StringBuilder()
        
        // Detect if any chunks contain table data
        val hasTableData = chunks.any { 
            it.chunk.text.contains("[TABLE DATA") || 
            it.chunk.text.contains("| ") && it.chunk.text.count { c -> c == '|' } > 4 
        }
        
        // Ultra-small model (0.5B): finalTopK = 1
        val isUltraSmallModel = config.finalTopK <= 1
        // Small model (1-2B): finalTopK = 2
        val isSmallModel = config.finalTopK <= 2
        
        when {
            isUltraSmallModel -> {
                // ULTRA-COMPACT format for 0.5B models
                // These models need the SIMPLEST possible prompt
                val chunk = chunks.firstOrNull() ?: return query
                
                // Extract only the most relevant part of the chunk (max 300 chars)
                val relevantText = extractRelevantSentences(chunk.chunk.text, query, maxChars = 300)
                
                // Super simple prompt format - no complex instructions
                contextBuilder.append("Info: $relevantText\n\n")
                contextBuilder.append("Question: $query\n")
                contextBuilder.append("Answer:")
            }
            
            isSmallModel -> {
                // COMPACT format for small models (1-2B)
                contextBuilder.append("Context:\n")
                for ((index, result) in chunks.withIndex()) {
                    val truncatedText = result.chunk.text.take(350)
                    contextBuilder.append("[${index + 1}] $truncatedText\n")
                }
                contextBuilder.append("\nQ: $query\nA:")
            }
            
            else -> {
                // FULL format for larger models (3B+)
                if (hasTableData) {
                    contextBuilder.append("Use the following context to answer the question. ")
                    contextBuilder.append("The context contains TABLE DATA - pay attention to column headers and row values. ")
                    contextBuilder.append("When answering about tables, be precise about which row/column the data comes from.\n\n")
                } else {
                    contextBuilder.append("Use the following context to answer the question. ")
                    contextBuilder.append("Cite sources using [1], [2], etc.\n\n")
                }
                contextBuilder.append("Context:\n")
                
                for ((index, result) in chunks.withIndex()) {
                    contextBuilder.append("---\n")
                    contextBuilder.append("[${index + 1}] ${result.documentName}\n")
                    contextBuilder.append(result.chunk.text)
                    contextBuilder.append("\n")
                }
                
                contextBuilder.append("---\n\n")
                contextBuilder.append("Question: $query\n\n")
                contextBuilder.append("Answer:")
            }
        }
        
        return contextBuilder.toString()
    }
    
    /**
     * Extract the most relevant sentences from a chunk based on query keywords
     * This helps small models focus on the right information
     */
    private fun extractRelevantSentences(text: String, query: String, maxChars: Int): String {
        val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        
        // Split into sentences
        val sentences = text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.isNotBlank() }
        
        if (sentences.isEmpty()) return text.take(maxChars)
        
        // Score each sentence by keyword overlap
        val scoredSentences = sentences.map { sentence ->
            val sentenceWords = sentence.lowercase().split(Regex("\\W+")).toSet()
            val overlap = queryWords.intersect(sentenceWords).size
            sentence to overlap
        }.sortedByDescending { it.second }
        
        // Take top sentences until we hit maxChars
        val result = StringBuilder()
        for ((sentence, _) in scoredSentences) {
            if (result.length + sentence.length > maxChars) break
            if (result.isNotEmpty()) result.append(" ")
            result.append(sentence.trim())
        }
        
        return if (result.isEmpty()) text.take(maxChars) else result.toString()
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
