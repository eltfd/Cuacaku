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
    private val bmkgApi = RetrofitClient.bmkgApi
    private val petaBencanaApi = RetrofitClient.petaBencanaApi
    private val weatherApi = RetrofitClient.weatherApi
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
            val reliefDeferred = async { fetchReliefPoints(latitude, longitude) }
            val bmkgDeferred = async { fetchBmkgEarthquakes(latitude, longitude) }
            val petaBencanaDeferred = async { fetchPetaBencanaReports(latitude, longitude) }
            val landslideDeferred = async { fetchLandslideRiskData(latitude, longitude) }

            val allEarthquakes = earthquakeDeferred.await()
            val significantEarthquakes = significantDeferred.await()
            val volcanoData = volcanoDeferred.await()
            val waveData = waveDeferred.await()
            val reliefPoints = reliefDeferred.await()
            val bmkgEarthquakes = bmkgDeferred.await()
            val crowdsourcedReports = petaBencanaDeferred.await()
            val landslideBaseData = landslideDeferred.await()

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

            // ── Determine disaster lifecycle ──
            val lifecycleStates = determineLifecycleStates(
                nearbyQuakes, tsunamiRisk, volcanicEvents, impactAreas, reliefPoints
            )
            val overallPhase = determineOverallPhase(lifecycleStates, impactAreas, reliefPoints)

            // ── Finalize landslide risk with seismic data ──
            val landslideRisk = finalizeLandslideRisk(landslideBaseData, nearbyQuakes)

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
                userLongitude = longitude,
                overallPhase = overallPhase,
                lifecycleStates = lifecycleStates,
                reliefPoints = reliefPoints,
                bmkgEarthquakes = bmkgEarthquakes,
                crowdsourcedReports = crowdsourcedReports,
                landslideRisk = landslideRisk
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  DISASTER LIFECYCLE DETERMINATION
    // ═══════════════════════════════════════════════════

    /**
     * Determine the lifecycle phase for each disaster type.
     */
    private fun determineLifecycleStates(
        earthquakes: List<EarthquakeEvent>,
        tsunamiRisk: TsunamiRiskAssessment,
        volcanicEvents: List<VolcanicEvent>,
        impactAreas: List<DisasterImpactArea>,
        reliefPoints: List<ReliefPoint>
    ): List<DisasterLifecycleState> {
        val states = mutableListOf<DisasterLifecycleState>()
        val strings = AppLocaleManager.strings
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 60 * 60 * 1000L
        val oneDayAgo = now - 24 * 60 * 60 * 1000L

        // ── Earthquake Lifecycle ──
        val recentStrongQuakes = earthquakes.filter { it.magnitude >= 4.0 && it.time >= oneHourAgo }
        val olderStrongQuakes = earthquakes.filter { it.magnitude >= 4.0 && it.time in oneDayAgo..oneHourAgo }
        val quakeImpactAreas = impactAreas.filter { it.type == ImpactAreaType.EARTHQUAKE }

        val eqPhase = when {
            recentStrongQuakes.any { it.isNearby && it.magnitude >= 5.0 } -> DisasterLifecyclePhase.ACTIVE_DISASTER
            olderStrongQuakes.isNotEmpty() && reliefPoints.isNotEmpty() -> DisasterLifecyclePhase.POST_DISASTER
            earthquakes.any { it.magnitude >= 3.5 && it.isNearby } -> DisasterLifecyclePhase.EARLY_WARNING
            else -> DisasterLifecyclePhase.NORMAL
        }

        val eqEarlyWarning = if (eqPhase == DisasterLifecyclePhase.EARLY_WARNING) {
            val indicators = mutableListOf<WarningIndicator>()
            val recentCount = earthquakes.count { it.time >= oneDayAgo && it.isNearby }
            indicators.add(WarningIndicator(
                strings.localized("Nearby Quakes (24h)", "Gempa Terdekat (24j)"),
                "$recentCount", "≥ 3", recentCount >= 3,
                if (recentCount >= 5) EscalationTrend.RAPID_INCREASE else EscalationTrend.STABLE
            ))
            val maxMag = earthquakes.filter { it.isNearby }.maxByOrNull { it.magnitude }
            maxMag?.let {
                indicators.add(WarningIndicator(
                    strings.localized("Max Magnitude", "Mag. Maksimum"),
                    "M${"%.1f".format(it.magnitude)}", "≥ M5.0", it.magnitude >= 5.0,
                    EscalationTrend.STABLE
                ))
            }
            EarlyWarningData(
                threatLevel = if (recentCount >= 5) EarlyWarningLevel.WATCH else EarlyWarningLevel.ADVISORY,
                indicators = indicators,
                preparednessChecklist = getEarthquakePreparedness(),
                escalationTrend = if (recentCount >= 5) EscalationTrend.INCREASING else EscalationTrend.STABLE
            )
        } else null

        val eqActiveInfo = if (eqPhase == DisasterLifecyclePhase.ACTIVE_DISASTER) {
            val strongestRecent = recentStrongQuakes.maxByOrNull { it.magnitude }
            ActiveDisasterInfo(
                severity = ImpactSeverity.fromMagnitude(strongestRecent?.magnitude ?: 0.0),
                impactArea = quakeImpactAreas.firstOrNull(),
                startTime = strongestRecent?.time ?: now,
                isUserInDangerZone = quakeImpactAreas.any { it.userInZone },
                emergencyContacts = EmergencyContacts.getForLocale()
            )
        } else null

        val eqPostInfo = if (eqPhase == DisasterLifecyclePhase.POST_DISASTER) {
            PostDisasterInfo(
                reliefPoints = reliefPoints,
                recentReports = emptyList(),
                recoveryStatus = strings.localized(
                    "Recovery phase — Check nearby relief points",
                    "Fase pemulihan — Cek posko bantuan terdekat"
                ),
                lastUpdated = now
            )
        } else null

        states.add(DisasterLifecycleState(
            type = DisasterLifecycleType.EARTHQUAKE,
            phase = eqPhase,
            summary = when (eqPhase) {
                DisasterLifecyclePhase.NORMAL -> strings.localized("No significant seismic activity", "Tidak ada aktivitas seismik signifikan")
                DisasterLifecyclePhase.EARLY_WARNING -> strings.localized("Increased seismic activity detected", "Aktivitas seismik meningkat terdeteksi")
                DisasterLifecyclePhase.ACTIVE_DISASTER -> strings.localized("Active earthquake — Take cover!", "Gempa aktif — Berlindung!")
                DisasterLifecyclePhase.POST_DISASTER -> strings.localized("Post-earthquake recovery phase", "Fase pemulihan pascagempa")
            },
            details = when (eqPhase) {
                DisasterLifecyclePhase.ACTIVE_DISASTER -> {
                    val strongest = recentStrongQuakes.maxByOrNull { it.magnitude }
                    strings.localized(
                        "M${"%.1f".format(strongest?.magnitude ?: 0.0)} earthquake detected ${strongest?.let { "${"%.0f".format(it.distanceFromUserKm)} km away" } ?: "nearby"}",
                        "Gempa M${"%.1f".format(strongest?.magnitude ?: 0.0)} terdeteksi ${strongest?.let { "${"%.0f".format(it.distanceFromUserKm)} km dari lokasi Anda" } ?: "di sekitar"}"
                    )
                }
                else -> ""
            },
            earlyWarning = eqEarlyWarning,
            activeDisasterInfo = eqActiveInfo,
            postDisasterInfo = eqPostInfo
        ))

        // ── Tsunami Lifecycle ──
        val tsunamiPhase = when {
            tsunamiRisk.riskLevel >= TsunamiRiskLevel.WARNING -> DisasterLifecyclePhase.ACTIVE_DISASTER
            tsunamiRisk.riskLevel >= TsunamiRiskLevel.ADVISORY -> DisasterLifecyclePhase.EARLY_WARNING
            else -> DisasterLifecyclePhase.NORMAL
        }

        val tsunamiEarlyWarning = if (tsunamiPhase == DisasterLifecyclePhase.EARLY_WARNING) {
            val indicators = tsunamiRisk.factors.map { f ->
                WarningIndicator(f.name, f.value, "-", f.isElevating, EscalationTrend.STABLE)
            }
            EarlyWarningData(
                threatLevel = EarlyWarningLevel.WATCH,
                indicators = indicators,
                preparednessChecklist = getTsunamiPreparedness(),
                estimatedOnsetHours = tsunamiRisk.estimatedArrivalMinutes?.let { it / 60 },
                escalationTrend = EscalationTrend.INCREASING
            )
        } else null

        val tsunamiActiveInfo = if (tsunamiPhase == DisasterLifecyclePhase.ACTIVE_DISASTER) {
            val tsunamiAreas = impactAreas.filter { it.type == ImpactAreaType.TSUNAMI }
            ActiveDisasterInfo(
                severity = ImpactSeverity.CATASTROPHIC,
                impactArea = tsunamiAreas.firstOrNull(),
                startTime = tsunamiRisk.triggerEarthquakes.firstOrNull()?.time ?: now,
                isUserInDangerZone = tsunamiAreas.any { it.userInZone },
                evacuationDirections = listOf(
                    EvacuationDirection(
                        "Inland / Higher Ground", "Ke Daratan / Dataran Tinggi",
                        0.0,
                        "Move immediately to higher ground (>30m above sea level)",
                        "Segera menuju dataran tinggi (>30m di atas permukaan laut)"
                    )
                ),
                emergencyContacts = EmergencyContacts.getForLocale()
            )
        } else null

        states.add(DisasterLifecycleState(
            type = DisasterLifecycleType.TSUNAMI,
            phase = tsunamiPhase,
            summary = when (tsunamiPhase) {
                DisasterLifecyclePhase.NORMAL -> strings.localized("No tsunami risk", "Tidak ada risiko tsunami")
                DisasterLifecyclePhase.EARLY_WARNING -> strings.localized("Tsunami advisory active", "Peringatan dini tsunami aktif")
                DisasterLifecyclePhase.ACTIVE_DISASTER -> strings.localized("TSUNAMI WARNING — Evacuate NOW!", "PERINGATAN TSUNAMI — EVAKUASI SEKARANG!")
                DisasterLifecyclePhase.POST_DISASTER -> strings.localized("Post-tsunami recovery", "Pemulihan pascatsunami")
            },
            details = tsunamiRisk.description,
            earlyWarning = tsunamiEarlyWarning,
            activeDisasterInfo = tsunamiActiveInfo
        ))

        // ── Volcano Lifecycle ──
        val nearbyActiveVolcanoes = volcanicEvents.filter { it.isNearby }
        val volcanoPhase = when {
            nearbyActiveVolcanoes.any { it.alertLevel >= VolcanoAlertLevel.WARNING } -> DisasterLifecyclePhase.ACTIVE_DISASTER
            nearbyActiveVolcanoes.any { it.alertLevel >= VolcanoAlertLevel.ADVISORY } -> DisasterLifecyclePhase.EARLY_WARNING
            else -> DisasterLifecyclePhase.NORMAL
        }

        val volcanoEarlyWarning = if (volcanoPhase == DisasterLifecyclePhase.EARLY_WARNING) {
            val activeVolcano = nearbyActiveVolcanoes.maxByOrNull { it.alertLevel.level }
            EarlyWarningData(
                threatLevel = EarlyWarningLevel.ADVISORY,
                indicators = listOf(
                    WarningIndicator(
                        strings.localized("Alert Level", "Level Peringatan"),
                        activeVolcano?.alertLevel?.label ?: "-",
                        "WARNING", activeVolcano?.alertLevel == VolcanoAlertLevel.WARNING,
                        EscalationTrend.STABLE
                    ),
                    WarningIndicator(
                        strings.localized("Distance", "Jarak"),
                        "${"%.0f".format(activeVolcano?.distanceFromUserKm ?: 0.0)} km",
                        "< 50 km", (activeVolcano?.distanceFromUserKm ?: 999.0) < 50.0,
                        EscalationTrend.STABLE
                    )
                ),
                preparednessChecklist = getVolcanoPreparedness(),
                escalationTrend = EscalationTrend.STABLE
            )
        } else null

        val volcanoActiveInfo = if (volcanoPhase == DisasterLifecyclePhase.ACTIVE_DISASTER) {
            val erupting = nearbyActiveVolcanoes.filter { it.alertLevel >= VolcanoAlertLevel.WARNING }
            val volcanoAreas = impactAreas.filter { it.type == ImpactAreaType.VOLCANIC_ERUPTION }
            ActiveDisasterInfo(
                severity = ImpactSeverity.CRITICAL,
                impactArea = volcanoAreas.firstOrNull(),
                startTime = erupting.firstOrNull()?.lastUpdate ?: now,
                isUserInDangerZone = volcanoAreas.any { it.userInZone },
                emergencyContacts = EmergencyContacts.getForLocale()
            )
        } else null

        states.add(DisasterLifecycleState(
            type = DisasterLifecycleType.VOLCANO,
            phase = volcanoPhase,
            summary = when (volcanoPhase) {
                DisasterLifecyclePhase.NORMAL -> strings.localized("No volcanic threats", "Tidak ada ancaman vulkanik")
                DisasterLifecyclePhase.EARLY_WARNING -> strings.localized("Volcanic activity increasing", "Aktivitas vulkanik meningkat")
                DisasterLifecyclePhase.ACTIVE_DISASTER -> strings.localized("ERUPTION IN PROGRESS — Evacuate!", "ERUPSI BERLANGSUNG — Evakuasi!")
                DisasterLifecyclePhase.POST_DISASTER -> strings.localized("Post-eruption monitoring", "Pemantauan pascaerupsi")
            },
            details = nearbyActiveVolcanoes.firstOrNull()?.description ?: "",
            earlyWarning = volcanoEarlyWarning,
            activeDisasterInfo = volcanoActiveInfo
        ))

        return states
    }

    /**
     * Determine the overall disaster phase (highest severity wins).
     */
    private fun determineOverallPhase(
        lifecycleStates: List<DisasterLifecycleState>,
        impactAreas: List<DisasterImpactArea>,
        reliefPoints: List<ReliefPoint>
    ): DisasterLifecyclePhase {
        // Check for active disasters first
        if (lifecycleStates.any { it.phase == DisasterLifecyclePhase.ACTIVE_DISASTER }) {
            return DisasterLifecyclePhase.ACTIVE_DISASTER
        }
        // If no active but we have relief points and past impact areas
        if (reliefPoints.isNotEmpty() && lifecycleStates.any { it.phase == DisasterLifecyclePhase.POST_DISASTER }) {
            return DisasterLifecyclePhase.POST_DISASTER
        }
        // Early warning
        if (lifecycleStates.any { it.phase == DisasterLifecyclePhase.EARLY_WARNING }) {
            return DisasterLifecyclePhase.EARLY_WARNING
        }
        return DisasterLifecyclePhase.NORMAL
    }

    // ═══════════════════════════════════════════════════
    //  RELIEF POINTS (Post-Disaster)
    // ═══════════════════════════════════════════════════

    /**
     * Fetch relief/evacuation points from ReliefWeb disaster reports.
     * Falls back to known emergency POIs if API unavailable.
     */
    private suspend fun fetchReliefPoints(
        latitude: Double,
        longitude: Double
    ): List<ReliefPoint> {
        val points = mutableListOf<ReliefPoint>()

        try {
            // Determine country ISO3 from coordinates (simplified)
            val countryIso3 = estimateCountryISO3(latitude, longitude)

            // Fetch ongoing disasters for the country
            val disasters = RetrofitClient.reliefWebApi.getDisastersByCountry(
                filterValue1 = countryIso3
            )

            disasters.data?.forEach { disaster ->
                val fields = disaster.fields ?: return@forEach
                val disasterId = disaster.id ?: return@forEach
                val disasterName = fields.name ?: ""

                // For each active disaster, create a relief point entry
                // ReliefWeb doesn't provide exact coordinates for relief posts,
                // but we can use the disaster info as reference points
                if (fields.status == "ongoing") {
                    points.add(ReliefPoint(
                        id = "rw_$disasterId",
                        name = disasterName,
                        type = ReliefPointType.COMMAND_CENTER,
                        latitude = latitude, // Approximate to user area
                        longitude = longitude,
                        distanceFromUserKm = 0.0,
                        address = fields.country?.firstOrNull()?.name ?: "",
                        description = fields.description ?: disasterName,
                        source = "ReliefWeb",
                        isVerified = true,
                        lastUpdated = System.currentTimeMillis()
                    ))
                }
            }
        } catch (_: Exception) {
            // ReliefWeb may fail — continue without relief data
        }

        // Add standard emergency locations based on known infrastructure
        if (points.isNotEmpty() || hasRecentLocalDisaster()) {
            points.addAll(getStandardReliefPoints(latitude, longitude))
        }

        return points.sortedBy { it.distanceFromUserKm }
    }

    /**
     * Provide standard relief point types when active disaster is detected.
     * These are general guidance points, not exact locations.
     */
    private fun getStandardReliefPoints(lat: Double, lon: Double): List<ReliefPoint> {
        val strings = AppLocaleManager.strings
        return listOf(
            ReliefPoint(
                id = "std_emergency",
                name = strings.localized("Emergency Services (112)", "Layanan Darurat (112)"),
                type = ReliefPointType.COMMAND_CENTER,
                latitude = lat, longitude = lon,
                distanceFromUserKm = 0.0,
                address = strings.localized("Call 112 for emergency", "Hubungi 112 untuk darurat"),
                description = strings.localized(
                    "National emergency number — connects to nearest BNPB command center",
                    "Nomor darurat nasional — terhubung ke posko BNPB terdekat"
                ),
                source = "System", isVerified = true
            ),
            ReliefPoint(
                id = "std_sar",
                name = strings.localized("BASARNAS SAR (115)", "BASARNAS SAR (115)"),
                type = ReliefPointType.SEARCH_RESCUE,
                latitude = lat, longitude = lon,
                distanceFromUserKm = 0.0,
                address = strings.localized("Call 115 for search & rescue", "Hubungi 115 untuk SAR"),
                description = strings.localized(
                    "National Search and Rescue Agency",
                    "Badan Nasional Pencarian dan Pertolongan"
                ),
                source = "System", isVerified = true
            ),
            ReliefPoint(
                id = "std_medical",
                name = strings.localized("Medical Emergency (118/119)", "Darurat Medis (118/119)"),
                type = ReliefPointType.MEDICAL_POST,
                latitude = lat, longitude = lon,
                distanceFromUserKm = 0.0,
                address = strings.localized("Call 118 or 119 for ambulance", "Hubungi 118/119 untuk ambulans"),
                description = strings.localized(
                    "Emergency medical services and nearest hospital",
                    "Layanan medis darurat dan rumah sakit terdekat"
                ),
                source = "System", isVerified = true
            )
        )
    }

    private fun hasRecentLocalDisaster(): Boolean {
        // Check if there's cached data indicating recent disaster
        return prefs.getLong(KEY_LAST_EARTHQUAKE_FETCH, 0L) > 0
    }

    /**
     * Simple country ISO3 estimation from coordinates.
     * Covers major disaster-prone regions.
     */
    private fun estimateCountryISO3(lat: Double, lon: Double): String {
        return when {
            // Indonesia
            lat in -11.0..6.0 && lon in 95.0..141.0 -> "IDN"
            // Philippines
            lat in 4.5..21.0 && lon in 116.0..127.0 -> "PHL"
            // Japan
            lat in 24.0..46.0 && lon in 122.0..146.0 -> "JPN"
            // Papua New Guinea
            lat in -12.0..0.0 && lon in 141.0..160.0 -> "PNG"
            // India
            lat in 6.0..36.0 && lon in 68.0..97.0 -> "IND"
            // Bangladesh
            lat in 20.0..27.0 && lon in 88.0..93.0 -> "BGD"
            // Myanmar
            lat in 9.0..29.0 && lon in 92.0..102.0 -> "MMR"
            // Thailand
            lat in 5.0..21.0 && lon in 97.0..106.0 -> "THA"
            // USA
            lat in 24.0..50.0 && lon in -125.0..-66.0 -> "USA"
            // Chile
            lat in -56.0..-17.0 && lon in -76.0..-66.0 -> "CHL"
            // Mexico
            lat in 14.0..33.0 && lon in -118.0..-86.0 -> "MEX"
            // Turkey
            lat in 36.0..42.0 && lon in 26.0..45.0 -> "TUR"
            else -> "IDN" // Default to Indonesia
        }
    }

    // ═══════════════════════════════════════════════════
    //  PREPAREDNESS CHECKLISTS
    // ═══════════════════════════════════════════════════

    private fun getEarthquakePreparedness(): List<PreparednessItem> {
        val strings = AppLocaleManager.strings
        return listOf(
            PreparednessItem(strings.localized("Secure heavy furniture", "Amankan furnitur berat"), "🪑", true),
            PreparednessItem(strings.localized("Prepare emergency kit", "Siapkan tas darurat"), "🎒", true),
            PreparednessItem(strings.localized("Know your evacuation route", "Kenali rute evakuasi"), "🗺️", true),
            PreparednessItem(strings.localized("Keep flashlight ready", "Siapkan senter"), "🔦"),
            PreparednessItem(strings.localized("Store water & food (3 days)", "Simpan air & makanan (3 hari)"), "💧"),
            PreparednessItem(strings.localized("Charge phone & power banks", "Cas HP & power bank"), "🔋"),
            PreparednessItem(strings.localized("Identify safe spots (under table, doorframe)", "Identifikasi titik aman (kolong meja, kusen pintu)"), "🏠")
        )
    }

    private fun getTsunamiPreparedness(): List<PreparednessItem> {
        val strings = AppLocaleManager.strings
        return listOf(
            PreparednessItem(strings.localized("Know nearest high ground", "Kenali dataran tinggi terdekat"), "⛰️", true),
            PreparednessItem(strings.localized("Move away from coast immediately", "Segera jauhi pesisir"), "🏃", true),
            PreparednessItem(strings.localized("Go to elevation >30m above sea level", "Pergi ke ketinggian >30m dpl"), "📐", true),
            PreparednessItem(strings.localized("Don't return until all-clear", "Jangan kembali sampai situasi aman"), "⚠️"),
            PreparednessItem(strings.localized("Prepare emergency supplies", "Siapkan perlengkapan darurat"), "🎒"),
            PreparednessItem(strings.localized("Alert family & neighbors", "Beritahu keluarga & tetangga"), "📢")
        )
    }

    private fun getVolcanoPreparedness(): List<PreparednessItem> {
        val strings = AppLocaleManager.strings
        return listOf(
            PreparednessItem(strings.localized("Prepare face mask/respirator", "Siapkan masker/respirator"), "😷", true),
            PreparednessItem(strings.localized("Know eruption evacuation route", "Kenali rute evakuasi erupsi"), "🗺️", true),
            PreparednessItem(strings.localized("Stay away from river valleys", "Jauhi lembah sungai"), "🏞️", true),
            PreparednessItem(strings.localized("Prepare goggles for ash", "Siapkan kacamata pelindung"), "🥽"),
            PreparednessItem(strings.localized("Cover water sources", "Tutup sumber air"), "💧"),
            PreparednessItem(strings.localized("Keep windows & doors closed", "Tutup jendela & pintu"), "🪟")
        )
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
    //  BMKG EARTHQUAKE DATA (Indonesian Source)
    // ═══════════════════════════════════════════════════

    /**
     * Fetch earthquake data from BMKG (Indonesian Met Agency).
     * Combines autogempa (latest) + gempaterkini (15 recent M5.0+).
     * Preferred for Indonesian users due to higher accuracy for local quakes.
     */
    private suspend fun fetchBmkgEarthquakes(
        userLat: Double,
        userLon: Double
    ): List<BmkgEarthquakeEvent> {
        val events = mutableListOf<BmkgEarthquakeEvent>()

        try {
            // Fetch both endpoints in parallel
            val autoResult = try { bmkgApi.getAutoGempa() } catch (_: Exception) { null }
            val terkiniResult = try { bmkgApi.getGempaTerkini() } catch (_: Exception) { null }

            // Process autogempa (latest single event with full detail)
            autoResult?.Infogempa?.gempa?.let { gempa ->
                val lat = gempa.parsedLatitude
                val lon = gempa.parsedLongitude
                val dist = KnownVolcanoes.haversineDistance(userLat, userLon, lat, lon)
                events.add(BmkgEarthquakeEvent(
                    magnitude = gempa.parsedMagnitude,
                    latitude = lat,
                    longitude = lon,
                    depthKm = gempa.parsedDepthKm,
                    time = gempa.parsedTimeMillis,
                    dateString = gempa.Tanggal ?: "",
                    timeString = gempa.Jam ?: "",
                    region = gempa.Wilayah ?: "",
                    potential = gempa.Potensi ?: "",
                    feltReport = gempa.Dirasakan,
                    shakemapUrl = gempa.shakemapUrl,
                    distanceFromUserKm = dist
                ))
            }

            // Process gempaterkini (15 recent M5.0+ quakes)
            terkiniResult?.Infogempa?.gempa?.forEach { gempa ->
                val lat = gempa.parsedLatitude
                val lon = gempa.parsedLongitude
                val dist = KnownVolcanoes.haversineDistance(userLat, userLon, lat, lon)

                // Avoid duplicate with autogempa
                if (events.none { it.time == gempa.parsedTimeMillis && it.magnitude == gempa.parsedMagnitude }) {
                    events.add(BmkgEarthquakeEvent(
                        magnitude = gempa.parsedMagnitude,
                        latitude = lat,
                        longitude = lon,
                        depthKm = gempa.parsedDepthKm,
                        time = gempa.parsedTimeMillis,
                        dateString = gempa.Tanggal ?: "",
                        timeString = gempa.Jam ?: "",
                        region = gempa.Wilayah ?: "",
                        potential = gempa.Potensi ?: "",
                        feltReport = null,
                        shakemapUrl = null,
                        distanceFromUserKm = dist
                    ))
                }
            }
        } catch (_: Exception) {
            // BMKG may be unreachable; continue with USGS data
        }

        return events.sortedByDescending { it.time }
    }

    // ═══════════════════════════════════════════════════
    //  PETABENCANA.ID CROWDSOURCED REPORTS
    // ═══════════════════════════════════════════════════

    /**
     * Fetch crowdsourced disaster reports from PetaBencana.id.
     * Returns last 7 days of confirmed reports filtered to non-training data.
     */
    private suspend fun fetchPetaBencanaReports(
        userLat: Double,
        userLon: Double
    ): List<CrowdsourcedDisasterReport> {
        val reports = mutableListOf<CrowdsourcedDisasterReport>()

        try {
            val response = petaBencanaApi.getReports(
                timeperiod = 604800 // 7 days
            )

            response.result?.objects?.output?.geometries?.forEach { geometry ->
                val props = geometry.properties ?: return@forEach
                val lat = geometry.latitude
                val lon = geometry.longitude

                // Skip training/test data  
                if (props.is_training == true) return@forEach

                val dist = KnownVolcanoes.haversineDistance(userLat, userLon, lat, lon)

                reports.add(CrowdsourcedDisasterReport(
                    id = props.pkey ?: "",
                    disasterType = CrowdsourcedDisasterType.fromApiKey(props.disaster_type),
                    latitude = lat,
                    longitude = lon,
                    time = props.parsedTimeMillis,
                    text = props.text?.trim() ?: "",
                    imageUrl = props.image_url,
                    cityName = props.tags?.city,
                    provinceCode = props.tags?.instance_region_code,
                    distanceFromUserKm = dist,
                    isTraining = props.is_training ?: false,
                    floodDepthCm = props.report_data?.flood_depth,
                    structureDamage = props.report_data?.structureFailure,
                    windImpact = props.report_data?.impact,
                    evacuationArea = props.report_data?.evacuationArea,
                    evacuationNumber = props.report_data?.evacuationNumber,
                    volcanicSigns = props.report_data?.volcanicSigns,
                    accessibilityFailure = props.report_data?.accessabilityFailure,
                    roadCondition = props.report_data?.condition,
                    severityPoints = props.report_data?.points
                ))
            }
        } catch (_: Exception) {
            // PetaBencana may be unreachable; continue without crowdsourced data
        }

        return reports
            .filter { it.isReal }
            .sortedByDescending { it.time }
    }

    // ═══════════════════════════════════════════════════
    //  LANDSLIDE RISK ASSESSMENT
    // ═══════════════════════════════════════════════════

    /**
     * Intermediate data from weather API (before combining with seismic data).
     */
    private data class LandslideWeatherData(
        val currentRainRate: Double,
        val rainfall24h: Double,
        val rainfall72h: Double,
        val rainfallForecast24h: Double,
        val soilMoistureSurface: Double,
        val soilMoistureMiddle: Double,
        val soilMoistureDeep: Double,
        val hourlyRainForecast: List<HourlyRainForecast>,
        val rainfallScore: Int,
        val soilMoistureScore: Int,
        val forecastScore: Int
    )

    /**
     * Fetch rainfall + soil moisture from Open-Meteo weather API
     * for landslide risk calculation.
     */
    private suspend fun fetchLandslideRiskData(
        latitude: Double,
        longitude: Double
    ): LandslideWeatherData? {
        return try {
            val response = weatherApi.getWeather(
                latitude = latitude,
                longitude = longitude,
                forecastDays = 7
            )

            val hourly = response.hourlyForecast ?: return null
            val daily = response.dailyForecast

            val hourlyRain = hourly.rain
            val hourlyPrecipProb = hourly.precipitationProbability
            val soilSurface = hourly.soilMoisture0to7 ?: emptyList()
            val soilMiddle = hourly.soilMoisture7to28 ?: emptyList()
            val soilDeep = hourly.soilMoisture28to100 ?: emptyList()
            val hourlyTimes = hourly.time

            // Current conditions (index 0 = now/closest hour)
            val currentRainRate = hourlyRain.getOrElse(0) { 0.0 }

            // Past 24h rainfall (last 24 indices of historical data)
            // Open-Meteo provides data from start of today; so indices 0-23 = today's hours
            val rainfall24h = hourlyRain.take(24).sum()
            val rainfall72h = daily?.rainSum?.take(3)?.sum() ?: rainfall24h

            // Future 24h rainfall forecast (indices 24-47)
            val rainfallForecast24h = hourlyRain.drop(24).take(24).sum()

            // Current soil moisture
            val currentSoilSurface = soilSurface.getOrElse(0) { 0.0 }
            val currentSoilMiddle = soilMiddle.getOrElse(0) { 0.0 }
            val currentSoilDeep = soilDeep.getOrElse(0) { 0.0 }

            // Build hourly rain forecast (next 24h)
            val hourlyForecast = (24 until minOf(48, hourlyTimes.size)).mapNotNull { i ->
                val time = hourlyTimes.getOrNull(i) ?: return@mapNotNull null
                val rain = hourlyRain.getOrElse(i) { 0.0 }
                val prob = hourlyPrecipProb.getOrElse(i) { 0 }
                HourlyRainForecast(time = time, rain = rain, probability = prob)
            }

            // ── Calculate rainfall score (0-40) ──
            var rainfallScore = 0
            // Current rain rate
            rainfallScore += when {
                currentRainRate >= 20.0 -> 15   // Very heavy rain
                currentRainRate >= 10.0 -> 10   // Heavy rain
                currentRainRate >= 5.0  -> 6    // Moderate rain
                currentRainRate > 0.5   -> 3    // Light rain
                else -> 0
            }
            // 24h accumulation
            rainfallScore += when {
                rainfall24h >= 200.0 -> 15      // Extreme
                rainfall24h >= 100.0 -> 12      // Very heavy
                rainfall24h >= 50.0  -> 8       // Heavy
                rainfall24h >= 20.0  -> 4       // Moderate
                else -> 0
            }
            // 72h accumulation bonus
            rainfallScore += when {
                rainfall72h >= 300.0 -> 10
                rainfall72h >= 150.0 -> 6
                rainfall72h >= 75.0  -> 3
                else -> 0
            }
            rainfallScore = rainfallScore.coerceAtMost(40)

            // ── Calculate soil moisture score (0-30) ──
            // Typical saturation point ~0.45-0.5 m³/m³
            val avgMoisture = listOf(currentSoilSurface, currentSoilMiddle, currentSoilDeep)
                .filter { v -> v > 0 }.let { list -> if (list.isEmpty()) listOf(0.0) else list }.average()

            var soilScore = 0
            soilScore += when {
                avgMoisture >= 0.45 -> 15       // Near saturation
                avgMoisture >= 0.35 -> 10       // Very wet
                avgMoisture >= 0.25 -> 5        // Moist
                else -> 0
            }
            // Deep soil saturation is especially dangerous
            soilScore += when {
                currentSoilDeep >= 0.45 -> 15   // Deep saturation
                currentSoilDeep >= 0.35 -> 8    // Deep moisture high
                currentSoilDeep >= 0.25 -> 3
                else -> 0
            }
            soilScore = soilScore.coerceAtMost(30)

            // ── Calculate forecast score (0-10) ──
            var forecastScore = 0
            forecastScore += when {
                rainfallForecast24h >= 100.0 -> 10
                rainfallForecast24h >= 50.0  -> 7
                rainfallForecast24h >= 20.0  -> 4
                rainfallForecast24h >= 10.0  -> 2
                else -> 0
            }
            forecastScore = forecastScore.coerceAtMost(10)

            LandslideWeatherData(
                currentRainRate = currentRainRate,
                rainfall24h = rainfall24h,
                rainfall72h = rainfall72h,
                rainfallForecast24h = rainfallForecast24h,
                soilMoistureSurface = currentSoilSurface,
                soilMoistureMiddle = currentSoilMiddle,
                soilMoistureDeep = currentSoilDeep,
                hourlyRainForecast = hourlyForecast,
                rainfallScore = rainfallScore,
                soilMoistureScore = soilScore,
                forecastScore = forecastScore
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Combine weather data with seismic data to produce final landslide risk assessment.
     */
    private fun finalizeLandslideRisk(
        weatherData: LandslideWeatherData?,
        nearbyQuakes: List<EarthquakeEvent>
    ): LandslideRiskAssessment? {
        val data = weatherData ?: return null

        // ── Seismic trigger score (0-20) ──
        val recentNearbyQuakes = nearbyQuakes.filter {
            it.distanceFromUserKm <= 100.0
        }
        val maxMagnitude = recentNearbyQuakes.maxByOrNull { it.magnitude }?.magnitude ?: 0.0

        var seismicScore = 0
        seismicScore += when {
            maxMagnitude >= 6.0 -> 15
            maxMagnitude >= 5.0 -> 10
            maxMagnitude >= 4.0 -> 6
            maxMagnitude >= 3.0 -> 3
            else -> 0
        }
        seismicScore += when {
            recentNearbyQuakes.size >= 10 -> 5
            recentNearbyQuakes.size >= 5  -> 3
            recentNearbyQuakes.size >= 2  -> 1
            else -> 0
        }
        seismicScore = seismicScore.coerceAtMost(20)

        // ── Total risk score ──
        val totalScore = (data.rainfallScore + data.soilMoistureScore +
                seismicScore + data.forecastScore).coerceIn(0, 100)
        val riskLevel = LandslideRiskLevel.fromScore(totalScore)

        // ── Build risk factors ──
        val factors = mutableListOf<LandslideRiskFactor>()

        factors.add(LandslideRiskFactor(
            name = "Rainfall Intensity",
            nameId = "Intensitas Hujan",
            score = data.rainfallScore,
            maxScore = 40,
            description = "Current: ${"%.1f".format(data.currentRainRate)} mm/h, 24h: ${"%.0f".format(data.rainfall24h)} mm, 72h: ${"%.0f".format(data.rainfall72h)} mm",
            descriptionId = "Saat ini: ${"%.1f".format(data.currentRainRate)} mm/jam, 24 jam: ${"%.0f".format(data.rainfall24h)} mm, 72 jam: ${"%.0f".format(data.rainfall72h)} mm"
        ))

        // Soil moisture saturation percent
        val saturationPct = (listOf(data.soilMoistureSurface, data.soilMoistureMiddle, data.soilMoistureDeep)
            .filter { v -> v > 0 }.let { list -> if (list.isEmpty()) listOf(0.0) else list }.average() / 0.5 * 100).coerceIn(0.0, 100.0)

        factors.add(LandslideRiskFactor(
            name = "Soil Saturation",
            nameId = "Kelembaban Tanah",
            score = data.soilMoistureScore,
            maxScore = 30,
            description = "Saturation: ${"%.0f".format(saturationPct)}% (Surface: ${"%.2f".format(data.soilMoistureSurface)}, Deep: ${"%.2f".format(data.soilMoistureDeep)} m\u00b3/m\u00b3)",
            descriptionId = "Kejenuhan: ${"%.0f".format(saturationPct)}% (Permukaan: ${"%.2f".format(data.soilMoistureSurface)}, Dalam: ${"%.2f".format(data.soilMoistureDeep)} m\u00b3/m\u00b3)"
        ))

        factors.add(LandslideRiskFactor(
            name = "Seismic Activity",
            nameId = "Aktivitas Seismik",
            score = seismicScore,
            maxScore = 20,
            description = "${recentNearbyQuakes.size} quakes within 100km (max M${"%.1f".format(maxMagnitude)})",
            descriptionId = "${recentNearbyQuakes.size} gempa dalam 100km (maks M${"%.1f".format(maxMagnitude)})"
        ))

        factors.add(LandslideRiskFactor(
            name = "Rain Forecast",
            nameId = "Prakiraan Hujan",
            score = data.forecastScore,
            maxScore = 10,
            description = "Next 24h: ${"%.0f".format(data.rainfallForecast24h)} mm expected",
            descriptionId = "24 jam ke depan: ${"%.0f".format(data.rainfallForecast24h)} mm diperkirakan"
        ))

        // ── Description & Recommendation ──
        val descEn = when (riskLevel) {
            LandslideRiskLevel.LOW -> "Low landslide risk. Current conditions are stable."
            LandslideRiskLevel.MODERATE -> "Moderate landslide risk. Monitor conditions if in hilly or steep terrain."
            LandslideRiskLevel.HIGH -> "High landslide risk. Heavy rainfall and saturated soil increase danger. Avoid steep slopes."
            LandslideRiskLevel.VERY_HIGH -> "Very high landslide risk! Evacuate from hillside areas immediately if possible."
            LandslideRiskLevel.CRITICAL -> "CRITICAL landslide risk! Extreme conditions detected. Evacuate immediately from all elevated terrain!"
        }
        val descId = when (riskLevel) {
            LandslideRiskLevel.LOW -> "Risiko longsor rendah. Kondisi saat ini stabil."
            LandslideRiskLevel.MODERATE -> "Risiko longsor sedang. Pantau kondisi jika berada di area perbukitan."
            LandslideRiskLevel.HIGH -> "Risiko longsor tinggi. Hujan deras dan tanah jenuh meningkatkan bahaya. Hindari lereng curam."
            LandslideRiskLevel.VERY_HIGH -> "Risiko longsor sangat tinggi! Segera evakuasi dari area perbukitan jika memungkinkan."
            LandslideRiskLevel.CRITICAL -> "Risiko longsor KRITIS! Kondisi ekstrem terdeteksi. Segera evakuasi dari semua area dataran tinggi!"
        }
        val recEn = when (riskLevel) {
            LandslideRiskLevel.LOW -> "No special precautions needed."
            LandslideRiskLevel.MODERATE -> "Stay alert for signs: cracks in ground, tilting trees, unusual water flow."
            LandslideRiskLevel.HIGH -> "Prepare evacuation route. Watch for landslide signs: ground movement, muddy water, falling debris."
            LandslideRiskLevel.VERY_HIGH -> "Evacuate hillside areas. Do not travel on mountain roads. Contact local authorities."
            LandslideRiskLevel.CRITICAL -> "EVACUATE IMMEDIATELY. This is a life-threatening situation!"
        }
        val recId = when (riskLevel) {
            LandslideRiskLevel.LOW -> "Tidak perlu tindakan khusus."
            LandslideRiskLevel.MODERATE -> "Tetap waspada terhadap tanda: retakan tanah, pohon miring, aliran air tidak biasa."
            LandslideRiskLevel.HIGH -> "Siapkan jalur evakuasi. Perhatikan tanda longsor: pergerakan tanah, air keruh, reruntuhan."
            LandslideRiskLevel.VERY_HIGH -> "Evakuasi area perbukitan. Jangan bepergian di jalan pegunungan. Hubungi pihak berwenang."
            LandslideRiskLevel.CRITICAL -> "SEGERA EVAKUASI. Ini adalah situasi yang mengancam jiwa!"
        }

        val isId = AppLocaleManager.locale == com.weather.forecast.data.locale.AppLocale.ID

        return LandslideRiskAssessment(
            riskLevel = riskLevel,
            riskScore = totalScore,
            factors = factors,
            currentRainRate = data.currentRainRate,
            rainfall24h = data.rainfall24h,
            rainfall72h = data.rainfall72h,
            rainfallForecast24h = data.rainfallForecast24h,
            soilMoistureSurface = data.soilMoistureSurface,
            soilMoistureMiddle = data.soilMoistureMiddle,
            soilMoistureDeep = data.soilMoistureDeep,
            soilSaturationPercent = saturationPct,
            recentNearbyQuakes = recentNearbyQuakes.size,
            maxNearbyMagnitude = maxMagnitude,
            hourlyRainForecast = data.hourlyRainForecast,
            description = if (isId) descId else descEn,
            recommendation = if (isId) recId else recEn
        )
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
