package io.shubham0204.startwithsmollm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.shubham0204.smollm.SmolLM
import io.shubham0204.startwithsmollm.gpu.LlamaGPU
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    modelPath: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var cpuResult by remember { mutableStateOf<BenchmarkResultData?>(null) }
    var gpuResult by remember { mutableStateOf<BenchmarkResultData?>(null) }
    var statusMessage by remember { mutableStateOf("Ready to benchmark") }
    var gpuInfo by remember { mutableStateOf("Checking...") }
    var vulkanAvailable by remember { mutableStateOf(false) }
    
    // Check GPU availability on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                gpuInfo = LlamaGPU.getGPUInfo()
                vulkanAvailable = LlamaGPU.isVulkanAvailable()
            } catch (e: Exception) {
                gpuInfo = "GPU check failed: ${e.message}"
                vulkanAvailable = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Benchmark") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // GPU Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (vulkanAvailable) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "GPU Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        gpuInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (vulkanAvailable) "✅ Vulkan GPU acceleration available" 
                        else "⚠️ Vulkan not available - CPU only",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vulkanAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                }
            }
            
            // Model Path
            if (modelPath != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Model",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            modelPath.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            
            // Status
            Text(
                statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Run Benchmark Button
            Button(
                onClick = {
                    if (modelPath == null) {
                        statusMessage = "❌ No model loaded. Please load a model first."
                        return@Button
                    }
                    
                    scope.launch {
                        isRunning = true
                        cpuResult = null
                        gpuResult = null
                        
                        try {
                            // CPU Benchmark (using original SmolLM)
                            statusMessage = "🔄 Running CPU benchmark..."
                            cpuResult = runCpuBenchmark(modelPath)
                            
                            // GPU Benchmark (using LlamaGPU)
                            if (vulkanAvailable) {
                                statusMessage = "🔄 Running GPU benchmark..."
                                gpuResult = runGpuBenchmark(modelPath)
                            }
                            
                            statusMessage = "✅ Benchmark complete!"
                        } catch (e: Exception) {
                            statusMessage = "❌ Error: ${e.message}"
                        } finally {
                            isRunning = false
                        }
                    }
                },
                enabled = !isRunning && modelPath != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Running...")
                } else {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run Benchmark")
                }
            }
            
            // Results
            AnimatedVisibility(visible = cpuResult != null || gpuResult != null) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // SmolLM Result
                    cpuResult?.let { result ->
                        BenchmarkResultCard(
                            title = "SmolLM (Original)",
                            result = result,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                    
                    // LlamaGPU Result
                    gpuResult?.let { result ->
                        BenchmarkResultCard(
                            title = "LlamaGPU (New JNI)",
                            result = result,
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    
                    // Comparison
                    if (cpuResult != null && gpuResult != null) {
                        val speedup = gpuResult!!.tokensPerSecond / cpuResult!!.tokensPerSecond
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    when {
                                        speedup > 1.1 -> "LlamaGPU is ${String.format("%.1f", speedup)}x faster"
                                        speedup < 0.9 -> "SmolLM is ${String.format("%.1f", 1/speedup)}x faster"
                                        else -> "Both perform similarly"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Note: Vulkan GPU disabled due to driver issues",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkResultCard(
    title: String,
    result: BenchmarkResultData,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ResultItem("Speed", "${String.format("%.1f", result.tokensPerSecond)} tok/s")
                ResultItem("Tokens", "${result.tokensGenerated}")
                ResultItem("Time", "${result.durationMs}ms")
            }
        }
    }
}

@Composable
private fun ResultItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class BenchmarkResultData(
    val tokensGenerated: Int,
    val durationMs: Long,
    val tokensPerSecond: Double
)

private suspend fun runCpuBenchmark(modelPath: String): BenchmarkResultData = 
    withContext(Dispatchers.IO) {
        val smolLM = SmolLM()
        try {
            smolLM.load(modelPath, SmolLM.InferenceParams(
                contextSize = 512,
                numThreads = 4
            ))
            
            val testPrompt = "Explain what artificial intelligence is in 2 sentences."
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            
            smolLM.getResponseAsFlow(testPrompt).collect { 
                tokenCount++
            }
            
            val endTime = System.currentTimeMillis()
            val durationMs = endTime - startTime
            val tokensPerSec = if (durationMs > 0) tokenCount * 1000.0 / durationMs else 0.0
            
            BenchmarkResultData(
                tokensGenerated = tokenCount,
                durationMs = durationMs,
                tokensPerSecond = tokensPerSec
            )
        } finally {
            smolLM.close()
        }
    }

private suspend fun runGpuBenchmark(modelPath: String): BenchmarkResultData = 
    withContext(Dispatchers.IO) {
        val llamaGPU = LlamaGPU()
        try {
            // LlamaGPU benchmark (CPU - Vulkan disabled due to Adreno driver issues)
            llamaGPU.load(modelPath, LlamaGPU.InferenceParams(
                contextSize = 512,
                numThreads = 4,
                useGPU = false,
                gpuLayers = 0
            ))
            
            val testPrompt = "Explain what artificial intelligence is in 2 sentences."
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            
            llamaGPU.getResponseAsFlow(testPrompt).collect { 
                tokenCount++
            }
            
            val endTime = System.currentTimeMillis()
            val durationMs = endTime - startTime
            val tokensPerSec = if (durationMs > 0) tokenCount * 1000.0 / durationMs else 0.0
            
            BenchmarkResultData(
                tokensGenerated = tokenCount,
                durationMs = durationMs,
                tokensPerSecond = tokensPerSec
            )
        } finally {
            llamaGPU.close()
        }
    }
