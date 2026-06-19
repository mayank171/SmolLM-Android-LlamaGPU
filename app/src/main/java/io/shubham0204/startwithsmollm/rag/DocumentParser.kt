package io.shubham0204.startwithsmollm.rag

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parses documents from various formats into plain text
 * Uses PdfBox-Android for robust PDF parsing
 * Falls back to OCR (ML Kit) for scanned PDFs and images
 */
class DocumentParser(private val context: Context) {
    
    companion object {
        private const val TAG = "DocumentParser"
        private var pdfBoxInitialized = false
        private const val MIN_CHARS_PER_PAGE = 100  // Below this, consider PDF as scanned
        
        // Pattern to detect fragmented table rows (e.g., "Week \n 4 \n Git &")
        private val WEEK_PATTERN = Regex("""Week\s*\n\s*(\d+)\s*\n""")
        private val MONTH_PATTERN = Regex("""MONTH\s+(\d+)""")
    }
    
    private val imageTextExtractor = ImageTextExtractor(context)
    private val tableExtractor = TableExtractor()
    private val imageExtractor = ImageExtractor(context)
    
    init {
        initializePdfBox()
    }
    
    private fun initializePdfBox() {
        if (!pdfBoxInitialized) {
            try {
                PDFBoxResourceLoader.init(context)
                pdfBoxInitialized = true
                Log.d(TAG, "PdfBox initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PdfBox: ${e.message}")
            }
        }
    }
    
    /**
     * Parse a document from URI and return its text content
     */
    fun parse(uri: Uri): ParseResult {
        val fileName = getFileName(uri)
        val type = getDocumentType(fileName)
        
        return when (type) {
            DocumentType.TXT, DocumentType.MARKDOWN -> parsePlainText(uri, fileName, type)
            DocumentType.PDF -> parsePdf(uri, fileName)
            DocumentType.IMAGE -> parseImage(uri, fileName)
            DocumentType.UNKNOWN -> ParseResult.Error("Unsupported file type: $fileName")
        }
    }
    
    /**
     * Parse with OCR support (suspend function for async OCR)
     */
    suspend fun parseWithOcr(uri: Uri): ParseResult {
        val fileName = getFileName(uri)
        val type = getDocumentType(fileName)
        
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "📄 PARSING DOCUMENT: $fileName")
        Log.d(TAG, "   Type: $type")
        Log.d(TAG, "   URI: $uri")
        Log.d(TAG, "═══════════════════════════════════════════════════")
        
        val result = when (type) {
            DocumentType.TXT, DocumentType.MARKDOWN -> parsePlainText(uri, fileName, type)
            DocumentType.PDF -> parsePdfWithOcrFallback(uri, fileName)
            DocumentType.IMAGE -> parseImageWithOcr(uri, fileName)
            DocumentType.UNKNOWN -> ParseResult.Error("Unsupported file type: $fileName")
        }
        
        // Log result summary
        when (result) {
            is ParseResult.Success -> {
                Log.d(TAG, "✅ PARSE SUCCESS: $fileName")
                Log.d(TAG, "   Text length: ${result.text.length} chars")
                Log.d(TAG, "   Used OCR: ${result.usedOcr}")
                Log.d(TAG, "   First 200 chars: ${result.text.take(200).replace("\n", "↵")}")
            }
            is ParseResult.Error -> {
                Log.e(TAG, "❌ PARSE ERROR: $fileName - ${result.message}")
            }
            is ParseResult.NeedsOcr -> {
                Log.d(TAG, "🔍 NEEDS OCR: $fileName")
            }
        }
        
