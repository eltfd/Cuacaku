package com.weather.forecast.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.data.model.*
import com.weather.forecast.ui.viewmodel.EnvironmentViewModel
import com.weather.forecast.ui.viewmodel.SeismicUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SEISMIC MONITOR SCREEN
 *
 * Comprehensive real-time monitoring for:
 * - Earthquakes (nearby + significant + all)
 * - Tsunami risk assessment
 * - Volcanic activity
 * - High wave warnings (maritime safety)
 * - Interactive impact area visualization
 */
@Composable
fun SeismicMonitorScreen(
    viewModel: EnvironmentViewModel
) {
    val state by viewModel.seismicState.collectAsState()
    val strings = LocalStrings.current

    when (val current = state) {
        is SeismicUiState.Loading -> SeismicLoadingScreen()
        is SeismicUiState.Success -> SeismicContent(
            data = current.data,
            onRefresh = { viewModel.loadSeismicData() }
        )
        is SeismicUiState.Error -> SeismicErrorScreen(
            message = current.message,
            onRetry = { viewModel.loadSeismicData() }
        )
    }
}

@Composable
private fun SeismicLoadingScreen() {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),
                        Color(0xFF0D47A1),
                        Color(0xFF01579B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = strings.loading,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SeismicErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF37474F), Color(0xFF263238))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(strings.retry)
            }
        }
    }
}

