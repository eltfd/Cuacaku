package com.weather.forecast.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.data.model.*
import com.weather.forecast.ui.components.GradientErrorContent
import com.weather.forecast.ui.components.GradientLoadingContent
import com.weather.forecast.ui.components.StatusBadge
import com.weather.forecast.ui.viewmodel.DisasterUiState
import com.weather.forecast.ui.viewmodel.EnvironmentViewModel

/**
 * Disaster Forecast Screen — Prakiraan Potensi Bencana Berbasis AI
 *
 * Menampilkan analisis risiko bencana berdasarkan data cuaca, laut, dan sungai.
 * Menggunakan Neural Network (MLP) + rule-based ensemble untuk prediksi.
 */
@Composable
fun DisasterForecastScreen(
    viewModel: EnvironmentViewModel
) {
    val uiState by viewModel.disasterState.collectAsState()

    when (val state = uiState) {
        is DisasterUiState.Loading -> {
            val s = LocalStrings.current
            GradientLoadingContent(mainText = s.analyzingDisasters, subText = s.aiProcessing)
        }
        is DisasterUiState.Success -> DisasterForecastContent(data = state.data)
        is DisasterUiState.Error -> GradientErrorContent(
            message = state.message,
            title = LocalStrings.current.failedToLoadAnalysis,
            onRetry = { viewModel.loadDisasterData() }
        )
    }
}

// ===================== MAIN CONTENT =====================

@Composable
private fun DisasterForecastContent(data: DisasterForecast) {
    val gradient = when (data.overallRiskLevel) {
        RiskLevel.EXTREME -> listOf(Color(0xFF4A0000), Color(0xFF8B0000), Color(0xFFB71C1C))
        RiskLevel.HIGH -> listOf(Color(0xFF4A1500), Color(0xFFBF360C), Color(0xFFE65100))
        RiskLevel.MODERATE -> listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB))
        RiskLevel.LOW -> listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(gradient)),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ═══ Header ═══
        item {
            DisasterHeader(data.overallRiskLevel)
        }

        // ═══ AI Summary Card ═══
        item {
            AiSummaryCard(
                summary = data.aiSummary,
                riskLevel = data.overallRiskLevel,
                aiModelVersion = data.aiModelVersion,
                aiDataCompleteness = data.aiDataCompleteness,
                learningSteps = data.learningSteps,
                learningSamples = data.learningSamples,
                storageUsed = data.storageUsed
            )
        }

        // ═══ Today's Predictions ═══
        item {
            SectionTitle(text = LocalStrings.current.todayAnalysis, icon = "🔍")
        }

        items(data.todayPredictions) { prediction ->
            DisasterPredictionCard(prediction = prediction)
        }

        // ═══ Terrain Analysis Card (if terrain data available) ═══
        if (data.terrainData != null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TerrainAnalysisCard(terrainData = data.terrainData)
            }
        }

        // ═══ 7-Day Disaster Heatmap ═══
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(text = LocalStrings.current.sevenDayForecast, icon = "📅")
        }

        item {
            WeeklyDisasterHeatmap(weekly = data.weeklyPredictions)
        }

        // ═══ Per-day detail ═══
        items(data.weeklyPredictions) { dayData ->
            DailyDisasterCard(day = dayData)
        }

        // ═══ Footer ═══
        item {
            DisasterFooter()
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ===================== HEADER =====================

@Composable
private fun DisasterHeader(riskLevel: RiskLevel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val s = LocalStrings.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛡️", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = s.disasterForecast,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = s.nnSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Overall risk badge
        val riskColor = Color(riskLevel.colorHex)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(riskColor.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(riskColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = s.statusLabel(s.localized(riskLevel.label, riskLevel.labelId)),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

// ===================== AI SUMMARY =====================

@Suppress("UNUSED_PARAMETER")
@Composable
private fun AiSummaryCard(
    summary: String,
    riskLevel: RiskLevel,
    aiModelVersion: String = "",
    aiDataCompleteness: Double = 0.0,
    learningSteps: Long = 0,
    learningSamples: Int = 0,
    storageUsed: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF00E676)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = LocalStrings.current.neuralNetworkAnalysis,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 22.sp
            )
        }
    }
}

// ===================== SECTION TITLE =====================

