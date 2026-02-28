package com.weather.forecast.ui.screens

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.data.model.*
import com.weather.forecast.ui.components.*
import com.weather.forecast.ui.theme.*
import com.weather.forecast.ui.viewmodel.WeatherUiState
import com.weather.forecast.ui.viewmodel.WeatherViewModel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Home Screen - Main weather display
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WeatherViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    @Suppress("UNUSED_VARIABLE")
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Location permissions
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.loadWeatherData()
        }
    }

    // Request permissions on first launch
    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            WeatherTopBar(
                uiState = uiState,
                showSearchBar = showSearchBar,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    viewModel.searchLocation(query)
                },
                onSearchBarToggle = { 
                    showSearchBar = it
                    if (!it) {
                        searchQuery = ""
                        viewModel.clearSearch()
                    }
                },
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is WeatherUiState.Loading -> {
                    LoadingContent()
                }
                
                is WeatherUiState.Success -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WeatherContent(
                            weatherData = state.data,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                is WeatherUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        isLocationError = state.isLocationError,
                        onRetry = { viewModel.loadWeatherData() },
                        onRequestPermission = {
                            locationPermissions.launchMultiplePermissionRequest()
                        }
                    )
                }
            }

            // Search Results Overlay
            if (showSearchBar && searchResults.isNotEmpty()) {
                SearchResultsList(
                    results = searchResults,
                    isLoading = isSearching,
                    onResultClick = { result ->
                        viewModel.loadWeatherForLocation(result)
                        showSearchBar = false
                        searchQuery = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherTopBar(
    uiState: WeatherUiState,
    showSearchBar: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchBarToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val s = LocalStrings.current
    val locationName = when (uiState) {
        is WeatherUiState.Success -> uiState.data.location.name
        else -> s.homeTitle
    }

    if (showSearchBar) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onSearch = {},
            active = true,
            onActiveChange = onSearchBarToggle,
            placeholder = { Text(s.searchPlaceholder) },
            leadingIcon = {
                IconButton(onClick = { onSearchBarToggle(false) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = s.back)
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = s.clear)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {}
    } else {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(locationName)
                }
            },
            actions = {
                IconButton(onClick = { onSearchBarToggle(true) }) {
                    Icon(Icons.Default.Search, contentDescription = s.search)
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = s.settings)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            val s = LocalStrings.current
            Text(s.loadingWeather)
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    isLocationError: Boolean,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (isLocationError) Icons.Default.LocationOff else Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            if (isLocationError) {
                Button(onClick = onRequestPermission) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val s = LocalStrings.current
                    Text(s.grantLocationPermission)
                }
            } else {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val s = LocalStrings.current
                    Text(s.retry)
                }
            }
        }
    }
}

@Composable
private fun WeatherContent(
    weatherData: WeatherData,
    modifier: Modifier = Modifier
) {
    val gradientColors = getGradientColors(
        weatherData.current.weatherCode,
        weatherData.current.isDay
    )

    LazyColumn(
        modifier = modifier
            .background(
                Brush.verticalGradient(gradientColors)
            ),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Current Weather Card
        item {
            CurrentWeatherCard(
                current = weatherData.current,
                location = weatherData.location
            )
        }

        // Weather Details (2 rows)
        item {
            WeatherDetailsCard(current = weatherData.current)
        }

        // Wind Info Card (compass + detailed wind)
        item {
            WindInfoCard(current = weatherData.current)
        }

        // Weather Potential / Alerts
        weatherData.currentPotential?.let { potential ->
            item {
                WeatherPotentialCard(potential = potential)
            }
        }

        // Precipitation Detail
        if (weatherData.current.precipitation > 0 || weatherData.current.rain > 0 ||
            weatherData.current.snowfall > 0 || weatherData.current.showers > 0) {
            item {
                PrecipitationDetailCard(current = weatherData.current)
            }
        }

        // Hourly Forecast (enhanced)
        item {
            HourlyForecastSection(hourlyData = weatherData.hourly)
        }

        // Daily Forecast (enhanced with weather potential)
        item {
            DailyForecastSection(dailyData = weatherData.daily)
        }

        // Sun Info
        weatherData.daily.firstOrNull()?.let { today ->
            item {
                SunInfoCard(sunrise = today.sunrise, sunset = today.sunset)
            }
        }
    }
}

