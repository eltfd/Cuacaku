package com.weather.forecast.data.model

import com.weather.forecast.data.locale.AppLocaleManager

/**
 * ══════════════════════════════════════════════════════════
 * Seismic & Volcanic Monitoring Data Models
 * ══════════════════════════════════════════════════════════
 *
 * Comprehensive models for:
 * - Earthquake monitoring & risk assessment
 * - Tsunami risk derived from submarine earthquakes
 * - Volcanic activity monitoring
 * - High wave warnings for maritime safety
 * - Impact area visualization
 *
 * Data sources:
 * - USGS Earthquake Hazards Program (real-time earthquakes)
 * - NASA EONET v3 (volcanic events, wildfires)
 * - Open-Meteo Marine API (wave data for maritime warnings)
 * - Derived analysis (tsunami risk from earthquake parameters)
 */

// ═══════════════════════════════════════════════════
//  MAIN COMPOSITE DATA
// ═══════════════════════════════════════════════════

/**
 * Complete seismic & volcanic monitoring data for display.
 * Supports full disaster lifecycle: NORMAL → EARLY_WARNING → ACTIVE_DISASTER → POST_DISASTER
 */
data class SeismicMonitorData(
    val earthquakes: List<EarthquakeEvent>,
    val nearbyEarthquakes: List<EarthquakeEvent>,
    val significantEarthquakes: List<EarthquakeEvent>,
    val tsunamiRisk: TsunamiRiskAssessment,
    val volcanicActivity: List<VolcanicEvent>,
    val nearbyVolcanoes: List<NearbyVolcano>,
    val highWaveWarning: HighWaveWarning?,
    val impactAreas: List<DisasterImpactArea>,
    val lastUpdated: Long,
    val userLatitude: Double,
    val userLongitude: Double,
    // ── Disaster Lifecycle ──
    val overallPhase: DisasterLifecyclePhase = DisasterLifecyclePhase.NORMAL,
    val lifecycleStates: List<DisasterLifecycleState> = emptyList(),
    val sosState: SOSState = SOSState(),
    val reliefPoints: List<ReliefPoint> = emptyList(),
    val emergencyContacts: List<EmergencyContact> = EmergencyContacts.getForLocale(),
    // ── Indonesian Data Sources ──
    val bmkgEarthquakes: List<BmkgEarthquakeEvent> = emptyList(),
    val crowdsourcedReports: List<CrowdsourcedDisasterReport> = emptyList(),
    // ── Landslide Risk ──
    val landslideRisk: LandslideRiskAssessment? = null
) {
    val hasActiveThreats: Boolean get() =
        nearbyEarthquakes.any { it.magnitude >= 4.0 } ||
        tsunamiRisk.riskLevel >= TsunamiRiskLevel.WATCH ||
        volcanicActivity.any { it.alertLevel >= VolcanoAlertLevel.WATCH } ||
        impactAreas.isNotEmpty()

    val totalActiveEvents: Int get() =
        nearbyEarthquakes.size +
        volcanicActivity.count { it.alertLevel >= VolcanoAlertLevel.ADVISORY } +
        (if (tsunamiRisk.riskLevel >= TsunamiRiskLevel.ADVISORY) 1 else 0)

    val isPostDisaster: Boolean get() = overallPhase == DisasterLifecyclePhase.POST_DISASTER
    val isActiveDisaster: Boolean get() = overallPhase == DisasterLifecyclePhase.ACTIVE_DISASTER
    val isEarlyWarning: Boolean get() = overallPhase == DisasterLifecyclePhase.EARLY_WARNING
    val showSOS: Boolean get() = sosState.isEligible
}

// ═══════════════════════════════════════════════════
//  EARTHQUAKE
// ═══════════════════════════════════════════════════

/**
 * Processed earthquake event with derived risk data.
 */
