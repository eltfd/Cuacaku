package com.weather.forecast.data.model

/**
 * Active Disaster Monitoring — Data Models
 *
 * Model untuk memantau bencana yang sedang/sudah terjadi,
 * termasuk status recovery dan timeline sampai normal kembali.
 *
 * Data Source: ReliefWeb API (UN OCHA) + Open-Meteo Flood
 * Lifecycle: ACTIVE → RECOVERY → RESOLVED → (auto-hide)
 */

/**
 * Container utama pemantauan bencana aktif
 */
data class ActiveDisasterMonitor(
    /** Daftar bencana yang sedang dipantau */
    val disasters: List<ActiveDisaster>,
    /** Jumlah bencana aktif */
    val activeCount: Int = disasters.count { it.phase == DisasterPhase.ACTIVE },
    /** Jumlah dalam recovery */
    val recoveryCount: Int = disasters.count { it.phase == DisasterPhase.RECOVERY },
    /** Jumlah yang sudah resolved (menunggu auto-hide) */
    val resolvedCount: Int = disasters.count { it.phase == DisasterPhase.RESOLVED },
    /** Timestamp terakhir di-update */
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Satu event bencana aktif
 */
data class ActiveDisaster(
    /** ID unik event */
    val id: String,
    /** Judul deskriptif */
    val title: String,
    /** Jenis bencana */
    val type: ActiveDisasterType,
    /** Fase saat ini */
    val phase: DisasterPhase,
    /** Lokasi terdampak */
    val locations: List<AffectedLocation>,
    /** Severity / tingkat keparahan */
    val severity: DisasterSeverity,
    /** Kapan bencana dimulai */
    val startDate: Long,
    /** Kapan terakhir di-update */
    val lastUpdate: Long,
    /** Estimasi selesai recovery (epoch ms, null jika belum diketahui) */
    val estimatedRecoveryDate: Long? = null,
    /** Kapan fase resolved dimulai (untuk auto-hide timer) */
    val resolvedDate: Long? = null,
    /** Berapa hari lagi tampilan akan hilang setelah resolved */
    val daysUntilHidden: Int = 0,
    /** Deskripsi situasi terkini */
    val currentSituation: String,
    /** Progress recovery (0.0 – 1.0) */
    val recoveryProgress: Float = 0f,
    /** Timeline events */
    val timeline: List<DisasterTimelineEvent> = emptyList(),
    /** Impact summary */
    val impact: DisasterImpact? = null,
    /** Sumber data */
    val source: String = "ReliefWeb / Open-Meteo",
    /** URL sumber untuk info lebih lanjut */
    val sourceUrl: String? = null
)

/**
 * Lokasi terdampak
 */
data class AffectedLocation(
    val name: String,
    val province: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Radius dampak dalam km (approx) */
    val radiusKm: Double? = null
)

/**
 * Jenis bencana untuk monitoring aktif
 */
enum class ActiveDisasterType(
    val label: String,
    val icon: String,
    val colorHex: Long
) {
    FLOOD("Flood", "🌊", 0xFF1565C0),
    FLASH_FLOOD("Flash Flood", "⚡🌊", 0xFF0D47A1),
    LANDSLIDE("Landslide", "⛰️", 0xFF795548),
    TIDAL_FLOOD("Tidal Flood", "🌊🌙", 0xFF00838F),
    CYCLONE("Tropical Cyclone", "🌀", 0xFF880E4F),
    EARTHQUAKE("Earthquake", "📳", 0xFFD84315),
    VOLCANIC("Volcanic Eruption", "🌋", 0xFFBF360C),
    TSUNAMI("Tsunami", "🌊💨", 0xFF01579B),
    DROUGHT("Drought", "☀️🔥", 0xFFE65100),
    OTHER("Other Disaster", "⚠️", 0xFF616161)
}

/**
 * Fase lifecycle bencana
 */
enum class DisasterPhase(
    val label: String,
    val labelId: String,
    val icon: String,
    val colorHex: Long
) {
    /** Bencana sedang terjadi / dampak masih terasa */
    ACTIVE("Active", "Aktif", "🔴", 0xFFD32F2F),
    /** Bencana sudah mereda, dalam proses pemulihan */
    RECOVERY("Recovery", "Pemulihan", "🟠", 0xFFFF9800),
    /** Pemulihan selesai, kondisi kembali normal */
    RESOLVED("Resolved", "Pulih", "🟢", 0xFF4CAF50)
}

/**
 * Tingkat keparahan bencana
 */
enum class DisasterSeverity(
    val label: String,
    val labelId: String,
    val level: Int
) {
    MINOR("Minor", "Ringan", 1),
    MODERATE("Moderate", "Sedang", 2),
    SEVERE("Severe", "Parah", 3),
    CRITICAL("Critical", "Kritis", 4)
}

/**
 * Event dalam timeline bencana
 */
data class DisasterTimelineEvent(
    val timestamp: Long,
    val phase: DisasterPhase,
    val description: String,
    /** Icon representatif */
    val icon: String = "📌"
)

/**
 * Ringkasan dampak bencana
 */
data class DisasterImpact(
    /** Jumlah orang terdampak (estimasi) */
    val affectedPeople: Int? = null,
    /** Area terdampak dalam km² */
    val affectedAreaKm2: Double? = null,
    /** Deskripsi infrastruktur terdampak */
    val infrastructureDamage: String? = null,
    /** Level kebutuhan bantuan */
    val aidStatus: String? = null
)

/**
 * Konfigurasi auto-hide setelah resolved
 */
object DisasterDisplayConfig {
    /** Berapa hari event RESOLVED tetap ditampilkan sebelum hilang */
    const val RESOLVED_DISPLAY_DAYS_MINOR = 3
    const val RESOLVED_DISPLAY_DAYS_MODERATE = 5
    const val RESOLVED_DISPLAY_DAYS_SEVERE = 7
    const val RESOLVED_DISPLAY_DAYS_CRITICAL = 14

    /** Berapa hari event RECOVERY maksimal ditampilkan tanpa update */
    const val STALE_RECOVERY_DAYS = 30

    fun getDisplayDays(severity: DisasterSeverity): Int = when (severity) {
        DisasterSeverity.MINOR -> RESOLVED_DISPLAY_DAYS_MINOR
        DisasterSeverity.MODERATE -> RESOLVED_DISPLAY_DAYS_MODERATE
        DisasterSeverity.SEVERE -> RESOLVED_DISPLAY_DAYS_SEVERE
        DisasterSeverity.CRITICAL -> RESOLVED_DISPLAY_DAYS_CRITICAL
    }
}