@Composable
private fun CurrentWeatherCard(
    current: CurrentWeatherData,
    location: LocationInfo
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Weather Icon
        WeatherIcon(
            weatherCode = current.weatherCode,
            isDay = current.isDay,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Temperature
        Text(
            text = current.temperatureFormatted,
            style = MaterialTheme.typography.displayLarge,
            color = Color.White
        )

        // Feels like
        val s = LocalStrings.current
        Text(
            text = s.feelsLike(current.apparentTemperatureFormatted),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Weather condition
        Text(
            text = s.localized(current.weatherCondition.description, current.weatherCondition.descriptionId),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Location
        Text(
            text = location.fullName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeatherDetailsCard(current: CurrentWeatherData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.2f)
        )
    ) {
        val s = LocalStrings.current
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherDetailItem(
                    icon = Icons.Outlined.WaterDrop,
                    label = s.humidity,
                    value = current.humidityFormatted
                )
                WeatherDetailItem(
                    icon = Icons.Outlined.Air,
                    label = s.wind,
                    value = current.windSpeedFormatted
                )
                WeatherDetailItem(
                    icon = Icons.Outlined.Compress,
                    label = s.pressure,
                    value = current.pressureFormatted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherDetailItem(
                    icon = Icons.Outlined.Thermostat,
                    label = s.dewPoint,
                    value = current.dewPointFormatted
                )
                WeatherDetailItem(
                    icon = Icons.Outlined.Storm,
                    label = s.gusts,
                    value = current.windGustsFormatted
                )
                WeatherDetailItem(
                    icon = Icons.Outlined.Cloud,
                    label = s.clouds,
                    value = "${current.cloudCover}%"
                )
            }
        }
    }
}

@Composable
private fun WeatherDetailItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ──────────────────────────────────────────────────────────
// Wind Info Card — Compass + Detail
// ──────────────────────────────────────────────────────────

@Composable
private fun WindInfoCard(current: CurrentWeatherData) {
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
                text = s.windInfo,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wind Compass
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WindCompass(
                        windDirection = current.windDirection.toFloat(),
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = s.windDirectionShort(current.windDirection),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${current.windDirection}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Wind Details
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WindDetailRow(
                        label = s.speed,
                        value = current.windSpeedFormatted,
                        icon = Icons.Outlined.Speed
                    )
                    WindDetailRow(
                        label = s.gusts,
                        value = current.windGustsFormatted,
                        icon = Icons.Outlined.Storm
                    )
                    WindDetailRow(
                        label = s.direction,
                        value = s.windDirectionFull(current.windDirection),
                        icon = Icons.Outlined.Navigation
                    )
                }
            }
        }
    }
}

@Composable
private fun WindDetailRow(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Custom wind compass using Canvas
 */
@Composable
private fun WindCompass(
    windDirection: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = min(centerX, centerY) - 8f

        // Outer circle
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2f)
        )

        // Tick marks
        for (i in 0 until 360 step 30) {
            val isMajor = i % 90 == 0
            val tickLength = if (isMajor) 12f else 6f
            val angleRad = Math.toRadians(i.toDouble() - 90)
            val startR = radius - tickLength
            drawLine(
                color = Color.White.copy(alpha = if (isMajor) 0.8f else 0.4f),
                start = Offset(
                    centerX + (startR * cos(angleRad)).toFloat(),
                    centerY + (startR * sin(angleRad)).toFloat()
                ),
                end = Offset(
                    centerX + (radius * cos(angleRad)).toFloat(),
                    centerY + (radius * sin(angleRad)).toFloat()
                ),
                strokeWidth = if (isMajor) 2f else 1f
            )
        }

        // Wind direction arrow
        rotate(degrees = windDirection, pivot = Offset(centerX, centerY)) {
            val arrowPath = Path().apply {
                moveTo(centerX, centerY - radius + 18f)  // tip
                lineTo(centerX - 6f, centerY - radius + 32f)
                lineTo(centerX + 6f, centerY - radius + 32f)
                close()
            }
            drawPath(arrowPath, color = Color(0xFFFF5252))

            // Arrow line
            drawLine(
                color = Color(0xFFFF5252),
                start = Offset(centerX, centerY - radius + 30f),
                end = Offset(centerX, centerY),
                strokeWidth = 3f
            )
            // Tail
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(centerX, centerY),
                end = Offset(centerX, centerY + radius - 30f),
                strokeWidth = 2f
            )
        }
    }
}

