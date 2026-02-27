package com.weather.forecast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Seismic & Volcanic Monitor Repository
 *
 * Manages real-time data for:
 * - Earthquake monitoring (USGS Earthquake Hazards API)
 * - Tsunami risk assessment (derived from submarine earthquakes)
 * - Volcanic activity monitoring (USGS Volcano + NASA EONET + embedded DB)
 * - High wave warnings (Open-Meteo Marine API)
 * - Impact area calculation for disaster visualization
 *
 * ── Data Sources ──
 * 1. USGS Earthquake Hazards API — Real-time global earthquake feed
 * 2. USGS Volcano Hazards API — Volcano alert levels
 * 3. NASA EONET v3 — Volcanic eruption events
 * 4. Open-Meteo Marine API — Wave/swell data for maritime warnings
 * 5. Embedded volcano database — 35+ active volcanoes (Ring of Fire)
 *
 * ── Caching ──
 * SharedPreferences with 10-minute interval for earthquake data,
 * 30-minute interval for volcanic data (less volatile).
 */
class SeismicRepository(private val context: Context) {

    private val earthquakeApi = RetrofitClient.earthquakeApi
    private val volcanoApi = RetrofitClient.volcanoApi
    private val eonetApi = RetrofitClient.eonetApi
    private val marineApi = RetrofitClient.marineApi
    private val gson = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "seismic_monitor"
        private const val KEY_LAST_EARTHQUAKE_FETCH = "last_eq_fetch"
        private const val KEY_LAST_VOLCANO_FETCH = "last_volcano_fetch"
        private const val KEY_CACHED_DATA = "cached_seismic_data"

        /** Earthquake data refresh interval: 10 minutes */
        private const val EQ_FETCH_INTERVAL_MS = 10 * 60 * 1000L
        /** Volcano data refresh interval: 30 minutes */
        private const val VOLCANO_FETCH_INTERVAL_MS = 30 * 60 * 1000L

        /** Max distance for "nearby" earthquakes */
        private const val NEARBY_RADIUS_KM = 500.0
        /** Max distance for earthquake query to USGS */
        private const val QUERY_RADIUS_KM = 2000.0
        /** Min magnitude to query */
        private const val MIN_MAGNITUDE = 2.5
        /** Min magnitude for significant quakes */
        private const val SIGNIFICANT_MAGNITUDE = 5.0
        /** Days of earthquake history to query */
        private const val LOOKBACK_DAYS = 7L

        /** Tsunami risk thresholds */
        private const val TSUNAMI_MIN_MAGNITUDE = 6.5
        private const val TSUNAMI_SHALLOW_DEPTH_KM = 100.0
        private const val TSUNAMI_HIGH_MAGNITUDE = 7.5