@Composable
private fun SectionTitle(text: String, icon: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

// ===================== PREDICTION CARD =====================

@Composable
private fun DisasterPredictionCard(prediction: DisasterPrediction) {
    var expanded by remember { mutableStateOf(prediction.riskLevel >= RiskLevel.MODERATE) }
    val riskColor = Color(prediction.riskLevel.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { expanded = !expanded }
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (prediction.riskLevel >= RiskLevel.HIGH)
                riskColor.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(prediction.type.icon, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = LocalStrings.current.localized(prediction.type.label, prediction.type.labelId),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = LocalStrings.current.localized(prediction.type.descriptionEn, prediction.type.description),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Risk badge
                StatusBadge(
                    text = LocalStrings.current.localized(prediction.riskLevel.label, prediction.riskLevel.labelId),
                    color = riskColor,
                    alpha = 0.4f,
                    horizontalPadding = 10.dp
                )
            }

            // Risk score bar
            Spacer(modifier = Modifier.height(8.dp))
            RiskScoreBar(score = prediction.riskScore, color = riskColor)

            // Confidence + AI score
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val s = LocalStrings.current
                Text(
                    text = s.score("%.0f".format(prediction.riskScore * 100)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                if (prediction.aiRawScore > 0.0) {
                    Text(
                        text = s.nnScore("%.0f".format(prediction.aiRawScore * 100)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E676).copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = s.confidence("%.0f".format(prediction.confidence * 100)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Expanded: description + factors + recommendation
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Description
                    Text(
                        text = prediction.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )

                    // Contributing factors
                    if (prediction.factors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = LocalStrings.current.analysisFactors,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        prediction.factors.forEach { factor ->
                            FactorRow(factor = factor)
                        }
                    }

                    // Recommendation
                    if (prediction.recommendation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("💡", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = prediction.recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== RISK SCORE BAR =====================

@Composable
private fun RiskScoreBar(score: Double, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(score.toFloat().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) })
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.8f))
        )
    }
}

// ===================== FACTOR ROW =====================

@Composable
private fun FactorRow(factor: ContributingFactor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (factor.isElevating) Color(0xFFF44336).copy(alpha = 0.8f)
                    else Color(0xFF4CAF50).copy(alpha = 0.8f)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = factor.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = factor.value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.8f)
        )

        // Mini contribution bar
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(factor.contribution.toFloat().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (factor.isElevating) Color(0xFFF44336).copy(alpha = 0.7f)
                        else Color(0xFF4CAF50).copy(alpha = 0.7f)
                    )
            )
        }
    }
}

// ===================== WEEKLY HEATMAP =====================

