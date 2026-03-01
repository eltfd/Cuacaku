package com.weather.forecast.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.data.model.*
import com.weather.forecast.ui.viewmodel.EnvironmentViewModel
import com.weather.forecast.ui.viewmodel.SeismicUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════
//  SHARED COLORS & COMPOSABLES
// ═══════════════════════════════════════════════════

/** Semantic color palette for the seismic monitor screen. */
private object SC {
    val red = Color(0xFFF44336)
    val deepRed = Color(0xFFD32F2F)
    val darkRed = Color(0xFFB71C1C)
    val orange = Color(0xFFFF9800)
    val lightOrange = Color(0xFFFFB74D)
    val amber = Color(0xFFFFC107)
    val yellow = Color(0xFFFFEB3B)
    val green = Color(0xFF4CAF50)
    val blue = Color(0xFF2196F3)
    val deepOrange = Color(0xFFFF5722)
    val brown = Color(0xFF795548)
    val blueGrey = Color(0xFF78909C)
    val grey = Color(0xFF9E9E9E)
    val lime = Color(0xFFCDDC39)
    val lightRed = Color(0xFFEF9A9A)
    val lightAmber = Color(0xFFFFCC80)
    val darkIndigo = Color(0xFF1A237E)
    val darkBlue = Color(0xFF0D47A1)
    val lightBlue = Color(0xFF01579B)
    val darkGreen = Color(0xFF1B5E20)

    val backgroundGradient = listOf(darkIndigo, darkBlue, lightBlue)
    val errorGradient = listOf(Color(0xFF37474F), Color(0xFF263238))

    fun magnitude(mag: Double) = when {
        mag >= 7.0 -> deepRed; mag >= 5.0 -> orange; mag >= 4.0 -> amber; else -> green
    }
}

private val expandEnter = expandVertically() + fadeIn()
private val expandExit = shrinkVertically() + fadeOut()

/** Reusable expandable card section with header and animated content. */
@Composable
private fun ExpandableSection(
    title: String,
    containerColor: Color = Color.White.copy(alpha = 0.1f),
    border: BorderStroke? = null,
    initialExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, tint = Color.White.copy(alpha = 0.7f)
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandEnter, exit = expandExit) {
                content()
            }
        }
    }
}

/**
 * Reusable card for crowdsourced report sections.
 * Encapsulates: Card + emoji header + subtitle + expand/collapse + AnimatedVisibility.
 *
 * @param emoji Header emoji
 * @param title Section title (e.g. "Flood Reports (12)")
 * @param subtitle Subtitle line (e.g. "PetaBencana.id • 7 days")
 * @param themeColor Section theme color
 * @param middleContent Optional composable between header and expandable content (chips, alerts)
 * @param content Expandable content
 */
@Composable
private fun ReportSectionCard(
    emoji: String,
    title: String,
    subtitle: String,
    themeColor: Color,
    border: BorderStroke? = null,
    middleContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.12f)),
        border = border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, tint = Color.White.copy(alpha = 0.7f)
                )
            }
            middleContent()
            AnimatedVisibility(visible = expanded, enter = expandEnter, exit = expandExit) {
                content()
            }
        }
    }
}