// ──────────────────────────────────────────────────────────
// Weather Potential Card — Risks & Alerts
// ──────────────────────────────────────────────────────────

@Composable
private fun WeatherPotentialCard(potential: WeatherPotential) {
    // Only show if there's at least one non-low risk
    val hasRisk = potential.stormRisk > RiskLevel.LOW ||
            potential.heavyRainRisk > RiskLevel.LOW ||
            potential.hailRisk > RiskLevel.LOW ||
            potential.strongWindRisk > RiskLevel.LOW ||
            potential.tornadoRisk > RiskLevel.LOW

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
                text = s.extremeWeather,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (!hasRisk) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = s.noExtremeWeather,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            } else {
                // Risk indicators grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RiskIndicator(s.thunderstorm, potential.stormRisk, "⛈")
                        RiskIndicator(s.heavyRain, potential.heavyRainRisk, "🌧")
                        RiskIndicator(s.hail, potential.hailRisk, "🧊")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RiskIndicator(s.strongWind, potential.strongWindRisk, "💨")
                        RiskIndicator(s.tornado, potential.tornadoRisk, "🌪")
                        // CAPE indicator
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(100.dp)
                        ) {
                            Text(
                                text = "⚡",
                                fontSize = 20.sp
                            )
                            Text(
                                text = "CAPE",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${potential.maxCape.toInt()} J/kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Active alerts
                if (potential.alerts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = s.activeWarnings,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    potential.alerts.forEach { alert ->
                        AlertItem(alert)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskIndicator(label: String, risk: RiskLevel, emoji: String) {
    val s = LocalStrings.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(risk.colorHex))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = s.localized(risk.label, risk.labelId),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun AlertItem(alert: WeatherAlert) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(alert.risk.colorHex).copy(alpha = 0.2f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(alert.risk.colorHex))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = s.localized(alert.type.label, alert.type.labelId),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = s.localized(alert.description, alert.descriptionId),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(alert.risk.colorHex))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = s.localized(alert.risk.label, alert.risk.labelId),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ──────────────────────────────────────────────────────────
// Precipitation Detail Card
// ──────────────────────────────────────────────────────────

@Composable
private fun PrecipitationDetailCard(current: CurrentWeatherData) {
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
                text = s.precipitationDetail,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PrecipItem(s.total, "${current.precipitation} mm", "🌧")
                PrecipItem(s.rain, "${current.rain} mm", "💧")
                PrecipItem(s.showers, "${current.showers} mm", "⛈")
                PrecipItem(s.snow, "${current.snowfall} cm", "❄️")
            }
        }
    }
}

@Composable
private fun PrecipItem(label: String, value: String, emoji: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = emoji, fontSize = 20.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ──────────────────────────────────────────────────────────
// Hourly Forecast (enhanced)
// ──────────────────────────────────────────────────────────

@Composable
private fun HourlyForecastSection(hourlyData: List<HourlyWeatherData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val s = LocalStrings.current
        Text(
            text = s.hourlyForecast,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(hourlyData.take(24)) { hourly ->
                HourlyForecastItem(hourly = hourly)
            }
        }
    }
}

