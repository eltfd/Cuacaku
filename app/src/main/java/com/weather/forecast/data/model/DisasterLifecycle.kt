package com.weather.forecast.data.model

import com.weather.forecast.data.locale.AppLocaleManager

/**
 * ══════════════════════════════════════════════════════════
 * Disaster Lifecycle Data Models
 * ══════════════════════════════════════════════════════════
 *
 * Complete disaster lifecycle management:
 * 1. NORMAL — Daily monitoring, no active threats
 * 2. EARLY_WARNING — Pre-disaster, forecasting indicates rising risk
 * 3. ACTIVE_DISASTER — Disaster is occurring, interactive displays
 * 4. POST_DISASTER — After disaster, relief & evacuation info
 *
 * Also includes:
 * - SOS system (smart triggering with AI behavior analysis)
 * - Relief points / evacuation shelters
 * - User movement tracking for SOS eligibility
 */

// ═══════════════════════════════════════════════════
//  DISASTER PHASE
// ═══════════════════════════════════════════════════

/**
 * Current phase in the disaster lifecycle.
 * Determines which UI sections and features are shown.
 */
enum class DisasterLifecyclePhase(
    val label: String,
    val labelId: String,
    val icon: String,
    val colorHex: Long,
    val level: Int
) {
    NORMAL(
        "Normal", "Normal", "✅", 0xFF4CAF50, 0
    ),
    EARLY_WARNING(
        "Early Warning", "Peringatan Dini", "⚠️", 0xFFFFC107, 1
    ),
    ACTIVE_DISASTER(
        "Active Disaster", "Bencana Aktif", "🚨", 0xFFF44336, 2
    ),
    POST_DISASTER(
        "Post Disaster", "Pasca Bencana", "🔧", 0xFF2196F3, 3
    )
}

/**
 * Per-disaster-type lifecycle state.
 */