/** Small pill chip used for summary stats in report sections. */
@Composable
private fun SummaryChip(text: String, color: Color, textColor: Color = Color.White) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.2f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Nearby alert badge shown between header and content. */
@Composable
private fun NearbyReportAlert(count: Int) {
    if (count <= 0) return
    val strings = LocalStrings.current
    Spacer(modifier = Modifier.height(8.dp))
    Surface(shape = RoundedCornerShape(8.dp), color = SC.orange.copy(alpha = 0.2f)) {
        Text(
            text = "⚠\uFE0F $count ${strings.localized("reports within 100 km", "laporan dalam 100 km")}",
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            color = SC.lightOrange, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun NearbyAlertBadge(count: Int) {
    val strings = LocalStrings.current
    Surface(shape = RoundedCornerShape(8.dp), color = SC.orange.copy(alpha = 0.2f)) {
        Text(
            text = "⚠️ $count ${strings.localized("reports within 100 km", "laporan dalam 100 km")}",
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            color = SC.lightOrange,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AreaDistributionRow(
    items: List<Map.Entry<String, Int>>,
    color: Color
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { (area, count) ->
            Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.2f)) {
                Text(
                    "$area ($count)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SafetyWarningBox(emoji: String, title: String, description: String, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.15f)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

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

    when (val current = state) {
        is SeismicUiState.Loading -> SeismicLoadingScreen()
        is SeismicUiState.Success -> SeismicContent(
            data = current.data,
            onRefresh = { viewModel.loadSeismicData() },
            onActivateSOS = { viewModel.activateSOS() },
            onDeactivateSOS = { viewModel.deactivateSOS() }
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
            .background(Brush.verticalGradient(SC.backgroundGradient)),
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
            .background(Brush.verticalGradient(SC.errorGradient)),
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
    onRefresh: () -> Unit,
    onActivateSOS: () -> Unit = {},
    onDeactivateSOS: () -> Unit = {}
) {
    val strings = LocalStrings.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(SC.backgroundGradient)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ──
        item {
            SeismicHeader(data, onRefresh)
        }

        // ── Disaster Phase Banner ──
        if (data.overallPhase != DisasterLifecyclePhase.NORMAL) {
            item {
                DisasterPhaseBanner(data.overallPhase, data.lifecycleStates)
            }
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

        // ── Early Warning System ──
        val earlyWarningStates = data.lifecycleStates.filter { it.phase == DisasterLifecyclePhase.EARLY_WARNING }
        if (earlyWarningStates.isNotEmpty()) {
            item {
                EarlyWarningSection(earlyWarningStates)
            }
        }

        // ── Active Disaster Enhanced Panel ──
        val activeDisasterStates = data.lifecycleStates.filter { it.phase == DisasterLifecyclePhase.ACTIVE_DISASTER }
        if (activeDisasterStates.isNotEmpty()) {
            item {
                ActiveDisasterPanel(activeDisasterStates)
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

        // ── BMKG Official Earthquake Data (Indonesia) ──
        if (data.bmkgEarthquakes.isNotEmpty()) {
            item {
                BmkgEarthquakeSection(data.bmkgEarthquakes)
            }
        }

        // ── PetaBencana Per-Category Disaster Reports ──
        val reportsByType = data.crowdsourcedReports.groupBy { it.disasterType }

        // Flood reports
        reportsByType[CrowdsourcedDisasterType.FLOOD]?.let { floodReports ->
            if (floodReports.isNotEmpty()) {
                item { FloodReportSection(floodReports) }
            }
        }

        // Crowdsourced earthquake reports
        reportsByType[CrowdsourcedDisasterType.EARTHQUAKE]?.let { quakeReports ->
            if (quakeReports.isNotEmpty()) {
                item { CrowdsourcedEarthquakeReportSection(quakeReports) }
            }
        }

        // Wind reports
        reportsByType[CrowdsourcedDisasterType.WIND]?.let { windReports ->
            if (windReports.isNotEmpty()) {
                item { WindReportSection(windReports) }
            }
        }

        // Haze reports
        reportsByType[CrowdsourcedDisasterType.HAZE]?.let { hazeReports ->
            if (hazeReports.isNotEmpty()) {
                item { HazeReportSection(hazeReports) }
            }
        }

        // Fire reports
        reportsByType[CrowdsourcedDisasterType.FIRE]?.let { fireReports ->
            if (fireReports.isNotEmpty()) {
                item { FireReportSection(fireReports) }
            }
        }

        // Crowdsourced volcano reports
        reportsByType[CrowdsourcedDisasterType.VOLCANO]?.let { volcanoReports ->
            if (volcanoReports.isNotEmpty()) {
                item { CrowdsourcedVolcanoReportSection(volcanoReports) }
            }
        }

        // ── Landslide Risk Monitoring ──
        data.landslideAnalysis?.let { analysis ->
            item {
                LandslideMonitorSection(
                    analysis = analysis,
                    terrainData = data.landslideTerrainData
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

        // ── Post-Disaster Relief Points ──
        if (data.reliefPoints.isNotEmpty()) {
            item {
                PostDisasterReliefSection(data.reliefPoints)
            }
        }

        // ── Emergency Contacts ──
        if (data.emergencyContacts.isNotEmpty() && data.overallPhase != DisasterLifecyclePhase.NORMAL) {
            item {
                EmergencyContactsSection(data.emergencyContacts)
            }
        }

        // ── SOS System ──
        if (data.showSOS || data.sosState.isActivated) {
            item {
                SOSSection(
                    sosState = data.sosState,
                    onActivateSOS = onActivateSOS,
                    onDeactivateSOS = onDeactivateSOS
                )
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

    ExpandableSection(title = "🗺️ ${strings.impactAreas} (${areas.size})") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            areas.forEach { area -> ImpactAreaCard(area) }
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
    ExpandableSection(title = "📳 $title (${earthquakes.size})") {
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
//  BMKG EARTHQUAKE SECTION (Indonesian Official Data)
// ═══════════════════════════════════════════════════

@Composable
private fun BmkgEarthquakeSection(earthquakes: List<BmkgEarthquakeEvent>) {
    val strings = LocalStrings.current
    ExpandableSection(
        title = "🇮🇩 ${strings.localized("BMKG Earthquakes", "Gempa BMKG")} (${earthquakes.size})",
        containerColor = SC.darkGreen.copy(alpha = 0.3f)
    ) {
        Text(
            text = strings.localized(
                "Official Indonesian seismic data",
                "Data seismik resmi Indonesia"
            ),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            earthquakes.take(10).forEach { eq ->
                BmkgEarthquakeCard(eq)
            }
        }
    }
}

@Composable
private fun BmkgEarthquakeCard(earthquake: BmkgEarthquakeEvent) {
    val strings = LocalStrings.current

    val magnitudeColor = when {
        earthquake.magnitude >= 7.0 -> Color(0xFFD32F2F)
        earthquake.magnitude >= 5.0 -> Color(0xFFFF9800)
        earthquake.magnitude >= 4.0 -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        text = earthquake.region,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${earthquake.dateString} • ${earthquake.timeString}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${strings.localized("Depth", "Kedalaman")}: ${"%.0f".format(earthquake.depthKm)} km • " +
                            "${strings.distance}: ${"%.0f".format(earthquake.distanceFromUserKm)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // Potential & felt report
            if (earthquake.potential.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = earthquake.potential,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (earthquake.hasTsunamiPotential) Color(0xFFF44336) else Color.White.copy(alpha = 0.7f),
                    fontWeight = if (earthquake.hasTsunamiPotential) FontWeight.Bold else FontWeight.Normal
                )
            }

            earthquake.feltReport?.let { felt ->
                if (felt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${strings.localized("Felt", "Dirasakan")}: $felt",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB74D)
                    )
                }
            }

            // Source label
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "BMKG • data.bmkg.go.id",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}


// ═══════════════════════════════════════════════════
//  PER-CATEGORY DISASTER REPORT SECTIONS (PetaBencana.id)
// ═══════════════════════════════════════════════════

// ─── FLOOD REPORTS (Banjir) ───

@Composable
private fun FloodReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    val nearbyReports = reports.filter { it.isNearby }
    val maxDepth = reports.mapNotNull { it.floodDepthCm }.maxOrNull() ?: 0
    val avgDepth = reports.mapNotNull { it.floodDepthCm }.let { list ->
        if (list.isNotEmpty()) list.average().toInt() else 0
    }
    val depthCounts = mapOf(
        strings.localized("Low", "Rendah") to reports.count { (it.floodDepthCm ?: 0) in 1..29 },
        strings.localized("Medium", "Sedang") to reports.count { (it.floodDepthCm ?: 0) in 30..69 },
        strings.localized("Deep", "Dalam") to reports.count { (it.floodDepthCm ?: 0) in 70..149 },
        strings.localized("Very Deep", "Sangat Dalam") to reports.count { (it.floodDepthCm ?: 0) >= 150 }
    )

    ReportSectionCard(
        emoji = "\uD83C\uDF0A",
        title = "${strings.localized("Flood Reports", "Laporan Banjir")} (${reports.size})",
        subtitle = "PetaBencana.id \u2022 ${strings.localized("7 days", "7 hari")}",
        themeColor = SC.blue,
        middleContent = { NearbyReportAlert(nearbyReports.size) }
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            // Flood Summary Stats
            Row(modifier = Modifier.fillMaxWidth()) {
                FloodStatBox(
                    label = strings.localized("Max Depth", "Kedalaman Maks"),
                    value = "${maxDepth} cm",
                    color = when {
                        maxDepth >= 150 -> SC.red; maxDepth >= 70 -> SC.orange
                        maxDepth >= 30 -> SC.yellow; else -> SC.blue
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloodStatBox(
                    label = strings.localized("Avg Depth", "Kedalaman Rata\u00B2"),
                    value = "${avgDepth} cm", color = SC.blue, modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloodStatBox(
                    label = strings.localized("Nearby", "Terdekat"),
                    value = "${nearbyReports.size}", color = SC.green, modifier = Modifier.weight(1f)
                )
            }

            // Depth Severity Distribution
            if (depthCounts.values.any { it > 0 }) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.localized("Depth Distribution", "Distribusi Kedalaman"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                val maxCount = depthCounts.values.maxOrNull()?.toFloat() ?: 1f
                val depthColors = listOf(SC.green, SC.yellow, SC.orange, SC.red)
                depthCounts.entries.forEachIndexed { index, (label, count) ->
                    if (count > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(90.dp))
                            Box(
                                modifier = Modifier.weight(1f).height(14.dp)
                                    .clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxHeight()
                                        .fillMaxWidth(fraction = if (maxCount > 0f) (count / maxCount).coerceIn(0.05f, 1f) else 0.05f)
                                        .clip(RoundedCornerShape(7.dp)).background(depthColors[index])
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$count", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Individual Flood Report Cards
            Spacer(modifier = Modifier.height(12.dp))
            reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                FloodReportCard(report)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun FloodStatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun FloodReportCard(report: CrowdsourcedDisasterReport) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)
    val floodBlue = Color(0xFF2196F3)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = floodBlue.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Water level gauge
                report.floodDepthCm?.let { depth ->
                    val gaugeHeight = ((depth.coerceIn(0, 300)) / 300f * 40f).coerceAtLeast(8f)
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gaugeHeight.dp)
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    when {
                                        depth >= 150 -> Color(0xFFF44336)
                                        depth >= 70 -> Color(0xFFFF9800)
                                        depth >= 30 -> Color(0xFFFFEB3B)
                                        else -> floodBlue
                                    }.copy(alpha = 0.8f)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                } ?: run {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(floodBlue.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) { Text("\uD83C\uDF0A", fontSize = 20.sp) }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.floodDepthCm?.let { depth ->
                            Text(
                                text = "${depth}cm",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = floodBlue
                            )
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        report.floodSeverityLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Row {
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "$timeAgo \u2022 ${"%.0f".format(report.distanceFromUserKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = report.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── CROWDSOURCED EARTHQUAKE REPORTS (Gempa - Laporan Warga) ───

@Composable
private fun CrowdsourcedEarthquakeReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    val nearbyReports = reports.filter { it.isNearby }
    val damageCounts = (0..4).associateWith { level -> reports.count { it.structureDamage == level } }
    val damageLabels = listOf(
        strings.localized("None", "Tidak Ada"), strings.localized("Light", "Ringan"),
        strings.localized("Moderate", "Sedang"), strings.localized("Heavy", "Berat"),
        strings.localized("Severe", "Sangat Berat")
    )

    ReportSectionCard(
        emoji = "\uD83C\uDF0D",
        title = "${strings.localized("Earthquake Reports", "Laporan Gempa Warga")} (${reports.size})",
        subtitle = "PetaBencana.id \u2022 ${strings.localized("Crowdsourced", "Laporan Warga")}",
        themeColor = SC.orange,
        middleContent = { NearbyReportAlert(nearbyReports.size) }
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            // Structure Damage Distribution
            val hasStructureData = damageCounts.values.any { it > 0 }
            if (hasStructureData) {
                Text(
                    text = strings.localized("Structure Damage", "Kerusakan Struktur"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                val maxDmg = damageCounts.values.maxOrNull()?.toFloat() ?: 1f
                val dmgColors = listOf(SC.green, SC.lime, SC.yellow, SC.orange, SC.red)
                damageCounts.entries.forEachIndexed { index, (_, count) ->
                    if (count > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(damageLabels[index], style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                            Box(modifier = Modifier.weight(1f).height(14.dp)
                                .clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.08f))) {
                                Box(modifier = Modifier.fillMaxHeight()
                                    .fillMaxWidth(fraction = if (maxDmg > 0f) (count / maxDmg).coerceIn(0.05f, 1f) else 0.05f)
                                    .clip(RoundedCornerShape(7.dp)).background(dmgColors[index]))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Individual Cards
            reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                CrowdsourcedQuakeCard(report)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CrowdsourcedQuakeCard(report: CrowdsourcedDisasterReport) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)
    val quakeOrange = Color(0xFFFF9800)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = quakeOrange.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Damage level badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (report.structureDamage) {
                                4 -> Color(0xFFF44336)
                                3 -> Color(0xFFFF9800)
                                2 -> Color(0xFFFFEB3B)
                                1 -> Color(0xFFCDDC39)
                                else -> quakeOrange
                            }.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = report.structureDamage?.let { "$it/4" } ?: "\uD83C\uDF0D",
                        fontSize = if (report.structureDamage != null) 14.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.structureDamageLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = quakeOrange)
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        text = "$timeAgo \u2022 ${"%.0f".format(report.distanceFromUserKm)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Extra chips
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                report.accessibilityFailure?.let { acc ->
                    if (acc > 0) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF44336).copy(alpha = 0.2f)) {
                            Text(
                                text = "\uD83D\uDEA7 ${strings.localized("Road Access", "Akses Jalan")}: $acc/4",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEF9A9A),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (report.evacuationArea == true) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF44336).copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83D\uDEA8 ${strings.localized("Evacuation", "Evakuasi")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── WIND REPORTS (Angin Kencang) ───

@Composable
private fun WindReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    val nearbyReports = reports.filter { it.isNearby }
    val significantImpact = reports.count { (it.windImpact ?: 0) >= 1 }

    ReportSectionCard(
        emoji = "\uD83D\uDCA8",
        title = "${strings.localized("Strong Wind Reports", "Laporan Angin Kencang")} (${reports.size})",
        subtitle = "PetaBencana.id \u2022 ${strings.localized("7 days", "7 hari")}",
        themeColor = SC.blueGrey,
        middleContent = {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip("\uD83D\uDCCD ${strings.localized("Nearby", "Terdekat")}: ${nearbyReports.size}", Color.White)
                if (significantImpact > 0) {
                    SummaryChip("\u26A0\uFE0F ${strings.localized("Significant Impact", "Dampak Signifikan")}: $significantImpact", SC.red, SC.lightRed)
                }
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            val provinceCounts = reports.groupBy { it.cityName ?: strings.localized("Unknown", "Tidak Diketahui") }
                .mapValues { it.value.size }.entries.sortedByDescending { it.value }.take(5)
            if (provinceCounts.isNotEmpty()) {
                Text(strings.localized("Affected Areas", "Wilayah Terdampak"),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(provinceCounts) { (city, count) ->
                        SummaryChip("$city ($count)", SC.blueGrey)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                GenericDisasterReportCard(report, SC.blueGrey)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ─── HAZE REPORTS (Kabut Asap) ───

@Composable
private fun HazeReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    val nearbyReports = reports.filter { it.isNearby }

    ReportSectionCard(
        emoji = "\uD83C\uDF2B\uFE0F",
        title = "${strings.localized("Haze Reports", "Laporan Kabut Asap")} (${reports.size})",
        subtitle = "PetaBencana.id \u2022 ${strings.localized("7 days", "7 hari")}",
        themeColor = SC.grey,
        middleContent = { NearbyReportAlert(nearbyReports.size) }
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            SafetyWarningBox(
                emoji = "\uD83D\uDC41\uFE0F",
                title = strings.localized("Visibility Warning", "Peringatan Jarak Pandang"),
                description = strings.localized(
                    "Haze may reduce visibility and affect air quality. Use mask when outdoors.",
                    "Kabut asap dapat mengurangi jarak pandang dan mempengaruhi kualitas udara. Gunakan masker saat beraktivitas di luar."
                ),
                color = SC.grey
            )
            Spacer(modifier = Modifier.height(8.dp))

            val areaCounts = reports.groupBy { it.cityName ?: strings.localized("Unknown", "Tidak Diketahui") }
                .mapValues { it.value.size }.entries.sortedByDescending { it.value }.take(5)
            if (areaCounts.isNotEmpty()) {
                Text(strings.localized("Affected Areas", "Wilayah Terdampak"),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(areaCounts) { (city, count) ->
                        SummaryChip("$city ($count)", SC.grey)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                GenericDisasterReportCard(report, SC.grey)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ─── FIRE REPORTS (Kebakaran Hutan) ───

@Composable
private fun FireReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    val nearbyReports = reports.filter { it.isNearby }
    val evacuationReports = reports.filter { it.evacuationArea == true }
    val totalEvacuees = reports.mapNotNull { it.evacuationNumber }.sum()

    ReportSectionCard(
        emoji = "\uD83D\uDD25",
        title = "${strings.localized("Forest Fire Reports", "Laporan Kebakaran Hutan")} (${reports.size})",
        subtitle = "PetaBencana.id \u2022 ${strings.localized("7 days", "7 hari")}",
        themeColor = SC.red,
        middleContent = {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nearbyReports.isNotEmpty()) SummaryChip("\u26A0\uFE0F ${strings.localized("Nearby", "Terdekat")}: ${nearbyReports.size}", SC.orange, SC.lightOrange)
                if (evacuationReports.isNotEmpty()) SummaryChip("\uD83D\uDEA8 ${strings.localized("Evacuation", "Evakuasi")}: ${evacuationReports.size}", SC.red, SC.lightRed)
                if (totalEvacuees > 0) SummaryChip("\uD83E\uDDD1 ${strings.localized("Evacuees", "Pengungsi")}: $totalEvacuees", SC.red, SC.lightRed)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            SafetyWarningBox(
                emoji = "\uD83D\uDEA8",
                title = strings.localized("Fire Safety", "Keselamatan Kebakaran"),
                description = strings.localized(
                    "Stay away from fire areas. Follow evacuation instructions from authorities.",
                    "Jauhi area kebakaran. Ikuti instruksi evakuasi dari pihak berwenang."
                ),
                color = SC.red
            )
            Spacer(modifier = Modifier.height(8.dp))

            val areaCounts = reports.groupBy { it.cityName ?: strings.localized("Unknown", "Tidak Diketahui") }
                .mapValues { it.value.size }.entries.sortedByDescending { it.value }.take(5)
            if (areaCounts.isNotEmpty()) {
                Text(strings.localized("Fire Locations", "Lokasi Kebakaran"),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(areaCounts) { (city, count) ->
                        SummaryChip("$city ($count)", SC.red)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                FireReportCard(report)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun FireReportCard(report: CrowdsourcedDisasterReport) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)
    val fireRed = Color(0xFFF44336)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = fireRed.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(fireRed.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) { Text("\uD83D\uDD25", fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = fireRed)
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "${"%.0f".format(report.distanceFromUserKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(timeAgo, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }

            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.evacuationArea == true) {
                    Surface(shape = RoundedCornerShape(12.dp), color = fireRed.copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83D\uDEA8 ${strings.localized("Evacuation Zone", "Zona Evakuasi")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                report.evacuationNumber?.let { num ->
                    if (num > 0) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF9800).copy(alpha = 0.2f)) {
                            Text(
                                text = "\uD83E\uDDD1 $num ${strings.localized("evacuees", "pengungsi")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFCC80),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── CROWDSOURCED VOLCANO REPORTS (Gunung Api - Laporan Warga) ───

@Composable
private fun CrowdsourcedVolcanoReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    val nearbyReports = reports.filter { it.isNearby }
    val evacuationReports = reports.filter { it.evacuationArea == true }
    val allSigns = reports.flatMap { it.volcanicSignsLabels }.groupBy { it }.mapValues { it.value.size }

    ReportSectionCard(
        emoji = "\uD83C\uDF0B",
        title = "${strings.localized("Volcano Reports", "Laporan Gunung Api")} (${reports.size})",
        subtitle = "PetaBencana.id \u2022 ${strings.localized("Crowdsourced", "Laporan Warga")}",
        themeColor = SC.deepRed,
        middleContent = {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nearbyReports.isNotEmpty()) SummaryChip("\u26A0\uFE0F ${strings.localized("Nearby", "Terdekat")}: ${nearbyReports.size}", SC.orange, SC.lightOrange)
                if (evacuationReports.isNotEmpty()) SummaryChip("\uD83D\uDEA8 ${strings.localized("Evacuation", "Evakuasi")}: ${evacuationReports.size}", SC.deepRed, SC.lightRed)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            if (allSigns.isNotEmpty()) {
                Text(strings.localized("Observed Volcanic Signs", "Tanda Vulkanik Teramati"),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allSigns.entries.toList()) { (sign, count) ->
                        SummaryChip("$sign ($count)", SC.deepRed)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                VolcanoReportCard(report)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun VolcanoReportCard(report: CrowdsourcedDisasterReport) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)
    val volcanoRed = Color(0xFFD32F2F)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = volcanoRed.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(volcanoRed.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) { Text("\uD83C\uDF0B", fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = volcanoRed)
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "${"%.0f".format(report.distanceFromUserKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(timeAgo, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Volcanic signs chips
            if (report.volcanicSignsLabels.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.volcanicSignsLabels.forEach { sign ->
                        Surface(shape = RoundedCornerShape(12.dp), color = volcanoRed.copy(alpha = 0.2f)) {
                            Text(
                                text = sign,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEF9A9A),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Evacuation chips
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.evacuationArea == true) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF44336).copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83D\uDEA8 ${strings.localized("Evacuation Zone", "Zona Evakuasi")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                report.evacuationNumber?.let { num ->
                    if (num > 0) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF9800).copy(alpha = 0.2f)) {
                            Text(
                                text = "\uD83E\uDDD1 $num ${strings.localized("evacuees", "pengungsi")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFCC80),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── GENERIC DISASTER REPORT CARD (for Wind / Haze) ───

@Composable
private fun GenericDisasterReportCard(report: CrowdsourcedDisasterReport, themeColor: Color) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(themeColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) { Text(report.emoji, fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = themeColor)
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "${"%.0f".format(report.distanceFromUserKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(timeAgo, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Wind impact chip
            report.windImpact?.let { impact ->
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = (if (impact >= 1) Color(0xFFF44336) else Color(0xFFFF9800)).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = report.windImpactLabel ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (impact >= 1) Color(0xFFEF9A9A) else Color(0xFFFFCC80),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}


/**
 * Format epoch millis to human-readable time ago string.
 */
private fun formatTimeAgo(timeMillis: Long, strings: com.weather.forecast.data.locale.AppStrings): String {
    val now = System.currentTimeMillis()
    val diff = now - timeMillis
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> strings.localized("Just now", "Baru saja")
        minutes < 60 -> strings.localized("${minutes}m ago", "${minutes} menit lalu")
        hours < 24 -> strings.localized("${hours}h ago", "${hours} jam lalu")
        days < 7 -> strings.localized("${days}d ago", "${days} hari lalu")
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timeMillis))
    }
}

// ═══════════════════════════════════════════════════
//  LANDSLIDE RISK MONITORING
// ═══════════════════════════════════════════════════

@Composable
private fun LandslideMonitorSection(
    analysis: DisasterPrediction,
    terrainData: LandslideTerrainData?
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    val landslideBrown = Color(0xFF795548)
    val riskScore = analysis.riskScore
    val riskLevel = analysis.riskLevel

    val riskColor = when (riskLevel) {
        RiskLevel.EXTREME -> Color(0xFFD32F2F)
        RiskLevel.HIGH -> Color(0xFFFF5722)
        RiskLevel.MODERATE -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }

    val riskLabel = when (riskLevel) {
        RiskLevel.EXTREME -> strings.localized("EXTREME", "BAHAYA")
        RiskLevel.HIGH -> strings.localized("High", "Tinggi")
        RiskLevel.MODERATE -> strings.localized("Moderate", "Sedang")
        else -> strings.localized("Low", "Rendah")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = landslideBrown.copy(alpha = 0.14f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⛰️", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.localized("Landslide Monitoring", "Pemantauan Longsor"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = strings.localized(
                                "Real-time risk analysis",
                                "Analisis risiko real-time"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            // ── Risk Level Badge ──
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = riskColor.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(riskColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${strings.localized("Risk", "Risiko")}: $riskLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = riskColor
                        )
                    }
                    Text(
                        text = "${(riskScore * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = riskColor
                    )
                }
            }

            // ── Risk Score Bar ──
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = riskScore.toFloat().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) })
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336))
                            )
                        )
                )
            }

            // ── Confidence ──
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${strings.localized("Confidence", "Keyakinan")}: ${(analysis.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // ── Terrain Quick Stats ──
                    terrainData?.let { terrain ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            LandslideStatBox(
                                label = strings.localized("Slope", "Kemiringan"),
                                value = "%.1f°".format(terrain.slopeAngle),
                                subLabel = strings.localized(
                                    terrain.slopeCategory.label,
                                    terrain.slopeCategory.labelId
                                ),
                                color = Color(terrain.slopeCategory.colorHex),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            LandslideStatBox(
                                label = strings.localized("Soil Saturation", "Saturasi Tanah"),
                                value = "${(terrain.soilSaturationIndex * 100).toInt()}%",
                                subLabel = when {
                                    terrain.soilSaturationIndex > 0.8 -> strings.localized("Saturated", "Jenuh")
                                    terrain.soilSaturationIndex > 0.5 -> strings.localized("Wet", "Basah")
                                    else -> strings.localized("Normal", "Normal")
                                },
                                color = when {
                                    terrain.soilSaturationIndex > 0.8 -> Color(0xFFD32F2F)
                                    terrain.soilSaturationIndex > 0.5 -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            LandslideStatBox(
                                label = strings.localized("Vegetation", "Vegetasi"),
                                value = "${(terrain.vegetationIndex * 100).toInt()}%",
                                subLabel = when {
                                    terrain.vegetationIndex < 0.3 -> strings.localized("Bare", "Gundul")
                                    terrain.vegetationIndex < 0.6 -> strings.localized("Sparse", "Jarang")
                                    else -> strings.localized("Dense", "Lebat")
                                },
                                color = when {
                                    terrain.vegetationIndex < 0.3 -> Color(0xFFF44336)
                                    terrain.vegetationIndex < 0.6 -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Rainfall Metrics ──
                        Row(modifier = Modifier.fillMaxWidth()) {
                            LandslideStatBox(
                                label = strings.localized("Rain Today", "Hujan Hari Ini"),
                                value = "%.1f mm".format(terrain.todayPrecipitation),
                                subLabel = when {
                                    terrain.todayPrecipitation > 100 -> strings.localized("Very Heavy", "Sangat Lebat")
                                    terrain.todayPrecipitation > 50 -> strings.localized("Heavy", "Lebat")
                                    terrain.todayPrecipitation > 20 -> strings.localized("Moderate", "Sedang")
                                    else -> strings.localized("Light", "Ringan")
                                },
                                color = when {
                                    terrain.todayPrecipitation > 100 -> Color(0xFFD32F2F)
                                    terrain.todayPrecipitation > 50 -> Color(0xFFFF5722)
                                    terrain.todayPrecipitation > 20 -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            LandslideStatBox(
                                label = strings.localized("Rain 3-Day", "Hujan 3 Hari"),
                                value = "%.0f mm".format(terrain.antecedentRainfall),
                                subLabel = when {
                                    terrain.antecedentRainfall > 150 -> strings.localized("Critical", "Kritis")
                                    terrain.antecedentRainfall > 100 -> strings.localized("High", "Tinggi")
                                    terrain.antecedentRainfall > 50 -> strings.localized("Alert", "Waspada")
                                    else -> strings.localized("Safe", "Aman")
                                },
                                color = when {
                                    terrain.antecedentRainfall > 150 -> Color(0xFFD32F2F)
                                    terrain.antecedentRainfall > 100 -> Color(0xFFFF5722)
                                    terrain.antecedentRainfall > 50 -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            LandslideStatBox(
                                label = strings.localized("Max Intensity", "Intensitas Maks"),
                                value = "%.1f mm/h".format(terrain.maxRainfallIntensity),
                                subLabel = when {
                                    terrain.maxRainfallIntensity > 50 -> strings.localized("Extreme", "Ekstrem")
                                    terrain.maxRainfallIntensity > 20 -> strings.localized("Heavy", "Deras")
                                    terrain.maxRainfallIntensity > 10 -> strings.localized("Moderate", "Sedang")
                                    else -> strings.localized("Light", "Ringan")
                                },
                                color = when {
                                    terrain.maxRainfallIntensity > 50 -> Color(0xFFD32F2F)
                                    terrain.maxRainfallIntensity > 20 -> Color(0xFFFF5722)
                                    terrain.maxRainfallIntensity > 10 -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // ── Soil Moisture Layers ──
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${strings.localized("Soil Moisture", "Kelembaban Tanah")}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SoilLayerBar(
                            label = strings.localized("Shallow (0-7cm)", "Dangkal (0-7cm)"),
                            value = terrain.soilMoistureShallow,
                            maxValue = 0.5
                        )
                        SoilLayerBar(
                            label = strings.localized("Medium (7-28cm)", "Sedang (7-28cm)"),
                            value = terrain.soilMoistureMedium,
                            maxValue = 0.5
                        )
                        SoilLayerBar(
                            label = strings.localized("Deep (28-100cm)", "Dalam (28-100cm)"),
                            value = terrain.soilMoistureDeep,
                            maxValue = 0.5
                        )

                        // ── Elevation Grid ──
                        if (terrain.elevationGrid.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${strings.localized("Elevation", "Elevasi")}: ${"%.0f".format(terrain.elevation)} m",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(terrain.elevationGrid) { point ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = landslideBrown.copy(alpha = 0.2f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = point.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                text = "${"%.0f".format(point.elevation)}m",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 8-Factor Risk Breakdown ──
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${strings.localized("Risk Factors", "Faktor Risiko")}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val factorWeights = listOf(0.20, 0.15, 0.15, 0.15, 0.10, 0.10, 0.05, 0.10)
                    analysis.factors.forEachIndexed { index, factor ->
                        val weight = factorWeights.getOrElse(index) { 0.10 }
                        LandslideFactorRow(
                            name = factor.name,
                            value = factor.value,
                            contribution = factor.contribution,
                            weight = weight,
                            isElevating = factor.isElevating
                        )
                    }

                    // ── Description & Recommendation ──
                    if (analysis.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = riskColor.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = analysis.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                if (analysis.recommendation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "💡 ${analysis.recommendation}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // ── Data Source Note ──
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.localized(
                            "Source: Open-Elevation (SRTM) • Open-Meteo (rainfall & soil)",
                            "Sumber: Open-Elevation (SRTM) • Open-Meteo (curah hujan & tanah)"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LandslideStatBox(
    label: String,
    value: String,
    subLabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
        Text(
            text = subLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SoilLayerBar(
    label: String,
    value: Double,
    maxValue: Double
) {
    val fraction = if (maxValue > 0.0) (value / maxValue).toFloat().coerceIn(0f, 1f) else 0f
    val barColor = when {
        fraction > 0.8f -> Color(0xFFD32F2F)
        fraction > 0.5f -> Color(0xFFFF9800)
        fraction > 0.3f -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.width(110.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = fraction.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "%.3f".format(value),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.width(45.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun LandslideFactorRow(
    name: String,
    value: String,
    contribution: Double,
    weight: Double,
    isElevating: Boolean
) {
    val barColor = when {
        contribution > 0.7 -> Color(0xFFD32F2F)
        contribution > 0.4 -> Color(0xFFFF9800)
        contribution > 0.2 -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }
    val indicatorIcon = if (isElevating) "▲" else "─"
    val indicatorColor = if (isElevating) Color(0xFFFF5722) else Color(0xFF4CAF50)

    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = indicatorIcon,
                    style = MaterialTheme.typography.labelSmall,
                    color = indicatorColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${(weight * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = contribution.toFloat().let { if (it.isNaN()) 0.02f else it.coerceIn(0.02f, 1f) })
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
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
    var showNearbyList by remember { mutableStateOf(false) }

    ExpandableSection(title = "🌋 ${strings.volcanoActivity} (${events.size})") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.height(4.dp))
            if (events.isEmpty()) {
                Text(strings.volcanoNoActivity, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
            }
            events.forEach { VolcanoEventCard(it) }

            if (nearbyVolcanoes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showNearbyList = !showNearbyList },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🗻 ${strings.volcanoNearbyList} (${nearbyVolcanoes.size})",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
                    Icon(if (showNearbyList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
                AnimatedVisibility(visible = showNearbyList, enter = expandEnter, exit = expandExit) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        nearbyVolcanoes.forEach { NearbyVolcanoRow(it) }
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
//  DISASTER PHASE BANNER
// ═══════════════════════════════════════════════════

@Composable
private fun DisasterPhaseBanner(
    phase: DisasterLifecyclePhase,
    lifecycleStates: List<DisasterLifecycleState>
) {
    val strings = LocalStrings.current
    val phaseLabel = when (phase) {
        DisasterLifecyclePhase.NORMAL -> strings.phaseNormal
        DisasterLifecyclePhase.EARLY_WARNING -> strings.phaseEarlyWarning
        DisasterLifecyclePhase.ACTIVE_DISASTER -> strings.phaseActiveDisaster
        DisasterLifecyclePhase.POST_DISASTER -> strings.phasePostDisaster
    }
    val phaseDesc = when (phase) {
        DisasterLifecyclePhase.NORMAL -> strings.phaseNormalDesc
        DisasterLifecyclePhase.EARLY_WARNING -> strings.phaseEarlyWarningDesc
        DisasterLifecyclePhase.ACTIVE_DISASTER -> strings.phaseActiveDisasterDesc
        DisasterLifecyclePhase.POST_DISASTER -> strings.phasePostDisasterDesc
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(phase.colorHex).copy(alpha = 0.2f)),
        border = BorderStroke(2.dp, Color(phase.colorHex))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(phase.icon, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${strings.currentPhase}: $phaseLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(phase.colorHex)
                    )
                    Text(
                        text = phaseDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Per-disaster-type lifecycle chips
            if (lifecycleStates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lifecycleStates) { state ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(state.phase.colorHex).copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.type.icon, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.localized(state.type.label, state.type.labelId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(state.phase.colorHex),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  EARLY WARNING SYSTEM
// ═══════════════════════════════════════════════════

@Composable
private fun EarlyWarningSection(states: List<DisasterLifecycleState>) {
    val strings = LocalStrings.current
    ExpandableSection(
        title = "⚠️ ${strings.earlyWarningSystem}",
        containerColor = SC.amber.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, SC.amber.copy(alpha = 0.5f))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            states.forEach { EarlyWarningCard(it) }
        }
    }
}

@Composable
private fun EarlyWarningCard(state: DisasterLifecycleState) {
    val strings = LocalStrings.current
    val warning = state.earlyWarning ?: return
    val warningColor = Color(warning.threatLevel.colorHex)
    var showChecklist by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(warningColor.copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.type.icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.localized(state.type.label, state.type.labelId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = strings.localized(warning.threatLevel.label, warning.threatLevel.labelId),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = warningColor
                )
            }
            // Escalation trend
            val trendText = when (warning.escalationTrend) {
                EscalationTrend.DECREASING -> "↓ ${strings.trendDecreasing}"
                EscalationTrend.STABLE -> "→ ${strings.trendStable}"
                EscalationTrend.INCREASING -> "↑ ${strings.trendIncreasing}"
                EscalationTrend.RAPID_INCREASE -> "⇑ ${strings.trendRapidIncrease}"
            }
            val trendColor = when (warning.escalationTrend) {
                EscalationTrend.DECREASING -> Color(0xFF4CAF50)
                EscalationTrend.STABLE -> Color(0xFFFFC107)
                EscalationTrend.INCREASING -> Color(0xFFFF9800)
                EscalationTrend.RAPID_INCREASE -> Color(0xFFF44336)
            }
            Text(
                text = trendText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = trendColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.summary,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )

        // Estimated onset
        warning.estimatedOnsetHours?.let { onset ->
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${strings.estimatedOnset}: ~$onset ${strings.hours}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Warning indicators
        if (warning.indicators.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = strings.warningIndicators,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            warning.indicators.forEach { indicator ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = indicator.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = indicator.currentValue,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (indicator.isExceeded) Color(0xFFF44336) else Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (indicator.isExceeded) "⚠️" else "✓",
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Preparedness checklist toggle
        if (warning.preparednessChecklist.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showChecklist = !showChecklist },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 ${strings.preparednessChecklist}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Icon(
                    if (showChecklist) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
            AnimatedVisibility(
                visible = showChecklist,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    warning.preparednessChecklist.forEach { item ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.isPriority) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f),
                                fontWeight = if (item.isPriority) FontWeight.Bold else FontWeight.Normal
                            )
                            if (item.isPriority) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.priorityAction,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFC107),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  ACTIVE DISASTER PANEL
// ═══════════════════════════════════════════════════

@Composable
private fun ActiveDisasterPanel(states: List<DisasterLifecycleState>) {
    val context = LocalContext.current
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.2f)),
        border = BorderStroke(2.dp, Color(0xFFF44336))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Prominent header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚨", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = strings.activeDisasterBanner,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF44336)
                    )
                    Text(
                        text = strings.phaseActiveDisasterDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            states.forEach { state ->
                val info = state.activeDisasterInfo ?: return@forEach

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.type.icon, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.localized(state.type.label, state.type.labelId),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    // User in danger zone warning
                    if (info.isUserInDangerZone) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF44336).copy(alpha = 0.3f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = strings.userInDangerZone,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Yellow,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Evacuation directions
                    if (info.evacuationDirections.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🧭 ${strings.evacuationDirections}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        info.evacuationDirections.forEach { dir ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("➤", fontSize = 12.sp, color = Color(0xFF4CAF50))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${strings.localized(dir.direction, dir.directionId)} (${"%.1f".format(dir.distanceKm)} km)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = strings.localized(dir.description, dir.descriptionId),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 20.dp)
                            )
                        }
                    }
                }
            }

            // Emergency contacts quick buttons
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "📱 ${strings.emergencyContacts}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val contacts = EmergencyContacts.getForLocale().take(3)
                contacts.forEach { contact ->
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${contact.name}\n${contact.number}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  POST-DISASTER RELIEF POINTS
// ═══════════════════════════════════════════════════

@Composable
private fun PostDisasterReliefSection(reliefPoints: List<ReliefPoint>) {
    val strings = LocalStrings.current
    ExpandableSection(
        title = "🏕️ ${strings.nearbyReliefPoints} (${reliefPoints.size})",
        containerColor = SC.blue.copy(alpha = 0.15f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            reliefPoints.sortedBy { it.distanceFromUserKm }.forEach { ReliefPointCard(it) }
            Spacer(modifier = Modifier.height(4.dp))
            Text(strings.reliefDataSource, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ReliefPointCard(point: ReliefPoint) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(point.type.icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = point.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = strings.localized(point.type.label, point.type.labelId),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2196F3)
            )
            if (point.description.isNotBlank()) {
                Text(
                    text = point.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${"%.1f".format(point.distanceFromUserKm)} km",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )
            if (point.isVerified) {
                Text("✓", fontSize = 12.sp, color = Color(0xFF4CAF50))
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  EMERGENCY CONTACTS
// ═══════════════════════════════════════════════════

@Composable
private fun EmergencyContactsSection(contacts: List<EmergencyContact>) {
    val context = LocalContext.current
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📱 ${strings.emergencyContacts}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            contacts.forEach { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                            context.startActivity(intent)
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = contact.number,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  SOS SYSTEM
// ═══════════════════════════════════════════════════

@Composable
private fun SOSSection(
    sosState: SOSState,
    onActivateSOS: () -> Unit,
    onDeactivateSOS: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showAnalysis by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sosState.isActivated) Color(0xFFD32F2F).copy(alpha = 0.3f)
            else Color(0xFFF44336).copy(alpha = 0.15f)
        ),
        border = BorderStroke(
            2.dp,
            if (sosState.isActivated) Color(0xFFD32F2F) else Color(0xFFF44336).copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text("🆘", fontSize = 40.sp)
            Text(
                text = strings.sosEmergency,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF44336)
            )

            if (sosState.isActivated) {
                // ── SOS IS ACTIVE ──
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.sosActivated,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Yellow,
                    textAlign = TextAlign.Center
                )

                // Expiry countdown
                sosState.expiresAt?.let { expiry ->
                    val remaining = (expiry - System.currentTimeMillis()) / 3600000.0
                    if (remaining > 0) {
                        Text(
                            text = "${strings.sosExpiresIn}: ${"%.1f".format(remaining)} ${strings.hours}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Share location
                    Button(
                        onClick = {
                            val shareText = "🆘 ${strings.sosEmergency}\n${strings.sosActivated}\n\nCall 112 / 115 BASARNAS"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, strings.sosShare))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(strings.sosShare, style = MaterialTheme.typography.labelSmall)
                    }

                    // Call 112
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(strings.sosCall112, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Deactivate button
                OutlinedButton(
                    onClick = onDeactivateSOS,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = strings.sosDeactivate,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                // ── SOS NOT YET ACTIVATED ──
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.sosDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                // AI Analysis toggle
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAnalysis = !showAnalysis },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🤖 ${strings.sosAIAnalysis}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Icon(
                        if (showAnalysis) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }

                AnimatedVisibility(
                    visible = showAnalysis,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(10.dp)
                    ) {
                        // Behavior analysis
                        sosState.behaviorAnalysis?.let { analysis ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.sosDistressScore, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                Text(
                                    text = "${"%.0f".format(analysis.distressScore * 100)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (analysis.distressScore >= 0.7) Color(0xFFF44336) else Color(0xFF4CAF50)
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.sosConfidence, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                Text(
                                    text = "${"%.0f".format(analysis.confidenceLevel * 100)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.sosBehaviorFactors,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            analysis.factors.forEach { factor ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = factor.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${"%.0f".format(factor.score * 100)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Movement data
                        sosState.userMovementData?.let { movement ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.sosMovementData,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.sosStationaryHours, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                Text(
                                    text = "${"%.1f".format(movement.hoursStationary)} ${strings.hours}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (movement.hoursStationary >= 2) Color(0xFFF44336) else Color.White
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.sosTotalDistance, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                Text(
                                    text = "${"%.2f".format(movement.totalDistanceLast6Hours)} km",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.sosMaxSpeed, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                Text(
                                    text = "${"%.1f".format(movement.maxSpeed6Hours)} km/h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // SOS Button
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(16.dp),
                    enabled = sosState.isEligible
                ) {
                    Text(
                        text = strings.sosActivate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!sosState.isEligible) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.localized(
                            sosState.eligibilityReason.message,
                            sosState.eligibilityReason.messageId
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "🆘 ${strings.sosConfirmTitle}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(strings.sosConfirmMessage)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onActivateSOS()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(strings.sosConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(strings.sosCancel)
                }
            }
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
    val s = AppLocaleManager.strings
    val diff = System.currentTimeMillis() - epochMillis
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> s.localized("Just now", "Baru saja")
        minutes < 60 -> s.localized("${minutes}m ago", "${minutes}m lalu")
        hours < 24 -> s.localized("${hours}h ago", "${hours}j lalu")
        else -> s.localized("${days}d ago", "${days}h lalu")
    }
}

private fun formatHourFromISO(isoTime: String): String {
    return try {
        isoTime.substring(11, 16)
    } catch (e: Exception) {
        isoTime.takeLast(5)
    }
}
