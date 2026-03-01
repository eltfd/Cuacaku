package com.weather.forecast.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.data.model.*
import com.weather.forecast.ui.components.SimpleErrorContent
import com.weather.forecast.ui.components.SimpleLoadingContent
import com.weather.forecast.ui.components.toggle
import com.weather.forecast.ui.viewmodel.EnvironmentViewModel
import com.weather.forecast.ui.viewmodel.WaterQualityUiState

/**
 * Water Quality Screen - Pemantauan kualitas air (laut, sungai, danau)
 */
@Composable
fun WaterQualityScreen(
    viewModel: EnvironmentViewModel
) {
    val uiState by viewModel.waterQualityState.collectAsState()

    when (val state = uiState) {
        is WaterQualityUiState.Loading -> SimpleLoadingContent(LocalStrings.current.loadingWaterQuality)
        is WaterQualityUiState.Success -> WaterQualityContent(data = state.data)
        is WaterQualityUiState.Error -> SimpleErrorContent(
            message = state.message,
            onRetry = { viewModel.loadWaterQualityData() }
        )
    }
}



@Composable
private fun WaterQualityContent(data: WaterQualityData) {
    val gradientColors = listOf(
        Color(0xFF0288D1).copy(alpha = 0.8f),
        Color(0xFF01579B).copy(alpha = 0.6f),
        Color(0xFF004D40).copy(alpha = 0.4f),
        MaterialTheme.colorScheme.background
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors)),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header
        item {
            WaterQualityHeader()
        }

        // ── Water Level Summary Card ──────────────────────
        data.waterLevelSummary?.let { summary ->
            if (summary.hasMarineData || summary.hasFloodData) {
                item {
                    WaterLevelSummaryCard(summary = summary)
                }
            }
        }

        // ── Marine / Sea Section ──────────────────────────
        data.marine?.let { marine ->
            item {
                SectionTitle(
                    icon = Icons.Outlined.Sailing,
                    title = LocalStrings.current.seaConditions,
                    subtitle = data.nearbySeaName
                )
            }
            // Current marine conditions
            marine.current?.let { current ->
                item {
                    CurrentMarineCard(current = current)
                }
            }

            // Wave height trend forecast
            if (marine.dailyForecast.isNotEmpty()) {
                item {
                    WaveHeightTrendCard(dailyData = marine.dailyForecast)
                }
            }

            // Daily marine forecast (expandable)
            if (marine.dailyForecast.isNotEmpty()) {
                item {
                    DailyMarineForecastSection(dailyData = marine.dailyForecast)
                }
            }
        }

        // ── Flood / River Section ─────────────────────────
        data.flood?.let { flood ->
            if (flood.dailyForecast.isNotEmpty()) {
                item {
                    SectionTitle(
                        icon = Icons.Outlined.Water,
                        title = LocalStrings.current.riverWaterLevel,
                        subtitle = data.nearbyRiverName
                    )
                }

                // River level trend card
                item {
                    RiverLevelTrendCard(
                        dailyData = flood.dailyForecast,
                        overallTrend = flood.overallTrend
                    )
                }

                item {
                    RiverDischargeSection(dailyData = flood.dailyForecast)
                }
            }
        }

        // Info section if both are null
        if (data.marine == null && data.flood == null) {
            item {
                NoDataCard()
            }
        }

        // Data Source Info
        item {
            DataSourceCard()
        }
    }
}

// ===================== HEADER =====================