@Composable
private fun SeismicContent(
    data: SeismicMonitorData,
    onRefresh: () -> Unit
) {
    val strings = LocalStrings.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),
                        Color(0xFF0D47A1),
                        Color(0xFF01579B)
                    )
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ──
        item {
            SeismicHeader(data, onRefresh)
        }

        // ── Active threats summary ──
        if (data.hasActiveThreats) {
            item {
                ActiveThreatsBanner(data)
            }
        }

        // ── Impact Areas (if any) ──
        if (data.impactAreas.isNotEmpty()) {
            item {
                ImpactAreaSection(data.impactAreas)
            }
        }

        // ── Tsunami Risk ──
        item {
            TsunamiRiskCard(data.tsunamiRisk)
        }

        // ── Nearby Earthquakes ──
        item {
            EarthquakeSection(
                title = strings.nearbyEarthquakes,
                earthquakes = data.nearbyEarthquakes,
                emptyText = strings.noEarthquakesDetected
            )
        }

        // ── Significant Earthquakes Worldwide ──
        if (data.significantEarthquakes.isNotEmpty()) {
            item {
                EarthquakeSection(
                    title = strings.significantEarthquakes,
                    earthquakes = data.significantEarthquakes.take(5),
                    emptyText = ""
                )
            }
        }

        // ── Volcanic Activity ──
        item {
            VolcanicActivitySection(data.volcanicActivity, data.nearbyVolcanoes)
        }

        // ── High Wave Warning ──
        data.highWaveWarning?.let { wave ->
            item {
                HighWaveWarningSection(wave)
            }
        }

        // ── Data source note ──
        item {
            Text(
                text = strings.seismicDataNote,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  HEADER
// ═══════════════════════════════════════════════════

@Composable
private fun SeismicHeader(
    data: SeismicMonitorData,
    onRefresh: () -> Unit
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = strings.seismicMonitorTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${strings.lastUpdated}: ${formatTime(data.lastUpdated)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = strings.refresh,
                tint = Color.White
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  ACTIVE THREATS BANNER
// ═══════════════════════════════════════════════════

@Composable
private fun ActiveThreatsBanner(data: SeismicMonitorData) {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚨", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.activeThreats,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${strings.totalActiveEvents}: ${data.totalActiveEvents}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
            // Show user in impact zone warning
            if (data.impactAreas.any { it.userInZone }) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.userInImpactZone,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Yellow
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  IMPACT AREA SECTION
// ═══════════════════════════════════════════════════

@Composable
private fun ImpactAreaSection(areas: List<DisasterImpactArea>) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🗺️ ${strings.impactAreas} (${areas.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    areas.forEach { area ->
                        ImpactAreaCard(area)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactAreaCard(area: DisasterImpactArea) {
    val strings = LocalStrings.current
    var showZones by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showZones = !showZones },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(area.severity.colorHex).copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(area.type.icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = area.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = area.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Distance badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(area.severity.colorHex).copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${"%.0f".format(area.distanceFromUserKm)} ${strings.km}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // User in zone indicator
            if (area.userInZone) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.userInImpactZone,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Impact zones detail ──
            AnimatedVisibility(
                visible = showZones,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = strings.impactZones,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    area.zones.forEach { zone ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(zone.colorHex).copy(alpha = zone.alpha))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.localized(zone.label, zone.labelId),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${"%.1f".format(zone.radiusKm)} km",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Text(
                        text = strings.localized(
                            area.zones.lastOrNull()?.description ?: "",
                            area.zones.lastOrNull()?.descriptionId ?: ""
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  TSUNAMI RISK
// ═══════════════════════════════════════════════════

@Composable
private fun TsunamiRiskCard(risk: TsunamiRiskAssessment) {
    val strings = LocalStrings.current
    val bgColor = Color(risk.riskLevel.colorHex).copy(alpha = 0.15f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌊", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = strings.tsunamiRisk,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(risk.riskLevel.colorHex))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.localized(risk.riskLevel.label, risk.riskLevel.labelId),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(risk.riskLevel.colorHex)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = risk.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )

            risk.estimatedArrivalMinutes?.let { arrival ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${strings.estimatedArrival}: ~$arrival ${strings.minutes}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Risk factors
            if (risk.factors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.riskFactors,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                risk.factors.forEach { factor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = factor.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = factor.value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (factor.isElevating) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = risk.recommendation,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  EARTHQUAKE SECTION
// ═══════════════════════════════════════════════════

@Composable
private fun EarthquakeSection(
    title: String,
    earthquakes: List<EarthquakeEvent>,
    emptyText: String
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📳 $title (${earthquakes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (earthquakes.isEmpty()) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        earthquakes.take(10).forEach { eq ->
                            EarthquakeCard(eq)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EarthquakeCard(earthquake: EarthquakeEvent) {
    val strings = LocalStrings.current
    var showDetails by remember { mutableStateOf(false) }

    val magnitudeColor = when {
        earthquake.magnitude >= 7.0 -> Color(0xFFD32F2F)
        earthquake.magnitude >= 5.0 -> Color(0xFFFF9800)
        earthquake.magnitude >= 4.0 -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetails = !showDetails },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = magnitudeColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Magnitude badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(magnitudeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${"%.1f".format(earthquake.magnitude)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = earthquake.place,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatRelativeTime(earthquake.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                // Alert level indicator
                if (earthquake.alertLevel != EarthquakeAlertLevel.UNKNOWN) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(earthquake.alertLevel.colorHex))
                    )
                }
            }

            // ── Expanded details ──
            AnimatedVisibility(
                visible = showDetails,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Info grid
                    Row(modifier = Modifier.fillMaxWidth()) {
                        InfoChip(strings.earthquakeDepth, "${"%.1f".format(earthquake.depthKm)} km", Modifier.weight(1f))
                        InfoChip(strings.distance, "${"%.0f".format(earthquake.distanceFromUserKm)} km", Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        InfoChip(
                            strings.intensity,
                            "${earthquake.intensity.roman} ${strings.localized(earthquake.intensity.label, earthquake.intensity.labelId)}",
                            Modifier.weight(1f)
                        )
                        InfoChip(
                            strings.earthquakeMagnitude,
                            "${earthquake.magnitudeType} ${"%.1f".format(earthquake.magnitude)}",
                            Modifier.weight(1f)
                        )
                    }

                    // Estimated MMI at user
                    if (earthquake.estimatedMMIAtUser > 1.0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val userIntensity = EarthquakeIntensity.fromMMI(earthquake.estimatedMMIAtUser)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(userIntensity.colorHex).copy(alpha = 0.15f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.estimatedMMI,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${userIntensity.roman} (${strings.localized(userIntensity.label, userIntensity.labelId)})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(userIntensity.colorHex)
                            )
                        }
                    }

                    // Felt reports
                    if (earthquake.feltReports > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${strings.earthquakeFelt} ${earthquake.feltReports} ${strings.earthquakePeople}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Tsunami flag
                    if (earthquake.tsunamiFlag) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ ${strings.tsunamiPotentialFlag}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF44336)
                        )
                    }

                    // Impact radius
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${strings.impactRadius}: ~${"%.0f".format(earthquake.estimatedImpactRadiusKm)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    // Status
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (earthquake.isReviewed) "✓ ${strings.earthquakeReviewed}" else "⏳ ${strings.earthquakeAutomatic}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

// ═══════════════════════════════════════════════════
//  VOLCANIC ACTIVITY
// ═══════════════════════════════════════════════════

@Composable
private fun VolcanicActivitySection(
    events: List<VolcanicEvent>,
    nearbyVolcanoes: List<NearbyVolcano>
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    var showNearbyList by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌋 ${strings.volcanoActivity} (${events.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))

                    if (events.isEmpty()) {
                        Text(
                            text = strings.volcanoNoActivity,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    events.forEach { volcano ->
                        VolcanoEventCard(volcano)
                    }

                    // Nearby volcanoes list
                    if (nearbyVolcanoes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNearbyList = !showNearbyList },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🗻 ${strings.volcanoNearbyList} (${nearbyVolcanoes.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Icon(
                                if (showNearbyList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = showNearbyList,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                nearbyVolcanoes.forEach { v ->
                                    NearbyVolcanoRow(v)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolcanoEventCard(volcano: VolcanicEvent) {
    val strings = LocalStrings.current
    val alertColor = Color(volcano.alertLevel.colorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(alertColor.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(alertColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = volcano.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${strings.localized(volcano.alertLevel.label, volcano.alertLevel.labelId)} • ${volcano.source}",
                style = MaterialTheme.typography.bodySmall,
                color = alertColor
            )
            if (volcano.description.isNotBlank()) {
                Text(
                    text = volcano.description.take(120),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = "${"%.0f".format(volcano.distanceFromUserKm)} km",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun NearbyVolcanoRow(volcano: NearbyVolcano) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (volcano.isActive) "🔴" else "⚪",
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = volcano.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "${volcano.type} • ${volcano.elevation}m • ${volcano.country}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${"%.0f".format(volcano.distanceFromUserKm)} km",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            volcano.lastEruption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  HIGH WAVE WARNING
// ═══════════════════════════════════════════════════

@Composable
private fun HighWaveWarningSection(warning: HighWaveWarning) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    var showHourly by remember { mutableStateOf(false) }

    val warningColor = Color(warning.warningLevel.colorHex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = warningColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚢", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = strings.maritimeSafety,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = strings.localized(warning.warningLevel.label, warning.warningLevel.labelId),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = warningColor
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Wave stats
                    Row(modifier = Modifier.fillMaxWidth()) {
                        WaveStatCard(
                            label = strings.waveHeight,
                            value = "${"%.1f".format(warning.currentWaveHeight)} m",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        WaveStatCard(
                            label = strings.maxWaveForecast,
                            value = "${"%.1f".format(warning.maxWaveHeightForecast)} m",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        WaveStatCard(
                            label = strings.swellHeight,
                            value = "${"%.1f".format(warning.currentSwellHeight)} m",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        WaveStatCard(
                            label = "Wind / Gust",
                            value = "${"%.0f".format(warning.maxWindSpeed)} / ${"%.0f".format(warning.maxGustSpeed)} km/h",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = warning.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = warning.recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    // Hourly forecast toggle
                    if (warning.hourlyForecast.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHourly = !showHourly },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.waveHourlyForecast,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Icon(
                                if (showHourly) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = showHourly,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                items(warning.hourlyForecast.take(24)) { hour ->
                                    HourlyWaveChip(hour)
                                }
                            }
                        }
                    }

                    // Data note
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.waveDataNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun HourlyWaveChip(hour: HourlyWaveForecast) {
    val waveColor = Color(hour.warningLevel.colorHex)
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(waveColor.copy(alpha = 0.12f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatHourFromISO(hour.time),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${"%.1f".format(hour.waveHeight)}m",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(waveColor)
        )
    }
}

// ═══════════════════════════════════════════════════
//  UTILITIES
// ═══════════════════════════════════════════════════

private fun formatTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

private fun formatRelativeTime(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

private fun formatHourFromISO(isoTime: String): String {
    return try {
        isoTime.substring(11, 16)
    } catch (e: Exception) {
        isoTime.takeLast(5)
    }
}
