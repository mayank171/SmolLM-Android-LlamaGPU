package io.shubham0204.startwithsmollm.rag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extracts text from images using ML Kit OCR
 * Supports: PDF pages (rendered as images), standalone images
 */
class ImageTextExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageTextExtractor"
        private const val PDF_RENDER_DPI = 150  // Balance between quality and speed
    }
    
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Extract text from a PDF using OCR (for scanned PDFs)
     * Renders each page as an image and runs OCR
     */
    suspend fun extractTextFromPdf(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext OcrResult.Error("Could not open PDF file")
            
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            val pageCount = pdfRenderer.pageCount
            
            Log.d(TAG, "Processing PDF with $pageCount pages for OCR")
            
            val allText = StringBuilder()
            var processedPages = 0
            
            for (pageIndex in 0 until pageCount) {
                val page = pdfRenderer.openPage(pageIndex)
                
                // Render page to bitmap
                val bitmap = renderPageToBitmap(page)
                page.close()
                
                // Run OCR on the bitmap
                val pageText = recognizeText(bitmap)
                bitmap.recycle()
                
                if (pageText.isNotBlank()) {
                    allText.append("\n\n--- Page ${pageIndex + 1} ---\n\n")
                    allText.append(pageText)
                    processedPages++
                }
                
                Log.d(TAG, "OCR Page ${pageIndex + 1}/$pageCount: ${pageText.length} chars")
            }
            
            pdfRenderer.close()
            parcelFileDescriptor.close()
            
            Log.d(TAG, "OCR complete: $processedPages pages, ${allText.length} total chars")
            
            OcrResult.Success(
                text = allText.toString().trim(),
                pageCount = pageCount,
                processedPages = processedPages
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed: ${e.message}", e)
            OcrResult.Error("OCR failed: ${e.message}")
        }
    }
    
    /**
     * Extract text from a standalone image file
     */
    suspend fun extractTextFromImage(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext OcrResult.Error("Could not open image")
            
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) {
                return@withContext OcrResult.Error("Could not decode image")
            }
            
            val text = recognizeText(bitmap)
            bitmap.recycle()
            
            Log.d(TAG, "Image OCR complete: ${text.length} chars")
            
            OcrResult.Success(
                text = text,
                pageCount = 1,
                processedPages = if (text.isNotBlank()) 1 else 0
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Image OCR failed: ${e.message}", e)
            OcrResult.Error("Image OCR failed: ${e.message}")
        }
    }
    
    /**
     * Render a PDF page to a bitmap
     */
    private fun renderPageToBitmap(page: PdfRenderer.Page): Bitmap {
        // Calculate dimensions based on DPI
        val scale = PDF_RENDER_DPI / 72f  // PDF is 72 DPI by default
        val width = (page.width * scale).toInt()
        val height = (page.height * scale).toInt()
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // White background (important for OCR)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        
        return bitmap
    }
    
    /**
     * Run ML Kit text recognition on a bitmap
     */
    private suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                // Extract text with structure preservation
                val extractedText = buildString {
                    for (block in visionText.textBlocks) {
                        append(block.text)
                        append("\n\n")
                    }
                }
                continuation.resume(extractedText.trim())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit recognition failed: ${e.message}")
                continuation.resume("")  // Return empty on failure, don't crash
            }
    }
    
    /**
     * Check if a PDF might be scanned (image-based)
     * Heuristic: If PdfBox extracts very little text relative to page count
     */
    fun isProbablyScannedPdf(extractedTextLength: Int, pageCount: Int): Boolean {
        if (pageCount == 0) return false
        val avgCharsPerPage = extractedTextLength / pageCount
        // Typical text page has 2000-3000 chars, scanned has < 100
        return avgCharsPerPage < 200
    }
    
    fun close() {
        textRecognizer.close()
    }
    
    sealed class OcrResult {
        data class Success(
            val text: String,
            val pageCount: Int,
            val processedPages: Int
        ) : OcrResult()
        
        data class Error(val message: String) : OcrResult()
    }
}