data class EarthquakeEvent(
    val id: String,
    val magnitude: Double,
    val magnitudeType: String,      // Mw, Ml, Mb, etc.
    val place: String,
    val time: Long,                 // epoch millis
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double,
    val distanceFromUserKm: Double,
    val feltReports: Int,
    val tsunamiFlag: Boolean,       // USGS tsunami flag
    val significance: Int,          // 0-1000
    val alertLevel: EarthquakeAlertLevel,
    val intensity: EarthquakeIntensity,
    val url: String,                // USGS detail page
    val isReviewed: Boolean
) {
    val isNearby: Boolean get() = distanceFromUserKm <= 500.0
    val isSignificant: Boolean get() = magnitude >= 5.0 || significance >= 500
    val isMajor: Boolean get() = magnitude >= 7.0
    val isShallow: Boolean get() = depthKm <= 70.0
    val isSubmarinePotential: Boolean get() = depthKm <= 100.0

    /**
     * Estimated impact radius in km based on magnitude & depth.
     * Uses empirical attenuation relationship.
     */
    val estimatedImpactRadiusKm: Double get() {
        val baseRadius = when {
            magnitude >= 8.0 -> 500.0
            magnitude >= 7.0 -> 300.0
            magnitude >= 6.0 -> 150.0
            magnitude >= 5.0 -> 80.0
            magnitude >= 4.0 -> 30.0
            magnitude >= 3.0 -> 10.0
            else -> 3.0
        }
        // Shallow quakes have wider surface impact
        val depthFactor = if (depthKm <= 20) 1.5 else if (depthKm <= 70) 1.0 else 0.6
        return baseRadius * depthFactor
    }

    /**
     * Estimated Modified Mercalli Intensity at user's location.
     * Based on magnitude, distance, and depth.
     */
    val estimatedMMIAtUser: Double get() {
        if (distanceFromUserKm <= 0) return 0.0
        val hypoDistance = kotlin.math.sqrt(distanceFromUserKm * distanceFromUserKm + depthKm * depthKm)
        // Simplified attenuation: MMI ≈ 1.5 * M - 3.5 * log10(R) - 0.00195 * R + 2.15
        val mmi = 1.5 * magnitude - 3.5 * kotlin.math.log10(hypoDistance) - 0.00195 * hypoDistance + 2.15
        return mmi.coerceIn(0.0, 12.0)
    }
}

/**
 * USGS PAGER alert level for earthquakes.
 */
enum class EarthquakeAlertLevel(
    val label: String,
    val labelId: String,
    val colorHex: Long,
    val level: Int
) {
    GREEN("Green", "Hijau", 0xFF4CAF50, 0),
    YELLOW("Yellow", "Kuning", 0xFFFFC107, 1),
    ORANGE("Orange", "Oranye", 0xFFFF9800, 2),
    RED("Red", "Merah", 0xFFF44336, 3),
    UNKNOWN("Unknown", "Tidak Diketahui", 0xFF9E9E9E, -1);

    companion object {
        fun fromString(alert: String?): EarthquakeAlertLevel = when (alert?.lowercase()) {
            "green" -> GREEN
            "yellow" -> YELLOW
            "orange" -> ORANGE
            "red" -> RED
            else -> UNKNOWN
        }
    }
}

/**
 * Modified Mercalli Intensity scale.
 */
