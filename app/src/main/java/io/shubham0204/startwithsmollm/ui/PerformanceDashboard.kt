package io.shubham0204.startwithsmollm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.shubham0204.startwithsmollm.rag.profiling.DashboardObserver
import io.shubham0204.startwithsmollm.rag.profiling.Profiler
import io.shubham0204.startwithsmollm.rag.profiling.ProfilingEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceDashboard(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Get dashboard observer if profiler is available
    val dashboardObserver = remember {
        if (Profiler.isInitialized()) {
            val profiler = Profiler.getInstance(context)
            // Find existing dashboard observer or create one
            DashboardObserver().also { observer ->
                profiler.addObserver(observer)
            }
        } else null
    }
    
    // Collect events from dashboard observer
    val events by dashboardObserver?.events?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val latencyStats by dashboardObserver?.latencyStats?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Dashboard") },
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
        if (dashboardObserver == null) {
            // Profiler not available
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Profiler not available\nEnable profiling in debug builds",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Cards
                item {
                    Text(
                        "Performance Metrics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Latency Statistics
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Latency Statistics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (latencyStats.isEmpty()) {
                                Text(
                                    "No operations recorded yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            } else {
                                latencyStats.forEach { (operation, stats) ->
                                    LatencyStatRow(operation, stats)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
                
                // Memory Usage
                item {
                    val memoryEvents = events.filterIsInstance<ProfilingEvent.MemoryMeasured>()
                    if (memoryEvents.isNotEmpty()) {
                        val latest = memoryEvents.last()
                        val usedMB = latest.usedBytes / 1024 / 1024
                        val totalMB = latest.totalBytes / 1024 / 1024
                        val percentage = (usedMB.toFloat() / totalMB * 100).toInt()
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Memory Usage",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    "${usedMB}MB / ${totalMB}MB",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                LinearProgressIndicator(
                                    progress = { percentage / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = when {
                                        percentage > 90 -> Color.Red
                                        percentage > 75 -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.tertiary
                                    }
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    "$percentage% utilized",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                // Recent Events
                item {
                    Text(
                        "Recent Events (${events.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(events.takeLast(20).reversed()) { event ->
                    EventCard(event)
                }
            }
        }
    }
}

@Composable
fun LatencyStatRow(operation: String, stats: DashboardObserver.LatencyStats) {
    Column {
        Text(
            operation,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatChip("Avg", "${stats.avgMs.toInt()}ms")
            StatChip("Min", "${stats.minMs}ms")
            StatChip("Max", "${stats.maxMs}ms")
            StatChip("P95", "${stats.p95Ms}ms")
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EventCard(event: ProfilingEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (event) {
                is ProfilingEvent.ErrorOccurred -> MaterialTheme.colorScheme.errorContainer
                is ProfilingEvent.LatencyMeasured -> {
                    when {
                        event.durationMs > 200 -> Color(0xFFFFEBEE)
                        event.durationMs > 100 -> Color(0xFFFFF3E0)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                }
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                when (event) {
                    is ProfilingEvent.LatencyMeasured -> Icons.Default.Speed
                    is ProfilingEvent.MemoryMeasured -> Icons.Default.Memory
                    else -> Icons.Default.Timer
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (event) {
                    is ProfilingEvent.ErrorOccurred -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                val componentName = when (event) {
                    is ProfilingEvent.LatencyMeasured -> event.component
                    is ProfilingEvent.MemoryMeasured -> event.component
                    is ProfilingEvent.CpuMeasured -> "System"
                    is ProfilingEvent.ErrorOccurred -> event.component
                    is ProfilingEvent.OperationStarted -> event.component
                    is ProfilingEvent.OperationCompleted -> event.component
                    is ProfilingEvent.CustomMetric -> "System"
                }
                Text(
                    "[$componentName] ${event.operation}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                when (event) {
                    is ProfilingEvent.LatencyMeasured -> {
                        Text(
                            "${event.durationMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                event.durationMs > 200 -> Color.Red
                                event.durationMs > 100 -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is ProfilingEvent.MemoryMeasured -> {
                        val usedMB = event.usedBytes / 1024 / 1024
                        Text(
                            "${usedMB}MB used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is ProfilingEvent.ErrorOccurred -> {
                        Text(
                            event.error.message ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is ProfilingEvent.CustomMetric -> {
                        Text(
                            "${event.metricName}: ${event.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
