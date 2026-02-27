package com.weather.forecast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.model.SearchResult
import com.weather.forecast.data.model.WeatherData
import com.weather.forecast.data.preferences.PreferencesManager
import com.weather.forecast.data.preferences.UserPreferences
import com.weather.forecast.data.repository.WeatherRepository
import com.weather.forecast.location.LocationManager
import com.weather.forecast.worker.WeatherWorkerScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Weather ViewModel
 * 
 * ViewModel untuk screen utama weather.
 * Mengelola state UI dan business logic.
 * 
 * State:
 * - WeatherUiState: Loading, Success, Error
 * - UserPreferences: Settings dari DataStore
 */
class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val weatherRepository = WeatherRepository()
    private val locationManager = LocationManager(application)
    private val preferencesManager = PreferencesManager(application)

    // UI State
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    // Search State
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // User Preferences
    val userPreferences: StateFlow<UserPreferences> = preferencesManager.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    // Is refreshing
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadWeatherData()
        setupBackgroundWorker()
    }

    /**
     * Load weather data berdasarkan lokasi
     */
    fun loadWeatherData() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading

            try {
                val location = if (locationManager.hasLocationPermission()) {
                    locationManager.getLocationWithFallback()
                } else {
                    // Try last known location from preferences
                    val prefs = userPreferences.value
                    if (prefs.lastLatitude != null && prefs.lastLongitude != null) {
                        android.location.Location("").apply {
                            latitude = prefs.lastLatitude
                            longitude = prefs.lastLongitude
                        }
                    } else {
                        null
                    }
                }

                if (location == null) {
                    _uiState.value = WeatherUiState.Error(
                        message = AppLocaleManager.strings.locationNotAvailable,
                        isLocationError = true
                    )
                    return@launch
                }

                fetchWeatherForLocation(location.latitude, location.longitude)
            } catch (e: SecurityException) {
                _uiState.value = WeatherUiState.Error(
                    message = AppLocaleManager.strings.locationPermissionRequired,
                    isLocationError = true
                )
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(
                    message = e.message ?: AppLocaleManager.strings.unknownError,
                    isLocationError = false
                )
            }
        }
    }

    /**
     * Fetch weather untuk koordinat tertentu
     */
    private suspend fun fetchWeatherForLocation(latitude: Double, longitude: Double) {
        val result = weatherRepository.getWeatherData(latitude, longitude)
        
        result.onSuccess { weatherData ->
            _uiState.value = WeatherUiState.Success(weatherData)
            
            // Save location to preferences
            preferencesManager.saveLastLocation(
                latitude = latitude,
                longitude = longitude,
                name = weatherData.location.name
            )
        }.onFailure { error ->
            _uiState.value = WeatherUiState.Error(
                message = error.message ?: AppLocaleManager.strings.failedLoadWeather,
                isLocationError = false
            )
        }
    }

    /**
     * Refresh weather data
     */
    fun refreshWeatherData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadWeatherData()
            _isRefreshing.value = false
        }
    }

    /**
     * Load weather untuk lokasi dari search result
     */
    fun loadWeatherForLocation(searchResult: SearchResult) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            _searchResults.value = emptyList()
            
            val latitude = searchResult.lat.toDoubleOrNull() ?: return@launch
            val longitude = searchResult.lon.toDoubleOrNull() ?: return@launch
            
            fetchWeatherForLocation(latitude, longitude)
        }
    }

    /**
     * Search lokasi berdasarkan query
     */
    fun searchLocation(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            
            val result = weatherRepository.searchLocation(query)
            result.onSuccess { results ->
                _searchResults.value = results
            }.onFailure {
                _searchResults.value = emptyList()
            }
            
            _isSearching.value = false
        }
    }

    /**
     * Clear search results
     */
    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    /**
     * Setup background worker untuk weather updates
     */
    private fun setupBackgroundWorker() {
        WeatherWorkerScheduler.schedulePeriodicWeatherUpdate(getApplication())
    }

    /**
     * Update notification settings
     */
    fun updateNotificationSettings(
        enabled: Boolean? = null,
        dailyEnabled: Boolean? = null,
        severeWeatherAlert: Boolean? = null,
        rainAlert: Boolean? = null,
        temperatureAlert: Boolean? = null
    ) {
        viewModelScope.launch {
            preferencesManager.updateNotificationSettings(
                enabled = enabled,
                dailyEnabled = dailyEnabled,
                severeWeatherAlert = severeWeatherAlert,
                rainAlert = rainAlert,
                temperatureAlert = temperatureAlert
            )
        }
    }
}

/**
 * UI State untuk Weather Screen
 */
sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    
    data class Success(val data: WeatherData) : WeatherUiState()
    
    data class Error(
        val message: String,
        val isLocationError: Boolean = false
    ) : WeatherUiState()
}
