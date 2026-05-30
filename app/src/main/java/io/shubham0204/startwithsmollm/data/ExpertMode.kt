package io.shubham0204.startwithsmollm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ExpertMode - Hidden developer/inference engineer mode
 * 
 * Activation methods (choose one or more):
 * 1. Tap model name 7 times (like Android developer options)
 * 2. Type secret phrase in chat: "enable expert mode"
 * 3. Long press on performance indicator
 * 
 * Features unlocked:
 * - Inference Insights dashboard
 * - Detailed latency breakdown
 * - Context visualization
 * - Memory/thermal monitoring
 * - Export reports
 */
object ExpertMode {
    
    private const val PREFS_NAME = "expert_mode_prefs"
    private const val KEY_EXPERT_MODE = "expert_mode_enabled"
    private const val KEY_TAP_COUNT = "tap_count"
    private const val KEY_LAST_TAP_TIME = "last_tap_time"
    
    private const val TAPS_REQUIRED = 7
    private const val TAP_TIMEOUT_MS = 2000L  // Reset if no tap within 2 seconds
    
    private const val SECRET_PHRASE = "enable expert mode"
    private const val DISABLE_PHRASE = "disable expert mode"
    
    private var prefs: SharedPreferences? = null
    
    private val _isExpertMode = MutableStateFlow(false)
    val isExpertMode: StateFlow<Boolean> = _isExpertMode
    
    private var tapCount = 0
    private var lastTapTime = 0L
    
    /**
     * Initialize with context (call from Application or MainActivity)
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isExpertMode.value = prefs?.getBoolean(KEY_EXPERT_MODE, false) ?: false
    }
    
    /**
     * Handle tap on model name or other trigger element
     * Returns true if expert mode was just activated
     */
    fun onTriggerTap(): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Reset if too much time passed
        if (currentTime - lastTapTime > TAP_TIMEOUT_MS) {
            tapCount = 0
        }
        
        lastTapTime = currentTime
        tapCount++
        
        if (tapCount >= TAPS_REQUIRED) {
            tapCount = 0
            if (!_isExpertMode.value) {
                enableExpertMode()
                return true
            }
        }
        
        return false
    }
    
    /**
     * Get remaining taps needed (for showing toast feedback)
     */
    fun getRemainingTaps(): Int {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime > TAP_TIMEOUT_MS) {
            return TAPS_REQUIRED
        }
        return (TAPS_REQUIRED - tapCount).coerceAtLeast(0)
    }
    
    /**
     * Check if message contains secret phrase
     * Returns: "enabled", "disabled", or null
     */
    fun checkSecretPhrase(message: String): String? {
        val lowerMessage = message.lowercase().trim()
        
        return when {
            lowerMessage == SECRET_PHRASE || lowerMessage == "expert mode" -> {
                enableExpertMode()
                "enabled"
            }
            lowerMessage == DISABLE_PHRASE || lowerMessage == "normal mode" -> {
                disableExpertMode()
                "disabled"
            }
            else -> null
        }
    }
    
    /**
     * Enable expert mode
     */
    fun enableExpertMode() {
        _isExpertMode.value = true
        prefs?.edit()?.putBoolean(KEY_EXPERT_MODE, true)?.apply()
        android.util.Log.d("ExpertMode", "Expert mode ENABLED")
    }
    
    /**
     * Disable expert mode
     */
    fun disableExpertMode() {
        _isExpertMode.value = false
        prefs?.edit()?.putBoolean(KEY_EXPERT_MODE, false)?.apply()
        android.util.Log.d("ExpertMode", "Expert mode DISABLED")
    }
    
    /**
     * Toggle expert mode
     */
    fun toggle() {
        if (_isExpertMode.value) {
            disableExpertMode()
        } else {
            enableExpertMode()
        }
    }
}
