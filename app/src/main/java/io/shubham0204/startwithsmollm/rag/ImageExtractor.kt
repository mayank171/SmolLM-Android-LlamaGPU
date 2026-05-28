package io.shubham0204.startwithsmollm.rag

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream

/**
 * Extracts images and diagrams from PDFs
 * Provides OCR capability for text in images
 */
class ImageExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageExtractor"
        private const val MIN_IMAGE_SIZE = 100 // Minimum width/height to consider
    }
    
    private val imageTextExtractor = ImageTextExtractor(context)
    
    /**
     * Extract all images from a PDF document
     */
    fun extractImages(document: PDDocument): List<ExtractedImage> {
        val images = mutableListOf<ExtractedImage>()
        
        try {
            val renderer = PDFRenderer(document)
            
            for ((pageIndex, page) in document.pages.withIndex()) {
                val pageNumber = pageIndex + 1
                
                // Extract embedded images
                val embeddedImages = extractEmbeddedImages(page, pageNumber)
                images.addAll(embeddedImages)
                
                // If no embedded images, try rendering the page and detecting image regions
                if (embeddedImages.isEmpty()) {
                    val pageImage = renderPageAsImage(renderer, pageIndex, pageNumber)
                    if (pageImage != null) {
                        images.add(pageImage)
                    }
                }
            }
            
            Log.d(TAG, "Extracted ${images.size} images from ${document.numberOfPages} pages")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting images: ${e.message}")
        }
        
        return images
    }
    
    /**
     * Extract images that are embedded in the PDF
     */
    private fun extractEmbeddedImages(page: PDPage, pageNumber: Int): List<ExtractedImage> {
        val images = mutableListOf<ExtractedImage>()
        
        try {
            val resources = page.resources
            val xObjectNames = resources.xObjectNames
            
            for (name in xObjectNames) {
                val xObject = resources.getXObject(name)
                
                if (xObject is PDImageXObject) {
                    val width = xObject.width
                    val height = xObject.height
                    
                    // Skip tiny images (likely icons or decorations)
                    if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
                        continue
                    }
                    
                    try {
                        val bitmap = xObject.image
                        images.add(ExtractedImage(
                            pageNumber = pageNumber,
                            bitmap = bitmap,
                            width = width,
                            height = height,
                            type = ImageType.EMBEDDED
                        ))
                        
                        Log.d(TAG, "Extracted embedded image from page $pageNumber: ${width}x${height}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract image from page $pageNumber: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting embedded images from page $pageNumber: ${e.message}")
        }
        
        return images
    }
    
    /**
     * Render entire page as image (fallback for complex layouts)
     */
    private fun renderPageAsImage(renderer: PDFRenderer, pageIndex: Int, pageNumber: Int): ExtractedImage? {
        return try {
            // Render at lower DPI to save memory (72 DPI is standard screen resolution)
            val bitmap = renderer.renderImageWithDPI(pageIndex, 72f)
            
            ExtractedImage(
                pageNumber = pageNumber,
                bitmap = bitmap,
                width = bitmap.width,
                height = bitmap.height,
                type = ImageType.RENDERED_PAGE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering page $pageNumber as image: ${e.message}")
            null
        }
    }
    
    /**
     * Extract text from an image using OCR
     */
    suspend fun extractTextFromImage(image: ExtractedImage): String? {
        return try {
            // Use the recognizeText method from ImageTextExtractor
            recognizeTextFromBitmap(image.bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from image on page ${image.pageNumber}: ${e.message}")
            null
        }
    }
    
    /**
     * Recognize text from a bitmap using ML Kit
     */
    private suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )
        
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
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
                continuation.resume("")
            }
    }
    
    /**
     * Process all images and extract text via OCR
     */
    suspend fun processImagesWithOcr(images: List<ExtractedImage>): List<ProcessedImage> {
        return images.mapNotNull { image ->
            val text = extractTextFromImage(image)
            if (!text.isNullOrBlank()) {
                ProcessedImage(
                    pageNumber = image.pageNumber,
                    extractedText = text,
                    width = image.width,
                    height = image.height,
                    type = image.type
                )
            } else {
                null
            }
        }
    }
    
    enum class ImageType {
        EMBEDDED,       // Image embedded in PDF
        RENDERED_PAGE,  // Entire page rendered as image
        DIAGRAM,        // Detected diagram/chart
        PHOTO           // Detected photograph
    }
    
    data class ExtractedImage(
        val pageNumber: Int,
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val type: ImageType
    )
    
    data class ProcessedImage(
        val pageNumber: Int,
        val extractedText: String,
        val width: Int,
        val height: Int,
        val type: ImageType
    ) {
        fun toChunkText(): String {
            return """
                |**Image/Diagram from Page $pageNumber** (${width}x$height)
                |Type: $type
                |
                |$extractedText
                |
            """.trimMargin()
        }
    }
}
