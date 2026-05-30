package io.shubham0204.startwithsmollm.rag.profiling

import android.content.Context
import android.net.Uri
import io.shubham0204.startwithsmollm.rag.DocumentParser

/**
 * Wrapper for DocumentParser that adds profiling
 * Measures parsing time, OCR latency, table extraction, and memory usage
 */
class ProfiledDocumentParser(
    context: Context,
    private val profiler: Profiler
) {
    
    companion object {
        private const val COMPONENT = "DocumentParser"
    }
    
    private val documentParser = DocumentParser(context)
    
    /**
     * Profile document parsing
     */
    fun parse(uri: Uri): DocumentParser.ParseResult {
        return profiler.profile("parse", COMPONENT) {
            profiler.recordMemory("before_parse", COMPONENT)
            
            val startTime = System.nanoTime()
            val result = documentParser.parse(uri)
            val duration = (System.nanoTime() - startTime) / 1_000_000
            
            // Record parsing metrics
            when (result) {
                is DocumentParser.ParseResult.Success -> {
                    profiler.notify(ProfilingEvent.CustomMetric(
                        operation = "parse",
                        metricName = "text_length",
                        value = result.text.length
                    ))
                    profiler.notify(ProfilingEvent.CustomMetric(
                        operation = "parse",
                        metricName = "table_count",
                        value = result.tables.size
                    ))
                    profiler.notify(ProfilingEvent.CustomMetric(
                        operation = "parse",
                        metricName = "image_count",
                        value = result.images.size
                    ))
                    profiler.notify(ProfilingEvent.CustomMetric(
                        operation = "parse",
                        metricName = "used_ocr",
                        value = if (result.usedOcr) 1 else 0
                    ))
                    profiler.notify(ProfilingEvent.CustomMetric(
                        operation = "parse",
                        metricName = "duration_ms",
                        value = duration
                    ))
                }
                is DocumentParser.ParseResult.NeedsOcr -> {
                    // OCR needed but not available
                    profiler.notify(ProfilingEvent.CustomMetric(
                        operation = "parse",
                        metricName = "needs_ocr",
                        value = 1
                    ))
                }
                is DocumentParser.ParseResult.Error -> {
                    profiler.recordError("parse", COMPONENT, Exception(result.message))
                }
            }
            
            profiler.recordMemory("after_parse", COMPONENT)
            result
        }
    }
}