enum class EarthquakeIntensity(
    val roman: String,
    val label: String,
    val labelId: String,
    val description: String,
    val descriptionId: String,
    val colorHex: Long,
    val level: Int
) {
    I("I", "Not felt", "Tidak terasa", "Not felt except by very few", "Tidak terasa kecuali oleh sangat sedikit orang", 0xFF4CAF50, 1),
    II("II", "Weak", "Lemah", "Felt by few persons at rest", "Dirasakan oleh beberapa orang saat istirahat", 0xFF66BB6A, 2),
    III("III", "Weak", "Lemah", "Felt noticeably indoors", "Terasa jelas di dalam ruangan", 0xFF81C784, 3),
    IV("IV", "Light", "Ringan", "Rattling of dishes, windows", "Gemerincing piring, jendela bergetar", 0xFFFFC107, 4),
    V("V", "Moderate", "Sedang", "Felt by nearly everyone", "Dirasakan hampir semua orang", 0xFFFFB300, 5),
    VI("VI", "Strong", "Kuat", "Felt by all, slight damage", "Dirasakan semua orang, kerusakan ringan", 0xFFFF9800, 6),
    VII("VII", "Very Strong", "Sangat Kuat", "Moderate damage to buildings", "Kerusakan sedang pada bangunan", 0xFFF44336, 7),
    VIII("VIII", "Severe", "Parah", "Heavy damage to structures", "Kerusakan berat pada struktur", 0xFFD32F2F, 8),
    IX("IX", "Violent", "Dahsyat", "Buildings shifted off foundations", "Bangunan bergeser dari fondasi", 0xFFB71C1C, 9),
    X("X", "Extreme", "Ekstrem", "Most structures destroyed", "Sebagian besar bangunan hancur", 0xFF880E4F, 10),
    XI("XI", "Extreme", "Ekstrem", "Rails bent, broad fissures", "Rel bengkok, retakan lebar", 0xFF4A148C, 11),
    XII("XII", "Extreme", "Ekstrem", "Total destruction", "Kehancuran total", 0xFF311B92, 12);

    companion object {
        fun fromMMI(mmi: Double): EarthquakeIntensity = when {
            mmi < 1.5 -> I
            mmi < 2.5 -> II
            mmi < 3.5 -> III
            mmi < 4.5 -> IV
            mmi < 5.5 -> V
            mmi < 6.5 -> VI
            mmi < 7.5 -> VII
            mmi < 8.5 -> VIII
            mmi < 9.5 -> IX
            mmi < 10.5 -> X
            mmi < 11.5 -> XI
            else -> XII
        }

        fun fromMagnitudeDistance(mag: Double, distKm: Double, depthKm: Double): EarthquakeIntensity {
            val hypoD = kotlin.math.sqrt(distKm * distKm + depthKm * depthKm)
            val mmi = 1.5 * mag - 3.5 * kotlin.math.log10(hypoD.coerceAtLeast(1.0)) - 0.00195 * hypoD + 2.15
            return fromMMI(mmi)
        }
    }
}

// ═══════════════════════════════════════════════════
//  TSUNAMI
// ═══════════════════════════════════════════════════

/**
 * Tsunami risk assessment derived from earthquake data.
 */
data class TsunamiRiskAssessment(
    val riskLevel: TsunamiRiskLevel,
    val triggerEarthquakes: List<EarthquakeEvent>,
    val estimatedArrivalMinutes: Int?,
    val nearestCoastDistanceKm: Double?,
    val description: String,
    val recommendation: String,
    val factors: List<TsunamiRiskFactor>
) {
    val isAtRisk: Boolean get() = riskLevel >= TsunamiRiskLevel.ADVISORY
}

data class TsunamiRiskFactor(
    val name: String,
    val value: String,
    val isElevating: Boolean
)

enum class TsunamiRiskLevel(
    val label: String,
    val labelId: String,
    val icon: String,
    val colorHex: Long,
    val level: Int
) {
    NONE("No Risk", "Tidak Ada Risiko", "✅", 0xFF4CAF50, 0),
    INFORMATION("Information", "Informasi", "ℹ️", 0xFF2196F3, 1),
    ADVISORY("Advisory", "Peringatan Dini", "⚠️", 0xFFFFC107, 2),
    WATCH("Watch", "Siaga", "🟠", 0xFFFF9800, 3),
    WARNING("Warning", "Peringatan", "🔴", 0xFFF44336, 4);
}

// ═══════════════════════════════════════════════════
//  VOLCANIC ACTIVITY
// ═══════════════════════════════════════════════════

/**
 * Volcanic activity event with alert information.
 */
data class VolcanicEvent(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Int,
    val country: String,
    val alertLevel: VolcanoAlertLevel,
    val colorCode: VolcanoColorCode,
    val lastUpdate: Long,
    val distanceFromUserKm: Double,
    val description: String,
    val source: String,                // "NASA_EONET", "USGS", etc.
    val type: String                   // stratovolcano, shield, caldera, etc.
) {
    val isNearby: Boolean get() = distanceFromUserKm <= 200.0
    val isDangerous: Boolean get() = alertLevel >= VolcanoAlertLevel.WATCH

    /**
     * Estimated danger zone radius based on alert level and volcano type.
     */
    val dangerZoneRadiusKm: Double get() = when (alertLevel) {
        VolcanoAlertLevel.WARNING -> 30.0
        VolcanoAlertLevel.WATCH -> 15.0
        VolcanoAlertLevel.ADVISORY -> 5.0
        VolcanoAlertLevel.NORMAL -> 2.0
    }
}

