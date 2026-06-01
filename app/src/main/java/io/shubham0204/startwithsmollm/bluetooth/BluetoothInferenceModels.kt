package io.shubham0204.startwithsmollm.bluetooth

import kotlinx.serialization.Serializable

@Serializable
data class InferenceRequest(
    val prompt: String,
    val modelName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class InferenceResponse(
    val text: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val inferenceTimeMs: Long = 0
)

@Serializable
data class DeviceInfo(
    val deviceName: String,
    val deviceAddress: String,
    val availableModels: List<String>,
    val batteryLevel: Int,
    val isAvailable: Boolean = true
)

sealed class BluetoothInferenceState {
    object Idle : BluetoothInferenceState()
    object Discovering : BluetoothInferenceState()
    data class DevicesFound(val devices: List<DeviceInfo>) : BluetoothInferenceState()
    data class Connecting(val deviceName: String) : BluetoothInferenceState()
    data class WaitingForApproval(val deviceName: String) : BluetoothInferenceState()
    data class Processing(val deviceName: String) : BluetoothInferenceState()
    data class Success(val response: String) : BluetoothInferenceState()
    data class Error(val message: String) : BluetoothInferenceState()
}
