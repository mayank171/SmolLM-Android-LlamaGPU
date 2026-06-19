package io.shubham0204.startwithsmollm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ToastType { INFO, SUCCESS, WARNING, ERROR }

/**
 * Modern themed in-app toast overlay. Place at the root of your screen Scaffold.
 * Shows a floating pill at the bottom with icon + message.
 */
@Composable
fun AppToastHost(
    message: String?,
    type: ToastType = ToastType.INFO,
    durationMs: Long = 2500L,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            visible = true
            delay(durationMs)
            visible = false
            delay(220) // wait for exit animation
            onDismiss()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible && !message.isNullOrBlank(),
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            ToastPill(message = message ?: "", type = type)
        }
    }
}

@Composable
private fun ToastPill(message: String, type: ToastType) {
    val (icon: ImageVector, accent: Color) = when (type) {
        ToastType.SUCCESS -> Icons.Filled.CheckCircle to Color(0xFF43A047)
        ToastType.WARNING -> Icons.Filled.Warning to Color(0xFFE65100)
        ToastType.ERROR -> Icons.Filled.Error to Color(0xFFC62828)
        ToastType.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 32.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
        }
    }
}