@Composable
private fun WaterQualityHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Water,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val s = LocalStrings.current
        Text(
            text = s.waterQuality,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Text(
            text = s.seaLakeRiver,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = "📍 $subtitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ===================== MARINE CURRENT =====================

@Composable
private fun CurrentMarineCard(current: CurrentMarineData) {
    val condColor = Color(current.seaCondition.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val s = LocalStrings.current
            // Sea condition badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.currentConditions,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(condColor.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = s.localized(current.seaCondition.label, current.seaCondition.labelId),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wave details grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MarineDetailItem(
                    icon = Icons.Outlined.Waves,
                    label = s.waves,
                    value = current.waveHeightFormatted
                )
                MarineDetailItem(
                    icon = Icons.Outlined.Explore,
                    label = s.direction,
                    value = current.waveDirectionText
                )
                MarineDetailItem(
                    icon = Icons.Outlined.Timer,
                    label = s.period,
                    value = current.wavePeriodFormatted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Swell info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MarineDetailItem(
                    icon = Icons.Outlined.Waves,
                    label = s.swell,
                    value = current.swellHeightFormatted
                )
                MarineDetailItem(
                    icon = Icons.Outlined.Explore,
                    label = s.swellDirection,
                    value = degreesToDirection(current.swellWaveDirection)
                )
                MarineDetailItem(
                    icon = Icons.Outlined.Timer,
                    label = s.swellPeriod,
                    value = "%.1f s".format(current.swellWavePeriod)
                )
            }
        }
    }
}

@Composable
private fun MarineDetailItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ===================== WATER LEVEL SUMMARY =====================

/**
 * Card ringkasan tinggi muka air — overview tren untuk laut & sungai
 */
