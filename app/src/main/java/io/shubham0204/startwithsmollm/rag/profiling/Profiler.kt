package io.shubham0204.startwithsmollm.rag.profiling

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Singleton profiler for RAG system
 * Thread-safe and lifecycle-aware
 */
class Profiler private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var instance: Profiler? = null
        
        fun getInstance(context: Context): Profiler {
            return instance ?: synchronized(this) {
                instance ?: Profiler(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        fun isInitialized() = instance != null
    }
    
    private val observers = CopyOnWriteArrayList<ProfilingObserver>()
    
    @Volatile
    internal var strategy: ProfilingStrategy = NoProfilingStrategy()
    
    private val handler = Handler(Looper.getMainLooper())
    private var samplingRunnable: Runnable? = null
    
    /**
     * Set profiling strategy
     */
    fun setStrategy(strategy: ProfilingStrategy) {
        this.strategy = strategy
        restartSampling()
    }
    
    /**
     * Add observer
     */
    fun addObserver(observer: ProfilingObserver) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }
    
    /**
     * Remove observer
     */
    fun removeObserver(observer: ProfilingObserver) {
        observers.remove(observer)
    }
    
    /**
     * Clear all observers
     */
    fun clearObservers() {
        observers.clear()
    }
    
    /**
     * Get all observers (for dashboard access)
     */
    fun getObservers(): List<ProfilingObserver> {
        return observers.toList()
    }
    
    /**
     * Notify all observers of an event
     */
    fun notify(event: ProfilingEvent) {
        if (!strategy.shouldProfile(event.operation, getComponent(event))) {
            return
        }
        
        observers.forEach { observer ->
            try {
                observer.onEvent(event)
            } catch (e: Exception) {
                android.util.Log.e("Profiler", "Observer error", e)
            }
        }
    }
    
    /**
     * Record latency metric
     */
    internal fun recordLatency(operation: String, component: String, durationMs: Long) {
        notify(ProfilingEvent.LatencyMeasured(operation, durationMs, component))
    }
    
    /**
     * Record memory usage
     */
    internal fun recordMemory(operation: String, component: String) {
        if (!strategy.shouldTrackMemory()) return
        
        val runtime = Runtime.getRuntime()
        notify(ProfilingEvent.MemoryMeasured(
            operation = operation,
            component = component,
            usedBytes = runtime.totalMemory() - runtime.freeMemory(),
            totalBytes = runtime.totalMemory(),
            freeBytes = runtime.freeMemory()
        ))
    }
    
    /**
     * Record error
     */
    internal fun recordError(operation: String, component: String, error: Throwable) {
        notify(ProfilingEvent.ErrorOccurred(operation, component, error))
    }
    
    /**
     * Record custom metric
     */
    fun recordCustomMetric(operation: String, metricName: String, value: Any) {
        notify(ProfilingEvent.CustomMetric(operation, metricName, value))
    }
    
    /**
     * Start async operation tracking
     */
    fun startOperation(operation: String, component: String): String {
        val operationId = UUID.randomUUID().toString()
        notify(ProfilingEvent.OperationStarted(operation, component, operationId))
        return operationId
    }
    
    /**
     * Complete async operation tracking
     */
    fun completeOperation(operation: String, component: String, operationId: String, durationMs: Long) {
        notify(ProfilingEvent.OperationCompleted(operation, component, operationId, durationMs))
    }
    
    /**
     * Profile a block of code
     */
    internal inline fun <T> profile(operation: String, component: String, block: () -> T): T {
        if (!strategy.shouldProfile(operation, component)) {
            return block()
        }
        
        val startTime = System.nanoTime()
        
        return try {
            block().also {
                val durationMs = (System.nanoTime() - startTime) / 1_000_000
                recordLatency(operation, component, durationMs)
                
                if (strategy.shouldTrackMemory()) {
                    recordMemory(operation, component)
                }
            }
        } catch (e: Exception) {
            recordError(operation, component, e)
            throw e
        }
    }
    
    /**
     * Start periodic sampling
     */
    private fun restartSampling() {
        stopSampling()
        
        val intervalMs = strategy.getSamplingIntervalMs()
        if (intervalMs > 0) {
            samplingRunnable = object : Runnable {
                override fun run() {
                    sampleMetrics()
                    handler.postDelayed(this, intervalMs)
                }
            }
            handler.post(samplingRunnable!!)
        }
    }
    
    /**
     * Stop periodic sampling
     */
    private fun stopSampling() {
        samplingRunnable?.let { handler.removeCallbacks(it) }
        samplingRunnable = null
    }
    
    /**
     * Sample current metrics
     */
    private fun sampleMetrics() {
        if (strategy.shouldTrackMemory()) {
            recordMemory("sample", "system")
        }
    }
    
    /**
     * Get component name from event
     */
    private fun getComponent(event: ProfilingEvent): String {
        return when (event) {
            is ProfilingEvent.LatencyMeasured -> event.component
            is ProfilingEvent.MemoryMeasured -> event.component
            is ProfilingEvent.ErrorOccurred -> event.component
            is ProfilingEvent.OperationStarted -> event.component
            is ProfilingEvent.OperationCompleted -> event.component
            else -> "unknown"
        }
    }
    
    /**
     * Cleanup resources
     */
    fun shutdown() {
        stopSampling()
        clearObservers()
    }
}

/**
 * Helper class for tracking operation timing
 */
class OperationTimer(
    private val operation: String,
    private val component: String,
    private val profiler: Profiler
) {
    private val startTime = System.nanoTime()
    private val operationId = profiler.startOperation(operation, component)
    
    fun finish() {
        val durationMs = (System.nanoTime() - startTime) / 1_000_000
        profiler.completeOperation(operation, component, operationId, durationMs)
    }
}
