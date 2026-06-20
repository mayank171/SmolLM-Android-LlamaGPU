package io.shubham0204.startwithsmollm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.shubham0204.startwithsmollm.data.AvailableModels
import io.shubham0204.startwithsmollm.data.DeviceProfile
import io.shubham0204.startwithsmollm.data.DeviceTier
import io.shubham0204.startwithsmollm.data.DownloadState
import io.shubham0204.startwithsmollm.data.ModelCapability
import io.shubham0204.startwithsmollm.data.ModelCompatibility
import io.shubham0204.startwithsmollm.data.ModelInfo
import io.shubham0204.startwithsmollm.data.ModelReliability

data class ModelSelectionUiState(
    val downloadingModelId: String? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val downloadedModelIds: Set<String> = emptySet(),
    val deviceProfile: DeviceProfile? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    uiState: ModelSelectionUiState,
    onDownloadClick: (ModelInfo) -> Unit,
    onDeleteClick: (ModelInfo) -> Unit,
    onStartChat: (ModelInfo) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Select a Model",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Download once, use offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // Device Info Card
            if (uiState.deviceProfile != null) {
                item {
                    DeviceInfoCard(uiState.deviceProfile)
                }
            }
            
            items(AvailableModels.models) { model ->
                val isDownloaded = model.id in uiState.downloadedModelIds
                val isDownloading = uiState.downloadingModelId == model.id
                val compatibility: ModelCompatibility = uiState.deviceProfile?.let { profile ->
                    io.shubham0204.startwithsmollm.data.DeviceCapabilities.getModelCompatibility(model, profile)
                } ?: ModelCompatibility.COMPATIBLE
                
                ModelCard(
                    model = model,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadState = if (isDownloading) uiState.downloadState else DownloadState.Idle,
                    compatibility = compatibility,
                    onDownloadClick = { onDownloadClick(model) },
                    onDeleteClick = { onDeleteClick(model) },
                    onStartChat = { onStartChat(model) }
                )
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DeviceInfoCard(profile: DeviceProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular icon badge
            androidx.compose.material3.Surface(
                modifier = Modifier.size(44.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your Device",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "%.1f GB RAM • %d cores • %s".format(
                        profile.totalRamGB,
                        profile.availableCores,
                        when (profile.deviceTier) {
                            DeviceTier.HIGH -> "High-end"
                            DeviceTier.MEDIUM -> "Mid-range"
                            DeviceTier.LOW -> "Entry-level"
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${profile.optimalThreads} threads",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${profile.maxContextSize} context",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    model: ModelInfo,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadState: DownloadState,
    compatibility: ModelCompatibility,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onStartChat: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expand_rotation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isDownloaded)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: icon + name/params + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular icon badge
                androidx.compose.material3.Surface(
                    modifier = Modifier.size(44.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${model.parameters} • ${model.quantization}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Metadata row — subtle inline text, no chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaText(text = formatSize(model.sizeInMB))
                MetaDot()
                MetaText(text = formatContextShort(model.maxContextSize))
                MetaDot()
                ReliabilityInline(model.reliability)
                Spacer(modifier = Modifier.weight(1f))
                CompatibilityBadge(compatibility)
            }
            
            // Expandable info section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
                    // Capabilities
                    Text(
                        text = "Capabilities",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        model.capabilities.forEach { capability ->
                            CapabilityChip(capability)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Best for
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Best for",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = model.bestFor,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Not good for
                    if (model.notGoodFor.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Not ideal for",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE57373)
                                )
                                Text(
                                    text = model.notGoodFor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Multi-turn warning if applicable
                    if (!model.supportsMultiTurn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Single-turn only — no conversation memory",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
            
            // Download progress
            AnimatedVisibility(
                visible = isDownloading && downloadState is DownloadState.Downloading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    val state = downloadState as? DownloadState.Downloading
                    if (state != null) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${state.downloadedMB} MB / ${state.totalMB} MB (${state.progress}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Error state
            AnimatedVisibility(
                visible = isDownloading && downloadState is DownloadState.Failed,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val state = downloadState as? DownloadState.Failed
                if (state != null) {
                    Text(
                        text = "Error: ${state.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action row: Info text-button on left, primary action on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info text button
                androidx.compose.material3.TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp, vertical = 6.dp
                    )
                ) {
                    Text(
                        text = if (isExpanded) "Less" else "Details",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotationAngle)
                    )
                }
                
                // Primary action
                if (isDownloaded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = onStartChat,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Start Chat")
                        }
                    }
                } else if (isDownloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Downloading…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (compatibility == ModelCompatibility.NOT_RECOMMENDED) {
                    // Disabled — device doesn't have enough RAM
                    Button(
                        onClick = { },
                        enabled = false,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Not supported")
                    }
                } else {
                    Button(onClick = onDownloadClick) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MetaDot() {
    Text(
        text = "·",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 6.dp)
    )
}

@Composable
private fun ReliabilityInline(reliability: ModelReliability) {
    val (label, color, icon) = when (reliability) {
        ModelReliability.LOW -> Triple("Basic", Color(0xFFB8860B), Icons.Filled.Warning)
        ModelReliability.MEDIUM -> Triple("Decent", Color(0xFF757575), Icons.Filled.Bolt)
        ModelReliability.HIGH -> Triple("Reliable", Color(0xFF43A047), Icons.Filled.Verified)
        ModelReliability.VERY_HIGH -> Triple("Excellent", Color(0xFF2E7D32), Icons.Filled.Verified)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

private fun formatSize(sizeMB: Int): String {
    return if (sizeMB >= 1024) {
        val gb = sizeMB / 1024.0
        "%.1f GB".format(gb)
    } else {
        "$sizeMB MB"
    }
}

private fun formatContextShort(tokens: Int): String {
    return when {
        tokens >= 1000 -> "${tokens / 1000}K ctx"
        else -> "$tokens ctx"
    }
}


@Composable
private fun CapabilityChip(capability: ModelCapability) {
    val (icon: ImageVector, label: String, tint: Color) = when (capability) {
        ModelCapability.CHAT -> Triple(Icons.Outlined.ChatBubbleOutline, "Chat", Color(0xFF78909C))
        ModelCapability.REASONING -> Triple(Icons.Outlined.Psychology, "Reasoning", Color(0xFF8E6FAD))
        ModelCapability.CODING -> Triple(Icons.Outlined.Code, "Coding", Color(0xFF26A69A))
        ModelCapability.MATH -> Triple(Icons.Outlined.Calculate, "Math", Color(0xFFE57373))
        ModelCapability.CREATIVE -> Triple(Icons.Outlined.AutoAwesome, "Creative", Color(0xFFFFB74D))
        ModelCapability.SUMMARIZATION -> Triple(Icons.Outlined.Summarize, "Summary", Color(0xFF81C784))
        ModelCapability.KNOWLEDGE -> Triple(Icons.Outlined.School, "Knowledge", Color(0xFF9575CD))
        ModelCapability.OCR -> Triple(Icons.Outlined.DocumentScanner, "OCR", Color(0xFF4FC3F7))
    }
    
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CompatibilityBadge(compatibility: ModelCompatibility) {
    data class CompatStyle(
        val text: String,
        val container: Color,
        val content: Color,
        val icon: ImageVector
    )
    val style = when (compatibility) {
        ModelCompatibility.OPTIMAL -> CompatStyle(
            "Fast",
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            Icons.Filled.Bolt
        )
        ModelCompatibility.COMPATIBLE -> CompatStyle(
            "Good",
            Color(0xFFF5F5F5),
            Color(0xFF616161),
            Icons.Filled.CheckCircle
        )
        ModelCompatibility.SLOW -> CompatStyle(
            "Slow",
            Color(0xFFFFF8E1),
            Color(0xFFE65100),
            Icons.Outlined.HourglassEmpty
        )
        ModelCompatibility.NOT_RECOMMENDED -> CompatStyle(
            "Heavy",
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            Icons.Filled.Warning
        )
    }
    
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(50),
        color = style.container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.content,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = style.text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = style.content
            )
        }
    }
}

