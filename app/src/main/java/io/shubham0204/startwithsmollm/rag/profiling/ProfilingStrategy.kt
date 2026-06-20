package io.shubham0204.startwithsmollm.rag.profiling

/**
 * Strategy interface for profiling behavior
 */
interface ProfilingStrategy {
    /**
     * Check if operation should be profiled
     */
    fun shouldProfile(operation: String, component: String): Boolean
    
    /**
     * Check if memory should be tracked
     */
    fun shouldTrackMemory(): Boolean
    
    /**
     * Check if CPU should be tracked
     */
    fun shouldTrackCpu(): Boolean
    
    /**
     * Get sampling interval in milliseconds (0 = no sampling)
     */
    fun getSamplingIntervalMs(): Long
}

/**
 * No profiling - zero overhead
 */
class NoProfilingStrategy : ProfilingStrategy {
    override fun shouldProfile(operation: String, component: String) = false
    override fun shouldTrackMemory() = false
    override fun shouldTrackCpu() = false
    override fun getSamplingIntervalMs() = 0L
}

/**
 * Basic profiling - only key operations
 */
class BasicProfilingStrategy : ProfilingStrategy {
    
    private val keyOperations = setOf(
        "query", "addDocument", "search", "embed", "parse"
    )
    
    override fun shouldProfile(operation: String, component: String): Boolean {
        return keyOperations.any { operation.contains(it, ignoreCase = true) }
    }
    
    override fun shouldTrackMemory() = false
    override fun shouldTrackCpu() = false
    override fun getSamplingIntervalMs() = 0L
}

/**
 * Detailed profiling - all operations + memory
 */
class DetailedProfilingStrategy : ProfilingStrategy {
    override fun shouldProfile(operation: String, component: String) = true
    override fun shouldTrackMemory() = true
    override fun shouldTrackCpu() = false
    // Bumped 1000ms -> 5000ms: memory rarely changes within 1s; reduces CPU/log overhead during inference
    override fun getSamplingIntervalMs() = 5000L
}

/**
 * Verbose profiling - everything including CPU
 */
class VerboseProfilingStrategy : ProfilingStrategy {
    override fun shouldProfile(operation: String, component: String) = true
    override fun shouldTrackMemory() = true
    override fun shouldTrackCpu() = true
    // Bumped 500ms -> 2000ms to reduce CPU contention during inference
    override fun getSamplingIntervalMs() = 2000L
}

/**
 * Benchmark profiling - optimized for benchmarking
 */
class BenchmarkProfilingStrategy : ProfilingStrategy {
    override fun shouldProfile(operation: String, component: String) = true
    override fun shouldTrackMemory() = true
    override fun shouldTrackCpu() = true
    // Bumped 100ms -> 1000ms: 100ms was flooding logcat (10 events/sec across UI thread) and burning CPU during inference
    override fun getSamplingIntervalMs() = 1000L
}
