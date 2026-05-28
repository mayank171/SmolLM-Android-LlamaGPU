package io.shubham0204.startwithsmollm.rag

import kotlin.math.ln

/**
 * BM25 (Best Matching 25) keyword search implementation
 * 
 * BM25 is a ranking function used for text retrieval that considers:
 * - Term frequency (TF): How often a term appears in a document
 * - Inverse document frequency (IDF): How rare a term is across all documents
 * - Document length normalization: Adjusts for document length
 */
class BM25Search(
    private val k1: Float = 1.5f,  // Term frequency saturation parameter
    private val b: Float = 0.75f   // Length normalization parameter
) {
    
    // Inverted index: term -> list of (docId, positions)
    private val invertedIndex = mutableMapOf<String, MutableList<TermOccurrence>>()
    
    // Document lengths (in terms)
    private val documentLengths = mutableMapOf<String, Int>()
    
    // Average document length
    private var avgDocLength: Float = 0f
    
    // Total number of documents
    private var totalDocuments: Int = 0
    
    // Document frequency: term -> number of documents containing the term
    private val documentFrequency = mutableMapOf<String, Int>()
    
    // Stopwords to ignore
    private val stopwords = setOf(
        "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
        "be", "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "must", "shall", "can", "need", "dare", "ought",
        "used", "it", "its", "this", "that", "these", "those", "i", "you", "he",
        "she", "we", "they", "what", "which", "who", "whom", "whose", "where",
        "when", "why", "how", "all", "each", "every", "both", "few", "more",
        "most", "other", "some", "such", "no", "nor", "not", "only", "own",
        "same", "so", "than", "too", "very", "just", "also"
    )
    
    /**
     * Index a document (chunk) for BM25 search
     */
    fun indexDocument(docId: String, text: String) {
        val terms = tokenize(text)
        documentLengths[docId] = terms.size
        totalDocuments++
        
        // Update average document length
        avgDocLength = documentLengths.values.sum().toFloat() / totalDocuments
        
        // Track unique terms in this document for DF calculation
        val uniqueTerms = mutableSetOf<String>()
        
        // Build inverted index
        for ((position, term) in terms.withIndex()) {
            if (term !in uniqueTerms) {
                uniqueTerms.add(term)
                documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
            }
            
            val occurrences = invertedIndex.getOrPut(term) { mutableListOf() }
            occurrences.add(TermOccurrence(docId, position))
        }
    }
    
    /**
     * Remove a document from the index
     */
    fun removeDocument(docId: String) {
        if (docId !in documentLengths) return
        
        // Remove from inverted index
        val termsToRemove = mutableListOf<String>()
        for ((term, occurrences) in invertedIndex) {
            val hadDoc = occurrences.any { it.documentId == docId }
            occurrences.removeAll { it.documentId == docId }
            
            if (hadDoc) {
                documentFrequency[term] = (documentFrequency[term] ?: 1) - 1
                if (documentFrequency[term] == 0) {
                    documentFrequency.remove(term)
                    termsToRemove.add(term)
                }
            }
            
            if (occurrences.isEmpty()) {
                termsToRemove.add(term)
            }
        }
        
        for (term in termsToRemove) {
            invertedIndex.remove(term)
        }
        
        documentLengths.remove(docId)
        totalDocuments--
        
        // Update average document length
        avgDocLength = if (totalDocuments > 0) {
            documentLengths.values.sum().toFloat() / totalDocuments
        } else {
            0f
        }
    }
    
    /**
     * Clear the entire index
     */
    fun clear() {
        invertedIndex.clear()
        documentLengths.clear()
        documentFrequency.clear()
        totalDocuments = 0
        avgDocLength = 0f
    }
    
    /**
     * Search for documents matching the query
     * Returns list of (docId, score) sorted by score descending
     */
    fun search(query: String, topK: Int = 10): List<BM25Result> {
        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty() || totalDocuments == 0) return emptyList()
        
        val scores = mutableMapOf<String, Float>()
        
        for (term in queryTerms.distinct()) {
            val df = documentFrequency[term] ?: continue
            val idf = calculateIDF(df)
            
            val occurrences = invertedIndex[term] ?: continue
            
            // Group occurrences by document
            val docOccurrences = occurrences.groupBy { it.documentId }
            
            for ((docId, docTermOccurrences) in docOccurrences) {
                val tf = docTermOccurrences.size
                val docLength = documentLengths[docId] ?: continue
                
                val tfScore = calculateTF(tf, docLength)
                val termScore = idf * tfScore
                
                scores[docId] = (scores[docId] ?: 0f) + termScore
            }
        }
        
        return scores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { BM25Result(it.key, it.value) }
    }
    
    /**
     * Calculate IDF (Inverse Document Frequency)
     * IDF = ln((N - df + 0.5) / (df + 0.5) + 1)
     */
    private fun calculateIDF(df: Int): Float {
        return ln((totalDocuments - df + 0.5f) / (df + 0.5f) + 1f).toFloat()
    }
    
    /**
     * Calculate TF component with BM25 saturation
     * TF = (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * (docLen / avgDocLen)))
     */
    private fun calculateTF(tf: Int, docLength: Int): Float {
        val lengthNorm = 1 - b + b * (docLength / avgDocLength)
        return (tf * (k1 + 1)) / (tf + k1 * lengthNorm)
    }
    
    /**
     * Tokenize text into terms
     */
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopwords }
            .map { stem(it) }
    }
    
    /**
     * Simple stemming (Porter-like suffix stripping)
     */
    private fun stem(word: String): String {
        var result = word
        
        // Common suffixes
        val suffixes = listOf(
            "ational" to "ate",
            "tional" to "tion",
            "enci" to "ence",
            "anci" to "ance",
            "izer" to "ize",
            "isation" to "ize",
            "ization" to "ize",
            "ation" to "ate",
            "ator" to "ate",
            "alism" to "al",
            "iveness" to "ive",
            "fulness" to "ful",
            "ousness" to "ous",
            "aliti" to "al",
            "iviti" to "ive",
            "biliti" to "ble",
            "alli" to "al",
            "entli" to "ent",
            "eli" to "e",
            "ousli" to "ous",
            "ness" to "",
            "ment" to "",
            "ing" to "",
            "ings" to "",
            "ed" to "",
            "ly" to "",
            "es" to "",
            "s" to ""
        )
        
        for ((suffix, replacement) in suffixes) {
            if (result.endsWith(suffix) && result.length > suffix.length + 2) {
                result = result.dropLast(suffix.length) + replacement
                break
            }
        }
        
        return result
    }
    
    /**
     * Get statistics about the index
     */
    fun getStats(): BM25Stats {
        return BM25Stats(
            totalDocuments = totalDocuments,
            totalTerms = invertedIndex.size,
            avgDocLength = avgDocLength
        )
    }
    
    data class TermOccurrence(
        val documentId: String,
        val position: Int
    )
    
    data class BM25Result(
        val documentId: String,
        val score: Float
    )
    
    data class BM25Stats(
        val totalDocuments: Int,
        val totalTerms: Int,
        val avgDocLength: Float
    )
}
