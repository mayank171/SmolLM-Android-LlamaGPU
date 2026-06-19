package io.shubham0204.startwithsmollm.rag

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage

/**
 * Extracts tables from PDFs and converts them to markdown format
 * Uses text-based heuristics with improved column detection
 */
class TableExtractor {
    
    companion object {
        private const val TAG = "TableExtractor"
        private const val USE_TABULA = false  // Tabula incompatible with PDFBox-Android
    }
    
    /**
     * Extract tables from a PDF document
     * Note: This is a stub - actual extraction happens via detectSimpleTables()
     */
    fun extractTables(document: PDDocument): List<ExtractedTable> {
        // Not used - we extract from text instead
        return emptyList()
    }
    
    /**
     * Convert a detected table to markdown format
     */
    private fun tableToMarkdown(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        
        val markdown = StringBuilder()
        
        // Header row
        val header = rows.first()
        markdown.append("| ${header.joinToString(" | ")} |\n")
        
        // Separator
        markdown.append("| ${header.joinToString(" | ") { "---" }} |\n")
        
        // Data rows
        for (row in rows.drop(1)) {
            markdown.append("| ${row.joinToString(" | ")} |\n")
        }
        
        return markdown.toString()
    }
    
    /**
     * Enhanced table detection using multiple heuristics
     * Detects tables by looking for:
     * - Multiple lines with similar structure
     * - Aligned columns
     * - Consistent spacing
     * - Bordered tables (┌─┬─┐ or +--+--+)
     * - Merged cells
     * - Multi-line cells
     */
    fun detectSimpleTables(text: String, pageNumber: Int): List<ExtractedTable> {
        val tables = mutableListOf<ExtractedTable>()
        
        // Method 1: Detect bordered tables (high confidence)
        tables.addAll(detectBorderedTables(text, pageNumber))
        
        // Method 2: Detect space-aligned tables
        tables.addAll(detectSpaceAlignedTables(text, pageNumber))
        
        // Remove duplicates (same region detected by multiple methods)
        return deduplicateTables(tables)
    }
    
    /**
     * Detect tables with visible borders: ┌─┬─┐ or +--+--+
     */
    private fun detectBorderedTables(text: String, pageNumber: Int): List<ExtractedTable> {
        val tables = mutableListOf<ExtractedTable>()
        val lines = text.split("\n")
        
        var inTable = false
        var tableLines = mutableListOf<String>()
        var tableCaption: String? = null
        
        for ((i, line) in lines.withIndex()) {
            // Check for caption
            if (line.trim().matches(Regex("^Table\\s+\\d+:.*", RegexOption.IGNORE_CASE))) {
                tableCaption = line.trim()
                continue
            }
            
            when {
                // Table start: ┌─────┬───┐ or +-----+---+
                isTableTopBorder(line) -> {
                    inTable = true
                    tableLines.add(line)
                }
                
                // Table end: └─────┴───┘ or +-----+---+
                isTableBottomBorder(line) && inTable -> {
                    tableLines.add(line)
                    if (tableLines.size >= 3) {
                        val markdown = parseBorderedTable(tableLines)
                        tables.add(ExtractedTable(
                            pageNumber = pageNumber,
                            markdown = markdown,
                            rawText = tableLines.joinToString("\n"),
                            rowCount = countDataRows(tableLines),
                            columnCount = estimateBorderedColumns(tableLines),
                            caption = tableCaption,
                            confidence = 0.95f  // High confidence for bordered tables
                        ))
                    }
                    tableLines.clear()
                    tableCaption = null
                    inTable = false
                }
                
                // Inside table
                inTable -> {
                    tableLines.add(line)
                }
            }
        }
        
        return tables
    }
    
