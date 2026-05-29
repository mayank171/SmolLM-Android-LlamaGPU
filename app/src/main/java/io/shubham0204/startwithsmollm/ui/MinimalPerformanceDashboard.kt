package io.shubham0204.startwithsmollm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.shubham0204.startwithsmollm.rag.profiling.MetricsAggregator
import io.shubham0204.startwithsmollm.rag.profiling.Profiler
import io.shubham0204.startwithsmollm.rag.profiling.PerformanceTargets
import io.shubham0204.startwithsmollm.rag.profiling.MetricStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalPerformanceDashboard(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Get metrics aggregator from profiler
    val metricsAggregator = remember {
        if (Profiler.isInitialized()) {
            val profiler = Profiler.getInstance(context)
            // Get the existing metrics aggregator that was added during initialization
            profiler.getObservers().filterIsInstance<MetricsAggregator>().firstOrNull()
                ?: MetricsAggregator().also { profiler.addObserver(it) }
        } else null
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Metrics") },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (metricsAggregator == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Profiler not available",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                item {
                    Text(
                        "Performance Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // RAG Performance Section
                item {
                    Text(
                        "📚 RAG Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // Document Processing
                item {
                    val parseAvg = metricsAggregator.getAverageLatency("DocumentParser.parse")
                    MetricCard(
                        title = "Document Processing",
                        value = if (parseAvg > 0) "${parseAvg.toInt()}ms" else "—",
                        target = PerformanceTargets.getTargetDescription("document_processing"),
                        status = PerformanceTargets.evaluateStatus(
                            parseAvg,
                            PerformanceTargets.RAG.DOCUMENT_PROCESSING_GOOD_MS,
                            PerformanceTargets.RAG.DOCUMENT_PROCESSING_ACCEPTABLE_MS
                        ),
                        description = "Time to parse and extract document content"
                    )
                }
                
                // Embedding Generation
                item {
                    val embedAvg = metricsAggregator.getAverageLatency("EmbeddingModel.embed")
                    MetricCard(
                        title = "Embedding Generation",
                        value = if (embedAvg > 0) "${embedAvg.toInt()}ms" else "—",
                        target = PerformanceTargets.getTargetDescription("embedding"),
                        status = PerformanceTargets.evaluateStatus(
                            embedAvg,
                            PerformanceTargets.RAG.EMBEDDING_GOOD_MS,
                            PerformanceTargets.RAG.EMBEDDING_ACCEPTABLE_MS
                        ),
                        description = "Time to generate embeddings per chunk"
                    )
                }
                
                // Search Performance
                item {
                    val searchAvg = metricsAggregator.getAverageLatency("VectorDatabase.searchHybrid")
                    MetricCard(
                        title = "Search Performance",
                        value = if (searchAvg > 0) "${searchAvg.toInt()}ms" else "—",
                        target = PerformanceTargets.getTargetDescription("search"),
                        status = PerformanceTargets.evaluateStatus(
                            searchAvg,
                            PerformanceTargets.RAG.SEARCH_GOOD_MS,
                            PerformanceTargets.RAG.SEARCH_ACCEPTABLE_MS
                        ),
                        description = "Time to search and retrieve relevant chunks"
                    )
                }
                
                // Total RAG Query Time
                item {
                    val embedAvg = metricsAggregator.getAverageLatency("EmbeddingModel.embed")
                    val searchAvg = metricsAggregator.getAverageLatency("VectorDatabase.searchHybrid")
                    val totalRag = if (embedAvg > 0 && searchAvg > 0) embedAvg + searchAvg else 0.0
                    
                    MetricCard(
                        title = "Total RAG Query Time",
                        value = if (totalRag > 0) "${totalRag.toInt()}ms" else "—",
                        target = PerformanceTargets.getTargetDescription("total_rag_query"),
                        status = PerformanceTargets.evaluateStatus(
                            totalRag,
                            PerformanceTargets.RAG.TOTAL_QUERY_GOOD_MS,
                            PerformanceTargets.RAG.TOTAL_QUERY_ACCEPTABLE_MS
                        ),
                        description = "End-to-end time for RAG-enhanced queries",
                        isHighlight = true
                    )
                }
                
                // Inference Performance Section (Placeholder for LLM metrics)
                item {
                    Text(
                        "🤖 Inference Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                
                // Total Generation Time
                item {
                    val genTimeAvg = metricsAggregator.getAverageLatency("LLM.ttft")
                    MetricCard(
                        title = "Total Generation Time",
                        value = if (genTimeAvg > 0) "${genTimeAvg.toInt()}ms" else "—",
                        target = "< 5000ms",
                        status = PerformanceTargets.evaluateStatus(
                            genTimeAvg,
                            5000L,  // < 5s is good
                            10000L  // < 10s is acceptable
                        ),
                        description = "Time to generate complete response"
                    )
                }
                
                // Tokens per Second
                item {
                    val tokensPerSecAvg = metricsAggregator.getAverageCustomMetric("LLM.tokens_per_second")
                    MetricCard(
                        title = "Tokens per Second",
                        value = if (tokensPerSecAvg > 0) "${tokensPerSecAvg.toInt()} tok/s" else "—",
                        target = "> 10 tok/s",
                        status = when {
                            tokensPerSecAvg <= 0 -> MetricStatus.UNKNOWN
                            tokensPerSecAvg >= 10 -> MetricStatus.GOOD
                            tokensPerSecAvg >= 5 -> MetricStatus.ACCEPTABLE
                            else -> MetricStatus.SLOW
                        },
                        description = "Token generation speed (higher is better)"
                    )
                }
                
                // RAM Usage
                item {
                    val ramAvg = metricsAggregator.getAverageCustomMetric("LLM.ram_usage_mb")
                    MetricCard(
                        title = "RAM Usage",
                        value = if (ramAvg > 0) "${ramAvg.toInt()}MB" else "—",
                        target = PerformanceTargets.getTargetDescription("ram"),
                        status = PerformanceTargets.evaluateStatus(
                            ramAvg,
                            PerformanceTargets.LLM.RAM_GOOD_MB,
                            PerformanceTargets.LLM.RAM_ACCEPTABLE_MB
                        ),
                        description = "Memory used during inference"
                    )
                }
                
                // Battery Drain
                item {
                    val batteryAvg = metricsAggregator.getAverageCustomMetric("LLM.battery_per_1k_tokens")
                    MetricCard(
                        title = "Battery Drain",
                        value = if (batteryAvg > 0) "${batteryAvg.toInt()}mAh/1K" else "—",
                        target = PerformanceTargets.getTargetDescription("battery"),
                        status = PerformanceTargets.evaluateStatus(
                            batteryAvg,
                            PerformanceTargets.LLM.BATTERY_GOOD_MAH,
                            PerformanceTargets.LLM.BATTERY_ACCEPTABLE_MAH
                        ),
                        description = "Estimated battery per 1000 tokens"
                    )
                }
                
                // Help text
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "💡 How to Improve Performance",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "• Use smaller documents (< 10 pages)\n" +
                                "• Reduce chunk size if embedding is slow\n" +
                                "• Use HYBRID search for best results\n" +
                                "• Clear old documents to reduce search time",
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

@Composable
fun MetricCard(
    title: String,
    value: String,
    target: String,
    status: MetricStatus,
    description: String,
    isHighlight: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isHighlight -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHighlight) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title and Status
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
                
                StatusBadge(status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Value and Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (status) {
                        MetricStatus.GOOD -> Color(0xFF4CAF50)
                        MetricStatus.ACCEPTABLE -> Color(0xFFFF9800)
                        MetricStatus.SLOW -> Color(0xFFF44336)
                        MetricStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Text(
                    "Target: $target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Description
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusBadge(status: MetricStatus) {
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = when (status) {
                MetricStatus.GOOD -> Color(0xFF4CAF50)
                MetricStatus.ACCEPTABLE -> Color(0xFFFF9800)
                MetricStatus.SLOW -> Color(0xFFF44336)
                MetricStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
