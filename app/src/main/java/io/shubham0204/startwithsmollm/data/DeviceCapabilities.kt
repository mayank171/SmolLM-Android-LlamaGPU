package io.shubham0204.startwithsmollm.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceProfile(
    val totalRamGB: Float,
    val availableCores: Int,
    val deviceTier: DeviceTier,
    val optimalThreads: Int,
    val maxContextSize: Int,
    val canRunLargeModels: Boolean
)

enum class DeviceTier {
    LOW,      // < 4GB RAM
    MEDIUM,   // 4-6GB RAM
    HIGH      // > 6GB RAM
}

object DeviceCapabilities {
    
    private var cachedProfile: DeviceProfile? = null
    
    fun getDeviceProfile(context: Context): DeviceProfile {
        cachedProfile?.let { return it }
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamGB = memoryInfo.totalMem / (1024f * 1024f * 1024f)
        val availableCores = Runtime.getRuntime().availableProcessors()
        
        val deviceTier = when {
            totalRamGB >= 6 -> DeviceTier.HIGH
            totalRamGB >= 4 -> DeviceTier.MEDIUM
            else -> DeviceTier.LOW
        }
        
        val optimalThreads = calculateOptimalThreads(availableCores, deviceTier)
        val maxContextSize = calculateMaxContextSize(totalRamGB, deviceTier)
        val canRunLargeModels = totalRamGB >= 5
        
        val profile = DeviceProfile(
            totalRamGB = totalRamGB,
            availableCores = availableCores,
            deviceTier = deviceTier,
            optimalThreads = optimalThreads,
            maxContextSize = maxContextSize,
            canRunLargeModels = canRunLargeModels
        )
        
        cachedProfile = profile
        return profile
    }
    
    private fun calculateOptimalThreads(cores: Int, tier: DeviceTier): Int {
        // Leave some cores free for UI and system
        // More aggressive on high-end devices
        return when (tier) {
            DeviceTier.HIGH -> (cores - 2).coerceIn(4, 6)
            DeviceTier.MEDIUM -> (cores - 2).coerceIn(2, 4)
            DeviceTier.LOW -> (cores - 1).coerceIn(2, 3)
        }
    }
    
    private fun calculateMaxContextSize(ramGB: Float, tier: DeviceTier): Int {
        // Context size affects KV cache memory usage
        // With Q8_0 KV cache quantization, we can use ~2x the context
        // compared to F16 (default) for the same memory
        // NOTE: 8K tested but TTFT grows too slow (2min at 61% context)
        // Attention is O(n²) - larger context = exponentially slower TTFT
        return when {
            ramGB >= 8 -> 6144   // 8GB+ → 6K context (balanced)
            ramGB >= 6 -> 4096   // 6-8GB → 4K context (optimal for speed)
            ramGB >= 4 -> 2048   // 4-6GB → 2K context
            ramGB >= 3 -> 1024   // 3-4GB → 1K context
            else -> 512          // <3GB → 512 context
        }
    }
    
    fun getModelCompatibility(model: ModelInfo, profile: DeviceProfile): ModelCompatibility {
        val requiredRamGB = when {
            model.sizeInMB >= 1500 -> 6f  // Gemma 2B
            model.sizeInMB >= 1000 -> 5f  // Qwen 1.5B
            model.sizeInMB >= 400 -> 3f   // Qwen 0.5B
            else -> 2f                     // SmolLM 360M
        }
        
        return when {
            profile.totalRamGB >= requiredRamGB + 2 -> ModelCompatibility.OPTIMAL
            profile.totalRamGB >= requiredRamGB -> ModelCompatibility.COMPATIBLE
            profile.totalRamGB >= requiredRamGB - 1 -> ModelCompatibility.SLOW
            else -> ModelCompatibility.NOT_RECOMMENDED
        }
    }
    
    fun getContextSizeForModel(model: ModelInfo, profile: DeviceProfile): Int {
        // Adjust context based on model size and device capability
        // With Q8_0 KV cache, we have more headroom
        val baseContext = profile.maxContextSize
        
        // Larger models need more memory for weights, so reduce context
        // With Q8_0 KV cache, we have 50% memory savings, so less reduction needed
        val contextMultiplier = when {
            model.sizeInMB >= 1500 -> 0.75f  // Gemma 2B - 75% context
            model.sizeInMB >= 1000 -> 1.0f   // Qwen 1.5B - FULL context (Q8_0 is efficient!)
            else -> 1.0f                      // Smaller models - full context
        }
        
        // Also respect model's own maxContextSize limit
        return minOf(
            (baseContext * contextMultiplier).toInt(),
            model.maxContextSize
        ).coerceAtLeast(512)
    }
}

enum class ModelCompatibility {
    OPTIMAL,        // Runs great
    COMPATIBLE,     // Runs well
    SLOW,           // Will be slow but works
    NOT_RECOMMENDED // May crash or be unusable
}
