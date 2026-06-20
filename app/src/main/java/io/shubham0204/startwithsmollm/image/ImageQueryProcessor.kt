package io.shubham0204.startwithsmollm.image

import android.content.Context
import android.net.Uri
import android.util.Log
import io.shubham0204.startwithsmollm.rag.ImageTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Processes images for chat queries
 * Extracts text via OCR and builds prompts for the LLM
 */
class ImageQueryProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageQueryProcessor"
        // Reduced from 2000 -> 1200 to cut prefill tokens on large models (P0 TTFT optimization)
        private const val MAX_OCR_TEXT_LENGTH = 1200
        // Threshold for inlining short OCR text without prompt scaffolding
        private const val INLINE_THRESHOLD = 80
    }
    
    private val imageTextExtractor = ImageTextExtractor(context)
    
    /**
     * Result of image processing
     */
    sealed class ProcessResult {
        data class Success(
            val extractedText: String,
            val augmentedPrompt: String,
            val imageUri: Uri
        ) : ProcessResult()
        
        data class Error(val message: String) : ProcessResult()
        
        data class NoTextFound(val imageUri: Uri) : ProcessResult()
    }
    
    /**
     * Process an image and build a query prompt
     * @param imageUri URI of the image to process
     * @param userQuestion Optional question about the image
     * @return ProcessResult with extracted text and augmented prompt
     */
    suspend fun processImageQuery(
        imageUri: Uri,
        userQuestion: String? = null
    ): ProcessResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "╔═══════════════════════════════════════════════════════════════╗")
            Log.d(TAG, "║           📸 IMAGE QUERY PROCESSING                           ║")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
            Log.d(TAG, "Image URI: $imageUri")
            Log.d(TAG, "User question: ${userQuestion ?: "(none)"}")
            
            // Extract text from image using OCR
            Log.d(TAG, "▶ Running OCR...")
            val ocrResult = imageTextExtractor.extractTextFromImage(imageUri)
            
            when (ocrResult) {
                is ImageTextExtractor.OcrResult.Success -> {
                    val extractedText = ocrResult.text.trim()
                    Log.d(TAG, "✅ OCR Success: ${extractedText.length} chars extracted")
                    
                    if (extractedText.isBlank()) {
                        Log.d(TAG, "⚠️ No text found in image")
                        return@withContext ProcessResult.NoTextFound(imageUri)
                    }
                    
                    // Clean and truncate OCR text to minimize prefill tokens
                    val cleanedText = cleanupOcrText(extractedText)
                    val truncatedText = if (cleanedText.length > MAX_OCR_TEXT_LENGTH) {
                        Log.d(TAG, "⚠️ Truncating OCR text from ${cleanedText.length} to $MAX_OCR_TEXT_LENGTH chars")
                        cleanedText.take(MAX_OCR_TEXT_LENGTH) + "…"
                    } else {
                        cleanedText
                    }
                    Log.d(TAG, "📉 OCR text: raw=${extractedText.length} → cleaned=${truncatedText.length} chars (saved ${extractedText.length - truncatedText.length})")
                    
                    // Build the augmented prompt
                    val augmentedPrompt = buildPrompt(truncatedText, userQuestion)
                    Log.d(TAG, "✅ Augmented prompt built: ${augmentedPrompt.length} chars")
                    
                    ProcessResult.Success(
                        extractedText = truncatedText,
                        augmentedPrompt = augmentedPrompt,
                        imageUri = imageUri
                    )
                }
                
                is ImageTextExtractor.OcrResult.Error -> {
                    Log.e(TAG, "❌ OCR Error: ${ocrResult.message}")
                    ProcessResult.Error("Failed to extract text: ${ocrResult.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Image processing failed: ${e.message}", e)
            ProcessResult.Error("Image processing failed: ${e.message}")
        }
    }
    
    /**
     * Build a compact prompt for the LLM with extracted image text.
     * Compact template saves ~20-25 prefill tokens vs verbose version.
     */
    private fun buildPrompt(extractedText: String, userQuestion: String?): String {
        val question = userQuestion?.trim()?.takeIf { it.isNotBlank() }
            ?: "What's in this image?"
        
        // For very short OCR text, inline it without scaffolding to save tokens
        return if (extractedText.length <= INLINE_THRESHOLD) {
            "Image text: \"$extractedText\"\n$question"
        } else {
            "Image text:\n$extractedText\n\nQ: $question"
        }
    }
    
    /**
     * Build a prompt from pre-extracted OCR text (skips OCR step entirely).
     * Used when OCR was already run in the preview dialog.
     */
    fun buildPromptFromText(extractedText: String, userQuestion: String?): String {
        val cleaned = cleanupOcrText(extractedText)
        val truncated = if (cleaned.length > MAX_OCR_TEXT_LENGTH) {
            cleaned.take(MAX_OCR_TEXT_LENGTH) + "…"
        } else cleaned
        return buildPrompt(truncated, userQuestion)
    }
    
    /**
     * Clean OCR output to reduce token count without losing information.
     * - Collapses repeated whitespace and blank lines
     * - Drops lines that are pure noise (single special chars, dividers)
     * - Trims each line
     */
    private fun cleanupOcrText(raw: String): String {
        return raw.lineSequence()
            .map { it.trim() }
            .filter { line ->
                if (line.isEmpty()) return@filter false
                // Drop lines that are only special chars/dividers (e.g. "---", "***", "...")
                if (line.length <= 3 && line.none { it.isLetterOrDigit() }) return@filter false
                true
            }
            // Collapse multiple internal spaces to a single space
            .map { it.replace(Regex("\\s+"), " ") }
            .joinToString("\n")
            .trim()
    }
    
    /**
     * Extract only the text from an image (without building a prompt)
     * Useful for preview purposes
     */
    suspend fun extractTextOnly(imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            when (val result = imageTextExtractor.extractTextFromImage(imageUri)) {
                is ImageTextExtractor.OcrResult.Success -> {
                    val text = result.text.trim()
                    if (text.isBlank()) null else text
                }
                is ImageTextExtractor.OcrResult.Error -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction failed: ${e.message}")
            null
        }
    }
    
    fun close() {
        imageTextExtractor.close()
    }
}