@Composable
private fun HourlyForecastItem(hourly: HourlyWeatherData) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = hourly.hour,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            WeatherIcon(
                weatherCode = hourly.weatherCode,
                isDay = hourly.isDay,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hourly.temperatureFormatted,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (hourly.precipitationProbability > 0) {
                Text(
                    text = hourly.precipitationProbabilityFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Rainy
                )
            }
            // Wind direction arrow + speed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Navigation,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(12.dp)
                        .rotate(hourly.windDirection.toFloat())
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "${hourly.windSpeed.toInt()} km/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Prakiraan 7 hari — setiap hari bisa di-expand untuk melihat prakiraan per jam (24 jam).
 * State expanded per‐hari disimpan di `expandedDays` set.
 */
@Composable
private fun DailyForecastSection(dailyData: List<DailyWeatherData>) {
    // Track which days are expanded by their date string
    var expandedDays by remember { mutableStateOf(setOf<String>()) }

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
                text = s.sevenDayForecast,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            dailyData.forEachIndexed { index, daily ->
                val isExpanded = daily.date in expandedDays

                DailyForecastItem(
                    daily = daily,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedDays = if (isExpanded) {
                            expandedDays - daily.date
                        } else {
                            expandedDays + daily.date
                        }
                    }
                )

                if (index < dailyData.lastIndex) {
                    Divider(
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Item prakiraan harian — menampilkan ringkasan hari dan panel
 * prakiraan per jam yang bisa di-expand.
 *
 * Saat di-tap akan menampilkan/menyembunyikan detail prakiraan per jam
 * dalam format horizontal scroll, sama seperti HourlyForecastSection.
 */
@Composable
private fun DailyForecastItem(
    daily: DailyWeatherData,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row — selalu tampil, bisa di-tap untuk expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nama hari
            Text(
                text = daily.dayName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            // Icon cuaca + probabilitas hujan
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                WeatherIcon(
                    weatherCode = daily.weatherCode,
                    isDay = true,
                    modifier = Modifier.size(24.dp)
                )
                if (daily.precipitationProbabilityMax > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = daily.precipitationProbabilityFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = Rainy
                    )
                }
            }

            // Suhu max/min + expand indicator
            Row(
                modifier = Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = daily.temperatureMaxFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = daily.temperatureMinFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val s = LocalStrings.current
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) s.close else s.hourlyDetailForecast,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (isExpanded) 180f else 0f)
                )
            }
        }

        // Expanded hourly detail — prakiraan per jam untuk hari ini
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            DailyHourlyDetail(daily = daily)
        }
    }
}

/**
 * Panel detail prakiraan per jam di dalam item harian.
 *
 * Menampilkan LazyRow horizontal berisi 24 kartu jam untuk hari tersebut.
 * Juga menampilkan info sunrise/sunset dan ringkasan angin.
 */
