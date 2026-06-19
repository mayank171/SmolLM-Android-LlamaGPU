package io.shubham0204.startwithsmollm.rag

import android.util.Log

/**
 * Structure-aware text chunker that preserves document hierarchy
 * Handles: headers, sections, lists, tables, code blocks
 * 
 * Uses recursive splitting (LangChain-style) with structure awareness
 */
class StructuredChunker(
    private val chunkSize: Int = 1024,
    private val chunkOverlap: Int = 100,
    private val preserveStructure: Boolean = true
) {
    
    companion object {
        private const val TAG = "StructuredChunker"
        // Separators in order of preference (most to least preferred)
        private val SEPARATORS = listOf(
            "\n\n\n",       // Major section break
            "\n\n",         // Paragraph break
            "\n",           // Line break
            ". ",           // Sentence end
            "? ",           // Question end
            "! ",           // Exclamation end
            "; ",           // Clause break
            ", ",           // Comma break
            " "             // Word break (last resort)
        )
        
        // Patterns for detecting structure
        private val HEADER_PATTERN = Regex("^(#{1,6}\\s+.+|[A-Z][^.!?]*:)$", RegexOption.MULTILINE)
        private val LIST_ITEM_PATTERN = Regex("^(\\s*[-*•]\\s+|\\s*\\d+\\.\\s+)", RegexOption.MULTILINE)
        private val TABLE_PATTERN = Regex("\\|[^|]+\\|")
        private val CODE_BLOCK_PATTERN = Regex("```[\\s\\S]*?```|`[^`]+`")
    }
    
    /**
     * Main chunking function - returns structured chunks with metadata
     */
    fun chunk(text: String, documentName: String = ""): List<StructuredChunk> {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🔪 CHUNKING START: $documentName")
        Log.d(TAG, "   Input text length: ${text.length} chars")
        Log.d(TAG, "   Chunk size: $chunkSize, Overlap: $chunkOverlap")
        Log.d(TAG, "═══════════════════════════════════════════════════")
        
        if (text.isBlank()) {
            Log.w(TAG, "⚠️ Input text is BLANK - returning empty chunks")
            return emptyList()
        }
        
        val cleanedText = preprocessText(text)
        Log.d(TAG, "   After preprocessing: ${cleanedText.length} chars")
        
        // First, extract structural elements
        val elements = extractElements(cleanedText)
        Log.d(TAG, "   Extracted ${elements.size} structural elements")
        
        // Log element type distribution
        val elementTypes = elements.groupBy { it.type }.mapValues { it.value.size }
        Log.d(TAG, "   Element types: $elementTypes")
        
        // Then chunk each element appropriately
        val chunks = mutableListOf<StructuredChunk>()
        var globalPosition = 0
        var currentSection = ""
        
        for (element in elements) {
            // Track current section for context
            if (element.type == ElementType.HEADER) {
                currentSection = element.content.take(100)
            }
            
            // Chunk the element
            val elementChunks = chunkElement(element, currentSection, globalPosition)
            chunks.addAll(elementChunks)
            globalPosition += elementChunks.size
        }
        
        // Final summary
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "✅ CHUNKING COMPLETE")
        Log.d(TAG, "   Total chunks created: ${chunks.size}")
        val chunkTypes = chunks.groupBy { it.type }.mapValues { it.value.size }
        Log.d(TAG, "   Chunk types: $chunkTypes")
        if (chunks.isNotEmpty()) {
            val avgChunkSize = chunks.map { it.text.length }.average().toInt()
            val minChunkSize = chunks.minOf { it.text.length }
            val maxChunkSize = chunks.maxOf { it.text.length }
            Log.d(TAG, "   Chunk sizes - Avg: $avgChunkSize, Min: $minChunkSize, Max: $maxChunkSize")
            Log.d(TAG, "   First chunk preview: ${chunks.first().text.take(100).replace("\n", "↵")}...")
        }
        Log.d(TAG, "═══════════════════════════════════════════════════")
        
        return chunks
    }
    
    /**
     * Preprocess text - normalize whitespace and line endings
     */
    private fun preprocessText(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\t", "    ")
            .replace(Regex("\\n{4,}"), "\n\n\n")  // Max 3 newlines
            .replace(Regex(" {3,}"), "  ")        // Max 2 spaces
            .trim()
    }
    
    /**
     * Extract structural elements from text
     * Strategy: Split by paragraphs, then merge small ones to reach target chunk size
     */
    private fun extractElements(text: String): List<DocumentElement> {
        val elements = mutableListOf<DocumentElement>()
        
        // Split by paragraph breaks (double newline)
        var rawSections = text.split(Regex("\n\n+"))
        Log.d(TAG, "   Initial split by \\n\\n: ${rawSections.size} sections")
        
        // If very few sections for the text size, the PDF likely has no paragraph breaks
        // In this case, split by single newlines but then MERGE them back to target size
        if (rawSections.size < 5 && text.length > 5000) {
            Log.d(TAG, "   PDF has no paragraph breaks, splitting by lines and merging")
            rawSections = text.split(Regex("\n"))
        }
        
        // Merge small consecutive sections to reach ~chunkSize
        val mergedSections = mergeSections(rawSections)
        Log.d(TAG, "   After merging: ${mergedSections.size} sections")
        
        for (section in mergedSections) {
            if (section.isBlank()) continue
            
            val trimmed = section.trim()
            if (trimmed.length < 20) continue  // Skip very short sections
            
            val type = detectElementType(trimmed)
            
            elements.add(DocumentElement(
                type = type,
                content = trimmed,
                metadata = extractMetadata(trimmed, type)
            ))
        }
        
        return elements
    }
    
    /**
     * Merge small sections together to reach target chunk size
     * This prevents creating too many tiny chunks
     */
    private fun mergeSections(sections: List<String>): List<String> {
        val merged = mutableListOf<String>()
        val currentMerge = StringBuilder()
        
        for (section in sections) {
            val trimmed = section.trim()
            if (trimmed.isEmpty()) continue
            
            // Check if this looks like a header (should not be merged)
            val isHeader = trimmed.length < 100 && (
                trimmed.startsWith("#") ||
                trimmed == trimmed.uppercase() && trimmed.any { it.isLetter() } ||
                trimmed.matches(Regex("^\\d+(\\.\\d+)*\\.?\\s+[A-Z].*"))
            )
            
            if (isHeader && currentMerge.isNotEmpty()) {
                // Save current merge before header
                merged.add(currentMerge.toString().trim())
                currentMerge.clear()
                merged.add(trimmed)
            } else if (currentMerge.length + trimmed.length < chunkSize * 0.9) {
                // Merge if combined size is under target
                if (currentMerge.isNotEmpty()) currentMerge.append("\n\n")
                currentMerge.append(trimmed)
            } else {
                // Current merge is big enough, save it
                if (currentMerge.isNotEmpty()) {
                    merged.add(currentMerge.toString().trim())
                    currentMerge.clear()
                }
                // Start new merge or add directly if section is already big
                if (trimmed.length >= chunkSize * 0.8) {
                    merged.add(trimmed)
                } else {
                    currentMerge.append(trimmed)
                }
            }
        }
        
        // Don't forget the last merge
        if (currentMerge.isNotEmpty()) {
            merged.add(currentMerge.toString().trim())
        }
        
        return merged
    }
    
    /**
     * Detect the type of a text element
     * Note: Headers must be short (< 200 chars) to avoid misclassifying large text blocks
     */
    private fun detectElementType(text: String): ElementType {
        val firstLine = text.lines().firstOrNull() ?: return ElementType.PARAGRAPH
        
        // If text is too long, it's definitely not a header - treat as paragraph
        if (text.length > 500) {
            return ElementType.PARAGRAPH
        }
        
        return when {
            // Markdown headers (must be single line or very short)
            firstLine.startsWith("#") && text.lines().size <= 2 -> ElementType.HEADER
            
            // Underlined headers (===== or -----)
            text.lines().size == 2 && 
                text.lines().getOrNull(1)?.matches(Regex("^[=-]+$")) == true -> ElementType.HEADER
            
            // ALL CAPS headers (must be short)
            firstLine.length < 80 && firstLine == firstLine.uppercase() && 
                firstLine.any { it.isLetter() } && text.lines().size == 1 -> ElementType.HEADER
            
            // Numbered section (1. Title or 1.2.3 Title) - must be short
            firstLine.length < 100 && 
                firstLine.matches(Regex("^\\d+(\\.\\d+)*\\.?\\s+[A-Z].*")) -> ElementType.HEADER
            
            // Tables (contains | characters in multiple lines)
            text.lines().count { TABLE_PATTERN.containsMatchIn(it) } >= 2 -> ElementType.TABLE
            
            // Code blocks
            text.startsWith("```") || text.contains(CODE_BLOCK_PATTERN) -> ElementType.CODE_BLOCK
            
            // Lists (starts with - * • or number.)
            LIST_ITEM_PATTERN.containsMatchIn(firstLine) -> ElementType.LIST
            
            // Default to paragraph
            else -> ElementType.PARAGRAPH
        }
    }
    
    /**
     * Extract metadata from element
     */
    private fun extractMetadata(text: String, type: ElementType): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        
        when (type) {
            ElementType.HEADER -> {
                // Extract header level
                val level = when {
                    text.startsWith("######") -> "6"
                    text.startsWith("#####") -> "5"
                    text.startsWith("####") -> "4"
                    text.startsWith("###") -> "3"
                    text.startsWith("##") -> "2"
                    text.startsWith("#") -> "1"
                    else -> "1"
                }
                metadata["level"] = level
                metadata["title"] = text.trimStart('#').trim()
            }
            ElementType.TABLE -> {
                val rows = text.lines().filter { it.contains("|") }
                metadata["rows"] = rows.size.toString()
            }
            ElementType.LIST -> {
                val items = text.lines().filter { LIST_ITEM_PATTERN.containsMatchIn(it) }
                metadata["items"] = items.size.toString()
            }
            else -> {}
        }
        
        return metadata
    }
    
    /**
     * Chunk a single element appropriately based on its type
     */
    private fun chunkElement(
        element: DocumentElement,
        currentSection: String,
        startPosition: Int
    ): List<StructuredChunk> {
        val chunks = mutableListOf<StructuredChunk>()
        
        when (element.type) {
            ElementType.HEADER -> {
                // Headers are usually small, keep as single chunk
                chunks.add(StructuredChunk(
                    text = element.content,
                    type = element.type,
                    position = startPosition,
                    sectionContext = "",
                    metadata = element.metadata
                ))
            }
            
            ElementType.TABLE -> {
                // Keep tables together if possible, or split by rows
                chunks.addAll(chunkTable(element, currentSection, startPosition))
            }
            
            ElementType.CODE_BLOCK -> {
                // Try to keep code blocks together
                chunks.addAll(chunkCodeBlock(element, currentSection, startPosition))
            }
            
            ElementType.LIST -> {
                // Keep list items together when possible
                chunks.addAll(chunkList(element, currentSection, startPosition))
            }
            
            ElementType.PARAGRAPH -> {
                // Use recursive splitting for paragraphs
                chunks.addAll(recursiveChunk(element.content, currentSection, startPosition))
            }
        }
        
        return chunks
    }
    
    /**
     * Chunk a table - try to keep together, split by rows if too large
     * IMPORTANT: Always preserve header context and add row summaries for better retrieval
     */
    private fun chunkTable(
        element: DocumentElement,
        sectionContext: String,
        startPosition: Int
    ): List<StructuredChunk> {
        val chunks = mutableListOf<StructuredChunk>()
        val content = element.content
        
        // For smaller tables, keep them whole
        if (content.length <= chunkSize * 1.5) {  // Allow slightly larger tables to stay whole
            chunks.add(StructuredChunk(
                text = content,
                type = ElementType.TABLE,
                position = startPosition,
                sectionContext = sectionContext,
                metadata = element.metadata
            ))
            return chunks
        }
        
        // For large tables, split intelligently
        val lines = content.lines()
        
        // Find header lines (first row + separator if markdown table)
        val headerEndIndex = lines.indexOfFirst { it.contains("---") }.let { if (it >= 0) it + 1 else 2 }
        val headerLines = lines.take(headerEndIndex)
        val header = headerLines.joinToString("\n")
        val headerRow = headerLines.firstOrNull { it.contains("|") } ?: ""
        
        // Extract column names for context
        val columnNames = headerRow.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val tableContext = if (columnNames.isNotEmpty()) {
            "[Table columns: ${columnNames.joinToString(", ")}]\n"
        } else ""
        
        var currentChunk = StringBuilder(tableContext + header)
        var rowsInChunk = 0
        var pos = startPosition
        
        for (i in headerEndIndex until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            
            val potentialLength = currentChunk.length + line.length + 1
            
            // Split when chunk gets too large, but ensure at least 3 data rows per chunk
            if (potentialLength > chunkSize && rowsInChunk >= 3) {
                // Add summary of what rows this chunk contains
                val chunkText = currentChunk.toString()
                chunks.add(StructuredChunk(
                    text = chunkText,
                    type = ElementType.TABLE,
                    position = pos++,
                    sectionContext = sectionContext,
                    metadata = mapOf(
                        "partial" to "true",
                        "rows" to rowsInChunk.toString(),
                        "columns" to columnNames.size.toString()
                    )
                ))
                // Start new chunk with header context
                currentChunk = StringBuilder(tableContext + header + "\n" + line)
                rowsInChunk = 1
            } else {
                currentChunk.append("\n").append(line)
                if (line.contains("|")) rowsInChunk++
            }
        }
        
        // Add final chunk
        if (currentChunk.isNotEmpty() && currentChunk.toString() != tableContext + header) {
            chunks.add(StructuredChunk(
                text = currentChunk.toString(),
                type = ElementType.TABLE,
                position = pos,
                sectionContext = sectionContext,
                metadata = element.metadata + mapOf("rows" to rowsInChunk.toString())
            ))
        }
        
        return chunks
    }
    
    /**
     * Chunk code blocks - try to keep together
     */
    private fun chunkCodeBlock(
        element: DocumentElement,
        sectionContext: String,
        startPosition: Int
    ): List<StructuredChunk> {
        val chunks = mutableListOf<StructuredChunk>()
        val content = element.content
        
        if (content.length <= chunkSize) {
            chunks.add(StructuredChunk(
                text = content,
                type = ElementType.CODE_BLOCK,
                position = startPosition,
                sectionContext = sectionContext,
                metadata = element.metadata
            ))
        } else {
            // Split by functions/methods or logical blocks
            val lines = content.lines()
            var currentChunk = StringBuilder()
            var pos = startPosition
            
            for (line in lines) {
                if (currentChunk.length + line.length + 1 > chunkSize && currentChunk.isNotEmpty()) {
                    chunks.add(StructuredChunk(
                        text = currentChunk.toString().trim(),
                        type = ElementType.CODE_BLOCK,
                        position = pos++,
                        sectionContext = sectionContext,
                        metadata = mapOf("partial" to "true")
                    ))
                    currentChunk = StringBuilder()
                }
                currentChunk.append(line).append("\n")
            }
            
            if (currentChunk.isNotEmpty()) {
                chunks.add(StructuredChunk(
                    text = currentChunk.toString().trim(),
                    type = ElementType.CODE_BLOCK,
                    position = pos,
                    sectionContext = sectionContext,
                    metadata = element.metadata
                ))
            }
        }
        
        return chunks
    }
    
    /**
     * Chunk lists - keep items together when possible
     */
    private fun chunkList(
        element: DocumentElement,
        sectionContext: String,
        startPosition: Int
    ): List<StructuredChunk> {
        val chunks = mutableListOf<StructuredChunk>()
        val content = element.content
        
        if (content.length <= chunkSize) {
            chunks.add(StructuredChunk(
                text = content,
                type = ElementType.LIST,
                position = startPosition,
                sectionContext = sectionContext,
                metadata = element.metadata
            ))
        } else {
            // Split by list items
            val items = content.split(Regex("(?=^\\s*[-*•]\\s+|^\\s*\\d+\\.\\s+)", RegexOption.MULTILINE))
                .filter { it.isNotBlank() }
            
            var currentChunk = StringBuilder()
            var pos = startPosition
            
            for (item in items) {
                if (currentChunk.length + item.length > chunkSize && currentChunk.isNotEmpty()) {
                    chunks.add(StructuredChunk(
                        text = currentChunk.toString().trim(),
                        type = ElementType.LIST,
                        position = pos++,
                        sectionContext = sectionContext,
                        metadata = mapOf("partial" to "true")
                    ))
                    currentChunk = StringBuilder()
                }
                currentChunk.append(item)
            }
            
            if (currentChunk.isNotEmpty()) {
                chunks.add(StructuredChunk(
                    text = currentChunk.toString().trim(),
                    type = ElementType.LIST,
                    position = pos,
                    sectionContext = sectionContext,
                    metadata = element.metadata
                ))
            }
        }
        
        return chunks
    }
    
    /**
     * Recursive chunking for paragraphs (LangChain-style)
     */
    private fun recursiveChunk(
        text: String,
        sectionContext: String,
        startPosition: Int
    ): List<StructuredChunk> {
        val chunks = mutableListOf<StructuredChunk>()
        
        if (text.length <= chunkSize) {
            chunks.add(StructuredChunk(
                text = text,
                type = ElementType.PARAGRAPH,
                position = startPosition,
                sectionContext = sectionContext,
                metadata = emptyMap()
            ))
            return chunks
        }
        
        // Find the best separator to use
        val textChunks = splitBySeparator(text)
        var pos = startPosition
        
        for (chunk in textChunks) {
            if (chunk.isNotBlank()) {
                chunks.add(StructuredChunk(
                    text = chunk.trim(),
                    type = ElementType.PARAGRAPH,
                    position = pos++,
                    sectionContext = sectionContext,
                    metadata = emptyMap()
                ))
            }
        }
        
        return chunks
    }
    
    /**
     * Split text using the best available separator
     */
    private fun splitBySeparator(text: String): List<String> {
        for (separator in SEPARATORS) {
            if (text.contains(separator)) {
                val parts = text.split(separator)
                if (parts.all { it.length <= chunkSize }) {
                    return mergeSplits(parts, separator)
                }
                // If parts are still too big, continue to next separator
                val merged = mergeSplits(parts, separator)
                if (merged.any { it.length > chunkSize }) {
                    // Recursively split large parts
                    return merged.flatMap { part ->
                        if (part.length > chunkSize) splitBySeparator(part)
                        else listOf(part)
                    }
                }
                return merged
            }
        }
        
        // No separator found, force split
        return text.chunked(chunkSize)
    }
    
    /**
     * Merge splits back together respecting chunk size and overlap
     */
    private fun mergeSplits(splits: List<String>, separator: String): List<String> {
        val merged = mutableListOf<String>()
        var current = StringBuilder()
        
        for (split in splits) {
            val newLength = current.length + separator.length + split.length
            
            if (newLength <= chunkSize) {
                if (current.isNotEmpty()) current.append(separator)
                current.append(split)
            } else {
                if (current.isNotEmpty()) {
                    merged.add(current.toString())
                    // Add overlap from end of previous chunk
                    val overlap = current.toString().takeLast(chunkOverlap)
                    current = StringBuilder(overlap)
                    if (current.isNotEmpty()) current.append(separator)
                }
                current.append(split)
            }
        }
        
        if (current.isNotEmpty()) {
            merged.add(current.toString())
        }
        
        return merged
    }
    
    /**
     * Document element types
     */
    enum class ElementType {
        HEADER,
        PARAGRAPH,
        TABLE,
        LIST,
        CODE_BLOCK
    }
    
    /**
     * Internal document element representation
     */
    private data class DocumentElement(
        val type: ElementType,
        val content: String,
        val metadata: Map<String, String>
    )
    
    /**
     * Output chunk with structure metadata
     */
    data class StructuredChunk(
        val text: String,
        val type: ElementType,
        val position: Int,
        val sectionContext: String,      // Parent section/header for context
        val metadata: Map<String, String>
    ) {
        /**
         * Get text with section context prepended (for better retrieval)
         */
        fun getContextualText(): String {
            return if (sectionContext.isNotBlank()) {
                "[$sectionContext]\n$text"
            } else {
                text
            }
        }
    }
}