        return result
    }
    
    private fun parseImage(uri: Uri, fileName: String): ParseResult {
        // For sync parsing, return a message that OCR is needed
        return ParseResult.NeedsOcr(fileName, DocumentType.IMAGE)
    }
    
    private suspend fun parseImageWithOcr(uri: Uri, fileName: String): ParseResult {
        return when (val ocrResult = imageTextExtractor.extractTextFromImage(uri)) {
            is ImageTextExtractor.OcrResult.Success -> {
                if (ocrResult.text.isBlank()) {
                    ParseResult.Error("No text found in image")
                } else {
                    Log.d(TAG, "OCR extracted ${ocrResult.text.length} chars from image")
                    ParseResult.Success(
                        text = ocrResult.text,
                        fileName = fileName,
                        type = DocumentType.IMAGE,
                        sizeBytes = ocrResult.text.length.toLong(),
                        usedOcr = true
                    )
                }
            }
            is ImageTextExtractor.OcrResult.Error -> {
                ParseResult.Error(ocrResult.message)
            }
        }
    }
    
    private fun parsePlainText(uri: Uri, fileName: String, type: DocumentType): ParseResult {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: return ParseResult.Error("Could not open file")
            
            if (text.isBlank()) {
                return ParseResult.Error("File is empty")
            }
            
            ParseResult.Success(
                text = text,
                fileName = fileName,
                type = type,
                sizeBytes = text.length.toLong()
            )
        } catch (e: Exception) {
            ParseResult.Error("Error reading file: ${e.message}")
        }
    }
    
    private fun parsePdf(uri: Uri, fileName: String): ParseResult {
        return try {
            Log.d(TAG, "📖 PDF PARSING START: $fileName")
            
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ParseResult.Error("Could not open PDF")
            
            val sizeBytes = inputStream.available().toLong()
            Log.d(TAG, "   File size: ${sizeBytes / 1024} KB")
            
            // Use PdfBox for proper PDF parsing
            val document = PDDocument.load(inputStream)
            val pageCount = document.numberOfPages
            Log.d(TAG, "   Page count: $pageCount")
            
            val text = try {
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true  // Maintain reading order
                }
                stripper.getText(document)
            } finally {
                document.close()
                inputStream.close()
            }
            
            // Check if PDF might be scanned (very little text extracted)
            val avgCharsPerPage = if (pageCount > 0) text.length / pageCount else 0
            Log.d(TAG, "   Total text extracted: ${text.length} chars")
            Log.d(TAG, "   Avg chars/page: $avgCharsPerPage")
            Log.d(TAG, "   Threshold for OCR: < $MIN_CHARS_PER_PAGE chars/page")
            
            if (text.isBlank() || avgCharsPerPage < MIN_CHARS_PER_PAGE) {
                Log.d(TAG, "⚠️ PDF appears to be SCANNED (low text content). Will try OCR.")
                return ParseResult.NeedsOcr(fileName, DocumentType.PDF)
            }
            
            Log.d(TAG, "✅ PDF TEXT EXTRACTION SUCCESS")
            Log.d(TAG, "   First 300 chars: ${text.take(300).replace("\n", "↵")}")
            
            ParseResult.Success(
                text = text.trim(),
                fileName = fileName,
                type = DocumentType.PDF,
                sizeBytes = sizeBytes,
                usedOcr = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ PDF PARSING ERROR: ${e.message}", e)
            ParseResult.Error("Error parsing PDF: ${e.message}")
        }
    }
    
    /**
     * Parse PDF with enhanced extraction (tables and images)
     */
    suspend fun parsePdfEnhanced(uri: Uri, fileName: String): ParseResult {
        return try {
            Log.d(TAG, "📖 ENHANCED PDF PARSING START: $fileName")
            
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ParseResult.Error("Could not open PDF")
            
            val sizeBytes = inputStream.available().toLong()
            Log.d(TAG, "   File size: ${sizeBytes / 1024} KB")
            
            // Load PDF document
            val document = PDDocument.load(inputStream)
            val pageCount = document.numberOfPages
            Log.d(TAG, "   Page count: $pageCount")
            
            // Extract text page by page for better table detection
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            
            val allTables = mutableListOf<TableExtractor.ExtractedTable>()
            val fullTextBuilder = StringBuilder()
            
            // Process each page separately for better table detection
            for (pageNum in 1..pageCount) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val pageText = stripper.getText(document)
                fullTextBuilder.append(pageText)
                
                // Extract tables from this page
                val pageTables = tableExtractor.detectSimpleTables(pageText, pageNum)
                allTables.addAll(pageTables)
            }
            
            var text = fullTextBuilder.toString()
            
            // Preprocess text to reconstruct fragmented table rows
            text = reconstructTableRows(text)
            Log.d(TAG, "   Text after reconstruction: ${text.length} chars")
            
            val tables = allTables
            
            Log.d(TAG, "   Extracting tables...")
            Log.d(TAG, "   Found ${tables.size} tables across $pageCount pages")
            
            // Extract images
            Log.d(TAG, "   Extracting images...")
            val extractedImages = imageExtractor.extractImages(document)
            Log.d(TAG, "   Found ${extractedImages.size} images")
            
            // Process images with OCR
            val processedImages = if (extractedImages.isNotEmpty()) {
                Log.d(TAG, "   Running OCR on images...")
                imageExtractor.processImagesWithOcr(extractedImages)
            } else {
                emptyList()
            }
            Log.d(TAG, "   Processed ${processedImages.size} images with OCR")
            
            document.close()
            inputStream.close()
            
            // Check if PDF might be scanned
            val avgCharsPerPage = if (pageCount > 0) text.length / pageCount else 0
            if (text.isBlank() || avgCharsPerPage < MIN_CHARS_PER_PAGE) {
                Log.d(TAG, "⚠️ PDF appears to be SCANNED")
                return ParseResult.NeedsOcr(fileName, DocumentType.PDF)
            }
            
            Log.d(TAG, "✅ ENHANCED PDF PARSING SUCCESS")
            Log.d(TAG, "   Text: ${text.length} chars")
            Log.d(TAG, "   Tables: ${tables.size}")
            Log.d(TAG, "   Images: ${processedImages.size}")
            
            ParseResult.Success(
                text = text.trim(),
                fileName = fileName,
                type = DocumentType.PDF,
                sizeBytes = sizeBytes,
                usedOcr = false,
                tables = tables,
                images = processedImages
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ ENHANCED PDF PARSING ERROR: ${e.message}", e)
            ParseResult.Error("Error parsing PDF: ${e.message}")
        }
    }
    
    /**
     * Parse PDF with automatic OCR fallback for scanned documents
     */
    private suspend fun parsePdfWithOcrFallback(uri: Uri, fileName: String): ParseResult {
        // First try normal PDF parsing
        val normalResult = parsePdf(uri, fileName)
        
        // If it needs OCR, run OCR
        if (normalResult is ParseResult.NeedsOcr) {
            Log.d(TAG, "Running OCR on scanned PDF: $fileName")
            
            return when (val ocrResult = imageTextExtractor.extractTextFromPdf(uri)) {
                is ImageTextExtractor.OcrResult.Success -> {
                    if (ocrResult.text.isBlank()) {
                        ParseResult.Error("OCR could not extract text from PDF")
                    } else {
                        Log.d(TAG, "OCR extracted ${ocrResult.text.length} chars from ${ocrResult.processedPages}/${ocrResult.pageCount} pages")
                        ParseResult.Success(
                            text = ocrResult.text,
                            fileName = fileName,
                            type = DocumentType.PDF,
                            sizeBytes = ocrResult.text.length.toLong(),
                            usedOcr = true
                        )
                    }
                }
                is ImageTextExtractor.OcrResult.Error -> {
                    ParseResult.Error("OCR failed: ${ocrResult.message}")
                }
            }
        }
        
        return normalResult
    }
    
    fun getFileName(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }
    
    private fun getDocumentType(fileName: String): DocumentType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt" -> DocumentType.TXT
            "md", "markdown" -> DocumentType.MARKDOWN
            "pdf" -> DocumentType.PDF
            "jpg", "jpeg", "png", "webp", "bmp" -> DocumentType.IMAGE
            else -> DocumentType.UNKNOWN
        }
    }
    
    /**
     * Reconstruct fragmented table rows from PDF text extraction
     * PDFs often split table cells across multiple lines, e.g.:
     * "Week \n 4 \n Git & \n Collaboration" -> "Week 4: Git & Collaboration"
     */
    private fun reconstructTableRows(text: String): String {
        var result = text
        
        // Fix "Week \n X" patterns -> "Week X:"
        // This handles PDFs where table cells are split across lines
        result = result.replace(Regex("""Week\s*\n\s*(\d+)\s*\n""")) { match ->
            val weekNum = match.groupValues[1]
            "\n\nWeek $weekNum: "
        }
        
        // Fix fragmented focus areas that follow week numbers
        // Pattern: short line followed by newline followed by continuation
        // e.g., "Git & \n Collaboration" -> "Git & Collaboration"
        result = result.replace(Regex("""([A-Za-z&])\s*\n\s*([A-Z][a-z]+)\s*\n""")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]} - "
        }
        
        // Consolidate multiple newlines into paragraph breaks
        result = result.replace(Regex("""\n{3,}"""), "\n\n")
        
        // Fix "Build / Output" column fragments - join lowercase words split by newlines
        result = result.replace(Regex("""([a-z,])\s*\n\s*([a-z])""")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]}"
        }
        
        Log.d(TAG, "   Reconstructed table rows in text")
        return result
    }
    
    sealed class ParseResult {
        data class Success(
            val text: String,
            val fileName: String,
            val type: DocumentType,
            val sizeBytes: Long,
            val usedOcr: Boolean = false,
            val tables: List<TableExtractor.ExtractedTable> = emptyList(),
            val images: List<ImageExtractor.ProcessedImage> = emptyList()
        ) : ParseResult() {
            /**
             * Get combined text including tables and image descriptions
             */
            fun getCombinedText(): String {
                val builder = StringBuilder(text)
                
                // Add tables
                if (tables.isNotEmpty()) {
                    builder.append("\n\n## Extracted Tables\n\n")
                    for (table in tables) {
                        builder.append(table.toChunkText())
                        builder.append("\n")
                    }
                }
                
                // Add image text
                if (images.isNotEmpty()) {
                    builder.append("\n\n## Extracted Images/Diagrams\n\n")
                    for (image in images) {
                        builder.append(image.toChunkText())
                        builder.append("\n")
                    }
                }
                
                return builder.toString()
            }
        }
        
        data class Error(val message: String) : ParseResult()
        
        data class NeedsOcr(
            val fileName: String,
            val type: DocumentType
        ) : ParseResult()
    }
}
