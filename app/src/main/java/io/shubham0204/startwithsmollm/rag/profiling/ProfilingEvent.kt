package io.shubham0204.startwithsmollm.rag.profiling

/**
 * Sealed class representing all profiling events
 * Events are immutable and contain timestamp
 */
sealed class ProfilingEvent {
    abstract val timestamp: Long
    abstract val operation: String
    
    /**
     * Latency measurement event
     */
    data class LatencyMeasured(
        override val operation: String,
        val durationMs: Long,
        val component: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProfilingEvent()
    
    /**
     * Memory usage event
     */
    data class MemoryMeasured(
        override val operation: String,
        val component: String,
        val usedBytes: Long,
        val totalBytes: Long,
        val freeBytes: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProfilingEvent()
    
    /**
     * CPU usage event
     */
    data class CpuMeasured(
        override val operation: String,
        val cpuPercent: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProfilingEvent()
    
    /**
     * Error/exception event
     */
    data class ErrorOccurred(
        override val operation: String,
        val component: String,
        val error: Throwable,
        val message: String = error.message ?: "Unknown error",
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProfilingEvent()
    
    /**
     * Operation started event (for async tracking)
     */
    data class OperationStarted(
        override val operation: String,
        val component: String,
        val operationId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProfilingEvent()
    
    /**
     * Operation completed event (for async tracking)
     */
    data class OperationCompleted(
        override val operation: String,
        val component: String,
        val operationId: String,
        val durationMs: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProfilingEvent()
    
    /**
     * Custom metric event
     */
    data class CustomMetric(
        override val operation: String,
        val metricName: String,
        val value: Any,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProfilingEvent()
}