enum class VolcanoAlertLevel(
    val label: String,
    val labelId: String,
    val icon: String,
    val colorHex: Long,
    val level: Int
) {
    NORMAL("Normal", "Normal", "🟢", 0xFF4CAF50, 0),
    ADVISORY("Advisory", "Waspada", "🟡", 0xFFFFC107, 1),
    WATCH("Watch", "Siaga", "🟠", 0xFFFF9800, 2),
    WARNING("Warning", "Awas", "🔴", 0xFFF44336, 3);

    companion object {
        fun fromString(level: String?): VolcanoAlertLevel = when (level?.uppercase()) {
            "WARNING", "RED", "AWAS" -> WARNING
            "WATCH", "ORANGE", "SIAGA" -> WATCH
            "ADVISORY", "YELLOW", "WASPADA" -> ADVISORY
            else -> NORMAL
        }
    }

}

enum class VolcanoColorCode(
    val label: String,
    val colorHex: Long
) {
    GREEN("Green", 0xFF4CAF50),
    YELLOW("Yellow", 0xFFFFC107),
    ORANGE("Orange", 0xFFFF9800),
    RED("Red", 0xFFF44336),
    UNKNOWN("Unknown", 0xFF9E9E9E);

    companion object {
        fun fromString(code: String?): VolcanoColorCode = when (code?.uppercase()) {
            "GREEN" -> GREEN
            "YELLOW" -> YELLOW
            "ORANGE" -> ORANGE
            "RED" -> RED
            else -> UNKNOWN
        }
    }
}

/**
 * Nearby known volcano for awareness display.
 */
data class NearbyVolcano(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Int,
    val country: String,
    val type: String,
    val lastEruption: String?,
    val distanceFromUserKm: Double,
    val isActive: Boolean
)

// ═══════════════════════════════════════════════════
//  HIGH WAVE WARNING (Maritime Safety)
// ═══════════════════════════════════════════════════

/**
 * High wave warning for maritime operations.
 * Used by fishermen and sea travelers.
 * NOTE: Not categorized as disaster — no vibration alert.
 */
data class HighWaveWarning(
    val warningLevel: WaveWarningLevel,
    val currentWaveHeight: Double,
    val maxWaveHeightForecast: Double,
    val currentSwellHeight: Double,
    val maxWindSpeed: Double,
    val maxGustSpeed: Double,
    val peakWaveTime: String?,       // ISO time of peak wave
    val description: String,
    val recommendation: String,
    val hourlyForecast: List<HourlyWaveForecast>
) {
    val isActive: Boolean get() = warningLevel > WaveWarningLevel.CALM
}

data class HourlyWaveForecast(
    val time: String,
    val waveHeight: Double,
    val swellHeight: Double,
    val windSpeed: Double,
    val gustSpeed: Double,
    val warningLevel: WaveWarningLevel
)

enum class WaveWarningLevel(
    val label: String,
    val labelId: String,
    val icon: String,
    val colorHex: Long,
    val level: Int
) {
    CALM("Calm", "Tenang", "🟢", 0xFF4CAF50, 0),
    MODERATE("Moderate", "Sedang", "🟡", 0xFFFFC107, 1),
    ROUGH("Rough", "Bergelombang", "🟠", 0xFFFF9800, 2),
    VERY_ROUGH("Very Rough", "Sangat Bergelombang", "🔴", 0xFFF44336, 3),
    HIGH("High Seas", "Laut Tinggi", "🟣", 0xFF9C27B0, 4);

    companion object {
        fun fromWaveHeight(height: Double): WaveWarningLevel = when {
            height >= 6.0 -> HIGH
            height >= 4.0 -> VERY_ROUGH
            height >= 2.5 -> ROUGH
            height >= 1.25 -> MODERATE
            else -> CALM
        }
    }

}

// ═══════════════════════════════════════════════════
//  IMPACT AREA VISUALIZATION
// ═══════════════════════════════════════════════════

/**
 * Disaster impact area for interactive visualization.
 * Represents a circular/polygonal zone affected by a disaster.
 */
