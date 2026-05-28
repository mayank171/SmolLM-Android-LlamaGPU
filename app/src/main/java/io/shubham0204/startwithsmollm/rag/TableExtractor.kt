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
     * Simple table detection using text alignment heuristics
     * Detects tables by looking for:
     * - Multiple lines with similar structure
     * - Aligned columns
     * - Consistent spacing
     */
    fun detectSimpleTables(text: String, pageNumber: Int): List<ExtractedTable> {
        val tables = mutableListOf<ExtractedTable>()
        val lines = text.split("\n")
        
        var tableStart = -1
        var tableRows = mutableListOf<String>()
        var tableCaption: String? = null
        
        for ((i, line) in lines.withIndex()) {
            // Check if this line is a table caption (e.g., "Table 1: ...")
            if (line.trim().matches(Regex("^Table\\s+\\d+:.*", RegexOption.IGNORE_CASE))) {
                tableCaption = line.trim()
                continue
            }
            
            // Simple heuristic: lines with multiple spaces might be table rows
            val spacedParts = line.split(Regex("\\s{2,}"))
            
            if (spacedParts.size >= 3) {
                // Potential table row
                if (tableStart == -1) {
                    tableStart = i
                }
                tableRows.add(line)
            } else {
                // End of potential table
                if (tableRows.size >= 3) {
                    // Convert to markdown
                    val markdown = convertAlignedTextToTable(tableRows)
                    tables.add(ExtractedTable(
                        pageNumber = pageNumber,
                        markdown = markdown,
                        rawText = tableRows.joinToString("\n"),
                        rowCount = tableRows.size,
                        columnCount = estimateColumnCount(tableRows),
                        caption = tableCaption
                    ))
                }
                tableStart = -1
                tableRows.clear()
                tableCaption = null  // Reset caption after table
            }
        }
        
        // Check for table at end of page
        if (tableRows.size >= 3) {
            val markdown = convertAlignedTextToTable(tableRows)
            tables.add(ExtractedTable(
                pageNumber = pageNumber,
                markdown = markdown,
                rawText = tableRows.joinToString("\n"),
                rowCount = tableRows.size,
                columnCount = estimateColumnCount(tableRows),
                caption = tableCaption
            ))
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
        val caption: String? = null
    ) {
        fun toChunkText(): String {
            val captionText = if (!caption.isNullOrBlank()) {
                "**$caption**\n\n"
            } else {
                "**Table from Page $pageNumber** (${rowCount}x$columnCount)\n\n"
            }
            
            return """
                |$captionText$markdown
                |
                |---
                |*Source: Page $pageNumber | ${rowCount} rows × $columnCount columns*
                |
            """.trimMargin()
        }
    }
}
