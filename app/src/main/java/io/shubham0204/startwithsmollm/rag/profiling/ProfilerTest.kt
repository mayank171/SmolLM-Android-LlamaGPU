package io.shubham0204.startwithsmollm.rag.profiling

import android.content.Context
import android.util.Log

/**
 * Simple test class to verify profiler functionality
 * Call from MainActivity or any other component to test
 */
class ProfilerTest(private val context: Context) {
    
    companion object {
        private const val TAG = "ProfilerTest"
    }
    
    /**
     * Run all profiler tests
     */
    fun runTests() {
        Log.d(TAG, "╔═══════════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║           🧪 PROFILER TESTS                                   ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
        
        testProfilerInitialization()
        testBasicProfiling()
        testMemoryTracking()
        testErrorTracking()
        testAsyncOperations()
        testObservers()
        
        Log.d(TAG, "╔═══════════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║           ✅ ALL TESTS COMPLETED                              ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
    }
    
    private fun testProfilerInitialization() {
        Log.d(TAG, "\n--- Test 1: Profiler Initialization ---")
        
        val isInitialized = Profiler.isInitialized()
        Log.d(TAG, "Profiler initialized: $isInitialized")
        
        if (isInitialized) {
            val profiler = Profiler.getInstance(context)
            Log.d(TAG, "✅ Profiler instance obtained successfully")
            Log.d(TAG, "Current strategy: ${profiler.strategy::class.simpleName}")
        } else {
            Log.e(TAG, "❌ Profiler not initialized!")
        }
    }
    
    private fun testBasicProfiling() {
        Log.d(TAG, "\n--- Test 2: Basic Profiling ---")
        
        val profiler = Profiler.getInstance(context)
        
        // Test inline profile function
        val result = profiler.profile("test_operation", "ProfilerTest") {
            Thread.sleep(50) // Simulate work
            "Test Result"
        }
        
        Log.d(TAG, "✅ Inline profiling completed: $result")
        
        // Test manual latency recording
        profiler.recordLatency("manual_test", "ProfilerTest", 25)
        Log.d(TAG, "✅ Manual latency recorded")
    }
    
    private fun testMemoryTracking() {
        Log.d(TAG, "\n--- Test 3: Memory Tracking ---")
        
        val profiler = Profiler.getInstance(context)
        profiler.recordMemory("memory_test", "ProfilerTest")
        
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val totalMb = runtime.totalMemory() / 1024 / 1024
        
        Log.d(TAG, "✅ Memory tracked: ${usedMb}MB / ${totalMb}MB")
    }
    
    private fun testErrorTracking() {
        Log.d(TAG, "\n--- Test 4: Error Tracking ---")
        
        val profiler = Profiler.getInstance(context)
        
        try {
            throw Exception("Test exception for profiling")
        } catch (e: Exception) {
            profiler.recordError("error_test", "ProfilerTest", e)
            Log.d(TAG, "✅ Error tracked successfully")
        }
    }
    
    private fun testAsyncOperations() {
        Log.d(TAG, "\n--- Test 5: Async Operations ---")
        
        val profiler = Profiler.getInstance(context)
        
        val operationId = profiler.startOperation("async_test", "ProfilerTest")
        Log.d(TAG, "Started async operation: $operationId")
        
        Thread.sleep(30) // Simulate async work
        
        profiler.completeOperation("async_test", "ProfilerTest", operationId, 30)
        Log.d(TAG, "✅ Async operation completed")
    }
    
    private fun testObservers() {
        Log.d(TAG, "\n--- Test 6: Observers ---")
        
        val profiler = Profiler.getInstance(context)
        
        // Add a test observer
        val testObserver = object : ProfilingObserver {
            override fun onEvent(event: ProfilingEvent) {
                Log.d(TAG, "Test observer received: ${event::class.simpleName}")
            }
        }
        
        profiler.addObserver(testObserver)
        Log.d(TAG, "✅ Test observer added")
        
        // Trigger an event
        profiler.recordLatency("observer_test", "ProfilerTest", 10)
        
        // Remove observer
        profiler.removeObserver(testObserver)
        Log.d(TAG, "✅ Test observer removed")
    }
    
    /**
     * Test different profiling strategies
     */
    fun testStrategies() {
        Log.d(TAG, "\n╔═══════════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║           🧪 STRATEGY TESTS                                   ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
        
        val profiler = Profiler.getInstance(context)
        
        // Test each strategy
        val strategies = listOf(
            NoProfilingStrategy() to "NoProfilingStrategy",
            BasicProfilingStrategy() to "BasicProfilingStrategy",
            DetailedProfilingStrategy() to "DetailedProfilingStrategy",
            VerboseProfilingStrategy() to "VerboseProfilingStrategy",
            BenchmarkProfilingStrategy() to "BenchmarkProfilingStrategy"
        )
        
        strategies.forEach { (strategy, name) ->
            Log.d(TAG, "\n--- Testing $name ---")
            profiler.setStrategy(strategy)
            
            Log.d(TAG, "Should profile 'query': ${strategy.shouldProfile("query", "Test")}")
            Log.d(TAG, "Should track memory: ${strategy.shouldTrackMemory()}")
            Log.d(TAG, "Should track CPU: ${strategy.shouldTrackCpu()}")
            Log.d(TAG, "Sampling interval: ${strategy.getSamplingIntervalMs()}ms")
            
            // Test profiling with this strategy
            profiler.profile("strategy_test", "ProfilerTest") {
                Thread.sleep(10)
            }
        }
        
        Log.d(TAG, "\n✅ All strategy tests completed")
    }
    
    /**
     * Test metrics aggregator
     */
    fun testMetricsAggregator() {
        Log.d(TAG, "\n╔═══════════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║           🧪 METRICS AGGREGATOR TEST                          ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════════╝")
        
        val profiler = Profiler.getInstance(context)
        val aggregator = MetricsAggregator()
        
        profiler.addObserver(aggregator)
        
        // Generate some test data
        repeat(10) { i ->
            profiler.recordLatency("test_op", "ProfilerTest", (50 + i * 10).toLong())
        }
        
        // Get statistics
        val avg = aggregator.getAverageLatency("ProfilerTest.test_op")
        val p50 = aggregator.getPercentile("ProfilerTest.test_op", 50)
        val p95 = aggregator.getPercentile("ProfilerTest.test_op", 95)
        
        Log.d(TAG, "Average latency: ${"%.2f".format(avg)}ms")
        Log.d(TAG, "P50 latency: ${p50}ms")
        Log.d(TAG, "P95 latency: ${p95}ms")
        
        Log.d(TAG, "\nSummary:")
        Log.d(TAG, aggregator.getSummary())
        
        profiler.removeObserver(aggregator)
        Log.d(TAG, "✅ Metrics aggregator test completed")
    }
}
