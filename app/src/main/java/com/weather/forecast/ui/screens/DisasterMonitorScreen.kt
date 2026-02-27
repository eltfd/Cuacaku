package com.weather.forecast.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.weather.forecast.ui.viewmodel.DisasterMonitorUiState
import com.weather.forecast.ui.viewmodel.EnvironmentViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Disaster Monitor Screen — Pemantauan Bencana Aktif
 *
 * Menampilkan bencana yang sedang/sudah terjadi secara interaktif:
 * - Status real-time (ACTIVE → RECOVERY → RESOLVED)
 * - Progress bar pemulihan
 * - Timeline event
 * - Auto-hide setelah kondisi normal
 * - Countdown hari sampai hilang dari tampilan
 */
@Composable
fun DisasterMonitorScreen(
    viewModel: EnvironmentViewModel
) {
    val uiState by viewModel.disasterMonitorState.collectAsState()

    when (val state = uiState) {
        is DisasterMonitorUiState.Loading -> MonitorLoadingContent()
        is DisasterMonitorUiState.Success -> MonitorContent(
            data = state.data,
            onRefresh = { viewModel.loadDisasterMonitorData() }
        )
        is DisasterMonitorUiState.Empty -> MonitorEmptyContent(
            onRefresh = { viewModel.loadDisasterMonitorData() }
        )
        is DisasterMonitorUiState.Error -> MonitorErrorContent(
            message = state.message,
            onRetry = { viewModel.loadDisasterMonitorData() }
        )
    }
}

// ═══════════════════════════════════════════════════
//  LOADING
// ═══════════════════════════════════════════════════

@Composable
private fun MonitorLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val s = LocalStrings.current
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                s.monitoringDisasters,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                s.fetchingData,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  EMPTY STATE
// ═══════════════════════════════════════════════════

@Composable
private fun MonitorEmptyContent(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            val s = LocalStrings.current
            Text("✅", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                s.allClear,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                s.noActiveDisasters,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onRefresh,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(s.refresh)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                s.sourceReliefWeb,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  ERROR
// ═══════════════════════════════════════════════════

@Composable
private fun MonitorErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            val s = LocalStrings.current
            Text("⚠️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                s.failedToLoadData,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(s.retry) }
        }
    }
}

// ═══════════════════════════════════════════════════
//  MAIN CONTENT
// ═══════════════════════════════════════════════════