data class DisasterImpactArea(
    val id: String,
    val type: ImpactAreaType,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusKm: Double,
    val severity: ImpactSeverity,
    val title: String,
    val description: String,
    val timestamp: Long,
    val source: String,
    /** Zones for graduated impact (inner = severe, outer = moderate, etc.) */
    val zones: List<ImpactZone>,
    /** Whether user is within impact area */
    val userInZone: Boolean,
    val distanceFromUserKm: Double
)

data class ImpactZone(
    val label: String,
    val labelId: String,
    val radiusKm: Double,
    val colorHex: Long,
    val alpha: Float,
    val description: String,
    val descriptionId: String
)

enum class ImpactAreaType(
    val label: String,
    val labelId: String,
    val icon: String,
    val colorHex: Long
) {
    EARTHQUAKE("Earthquake", "Gempa Bumi", "📳", 0xFFD84315),
    TSUNAMI("Tsunami", "Tsunami", "🌊", 0xFF01579B),
    VOLCANIC_ERUPTION("Volcanic Eruption", "Erupsi Gunung Api", "🌋", 0xFFBF360C),
    FLOOD("Flood", "Banjir", "🌊", 0xFF1565C0),
    LANDSLIDE("Landslide", "Tanah Longsor", "⛰️", 0xFF795548),
    CYCLONE("Tropical Cyclone", "Siklon Tropis", "🌀", 0xFF880E4F)
}

enum class ImpactSeverity(
    val label: String,
    val labelId: String,
    val colorHex: Long,
    val level: Int
) {
    MINOR("Minor", "Ringan", 0xFFFFC107, 0),
    MODERATE("Moderate", "Sedang", 0xFFFF9800, 1),
    SEVERE("Severe", "Parah", 0xFFF44336, 2),
    CRITICAL("Critical", "Kritis", 0xFFD32F2F, 3),
    CATASTROPHIC("Catastrophic", "Bencana Besar", 0xFF880E4F, 4);

    companion object {
        fun fromMagnitude(mag: Double): ImpactSeverity = when {
            mag >= 8.0 -> CATASTROPHIC
            mag >= 7.0 -> CRITICAL
            mag >= 6.0 -> SEVERE
            mag >= 5.0 -> MODERATE
            else -> MINOR
        }

        fun fromVolcanoAlert(level: VolcanoAlertLevel): ImpactSeverity = when (level) {
            VolcanoAlertLevel.WARNING -> CRITICAL
            VolcanoAlertLevel.WATCH -> SEVERE
            VolcanoAlertLevel.ADVISORY -> MODERATE
            VolcanoAlertLevel.NORMAL -> MINOR
        }
    }
}

// ═══════════════════════════════════════════════════
//  KNOWN ACTIVE VOLCANOES DATABASE (Indonesia focus)
// ═══════════════════════════════════════════════════

/**
 * Embedded database of known active volcanoes, primarily Indonesia.
 * Used when USGS API is unavailable or for enrichment.
 *
 * Sources: Smithsonian GVP, PVMBG (Pusat Vulkanologi dan Mitigasi Bencana Geologi)
 */
object KnownVolcanoes {
    data class VolcanoRecord(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val elevation: Int,
        val country: String,
        val type: String,
        val lastEruption: String?
    )

