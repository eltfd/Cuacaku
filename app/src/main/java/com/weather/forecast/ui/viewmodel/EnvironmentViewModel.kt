package com.weather.forecast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.model.ActiveDisasterMonitor
import com.weather.forecast.data.model.AirQualityData
import com.weather.forecast.data.model.DisasterForecast
import com.weather.forecast.data.model.SeismicMonitorData
import com.weather.forecast.data.model.WaterQualityData
import com.weather.forecast.data.model.SOSState
import com.weather.forecast.data.model.DisasterLifecyclePhase
import com.weather.forecast.data.preferences.PreferencesManager
import com.weather.forecast.data.repository.AirQualityRepository
import com.weather.forecast.data.repository.DisasterMonitorRepository
import com.weather.forecast.data.repository.DisasterRepository
import com.weather.forecast.data.repository.SeismicRepository
import com.weather.forecast.data.repository.WaterQualityRepository
import com.weather.forecast.location.LocationManager
import com.weather.forecast.service.SOSManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Environment ViewModel
 *
 * Mengelola data lingkungan (udara, air, bencana) dengan
 * prioritized async loading berdasarkan lokasi user.
 *
 * ── Loading Strategy ──
 * Phase 1 (High Priority): Air quality & water quality — data yang langsung
 *   terlihat user di tab pertama, paling ringan bandwidth-nya.
 * Phase 2 (Medium Priority): Disaster forecast — prediksi potensi bencana.
 * Phase 3 (Low Priority): Disaster monitor — data ReliefWeb yang lebih berat
 *   (reverse geocode + multiple API calls).
 *
 * Semua data diambil berdasarkan lokasi user secara dinamis → global support.
 *
 * ── Bandwidth Optimization ──
 * - Sequential phases menghindari burst request bersamaan
 * - Per-module caching di masing-masing repository
 * - Lazy load: data Phase 2/3 bisa di-trigger manual saat user buka tab
 */
class EnvironmentViewModel(application: Application) : AndroidViewModel(application) {

    private val airQualityRepository = AirQualityRepository()
    private val waterQualityRepository = WaterQualityRepository()
    private val disasterRepository = DisasterRepository(application)
    private val disasterMonitorRepository = DisasterMonitorRepository(application)
    private val seismicRepository = SeismicRepository(application)
    private val sosManager = SOSManager(application)
    private val locationManager = LocationManager(application)
    private val preferencesManager = PreferencesManager(application)

    // Air Quality UI State
    private val _airQualityState = MutableStateFlow<AirQualityUiState>(AirQualityUiState.Loading)
    val airQualityState: StateFlow<AirQualityUiState> = _airQualityState.asStateFlow()

    // Water Quality UI State
    private val _waterQualityState = MutableStateFlow<WaterQualityUiState>(WaterQualityUiState.Loading)
    val waterQualityState: StateFlow<WaterQualityUiState> = _waterQualityState.asStateFlow()

    // Disaster UI State
    private val _disasterState = MutableStateFlow<DisasterUiState>(DisasterUiState.Loading)
    val disasterState: StateFlow<DisasterUiState> = _disasterState.asStateFlow()

    // Disaster Monitor UI State (active/recovery disasters)
    private val _disasterMonitorState = MutableStateFlow<DisasterMonitorUiState>(DisasterMonitorUiState.Loading)
    val disasterMonitorState: StateFlow<DisasterMonitorUiState> = _disasterMonitorState.asStateFlow()

