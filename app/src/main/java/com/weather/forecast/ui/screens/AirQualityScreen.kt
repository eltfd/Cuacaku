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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.data.model.*
import com.weather.forecast.ui.viewmodel.AirQualityUiState
import com.weather.forecast.ui.viewmodel.EnvironmentViewModel

/**
 * Air Quality Screen - Pemantauan & prakiraan kualitas udara
 */
@Composable
fun AirQualityScreen(
    viewModel: EnvironmentViewModel
) {
    val uiState by viewModel.airQualityState.collectAsState()

    when (val state = uiState) {
        is AirQualityUiState.Loading -> {
            AqLoadingContent()
        }
        is AirQualityUiState.Success -> {
            AirQualityContent(data = state.data)
        }
        is AirQualityUiState.Error -> {
            AqErrorContent(
                message = state.message,
                onRetry = { viewModel.loadAirQualityData() }
            )
        }
    }
}

@Composable
private fun AqLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(LocalStrings.current.loadingAirQuality)
        }
    }
}

@Composable
private fun AqErrorContent(message: String, onRetry: () -> Unit) {
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

@Composable
private fun AirQualityContent(data: AirQualityData) {
    val aqiColor = Color(data.current.aqiLevel.colorHex)
    val gradientColors = listOf(
        aqiColor.copy(alpha = 0.8f),
        aqiColor.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.background
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors)),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // AQI Summary Card
        item {
            AqiSummaryCard(current = data.current)
        }

        // Pollutant Details
        item {
            PollutantDetailsCard(current = data.current)
        }

        // Hourly AQI Forecast (horizontal scroll)
        item {
            HourlyAqiForecastSection(hourlyData = data.hourlyForecast)
        }

        // Daily AQI Forecast (expandable)
        item {
            DailyAqiForecastSection(dailyData = data.dailyForecast)
        }

        // Health Recommendations
        item {
            HealthRecommendationCard(aqiLevel = data.current.aqiLevel)
        }
    }
}

// ===================== AQI SUMMARY CARD =====================

@Composable
private fun AqiSummaryCard(current: CurrentAirQualityData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val s = LocalStrings.current
        Text(
            text = s.airQuality,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // AQI Circle
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = current.aqiFormatted,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 48.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "US AQI",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // AQI Level label
        Text(
            text = s.localized(current.aqiLevel.label, current.aqiLevel.labelId),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = s.localized(current.aqiLevel.description, current.aqiLevel.descriptionId),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // UV Index
        if (current.uvIndex > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = s.uvIndex(current.uvIndexFormatted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

// ===================== POLLUTANT DETAILS =====================

@Composable
private fun PollutantDetailsCard(current: CurrentAirQualityData) {
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
            Text(
                text = s.pollutantDetail,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Row 1: PM2.5, PM10, O3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PollutantItem(label = "PM2.5", value = current.pm25Formatted, icon = Icons.Outlined.Grain)
                PollutantItem(label = "PM10", value = current.pm10Formatted, icon = Icons.Outlined.BlurOn)
                PollutantItem(label = "O₃", value = current.o3Formatted, icon = Icons.Outlined.Cloud)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 2: CO, NO2, SO2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PollutantItem(label = "CO", value = current.coFormatted, icon = Icons.Outlined.LocalFireDepartment)
                PollutantItem(label = "NO₂", value = current.no2Formatted, icon = Icons.Outlined.Factory)
                PollutantItem(label = "SO₂", value = current.so2Formatted, icon = Icons.Outlined.Warning)
            }

            // Dust row if significant
            if (current.dust > 0.1) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    PollutantItem(label = LocalStrings.current.dust, value = current.dustFormatted, icon = Icons.Outlined.Air)
                }
            }
        }
    }
}

@Composable
private fun PollutantItem(label: String, value: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ===================== HOURLY AQI FORECAST =====================

@Composable
private fun HourlyAqiForecastSection(hourlyData: List<HourlyAirQualityData>) {
    if (hourlyData.isEmpty()) return

    // Show only next 24 hours
    val displayData = hourlyData.take(24)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = LocalStrings.current.hourlyAQIForecast,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayData) { item ->
                HourlyAqiItem(data = item)
            }
        }
    }
}

