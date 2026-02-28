package com.weather.forecast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weather.forecast.data.locale.LocalStrings

// ═══════════════════════════════════════════════════
//  SHARED LOADING CONTENT
// ═══════════════════════════════════════════════════

/** Simple centered loading spinner with text — used by Home, AirQuality, WaterQuality */
@Composable
fun SimpleLoadingContent(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text)
        }
    }
}

/** Dark gradient loading with main + sub text — used by DisasterForecast, DisasterMonitor */
@Composable
fun GradientLoadingContent(
    mainText: String,
    subText: String,
    gradientColors: List<Color> = DarkBlueGradient
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = mainText,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subText,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  SHARED ERROR CONTENT
// ═══════════════════════════════════════════════════

/** Simple centered error with CloudOff icon + retry button — used by AirQuality, WaterQuality */
@Composable
fun SimpleErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(LocalStrings.current.retry)
            }
        }
    }
}

/** Dark gradient error with title + message + retry — used by DisasterForecast, DisasterMonitor */
@Composable
fun GradientErrorContent(
    message: String,
    title: String,
    onRetry: () -> Unit,
    gradientColors: List<Color> = DarkBlueGradient
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(LocalStrings.current.retry)
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  SHARED UI COMPONENTS
// ═══════════════════════════════════════════════════

/** Semi-transparent white card — replaces repeated Card + CardDefaults.cardColors pattern */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    alpha: Float = 0.15f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = alpha)),
        shape = shape
    ) {
        Column(content = content)
    }
}

/** Small colored badge/chip for status/risk labels */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    alpha: Float = 0.3f,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 4.dp,
    textColor: Color = Color.White,
    style: androidx.compose.ui.text.TextStyle? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = alpha))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text = text,
            style = style ?: MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

// ═══════════════════════════════════════════════════
//  SHARED CONSTANTS & EXTENSIONS
// ═══════════════════════════════════════════════════

/** Standard dark blue gradient used by Disaster screens */
val DarkBlueGradient = listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB))

/** Toggle an item in/out of a set — used for expandedDays state */
fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
