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
import com.weather.forecast.data.model.*
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
            DisasterLoadingContent()
        }
        is DisasterUiState.Success -> {
            DisasterForecastContent(data = state.data)
        }
        is DisasterUiState.Error -> {
            DisasterErrorContent(
                message = state.message,
                onRetry = { viewModel.loadDisasterData() }
            )
        }
    }
}

// ===================== LOADING =====================

@Composable
private fun DisasterLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),
                        Color(0xFF283593),
                        Color(0xFF3949AB)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Menganalisis potensi bencana...",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "AI sedang memproses data cuaca, laut & sungai",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ===================== ERROR =====================

@Composable
private fun DisasterErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),
                        Color(0xFF283593),
                        Color(0xFF3949AB)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Gagal Memuat Analisis",
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
                Text("Coba Lagi")
            }
        }
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
                aiDataCompleteness = data.aiDataCompleteness
            )
        }

        // ═══ Today's Predictions ═══
        item {
            SectionTitle(text = "Analisis Hari Ini", icon = "🔍")
        }

        items(data.todayPredictions) { prediction ->
            DisasterPredictionCard(prediction = prediction)
        }

        // ═══ 7-Day Disaster Heatmap ═══
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(text = "Prakiraan 7 Hari", icon = "📅")
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛡️", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Prakiraan Bencana",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Neural Network + Rule-Based Ensemble",
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
                text = "Status: ${riskLevel.labelId}",
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
    aiDataCompleteness: Double = 0.0
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
                    Text("🧠", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Analisis Neural Network",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // AI Model badge
                if (aiModelVersion.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF00C853).copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }

            // Model info row
            if (aiModelVersion.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AiInfoChip(label = "Model", value = aiModelVersion.substringBefore("-domain"))
                    AiInfoChip(
                        label = "Data",
                        value = "${"%.0f".format(aiDataCompleteness * 100)}%"
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

@Composable
private fun AiInfoChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.8f)
        )
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
                        text = prediction.type.labelId,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = prediction.type.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Risk badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(riskColor.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = prediction.riskLevel.labelId,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
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
                Text(
                    text = "Skor: ${"%.0f".format(prediction.riskScore * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                if (prediction.aiRawScore > 0.0) {
                    Text(
                        text = "NN: ${"%.0f".format(prediction.aiRawScore * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E676).copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = "Keyakinan: ${"%.0f".format(prediction.confidence * 100)}%",
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
                            text = "Faktor Analisis:",
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
                .fillMaxWidth(score.toFloat().coerceIn(0f, 1f))
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
                    .fillMaxWidth(factor.contribution.toFloat())
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
                text = "Peta Risiko 7 Hari",
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
                            text = type.labelId,
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
                HeatmapLegendItem(color = Color(0xFF4CAF50), label = "Rendah")
                Spacer(modifier = Modifier.width(8.dp))
                HeatmapLegendItem(color = Color(0xFFFF9800), label = "Sedang")
                Spacer(modifier = Modifier.width(8.dp))
                HeatmapLegendItem(color = Color(0xFFFF5722), label = "Tinggi")
                Spacer(modifier = Modifier.width(8.dp))
                HeatmapLegendItem(color = Color(0xFFD32F2F), label = "Ekstrem")
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(riskColor.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = day.highestRisk.labelId,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pred.type.icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pred.type.labelId,
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
                                        .fillMaxWidth(pred.riskScore.toFloat())
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
                    text = "Tentang Analisis",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Analisis menggunakan Neural Network (MLP 20→32→16→6) " +
                    "dengan domain-informed initialization, dikombinasikan " +
                    "dengan rule-based scoring (ensemble fusion). " +
                    "Referensi: Gorishniy et al. (NeurIPS 2021), Guo et al. (ICML 2017). " +
                    "Data dari Open-Meteo (cuaca), Marine API (laut), " +
                    "dan GloFAS/ECMWF (sungai). " +
                    "Prakiraan bersifat indikatif — ikuti peringatan resmi BMKG.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "AI Engine: MLP-v1.0 • Open-Meteo • GloFAS • ECMWF",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