        /** Wave warning thresholds (meters) */
        private const val WAVE_WARNING_MODERATE = 1.25
        private const val WAVE_WARNING_ROUGH = 2.5
        private const val WAVE_WARNING_VERY_ROUGH = 4.0
        private const val WAVE_WARNING_HIGH = 6.0
    }

    /**
     * Main entry point: Fetch all seismic/volcanic/maritime monitoring data.
     */
    suspend fun getSeismicMonitorData(
        latitude: Double,
        longitude: Double
    ): Result<SeismicMonitorData> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val startTime = formatISODate(now - TimeUnit.DAYS.toMillis(LOOKBACK_DAYS))

            // ── Parallel data fetching ──
            val earthquakeDeferred = async { fetchEarthquakes(latitude, longitude, startTime) }
            val significantDeferred = async { fetchSignificantEarthquakes(startTime) }
            val volcanoDeferred = async { fetchVolcanicActivity(latitude, longitude) }
            val waveDeferred = async { fetchHighWaveData(latitude, longitude) }

            val allEarthquakes = earthquakeDeferred.await()
            val significantEarthquakes = significantDeferred.await()
            val volcanoData = volcanoDeferred.await()
            val waveData = waveDeferred.await()

            // ── Process earthquakes ──
            val processedQuakes = allEarthquakes.map { feature ->
                mapToEarthquakeEvent(feature, latitude, longitude)
            }.sortedByDescending { it.time }

            val nearbyQuakes = processedQuakes.filter { it.distanceFromUserKm <= NEARBY_RADIUS_KM }
            val processedSignificant = significantEarthquakes.map { feature ->
                mapToEarthquakeEvent(feature, latitude, longitude)
            }.sortedByDescending { it.magnitude }

            // ── Assess tsunami risk ──
            val tsunamiRisk = assessTsunamiRisk(processedQuakes, latitude, longitude)

            // ── Process volcanic data ──
            val (volcanicEvents, nearbyVolcanoes) = volcanoData

            // ── Process wave warnings ──
            val highWaveWarning = waveData

            // ── Build impact areas ──
            val impactAreas = buildImpactAreas(
                nearbyQuakes, tsunamiRisk, volcanicEvents, latitude, longitude
            )

            val result = SeismicMonitorData(
                earthquakes = processedQuakes,
                nearbyEarthquakes = nearbyQuakes,
                significantEarthquakes = processedSignificant,
                tsunamiRisk = tsunamiRisk,
                volcanicActivity = volcanicEvents,
                nearbyVolcanoes = nearbyVolcanoes,
                highWaveWarning = highWaveWarning,
                impactAreas = impactAreas,
                lastUpdated = now,
                userLatitude = latitude,
                userLongitude = longitude
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  EARTHQUAKE FETCHING
    // ═══════════════════════════════════════════════════

    private suspend fun fetchEarthquakes(
        latitude: Double,
        longitude: Double,
        startTime: String
    ): List<com.weather.forecast.data.api.USGSFeature> {
        return try {
            val response = earthquakeApi.getEarthquakes(
                minMagnitude = MIN_MAGNITUDE,
                latitude = latitude,
                longitude = longitude,
                maxRadiusKm = QUERY_RADIUS_KM,
                startTime = startTime,
                limit = 100
            )
            response.features ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSignificantEarthquakes(
        startTime: String
    ): List<com.weather.forecast.data.api.USGSFeature> {
        return try {
            val response = earthquakeApi.getSignificantEarthquakes(
                minMagnitude = SIGNIFICANT_MAGNITUDE,
                startTime = startTime,
                limit = 20
            )
            response.features ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun mapToEarthquakeEvent(
        feature: com.weather.forecast.data.api.USGSFeature,
        userLat: Double,
        userLon: Double
    ): EarthquakeEvent {
        val props = feature.properties!!
        val geo = feature.geometry!!
        val lat = geo.coordinates?.getOrNull(1) ?: 0.0
        val lon = geo.coordinates?.getOrNull(0) ?: 0.0
        val depth = geo.coordinates?.getOrNull(2) ?: 0.0
        val distance = KnownVolcanoes.haversineDistance(userLat, userLon, lat, lon)
        val mag = props.mag ?: 0.0

        return EarthquakeEvent(
            id = feature.id ?: "",
            magnitude = mag,
            magnitudeType = props.magType ?: "M",
            place = props.place ?: "",
            time = props.time ?: 0L,
            latitude = lat,
            longitude = lon,
            depthKm = depth,
            distanceFromUserKm = distance,
            feltReports = props.felt ?: 0,
            tsunamiFlag = (props.tsunami ?: 0) > 0,
            significance = props.sig ?: 0,
            alertLevel = EarthquakeAlertLevel.fromString(props.alert),
            intensity = EarthquakeIntensity.fromMagnitudeDistance(mag, distance, depth),
            url = props.url ?: "",
            isReviewed = props.status == "reviewed"
        )
    }

    // ═══════════════════════════════════════════════════
    //  TSUNAMI RISK ASSESSMENT
    // ═══════════════════════════════════════════════════

    /**
     * Assess tsunami risk based on recent earthquake characteristics.
     *
     * Factors considered:
     * - Earthquake magnitude (>= 6.5 for danger)
     * - Depth (shallow = more dangerous)
     * - USGS tsunami flag
     * - Distance from coast (user proximity to coastline)
     * - Number of strong recent quakes (swarm activity)
     */
    private fun assessTsunamiRisk(
        earthquakes: List<EarthquakeEvent>,
        userLat: Double,
        userLon: Double
    ): TsunamiRiskAssessment {
        val strings = AppLocaleManager.strings

        // Find earthquake that could trigger tsunami
        val recentHours = 24L
        val recentCutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(recentHours)

        val potentialTsunamiQuakes = earthquakes.filter { eq ->
            eq.time >= recentCutoff &&
            eq.magnitude >= TSUNAMI_MIN_MAGNITUDE &&
            eq.depthKm <= TSUNAMI_SHALLOW_DEPTH_KM
        }

        val usgsMarkedTsunami = earthquakes.filter { it.tsunamiFlag && it.time >= recentCutoff }

        val triggerQuakes = (potentialTsunamiQuakes + usgsMarkedTsunami)
            .distinctBy { it.id }
            .sortedByDescending { it.magnitude }

        // Build risk factors
        val factors = mutableListOf<TsunamiRiskFactor>()

        // Determine risk level
        val riskLevel: TsunamiRiskLevel
        val description: String
        val recommendation: String
        var estimatedArrival: Int? = null

        if (triggerQuakes.isEmpty()) {
            riskLevel = TsunamiRiskLevel.NONE
            description = strings.tsunamiNoRisk
            recommendation = strings.tsunamiNoRiskAdvice
        } else {
            val strongestQuake = triggerQuakes.first()
            val mag = strongestQuake.magnitude
            val depth = strongestQuake.depthKm
            val dist = strongestQuake.distanceFromUserKm

            factors.add(TsunamiRiskFactor(
                strings.tsunamiFactorMagnitude, "M${"%.1f".format(mag)}", mag >= TSUNAMI_HIGH_MAGNITUDE
            ))
            factors.add(TsunamiRiskFactor(
                strings.tsunamiFactorDepth, "${"%.0f".format(depth)} km", depth <= 50
            ))
            factors.add(TsunamiRiskFactor(
                strings.tsunamiFactorDistance, "${"%.0f".format(dist)} km", dist <= 500
            ))
            factors.add(TsunamiRiskFactor(
                strings.tsunamiFactorUSGSFlag,
                if (strongestQuake.tsunamiFlag) "⚠️" else "✅",
                strongestQuake.tsunamiFlag
            ))

            // Estimate arrival time (rough: tsunami travels ~700-800 km/h)
            val tsunamiSpeed = 750.0 // km/h average in deep ocean
            estimatedArrival = ((dist / tsunamiSpeed) * 60).toInt().coerceAtLeast(5)

            when {
                mag >= 8.0 && depth <= 50 && strongestQuake.tsunamiFlag -> {
                    riskLevel = TsunamiRiskLevel.WARNING
                    description = strings.tsunamiWarningDesc
                    recommendation = strings.tsunamiWarningAdvice
                }
                mag >= TSUNAMI_HIGH_MAGNITUDE && depth <= TSUNAMI_SHALLOW_DEPTH_KM -> {
                    riskLevel = TsunamiRiskLevel.WATCH
                    description = strings.tsunamiWatchDesc
                    recommendation = strings.tsunamiWatchAdvice
                }
                mag >= TSUNAMI_MIN_MAGNITUDE || strongestQuake.tsunamiFlag -> {
                    riskLevel = TsunamiRiskLevel.ADVISORY
                    description = strings.tsunamiAdvisoryDesc
                    recommendation = strings.tsunamiAdvisoryAdvice
                }
                else -> {
                    riskLevel = TsunamiRiskLevel.INFORMATION
                    description = strings.tsunamiInfoDesc
                    recommendation = strings.tsunamiInfoAdvice
                }
            }
        }

        return TsunamiRiskAssessment(
            riskLevel = riskLevel,
            triggerEarthquakes = triggerQuakes,
            estimatedArrivalMinutes = estimatedArrival,
            nearestCoastDistanceKm = null, // Would require coast detection
            description = description,
            recommendation = recommendation,
            factors = factors
        )
    }

    // ═══════════════════════════════════════════════════
    //  VOLCANIC ACTIVITY
    // ═══════════════════════════════════════════════════

    /**
     * Fetch volcanic activity from multiple sources:
     * 1. USGS Volcano Alerts API
     * 2. NASA EONET volcanic events
     * 3. Local embedded database for known volcanoes nearby
     */
    private suspend fun fetchVolcanicActivity(
        latitude: Double,
        longitude: Double
    ): Pair<List<VolcanicEvent>, List<NearbyVolcano>> {
        val events = mutableListOf<VolcanicEvent>()

        // ── Source 1: USGS Volcano Alerts ──
        try {
            val alertResponse = volcanoApi.getVolcanoAlerts()
            alertResponse.features?.forEach { alert ->
                val data = alert.properties
                if (data != null) {
                    val lat = alert.geometry?.coordinates?.getOrNull(1) ?: 0.0
                    val lon = alert.geometry?.coordinates?.getOrNull(0) ?: 0.0
                    val dist = KnownVolcanoes.haversineDistance(latitude, longitude, lat, lon)

                    events.add(VolcanicEvent(
                        id = "usgs_${data.volcanoName?.hashCode() ?: 0}",
                        name = data.volcanoName ?: "",
                        latitude = lat,
                        longitude = lon,
                        elevation = 0,
                        country = "",
                        alertLevel = VolcanoAlertLevel.fromString(data.alertLevel),
                        colorCode = VolcanoColorCode.fromString(data.colorCode),
                        lastUpdate = try {
                            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                .parse(data.date ?: "")?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) { System.currentTimeMillis() },
                        distanceFromUserKm = dist,
                        description = data.message ?: "",
                        source = "USGS",
                        type = ""
                    ))
                }
            }
        } catch (e: Exception) {
            // USGS volcano API may fail; continue with other sources
        }

        // ── Source 2: NASA EONET volcanic events ──
        try {
            val eonetResponse = eonetApi.getEvents(
                category = "volcanoes",
                status = "open",
                limit = 30
            )
            eonetResponse.events?.forEach { event ->
                val geo = event.geometry?.lastOrNull()
                val lat = geo?.coordinates?.getOrNull(1) ?: 0.0
                val lon = geo?.coordinates?.getOrNull(0) ?: 0.0
                val dist = KnownVolcanoes.haversineDistance(latitude, longitude, lat, lon)

                // Avoid duplicates from USGS
                if (events.none { it.name.equals(event.title, ignoreCase = true) }) {
                    events.add(VolcanicEvent(
                        id = "eonet_${event.id}",
                        name = event.title ?: "",
                        latitude = lat,
                        longitude = lon,
                        elevation = 0,
                        country = "",
                        alertLevel = VolcanoAlertLevel.ADVISORY, // EONET "open" = at least advisory
                        colorCode = VolcanoColorCode.YELLOW,
                        lastUpdate = try {
                            Instant.parse(geo?.date ?: "").toEpochMilli()
                        } catch (e: Exception) { System.currentTimeMillis() },
                        distanceFromUserKm = dist,
                        description = event.description ?: "",
                        source = "NASA_EONET",
                        type = ""
                    ))
                }
            }
        } catch (e: Exception) {
            // EONET may fail; continue
        }

        // Sort by distance, then by alert level
        events.sortWith(compareBy<VolcanicEvent> { it.distanceFromUserKm }
            .thenByDescending { it.alertLevel.level })

        // ── Nearby known volcanoes from embedded DB ──
        val nearbyRecords = KnownVolcanoes.findNearby(latitude, longitude, 300.0)
        val nearbyVolcanoes = nearbyRecords.map { record ->
            val dist = KnownVolcanoes.haversineDistance(latitude, longitude, record.latitude, record.longitude)
            // Check if this volcano has active alerts
            val hasActiveAlert = events.any {
                it.name.contains(record.name, ignoreCase = true) ||
                (kotlin.math.abs(it.latitude - record.latitude) < 0.1 &&
                 kotlin.math.abs(it.longitude - record.longitude) < 0.1)
            }

            NearbyVolcano(
                name = record.name,
                latitude = record.latitude,
                longitude = record.longitude,
                elevation = record.elevation,
                country = record.country,
                type = record.type,
                lastEruption = record.lastEruption,
                distanceFromUserKm = dist,
                isActive = hasActiveAlert
            )
        }.sortedBy { it.distanceFromUserKm }

        return Pair(events, nearbyVolcanoes)
    }

    // ═══════════════════════════════════════════════════
    //  HIGH WAVE WARNING (Maritime Safety)
    // ═══════════════════════════════════════════════════

    /**
     * Fetch marine wave data and generate warnings for maritime safety.
     * Uses existing Open-Meteo Marine API.
     */
    private suspend fun fetchHighWaveData(
        latitude: Double,
        longitude: Double
    ): HighWaveWarning? {
        return try {
            val response = marineApi.getMarineData(
                latitude = latitude,
                longitude = longitude,
                hourly = "wave_height,swell_wave_height",
                daily = "wave_height_max,swell_wave_height_max",
                forecastDays = 3
            )

            val hourlyData = response.hourly
            val dailyData = response.daily

            if (hourlyData == null && dailyData == null) return null

            val hourlyTimes = hourlyData?.time ?: emptyList()
            val hourlyWaveHeights = hourlyData?.waveHeight ?: emptyList()
            val hourlySwellHeights = hourlyData?.swellWaveHeight ?: emptyList()

            if (hourlyWaveHeights.isEmpty()) return null

            // Current conditions (first entry or nearest to now)
            val currentWave = hourlyWaveHeights.firstOrNull() ?: 0.0
            val currentSwell = hourlySwellHeights.firstOrNull() ?: 0.0

            // Max forecast
            val maxWave = dailyData?.waveHeightMax?.filterNotNull()?.maxOrNull() ?: hourlyWaveHeights.filterNotNull().maxOrNull() ?: 0.0
            val maxWind = 0.0 // Wind data not available in Marine API
            val maxGust = 0.0

            // Find peak wave time
            val peakIndex = hourlyWaveHeights.withIndex().maxByOrNull { it.value ?: 0.0 }?.index
            val peakTime = if (peakIndex != null && peakIndex < hourlyTimes.size) hourlyTimes[peakIndex] else null

            // Build hourly forecast
            val hourlyForecast = hourlyTimes.mapIndexedNotNull { index, time ->
                val wh = hourlyWaveHeights.getOrNull(index) ?: return@mapIndexedNotNull null
                HourlyWaveForecast(
                    time = time ?: "",
                    waveHeight = wh,
                    swellHeight = hourlySwellHeights.getOrNull(index) ?: 0.0,
                    windSpeed = 0.0, // hourly wind not in marine API
                    gustSpeed = 0.0,
                    warningLevel = WaveWarningLevel.fromWaveHeight(wh)
                )
            }.take(72) // 3 days

            // Overall warning level
            val warningLevel = WaveWarningLevel.fromWaveHeight(maxWave)

            val strings = AppLocaleManager.strings
            val description = when (warningLevel) {
                WaveWarningLevel.HIGH -> strings.waveHighDesc
                WaveWarningLevel.VERY_ROUGH -> strings.waveVeryRoughDesc
                WaveWarningLevel.ROUGH -> strings.waveRoughDesc
                WaveWarningLevel.MODERATE -> strings.waveModerateDesc
                WaveWarningLevel.CALM -> strings.waveCalmDesc
            }
            val recommendation = when (warningLevel) {
                WaveWarningLevel.HIGH -> strings.waveHighAdvice
                WaveWarningLevel.VERY_ROUGH -> strings.waveVeryRoughAdvice
                WaveWarningLevel.ROUGH -> strings.waveRoughAdvice
                WaveWarningLevel.MODERATE -> strings.waveModerateAdvice
                WaveWarningLevel.CALM -> strings.waveCalmAdvice
            }

            HighWaveWarning(
                warningLevel = warningLevel,
                currentWaveHeight = currentWave,
                maxWaveHeightForecast = maxWave,
                currentSwellHeight = currentSwell,
                maxWindSpeed = maxWind,
                maxGustSpeed = maxGust,
                peakWaveTime = peakTime,
                description = description,
                recommendation = recommendation,
                hourlyForecast = hourlyForecast
            )
        } catch (e: Exception) {
            null // Marine data optional; don't fail the whole request
        }
    }

    // ═══════════════════════════════════════════════════
    //  IMPACT AREA CALCULATION
    // ═══════════════════════════════════════════════════

    /**
     * Build impact areas for interactive visualization.
     * Creates graduated zones for each significant event.
     */
    private fun buildImpactAreas(
        nearbyQuakes: List<EarthquakeEvent>,
        tsunamiRisk: TsunamiRiskAssessment,
        volcanicEvents: List<VolcanicEvent>,
        userLat: Double,
        userLon: Double
    ): List<DisasterImpactArea> {
        val areas = mutableListOf<DisasterImpactArea>()
        val strings = AppLocaleManager.strings

        // ── Earthquake impact areas (M >= 4.0 within 500km) ──
        nearbyQuakes.filter { it.magnitude >= 4.0 }.forEach { eq ->
            val severity = ImpactSeverity.fromMagnitude(eq.magnitude)
            val radius = eq.estimatedImpactRadiusKm
            val dist = eq.distanceFromUserKm

            val zones = listOf(
                ImpactZone(
                    label = "Severe",
                    labelId = "Parah",
                    radiusKm = radius * 0.3,
                    colorHex = 0xFFF44336,
                    alpha = 0.4f,
                    description = "Heavy damage zone",
                    descriptionId = "Zona kerusakan berat"
                ),
                ImpactZone(
                    label = "Moderate",
                    labelId = "Sedang",
                    radiusKm = radius * 0.6,
                    colorHex = 0xFFFF9800,
                    alpha = 0.3f,
                    description = "Moderate damage zone",
                    descriptionId = "Zona kerusakan sedang"
                ),
                ImpactZone(
                    label = "Light",
                    labelId = "Ringan",
                    radiusKm = radius,
                    colorHex = 0xFFFFC107,
                    alpha = 0.2f,
                    description = "Light damage / felt zone",
                    descriptionId = "Zona kerusakan ringan / terasa"
                )
            )

            areas.add(DisasterImpactArea(
                id = "eq_${eq.id}",
                type = ImpactAreaType.EARTHQUAKE,
                centerLatitude = eq.latitude,
                centerLongitude = eq.longitude,
                radiusKm = radius,
                severity = severity,
                title = "M${"%.1f".format(eq.magnitude)} ${eq.place}",
                description = "${strings.earthquakeDepth}: ${"%.1f".format(eq.depthKm)} km | " +
                    "${strings.distance}: ${"%.0f".format(dist)} km",
                timestamp = eq.time,
                source = "USGS",
                zones = zones,
                userInZone = dist <= radius,
                distanceFromUserKm = dist
            ))
        }

        // ── Tsunami impact areas ──
        if (tsunamiRisk.riskLevel >= TsunamiRiskLevel.ADVISORY) {
            tsunamiRisk.triggerEarthquakes.firstOrNull()?.let { eq ->
                val tsunamiRadius = when (tsunamiRisk.riskLevel) {
                    TsunamiRiskLevel.WARNING -> 1000.0
                    TsunamiRiskLevel.WATCH -> 500.0
                    TsunamiRiskLevel.ADVISORY -> 200.0
                    else -> 100.0
                }

                val zones = listOf(
                    ImpactZone(
                        label = "Danger",
                        labelId = "Berbahaya",
                        radiusKm = tsunamiRadius * 0.2,
                        colorHex = 0xFFD32F2F,
                        alpha = 0.5f,
                        description = "Immediate coastal danger",
                        descriptionId = "Bahaya pantai langsung"
                    ),
                    ImpactZone(
                        label = "Warning",
                        labelId = "Peringatan",
                        radiusKm = tsunamiRadius * 0.5,
                        colorHex = 0xFFFF9800,
                        alpha = 0.3f,
                        description = "Tsunami wave impact zone",
                        descriptionId = "Zona dampak gelombang tsunami"
                    ),
                    ImpactZone(
                        label = "Advisory",
                        labelId = "Waspada",
                        radiusKm = tsunamiRadius,
                        colorHex = 0xFFFFC107,
                        alpha = 0.2f,
                        description = "Extended advisory zone",
                        descriptionId = "Zona peringatan dini diperluas"
                    )
                )

                areas.add(DisasterImpactArea(
                    id = "tsunami_${eq.id}",
                    type = ImpactAreaType.TSUNAMI,
                    centerLatitude = eq.latitude,
                    centerLongitude = eq.longitude,
                    radiusKm = tsunamiRadius,
                    severity = when (tsunamiRisk.riskLevel) {
                        TsunamiRiskLevel.WARNING -> ImpactSeverity.CATASTROPHIC
                        TsunamiRiskLevel.WATCH -> ImpactSeverity.CRITICAL
                        else -> ImpactSeverity.SEVERE
                    },
                    title = "${strings.tsunamiRisk}: ${tsunamiRisk.riskLevel.label}",
                    description = strings.tsunamiTriggeredBy + " M${"%.1f".format(eq.magnitude)}",
                    timestamp = eq.time,
                    source = "USGS-derived",
                    zones = zones,
                    userInZone = eq.distanceFromUserKm <= tsunamiRadius,
                    distanceFromUserKm = eq.distanceFromUserKm
                ))
            }
        }

        // ── Volcanic impact areas ──
        volcanicEvents.filter { it.alertLevel >= VolcanoAlertLevel.ADVISORY }.forEach { volcano ->
            val radius = volcano.dangerZoneRadiusKm
            val dist = volcano.distanceFromUserKm

            val zones = listOf(
                ImpactZone(
                    label = "Exclusion",
                    labelId = "Zona Terlarang",
                    radiusKm = radius * 0.3,
                    colorHex = 0xFFD32F2F,
                    alpha = 0.5f,
                    description = "Pyroclastic flow / lava zone",
                    descriptionId = "Zona awan panas / lava"
                ),
                ImpactZone(
                    label = "Danger",
                    labelId = "Berbahaya",
                    radiusKm = radius * 0.6,
                    colorHex = 0xFFFF9800,
                    alpha = 0.3f,
                    description = "Lahar / heavy ashfall zone",
                    descriptionId = "Zona lahar / hujan abu tebal"
                ),
                ImpactZone(
                    label = "Alert",
                    labelId = "Siaga",
                    radiusKm = radius,
                    colorHex = 0xFFFFC107,
                    alpha = 0.2f,
                    description = "Ashfall / gas hazard zone",
                    descriptionId = "Zona hujan abu / gas beracun"
                )
            )

            areas.add(DisasterImpactArea(
                id = "volcano_${volcano.id}",
                type = ImpactAreaType.VOLCANIC_ERUPTION,
                centerLatitude = volcano.latitude,
                centerLongitude = volcano.longitude,
                radiusKm = radius,
                severity = ImpactSeverity.fromVolcanoAlert(volcano.alertLevel),
                title = volcano.name,
                description = "${volcano.alertLevel.label} - ${volcano.description.take(100)}",
                timestamp = volcano.lastUpdate,
                source = volcano.source,
                zones = zones,
                userInZone = dist <= radius,
                distanceFromUserKm = dist
            ))
        }

        return areas.sortedBy { it.distanceFromUserKm }
    }

    // ═══════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════

    private fun formatISODate(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val ldt = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"))
        return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}