    /**
     * Detect space-aligned tables (original method, enhanced)
     */
    private fun detectSpaceAlignedTables(text: String, pageNumber: Int): List<ExtractedTable> {
        val tables = mutableListOf<ExtractedTable>()
        val lines = text.split("\n")
        
        var tableStart = -1
        var tableRows = mutableListOf<String>()
        var tableCaption: String? = null
        
        for ((i, line) in lines.withIndex()) {
            // Skip if already part of bordered table
            if (isTableBorder(line)) continue
            
            // Check for caption
            if (line.trim().matches(Regex("^Table\\s+\\d+:.*", RegexOption.IGNORE_CASE))) {
                tableCaption = line.trim()
                continue
            }
            
            // Enhanced detection: check for aligned columns
            val spacedParts = line.split(Regex("\\s{2,}"))
            val hasTabSeparators = line.contains("\t")
            val hasPipeSeparators = line.contains("|") && line.count { it == '|' } >= 2
            
            if (spacedParts.size >= 3 || hasTabSeparators || hasPipeSeparators) {
                // Potential table row
                if (tableStart == -1) {
                    tableStart = i
                }
                tableRows.add(line)
            } else {
                // End of potential table
                if (tableRows.size >= 3) {
                    val markdown = convertAlignedTextToTable(tableRows)
                    val confidence = calculateTableConfidence(tableRows)
                    
                    // Only add if confidence is reasonable
                    if (confidence >= 0.5f) {
                        tables.add(ExtractedTable(
                            pageNumber = pageNumber,
                            markdown = markdown,
                            rawText = tableRows.joinToString("\n"),
                            rowCount = tableRows.size,
                            columnCount = estimateColumnCount(tableRows),
                            caption = tableCaption,
                            confidence = confidence
                        ))
                    }
                }
                tableStart = -1
                tableRows.clear()
                tableCaption = null
            }
        }
        
        // Check for table at end of page
        if (tableRows.size >= 3) {
            val markdown = convertAlignedTextToTable(tableRows)
            val confidence = calculateTableConfidence(tableRows)
            
            if (confidence >= 0.5f) {
                tables.add(ExtractedTable(
                    pageNumber = pageNumber,
                    markdown = markdown,
                    rawText = tableRows.joinToString("\n"),
                    rowCount = tableRows.size,
                    columnCount = estimateColumnCount(tableRows),
                    caption = tableCaption,
                    confidence = confidence
                ))
            }
        }
        
        return tables
    }
    
    /**
     * Convert aligned text to markdown table
     * Improved to handle complex content like mathematical notation
     */
    private fun convertAlignedTextToTable(rows: List<String>): String {
        if (rows.isEmpty()) return ""
        
        // Try to detect column positions from the first row (header)
        val columnPositions = detectColumnPositions(rows)
        
        if (columnPositions.isEmpty()) {
            // Fallback to simple space-based splitting
            return convertAlignedTextSimple(rows)
        }
        
        // Split rows based on detected column positions
        val splitRows = rows.map { row ->
            extractColumnsAtPositions(row, columnPositions)
        }
        
        return tableToMarkdown(splitRows)
    }
    
    /**
     * Detect column positions by analyzing alignment across all rows
     */
    private fun detectColumnPositions(rows: List<String>): List<Int> {
        if (rows.isEmpty()) return emptyList()
        
        val maxLength = rows.maxOfOrNull { it.length } ?: 0
        val positions = mutableListOf<Int>()
        
        // Count spaces at each position across all rows
        val spaceCount = IntArray(maxLength)
        for (row in rows) {
            for (i in row.indices) {
                if (row[i].isWhitespace()) {
                    spaceCount[i]++
                }
            }
        }
        
        // Find positions where most rows have spaces (column boundaries)
        val threshold = rows.size * 0.6  // 60% of rows must have space
        var inSpace = false
        var spaceStart = -1
        
        for (i in spaceCount.indices) {
            if (spaceCount[i] >= threshold) {
                if (!inSpace) {
                    spaceStart = i
                    inSpace = true
                }
            } else {
                if (inSpace && spaceStart >= 0) {
                    // End of space region - this is a column boundary
                    positions.add(spaceStart)
                    inSpace = false
                }
            }
        }
        
        return positions
    }
    
