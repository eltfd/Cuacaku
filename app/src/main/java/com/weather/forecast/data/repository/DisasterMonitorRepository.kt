package com.weather.forecast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.haversineDistance
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Active Disaster Monitor Repository
 *
 * Memantau bencana yang sedang/sudah terjadi di seluruh dunia,
 * berdasarkan lokasi user secara dinamis.
 *
 * ── Data Sources ──
 * 1. **ReliefWeb API** (UN OCHA) — Data bencana aktif, global coverage
 *    - Status: alert, ongoing, past
 *    - Filter: Dinamis berdasarkan negara user (reverse geocode → ISO3)
 *    - Gratis, tanpa API key
 *
 * 2. **Open-Meteo Flood API** — Data debit sungai real-time
 *    - Deteksi banjir lokal berdasarkan threshold, global coverage
 *    - Sudah ada di FloodApiService
 *
 * 3. **Nominatim Reverse Geocoding** — Deteksi negara dari koordinat
 *    - Mendapatkan ISO3 code negara untuk filter ReliefWeb
 *    - Gratis (OpenStreetMap)
 *
 * 4. **NASA EONET v3** — Event tracking bencana alam global
 *    - Real-time landslide, flood, dan event lainnya
 *    - Koordinat presisi tinggi untuk proximity filter
 *    - Gratis, tanpa API key
 *
 * ── Lifecycle Management ──
 * ACTIVE → RECOVERY → RESOLVED → (auto-hide)
 *
 * - ACTIVE: Bencana sedang berlangsung (status=ongoing di ReliefWeb, atau
 *   debit sungai di atas threshold)
 * - RECOVERY: Sudah tidak ada laporan baru & debit sungai menurun,
 *   atau bencana ditandai "past" tapi masih < N hari
 * - RESOLVED: Kondisi kembali normal, tetap ditampilkan untuk M hari
 *   tergantung severity (3–14 hari)
 * - Auto-hide: Setelah M hari resolved, event dihapus dari tampilan
 *
 * ── Persistence ──
 * State disimpan di SharedPreferences agar tak hilang saat restart.
 * Manual phase override (user bisa memindahkan phase) tersimpan juga.
 */
class DisasterMonitorRepository(private val context: Context) {

    private val reliefWebApi = RetrofitClient.reliefWebApi
    private val floodApi = RetrofitClient.floodApi
    private val geocodingApi = RetrofitClient.geocodingApi
    private val eonetApi = RetrofitClient.eonetApi
    private val gson = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "disaster_monitor"
        private const val KEY_CACHED_DISASTERS = "cached_disasters"
        private const val KEY_LAST_FETCH = "last_fetch"
        private const val KEY_MANUAL_OVERRIDES = "manual_overrides"
        private const val KEY_CACHED_COUNTRY_ISO3 = "cached_country_iso3"
        private const val KEY_CACHED_COUNTRY_NAME = "cached_country_name"
        private const val KEY_COUNTRY_CACHE_TIME = "country_cache_time"

        /** Minimum interval antara fetch API (15 menit) */
        private const val FETCH_INTERVAL_MS = 15 * 60 * 1000L

        /** Cache country result for 1 hour — reverse geocode jarang berubah */
        private const val COUNTRY_CACHE_INTERVAL_MS = 60 * 60 * 1000L

        /** Threshold debit sungai untuk klasifikasi banjir aktif (m³/s) */
        private const val FLOOD_DISCHARGE_THRESHOLD_HIGH = 500.0
        private const val FLOOD_DISCHARGE_THRESHOLD_MODERATE = 200.0

        /** Berapa hari ke belakang dicari event di ReliefWeb */
        private const val LOOKBACK_DAYS = 60L

        /** Radius proximity filter (km) — bencana dalam jarak ini ditampilkan */
        private const val PROXIMITY_RADIUS_KM = 500.0

