package io.shubham0204.startwithsmollm

import android.app.Application
import android.util.Log
import io.shubham0204.startwithsmollm.data.ExpertMode
import io.shubham0204.startwithsmollm.rag.profiling.Profiler
import io.shubham0204.startwithsmollm.rag.profiling.ProfilerPresets
import io.shubham0204.startwithsmollm.rag.profiling.ProfilerTest

/**
 * Application class for SmolLM
 * Initializes profiler and other global components
 */
class SmolLMApplication : Application() {
    
    companion object {
        private const val TAG = "SmolLMApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "╔═══════════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║           🚀 SmolLM Application Starting                      ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
        
        // Initialize ExpertMode (hidden developer mode)
        ExpertMode.init(this)
        
        // Initialize profiler
        initializeProfiler()
        
        // Run profiler tests in debug builds
        if (BuildConfig.DEBUG) {
            runProfilerTests()
        }
    }
    
    private fun initializeProfiler() {
        Log.d(TAG, "Initializing profiler...")
        
        val profiler = Profiler.getInstance(this)
        
        // Use preset based on build type
        val config = if (BuildConfig.DEBUG) {
            ProfilerPresets.development(this)
        } else {
            ProfilerPresets.production(this)
        }
        
        config.applyTo(profiler)
        
        Log.d(TAG, "✅ Profiler initialized with ${if (BuildConfig.DEBUG) "development" else "production"} preset")
    }
    
    private fun runProfilerTests() {
        Log.d(TAG, "Running profiler tests...")
        
        try {
            val test = ProfilerTest(this)
            test.runTests()
            test.testStrategies()
            test.testMetricsAggregator()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Profiler tests failed", e)
        }
    }
}
