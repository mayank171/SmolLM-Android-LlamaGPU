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
    override fun getSamplingIntervalMs() = 1000L
}

/**
 * Verbose profiling - everything including CPU
 */
class VerboseProfilingStrategy : ProfilingStrategy {
    override fun shouldProfile(operation: String, component: String) = true
    override fun shouldTrackMemory() = true
    override fun shouldTrackCpu() = true
    override fun getSamplingIntervalMs() = 500L
}

/**
 * Benchmark profiling - optimized for benchmarking
 */
class BenchmarkProfilingStrategy : ProfilingStrategy {
    override fun shouldProfile(operation: String, component: String) = true
    override fun shouldTrackMemory() = true
    override fun shouldTrackCpu() = true
    override fun getSamplingIntervalMs() = 100L
}
