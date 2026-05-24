package io.shubham0204.startwithsmollm.rag

/**
 * Splits text into overlapping chunks for RAG
 */
class TextChunker(
    private val chunkSize: Int = 512,
    private val overlap: Int = 50
) {
    
    /**
     * Split text into chunks with overlap
     * Tries to split at sentence boundaries when possible
     */
    fun chunk(text: String): List<ChunkInfo> {
        if (text.isBlank()) return emptyList()
        
        val cleanedText = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("\\n{3,}"), "\n\n")  // Max 2 newlines
            .trim()
        
        if (cleanedText.length <= chunkSize) {
            return listOf(ChunkInfo(cleanedText, 0, cleanedText.length))
        }
        
        val chunks = mutableListOf<ChunkInfo>()
        var startIndex = 0
        
        while (startIndex < cleanedText.length) {
            var endIndex = minOf(startIndex + chunkSize, cleanedText.length)
            
            // Try to find a good break point (sentence end)
            if (endIndex < cleanedText.length) {
                val searchStart = maxOf(startIndex + chunkSize - 100, startIndex)
                val searchEnd = minOf(startIndex + chunkSize + 50, cleanedText.length)
                val searchRegion = cleanedText.substring(searchStart, searchEnd)
                
                // Look for sentence boundaries
                val sentenceEnd = findBestBreakPoint(searchRegion)
                if (sentenceEnd != -1) {
                    endIndex = searchStart + sentenceEnd + 1
                }
            }
            
            val chunkText = cleanedText.substring(startIndex, endIndex).trim()
            if (chunkText.isNotEmpty()) {
                chunks.add(ChunkInfo(chunkText, startIndex, endIndex))
            }
            
            // Move start, accounting for overlap
            startIndex = endIndex - overlap
            if (startIndex >= cleanedText.length - overlap) break
        }
        
        return chunks
    }
    
    /**
     * Find the best break point in a text region
     * Prefers: paragraph > sentence > clause > word
     */
    private fun findBestBreakPoint(text: String): Int {
        // Try paragraph break first
        val paragraphBreak = text.lastIndexOf("\n\n")
        if (paragraphBreak > text.length / 3) return paragraphBreak + 1
        
        // Try sentence end (. ! ?)
        val sentenceEnders = listOf(". ", "! ", "? ", ".\n", "!\n", "?\n")
        for (ender in sentenceEnders) {
            val idx = text.lastIndexOf(ender)
            if (idx > text.length / 3) return idx + ender.length - 1
        }
        
        // Try clause break (, ; :)
        val clauseBreaks = listOf(", ", "; ", ": ")
        for (breaker in clauseBreaks) {
            val idx = text.lastIndexOf(breaker)
            if (idx > text.length / 2) return idx + breaker.length - 1
        }
        
        // Try word break
        val spaceIdx = text.lastIndexOf(' ')
        if (spaceIdx > text.length / 2) return spaceIdx
        
        return -1  // No good break point found
    }
    
    data class ChunkInfo(
        val text: String,
        val startChar: Int,
        val endChar: Int
    )
}
