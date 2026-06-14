package io.shubham0204.startwithsmollm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.shubham0204.startwithsmollm.data.AvailableModels
import io.shubham0204.startwithsmollm.data.DeviceProfile
import io.shubham0204.startwithsmollm.data.DeviceTier
import io.shubham0204.startwithsmollm.data.DownloadState
import io.shubham0204.startwithsmollm.data.ModelCompatibility
import io.shubham0204.startwithsmollm.data.ModelInfo

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
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular icon badge on dark background
            androidx.compose.material3.Surface(
                modifier = Modifier.size(44.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
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
                    color = MaterialTheme.colorScheme.onPrimary
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
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${profile.optimalThreads} threads",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "${profile.maxContextSize} context",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                )
            }
        }
    }
}

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
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Circular icon badge
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(40.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (isDownloaded)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${model.parameters} • ${model.quantization}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Compatibility Badge
                CompatibilityBadge(compatibility)
                
                Spacer(modifier = Modifier.width(8.dp))
                
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Size chip
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "${model.sizeInMB} MB",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloaded) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onStartChat) {
                        Text("Start Chat")
                    }
                } else if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Downloading...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedButton(onClick = onDownloadClick) {
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
private fun CompatibilityBadge(compatibility: ModelCompatibility) {
    val (text, containerColor, contentColor) = when (compatibility) {
        ModelCompatibility.OPTIMAL -> Triple(
            "⚡ Fast",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        ModelCompatibility.COMPATIBLE -> Triple(
            "✓ Good",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        ModelCompatibility.SLOW -> Triple(
            "🐢 Slow",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        ModelCompatibility.NOT_RECOMMENDED -> Triple(
            "⚠️ Heavy",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }
    
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