    /** Major active volcanoes in Indonesia and nearby regions */
    val list: List<VolcanoRecord> = listOf(
        // ═══ Sumatra ═══
        VolcanoRecord("Sinabung", 3.17, 98.39, 2460, "Indonesia", "Stratovolcano", "2021"),
        VolcanoRecord("Sibayak", 3.23, 98.52, 2212, "Indonesia", "Stratovolcano", "1881"),
        VolcanoRecord("Marapi", -0.38, 100.47, 2891, "Indonesia", "Complex Volcano", "2023"),
        VolcanoRecord("Kerinci", -1.70, 101.26, 3800, "Indonesia", "Stratovolcano", "2024"),
        VolcanoRecord("Talang", -0.98, 100.68, 2597, "Indonesia", "Stratovolcano", "2007"),

        // ═══ Java ═══
        VolcanoRecord("Krakatau (Anak)", -6.10, 105.42, 155, "Indonesia", "Caldera", "2023"),
        VolcanoRecord("Tangkuban Parahu", -6.77, 107.60, 2084, "Indonesia", "Stratovolcano", "2019"),
        VolcanoRecord("Papandayan", -7.32, 107.73, 2665, "Indonesia", "Stratovolcano", "2002"),
        VolcanoRecord("Galunggung", -7.25, 108.06, 2168, "Indonesia", "Stratovolcano", "1984"),
        VolcanoRecord("Slamet", -7.24, 109.21, 3432, "Indonesia", "Stratovolcano", "2014"),
        VolcanoRecord("Dieng (Complex)", -7.20, 109.92, 2565, "Indonesia", "Complex Volcano", "2011"),
        VolcanoRecord("Merapi", -7.54, 110.45, 2968, "Indonesia", "Stratovolcano", "2024"),
        VolcanoRecord("Kelud", -7.93, 112.31, 1731, "Indonesia", "Stratovolcano", "2014"),
        VolcanoRecord("Bromo (Tengger)", -7.94, 112.95, 2329, "Indonesia", "Caldera", "2019"),
        VolcanoRecord("Semeru", -8.11, 112.92, 3676, "Indonesia", "Stratovolcano", "2024"),
        VolcanoRecord("Raung", -8.13, 114.04, 3332, "Indonesia", "Stratovolcano", "2021"),
        VolcanoRecord("Kawah Ijen", -8.06, 114.24, 2799, "Indonesia", "Stratovolcano", "1999"),

        // ═══ Bali & Nusa Tenggara ═══
        VolcanoRecord("Agung", -8.34, 115.51, 3142, "Indonesia", "Stratovolcano", "2019"),
        VolcanoRecord("Batur", -8.24, 115.38, 1717, "Indonesia", "Caldera", "2000"),
        VolcanoRecord("Rinjani", -8.42, 116.47, 3726, "Indonesia", "Stratovolcano", "2016"),
        VolcanoRecord("Lewotobi", -8.54, 122.77, 1703, "Indonesia", "Complex Volcano", "2024"),
        VolcanoRecord("Ile Ape", -8.63, 123.68, 1637, "Indonesia", "Stratovolcano", "2004"),

        // ═══ Sulawesi ═══
        VolcanoRecord("Lokon-Empung", 1.36, 124.79, 1580, "Indonesia", "Stratovolcano", "2015"),
        VolcanoRecord("Soputan", 1.11, 124.73, 1784, "Indonesia", "Stratovolcano", "2020"),
        VolcanoRecord("Karangetang", 2.78, 125.40, 1784, "Indonesia", "Stratovolcano", "2020"),

        // ═══ Maluku & Papua ═══
        VolcanoRecord("Gamalama", 0.80, 127.33, 1715, "Indonesia", "Stratovolcano", "2018"),
        VolcanoRecord("Dukono", 1.69, 127.87, 1335, "Indonesia", "Complex Volcano", "2024"),
        VolcanoRecord("Ibu", 1.49, 127.63, 1325, "Indonesia", "Stratovolcano", "2024"),

        // ═══ Regional (Southeast Asia / Pacific Ring of Fire) ═══
        VolcanoRecord("Pinatubo", 15.13, 120.35, 1486, "Philippines", "Stratovolcano", "1991"),
        VolcanoRecord("Mayon", 13.26, 123.69, 2462, "Philippines", "Stratovolcano", "2024"),
        VolcanoRecord("Taal", 14.00, 120.99, 311, "Philippines", "Caldera", "2022"),
        VolcanoRecord("Fuji", 35.36, 138.73, 3776, "Japan", "Stratovolcano", "1707"),
        VolcanoRecord("Sakurajima", 31.58, 130.66, 1117, "Japan", "Stratovolcano", "2024"),
        VolcanoRecord("Ruang", 2.30, 125.37, 725, "Indonesia", "Stratovolcano", "2024")
    )

    /**
     * Find volcanoes within a given radius of a location.
     */
    fun findNearby(lat: Double, lon: Double, maxDistanceKm: Double = 300.0): List<VolcanoRecord> {
        return list.filter { v ->
            haversineDistance(lat, lon, v.latitude, v.longitude) <= maxDistanceKm
        }
    }

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }
}

// ═══════════════════════════════════════════════════
//  BMKG EARTHQUAKE (Indonesian Official Source)
// ═══════════════════════════════════════════════════

