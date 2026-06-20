package io.shubham0204.startwithsmollm.rag.profiling

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Observer interface for profiling events
 */
interface ProfilingObserver {
    fun onEvent(event: ProfilingEvent)
    fun onBatchEvents(events: List<ProfilingEvent>) {
        events.forEach { onEvent(it) }
    }
}

/**
 * Logs profiling events to Logcat
 */
class LoggingObserver(
    private val tag: String = "RAGProfiler",
    private val verbose: Boolean = false
) : ProfilingObserver {
    
    override fun onEvent(event: ProfilingEvent) {
        when (event) {
            is ProfilingEvent.LatencyMeasured -> {
                val emoji = when {
                    event.durationMs < 50 -> "⚡"
                    event.durationMs < 100 -> "✅"
                    event.durationMs < 200 -> "⚠️"
                    else -> "🐌"
                }
                Log.d(tag, "$emoji [${event.component}] ${event.operation}: ${event.durationMs}ms")
            }
            is ProfilingEvent.MemoryMeasured -> {
                // Skip periodic sampling events — they're for the dashboard, not logcat.
                // Logging them every sample flooded logcat (RAGProfiler spam) and burned CPU.
                if (event.operation == "sample") return
                val usedMb = event.usedBytes / 1024 / 1024
                val totalMb = event.totalBytes / 1024 / 1024
                Log.d(tag, "💾 [${event.component}] Memory: ${usedMb}MB / ${totalMb}MB")
            }
            is ProfilingEvent.ErrorOccurred -> {
                Log.e(tag, "❌ [${event.component}] ${event.operation}: ${event.message}", event.error)
            }
            is ProfilingEvent.OperationStarted -> {
                if (verbose) {
                    Log.d(tag, "▶️ [${event.component}] Started: ${event.operation}")
                }
            }
            is ProfilingEvent.OperationCompleted -> {
                if (verbose) {
                    Log.d(tag, "⏹️ [${event.component}] Completed: ${event.operation} (${event.durationMs}ms)")
                }
            }
            is ProfilingEvent.CpuMeasured -> {
                Log.d(tag, "🔥 CPU: ${event.cpuPercent}%")
            }
            is ProfilingEvent.CustomMetric -> {
                Log.d(tag, "📊 ${event.metricName}: ${event.value}")
            }
        }
    }
}

/**
 * Collects events for UI display (dashboard)
 */
class DashboardObserver : ProfilingObserver {
    
    private val _events = MutableStateFlow<List<ProfilingEvent>>(emptyList())
    val events: StateFlow<List<ProfilingEvent>> = _events.asStateFlow()
    
    private val _latencyStats = MutableStateFlow<Map<String, LatencyStats>>(emptyMap())
    val latencyStats: StateFlow<Map<String, LatencyStats>> = _latencyStats.asStateFlow()
    
    private val maxEvents = 100
    
    data class LatencyStats(
        val operation: String,
        val count: Int,
        val avgMs: Double,
        val minMs: Long,
        val maxMs: Long,
        val p50Ms: Long,
        val p95Ms: Long
    )
    
    override fun onEvent(event: ProfilingEvent) {
        _events.value = (_events.value + event).takeLast(maxEvents)
        
        if (event is ProfilingEvent.LatencyMeasured) {
            updateLatencyStats(event)
        }
    }
    
    private fun updateLatencyStats(event: ProfilingEvent.LatencyMeasured) {
        val key = "${event.component}.${event.operation}"
        val currentStats = _latencyStats.value[key]
        
        val latencies = mutableListOf<Long>()
        if (currentStats != null) {
            latencies.add(currentStats.minMs)
            latencies.add(currentStats.maxMs)
        }
        latencies.add(event.durationMs)
        latencies.sort()
        
        val newStats = LatencyStats(
            operation = key,
            count = (currentStats?.count ?: 0) + 1,
            avgMs = if (currentStats != null) {
                (currentStats.avgMs * currentStats.count + event.durationMs) / (currentStats.count + 1)
            } else {
                event.durationMs.toDouble()
            },
            minMs = latencies.first(),
            maxMs = latencies.last(),
            p50Ms = latencies[latencies.size / 2],
            p95Ms = latencies[(latencies.size * 0.95).toInt().coerceIn(0, latencies.size - 1)]
        )
        
        _latencyStats.value = _latencyStats.value + (key to newStats)
    }
    
    fun clear() {
        _events.value = emptyList()
        _latencyStats.value = emptyMap()
    }
}

/**
 * Aggregates metrics for analysis
 */