@Composable
private fun WaterLevelSummaryCard(summary: WaterLevelSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ShowChart,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = LocalStrings.current.waterLevelForecast,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Sea wave trend ──
            if (summary.hasMarineData) {
                WaterLevelTrendRow(
                    emoji = "🌊",
                    label = LocalStrings.current.seaWaves,
                    currentValue = "%.1f m".format(summary.currentWaveHeight),
                    tomorrowValue = "%.1f m".format(summary.tomorrowWaveHeight),
                    changePercent = summary.waveChangePercent,
                    trend = summary.seaWaveTrend
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── River discharge trend ──
            if (summary.hasFloodData) {
                WaterLevelTrendRow(
                    emoji = "🏞️",
                    label = LocalStrings.current.riverDischarge,
                    currentValue = "%.1f m³/s".format(summary.currentDischarge),
                    tomorrowValue = "%.1f m³/s".format(summary.tomorrowDischarge),
                    changePercent = summary.dischargeChangePercent,
                    trend = summary.riverDischargeTrend
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Keterangan
            Text(
                text = LocalStrings.current.forecastDisclaimer,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun WaterLevelTrendRow(
    emoji: String,
    label: String,
    currentValue: String,
    tomorrowValue: String,
    changePercent: Double,
    trend: WaterLevelTrend
) {
    val s = LocalStrings.current
    val trendColor = Color(trend.colorHex)
    val changeSign = if (changePercent >= 0) "+" else ""
    val changeText = "${changeSign}${"%.1f".format(changePercent)}%"

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            // Trend badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(trendColor.copy(alpha = 0.3f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${trend.icon} ${s.localized(trend.label, trend.labelId)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(s.today, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                Text(currentValue, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.tomorrow, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                Text(tomorrowValue, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(s.change, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                Text(
                    text = changeText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = trendColor
                )
            }
        }
    }
}

// ===================== WAVE HEIGHT TREND =====================

/**
 * Card visual tren tinggi gelombang 7 hari — bar chart sederhana + tren arrow
 */
@Composable
private fun WaveHeightTrendCard(dailyData: List<DailyMarineData>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = LocalStrings.current.waveTrend7Days,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Simple bar chart representation
            val maxWave = dailyData.maxOfOrNull { it.waveHeightMax } ?: 1.0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                dailyData.take(7).forEach { day ->
                    val fraction = if (maxWave > 0.0) (day.waveHeightMax / maxWave).toFloat().coerceIn(0.05f, 1f) else 0.05f
                    val barColor = Color(day.seaCondition.colorHex)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Trend arrow
                        Text(
                            text = day.waveTrend.icon,
                            fontSize = 10.sp
                        )

                        // Value
                        Text(
                            text = "%.1f".format(day.waveHeightMax),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Bar
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor.copy(alpha = 0.7f))
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Day label
                        Text(
                            text = day.dayName.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

// ===================== RIVER LEVEL TREND =====================

/**
 * Card tren tinggi muka air sungai — overview + bar chart
 */
@Composable
private fun RiverLevelTrendCard(
    dailyData: List<DailyFloodData>,
    overallTrend: WaterLevelTrend
) {
    val trendColor = Color(overallTrend.colorHex)

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LocalStrings.current.riverWaterTrend,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                // Overall trend badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(trendColor.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${overallTrend.icon} ${LocalStrings.current.localized(overallTrend.label, overallTrend.labelId)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bar chart for discharge
            val maxDischarge = dailyData.maxOfOrNull { it.riverDischarge } ?: 1.0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                dailyData.take(7).forEach { day ->
                    val fraction = if (maxDischarge > 0.0) (day.riverDischarge / maxDischarge).toFloat().coerceIn(0.05f, 1f) else 0.05f
                    val riskColor = Color(day.floodRisk.colorHex)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Trend arrow
                        Text(
                            text = day.waterLevelTrend.icon,
                            fontSize = 10.sp
                        )

                        // Value
                        Text(
                            text = "%.0f".format(day.riverDischarge),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Bar
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(riskColor.copy(alpha = 0.7f))
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Day label
                        Text(
                            text = day.dayName.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = LocalStrings.current.riverDischargeDisclaimer,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

// ===================== DAILY MARINE FORECAST =====================

@Composable
private fun DailyMarineForecastSection(dailyData: List<DailyMarineData>) {
    var expandedDays by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = LocalStrings.current.seaForecast7Days,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        dailyData.forEach { daily ->
            DailyMarineItem(
                data = daily,
                isExpanded = daily.date in expandedDays,
                onToggle = { expandedDays = expandedDays.toggle(daily.date) }
            )
        }
    }
}

@Composable
private fun DailyMarineItem(
    data: DailyMarineData,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val condColor = Color(data.seaCondition.colorHex)

    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable { onToggle() },
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.dayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Sea condition badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(condColor.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = LocalStrings.current.localized(data.seaCondition.label, data.seaCondition.labelId),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Trend arrow
                Text(
                    text = data.waveTrend.icon,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = data.waveHeightMaxFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                val s = LocalStrings.current
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) s.close else s.open,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (isExpanded) 180f else 0f)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            DailyMarineDetailContent(data = data)
        }
    }
}

@Composable
private fun DailyMarineDetailContent(data: DailyMarineData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val s = LocalStrings.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.waveMax, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text(data.waveHeightMaxFormatted, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.direction, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text(data.waveDirectionText, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.swellMax, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text("%.1f m".format(data.swellWaveHeightMax), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }

            // Hourly forecast
            if (data.hourlyForecasts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = s.hourly,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(data.hourlyForecasts) { item ->
                        HourlyMarineCompactItem(data = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyMarineCompactItem(data: HourlyMarineData) {
    val condColor = Color(data.seaCondition.colorHex)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp)
    ) {
        Text(
            text = data.hour,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(condColor.copy(alpha = 0.4f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = data.waveHeightFormatted,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

// ===================== RIVER DISCHARGE =====================

@Composable
private fun RiverDischargeSection(dailyData: List<DailyFloodData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        dailyData.forEach { daily ->
            RiverDischargeItem(data = daily)
        }
    }
}

@Composable
private fun RiverDischargeItem(data: DailyFloodData) {
    val riskColor = Color(data.floodRisk.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day name
            Text(
                text = data.dayName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            // Trend arrow
            Text(
                text = data.waterLevelTrend.icon,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Discharge value
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = data.dischargeFormatted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = LocalStrings.current.average(data.dischargeMeanFormatted),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    if (data.dischargeChangePercent != 0.0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val sign = if (data.dischargeChangePercent > 0) "+" else ""
                        Text(
                            text = "${sign}${"%.0f".format(data.dischargeChangePercent)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(data.waterLevelTrend.colorHex)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Risk badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(riskColor.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = LocalStrings.current.localized(data.floodRisk.label, data.floodRisk.labelId),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

// ===================== INFO CARDS =====================

@Composable
private fun NoDataCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val s = LocalStrings.current
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = s.noDataForLocation,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = s.dataOnlyNearCoast,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DataSourceCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val s = LocalStrings.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = s.dataSource,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = s.waterDataSourceDetail,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
