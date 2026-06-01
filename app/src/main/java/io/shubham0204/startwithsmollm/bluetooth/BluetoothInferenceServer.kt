package io.shubham0204.startwithsmollm.bluetooth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import io.shubham0204.startwithsmollm.R
import io.shubham0204.startwithsmollm.gpu.LlamaGPU
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

class BluetoothInferenceServer(
    private val context: Context,
    private val llamaGPU: LlamaGPU?,
    private val getCurrentModel: () -> String,
    private val onRequestReceived: (String, String) -> Unit // deviceName, prompt
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null
    private var isRunning = false
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        private const val TAG = "BTInferenceServer"
        private const val SERVICE_NAME = "SmolLM_Inference"
        val SERVICE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        private const val CHANNEL_ID = "bluetooth_inference"
        private const val NOTIFICATION_ID = 1001
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Inference",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for Bluetooth inference requests"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun startServer() {
        if (isRunning) return
        
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME,
                    SERVICE_UUID
                )
                
                Log.d(TAG, "Server started, waiting for connections...")
                
                while (isRunning) {
                    try {
                        val socket = serverSocket?.accept() // Blocks until connection
                        socket?.let {
                            Log.d(TAG, "Client connected: ${it.remoteDevice.name}")
                            handleClient(it)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Accept failed: ${e.message}")
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }
    
    private fun handleClient(socket: BluetoothSocket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val input = socket.inputStream.bufferedReader()
                val output = socket.outputStream.bufferedWriter()
                
                // Read request
                val requestJson = input.readLine()
                Log.d(TAG, "Received request: $requestJson")
                
                val request = Json.decodeFromString<InferenceRequest>(requestJson)
                
                // Notify UI
                val deviceName = socket.remoteDevice.name ?: "Unknown"
                onRequestReceived(deviceName, request.prompt)
                
                // Show notification
                showInferenceNotification(deviceName, request.prompt, isProcessing = true)
                
                // Check if we have the requested model
                val currentModel = getCurrentModel()
                if (currentModel != request.modelName) {
                    val errorResponse = InferenceResponse(
                        text = "",
                        success = false,
                        errorMessage = "Model mismatch. Have: $currentModel, Requested: ${request.modelName}"
                    )
                    output.write(Json.encodeToString(InferenceResponse.serializer(), errorResponse))
                    output.newLine()
                    output.flush()
                    socket.close()
                    return@launch
                }
                
                // Run inference
                val startTime = System.currentTimeMillis()
                val result = runInference(request.prompt)
                val inferenceTime = System.currentTimeMillis() - startTime
                
                // Send response
                val response = InferenceResponse(
                    text = result,
                    success = true,
                    inferenceTimeMs = inferenceTime
                )
                
                output.write(Json.encodeToString(InferenceResponse.serializer(), response))
                output.newLine()
                output.flush()
                
                Log.d(TAG, "Sent response back (${inferenceTime}ms)")
                
                // Update notification - completed
                showInferenceNotification(
                    socket.remoteDevice.name ?: "Unknown",
                    "Completed in ${inferenceTime}ms",
                    isProcessing = false
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client: ${e.message}")
                // Show error notification
                showInferenceNotification(
                    socket.remoteDevice.name ?: "Unknown",
                    "Error: ${e.message}",
                    isProcessing = false
                )
            } finally {
                try {
                    socket.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing socket: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun runInference(prompt: String): String {
        if (llamaGPU == null) {
            return "Error: LLM not loaded"
        }
        
        try {
            val response = StringBuilder()
            var shouldStop = false
            
            llamaGPU.getResponseAsFlow(prompt).collect { token ->
                if (!shouldStop) {
                    response.append(token)
                    if (token.contains("\n") || response.length > 500) {
                        shouldStop = true
                    }
                }
            }
            
            return response.toString()
        } catch (e: Exception) {
            return "Error: ${e.message}"
        }
    }
    
    private fun showInferenceNotification(deviceName: String, message: String, isProcessing: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(
                if (isProcessing) "🔵 Processing Bluetooth Request"
                else "✅ Bluetooth Inference Complete"
            )
            .setContentText("From: $deviceName")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("From: $deviceName\n\n$message")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(!isProcessing)
            .apply {
                if (isProcessing) {
                    setProgress(0, 0, true) // Indeterminate progress
                    setOngoing(true) // Can't be dismissed while processing
                }
            }
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        // Cancel any ongoing notifications
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
