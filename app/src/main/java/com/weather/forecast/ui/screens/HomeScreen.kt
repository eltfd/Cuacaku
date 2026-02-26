package com.weather.forecast.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.weather.forecast.data.model.*
import com.weather.forecast.ui.components.*
import com.weather.forecast.ui.theme.*
import com.weather.forecast.ui.viewmodel.WeatherUiState
import com.weather.forecast.ui.viewmodel.WeatherViewModel

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
    val locationName = when (uiState) {
        is WeatherUiState.Success -> uiState.data.location.name
        else -> "Weather Forecast"
    }

    if (showSearchBar) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onSearch = {},
            active = true,
            onActiveChange = onSearchBarToggle,
            placeholder = { Text("Cari lokasi...") },
            leadingIcon = {
                IconButton(onClick = { onSearchBarToggle(false) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            Text("Memuat data cuaca...")
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
                    Text("Berikan Izin Lokasi")
                }
            } else {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Coba Lagi")
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

        // Weather Details
        item {
            WeatherDetailsCard(current = weatherData.current)
        }

        // Hourly Forecast
        item {
            HourlyForecastSection(hourlyData = weatherData.hourly)
        }

        // Daily Forecast
        item {
            DailyForecastSection(dailyData = weatherData.daily)
        }

        // Sun Info (if available)
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
        Text(
            text = "Terasa seperti ${current.apparentTemperatureFormatted}",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Weather condition
        Text(
            text = current.weatherCondition.descriptionId,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WeatherDetailItem(
                icon = Icons.Outlined.WaterDrop,
                label = "Kelembaban",
                value = current.humidityFormatted
            )
            WeatherDetailItem(
                icon = Icons.Outlined.Air,
                label = "Angin",
                value = current.windSpeedFormatted
            )
            WeatherDetailItem(
                icon = Icons.Outlined.Compress,
                label = "Tekanan",
                value = current.pressureFormatted
            )
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

@Composable
private fun HourlyForecastSection(hourlyData: List<HourlyWeatherData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Prakiraan Per Jam",
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
        }
    }
}

@Composable
private fun DailyForecastSection(dailyData: List<DailyWeatherData>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Prakiraan 7 Hari",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            dailyData.forEach { daily ->
                DailyForecastItem(daily = daily)
                if (daily != dailyData.last()) {
                    Divider(
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyForecastItem(daily: DailyWeatherData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = daily.dayName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

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

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End
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
                    text = "Terbit",
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
                    text = "Terbenam",
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
