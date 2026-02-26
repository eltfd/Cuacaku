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
import androidx.compose.ui.unit.dp
import com.weather.forecast.data.model.*
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
        is WaterQualityUiState.Loading -> {
            WqLoadingContent()
        }
        is WaterQualityUiState.Success -> {
            WaterQualityContent(data = state.data)
        }
        is WaterQualityUiState.Error -> {
            WqErrorContent(
                message = state.message,
                onRetry = { viewModel.loadWaterQualityData() }
            )
        }
    }
}

@Composable
private fun WqLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Memuat data kualitas air...")
        }
    }
}

@Composable
private fun WqErrorContent(message: String, onRetry: () -> Unit) {
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
                Text("Coba Lagi")
            }
        }
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

        // Marine / Sea Section
        data.marine?.let { marine ->
            item {
                SectionTitle(
                    icon = Icons.Outlined.Sailing,
                    title = "Kondisi Laut"
                )
            }

            // Current marine conditions
            marine.current?.let { current ->
                item {
                    CurrentMarineCard(current = current)
                }
            }

            // Daily marine forecast (expandable)
            if (marine.dailyForecast.isNotEmpty()) {
                item {
                    DailyMarineForecastSection(dailyData = marine.dailyForecast)
                }
            }
        }

        // Flood / River Section
        data.flood?.let { flood ->
            if (flood.dailyForecast.isNotEmpty()) {
                item {
                    SectionTitle(
                        icon = Icons.Outlined.Water,
                        title = "Kondisi Sungai"
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

        Text(
            text = "Kualitas Air",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Text(
            text = "Laut, Sungai & Danau",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
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
            // Sea condition badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kondisi Saat Ini",
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
                        text = current.seaCondition.labelId,
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
                    label = "Gelombang",
                    value = current.waveHeightFormatted
                )
                MarineDetailItem(
                    icon = Icons.Outlined.Explore,
                    label = "Arah",
                    value = current.waveDirectionText
                )
                MarineDetailItem(
                    icon = Icons.Outlined.Timer,
                    label = "Periode",
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
                    label = "Swell",
                    value = current.swellHeightFormatted
                )
                MarineDetailItem(
                    icon = Icons.Outlined.Explore,
                    label = "Arah Swell",
                    value = degreesToDirection(current.swellWaveDirection)
                )
                MarineDetailItem(
                    icon = Icons.Outlined.Timer,
                    label = "Periode Swell",
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
            text = "Prakiraan Laut 7 Hari",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        dailyData.forEach { daily ->
            val isExpanded = daily.date in expandedDays

            DailyMarineItem(
                data = daily,
                isExpanded = isExpanded,
                onToggle = {
                    expandedDays = if (isExpanded) {
                        expandedDays - daily.date
                    } else {
                        expandedDays + daily.date
                    }
                }
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
                        text = data.seaCondition.labelId,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = data.waveHeightMaxFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Tutup" else "Buka",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gelombang Max", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text(data.waveHeightMaxFormatted, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Arah", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text(data.waveDirectionText, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Swell Max", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text("%.1f m".format(data.swellWaveHeightMax), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }

            // Hourly forecast
            if (data.hourlyForecasts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Per Jam",
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

            // Discharge value
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = data.dischargeFormatted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Rata-rata: ${data.dischargeMeanFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
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
                    text = data.floodRisk.labelId,
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
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Data tidak tersedia untuk lokasi ini",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Data laut hanya tersedia untuk lokasi dekat pantai. Data sungai mungkin tidak tersedia di semua daerah.",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sumber Data",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Laut: Open-Meteo Marine API\nSungai: GloFAS (Global Flood Awareness System, ECMWF)\nData diperbarui setiap jam",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
