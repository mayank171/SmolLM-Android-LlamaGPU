package io.shubham0204.startwithsmollm.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.shubham0204.startwithsmollm.rag.profiling.MetricsAggregator
import io.shubham0204.startwithsmollm.rag.profiling.Profiler
import io.shubham0204.startwithsmollm.rag.profiling.PerformanceTargets
import io.shubham0204.startwithsmollm.rag.profiling.MetricStatus
import kotlinx.coroutines.launch

/**
 * Inference Insights - Expert mode dashboard for inference engineers
 * 
 * Shows detailed metrics about model inference:
 * - Latency breakdown (TTFT, decode, etc.)
 * - Context usage with O(n²) projection
 * - Memory and thermal monitoring
 * - Optimization status
 */
data class InferenceMetrics(
    // Model info
    val modelName: String = "",
    val modelSize: String = "",
    val quantization: String = "Q4_K_M",
    val contextSize: Int = 4096,
    val threads: Int = 4,
    val flashAttention: Boolean = true,
    val kvCacheType: String = "Q8_0",
    
    // Last inference metrics
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val ttftMs: Long = 0,
    val totalTimeMs: Long = 0,
    val tokensPerSecond: Float = 0f,
    
    // Context
    val contextUsed: Int = 0,
    val contextPercent: Int = 0,
    
    // Memory
    val ramUsedMB: Int = 0,
    val kvCacheMB: Int = 0,
    
    // Computed
    val avgItlMs: Float = 0f,
    val prefillTimeMs: Long = 0,
    val decodeTimeMs: Long = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InferenceInsightsScreen(
    metrics: InferenceMetrics,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tabs = listOf("⚡ Inference", "📊 Performance")
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    // Get metrics aggregator for performance tab
    val metricsAggregator = remember {
        if (Profiler.isInitialized()) {
            val profiler = Profiler.getInstance(context)
            profiler.getObservers().filterIsInstance<MetricsAggregator>().firstOrNull()
                ?: MetricsAggregator().also { profiler.addObserver(it) }
        } else null
    }
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Expert Dashboard",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "EXPERT",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                
                // Tab Row
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (selectedTabIndex) {
            0 -> InferenceTab(metrics, Modifier.padding(padding))
            1 -> PerformanceTab(metricsAggregator, Modifier.padding(padding))
        }
    }
}