@Composable
private fun DailyHourlyDetail(daily: DailyWeatherData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // Row 1: Sunrise, Sunset, Wind max
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = Sunny,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = daily.sunrise,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WbTwilight,
                    contentDescription = null,
                    tint = GradientSunsetStart,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = daily.sunset,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Air,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${daily.windSpeedMax.toInt()} km/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Row 2: Wind Direction, Gusts, Precipitation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Navigation,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(daily.windDirectionDominant.toFloat())
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${LocalStrings.current.wind} ${LocalStrings.current.windDirectionShort(daily.windDirectionDominant)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Storm,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${LocalStrings.current.gusts} ${daily.windGustsMax.toInt()} km/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            if (daily.precipitationSum > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.WaterDrop,
                        contentDescription = null,
                        tint = Rainy,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${daily.precipitationSum} mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Weather Potential badges (for this day)
        daily.weatherPotential?.let { potential ->
            val hasRisk = potential.stormRisk > RiskLevel.LOW ||
                    potential.heavyRainRisk > RiskLevel.LOW ||
                    potential.hailRisk > RiskLevel.LOW ||
                    potential.strongWindRisk > RiskLevel.LOW ||
                    potential.tornadoRisk > RiskLevel.LOW

            if (hasRisk) {
                val s = LocalStrings.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (potential.stormRisk > RiskLevel.LOW) {
                        DailyRiskBadge("⛈ ${s.localized(potential.stormRisk.label, potential.stormRisk.labelId)}", potential.stormRisk)
                    }
                    if (potential.heavyRainRisk > RiskLevel.LOW) {
                        DailyRiskBadge("🌧 ${s.localized(potential.heavyRainRisk.label, potential.heavyRainRisk.labelId)}", potential.heavyRainRisk)
                    }
                    if (potential.hailRisk > RiskLevel.LOW) {
                        DailyRiskBadge("🧊 ${s.localized(potential.hailRisk.label, potential.hailRisk.labelId)}", potential.hailRisk)
                    }
                    if (potential.strongWindRisk > RiskLevel.LOW) {
                        DailyRiskBadge("💨 ${s.localized(potential.strongWindRisk.label, potential.strongWindRisk.labelId)}", potential.strongWindRisk)
                    }
                    if (potential.tornadoRisk > RiskLevel.LOW) {
                        DailyRiskBadge("🌪 ${s.localized(potential.tornadoRisk.label, potential.tornadoRisk.labelId)}", potential.tornadoRisk)
                    }
                }
            }
        }

        // Prakiraan per jam — horizontal scroll
        if (daily.hourlyForecasts.isNotEmpty()) {
            Text(
                text = LocalStrings.current.hourlyDetailForecast,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(daily.hourlyForecasts) { hourly ->
                    DailyHourlyItem(hourly = hourly)
                }
            }
        } else {
            Text(
                text = LocalStrings.current.hourlyDataUnavailable,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun DailyRiskBadge(text: String, risk: RiskLevel) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(risk.colorHex).copy(alpha = 0.3f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontSize = 10.sp
        )
    }
}

/**
 * Kartu kecil untuk 1 jam di dalam panel detail harian.
 * Menampilkan jam, icon, suhu, probabilitas hujan, dan kecepatan angin.
 */
@Composable
private fun DailyHourlyItem(hourly: HourlyWeatherData) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Jam
            Text(
                text = hourly.hour,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Icon cuaca
            WeatherIcon(
                weatherCode = hourly.weatherCode,
                isDay = hourly.isDay,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Suhu
            Text(
                text = hourly.temperatureFormatted,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            // Probabilitas hujan
            if (hourly.precipitationProbability > 0) {
                Text(
                    text = hourly.precipitationProbabilityFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Rainy,
                    fontSize = 10.sp
                )
            }
            // Kelembaban
            Text(
                text = "${hourly.humidity}%",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
            // Wind direction arrow + speed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Navigation,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(10.dp)
                        .rotate(hourly.windDirection.toFloat())
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "${hourly.windSpeed.toInt()} km/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SunInfoCard(sunrise: String, sunset: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = Sunny,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = LocalStrings.current.sunrise,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = sunrise,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.WbTwilight,
                    contentDescription = null,
                    tint = GradientSunsetStart,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = LocalStrings.current.sunset,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = sunset,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    isLoading: Boolean,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else {
            LazyColumn {
                items(results) { result ->
                    ListItem(
                        headlineContent = { 
                            Text(result.address?.getLocationName() ?: result.displayName)
                        },
                        supportingContent = {
                            Text(
                                result.address?.getFullLocation() ?: "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                        },
                        modifier = Modifier.clickableOnce { onResultClick(result) }
                    )
                }
            }
        }
    }
}

/**
 * Get gradient colors based on weather condition and time of day
 */
private fun getGradientColors(weatherCode: Int, isDay: Boolean): List<Color> {
    return when {
        !isDay -> listOf(GradientNightStart, GradientNightEnd)
        weatherCode in listOf(0, 1) -> listOf(GradientClearStart, GradientClearEnd)
        weatherCode in listOf(2, 3, 45, 48) -> listOf(GradientCloudyStart, GradientCloudyEnd)
        else -> listOf(GradientRainyStart, GradientRainyEnd)
    }
}

/**
 * Prevent double click
 */
@Composable
private fun Modifier.clickableOnce(onClick: () -> Unit): Modifier {
    var clicked by remember { mutableStateOf(false) }
    return this.clickable {
        if (!clicked) {
            clicked = true
            onClick()
        }
    }
}
