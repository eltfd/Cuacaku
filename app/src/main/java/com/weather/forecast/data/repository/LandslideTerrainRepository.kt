package com.weather.forecast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.weather.forecast.data.api.ElevationApiService
import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * Landslide Terrain Repository
 *
 * Mengelola data terrain/topografi untuk analisis risiko longsor:
 *
 * ── Data Sources ──
 * 1. **Open-Elevation API** — Elevasi & kemiringan lereng (SRTM 30m)
 *    - Grid 5 titik (center + N/S/E/W)
 *    - Kalkulasi slope gradient dari selisih elevasi
 *    - Gratis, tanpa API key
 *
 * 2. **Open-Meteo Soil Data** — dari hourly weather data
 *    - soil_moisture_0_to_7cm, 7_to_28cm, 28_to_100cm
 *    - soil_temperature_0cm
 *    - Sudah termasuk di HourlyWeatherData
 *
 * 3. **Derived Data**
 *    - Soil Saturation Index — dari 3 lapisan soil moisture
 *    - Vegetation Proxy Index — estimasi dari kondisi tanah & udara
 *    - Rainfall metrics — dari hourly weather data
 *
 * ── Caching ──
 * Elevation data di-cache per lokasi (resolusi 0.01°)
 * karena topografi tidak berubah. Cache berlaku 7 hari.
 */
class LandslideTerrainRepository(context: Context) {

    private val elevationApi = RetrofitClient.elevationApi
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "landslide_terrain"
        private const val KEY_CACHED_ELEVATION = "cached_elevation"
        private const val KEY_CACHED_SLOPE = "cached_slope"
        private const val KEY_CACHED_LAT = "cached_lat"
        private const val KEY_CACHED_LON = "cached_lon"
        private const val KEY_CACHE_TIME = "cache_time"

        /** Cache elevasi selama 7 hari (topografi tidak berubah) */
        private const val ELEVATION_CACHE_MS = 7 * 24 * 60 * 60 * 1000L

        /** Grid spacing for slope calculation (km) */
        private const val GRID_SPACING_KM = 0.5