        /** ISO 3166-1 alpha-2 → alpha-3 mapping (common countries) */
        private val COUNTRY_CODE_MAP = mapOf(
            "id" to "IDN", "us" to "USA", "gb" to "GBR", "jp" to "JPN",
            "de" to "DEU", "fr" to "FRA", "in" to "IND", "cn" to "CHN",
            "br" to "BRA", "au" to "AUS", "ca" to "CAN", "kr" to "KOR",
            "mx" to "MEX", "it" to "ITA", "es" to "ESP", "ru" to "RUS",
            "nl" to "NLD", "tr" to "TUR", "sa" to "SAU", "ae" to "ARE",
            "sg" to "SGP", "my" to "MYS", "th" to "THA", "ph" to "PHL",
            "vn" to "VNM", "bd" to "BGD", "pk" to "PAK", "lk" to "LKA",
            "np" to "NPL", "mm" to "MMR", "kh" to "KHM", "la" to "LAO",
            "nz" to "NZL", "za" to "ZAF", "ng" to "NGA", "ke" to "KEN",
            "eg" to "EGY", "ma" to "MAR", "et" to "ETH", "gh" to "GHA",
            "tz" to "TZA", "co" to "COL", "ar" to "ARG", "cl" to "CHL",
            "pe" to "PER", "ec" to "ECU", "ve" to "VEN", "bo" to "BOL",
            "py" to "PRY", "uy" to "URY", "ht" to "HTI", "do" to "DOM",
            "gt" to "GTM", "hn" to "HND", "sv" to "SLV", "ni" to "NIC",
            "cr" to "CRI", "pa" to "PAN", "cu" to "CUB", "jm" to "JAM",
            "at" to "AUT", "be" to "BEL", "ch" to "CHE", "cz" to "CZE",
            "dk" to "DNK", "fi" to "FIN", "gr" to "GRC", "hu" to "HUN",
            "ie" to "IRL", "no" to "NOR", "pl" to "POL", "pt" to "PRT",
            "ro" to "ROU", "se" to "SWE", "ua" to "UKR", "bg" to "BGR",
            "hr" to "HRV", "rs" to "SRB", "si" to "SVN", "sk" to "SVK",
            "il" to "ISR", "jo" to "JOR", "lb" to "LBN", "iq" to "IRQ",
            "ir" to "IRN", "af" to "AFG", "mn" to "MNG", "kz" to "KAZ",
            "uz" to "UZB", "tm" to "TKM", "kg" to "KGZ", "tj" to "TJK",
            "pg" to "PNG", "fj" to "FJI", "ws" to "WSM", "to" to "TON",
            "mz" to "MOZ", "mg" to "MDG", "cd" to "COD", "cm" to "CMR",
            "sn" to "SEN", "ml" to "MLI", "bf" to "BFA", "ne" to "NER",
            "td" to "TCD", "sd" to "SDN", "ss" to "SSD", "so" to "SOM",
            "ly" to "LBY", "tn" to "TUN", "dz" to "DZA", "ci" to "CIV",
            "tl" to "TLS", "bn" to "BRN", "tw" to "TWN", "hk" to "HKG"
        )
    }

    /**
     * Mendapatkan daftar bencana aktif yang sedang dipantau.
     *
     * Flow:
     * 1. Cek cache → jika masih fresh, gunakan cache
     * 2. Fetch dari ReliefWeb (Indonesia + regional)
     * 3. Fetch flood data dari Open-Meteo (lokasi user)
     * 4. Merge dengan local state (phase overrides, timeline)
     * 5. Apply lifecycle rules (auto-transition, auto-hide)
     * 6. Return filtered & sorted list
     */
    suspend fun getActiveDisasters(
        latitude: Double,
        longitude: Double
    ): Result<ActiveDisasterMonitor> {
        return withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0)

                // Use cache if fresh
                if (now - lastFetch < FETCH_INTERVAL_MS) {
                    val cached = loadCachedDisasters()
                    if (cached.isNotEmpty()) {
                        val processed = applyLifecycleRules(cached)
                        return@withContext Result.success(
                            ActiveDisasterMonitor(disasters = processed)
                        )
                    }
                }

                // Fetch from APIs in parallel
                val reliefWebResult = async { fetchReliefWebDisasters(latitude, longitude) }
                val floodResult = async { fetchLocalFloodStatus(latitude, longitude) }
                val eonetResult = async { fetchEonetLandslides(latitude, longitude) }

                val reliefWebDisasters = reliefWebResult.await()
                val localFloodDisaster = floodResult.await()
                val eonetDisasters = eonetResult.await()

                // Merge all sources
                val allDisasters = mutableListOf<ActiveDisaster>()
                allDisasters.addAll(reliefWebDisasters)
                localFloodDisaster?.let { allDisasters.add(it) }
                allDisasters.addAll(eonetDisasters)

                // Merge with cached state (preserve timeline, manual overrides)
                val merged = mergeWithCachedState(allDisasters)

                // Apply lifecycle rules
                val processed = applyLifecycleRules(merged)

                // Save to cache
                saveCachedDisasters(processed)
                prefs.edit().putLong(KEY_LAST_FETCH, now).apply()

                Result.success(ActiveDisasterMonitor(disasters = processed))
            } catch (e: Exception) {
                // On error, try to serve cache
                val cached = loadCachedDisasters()
                if (cached.isNotEmpty()) {
                    Result.success(
                        ActiveDisasterMonitor(disasters = applyLifecycleRules(cached))
                    )
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    // ════════════════════════════════════════════════
    //  ReliefWeb Data Fetching (Global)
    // ════════════════════════════════════════════════

    /**
     * Deteksi negara dari koordinat user via reverse geocoding.
     * Hasil di-cache selama 1 jam untuk hemat bandwidth.
     *
     * @return ISO3 country code (e.g. "IDN", "USA", "JPN") atau null jika gagal
     */
    private suspend fun detectCountryIso3(latitude: Double, longitude: Double): String? {
        // Check cache first
        val now = System.currentTimeMillis()
        val cacheTime = prefs.getLong(KEY_COUNTRY_CACHE_TIME, 0)
        if (now - cacheTime < COUNTRY_CACHE_INTERVAL_MS) {
            val cached = prefs.getString(KEY_CACHED_COUNTRY_ISO3, null)
            if (!cached.isNullOrBlank()) return cached
        }

        return try {
            val response = geocodingApi.reverseGeocode(
                latitude = latitude,
                longitude = longitude,
                zoom = 5  // Country level — hemat bandwidth
            )
            val alpha2 = response.address?.countryCode?.lowercase()
            val iso3 = alpha2?.let { COUNTRY_CODE_MAP[it] }

            // Cache result
            if (iso3 != null) {
                prefs.edit()
                    .putString(KEY_CACHED_COUNTRY_ISO3, iso3)
                    .putString(KEY_CACHED_COUNTRY_NAME, response.address?.country ?: "")
                    .putLong(KEY_COUNTRY_CACHE_TIME, now)
                    .apply()
            }
            iso3
        } catch (_: Exception) {
            // Fallback: gunakan cache lama jika ada
            prefs.getString(KEY_CACHED_COUNTRY_ISO3, null)
        }
    }

    /**
     * Fetch bencana aktif dari ReliefWeb API — global, berdasarkan lokasi user.
     *
     * Strategy:
     * 1. Fetch disasters berdasarkan negara user (via reverse geocode → ISO3)
     * 2. Fetch recent global disasters, filter by proximity
     * 3. Merge & deduplicate
     */
    private suspend fun fetchReliefWebDisasters(
        latitude: Double,
        longitude: Double
    ): List<ActiveDisaster> {
        val disasters = mutableListOf<ActiveDisaster>()

        try {
            // Step 1: Detect user's country
            val countryIso3 = detectCountryIso3(latitude, longitude)

            // Step 2: Fetch country-specific disasters (if country detected)
            if (countryIso3 != null) {
                // Ongoing disasters in user's country
                val ongoingResponse = reliefWebApi.getDisastersByCountry(
                    filterValue1 = countryIso3,
                    filterValue2 = "ongoing",
                    limit = 20
                )
                disasters.addAll(mapReliefWebToActiveDisasters(ongoingResponse, DisasterPhase.ACTIVE))

                // Alerts (early warning) in user's country
                val alertResponse = reliefWebApi.getDisastersByCountry(
                    filterValue1 = countryIso3,
                    filterValue2 = "alert",
                    limit = 10
                )
                disasters.addAll(mapReliefWebToActiveDisasters(alertResponse, DisasterPhase.ACTIVE))
            }

            // Step 3: Fetch recent global disasters (proximity-based)
            val dateFrom = LocalDate.now().minusDays(LOOKBACK_DAYS)
                .format(DateTimeFormatter.ISO_DATE)
            val globalResponse = try {
                reliefWebApi.getRecentDisasters(
                    dateFrom = dateFrom,
                    limit = 50
                )
            } catch (_: Exception) { null }

            globalResponse?.data?.forEach { item ->
                val fields = item.fields ?: return@forEach
                val countries = fields.country ?: return@forEach

                // Filter: nearby disasters by proximity OR recovery in user's country
                val isNearby = countries.any { country ->
                    val loc = country.location
                    if (loc?.lat != null && loc.lon != null) {
                        haversineDistance(latitude, longitude, loc.lat, loc.lon) <= PROXIMITY_RADIUS_KM
                    } else {
                        // Fallback: match by country ISO3
                        countryIso3 != null && country.iso3 == countryIso3
                    }
                }

                if (isNearby) {
                    val phase = when (fields.status) {
                        "ongoing" -> DisasterPhase.ACTIVE
                        "alert" -> DisasterPhase.ACTIVE
                        "past" -> DisasterPhase.RECOVERY
                        else -> DisasterPhase.RECOVERY
                    }
                    mapSingleReliefWebDisaster(item, phase)?.let { disaster ->
                        disasters.add(disaster)
                    }
                }
            }
        } catch (e: Exception) {
            // Silently continue — we'll merge with cache
            e.printStackTrace()
        }

        return disasters.distinctBy { it.id }
    }



    /**
     * Map ReliefWeb response ke ActiveDisaster list.
     */
    private fun mapReliefWebToActiveDisasters(
        response: ReliefWebDisasterResponse,
        defaultPhase: DisasterPhase
    ): List<ActiveDisaster> {
        return response.data.mapNotNull { item ->
            mapSingleReliefWebDisaster(item, defaultPhase)
        }
    }

    private fun mapSingleReliefWebDisaster(
        item: ReliefWebDisasterItem,
        phase: DisasterPhase
    ): ActiveDisaster? {
        val fields = item.fields ?: return null
        val name = fields.name.ifBlank { return null }

        // Determine disaster type from ReliefWeb type field
        val rwType = fields.primaryType?.name ?: fields.type?.firstOrNull()?.name ?: ""
        val disasterType = mapReliefWebType(rwType)

        // Parse locations from country data
        val locations = fields.country?.map { country ->
            AffectedLocation(
                name = extractLocationFromTitle(name, country.name),
                province = extractProvinceFromTitle(name),
                latitude = country.location?.lat,
                longitude = country.location?.lon
            )
        } ?: emptyList()

        // Parse dates
        val eventDate = parseReliefWebDate(fields.date?.event)
        val changedDate = parseReliefWebDate(fields.date?.changed)

        // Determine severity from event characteristics
        val severity = estimateSeverity(name, fields.description)

        // Calculate estimated recovery and display info
        val now = System.currentTimeMillis()
        val daysSinceEvent = if (eventDate > 0) {
            TimeUnit.MILLISECONDS.toDays(now - eventDate).toInt()
        } else 0

        val displayDays = DisasterDisplayConfig.getDisplayDays(severity)

        return ActiveDisaster(
            id = "rw-${fields.id}",
            title = name,
            type = disasterType,
            phase = phase,
            locations = locations,
            severity = severity,
            startDate = if (eventDate > 0) eventDate else now,
            lastUpdate = if (changedDate > 0) changedDate else now,
            currentSituation = buildSituationDescription(name, phase, daysSinceEvent),
            recoveryProgress = estimateRecoveryProgress(phase, daysSinceEvent, severity),
            daysUntilHidden = if (phase == DisasterPhase.RESOLVED) {
                maxOf(0, displayDays - daysSinceEvent)
            } else 0,
            timeline = buildInitialTimeline(name, eventDate, changedDate, phase),
            impact = estimateImpact(name, fields.description),
            source = "ReliefWeb (UN OCHA)",
            sourceUrl = fields.url ?: fields.urlAlias
        )
    }

    // ════════════════════════════════════════════════
    //  Local Flood Detection (Open-Meteo)
    // ════════════════════════════════════════════════

    /**
     * Deteksi banjir lokal berdasarkan data debit sungai.
     * Jika debit di atas threshold, buat ActiveDisaster lokal.
     */
    private suspend fun fetchLocalFloodStatus(
        latitude: Double,
        longitude: Double
    ): ActiveDisaster? {
        return try {
            val floodData = floodApi.getFloodData(
                latitude = latitude,
                longitude = longitude,
                forecastDays = 7
            )

            val maxDischarge = floodData.daily?.riverDischargeMax?.filterNotNull()?.maxOrNull() ?: 0.0
            val currentDischarge = floodData.daily?.riverDischarge?.firstOrNull() ?: 0.0

            if (currentDischarge < FLOOD_DISCHARGE_THRESHOLD_MODERATE) return null

            val severity = when {
                currentDischarge >= FLOOD_DISCHARGE_THRESHOLD_HIGH -> DisasterSeverity.SEVERE
                currentDischarge >= FLOOD_DISCHARGE_THRESHOLD_MODERATE -> DisasterSeverity.MODERATE
                else -> return null
            }

            val phase = if (maxDischarge > currentDischarge && currentDischarge < FLOOD_DISCHARGE_THRESHOLD_HIGH) {
                DisasterPhase.RECOVERY  // Debit menurun
            } else {
                DisasterPhase.ACTIVE
            }

            val dischargeStr = "%.1f".format(currentDischarge)
            val maxStr = "%.1f".format(maxDischarge)

            val s = AppLocaleManager.strings
            ActiveDisaster(
                id = "local-flood-${"%.2f".format(latitude)}-${"%.2f".format(longitude)}",
                title = s.floodRiskTitle,
                type = ActiveDisasterType.FLOOD,
                phase = phase,
                locations = listOf(
                    AffectedLocation(
                        name = s.yourLocation,
                        latitude = latitude,
                        longitude = longitude,
                        radiusKm = 25.0
                    )
                ),
                severity = severity,
                startDate = System.currentTimeMillis(),
                lastUpdate = System.currentTimeMillis(),
                currentSituation = s.riverDischargeDesc(dischargeStr, maxStr) +
                        if (phase == DisasterPhase.RECOVERY) s.dischargeDecreasing
                        else s.dischargeVeryHigh,
                recoveryProgress = if (phase == DisasterPhase.RECOVERY) {
                    (1f - (currentDischarge / maxDischarge).toFloat()).coerceIn(0.1f, 0.8f)
                } else 0f,
                source = "Open-Meteo Flood API (GloFAS/ECMWF)"
            )
        } catch (_: Exception) {
            null
        }
    }

    // ════════════════════════════════════════════════
    //  NASA EONET — Landslide & Flood Event Tracking
    // ════════════════════════════════════════════════

    /**
     * Fetch event bencana dari NASA EONET v3 — landslide, floods.
     * Filter by proximity ke lokasi user.
     *
     * EONET menyediakan koordinat presisi tinggi (+metadata) untuk setiap event.
     * Kategori: landslides, floods, severe storms, volcanoes, dll.
     */
    private suspend fun fetchEonetLandslides(
        latitude: Double,
        longitude: Double
    ): List<ActiveDisaster> {
        val disasters = mutableListOf<ActiveDisaster>()
        val s = AppLocaleManager.strings

        try {
            // Fetch landslide events (last 90 days)
            disasters.addAll(fetchEonetByCategory(
                category = "landslides", days = 90, limit = 30,
                userLat = latitude, userLon = longitude,
                disasterType = ActiveDisasterType.LANDSLIDE,
                defaultTitle = "Landslide Event",
                situationBuilder = { distance, event ->
                    buildString {
                        append(s.eonetLandslideDetected)
                        append(" ")
                        append(s.eonetDistance("%.0f".format(distance)))
                        event.description?.takeIf { it.isNotBlank() }?.let {
                            append("\n"); append(it.take(200))
                        }
                    }
                }
            ))

            // Fetch flood events from EONET (complementary to Open-Meteo)
            disasters.addAll(fetchEonetByCategory(
                category = "floods", days = 60, limit = 20,
                userLat = latitude, userLon = longitude,
                disasterType = ActiveDisasterType.FLOOD,
                defaultTitle = "Flood Event",
                situationBuilder = { distance, _ ->
                    "${s.eonetFloodDetected} ${s.eonetDistance("%.0f".format(distance))}"
                }
            ))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return disasters
    }

    /**
     * Fetch EONET events by category, filter by proximity.
     */
    private suspend fun fetchEonetByCategory(
        category: String, days: Int, limit: Int,
        userLat: Double, userLon: Double,
        disasterType: ActiveDisasterType,
        defaultTitle: String,
        situationBuilder: (distance: Double, event: EonetEvent) -> String
    ): List<ActiveDisaster> {
        val response = try {
            eonetApi.getEvents(category = category, days = days, status = "open", limit = limit)
        } catch (_: Exception) { return emptyList() }

        return response.events?.mapNotNull { event ->
            val lat = event.geometry?.firstOrNull()?.latitude ?: return@mapNotNull null
            val lon = event.geometry?.firstOrNull()?.longitude ?: return@mapNotNull null
            val distance = haversineDistance(userLat, userLon, lat, lon)
            if (distance > PROXIMITY_RADIUS_KM) return@mapNotNull null

            val phase = if (event.closed == null) DisasterPhase.ACTIVE else DisasterPhase.RECOVERY
            val eventDate = try {
                event.geometry?.firstOrNull()?.date?.let { Instant.parse(it).toEpochMilli() }
                    ?: System.currentTimeMillis()
            } catch (_: Exception) { System.currentTimeMillis() }

            ActiveDisaster(
                id = "eonet-${event.id}",
                title = event.title ?: defaultTitle,
                type = disasterType,
                phase = phase,
                locations = listOf(
                    AffectedLocation(
                        name = event.title ?: "Unknown Location",
                        latitude = lat, longitude = lon, radiusKm = 50.0
                    )
                ),
                severity = DisasterSeverity.MODERATE,
                startDate = eventDate,
                lastUpdate = eventDate,
                currentSituation = situationBuilder(distance, event),
                source = "NASA EONET v3",
                sourceUrl = event.link
            )
        } ?: emptyList()
    }

    // ════════════════════════════════════════════════
    //  Lifecycle Management
    // ════════════════════════════════════════════════

    /**
     * Apply lifecycle rules:
     * - ACTIVE with no update > 7 days → RECOVERY
     * - RECOVERY with no update > STALE_RECOVERY_DAYS → RESOLVED
     * - RESOLVED > display days → remove (auto-hide)
     */
    private fun applyLifecycleRules(disasters: List<ActiveDisaster>): List<ActiveDisaster> {
        val now = System.currentTimeMillis()
        val overrides = loadManualOverrides()

        return disasters.mapNotNull { disaster ->
            // Apply manual override if exists
            val overriddenPhase = overrides[disaster.id]
            val current = if (overriddenPhase != null) {
                disaster.copy(phase = overriddenPhase)
            } else {
                disaster
            }

            val daysSinceUpdate = TimeUnit.MILLISECONDS.toDays(now - current.lastUpdate).toInt()
            val daysSinceStart = TimeUnit.MILLISECONDS.toDays(now - current.startDate).toInt()

            // Auto-transition logic
            val updated = when (current.phase) {
                DisasterPhase.ACTIVE -> {
                    if (daysSinceUpdate > 7 && overriddenPhase == null) {
                        current.copy(
                            phase = DisasterPhase.RECOVERY,
                            recoveryProgress = 0.3f,
                            currentSituation = "${current.currentSituation}${AppLocaleManager.strings.noUpdateRecovery(7)}"
                        )
                    } else current
                }
                DisasterPhase.RECOVERY -> {
                    if (daysSinceUpdate > DisasterDisplayConfig.STALE_RECOVERY_DAYS && overriddenPhase == null) {
                        current.copy(
                            phase = DisasterPhase.RESOLVED,
                            recoveryProgress = 1.0f,
                            resolvedDate = now,
                            currentSituation = AppLocaleManager.strings.recoveryComplete(DisasterDisplayConfig.STALE_RECOVERY_DAYS)
                        )
                    } else current
                }
                DisasterPhase.RESOLVED -> {
                    val resolvedAt = current.resolvedDate ?: now
                    val daysSinceResolved = TimeUnit.MILLISECONDS.toDays(now - resolvedAt).toInt()
                    val displayDays = DisasterDisplayConfig.getDisplayDays(current.severity)

                    if (daysSinceResolved > displayDays) {
                        null  // Auto-hide: sudah melewati display period
                    } else {
                        current.copy(daysUntilHidden = maxOf(0, displayDays - daysSinceResolved))
                    }
                }
            }

            updated
        }.sortedWith(
            compareBy<ActiveDisaster> { it.phase.ordinal }
                .thenByDescending { it.severity.level }
                .thenByDescending { it.lastUpdate }
        )
    }

    /**
     * Merge data API baru dengan state yang sudah tersimpan.
     * Mempertahankan timeline & phase manual dari cache.
     */
    private fun mergeWithCachedState(newDisasters: List<ActiveDisaster>): List<ActiveDisaster> {
        val cached = loadCachedDisasters().associateBy { it.id }

        return newDisasters.map { newDisaster ->
            val existing = cached[newDisaster.id]
            if (existing != null) {
                // Merge: keep existing timeline, append update if phase changed
                val timeline = existing.timeline.toMutableList()
                if (existing.phase != newDisaster.phase) {
                    timeline.add(
                        DisasterTimelineEvent(
                            timestamp = System.currentTimeMillis(),
                            phase = newDisaster.phase,
                            description = AppLocaleManager.strings.statusChanged(existing.phase.labelId, newDisaster.phase.labelId),
                            icon = newDisaster.phase.icon
                        )
                    )
                }
                newDisaster.copy(
                    timeline = timeline,
                    resolvedDate = existing.resolvedDate ?: newDisaster.resolvedDate
                )
            } else {
                newDisaster
            }
        }
    }

    // ════════════════════════════════════════════════
    //  Helper: Mapping & Estimation
    // ════════════════════════════════════════════════

    private fun mapReliefWebType(typeName: String): ActiveDisasterType {
        val lower = typeName.lowercase()
        return when {
            "flood" in lower && "flash" in lower -> ActiveDisasterType.FLASH_FLOOD
            "flood" in lower -> ActiveDisasterType.FLOOD
            "landslide" in lower || "mudslide" in lower -> ActiveDisasterType.LANDSLIDE
            "cyclone" in lower || "typhoon" in lower || "storm" in lower -> ActiveDisasterType.CYCLONE
            "earthquake" in lower -> ActiveDisasterType.EARTHQUAKE
            "volcano" in lower || "eruption" in lower -> ActiveDisasterType.VOLCANIC
            "tsunami" in lower -> ActiveDisasterType.TSUNAMI
            "drought" in lower -> ActiveDisasterType.DROUGHT
            else -> ActiveDisasterType.OTHER
        }
    }

    private fun extractLocationFromTitle(title: String, countryName: String): String {
        // Try to extract province/city from title (e.g., "Indonesia: Floods in Aceh")
        val parts = title.split(":", "-", "–", "—").map { it.trim() }
        return if (parts.size > 1) {
            parts.drop(1).joinToString(" - ").take(80)
        } else {
            countryName
        }
    }

    private fun extractProvinceFromTitle(title: String): String {
        // Try to extract region/province from disaster title
        // ReliefWeb titles are typically formatted as:
        //   "Country: Event in Region" or "Region - Event"
        val parts = title.split(":", "-", "–", "—").map { it.trim() }
        return if (parts.size > 1) {
            // Try the second part which often contains the region
            val regionPart = parts.drop(1).joinToString(" ").trim()
            // Remove common prefixes like "Floods in", "Earthquake in", etc.
            val cleaned = regionPart
                .replace(Regex("^(Floods?|Earthquake|Landslide|Cyclone|Storm|Tsunami|Drought|Eruption|Volcano)\\s+(in|near|at)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
            cleaned.take(80)
        } else ""
    }

    private fun estimateSeverity(title: String, description: String?): DisasterSeverity {
        val text = "$title ${description ?: ""}".lowercase()
        return when {
            "severe" in text || "critical" in text || "major" in text ||
                    "devastating" in text || "catastroph" in text -> DisasterSeverity.CRITICAL
            "significant" in text || "serious" in text ||
                    "heavy" in text || "massive" in text -> DisasterSeverity.SEVERE
            "moderate" in text || "medium" in text -> DisasterSeverity.MODERATE
            else -> DisasterSeverity.MODERATE  // Default
        }
    }

    private fun estimateRecoveryProgress(
        phase: DisasterPhase,
        daysSinceEvent: Int,
        severity: DisasterSeverity
    ): Float {
        return when (phase) {
            DisasterPhase.ACTIVE -> 0f
            DisasterPhase.RECOVERY -> {
                val expectedDays = when (severity) {
                    DisasterSeverity.MINOR -> 7f
                    DisasterSeverity.MODERATE -> 14f
                    DisasterSeverity.SEVERE -> 30f
                    DisasterSeverity.CRITICAL -> 60f
                }
                (daysSinceEvent.toFloat() / expectedDays).coerceIn(0.1f, 0.95f)
            }
            DisasterPhase.RESOLVED -> 1.0f
        }
    }

    private fun buildSituationDescription(
        title: String,
        phase: DisasterPhase,
        daysSinceEvent: Int
    ): String {
        val s = AppLocaleManager.strings
        val phaseDesc = when (phase) {
            DisasterPhase.ACTIVE -> s.disasterIsOngoing
            DisasterPhase.RECOVERY -> s.areaInRecovery(daysSinceEvent)
            DisasterPhase.RESOLVED -> s.conditionsNormal
        }
        return "$title\n$phaseDesc"
    }

    private fun buildInitialTimeline(
        title: String,
        eventDate: Long,
        changedDate: Long,
        phase: DisasterPhase
    ): List<DisasterTimelineEvent> {
        val timeline = mutableListOf<DisasterTimelineEvent>()

        if (eventDate > 0) {
            timeline.add(
                DisasterTimelineEvent(
                    timestamp = eventDate,
                    phase = DisasterPhase.ACTIVE,
                    description = AppLocaleManager.strings.disasterReported(title),
                    icon = "🚨"
                )
            )
        }

        if (changedDate > 0 && changedDate != eventDate) {
            timeline.add(
                DisasterTimelineEvent(
                    timestamp = changedDate,
                    phase = phase,
                    description = AppLocaleManager.strings.latestUpdateLabel,
                    icon = "📋"
                )
            )
        }

        return timeline.sortedBy { it.timestamp }
    }

    private fun estimateImpact(title: String, description: String?): DisasterImpact? {
        if (description.isNullOrBlank()) return null
        return DisasterImpact(
            aidStatus = when {
                "response" in description.lowercase() -> AppLocaleManager.strings.aidInProgress
                "relief" in description.lowercase() -> AppLocaleManager.strings.reliefActive
                else -> null
            }
        )
    }

    private fun parseReliefWebDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            // ReliefWeb uses ISO 8601 format
            Instant.parse(dateStr).toEpochMilli()
        } catch (_: Exception) {
            try {
                // Fallback: try common date formats
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                sdf.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    // ════════════════════════════════════════════════
    //  Persistence
    // ════════════════════════════════════════════════

    private fun saveCachedDisasters(disasters: List<ActiveDisaster>) {
        try {
            prefs.edit()
                .putString(KEY_CACHED_DISASTERS, gson.toJson(disasters))
                .apply()
        } catch (_: Exception) { }
    }

    private fun loadCachedDisasters(): List<ActiveDisaster> {
        val json = prefs.getString(KEY_CACHED_DISASTERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ActiveDisaster>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadManualOverrides(): Map<String, DisasterPhase> {
        val json = prefs.getString(KEY_MANUAL_OVERRIDES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val raw: Map<String, String> = gson.fromJson(json, type)
            raw.mapValues { DisasterPhase.valueOf(it.value) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Save manual phase override (e.g., user marks a disaster as resolved).
     */
    fun setManualPhaseOverride(disasterId: String, phase: DisasterPhase) {
        val overrides = loadManualOverrides().toMutableMap()
        overrides[disasterId] = phase
        prefs.edit()
            .putString(KEY_MANUAL_OVERRIDES, gson.toJson(overrides.mapValues { it.value.name }))
            .apply()
    }

    /**
     * Clear manual override.
     */
    fun clearManualOverride(disasterId: String) {
        val overrides = loadManualOverrides().toMutableMap()
        overrides.remove(disasterId)
        prefs.edit()
            .putString(KEY_MANUAL_OVERRIDES, gson.toJson(overrides.mapValues { it.value.name }))
            .apply()
    }
}
