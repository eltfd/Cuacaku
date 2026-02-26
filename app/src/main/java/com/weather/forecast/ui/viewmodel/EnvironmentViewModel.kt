package com.weather.forecast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weather.forecast.data.model.AirQualityData
import com.weather.forecast.data.model.WaterQualityData
import com.weather.forecast.data.preferences.PreferencesManager
import com.weather.forecast.data.repository.AirQualityRepository
import com.weather.forecast.data.repository.WaterQualityRepository
import com.weather.forecast.location.LocationManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Environment ViewModel
 *
 * Mengelola data kualitas udara dan kualitas air.
 * Menggunakan lokasi yang sama dengan WeatherViewModel.
 *
 * State:
 * - AirQualityUiState: Loading, Success, Error
 * - WaterQualityUiState: Loading, Success, Error
 */
class EnvironmentViewModel(application: Application) : AndroidViewModel(application) {

    private val airQualityRepository = AirQualityRepository()
    private val waterQualityRepository = WaterQualityRepository()
    private val locationManager = LocationManager(application)
    private val preferencesManager = PreferencesManager(application)

    // Air Quality UI State
    private val _airQualityState = MutableStateFlow<AirQualityUiState>(AirQualityUiState.Loading)
    val airQualityState: StateFlow<AirQualityUiState> = _airQualityState.asStateFlow()

    // Water Quality UI State
    private val _waterQualityState = MutableStateFlow<WaterQualityUiState>(WaterQualityUiState.Loading)
    val waterQualityState: StateFlow<WaterQualityUiState> = _waterQualityState.asStateFlow()

    // Track current location for refresh
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    // User Preferences (shared)
    val userPreferences = preferencesManager.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.weather.forecast.data.preferences.UserPreferences()
        )

    init {
        loadAllData()
    }

    /**
     * Load air quality & water quality data
     */
    fun loadAllData() {
        viewModelScope.launch {
            val location = getLocation() ?: return@launch

            currentLatitude = location.first
            currentLongitude = location.second

            // Load both in parallel
            launch { loadAirQualityData(location.first, location.second) }
            launch { loadWaterQualityData(location.first, location.second) }
        }
    }

    /**
     * Load air quality data saja
     */
    fun loadAirQualityData() {
        viewModelScope.launch {
            val lat = currentLatitude
            val lon = currentLongitude
            if (lat != null && lon != null) {
                loadAirQualityData(lat, lon)
            } else {
                val location = getLocation() ?: return@launch
                currentLatitude = location.first
                currentLongitude = location.second
                loadAirQualityData(location.first, location.second)
            }
        }
    }

    /**
     * Load water quality data saja
     */
    fun loadWaterQualityData() {
        viewModelScope.launch {
            val lat = currentLatitude
            val lon = currentLongitude
            if (lat != null && lon != null) {
                loadWaterQualityData(lat, lon)
            } else {
                val location = getLocation() ?: return@launch
                currentLatitude = location.first
                currentLongitude = location.second
                loadWaterQualityData(location.first, location.second)
            }
        }
    }

    private suspend fun loadAirQualityData(latitude: Double, longitude: Double) {
        _airQualityState.value = AirQualityUiState.Loading

        val result = airQualityRepository.getAirQualityData(latitude, longitude)
        result.onSuccess { data ->
            _airQualityState.value = AirQualityUiState.Success(data)
        }.onFailure { error ->
            _airQualityState.value = AirQualityUiState.Error(
                message = error.message ?: "Gagal memuat data kualitas udara"
            )
        }
    }

    private suspend fun loadWaterQualityData(latitude: Double, longitude: Double) {
        _waterQualityState.value = WaterQualityUiState.Loading

        val result = waterQualityRepository.getWaterQualityData(latitude, longitude)
        result.onSuccess { data ->
            _waterQualityState.value = WaterQualityUiState.Success(data)
        }.onFailure { error ->
            _waterQualityState.value = WaterQualityUiState.Error(
                message = error.message ?: "Gagal memuat data kualitas air"
            )
        }
    }

    /**
     * Get location dari GPS atau preferences
     */
    private suspend fun getLocation(): Pair<Double, Double>? {
        return try {
            if (locationManager.hasLocationPermission()) {
                val location = locationManager.getLocationWithFallback()
                if (location != null) {
                    Pair(location.latitude, location.longitude)
                } else {
                    getLocationFromPrefs()
                }
            } else {
                getLocationFromPrefs()
            }
        } catch (e: Exception) {
            getLocationFromPrefs()
        }
    }

    private fun getLocationFromPrefs(): Pair<Double, Double>? {
        val prefs = userPreferences.value
        return if (prefs.lastLatitude != null && prefs.lastLongitude != null) {
            Pair(prefs.lastLatitude, prefs.lastLongitude)
        } else {
            _airQualityState.value = AirQualityUiState.Error("Lokasi tidak tersedia")
            _waterQualityState.value = WaterQualityUiState.Error("Lokasi tidak tersedia")
            null
        }
    }
}

/**
 * UI State untuk Air Quality Screen
 */
sealed class AirQualityUiState {
    data object Loading : AirQualityUiState()
    data class Success(val data: AirQualityData) : AirQualityUiState()
    data class Error(val message: String) : AirQualityUiState()
}

/**
 * UI State untuk Water Quality Screen
 */
sealed class WaterQualityUiState {
    data object Loading : WaterQualityUiState()
    data class Success(val data: WaterQualityData) : WaterQualityUiState()
    data class Error(val message: String) : WaterQualityUiState()
}