class MetricsAggregator : ProfilingObserver {
    
    private val latencies = mutableMapOf<String, MutableList<Long>>()
    private val memorySnapshots = mutableListOf<ProfilingEvent.MemoryMeasured>()
    private val errors = mutableListOf<ProfilingEvent.ErrorOccurred>()
    private val customMetrics = mutableMapOf<String, MutableList<Double>>()
    
    override fun onEvent(event: ProfilingEvent) {
        when (event) {
            is ProfilingEvent.LatencyMeasured -> {
                val key = "${event.component}.${event.operation}"
                latencies.getOrPut(key) { mutableListOf() }.add(event.durationMs)
            }
            is ProfilingEvent.MemoryMeasured -> {
                memorySnapshots.add(event)
            }
            is ProfilingEvent.ErrorOccurred -> {
                errors.add(event)
            }
            is ProfilingEvent.CustomMetric -> {
                val key = "${event.operation}.${event.metricName}"
                val doubleValue = when (val v = event.value) {
                    is Number -> v.toDouble()
                    else -> 0.0
                }
                customMetrics.getOrPut(key) { mutableListOf() }.add(doubleValue)
            }
            else -> { }
        }
    }
    
    fun getAverageLatency(operation: String): Double {
        return latencies[operation]?.average() ?: 0.0
    }
    
    fun getPercentile(operation: String, percentile: Int): Long {
        val sorted = latencies[operation]?.sorted() ?: return 0
        if (sorted.isEmpty()) return 0
        val index = (sorted.size * percentile / 100).coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
    
    fun getPeakMemory(): Long {
        return memorySnapshots.maxOfOrNull { it.usedBytes } ?: 0
    }
    
    fun getErrorCount(): Int = errors.size
    
    fun getAverageCustomMetric(metricName: String): Double {
        return customMetrics[metricName]?.average() ?: 0.0
    }
    
    fun getSummary(): String {
        return buildString {
            appendLine("=== Profiling Summary ===")
            appendLine("Operations tracked: ${latencies.size}")
            latencies.forEach { (op, times) ->
                appendLine("  $op:")
                appendLine("    Count: ${times.size}")
                appendLine("    Avg: ${"%.2f".format(times.average())}ms")
                appendLine("    Min: ${times.minOrNull()}ms")
                appendLine("    Max: ${times.maxOrNull()}ms")
            }
            appendLine("Peak memory: ${getPeakMemory() / 1024 / 1024}MB")
            appendLine("Errors: ${errors.size}")
        }
    }
    
    fun clear() {
        latencies.clear()
        memorySnapshots.clear()
        errors.clear()
    }
}

/**
 * Exports metrics to file (CSV/JSON)
 */
class FileExporter(
    private val outputDir: File
) : ProfilingObserver {
    
    private val events = mutableListOf<ProfilingEvent>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    
    override fun onEvent(event: ProfilingEvent) {
        events.add(event)
    }
    
    fun exportToCsv(): File {
        val timestamp = dateFormat.format(Date())
        val file = File(outputDir, "profiling_$timestamp.csv")
        
        file.bufferedWriter().use { writer ->
            writer.write("Timestamp,Type,Operation,Component,Value,Unit\n")
            
            events.forEach { event ->
                when (event) {
                    is ProfilingEvent.LatencyMeasured -> {
                        writer.write("${event.timestamp},Latency,${event.operation},${event.component},${event.durationMs},ms\n")
                    }
                    is ProfilingEvent.MemoryMeasured -> {
                        writer.write("${event.timestamp},Memory,${event.operation},${event.component},${event.usedBytes},bytes\n")
                    }
                    is ProfilingEvent.ErrorOccurred -> {
                        writer.write("${event.timestamp},Error,${event.operation},${event.component},${event.message},\n")
                    }
                    else -> { }
                }
            }
        }
        
        return file
    }
    
    fun exportToJson(): File {
        val timestamp = dateFormat.format(Date())
        val file = File(outputDir, "profiling_$timestamp.json")
        
        file.bufferedWriter().use { writer ->
            writer.write("[\n")
            events.forEachIndexed { index, event ->
                writer.write("  {")
                writer.write("\"timestamp\": ${event.timestamp}, ")
                writer.write("\"operation\": \"${event.operation}\"")
                writer.write("}")
                if (index < events.size - 1) writer.write(",")
                writer.write("\n")
            }
            writer.write("]\n")
        }
        
        return file
    }
    
    fun clear() {
        events.clear()
    }
}