@Composable
private fun HourlyAqiItem(data: HourlyAirQualityData) {
    val aqiColor = Color(data.aqiLevel.colorHex)

    Card(
        modifier = Modifier.width(72.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = data.hour,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // AQI value with color indicator
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(aqiColor.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.aqiFormatted,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // PM2.5
            Text(
                text = data.pm25Formatted,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )

            // Wind info (if available)
            if (data.windSpeed > 0) {
                Text(
                    text = "${data.windSpeed.toInt()} km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = LocalStrings.current.windDirectionShort(data.windDirection),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ===================== DAILY AQI FORECAST (EXPANDABLE) =====================

@Composable
private fun DailyAqiForecastSection(dailyData: List<DailyAirQualityData>) {
    if (dailyData.isEmpty()) return

    var expandedDays by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = LocalStrings.current.dailyAirQualityForecast,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        dailyData.forEach { daily ->
            val isExpanded = daily.date in expandedDays

            DailyAqiItem(
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
private fun DailyAqiItem(
    data: DailyAirQualityData,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val aqiColor = Color(data.aqiLevel.colorHex)

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
                // Day name
                Text(
                    text = data.dayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // AQI badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(aqiColor.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${data.avgAqiFormatted} AQI",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // PM2.5
                Text(
                    text = data.avgPm25Formatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Expand arrow
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

        // Expanded detail
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            DailyAqiDetailContent(data = data)
        }
    }
}

@Composable
private fun DailyAqiDetailContent(data: DailyAirQualityData) {
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
            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.aqiMin, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text("${data.minAqi}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.aqiMax, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text("${data.maxAqi}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.uvMax, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text("%.1f".format(data.maxUvIndex), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                if (data.avgWindSpeed > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.wind, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                        Text("${data.avgWindSpeed.toInt()} km/h", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }

            // Hourly forecast row
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
                        HourlyAqiCompactItem(data = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyAqiCompactItem(data: HourlyAirQualityData) {
    val aqiColor = Color(data.aqiLevel.colorHex)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Text(
            text = data.hour,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(aqiColor.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${data.aqi}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}

// ===================== HEALTH RECOMMENDATIONS =====================

@Composable
private fun HealthRecommendationCard(aqiLevel: AqiLevel) {
    val s = LocalStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.HealthAndSafety,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = s.healthRecommendations,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val recommendations = getRecommendations(aqiLevel)
            recommendations.forEach { rec ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = rec.icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rec.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

private data class Recommendation(val icon: ImageVector, val text: String)

@Composable
private fun getRecommendations(level: AqiLevel): List<Recommendation> {
    val s = LocalStrings.current
    return when (level) {
        AqiLevel.GOOD -> listOf(
            Recommendation(Icons.Outlined.DirectionsRun, s.aqiGoodRec1),
            Recommendation(Icons.Outlined.OpenInNew, s.aqiGoodRec2),
            Recommendation(Icons.Outlined.Park, s.aqiGoodRec3)
        )
        AqiLevel.MODERATE -> listOf(
            Recommendation(Icons.Outlined.DirectionsRun, s.aqiModerateRec1),
            Recommendation(Icons.Outlined.Masks, s.aqiModerateRec2),
            Recommendation(Icons.Outlined.Air, s.aqiModerateRec3)
        )
        AqiLevel.UNHEALTHY_SENSITIVE -> listOf(
            Recommendation(Icons.Outlined.Masks, s.aqiUSensRec1),
            Recommendation(Icons.Outlined.ReduceCapacity, s.aqiUSensRec2),
            Recommendation(Icons.Outlined.Home, s.aqiUSensRec3),
            Recommendation(Icons.Outlined.Air, s.aqiUSensRec4)
        )
        AqiLevel.UNHEALTHY -> listOf(
            Recommendation(Icons.Outlined.Masks, s.aqiUnhealthyRec1),
            Recommendation(Icons.Outlined.Home, s.aqiUnhealthyRec2),
            Recommendation(Icons.Outlined.Air, s.aqiUnhealthyRec3),
            Recommendation(Icons.Outlined.LocalHospital, s.aqiUnhealthyRec4)
        )
        AqiLevel.VERY_UNHEALTHY -> listOf(
            Recommendation(Icons.Outlined.Warning, s.aqiVeryUnhealthyRec1),
            Recommendation(Icons.Outlined.Home, s.aqiVeryUnhealthyRec2),
            Recommendation(Icons.Outlined.Masks, s.aqiVeryUnhealthyRec3),
            Recommendation(Icons.Outlined.LocalHospital, s.aqiVeryUnhealthyRec4)
        )
        AqiLevel.HAZARDOUS -> listOf(
            Recommendation(Icons.Outlined.Dangerous, s.aqiHazardousRec1),
            Recommendation(Icons.Outlined.Home, s.aqiHazardousRec2),
            Recommendation(Icons.Outlined.Air, s.aqiHazardousRec3),
            Recommendation(Icons.Outlined.LocalHospital, s.aqiHazardousRec4)
        )
    }
}
