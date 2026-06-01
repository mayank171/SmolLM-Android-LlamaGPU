package io.shubham0204.startwithsmollm.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

class BluetoothInferenceClient(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    companion object {
        private const val TAG = "BTInferenceClient"
    }
    
    suspend fun discoverDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DeviceInfo>()
        
        try {
            // Get paired devices
            val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
            
            pairedDevices.forEach { device ->
                // For now, assume all paired devices running the app are available
                // In production, you'd query each device for capabilities
                devices.add(
                    DeviceInfo(
                        deviceName = device.name ?: "Unknown",
                        deviceAddress = device.address,
                        availableModels = listOf("unknown"), // Would query in real implementation
                        batteryLevel = 100, // Would query in real implementation
                        isAvailable = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering devices: ${e.message}")
        }
        
        devices
    }
    
    suspend fun sendInferenceRequest(
        deviceAddress: String,
        prompt: String,
        modelName: String
    ): InferenceResponse = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        
        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                ?: return@withContext InferenceResponse(
                    text = "",
                    success = false,
                    errorMessage = "Device not found"
                )
            
            // Connect to device
            socket = device.createRfcommSocketToServiceRecord(BluetoothInferenceServer.SERVICE_UUID)
            socket.connect()
            
            Log.d(TAG, "Connected to ${device.name}")
            
            val input = socket.inputStream.bufferedReader()
            val output = socket.outputStream.bufferedWriter()
            
            // Send request
            val request = InferenceRequest(
                prompt = prompt,
                modelName = modelName
            )
            
            output.write(Json.encodeToString(InferenceRequest.serializer(), request))
            output.newLine()
            output.flush()
            
            Log.d(TAG, "Sent request, waiting for response...")
            
            // Read response
            val responseJson = input.readLine()
            val response = Json.decodeFromString<InferenceResponse>(responseJson)
            
            Log.d(TAG, "Received response: ${response.text.take(50)}...")
            
            response
            
        } catch (e: IOException) {
            Log.e(TAG, "Connection error: ${e.message}")
            InferenceResponse(
                text = "",
                success = false,
                errorMessage = "Connection failed: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            InferenceResponse(
                text = "",
                success = false,
                errorMessage = "Error: ${e.message}"
            )
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing socket: ${e.message}")
            }
        }
    }
}