@Composable
private fun InferenceTab(metrics: InferenceMetrics, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Model Configuration Card
        ModelConfigCard(metrics)
        
        // Last Inference Card
        LastInferenceCard(metrics)
        
        // Latency Breakdown Card
        LatencyBreakdownCard(metrics)
        
        // Context Analysis Card
        ContextAnalysisCard(metrics)
        
        // Optimization Status Card
        OptimizationStatusCard(metrics)
        
        // Footer
        Text(
            text = "💡 Tip: Type \"disable expert mode\" to hide this",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PerformanceTab(metricsAggregator: MetricsAggregator?, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (metricsAggregator == null) {
            Text(
                "Profiler not available",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            // RAG Performance Section
            Text(
                "📚 RAG Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Document Processing
            val parseAvg = metricsAggregator.getAverageLatency("DocumentParser.parse")
            PerformanceMetricCard(
                title = "Document Processing",
                value = if (parseAvg > 0) "${parseAvg.toInt()}ms" else "—",
                target = "< 500ms (Good)",
                status = PerformanceTargets.evaluateStatus(
                    parseAvg,
                    PerformanceTargets.RAG.DOCUMENT_PROCESSING_GOOD_MS,
                    PerformanceTargets.RAG.DOCUMENT_PROCESSING_ACCEPTABLE_MS
                ),
                description = "Time to parse document content"
            )
            
            // Embedding Generation
            val embedAvg = metricsAggregator.getAverageLatency("EmbeddingModel.embed")
            PerformanceMetricCard(
                title = "Embedding Generation",
                value = if (embedAvg > 0) "${embedAvg.toInt()}ms" else "—",
                target = "< 100ms (Good)",
                status = PerformanceTargets.evaluateStatus(
                    embedAvg,
                    PerformanceTargets.RAG.EMBEDDING_GOOD_MS,
                    PerformanceTargets.RAG.EMBEDDING_ACCEPTABLE_MS
                ),
                description = "Time to generate embeddings"
            )
            
            // Search Performance
            val searchAvg = metricsAggregator.getAverageLatency("VectorDatabase.searchHybrid")
            PerformanceMetricCard(
                title = "Search Performance",
                value = if (searchAvg > 0) "${searchAvg.toInt()}ms" else "—",
                target = "< 50ms (Good)",
                status = PerformanceTargets.evaluateStatus(
                    searchAvg,
                    PerformanceTargets.RAG.SEARCH_GOOD_MS,
                    PerformanceTargets.RAG.SEARCH_ACCEPTABLE_MS
                ),
                description = "Time to search relevant chunks"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // LLM Performance Section
            Text(
                "🤖 LLM Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // TTFT
            val ttftAvg = metricsAggregator.getAverageLatency("LLM.ttft")
            PerformanceMetricCard(
                title = "TTFT (Time To First Token)",
                value = if (ttftAvg > 0) {
                    if (ttftAvg >= 1000) "${String.format("%.1f", ttftAvg / 1000.0)}s" else "${ttftAvg.toInt()}ms"
                } else "—",
                target = "< 3s (Good)",
                status = PerformanceTargets.evaluateStatus(
                    ttftAvg,
                    PerformanceTargets.LLM.TTFT_GOOD_MS,
                    PerformanceTargets.LLM.TTFT_ACCEPTABLE_MS
                ),
                description = "Time until first token"
            )
            
            // ITL
            val itlAvg = metricsAggregator.getAverageLatency("LLM.itl")
            PerformanceMetricCard(
                title = "ITL (Inter-Token Latency)",
                value = if (itlAvg > 0) "${itlAvg.toInt()}ms" else "—",
                target = "< 100ms (Good)",
                status = PerformanceTargets.evaluateStatus(
                    itlAvg,
                    PerformanceTargets.LLM.ITL_GOOD_MS,
                    PerformanceTargets.LLM.ITL_ACCEPTABLE_MS
                ),
                description = "Average time between tokens"
            )
            
            // RAM Usage
            val ramAvg = metricsAggregator.getAverageCustomMetric("LLM.ram_usage_mb")
            PerformanceMetricCard(
                title = "RAM Usage",
                value = if (ramAvg > 0) "${ramAvg.toInt()}MB" else "—",
                target = "< 2GB (Good)",
                status = PerformanceTargets.evaluateStatus(
                    ramAvg,
                    PerformanceTargets.LLM.RAM_GOOD_MB,
                    PerformanceTargets.LLM.RAM_ACCEPTABLE_MB
                ),
                description = "Memory used during inference"
            )
            
            // Footer
            Text(
                text = "💡 Metrics are averaged across all sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PerformanceMetricCard(
    title: String,
    value: String,
    target: String,
    status: MetricStatus,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (status) {
                        MetricStatus.GOOD -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        MetricStatus.ACCEPTABLE -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        MetricStatus.SLOW -> Color(0xFFF44336).copy(alpha = 0.2f)
                        MetricStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (status) {
                            MetricStatus.GOOD -> "✓ Good"
                            MetricStatus.ACCEPTABLE -> "⚠ OK"
                            MetricStatus.SLOW -> "✗ Slow"
                            MetricStatus.UNKNOWN -> "—"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            MetricStatus.GOOD -> Color(0xFF4CAF50)
                            MetricStatus.ACCEPTABLE -> Color(0xFFFF9800)
                            MetricStatus.SLOW -> Color(0xFFF44336)
                            MetricStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (status) {
                        MetricStatus.GOOD -> Color(0xFF4CAF50)
                        MetricStatus.ACCEPTABLE -> Color(0xFFFF9800)
                        MetricStatus.SLOW -> Color(0xFFF44336)
                        MetricStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    target,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelConfigCard(metrics: InferenceMetrics) {
    InsightCard(title = "Model Configuration") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfigRow("Model", metrics.modelName.ifEmpty { "Not loaded" })
            ConfigRow("Size", metrics.modelSize.ifEmpty { "-" })
            ConfigRow("Quantization", metrics.quantization)
            ConfigRow("Context Window", "${metrics.contextSize} tokens")
            ConfigRow("Threads", "${metrics.threads} cores")
            ConfigRow("KV Cache", metrics.kvCacheType)
            ConfigRow("Flash Attention", if (metrics.flashAttention) "✅ Enabled" else "❌ Disabled")
        }
    }
}

@Composable
private fun LastInferenceCard(metrics: InferenceMetrics) {
    InsightCard(title = "Last Inference") {
        if (metrics.outputTokens == 0) {
            Text(
                "No inference yet. Send a message to see metrics.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricBox(
                        icon = Icons.Default.Timer,
                        label = "TTFT",
                        value = formatMs(metrics.ttftMs),
                        color = if (metrics.ttftMs < 2000) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                    MetricBox(
                        icon = Icons.Default.Speed,
                        label = "Speed",
                        value = "${String.format("%.1f", metrics.tokensPerSecond)} tok/s",
                        color = if (metrics.tokensPerSecond > 5) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                    MetricBox(
                        icon = Icons.Default.Memory,
                        label = "RAM",
                        value = "${metrics.ramUsedMB} MB",
                        color = Color(0xFF2196F3)
                    )
                }
                
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Input", style = MaterialTheme.typography.bodySmall)
                    Text("${metrics.inputTokens} tokens", fontFamily = FontFamily.Monospace)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Output", style = MaterialTheme.typography.bodySmall)
                    Text("${metrics.outputTokens} tokens", fontFamily = FontFamily.Monospace)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Time", style = MaterialTheme.typography.bodySmall)
                    Text(formatMs(metrics.totalTimeMs), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun LatencyBreakdownCard(metrics: InferenceMetrics) {
    InsightCard(title = "Latency Breakdown") {
        if (metrics.outputTokens == 0) {
            Text(
                "Run an inference to see breakdown.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // TTFT bar
                LatencyBar(
                    label = "TTFT (Prefill)",
                    timeMs = metrics.ttftMs,
                    maxMs = metrics.totalTimeMs,
                    color = Color(0xFFFF9800),
                    description = "O(n²) attention on full context"
                )
                
                // Decode bar
                val decodeTime = metrics.totalTimeMs - metrics.ttftMs
                LatencyBar(
                    label = "Decode",
                    timeMs = decodeTime,
                    maxMs = metrics.totalTimeMs,
                    color = Color(0xFF4CAF50),
                    description = "${metrics.outputTokens} tokens @ ${String.format("%.0f", metrics.avgItlMs)}ms/token"
                )
                
                Divider()
                
                // Explanation
                Text(
                    "⚠️ TTFT grows O(n²) with context size",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                
                // Projection
                val currentOps = metrics.contextUsed.toLong() * metrics.contextUsed
                val at70Ops = (metrics.contextSize * 0.7).toLong().let { it * it }
                val projectedTtft = if (currentOps > 0) {
                    (metrics.ttftMs * at70Ops / currentOps).coerceAtMost(300000)
                } else 0
                
                Text(
                    "📊 At 70% context: TTFT ≈ ${formatMs(projectedTtft)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ContextAnalysisCard(metrics: InferenceMetrics) {
    InsightCard(title = "Context Analysis") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Context usage bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Usage: ${metrics.contextUsed} / ${metrics.contextSize}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${metrics.contextPercent}%",
                        fontWeight = FontWeight.Bold,
                        color = when {
                            metrics.contextPercent >= 70 -> MaterialTheme.colorScheme.error
                            metrics.contextPercent >= 50 -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    // Used portion
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(metrics.contextPercent / 100f)
                            .background(
                                when {
                                    metrics.contextPercent >= 70 -> MaterialTheme.colorScheme.error
                                    metrics.contextPercent >= 50 -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                }
                            )
                    )
                    
                    // 70% trim marker
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .offset(x = (0.7f * 300).dp)  // Approximate
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    )
                }
                
                Text(
                    "Trim threshold: 70%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Divider()
            
            // Attention complexity
            val attentionOps = metrics.contextUsed.toLong() * metrics.contextUsed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Attention Ops", style = MaterialTheme.typography.bodySmall)
                Text(
                    formatNumber(attentionOps),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // KV Cache size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("KV Cache", style = MaterialTheme.typography.bodySmall)
                Text(
                    "~${metrics.kvCacheMB} MB",
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Estimated turns remaining
            val turnsRemaining = if (metrics.contextPercent > 0) {
                ((70 - metrics.contextPercent) / (metrics.contextPercent / 2f)).toInt().coerceAtLeast(0)
            } else {
                10
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Est. Turns Until Trim", style = MaterialTheme.typography.bodySmall)
                Text(
                    "~$turnsRemaining",
                    fontFamily = FontFamily.Monospace,
                    color = if (turnsRemaining <= 2) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
        }
    }
}

@Composable
private fun OptimizationStatusCard(metrics: InferenceMetrics) {
    InsightCard(title = "Optimization Status") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptimizationRow(
                enabled = true,
                label = "Model Quantization",
                detail = "${metrics.quantization} (~73% size reduction)"
            )
            OptimizationRow(
                enabled = metrics.kvCacheType == "Q8_0",
                label = "KV Cache Quantization",
                detail = "${metrics.kvCacheType} (50% memory savings)"
            )
            OptimizationRow(
                enabled = metrics.flashAttention,
                label = "Flash Attention",
                detail = "Memory-efficient attention"
            )
            OptimizationRow(
                enabled = true,
                label = "Thread Optimization",
                detail = "${metrics.threads} of available cores"
            )
            OptimizationRow(
                enabled = false,
                label = "GPU Acceleration",
                detail = "Vulkan unstable on Adreno"
            )
            OptimizationRow(
                enabled = false,
                label = "Speculative Decoding",
                detail = "Not supported on mobile"
            )
        }
    }
}

// Helper Composables

@Composable
private fun InsightCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MetricBox(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LatencyBar(
    label: String,
    timeMs: Long,
    maxMs: Long,
    color: Color,
    description: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                formatMs(timeMs),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val fraction = if (maxMs > 0) (timeMs.toFloat() / maxMs).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(color)
            )
        }
        
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OptimizationRow(
    enabled: Boolean,
    label: String,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (enabled) "✅" else "❌",
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Utility functions

private fun formatMs(ms: Long): String {
    return when {
        ms >= 60000 -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
        ms >= 1000 -> "${String.format("%.1f", ms / 1000f)}s"
        else -> "${ms}ms"
    }
}

private fun formatNumber(n: Long): String {
    return when {
        n >= 1_000_000_000 -> "${String.format("%.1f", n / 1_000_000_000f)}B"
        n >= 1_000_000 -> "${String.format("%.1f", n / 1_000_000f)}M"
        n >= 1_000 -> "${String.format("%.1f", n / 1_000f)}K"
        else -> n.toString()
    }
}