/**
 * Processed BMKG earthquake event for UI display.
 * Prioritized for Indonesian users as the official national data source.
 */
data class BmkgEarthquakeEvent(
    val magnitude: Double,
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double,
    val time: Long,
    val dateString: String,        // "27 Feb 2026"
    val timeString: String,        // "03:12:27 WIB"
    val region: String,            // "Pusat gempa berada di darat 35 km BaratLaut Kaimana"
    val potential: String,         // Tsunami potential assessment
    val feltReport: String?,       // "II-III Kaimana"
    val shakemapUrl: String?,      // URL to BMKG shakemap image
    val distanceFromUserKm: Double,
    val source: String = "BMKG"
) {
    val isNearby: Boolean get() = distanceFromUserKm <= 500.0
    val isSignificant: Boolean get() = magnitude >= 5.0
    val hasTsunamiPotential: Boolean get() =
        potential.contains("tsunami", ignoreCase = true) &&
        !potential.contains("tidak", ignoreCase = true) &&
        !potential.contains("no", ignoreCase = true)
}

// ═══════════════════════════════════════════════════
//  CROWDSOURCED DISASTER REPORTS (PetaBencana.id)
// ═══════════════════════════════════════════════════

/**
 * Crowdsourced disaster report from PetaBencana.id.
 * Real-time community reports for Indonesian disasters.
 */
data class CrowdsourcedDisasterReport(
    val id: String,
    val disasterType: CrowdsourcedDisasterType,
    val latitude: Double,
    val longitude: Double,
    val time: Long,
    val text: String,              // User-submitted description
    val imageUrl: String?,         // Photo evidence
    val cityName: String?,         // From tags
    val provinceCode: String?,     // "ID-JK", "ID-JB", etc.
    val distanceFromUserKm: Double,
    val isTraining: Boolean,       // Training/test data flag
    val floodDepthCm: Int? = null, // For flood reports
    val structureDamage: Int? = null, // For earthquake reports (0-4)
    val windImpact: Int? = null,   // For wind reports (0-1)
    val evacuationArea: Boolean? = null, // For volcano reports
    val evacuationNumber: Int? = null, // Number of evacuees
    val volcanicSigns: List<Int>? = null, // Volcanic signs observed
    val accessibilityFailure: Int? = null, // Road accessibility (0-4) for earthquake
    val roadCondition: Int? = null, // Road condition for earthquake
    val severityPoints: Int? = null, // General severity score
    val source: String = "PetaBencana.id"
) {
    val isNearby: Boolean get() = distanceFromUserKm <= 100.0
    val isReal: Boolean get() = !isTraining

    val floodSeverityLabel: String? get() = floodDepthCm?.let {
        when {
            it >= 150 -> "Sangat Dalam (≥150cm)"
            it >= 70 -> "Dalam (70-150cm)"
            it >= 30 -> "Sedang (30-70cm)"
            else -> "Rendah (<30cm)"
        }
    }

    val floodSeverityLevel: Int get() = when {
        (floodDepthCm ?: 0) >= 150 -> 4
        (floodDepthCm ?: 0) >= 70 -> 3
        (floodDepthCm ?: 0) >= 30 -> 2
        (floodDepthCm ?: 0) > 0 -> 1
        else -> 0
    }

    val structureDamageLabel: String? get() = structureDamage?.let {
        when (it) {
            0 -> "Tidak Ada Kerusakan"
            1 -> "Ringan"
            2 -> "Sedang"
            3 -> "Berat"
            4 -> "Sangat Berat"
            else -> null
        }
    }

    val windImpactLabel: String? get() = windImpact?.let {
        when (it) {
            0 -> "Dampak Ringan"
            1 -> "Dampak Signifikan"
            else -> null
        }
    }

    val volcanicSignsLabels: List<String> get() = volcanicSigns?.map {
        when (it) {
            0 -> "Asap/Abu"
            1 -> "Lava"
            2 -> "Gempa Vulkanik"
            3 -> "Suara Gemuruh"
            4 -> "Bau Belerang"
            else -> "Tanda #$it"
        }
    } ?: emptyList()

    val emoji: String get() = disasterType.emoji
}