        /** Location change threshold for cache invalidation (degrees, ~1km) */
        private const val LOCATION_THRESHOLD = 0.01
    }

    /**
     * Mendapatkan data terrain lengkap untuk analisis longsor.
     *
     * Flow:
     * 1. Fetch/cache elevation grid → calculate slope
     * 2. Extract soil data dari hourly weather
     * 3. Calculate derived indices
     * 4. Build LandslideTerrainData
     *
     * @param latitude User latitude
     * @param longitude User longitude
     * @param hourly Hourly weather data (contains soil moisture/temp)
     * @param allDaily Daily data (for antecedent rainfall)
     * @param elevation Elevation from WeatherResponse (fallback)
     */
    suspend fun getTerrainData(
        latitude: Double,
        longitude: Double,
        hourly: List<HourlyWeatherData>,
        allDaily: List<DailyWeatherData>,
        elevation: Double = 0.0
    ): LandslideTerrainData {
        return withContext(Dispatchers.IO) {
            // 1. Elevation & slope (from cache or API)
            val slopeResult = fetchSlopeData(latitude, longitude, elevation)

            // 2. Soil data from hourly weather
            val soilData = extractSoilData(hourly)

            // 3. Rainfall metrics
            val rainfallMetrics = extractRainfallMetrics(hourly, allDaily)

            // 4. Derived indices
            val saturationIndex = SoilSaturationCalculator.calculate(
                soilData.shallow, soilData.medium, soilData.deep
            )

            val avgHumidity = hourly.map { it.humidity.toDouble() }.average()
                .takeIf { !it.isNaN() } ?: 50.0

            val vegetationIndex = VegetationProxyEstimator.estimate(
                soilMoisture = soilData.shallow,
                soilTemp = soilData.temperature,
                humidity = avgHumidity
            )

            LandslideTerrainData(
                elevation = slopeResult.centerElevation,
                slopeAngle = slopeResult.maxSlopeAngle,
                slopeCategory = SlopeCategory.fromAngle(slopeResult.maxSlopeAngle),
                soilMoistureShallow = soilData.shallow,
                soilMoistureMedium = soilData.medium,
                soilMoistureDeep = soilData.deep,
                soilTemperature = soilData.temperature,
                soilSaturationIndex = saturationIndex,
                vegetationIndex = vegetationIndex,
                todayPrecipitation = rainfallMetrics.todayPrecip,
                antecedentRainfall = rainfallMetrics.antecedentRain,
                maxRainfallIntensity = rainfallMetrics.maxIntensity,
                continuousRainHours = rainfallMetrics.continuousHours,
                elevationGrid = slopeResult.gridPoints
            )
        }
    }

    // ════════════════════════════════════════════════
    //  Elevation & Slope
    // ════════════════════════════════════════════════

    private data class SlopeResult(
        val centerElevation: Double,
        val maxSlopeAngle: Double,
        val gridPoints: List<ElevationPoint>
    )

    /**
     * Fetch elevation data dan hitung kemiringan lereng.
     *
     * Menggunakan grid 5 titik (center + 4 arah kardinal)
     * dengan jarak ~500m. Slope dihitung dari selisih elevasi
     * antara center dan masing-masing titik.
     */
    private suspend fun fetchSlopeData(
        latitude: Double,
        longitude: Double,
        fallbackElevation: Double
    ): SlopeResult {
        // Check cache
        val cached = getCachedSlope(latitude, longitude)
        if (cached != null) return cached

        return try {
            val gridPoints = ElevationApiService.generateSlopeGrid(
                latitude, longitude, GRID_SPACING_KM
            )
            val query = ElevationApiService.buildLocationsQuery(gridPoints)
            val response = elevationApi.getElevation(query)
            val results = response.results

            if (results != null && results.size >= 5) {
                val labels = listOf("Center", "N", "S", "E", "W")
                val elevationPoints = results.mapIndexed { i, r ->
                    ElevationPoint(
                        label = labels.getOrElse(i) { "?" },
                        latitude = r.latitude,
                        longitude = r.longitude,
                        elevation = r.elevation
                    )
                }

                val centerElev = results[0].elevation
                val distanceM = GRID_SPACING_KM * 1000.0 // Convert km to meters

                // Calculate slope angle from center to each cardinal point
                val slopes = (1..4).map { i ->
                    val elevDiff = kotlin.math.abs(centerElev - results[i].elevation)
                    Math.toDegrees(atan(elevDiff / distanceM))
                }

                val maxSlope = slopes.maxOrNull() ?: 0.0

                val result = SlopeResult(
                    centerElevation = centerElev,
                    maxSlopeAngle = maxSlope,
                    gridPoints = elevationPoints
                )

                // Cache result
                cacheSlope(latitude, longitude, result)
                result
            } else {
                // Fallback: use weather API elevation, assume gentle slope
                SlopeResult(
                    centerElevation = fallbackElevation,
                    maxSlopeAngle = 0.0,
                    gridPoints = emptyList()
                )
            }
        } catch (_: Exception) {
            // On error, use cached or fallback
            SlopeResult(
                centerElevation = fallbackElevation,
                maxSlopeAngle = getCachedSlopeAngle(),
                gridPoints = emptyList()
            )
        }
    }

    // ════════════════════════════════════════════════
    //  Soil Data Extraction
    // ════════════════════════════════════════════════

    private data class SoilData(
        val shallow: Double,   // 0-7cm
        val medium: Double,    // 7-28cm
        val deep: Double,      // 28-100cm
        val temperature: Double
    )

    private fun extractSoilData(hourly: List<HourlyWeatherData>): SoilData {
        // Average across available hours (usually 24h)
        val shallow = hourly.map { it.soilMoistureShallow }
            .filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val medium = hourly.map { it.soilMoistureMedium }
            .filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val deep = hourly.map { it.soilMoistureDeep }
            .filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val temp = hourly.map { it.soilTemperature }
            .filter { it != 0.0 }.average().takeIf { !it.isNaN() } ?: 20.0

        return SoilData(shallow, medium, deep, temp)
    }

    // ════════════════════════════════════════════════
    //  Rainfall Metrics
    // ════════════════════════════════════════════════

    private data class RainfallMetrics(
        val todayPrecip: Double,
        val antecedentRain: Double,
        val maxIntensity: Double,
        val continuousHours: Int
    )

    private fun extractRainfallMetrics(
        hourly: List<HourlyWeatherData>,
        allDaily: List<DailyWeatherData>
    ): RainfallMetrics {
        val todayPrecip = hourly.sumOf { it.precipitation }
        val antecedentRain = allDaily.take(3).sumOf { it.precipitationSum }
        val maxIntensity = hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0

        // Count continuous rain hours from latest
        var continuous = 0
        for (h in hourly.reversed()) {
            if (h.precipitation > 0.5) continuous++ else break
        }

        return RainfallMetrics(todayPrecip, antecedentRain, maxIntensity, continuous)
    }

    // ════════════════════════════════════════════════
    //  Caching
    // ════════════════════════════════════════════════

    private fun getCachedSlope(latitude: Double, longitude: Double): SlopeResult? {
        val now = System.currentTimeMillis()
        val cacheTime = prefs.getLong(KEY_CACHE_TIME, 0)
        if (now - cacheTime > ELEVATION_CACHE_MS) return null

        val cachedLat = prefs.getFloat(KEY_CACHED_LAT, 0f).toDouble()
        val cachedLon = prefs.getFloat(KEY_CACHED_LON, 0f).toDouble()

        // Check if location changed significantly
        if (kotlin.math.abs(latitude - cachedLat) > LOCATION_THRESHOLD ||
            kotlin.math.abs(longitude - cachedLon) > LOCATION_THRESHOLD) {
            return null
        }

        val elevation = prefs.getFloat(KEY_CACHED_ELEVATION, 0f).toDouble()
        val slope = prefs.getFloat(KEY_CACHED_SLOPE, 0f).toDouble()
        if (elevation == 0.0 && slope == 0.0) return null

        return SlopeResult(
            centerElevation = elevation,
            maxSlopeAngle = slope,
            gridPoints = emptyList()  // Grid isn't cached for simplicity
        )
    }

    private fun cacheSlope(latitude: Double, longitude: Double, result: SlopeResult) {
        prefs.edit()
            .putFloat(KEY_CACHED_LAT, latitude.toFloat())
            .putFloat(KEY_CACHED_LON, longitude.toFloat())
            .putFloat(KEY_CACHED_ELEVATION, result.centerElevation.toFloat())
            .putFloat(KEY_CACHED_SLOPE, result.maxSlopeAngle.toFloat())
            .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun getCachedSlopeAngle(): Double {
        return prefs.getFloat(KEY_CACHED_SLOPE, 0f).toDouble()
    }
}