@Composable
private fun MonitorContent(
    data: ActiveDisasterMonitor,
    onRefresh: () -> Unit
) {
    val hasActive = data.activeCount > 0
    val gradient = if (hasActive) {
        listOf(Color(0xFF4A0000), Color(0xFF8B0000), Color(0xFF2C1810))
    } else {
        listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF1A1A2E))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(gradient)),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ═══ Header ═══
        item {
            MonitorHeader(data = data, onRefresh = onRefresh)
        }

        // ═══ Summary Bar ═══
        item {
            PhasesSummaryBar(data = data)
        }

        // ═══ Filter chips ═══
        // (we'll show all disasters grouped by phase)

        // ═══ ACTIVE Disasters ═══
        val activeDisasters = data.disasters.filter { it.phase == DisasterPhase.ACTIVE }
        if (activeDisasters.isNotEmpty()) {
            item {
                PhaseSectionHeader(
                    phase = DisasterPhase.ACTIVE,
                    count = activeDisasters.size
                )
            }
            items(activeDisasters, key = { it.id }) { disaster ->
                DisasterMonitorCard(disaster = disaster)
            }
        }

        // ═══ RECOVERY Disasters ═══
        val recoveryDisasters = data.disasters.filter { it.phase == DisasterPhase.RECOVERY }
        if (recoveryDisasters.isNotEmpty()) {
            item {
                PhaseSectionHeader(
                    phase = DisasterPhase.RECOVERY,
                    count = recoveryDisasters.size
                )
            }
            items(recoveryDisasters, key = { it.id }) { disaster ->
                DisasterMonitorCard(disaster = disaster)
            }
        }

        // ═══ RESOLVED Disasters ═══
        val resolvedDisasters = data.disasters.filter { it.phase == DisasterPhase.RESOLVED }
        if (resolvedDisasters.isNotEmpty()) {
            item {
                PhaseSectionHeader(
                    phase = DisasterPhase.RESOLVED,
                    count = resolvedDisasters.size
                )
            }
            items(resolvedDisasters, key = { it.id }) { disaster ->
                DisasterMonitorCard(disaster = disaster)
            }
        }

        // ═══ Footer ═══
        item {
            val s = LocalStrings.current
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                s.monitorFooter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════
//  HEADER
// ═══════════════════════════════════════════════════

@Composable
private fun MonitorHeader(data: ActiveDisasterMonitor, onRefresh: () -> Unit) {
    val hasActive = data.activeCount > 0

    // Pulsing animation for active indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val s = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasActive) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = s.monitorTitle(hasActive),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = s.realTimeData,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = s.refresh,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Last updated
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        Text(
            text = s.lastUpdated(dateFormat.format(Date(data.lastUpdated))),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ═══════════════════════════════════════════════════
//  PHASES SUMMARY BAR
// ═══════════════════════════════════════════════════

@Composable
private fun PhasesSummaryBar(data: ActiveDisasterMonitor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val s = LocalStrings.current
        PhaseChip(
            label = s.active,
            count = data.activeCount,
            color = Color(DisasterPhase.ACTIVE.colorHex),
            modifier = Modifier.weight(1f)
        )
        PhaseChip(
            label = s.recovery,
            count = data.recoveryCount,
            color = Color(DisasterPhase.RECOVERY.colorHex),
            modifier = Modifier.weight(1f)
        )
        PhaseChip(
            label = s.resolved,
            count = data.resolvedCount,
            color = Color(DisasterPhase.RESOLVED.colorHex),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PhaseChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  SECTION HEADERS
// ═══════════════════════════════════════════════════

@Composable
private fun PhaseSectionHeader(phase: DisasterPhase, count: Int) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(phase.icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = s.localized(phase.label, phase.labelId),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(phase.colorHex)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(phase.colorHex).copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.weight(1f))

        // Status line
        Box(
            modifier = Modifier
                .height(2.dp)
                .weight(2f)
                .background(
                    Color(phase.colorHex).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(1.dp)
                )
        )
    }
}

// ═══════════════════════════════════════════════════
//  DISASTER CARD (Expandable)
// ═══════════════════════════════════════════════════

@Composable
private fun DisasterMonitorCard(disaster: ActiveDisaster) {
    var expanded by remember { mutableStateOf(false) }
    val phaseColor = Color(disaster.phase.colorHex)
    val typeColor = Color(disaster.type.colorHex)
    val s = LocalStrings.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { expanded = !expanded }
            .animateContentSize(animationSpec = spring(dampingRatio = 0.8f)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = phaseColor.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Top Row: Icon + Title + Phase Badge ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Disaster type icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(typeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(disaster.type.icon, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = disaster.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Location
                    if (disaster.locations.isNotEmpty()) {
                        val locStr = disaster.locations.joinToString(", ") {
                            if (it.province.isNotBlank()) "${it.name} (${it.province})" else it.name
                        }
                        Text(
                            text = "📍 $locStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Phase badge
                Surface(
                    color = phaseColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = s.localized(disaster.phase.label, disaster.phase.labelId),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = phaseColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Recovery Progress Bar ──
            if (disaster.phase != DisasterPhase.ACTIVE || disaster.recoveryProgress > 0f) {
                RecoveryProgressSection(disaster)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Time Info Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Time since event
                val daysSince = TimeUnit.MILLISECONDS.toDays(
                    System.currentTimeMillis() - disaster.startDate
                ).toInt()
                Text(
                    text = "⏱️ ${formatDuration(daysSince, s)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )

                // Severity badge
                SeverityBadge(disaster.severity)

                // Days until hidden (only for resolved)
                if (disaster.phase == DisasterPhase.RESOLVED && disaster.daysUntilHidden > 0) {
                    Text(
                        text = s.hiddenInDays(disaster.daysUntilHidden),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(DisasterPhase.RESOLVED.colorHex).copy(alpha = 0.6f)
                    )
                }
            }

            // ── Expand indicator ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // ═══ EXPANDED CONTENT ═══
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Divider(
                        color = Color.White.copy(alpha = 0.1f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Current Situation ──
                    Text(
                        text = s.currentSituation,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = disaster.currentSituation,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    // ── Impact ──
                    disaster.impact?.let { impact ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = s.impact,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        impact.affectedPeople?.let {
                            Text("👥 ${s.peopleAffected(it)}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        impact.affectedAreaKm2?.let {
                            Text("📏 ${s.areaAffected("%.1f".format(it))}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        impact.infrastructureDamage?.let {
                            Text("🏗️ $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        impact.aidStatus?.let {
                            Text("🆘 $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                    }

                    // ── Timeline ──
                    if (disaster.timeline.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = s.timeline,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DisasterTimeline(events = disaster.timeline)
                    }

                    // ── Source ──
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = s.sourceLabel(disaster.source),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  RECOVERY PROGRESS
// ═══════════════════════════════════════════════════

@Composable
private fun RecoveryProgressSection(disaster: ActiveDisaster) {
    val phaseColor = Color(disaster.phase.colorHex)
    val progress = disaster.recoveryProgress.coerceIn(0f, 1f)
    val s = LocalStrings.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (disaster.phase) {
                    DisasterPhase.ACTIVE -> s.disasterOngoing
                    DisasterPhase.RECOVERY -> s.recoveryPercent("%.0f".format(progress * 100))
                    DisasterPhase.RESOLVED -> s.fullyRecovered
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = phaseColor
            )

            if (disaster.phase == DisasterPhase.RESOLVED && disaster.daysUntilHidden > 0) {
                Text(
                    text = "⏳ ${disaster.daysUntilHidden}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = phaseColor.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Animated progress bar
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
            label = "progress"
        )

        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = phaseColor,
            trackColor = phaseColor.copy(alpha = 0.15f)
        )
    }
}

// ═══════════════════════════════════════════════════
//  SEVERITY BADGE
// ═══════════════════════════════════════════════════

@Composable
private fun SeverityBadge(severity: DisasterSeverity) {
    val s = LocalStrings.current
    val color = when (severity) {
        DisasterSeverity.MINOR -> Color(0xFF4CAF50)
        DisasterSeverity.MODERATE -> Color(0xFFFF9800)
        DisasterSeverity.SEVERE -> Color(0xFFFF5722)
        DisasterSeverity.CRITICAL -> Color(0xFFD32F2F)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = s.localized(severity.label, severity.labelId),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════
//  TIMELINE
// ═══════════════════════════════════════════════════

@Composable
private fun DisasterTimeline(events: List<DisasterTimelineEvent>) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    Column {
        events.forEachIndexed { index, event ->
            val isLast = index == events.lastIndex
            val phaseColor = Color(event.phase.colorHex)

            Row(modifier = Modifier.fillMaxWidth()) {
                // Timeline visual (dot + line)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(phaseColor)
                    )
                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(32.dp)
                                .background(phaseColor.copy(alpha = 0.3f))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(event.icon, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = dateFormat.format(Date(event.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    if (!isLast) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  UTILS
// ═══════════════════════════════════════════════════

private fun formatDuration(days: Int, s: com.weather.forecast.data.locale.AppStrings): String {
    return when {
        days == 0 -> s.durationToday
        days == 1 -> s.duration1Day
        days < 7 -> s.durationDays(days)
        days < 30 -> s.durationWeeks(days / 7)
        days < 365 -> s.durationMonths(days / 30)
        else -> s.durationYears(days / 365)
    }
}