/**
 * Supported disaster types from PetaBencana.id
 */
enum class CrowdsourcedDisasterType(
    val apiKey: String,
    val labelEn: String,
    val labelId: String,
    val emoji: String
) {
    FLOOD("flood", "Flood", "Banjir", "🌊"),
    EARTHQUAKE("earthquake", "Earthquake", "Gempa Bumi", "🌍"),
    WIND("wind", "Strong Wind", "Angin Kencang", "💨"),
    HAZE("haze", "Haze", "Kabut Asap", "🌫️"),
    FIRE("fire", "Forest Fire", "Kebakaran Hutan", "🔥"),
    VOLCANO("volcano", "Volcano", "Gunung Api", "🌋"),
    UNKNOWN("unknown", "Unknown", "Tidak Diketahui", "⚠️");

    companion object {
        fun fromApiKey(key: String?): CrowdsourcedDisasterType {
            return values().find { it.apiKey == key } ?: UNKNOWN
        }
    }

    val localizedLabel: String get() {
        val isId = AppLocaleManager.locale == com.weather.forecast.data.locale.AppLocale.ID
        return if (isId) labelId else labelEn
    }
}

// ═══════════════════════════════════════════════════
//  LANDSLIDE RISK ASSESSMENT
// ═══════════════════════════════════════════════════

/**
 * Landslide risk assessment based on meteorological,
 * hydrological, and seismic factors.
 *
 * Data Sources:
 * - Open-Meteo Weather API (rainfall, soil moisture)
 * - USGS Earthquake data (seismic trigger)
 */
data class LandslideRiskAssessment(
    val riskLevel: LandslideRiskLevel,
    val riskScore: Int,                    // 0-100
    val factors: List<LandslideRiskFactor>,

    // ── Rainfall Data ──
    val currentRainRate: Double,            // mm/hr
    val rainfall24h: Double,                // mm accumulated
    val rainfall72h: Double,                // mm accumulated
    val rainfallForecast24h: Double,        // mm expected next 24h

    // ── Soil Moisture Data ──
    val soilMoistureSurface: Double,        // m³/m³ (0-7cm)
    val soilMoistureMiddle: Double,         // m³/m³ (7-28cm)
    val soilMoistureDeep: Double,           // m³/m³ (28-100cm)
    val soilSaturationPercent: Double,      // 0-100%

    // ── Seismic Trigger ──
    val recentNearbyQuakes: Int,            // Count in past 7 days within 100km
    val maxNearbyMagnitude: Double,         // Strongest nearby quake

    // ── Forecast ──
    val hourlyRainForecast: List<HourlyRainForecast> = emptyList(),
    val description: String,
    val recommendation: String
)

data class LandslideRiskFactor(
    val name: String,
    val nameId: String,
    val score: Int,          // Individual factor score
    val maxScore: Int,       // Max possible for this factor
    val description: String,
    val descriptionId: String
)

data class HourlyRainForecast(
    val time: String,
    val rain: Double,         // mm
    val probability: Int      // 0-100%
)

enum class LandslideRiskLevel(
    val labelEn: String,
    val labelId: String,
    val colorHex: Long,
    val emoji: String
) {
    LOW("Low Risk", "Risiko Rendah", 0xFF4CAF50, "\u2705"),
    MODERATE("Moderate Risk", "Risiko Sedang", 0xFFFFEB3B, "\u26A0\uFE0F"),
    HIGH("High Risk", "Risiko Tinggi", 0xFFFF9800, "\uD83D\uDFE0"),
    VERY_HIGH("Very High Risk", "Risiko Sangat Tinggi", 0xFFF44336, "\uD83D\uDD34"),
    CRITICAL("Critical", "Kritis", 0xFF880E4F, "\u203C\uFE0F");

    companion object {
        fun fromScore(score: Int): LandslideRiskLevel = when {
            score >= 80 -> CRITICAL
            score >= 60 -> VERY_HIGH
            score >= 40 -> HIGH
            score >= 20 -> MODERATE
            else -> LOW
        }
    }

    val localizedLabel: String get() {
        val isId = AppLocaleManager.locale == com.weather.forecast.data.locale.AppLocale.ID
        return if (isId) labelId else labelEn
    }
}
