package com.weather.forecast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weather.forecast.data.api.RetrofitClient
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
 * Memantau bencana yang sedang/sudah terjadi di Indonesia dan sekitarnya.
 *
 * ── Data Sources ──
 * 1. **ReliefWeb API** (UN OCHA) — Data bencana aktif, global
 *    - Status: alert, ongoing, past
 *    - Filter: Indonesia (IDN)
 *    - Gratis, tanpa API key
 *
 * 2. **Open-Meteo Flood API** — Data debit sungai real-time
 *    - Digunakan untuk deteksi banjir lokal berdasarkan threshold
 *    - Sudah ada di FloodApiService
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
    private val gson = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "disaster_monitor"
        private const val KEY_CACHED_DISASTERS = "cached_disasters"
        private const val KEY_LAST_FETCH = "last_fetch"
        private const val KEY_MANUAL_OVERRIDES = "manual_overrides"

        /** Minimum interval antara fetch API (15 menit) */
        private const val FETCH_INTERVAL_MS = 15 * 60 * 1000L

        /** Threshold debit sungai untuk klasifikasi banjir aktif (m³/s) */
        private const val FLOOD_DISCHARGE_THRESHOLD_HIGH = 500.0
        private const val FLOOD_DISCHARGE_THRESHOLD_MODERATE = 200.0

        /** Berapa hari ke belakang dicari event di ReliefWeb */
        private const val LOOKBACK_DAYS = 60L
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
                val reliefWebResult = async { fetchReliefWebDisasters() }
                val floodResult = async { fetchLocalFloodStatus(latitude, longitude) }

                val reliefWebDisasters = reliefWebResult.await()
                val localFloodDisaster = floodResult.await()

                // Merge all sources
                val allDisasters = mutableListOf<ActiveDisaster>()
                allDisasters.addAll(reliefWebDisasters)
                localFloodDisaster?.let { allDisasters.add(it) }

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
    //  ReliefWeb Data Fetching
    // ════════════════════════════════════════════════

    /**
     * Fetch bencana aktif dari ReliefWeb API.
     * Filter: Indonesia (IDN), status ongoing + alert.
     */
    private suspend fun fetchReliefWebDisasters(): List<ActiveDisaster> {
        val disasters = mutableListOf<ActiveDisaster>()

        try {
            // Fetch ongoing disasters in Indonesia
            val ongoingResponse = reliefWebApi.getDisastersByCountry(
                filterValue1 = "IDN",
                filterValue2 = "ongoing",
                limit = 20
            )
            disasters.addAll(mapReliefWebToActiveDisasters(ongoingResponse, DisasterPhase.ACTIVE))

            // Also fetch alerts (early warning)
            val alertResponse = reliefWebApi.getDisastersByCountry(
                filterValue1 = "IDN",
                filterValue2 = "alert",
                limit = 10
            )
            disasters.addAll(mapReliefWebToActiveDisasters(alertResponse, DisasterPhase.ACTIVE))

            // Fetch recently past (untuk tracking recovery)
            val dateFrom = LocalDate.now().minusDays(LOOKBACK_DAYS)
                .format(DateTimeFormatter.ISO_DATE)
            val pastResponse = try {
                reliefWebApi.getRecentDisasters(
                    dateFrom = dateFrom,
                    limit = 20
                )
            } catch (_: Exception) { null }

            // Filter past disasters yang masih relevan (Indonesia)
            pastResponse?.data?.forEach { item ->
                val fields = item.fields ?: return@forEach
                val countries = fields.country ?: return@forEach
                val isIndonesia = countries.any { it.iso3 == "IDN" }
                if (isIndonesia && fields.status == "past") {
                    mapSingleReliefWebDisaster(item, DisasterPhase.RECOVERY)?.let {
                        disasters.add(it)
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

            ActiveDisaster(
                id = "local-flood-${"%.2f".format(latitude)}-${"%.2f".format(longitude)}",
                title = "Potensi Banjir (Debit Sungai Tinggi)",
                type = ActiveDisasterType.FLOOD,
                phase = phase,
                locations = listOf(
                    AffectedLocation(
                        name = "Lokasi Anda",
                        latitude = latitude,
                        longitude = longitude,
                        radiusKm = 25.0
                    )
                ),
                severity = severity,
                startDate = System.currentTimeMillis(),
                lastUpdate = System.currentTimeMillis(),
                currentSituation = "Debit sungai terdekat: $dischargeStr m³/s (maks: $maxStr m³/s). " +
                        if (phase == DisasterPhase.RECOVERY) "Debit mulai menurun, waspada."
                        else "Debit sangat tinggi, potensi banjir!",
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
    //  Lifecycle Management
    // ════════════════════════════════════════════════

    /**
     * Apply lifecycle rules:
     * - ACTIVE tanpa update > 7 hari → RECOVERY
     * - RECOVERY tanpa update > STALE_RECOVERY_DAYS → RESOLVED
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
                            currentSituation = "${current.currentSituation}\n⏳ Tidak ada update > 7 hari, kemungkinan dalam pemulihan."
                        )
                    } else current
                }
                DisasterPhase.RECOVERY -> {
                    if (daysSinceUpdate > DisasterDisplayConfig.STALE_RECOVERY_DAYS && overriddenPhase == null) {
                        current.copy(
                            phase = DisasterPhase.RESOLVED,
                            recoveryProgress = 1.0f,
                            resolvedDate = now,
                            currentSituation = "Pemulihan dianggap selesai (tidak ada update > ${DisasterDisplayConfig.STALE_RECOVERY_DAYS} hari)."
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
                            description = "Status berubah: ${existing.phase.labelId} → ${newDisaster.phase.labelId}",
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
        val provinces = listOf(
            "Aceh", "Sumatera Utara", "Sumatera Barat", "Riau", "Jambi",
            "Sumatera Selatan", "Bengkulu", "Lampung", "Bangka Belitung",
            "Kepulauan Riau", "DKI Jakarta", "Jawa Barat", "Jawa Tengah",
            "DI Yogyakarta", "Jawa Timur", "Banten", "Bali",
            "Nusa Tenggara Barat", "Nusa Tenggara Timur", "Kalimantan Barat",
            "Kalimantan Tengah", "Kalimantan Selatan", "Kalimantan Timur",
            "Kalimantan Utara", "Sulawesi Utara", "Sulawesi Tengah",
            "Sulawesi Selatan", "Sulawesi Tenggara", "Gorontalo",
            "Sulawesi Barat", "Maluku", "Maluku Utara", "Papua",
            "Papua Barat", "Papua Tengah", "Papua Pegunungan",
            "North Sumatra", "West Sumatra", "South Sumatra",
            "West Java", "Central Java", "East Java", "West Kalimantan",
            "Central Kalimantan", "South Kalimantan", "East Kalimantan",
            "North Sulawesi", "Central Sulawesi", "South Sulawesi",
            "West Papua", "North Maluku"
        )
        return provinces.firstOrNull { it.lowercase() in title.lowercase() } ?: ""
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
        val phaseDesc = when (phase) {
            DisasterPhase.ACTIVE -> "Bencana masih berlangsung."
            DisasterPhase.RECOVERY -> "Area dalam proses pemulihan ($daysSinceEvent hari sejak kejadian)."
            DisasterPhase.RESOLVED -> "Kondisi sudah kembali normal."
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
                    description = "Bencana dilaporkan: $title",
                    icon = "🚨"
                )
            )
        }

        if (changedDate > 0 && changedDate != eventDate) {
            timeline.add(
                DisasterTimelineEvent(
                    timestamp = changedDate,
                    phase = phase,
                    description = "Update terbaru",
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
                "response" in description.lowercase() -> "Bantuan sedang disalurkan"
                "relief" in description.lowercase() -> "Operasi bantuan aktif"
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
     * Simpan manual phase override (misal: user menandai bencana sudah resolved).
     */
    fun setManualPhaseOverride(disasterId: String, phase: DisasterPhase) {
        val overrides = loadManualOverrides().toMutableMap()
        overrides[disasterId] = phase
        prefs.edit()
            .putString(KEY_MANUAL_OVERRIDES, gson.toJson(overrides.mapValues { it.value.name }))
            .apply()
    }

    /**
     * Hapus manual override.
     */
    fun clearManualOverride(disasterId: String) {
        val overrides = loadManualOverrides().toMutableMap()
        overrides.remove(disasterId)
        prefs.edit()
            .putString(KEY_MANUAL_OVERRIDES, gson.toJson(overrides.mapValues { it.value.name }))
            .apply()
    }
}