data class DisasterLifecycleState(
    val type: DisasterLifecycleType,
    val phase: DisasterLifecyclePhase,
    val summary: String,
    val details: String,
    val earlyWarning: EarlyWarningData? = null,
    val activeDisasterInfo: ActiveDisasterInfo? = null,
    val postDisasterInfo: PostDisasterInfo? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class DisasterLifecycleType(
    val label: String,
    val labelId: String,
    val icon: String
) {
    EARTHQUAKE("Earthquake", "Gempa Bumi", "📳"),
    TSUNAMI("Tsunami", "Tsunami", "🌊"),
    VOLCANO("Volcano", "Gunung Api", "🌋"),
    FLOOD("Flood", "Banjir", "💧"),
    LANDSLIDE("Landslide", "Tanah Longsor", "⛰️"),
    CYCLONE("Cyclone", "Siklon", "🌀")
}

// ═══════════════════════════════════════════════════
//  EARLY WARNING (Pre-Disaster)
// ═══════════════════════════════════════════════════

/**
 * Early warning data with escalation tracking.
 */
data class EarlyWarningData(
    val threatLevel: EarlyWarningLevel,
    val indicators: List<WarningIndicator>,
    val preparednessChecklist: List<PreparednessItem>,
    val estimatedOnsetHours: Int? = null,
    val escalationTrend: EscalationTrend = EscalationTrend.STABLE,
    val lastEscalation: Long? = null
)

enum class EarlyWarningLevel(
    val label: String,
    val labelId: String,
    val colorHex: Long,
    val level: Int
) {
    ADVISORY("Advisory", "Waspada", 0xFFFFC107, 1),
    WATCH("Watch", "Siaga", 0xFFFF9800, 2),
    WARNING("Warning", "Peringatan", 0xFFF44336, 3),
    EMERGENCY("Emergency", "Darurat", 0xFFD32F2F, 4)
}

enum class EscalationTrend {
    DECREASING,
    STABLE,
    INCREASING,
    RAPID_INCREASE
}

data class WarningIndicator(
    val name: String,
    val currentValue: String,
    val threshold: String,
    val isExceeded: Boolean,
    val trend: EscalationTrend
)

data class PreparednessItem(
    val text: String,
    val icon: String,
    val isPriority: Boolean = false
)

// ═══════════════════════════════════════════════════
//  ACTIVE DISASTER
// ═══════════════════════════════════════════════════

/**
 * Information displayed during an active disaster.
 */
data class ActiveDisasterInfo(
    val severity: ImpactSeverity,
    val impactArea: DisasterImpactArea? = null,
    val startTime: Long,
    val isUserInDangerZone: Boolean = false,
    val evacuationDirections: List<EvacuationDirection> = emptyList(),
    val emergencyContacts: List<EmergencyContact> = emptyList()
)

data class EvacuationDirection(
    val direction: String,
    val directionId: String,
    val distanceKm: Double,
    val description: String,
    val descriptionId: String
)

data class EmergencyContact(
    val name: String,
    val number: String,
    val type: EmergencyContactType
)

enum class EmergencyContactType {
    NATIONAL_EMERGENCY,     // 112
    DISASTER_AGENCY,        // BNPB: 117
    SEARCH_RESCUE,          // BASARNAS: 115
    MEDICAL,                // 118/119
    POLICE,                 // 110
    FIRE                    // 113
}

// ═══════════════════════════════════════════════════
//  POST DISASTER (Relief & Evacuation)
// ═══════════════════════════════════════════════════

/**
 * Post-disaster information including relief points.
 */
data class PostDisasterInfo(
    val reliefPoints: List<ReliefPoint>,
    val recentReports: List<DisasterReport>,
    val recoveryStatus: String,
    val lastUpdated: Long
)

/**
 * Relief/evacuation point (posko bencana).
 */
data class ReliefPoint(
    val id: String,
    val name: String,
    val type: ReliefPointType,
    val latitude: Double,
    val longitude: Double,
    val distanceFromUserKm: Double,
    val address: String,
    val description: String,
    val source: String,
    val isVerified: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class ReliefPointType(
    val label: String,
    val labelId: String,
    val icon: String
) {
    EVACUATION_SHELTER("Evacuation Shelter", "Posko Pengungsi", "🏕️"),
    MEDICAL_POST("Medical Post", "Pos Kesehatan", "🏥"),
    FOOD_DISTRIBUTION("Food Distribution", "Distribusi Makanan", "🍚"),
    LOGISTICS_CENTER("Logistics Center", "Pusat Logistik", "📦"),
    COMMAND_CENTER("Command Center", "Posko Komando", "🎯"),
    SEARCH_RESCUE("SAR Base", "Posko SAR", "🚁"),
    WATER_SUPPLY("Clean Water", "Air Bersih", "💧"),
    COMMUNICATION("Communication Hub", "Posko Komunikasi", "📡")
}

/**
 * Disaster report from ReliefWeb.
 */
data class DisasterReport(
    val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val date: Long,
    val source: String
)

// ═══════════════════════════════════════════════════
//  SOS SYSTEM
// ═══════════════════════════════════════════════════

/**
 * SOS system state — smart trigger with AI behavior analysis.
 *
 * SOS button only appears when:
 * 1. User is in a CRITICAL disaster impact zone
 * 2. User has not moved significantly for several hours
 * 3. AI behavior analysis confirms distress pattern
 *
 * This prevents false SOS signals while ensuring help
 * reaches those who truly need it.
 */
data class SOSState(
    val isEligible: Boolean = false,
    val eligibilityReason: SOSEligibilityReason = SOSEligibilityReason.NOT_IN_DANGER_ZONE,
    val isActivated: Boolean = false,
    val activatedAt: Long? = null,
    val userMovementData: UserMovementData? = null,
    val behaviorAnalysis: BehaviorAnalysis? = null,
    /** Auto-deactivate after 24h if no confirmation */
    val expiresAt: Long? = null
)

enum class SOSEligibilityReason(
    val message: String,
    val messageId: String
) {
    ELIGIBLE(
        "SOS available — You appear to be trapped in a disaster zone",
        "SOS tersedia — Anda terdeteksi terjebak di zona bencana"
    ),
    NOT_IN_DANGER_ZONE(
        "You are not in a critical disaster zone",
        "Anda tidak berada di zona bencana kritis"
    ),
    USER_IS_MOVING(
        "Movement detected — SOS not needed if you can move",
        "Pergerakan terdeteksi — SOS tidak diperlukan jika Anda bisa bergerak"
    ),
    INSUFFICIENT_DATA(
        "Collecting location data for safety analysis...",
        "Mengumpulkan data lokasi untuk analisis keselamatan..."
    ),
    AI_LOW_CONFIDENCE(
        "AI analysis indicates you are likely safe",
        "Analisis AI menunjukkan Anda kemungkinan aman"
    ),
    RECENTLY_DEACTIVATED(
        "SOS was recently deactivated",
        "SOS baru saja dinonaktifkan"
    )
}

// ═══════════════════════════════════════════════════
//  USER MOVEMENT & BEHAVIOR ANALYSIS
// ═══════════════════════════════════════════════════

/**
 * User movement tracking data for SOS eligibility.
 */
data class UserMovementData(
    val locationHistory: List<LocationRecord>,
    val lastSignificantMovement: Long?,
    val hoursStationary: Double,
    val totalDistanceLast6Hours: Double,
    val maxSpeed6Hours: Double,
    val isStationary: Boolean
) {
    /**
     * User is considered "stuck" if stationary for 2+ hours
     * in a disaster zone with minimal movement (< 100m total).
     */
    val isLikelyTrapped: Boolean get() =
        isStationary && hoursStationary >= 2.0 && totalDistanceLast6Hours < 0.1
}

data class LocationRecord(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float
)

/**
 * AI-based behavior pattern analysis.
 *
 * Uses a rule-based + statistical approach to analyze:
 * - Movement patterns (velocity, acceleration, trajectory)
 * - Stationarity duration and context
 * - Proximity to disaster epicenter
 * - Time of day analysis
 * - Phone usage patterns (sensor availability)
 *
 * Confidence threshold for SOS eligibility: >= 0.7
 */
data class BehaviorAnalysis(
    val distressScore: Double,          // 0.0 – 1.0
    val confidenceLevel: Double,        // 0.0 – 1.0
    val factors: List<BehaviorFactor>,
    val recommendation: BehaviorRecommendation,
    val analysisTimestamp: Long
) {
    val isDistressed: Boolean get() = distressScore >= 0.7 && confidenceLevel >= 0.6
}

data class BehaviorFactor(
    val name: String,
    val weight: Double,
    val score: Double,
    val description: String
)

enum class BehaviorRecommendation(
    val label: String,
    val labelId: String
) {
    SAFE("Likely Safe", "Kemungkinan Aman"),
    MONITOR("Monitoring Required", "Perlu Dipantau"),
    AT_RISK("At Risk — Consider SOS", "Berisiko — Pertimbangkan SOS"),
    DISTRESSED("Likely Trapped — SOS Recommended", "Kemungkinan Terjebak — SOS Disarankan")
}

// ═══════════════════════════════════════════════════
//  EMERGENCY CONTACTS DATABASE
// ═══════════════════════════════════════════════════

/**
 * Emergency contacts for Indonesia and international.
 */
object EmergencyContacts {
    val indonesia = listOf(
        EmergencyContact("Darurat Nasional", "112", EmergencyContactType.NATIONAL_EMERGENCY),
        EmergencyContact("BNPB", "117", EmergencyContactType.DISASTER_AGENCY),
        EmergencyContact("BASARNAS", "115", EmergencyContactType.SEARCH_RESCUE),
        EmergencyContact("Ambulans", "118", EmergencyContactType.MEDICAL),
        EmergencyContact("Polisi", "110", EmergencyContactType.POLICE),
        EmergencyContact("Pemadam Kebakaran", "113", EmergencyContactType.FIRE)
    )

    val international = listOf(
        EmergencyContact("Emergency", "112", EmergencyContactType.NATIONAL_EMERGENCY),
        EmergencyContact("Emergency (US)", "911", EmergencyContactType.NATIONAL_EMERGENCY),
        EmergencyContact("Police", "110", EmergencyContactType.POLICE),
        EmergencyContact("Medical", "119", EmergencyContactType.MEDICAL)
    )

    fun getForLocale(): List<EmergencyContact> {
        return if (AppLocaleManager.locale == com.weather.forecast.data.locale.AppLocale.ID) indonesia else international
    }
}