    // Seismic Monitor UI State (earthquakes, tsunami, volcanoes, waves)
    private val _seismicState = MutableStateFlow<SeismicUiState>(SeismicUiState.Loading)
    val seismicState: StateFlow<SeismicUiState> = _seismicState.asStateFlow()

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
     * Load all environment data with prioritized async loading.
     *
     * Phase 1 (immediate): Air quality + Water quality — lightweight, user sees first
     * Phase 2 (after Phase 1): Disaster forecast — medium weight
     * Phase 3 (after Phase 2): Disaster monitor — heaviest (reverse geocode + ReliefWeb)
     *
     * Each phase runs in parallel internally, phases are sequential to avoid
     * burst network usage and reduce bandwidth pressure.
     */
    fun loadAllData() {
        viewModelScope.launch {
            val location = getLocation() ?: return@launch

            currentLatitude = location.first
            currentLongitude = location.second

            // ── Phase 1: High Priority (lightweight, visible first) ──
            val airJob = launch { loadAirQualityData(location.first, location.second) }
            val waterJob = launch { loadWaterQualityData(location.first, location.second) }

            // Wait for Phase 1 to complete before starting heavier APIs
            airJob.join()
            waterJob.join()

            // ── Phase 2: Medium Priority (disaster prediction) ──
            val disasterJob = launch { loadDisasterData(location.first, location.second) }
            disasterJob.join()

            // ── Phase 3: Low Priority (heaviest — reverse geocode + ReliefWeb) ──
            launch { loadDisasterMonitorData(location.first, location.second) }

            // ── Phase 4: Seismic monitoring (earthquake, tsunami, volcano) ──
            launch { loadSeismicData(location.first, location.second) }
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

    /**
     * Load disaster monitor data saja
     */
    fun loadDisasterMonitorData() {
        viewModelScope.launch {
            val lat = currentLatitude
            val lon = currentLongitude
            if (lat != null && lon != null) {
                loadDisasterMonitorData(lat, lon)
            } else {
                val location = getLocation() ?: return@launch
                currentLatitude = location.first
                currentLongitude = location.second
                loadDisasterMonitorData(location.first, location.second)
            }
        }
    }

    /**
     * Load disaster forecast data saja
     */
    fun loadDisasterData() {
        viewModelScope.launch {
            val lat = currentLatitude
            val lon = currentLongitude
            if (lat != null && lon != null) {
                loadDisasterData(lat, lon)
            } else {
                val location = getLocation() ?: return@launch
                currentLatitude = location.first
                currentLongitude = location.second
                loadDisasterData(location.first, location.second)
            }
        }
    }

    /**
     * Load seismic monitoring data (earthquakes, tsunami, volcanoes, waves)
     */
    fun loadSeismicData() {
        viewModelScope.launch {
            val lat = currentLatitude
            val lon = currentLongitude
            if (lat != null && lon != null) {
                loadSeismicData(lat, lon)
            } else {
                val location = getLocation() ?: return@launch
                currentLatitude = location.first
                currentLongitude = location.second
                loadSeismicData(location.first, location.second)
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
                message = error.message ?: AppLocaleManager.strings.failedLoadAirQuality
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
                message = error.message ?: AppLocaleManager.strings.failedLoadWaterQuality
            )
        }
    }

    private suspend fun loadDisasterData(latitude: Double, longitude: Double) {
        _disasterState.value = DisasterUiState.Loading

        val result = disasterRepository.getDisasterForecast(latitude, longitude)
        result.onSuccess { data ->
            _disasterState.value = DisasterUiState.Success(data)
        }.onFailure { error ->
            _disasterState.value = DisasterUiState.Error(
                message = error.message ?: AppLocaleManager.strings.failedLoadDisasterForecast
            )
        }
    }

    private suspend fun loadDisasterMonitorData(latitude: Double, longitude: Double) {
        _disasterMonitorState.value = DisasterMonitorUiState.Loading

        val result = disasterMonitorRepository.getActiveDisasters(latitude, longitude)
        result.onSuccess { data ->
            if (data.disasters.isEmpty()) {
                _disasterMonitorState.value = DisasterMonitorUiState.Empty
            } else {
                _disasterMonitorState.value = DisasterMonitorUiState.Success(data)
            }
        }.onFailure { error ->
            _disasterMonitorState.value = DisasterMonitorUiState.Error(
                message = error.message ?: AppLocaleManager.strings.failedLoadDisasterMonitor
            )
        }
    }

    private suspend fun loadSeismicData(latitude: Double, longitude: Double) {
        _seismicState.value = SeismicUiState.Loading

        // Record location for SOS movement tracking
        sosManager.recordLocation(latitude, longitude)

        val result = seismicRepository.getSeismicMonitorData(latitude, longitude)
        result.onSuccess { data ->
            // Evaluate SOS eligibility based on current disaster data
            val sosState = sosManager.evaluateSOSEligibility(
                impactAreas = data.impactAreas,
                userLat = latitude,
                userLon = longitude
            )
            val enrichedData = data.copy(sosState = sosState)
            _seismicState.value = SeismicUiState.Success(enrichedData)
        }.onFailure { error ->
            _seismicState.value = SeismicUiState.Error(
                message = error.message ?: AppLocaleManager.strings.failedLoadSeismicData
            )
        }
    }

    /**
     * Activate SOS emergency signal.
     * Requires user to be in a critical disaster zone and stationary.
     */
    fun activateSOS() {
        viewModelScope.launch {
            val lat = currentLatitude ?: return@launch
            val lon = currentLongitude ?: return@launch
            val currentData = (_seismicState.value as? SeismicUiState.Success)?.data ?: return@launch

            val message = sosManager.activateSOS(lat, lon)

            // Update state to reflect activated SOS
            val updatedSOS = sosManager.evaluateSOSEligibility(
                impactAreas = currentData.impactAreas,
                userLat = lat,
                userLon = lon
            )
            val updatedData = currentData.copy(sosState = updatedSOS)
            _seismicState.value = SeismicUiState.Success(updatedData)
        }
    }

    /**
     * Deactivate SOS emergency signal.
     */
    fun deactivateSOS() {
        viewModelScope.launch {
            sosManager.deactivateSOS()

            val currentData = (_seismicState.value as? SeismicUiState.Success)?.data ?: return@launch
            val updatedData = currentData.copy(sosState = SOSState())
            _seismicState.value = SeismicUiState.Success(updatedData)
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
            val msg = AppLocaleManager.strings.locationNotAvailable
            _airQualityState.value = AirQualityUiState.Error(msg)
            _waterQualityState.value = WaterQualityUiState.Error(msg)
            _disasterState.value = DisasterUiState.Error(msg)
            _disasterMonitorState.value = DisasterMonitorUiState.Error(msg)
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

/**
 * UI State untuk Disaster Forecast Screen
 */
sealed class DisasterUiState {
    data object Loading : DisasterUiState()
    data class Success(val data: DisasterForecast) : DisasterUiState()
    data class Error(val message: String) : DisasterUiState()
}

/**
 * UI State untuk Disaster Monitor Screen (bencana aktif/recovery)
 */
sealed class DisasterMonitorUiState {
    data object Loading : DisasterMonitorUiState()
    data class Success(val data: ActiveDisasterMonitor) : DisasterMonitorUiState()
    data object Empty : DisasterMonitorUiState()
    data class Error(val message: String) : DisasterMonitorUiState()
}

/**
 * UI State untuk Seismic Monitor Screen (gempa, tsunami, gunung api, gelombang)
 */
sealed class SeismicUiState {
    data object Loading : SeismicUiState()
    data class Success(val data: SeismicMonitorData) : SeismicUiState()
    data class Error(val message: String) : SeismicUiState()
}