    /**
     * Extract columns from a row based on detected positions
     */
    private fun extractColumnsAtPositions(row: String, positions: List<Int>): List<String> {
        val columns = mutableListOf<String>()
        var lastPos = 0
        
        for (pos in positions) {
            if (pos > lastPos && pos <= row.length) {
                columns.add(row.substring(lastPos, pos).trim())
                lastPos = pos
            }
        }
        
        // Add remaining text as last column
        if (lastPos < row.length) {
            columns.add(row.substring(lastPos).trim())
        }
        
        return columns.filter { it.isNotEmpty() }
    }
    
    /**
     * Fallback: simple space-based splitting
     */
    private fun convertAlignedTextSimple(rows: List<String>): String {
        val splitRows = rows.map { row ->
            row.split(Regex("\\s{2,}"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        
        val maxCols = splitRows.maxOfOrNull { it.size } ?: 0
        if (maxCols == 0) return rows.joinToString("\n")
        
        val paddedRows = splitRows.map { row ->
            row + List(maxCols - row.size) { "" }
        }
        
        return tableToMarkdown(paddedRows)
    }
    
    /**
     * Estimate number of columns in table
     */
    private fun estimateColumnCount(rows: List<String>): Int {
        return rows.maxOfOrNull { row ->
            row.split(Regex("\\s{2,}")).filter { it.trim().isNotEmpty() }.size
        } ?: 0
    }
    
    /**
     * Check if line is a table top border
     */
    private fun isTableTopBorder(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.matches(Regex("^[┌┬┐+─-]{3,}$")) ||
               trimmed.matches(Regex("^\\+[-=]{2,}\\+.*$")) ||
               trimmed.startsWith("┌") && trimmed.contains("┬") && trimmed.endsWith("┐")
    }
    
    /**
     * Check if line is a table bottom border
     */
    private fun isTableBottomBorder(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.matches(Regex("^[└┴┘+─-]{3,}$")) ||
               trimmed.matches(Regex("^\\+[-=]{2,}\\+.*$")) ||
               trimmed.startsWith("└") && trimmed.contains("┴") && trimmed.endsWith("┘")
    }
    
    /**
     * Check if line is any table border
     */
    private fun isTableBorder(line: String): Boolean {
        val trimmed = line.trim()
        return isTableTopBorder(line) || 
               isTableBottomBorder(line) ||
               trimmed.matches(Regex("^[├┼┤+│|─-]{3,}$"))
    }
    
    /**
     * Parse bordered table to markdown
     */
    private fun parseBorderedTable(lines: List<String>): String {
        val dataRows = lines.filter { line ->
            !isTableBorder(line) && line.trim().isNotEmpty()
        }
        
        if (dataRows.isEmpty()) return ""
        
        // Split by | or │
        val rows = dataRows.map { line ->
            line.split(Regex("[|│]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        
        return tableToMarkdown(rows)
    }
    
    /**
     * Count data rows (excluding borders)
     */
    private fun countDataRows(lines: List<String>): Int {
        return lines.count { !isTableBorder(it) && it.trim().isNotEmpty() }
    }
    
    /**
     * Estimate column count for bordered table
     */
    private fun estimateBorderedColumns(lines: List<String>): Int {
        val dataLines = lines.filter { !isTableBorder(it) && it.trim().isNotEmpty() }
        if (dataLines.isEmpty()) return 0
        
        return dataLines.maxOfOrNull { line ->
            line.count { it == '|' || it == '│' } + 1
        } ?: 0
    }
    
    /**
     * Calculate confidence score for detected table
     */
    private fun calculateTableConfidence(rows: List<String>): Float {
        if (rows.isEmpty()) return 0f
        
        var score = 1.0f
        
        // Check column consistency
        val columnCounts = rows.map { row ->
            row.split(Regex("\\s{2,}|\\t|\\|")).filter { it.trim().isNotEmpty() }.size
        }
        val avgColumns = columnCounts.average()
        val columnVariance = columnCounts.map { (it - avgColumns) * (it - avgColumns) }.average()
        
        // Penalize high variance in column count
        if (columnVariance > 2.0) score -= 0.3f
        
        // Check for consistent spacing
        val hasConsistentSpacing = rows.all { row ->
            row.contains(Regex("\\s{2,}")) || row.contains("\\t") || row.contains("|")
        }
        if (!hasConsistentSpacing) score -= 0.2f
        
        // Check for numeric content (tables often have numbers)
        val hasNumbers = rows.any { it.contains(Regex("\\d+")) }
        if (hasNumbers) score += 0.1f
        
        // Check for header indicators (first row different)
        if (rows.size >= 2) {
            val firstRowLength = rows[0].length
            val avgOtherLength = rows.drop(1).map { it.length }.average()
            if (kotlin.math.abs(firstRowLength - avgOtherLength) < firstRowLength * 0.3) {
                score += 0.1f  // Similar lengths suggest proper table
            }
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Remove duplicate tables detected by multiple methods
     */
    private fun deduplicateTables(tables: List<ExtractedTable>): List<ExtractedTable> {
        if (tables.size <= 1) return tables
        
        val deduplicated = mutableListOf<ExtractedTable>()
        val used = mutableSetOf<Int>()
        
        for (i in tables.indices) {
            if (i in used) continue
            
            var best = tables[i]
            used.add(i)
            
            // Check for overlapping tables
            for (j in (i + 1) until tables.size) {
                if (j in used) continue
                
                if (tablesOverlap(tables[i], tables[j])) {
                    // Keep the one with higher confidence
                    if (tables[j].confidence > best.confidence) {
                        best = tables[j]
                    }
                    used.add(j)
                }
            }
            
            deduplicated.add(best)
        }
        
        return deduplicated
    }
    
    /**
     * Check if two tables overlap (same content region)
     */
    private fun tablesOverlap(t1: ExtractedTable, t2: ExtractedTable): Boolean {
        if (t1.pageNumber != t2.pageNumber) return false
        
        // Simple overlap check: if raw text is very similar
        val similarity = calculateTextSimilarity(t1.rawText, t2.rawText)
        return similarity > 0.7f
    }
    
    /**
     * Calculate text similarity (simple Jaccard similarity)
     */
    private fun calculateTextSimilarity(text1: String, text2: String): Float {
        val words1 = text1.split(Regex("\\s+")).toSet()
        val words2 = text2.split(Regex("\\s+")).toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union > 0) intersection.toFloat() / union else 0f
    }
    
    /**
     * Custom text stripper for table detection
     */
    private class TableDetectingStripper : com.tom_roush.pdfbox.text.PDFTextStripper() {
        // This would contain more sophisticated table detection logic
        // For now, we use the simpler text-based approach
    }
    
    data class ExtractedTable(
        val pageNumber: Int,
        val markdown: String,
        val rawText: String,
        val rowCount: Int,
        val columnCount: Int,
        val caption: String? = null,
        val confidence: Float = 0.7f  // Confidence score (0-1)
    ) {
        fun toChunkText(): String {
            val captionText = if (!caption.isNullOrBlank()) {
                "$caption\n\n"
            } else {
                "Table (Page $pageNumber):\n\n"
            }
            
            // Create a clean, embedding-friendly representation
            // Include both markdown table AND a linearized text version for better retrieval
            val linearized = getLinearizedText()
            
            return """
                |[TABLE DATA - ${rowCount} rows × $columnCount columns]
                |$captionText
                |$markdown
                |
                |Summary: $linearized
            """.trimMargin()
        }
        
        /**
         * Get a linearized text representation of the table for better embedding/retrieval
         * Converts "| Name | Age |" rows to "Name: [value], Age: [value]" format
         */
        private fun getLinearizedText(): String {
            val lines = markdown.lines().filter { it.contains("|") && !it.contains("---") }
            if (lines.size < 2) return rawText.take(200)
            
            val headers = lines.first().split("|").map { it.trim() }.filter { it.isNotEmpty() }
            val dataRows = lines.drop(1)
            
            return dataRows.take(5).mapIndexed { idx, row ->
                val cells = row.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val pairs = headers.zip(cells).joinToString(", ") { (h, c) -> "$h: $c" }
                "Row ${idx + 1}: $pairs"
            }.joinToString(". ") + if (dataRows.size > 5) ". (${dataRows.size - 5} more rows)" else ""
        }
    }
}
