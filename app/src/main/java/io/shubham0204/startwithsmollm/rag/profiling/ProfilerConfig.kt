package io.shubham0204.startwithsmollm.rag.profiling

import android.content.Context
import java.io.File

/**
 * Configuration for profiler
 * Use builder pattern for clean setup
 */
data class ProfilerConfig(
    val strategy: ProfilingStrategy,
    val observers: List<ProfilingObserver>,
    val enableLogging: Boolean,
    val enableDashboard: Boolean,
    val enableExport: Boolean,
    val exportDirectory: File?
) {
    
    class Builder(private val context: Context) {
        private var strategy: ProfilingStrategy = NoProfilingStrategy()
        private val observers = mutableListOf<ProfilingObserver>()
        private var enableLogging = false
        private var enableDashboard = false
        private var enableExport = false
        private var exportDirectory: File? = null
        
        /**
         * Set profiling strategy
         */
        fun setStrategy(strategy: ProfilingStrategy) = apply {
            this.strategy = strategy
        }
        
        /**
         * Add custom observer
         */
        fun addObserver(observer: ProfilingObserver) = apply {
            observers.add(observer)
        }
        
        /**
         * Enable logging to Logcat
         */
        fun enableLogging(verbose: Boolean = false) = apply {
            enableLogging = true
            observers.add(LoggingObserver(verbose = verbose))
        }
        
        /**
         * Enable dashboard observer
         */
        fun enableDashboard() = apply {
            enableDashboard = true
            observers.add(DashboardObserver())
        }
        
        /**
         * Enable metrics export
         */
        fun enableExport(directory: File = context.cacheDir) = apply {
            enableExport = true
            exportDirectory = directory
            observers.add(FileExporter(directory))
        }
        
        /**
         * Enable metrics aggregation
         */
        fun enableAggregation() = apply {
            observers.add(MetricsAggregator())
        }
        
        /**
         * Build configuration
         */
        fun build(): ProfilerConfig {
            return ProfilerConfig(
                strategy = strategy,
                observers = observers.toList(),
                enableLogging = enableLogging,
                enableDashboard = enableDashboard,
                enableExport = enableExport,
                exportDirectory = exportDirectory
            )
        }
    }
    
    /**
     * Apply configuration to profiler
     */
    fun applyTo(profiler: Profiler) {
        profiler.setStrategy(strategy)
        observers.forEach { profiler.addObserver(it) }
    }
}

/**
 * Preset configurations
 */
object ProfilerPresets {
    
    /**
     * Production config - no profiling
     */
    fun production(context: Context) = ProfilerConfig.Builder(context)
        .setStrategy(NoProfilingStrategy())
        .build()
    
    /**
     * Debug config - basic profiling with logging
     */
    fun debug(context: Context) = ProfilerConfig.Builder(context)
        .setStrategy(BasicProfilingStrategy())
        .enableLogging()
        .build()
    
    /**
     * Development config - detailed profiling with dashboard
     */
    fun development(context: Context) = ProfilerConfig.Builder(context)
        .setStrategy(DetailedProfilingStrategy())
        .enableLogging(verbose = true)
        .enableDashboard()
        .enableAggregation()
        .build()
    
    /**
     * Benchmark config - full profiling with export
     */
    fun benchmark(context: Context) = ProfilerConfig.Builder(context)
        .setStrategy(BenchmarkProfilingStrategy())
        .enableLogging()
        .enableExport()
        .enableAggregation()
        .build()
}