@Composable
private fun WeeklyDisasterHeatmap(weekly: List<DailyDisasterSummary>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = LocalStrings.current.riskMap7Days,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Heatmap grid: rows = disaster types, columns = days
            val disasterTypes = DisasterType.entries

            // Day headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Empty space for row label
                Spacer(modifier = Modifier.width(72.dp))

                weekly.take(7).forEach { day ->
                    Text(
                        text = day.dayName.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Rows per disaster type
            disasterTypes.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type label
                    Row(
                        modifier = Modifier.width(72.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(type.icon, fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = LocalStrings.current.localized(type.label, type.labelId),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }

                    // Heatmap cells
                    weekly.take(7).forEach { day ->
                        val prediction = day.predictions.find { it.type == type }
                        val cellColor = when (prediction?.riskLevel) {
                            RiskLevel.EXTREME -> Color(0xFFD32F2F)
                            RiskLevel.HIGH -> Color(0xFFFF5722)
                            RiskLevel.MODERATE -> Color(0xFFFF9800)
                            RiskLevel.LOW -> Color(0xFF4CAF50)
                            null -> Color.White.copy(alpha = 0.1f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp)
                                .padding(horizontal = 1.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(cellColor.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val scoreText = prediction?.let { "${"%.0f".format(it.riskScore * 100)}" } ?: ""
                            Text(
                                text = scoreText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 7.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val s = LocalStrings.current
                HeatmapLegendItem(color = Color(0xFF4CAF50), label = s.low)
                Spacer(modifier = Modifier.width(8.dp))
                HeatmapLegendItem(color = Color(0xFFFF9800), label = s.moderate)
                Spacer(modifier = Modifier.width(8.dp))
                HeatmapLegendItem(color = Color(0xFFFF5722), label = s.high)
                Spacer(modifier = Modifier.width(8.dp))
                HeatmapLegendItem(color = Color(0xFFD32F2F), label = s.extreme)
            }
        }
    }
}

@Composable
private fun HeatmapLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.7f))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp
        )
    }
}

// ===================== DAILY DISASTER CARD =====================

@Composable
private fun DailyDisasterCard(day: DailyDisasterSummary) {
    var expanded by remember { mutableStateOf(false) }
    val hasRisk = day.highestRisk >= RiskLevel.MODERATE
    val riskColor = Color(day.highestRisk.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (hasRisk) riskColor.copy(alpha = 0.15f)
            else Color.White.copy(alpha = 0.10f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = day.dayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Show top risk icons
                day.predictions
                    .filter { it.riskLevel >= RiskLevel.MODERATE }
                    .take(3)
                    .forEach { pred ->
                        Text(pred.type.icon, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                Spacer(modifier = Modifier.width(8.dp))

                // Risk badge
                StatusBadge(
                    text = LocalStrings.current.localized(day.highestRisk.label, day.highestRisk.labelId),
                    color = riskColor,
                    alpha = 0.4f,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    day.predictions.forEach { pred ->
                        val predColor = Color(pred.riskLevel.colorHex)
                        val s = LocalStrings.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pred.type.icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.localized(pred.type.label, pred.type.labelId),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = pred.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1,
                                modifier = Modifier.weight(1.5f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Mini risk bar
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(pred.riskScore.toFloat().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) })
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(predColor.copy(alpha = 0.8f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== TERRAIN ANALYSIS =====================

@Composable
private fun TerrainAnalysisCard(terrainData: LandslideTerrainData) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF795548).copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⛰️", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.terrainAnalysis,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = s.terrainDataSourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Slope
                TerrainMetricItem(
                    icon = "📐",
                    label = s.slopeGradient,
                    value = "%.1f°".format(terrainData.slopeAngle),
                    subLabel = terrainData.slopeCategory.name,
                    color = Color(
                        when {
                            terrainData.slopeAngle > 30 -> 0xFFD32F2F
                            terrainData.slopeAngle > 15 -> 0xFFFF9800
                            terrainData.slopeAngle > 5 -> 0xFFFFC107
                            else -> 0xFF4CAF50
                        }
                    )
                )

                // Soil Saturation
                val satPct = "%.0f%%".format(terrainData.soilSaturationIndex * 100)
                TerrainMetricItem(
                    icon = "💧",
                    label = s.soilSaturation,
                    value = satPct,
                    subLabel = when {
                        terrainData.soilSaturationIndex > 0.8 -> s.soilSaturated
                        terrainData.soilSaturationIndex > 0.5 -> s.soilWet
                        else -> s.soilNormal
                    },
                    color = Color(
                        when {
                            terrainData.soilSaturationIndex > 0.8 -> 0xFFD32F2F
                            terrainData.soilSaturationIndex > 0.5 -> 0xFFFF9800
                            else -> 0xFF4CAF50
                        }
                    )
                )

                // Elevation
                TerrainMetricItem(
                    icon = "🏔️",
                    label = s.elevation,
                    value = "%.0f m".format(terrainData.elevation),
                    subLabel = s.aboveSeaLevel,
                    color = Color.White
                )
            }

            // Expanded detail
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Soil moisture breakdown
                    Text(
                        text = "🌱 ${s.soilMoisture}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    SoilMoistureBar(
                        label = s.soilMoistureShallow,
                        value = terrainData.soilMoistureShallow,
                        maxValue = 0.5
                    )
                    SoilMoistureBar(
                        label = s.soilMoistureMedium,
                        value = terrainData.soilMoistureMedium,
                        maxValue = 0.5
                    )
                    SoilMoistureBar(
                        label = s.soilMoistureDeep,
                        value = terrainData.soilMoistureDeep,
                        maxValue = 0.5
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Vegetation proxy + soil temp
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🌿 ${s.vegetationCover}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "%.2f".format(terrainData.vegetationIndex),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(if (terrainData.vegetationIndex > 0.5) 0xFF4CAF50 else 0xFFFF9800)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🌡️ ${s.soilTemperature}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "%.1f°C".format(terrainData.soilTemperature),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Rainfall metrics
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.rainfallTodayShort, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            Text("%.1f mm".format(terrainData.todayPrecipitation), style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.rain3DayShort, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            Text("%.1f mm".format(terrainData.antecedentRainfall), style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.maxPerHourShort, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            Text("%.1f mm".format(terrainData.maxRainfallIntensity), style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }

                    // Elevation grid
                    if (terrainData.elevationGrid.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = s.elevationGridLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        terrainData.elevationGrid.forEach { point ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Text(
                                    text = "${point.label}: ${point.elevation.toInt()} m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerrainMetricItem(
    icon: String,
    label: String,
    value: String,
    subLabel: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = subLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp
        )
    }
}

@Composable
private fun SoilMoistureBar(
    label: String,
    value: Double,
    maxValue: Double
) {
    val fraction = if (maxValue > 0.0) (value / maxValue).toFloat().coerceIn(0f, 1f) else 0f
    val color = when {
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
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(110.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "%.3f".format(value),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(45.dp),
            textAlign = TextAlign.End
        )
    }
}

// ===================== FOOTER =====================

@Composable
private fun DisasterFooter() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ℹ️", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = LocalStrings.current.aboutAnalysis,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = LocalStrings.current.aboutAnalysisDescV2,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = LocalStrings.current.aiEngineLineV2,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
