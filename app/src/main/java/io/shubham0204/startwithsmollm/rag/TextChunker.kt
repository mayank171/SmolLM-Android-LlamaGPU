package io.shubham0204.startwithsmollm.rag

import java.text.BreakIterator
import java.util.Locale

/**
 * Advanced text chunker with multiple strategies:
 * - Sentence-aware chunking (respects sentence boundaries)
 * - Paragraph-based chunking (for structured documents)
 * - Semantic chunking (groups related sentences)
 * - Hierarchical chunks (parent-child relationships)
 */
class TextChunker(
    private val chunkSize: Int = 512,
    private val overlap: Int = 100,
    private val strategy: ChunkingStrategy = ChunkingStrategy.SENTENCE_AWARE
) {
    
    enum class ChunkingStrategy {
        FIXED_SIZE,      // Simple fixed-size chunks
        SENTENCE_AWARE,  // Respects sentence boundaries
        PARAGRAPH,       // Chunks by paragraphs
        SEMANTIC         // Groups semantically related sentences
    }
    
    /**
     * Split text into chunks using the configured strategy
     */
    fun chunk(text: String): List<ChunkInfo> {
        if (text.isBlank()) return emptyList()
        
        val cleanedText = preprocessText(text)
        
        if (cleanedText.length <= chunkSize) {
            return listOf(ChunkInfo(
                text = cleanedText,
                startChar = 0,
                endChar = cleanedText.length,
                metadata = ChunkMetadata(
                    sentenceCount = countSentences(cleanedText),
                    paragraphIndex = 0,
                    isComplete = true
                )
            ))
        }
        
        return when (strategy) {
            ChunkingStrategy.FIXED_SIZE -> chunkFixedSize(cleanedText)
            ChunkingStrategy.SENTENCE_AWARE -> chunkSentenceAware(cleanedText)
            ChunkingStrategy.PARAGRAPH -> chunkByParagraph(cleanedText)
            ChunkingStrategy.SEMANTIC -> chunkSemantic(cleanedText)
        }
    }
    
    /**
     * Preprocess text: normalize whitespace, remove excessive newlines
     */
    private fun preprocessText(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\t", " ")
            .replace(Regex(" {2,}"), " ")      // Multiple spaces to single
            .replace(Regex("\\n{3,}"), "\n\n") // Max 2 newlines
            .trim()
    }
    
    /**
     * Split text into sentences using BreakIterator
     */
    private fun splitIntoSentences(text: String): List<SentenceInfo> {
        val sentences = mutableListOf<SentenceInfo>()
        val iterator = BreakIterator.getSentenceInstance(Locale.US)
        iterator.setText(text)
        
        var start = iterator.first()
        var end = iterator.next()
        
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) {
                sentences.add(SentenceInfo(sentence, start, end))
            }
            start = end
            end = iterator.next()
        }
        
        return sentences
    }
    
    /**
     * Split text into paragraphs
     */
    private fun splitIntoParagraphs(text: String): List<ParagraphInfo> {
        val paragraphs = mutableListOf<ParagraphInfo>()
        val parts = text.split(Regex("\\n\\n+"))
        var offset = 0
        
        for ((index, part) in parts.withIndex()) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                val start = text.indexOf(trimmed, offset)
                paragraphs.add(ParagraphInfo(
                    text = trimmed,
                    startChar = start,
                    endChar = start + trimmed.length,
                    index = index
                ))
                offset = start + trimmed.length
            }
        }
        
        return paragraphs
    }
    
    /**
     * Fixed-size chunking (original simple approach)
     */
    private fun chunkFixedSize(text: String): List<ChunkInfo> {
        val chunks = mutableListOf<ChunkInfo>()
        var startIndex = 0
        var position = 0
        
        while (startIndex < text.length) {
            var endIndex = minOf(startIndex + chunkSize, text.length)
            
            // Try to break at word boundary
            if (endIndex < text.length) {
                val lastSpace = text.lastIndexOf(' ', endIndex)
                if (lastSpace > startIndex + chunkSize / 2) {
                    endIndex = lastSpace + 1
                }
            }
            
            val chunkText = text.substring(startIndex, endIndex).trim()
            if (chunkText.isNotEmpty()) {
                chunks.add(ChunkInfo(
                    text = chunkText,
                    startChar = startIndex,
                    endChar = endIndex,
                    metadata = ChunkMetadata(
                        sentenceCount = countSentences(chunkText),
                        paragraphIndex = position,
                        isComplete = false
                    )
                ))
                position++
            }
            
            startIndex = endIndex - overlap
            if (startIndex >= text.length - overlap) break
        }
        
        return chunks
    }
    
    /**
     * Sentence-aware chunking: never breaks mid-sentence
     */
    private fun chunkSentenceAware(text: String): List<ChunkInfo> {
        val sentences = splitIntoSentences(text)
        if (sentences.isEmpty()) return chunkFixedSize(text)
        
        val chunks = mutableListOf<ChunkInfo>()
        var currentChunk = StringBuilder()
        var chunkStart = sentences.first().startChar
        var sentencesInChunk = 0
        var position = 0
        var overlapSentences = mutableListOf<SentenceInfo>()
        
        for (sentence in sentences) {
            val potentialLength = currentChunk.length + sentence.text.length + 1
            
            if (potentialLength > chunkSize && currentChunk.isNotEmpty()) {
                // Save current chunk
                chunks.add(ChunkInfo(
                    text = currentChunk.toString().trim(),
                    startChar = chunkStart,
                    endChar = sentence.startChar,
                    metadata = ChunkMetadata(
                        sentenceCount = sentencesInChunk,
                        paragraphIndex = position,
                        isComplete = true
                    )
                ))
                position++
                
                // Start new chunk with overlap (last N sentences)
                currentChunk = StringBuilder()
                val overlapSize = calculateOverlapSentences(overlapSentences)
                for (overlapSentence in overlapSentences.takeLast(overlapSize)) {
                    currentChunk.append(overlapSentence.text).append(" ")
                }
                chunkStart = if (overlapSentences.isNotEmpty()) {
                    overlapSentences.takeLast(overlapSize).firstOrNull()?.startChar ?: sentence.startChar
                } else {
                    sentence.startChar
                }
                sentencesInChunk = overlapSize
                overlapSentences.clear()
            }
            
            currentChunk.append(sentence.text).append(" ")
            sentencesInChunk++
            overlapSentences.add(sentence)
            
            // Keep only recent sentences for overlap
            if (overlapSentences.size > 5) {
                overlapSentences.removeAt(0)
            }
        }
        
        // Add remaining chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(ChunkInfo(
                text = currentChunk.toString().trim(),
                startChar = chunkStart,
                endChar = text.length,
                metadata = ChunkMetadata(
                    sentenceCount = sentencesInChunk,
                    paragraphIndex = position,
                    isComplete = true
                )
            ))
        }
        
        return chunks
    }
    
    /**
     * Paragraph-based chunking: keeps paragraphs together when possible
     */
    private fun chunkByParagraph(text: String): List<ChunkInfo> {
        val paragraphs = splitIntoParagraphs(text)
        if (paragraphs.isEmpty()) return chunkSentenceAware(text)
        
        val chunks = mutableListOf<ChunkInfo>()
        var currentChunk = StringBuilder()
        var chunkStart = 0
        var paragraphsInChunk = mutableListOf<Int>()
        var position = 0
        
        for (paragraph in paragraphs) {
            // If single paragraph is too large, use sentence chunking for it
            if (paragraph.text.length > chunkSize) {
                // Save current chunk first
                if (currentChunk.isNotEmpty()) {
                    chunks.add(ChunkInfo(
                        text = currentChunk.toString().trim(),
                        startChar = chunkStart,
                        endChar = paragraph.startChar,
                        metadata = ChunkMetadata(
                            sentenceCount = countSentences(currentChunk.toString()),
                            paragraphIndex = position,
                            isComplete = true,
                            paragraphIndices = paragraphsInChunk.toList()
                        )
                    ))
                    position++
                    currentChunk = StringBuilder()
                    paragraphsInChunk.clear()
                }
                
                // Chunk the large paragraph
                val subChunks = chunkSentenceAware(paragraph.text)
                for (subChunk in subChunks) {
                    chunks.add(ChunkInfo(
                        text = subChunk.text,
                        startChar = paragraph.startChar + subChunk.startChar,
                        endChar = paragraph.startChar + subChunk.endChar,
                        metadata = subChunk.metadata.copy(
                            paragraphIndex = position,
                            paragraphIndices = listOf(paragraph.index)
                        )
                    ))
                    position++
                }
                chunkStart = paragraph.endChar
                continue
            }
            
            val potentialLength = currentChunk.length + paragraph.text.length + 2
            
            if (potentialLength > chunkSize && currentChunk.isNotEmpty()) {
                // Save current chunk
                chunks.add(ChunkInfo(
                    text = currentChunk.toString().trim(),
                    startChar = chunkStart,
                    endChar = paragraph.startChar,
                    metadata = ChunkMetadata(
                        sentenceCount = countSentences(currentChunk.toString()),
                        paragraphIndex = position,
                        isComplete = true,
                        paragraphIndices = paragraphsInChunk.toList()
                    )
                ))
                position++
                currentChunk = StringBuilder()
                chunkStart = paragraph.startChar
                paragraphsInChunk.clear()
            }
            
            currentChunk.append(paragraph.text).append("\n\n")
            paragraphsInChunk.add(paragraph.index)
        }
        
        // Add remaining chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(ChunkInfo(
                text = currentChunk.toString().trim(),
                startChar = chunkStart,
                endChar = text.length,
                metadata = ChunkMetadata(
                    sentenceCount = countSentences(currentChunk.toString()),
                    paragraphIndex = position,
                    isComplete = true,
                    paragraphIndices = paragraphsInChunk.toList()
                )
            ))
        }
        
        return chunks
    }
    
    /**
     * Semantic chunking: groups sentences by topic similarity
     * Uses simple heuristics (shared words, pronouns, etc.)
     */
    private fun chunkSemantic(text: String): List<ChunkInfo> {
        val sentences = splitIntoSentences(text)
        if (sentences.size < 3) return chunkSentenceAware(text)
        
        val chunks = mutableListOf<ChunkInfo>()
        var currentGroup = mutableListOf<SentenceInfo>()
        var position = 0
        
        for (i in sentences.indices) {
            val sentence = sentences[i]
            currentGroup.add(sentence)
            
            val currentLength = currentGroup.sumOf { it.text.length + 1 }
            
            // Check if we should break here
            val shouldBreak = when {
                currentLength >= chunkSize -> true
                i == sentences.lastIndex -> true
                isSemanticBoundary(sentences, i) && currentLength >= chunkSize / 2 -> true
                else -> false
            }
            
            if (shouldBreak && currentGroup.isNotEmpty()) {
                val chunkText = currentGroup.joinToString(" ") { it.text }
                chunks.add(ChunkInfo(
                    text = chunkText,
                    startChar = currentGroup.first().startChar,
                    endChar = currentGroup.last().endChar,
                    metadata = ChunkMetadata(
                        sentenceCount = currentGroup.size,
                        paragraphIndex = position,
                        isComplete = true
                    )
                ))
                position++
                
                // Keep last sentence for context overlap
                val lastSentence = currentGroup.last()
                currentGroup.clear()
                if (i < sentences.lastIndex) {
                    currentGroup.add(lastSentence)
                }
            }
        }
        
        return chunks
    }
    
    /**
     * Detect semantic boundaries using simple heuristics
     */
    private fun isSemanticBoundary(sentences: List<SentenceInfo>, index: Int): Boolean {
        if (index >= sentences.lastIndex) return true
        
        val current = sentences[index].text.lowercase()
        val next = sentences[index + 1].text.lowercase()
        
        // Topic change indicators
        val topicChangeWords = listOf(
            "however", "but", "although", "nevertheless", "on the other hand",
            "in contrast", "meanwhile", "furthermore", "additionally",
            "first", "second", "third", "finally", "in conclusion",
            "for example", "for instance", "specifically"
        )
        
        // Check if next sentence starts with topic change word
        for (word in topicChangeWords) {
            if (next.startsWith(word)) return true
        }
        
        // Check for heading-like patterns (short sentence followed by longer)
        if (current.length < 50 && !current.contains(".") && next.length > 100) {
            return true
        }
        
        // Check word overlap (low overlap = topic change)
        val currentWords = current.split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        val nextWords = next.split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        val overlap = currentWords.intersect(nextWords).size
        val minSize = minOf(currentWords.size, nextWords.size)
        
        if (minSize > 0 && overlap.toFloat() / minSize < 0.1f) {
            return true
        }
        
        return false
    }
    
    /**
     * Calculate how many sentences to include in overlap
     */
    private fun calculateOverlapSentences(sentences: List<SentenceInfo>): Int {
        if (sentences.isEmpty()) return 0
        
        var totalLength = 0
        var count = 0
        
        for (sentence in sentences.reversed()) {
            if (totalLength + sentence.text.length > overlap) break
            totalLength += sentence.text.length
            count++
        }
        
        return maxOf(1, count)
    }
    
    /**
     * Count sentences in text
     */
    private fun countSentences(text: String): Int {
        val iterator = BreakIterator.getSentenceInstance(Locale.US)
        iterator.setText(text)
        var count = 0
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            count++
            start = end
            end = iterator.next()
        }
        return maxOf(1, count)
    }
    
    // Data classes
    
    data class ChunkInfo(
        val text: String,
        val startChar: Int,
        val endChar: Int,
        val metadata: ChunkMetadata = ChunkMetadata()
    )
    
    data class ChunkMetadata(
        val sentenceCount: Int = 0,
        val paragraphIndex: Int = 0,
        val isComplete: Boolean = true,
        val paragraphIndices: List<Int> = emptyList()
    )
    
    private data class SentenceInfo(
        val text: String,
        val startChar: Int,
        val endChar: Int
    )
    
    private data class ParagraphInfo(
        val text: String,
        val startChar: Int,
        val endChar: Int,
        val index: Int
    )
}
