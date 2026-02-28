package com.weather.forecast.service

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weather.forecast.data.model.*
import kotlin.math.*

/**
 * ══════════════════════════════════════════════════════════
 * Smart SOS Manager
 * ══════════════════════════════════════════════════════════
 *
 * Intelligent SOS system that prevents false alarms while
 * ensuring help reaches those who truly need it.
 *
 * SOS button appears ONLY when ALL conditions are met:
 * 1. User is inside a CRITICAL disaster impact zone
 * 2. User has been stationary for >= 2 hours (minimal movement)
 * 3. AI behavior analysis confidence >= 0.6 with distress score >= 0.7
 *
 * ── AI Behavior Pattern Recognition ──
 * Uses a weighted multi-factor scoring system:
 *
 * Factor                    | Weight | Description
 * ─────────────────────────|────────|──────────────
 * Stationarity Duration     | 0.25   | Hours without significant movement
 * Disaster Zone Proximity   | 0.25   | Distance from disaster epicenter
 * Movement Pattern          | 0.20   | Speed, acceleration, trajectory
 * Time Context              | 0.10   | Night vs day (night = higher risk)
 * Impact Severity           | 0.15   | Severity of the active disaster
 * Historical Pattern        | 0.05   | Previous location behavior
 *
 * The scoring formula:
 *   distressScore = Σ (factor_weight × factor_score)
 *   where each factor_score is normalized to [0.0, 1.0]
 *
 * SOS eligibility threshold: distressScore >= 0.7 AND confidence >= 0.6
 */
class SOSManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "sos_manager"
        private const val KEY_LOCATION_HISTORY = "location_history"
        private const val KEY_SOS_ACTIVATED = "sos_activated"
        private const val KEY_SOS_ACTIVATED_AT = "sos_activated_at"
        private const val KEY_LAST_SOS_DEACTIVATED = "last_sos_deactivated"

        /** Max location records to keep */
        private const val MAX_HISTORY_SIZE = 360 // 6 hours at 1/min

        /** Minimum stationary hours before SOS eligibility */
        private const val MIN_STATIONARY_HOURS = 2.0

        /** Maximum movement in km to be considered "stationary" */
        private const val STATIONARY_THRESHOLD_KM = 0.1  // 100 meters

        /** Distress score threshold for SOS eligibility */
        private const val DISTRESS_THRESHOLD = 0.7

        /** Confidence threshold for SOS eligibility */
        private const val CONFIDENCE_THRESHOLD = 0.6

        /** Cooldown after SOS deactivation (1 hour) */
        private const val SOS_COOLDOWN_MS = 60 * 60 * 1000L

        /** SOS auto-expires after 24 hours */
        private const val SOS_EXPIRY_MS = 24 * 60 * 60 * 1000L
    }

    // ═══════════════════════════════════════════════════
    //  LOCATION TRACKING
    // ═══════════════════════════════════════════════════

    /**
     * Record a new location point.
     * Called periodically by WorkManager or foreground service.
     */
    fun recordLocation(latitude: Double, longitude: Double, accuracy: Float = 0f) {
        val history = getLocationHistory().toMutableList()
        history.add(LocationRecord(latitude, longitude, System.currentTimeMillis(), accuracy))

        // Keep only last MAX_HISTORY_SIZE records
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(0)
        }

        saveLocationHistory(history)
    }

    /**
     * Get the current user movement data analysis.
     */
    fun analyzeMovement(): UserMovementData {
        val history = getLocationHistory()
        if (history.size < 2) {
            return UserMovementData(
                locationHistory = history,
                lastSignificantMovement = null,
                hoursStationary = 0.0,
                totalDistanceLast6Hours = 0.0,
                maxSpeed6Hours = 0.0,
                isStationary = false
            )
        }

        val now = System.currentTimeMillis()
        val sixHoursAgo = now - 6 * 60 * 60 * 1000L

        // Calculate total distance in last 6 hours
        val recentHistory = history.filter { it.timestamp >= sixHoursAgo }
        var totalDistance = 0.0
        var maxSpeed = 0.0
        var lastSignificantMove: Long? = null

        for (i in 1 until recentHistory.size) {
            val prev = recentHistory[i - 1]
            val curr = recentHistory[i]
            val dist = haversineDistance(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            val timeHours = (curr.timestamp - prev.timestamp) / 3600000.0

            totalDistance += dist

            if (timeHours > 0) {
                val speed = dist / timeHours
                if (speed > maxSpeed) maxSpeed = speed
            }

            // Significant movement = > 50 meters
            if (dist > 0.05) {
                lastSignificantMove = curr.timestamp
            }
        }

        // Calculate hours stationary
        val hoursStationary = if (lastSignificantMove != null) {
            (now - lastSignificantMove) / 3600000.0
        } else if (recentHistory.isNotEmpty()) {
            (now - recentHistory.first().timestamp) / 3600000.0
        } else {
            0.0
        }

        val isStationary = totalDistance < STATIONARY_THRESHOLD_KM && hoursStationary >= 0.5

        return UserMovementData(
            locationHistory = history,
            lastSignificantMovement = lastSignificantMove,
            hoursStationary = hoursStationary,
            totalDistanceLast6Hours = totalDistance,
            maxSpeed6Hours = maxSpeed,
            isStationary = isStationary
        )
    }

    // ═══════════════════════════════════════════════════
    //  AI BEHAVIOR PATTERN ANALYSIS
    // ═══════════════════════════════════════════════════

    /**
     * Perform AI-based behavior pattern analysis.
     *
     * Multi-factor weighted scoring:
     * 1. Stationarity duration (0.25)
     * 2. Disaster zone proximity (0.25)
     * 3. Movement pattern quality (0.20)
     * 4. Time context (0.10)
     * 5. Impact severity (0.15)
     * 6. Historical pattern (0.05)
     */
    fun analyzeBehavior(
        movementData: UserMovementData,
        impactAreas: List<DisasterImpactArea>,
        userLat: Double,
        userLon: Double
    ): BehaviorAnalysis {
        val factors = mutableListOf<BehaviorFactor>()
        val now = System.currentTimeMillis()

        // ── Factor 1: Stationarity Duration (weight=0.25) ──
        val stationarityScore = when {
            movementData.hoursStationary >= 6.0 -> 1.0
            movementData.hoursStationary >= 4.0 -> 0.85
            movementData.hoursStationary >= 2.0 -> 0.7
            movementData.hoursStationary >= 1.0 -> 0.4
            movementData.hoursStationary >= 0.5 -> 0.2
            else -> 0.0
        }
        factors.add(BehaviorFactor(
            name = "Stationarity",
            weight = 0.25,
            score = stationarityScore,
            description = "${"%.1f".format(movementData.hoursStationary)} hours without movement"
        ))

        // ── Factor 2: Disaster Zone Proximity (weight=0.25) ──
        val criticalAreas = impactAreas.filter {
            it.severity.level >= ImpactSeverity.SEVERE.level
        }
        val nearestCritical = criticalAreas.minByOrNull { it.distanceFromUserKm }
        val proximityScore = if (nearestCritical != null) {
            when {
                nearestCritical.userInZone -> 1.0
                nearestCritical.distanceFromUserKm <= 5.0 -> 0.8
                nearestCritical.distanceFromUserKm <= 20.0 -> 0.5
                nearestCritical.distanceFromUserKm <= 50.0 -> 0.2
                else -> 0.0
            }
        } else 0.0
        factors.add(BehaviorFactor(
            name = "Disaster Proximity",
            weight = 0.25,
            score = proximityScore,
            description = nearestCritical?.let {
                "${"%.1f".format(it.distanceFromUserKm)} km from ${it.title}"
            } ?: "No critical areas nearby"
        ))

        // ── Factor 3: Movement Pattern (weight=0.20) ──
        // Low movement + low speed in disaster zone = trapped
        val movementScore = when {
            movementData.totalDistanceLast6Hours < 0.05 && movementData.maxSpeed6Hours < 0.5 -> 1.0
            movementData.totalDistanceLast6Hours < 0.1 && movementData.maxSpeed6Hours < 1.0 -> 0.8
            movementData.totalDistanceLast6Hours < 0.5 -> 0.4
            movementData.totalDistanceLast6Hours < 1.0 -> 0.2
            else -> 0.0
        }
        factors.add(BehaviorFactor(
            name = "Movement Pattern",
            weight = 0.20,
            score = movementScore,
            description = "${"%.2f".format(movementData.totalDistanceLast6Hours)} km in 6h, max ${"%.1f".format(movementData.maxSpeed6Hours)} km/h"
        ))

        // ── Factor 4: Time Context (weight=0.10) ──
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeScore = when (hour) {
            in 0..5 -> 0.9   // Late night — higher risk if stationary
            in 22..23 -> 0.7 // Night
            in 6..8 -> 0.3   // Morning
            else -> 0.2      // Daytime — lower risk
        }
        factors.add(BehaviorFactor(
            name = "Time Context",
            weight = 0.10,
            score = timeScore,
            description = "Current hour: $hour:00"
        ))

        // ── Factor 5: Impact Severity (weight=0.15) ──
        val maxSeverity = impactAreas
            .filter { it.userInZone || it.distanceFromUserKm <= 10.0 }
            .maxByOrNull { it.severity.level }
        val severityScore = when (maxSeverity?.severity) {
            ImpactSeverity.CATASTROPHIC -> 1.0
            ImpactSeverity.CRITICAL -> 0.85
            ImpactSeverity.SEVERE -> 0.65
            ImpactSeverity.MODERATE -> 0.3
            ImpactSeverity.MINOR -> 0.1
            null -> 0.0
        }
        factors.add(BehaviorFactor(
            name = "Impact Severity",
            weight = 0.15,
            score = severityScore,
            description = maxSeverity?.let { "${it.severity.label} — ${it.title}" } ?: "No nearby impact"
        ))

        // ── Factor 6: Historical Pattern (weight=0.05) ──
        // Check if user was previously moving normally then stopped suddenly
        val historyScore = if (movementData.locationHistory.size >= 10) {
            val recentHalf = movementData.locationHistory.takeLast(movementData.locationHistory.size / 2)
            val olderHalf = movementData.locationHistory.take(movementData.locationHistory.size / 2)

            val recentMovement = calculateTotalDistance(recentHalf)
            val olderMovement = calculateTotalDistance(olderHalf)

            // Sudden stop pattern: was moving, then stopped
            if (olderMovement > 0.5 && recentMovement < 0.1) 0.9
            else if (olderMovement > 0.2 && recentMovement < 0.05) 0.7
            else 0.2
        } else 0.3 // Insufficient data
        factors.add(BehaviorFactor(
            name = "Historical Pattern",
            weight = 0.05,
            score = historyScore,
            description = "Movement pattern change analysis"
        ))

        // ── Calculate weighted distress score ──
        val distressScore = factors.sumOf { it.weight * it.score }

        // ── Calculate confidence based on data quality ──
        val dataPoints = movementData.locationHistory.size
        val dataSpanHours = if (movementData.locationHistory.size >= 2) {
            (movementData.locationHistory.last().timestamp - movementData.locationHistory.first().timestamp) / 3600000.0
        } else 0.0

        val confidence = when {
            dataPoints >= 60 && dataSpanHours >= 2.0 -> 0.9
            dataPoints >= 30 && dataSpanHours >= 1.0 -> 0.75
            dataPoints >= 10 && dataSpanHours >= 0.5 -> 0.6
            dataPoints >= 5 -> 0.4
            else -> 0.2
        }

        val recommendation = when {
            distressScore >= 0.8 && confidence >= 0.7 -> BehaviorRecommendation.DISTRESSED
            distressScore >= 0.6 && confidence >= 0.5 -> BehaviorRecommendation.AT_RISK
            distressScore >= 0.4 -> BehaviorRecommendation.MONITOR
            else -> BehaviorRecommendation.SAFE
        }

        return BehaviorAnalysis(
            distressScore = distressScore.coerceIn(0.0, 1.0),
            confidenceLevel = confidence,
            factors = factors,
            recommendation = recommendation,
            analysisTimestamp = now
        )
    }

    // ═══════════════════════════════════════════════════
    //  SOS ELIGIBILITY CHECK
    // ═══════════════════════════════════════════════════

    /**
     * Determine if SOS button should be shown.
     *
     * Requirements (ALL must be met):
     * 1. User is inside a critical impact zone (severity >= SEVERE)
     * 2. User stationary for >= 2 hours
     * 3. AI distress score >= 0.7 with confidence >= 0.6
     * 4. Not in cooldown from recent deactivation
     */
    fun evaluateSOSEligibility(
        impactAreas: List<DisasterImpactArea>,
        userLat: Double,
        userLon: Double
    ): SOSState {
        val now = System.currentTimeMillis()

        // Check cooldown
        val lastDeactivated = prefs.getLong(KEY_LAST_SOS_DEACTIVATED, 0L)
        if (now - lastDeactivated < SOS_COOLDOWN_MS) {
            return SOSState(
                isEligible = false,
                eligibilityReason = SOSEligibilityReason.RECENTLY_DEACTIVATED
            )
        }

        // Check if already activated
        if (prefs.getBoolean(KEY_SOS_ACTIVATED, false)) {
            val activatedAt = prefs.getLong(KEY_SOS_ACTIVATED_AT, 0L)
            if (now - activatedAt > SOS_EXPIRY_MS) {
                deactivateSOS()
            } else {
                val movement = analyzeMovement()
                val behavior = analyzeBehavior(movement, impactAreas, userLat, userLon)
                return SOSState(
                    isEligible = true,
                    eligibilityReason = SOSEligibilityReason.ELIGIBLE,
                    isActivated = true,
                    activatedAt = activatedAt,
                    userMovementData = movement,
                    behaviorAnalysis = behavior,
                    expiresAt = activatedAt + SOS_EXPIRY_MS
                )
            }
        }

        // Check if user is in critical zone
        val userInCriticalZone = impactAreas.any { area ->
            area.userInZone && area.severity.level >= ImpactSeverity.SEVERE.level
        }

        if (!userInCriticalZone) {
            return SOSState(
                isEligible = false,
                eligibilityReason = SOSEligibilityReason.NOT_IN_DANGER_ZONE
            )
        }

        // Analyze movement
        val movement = analyzeMovement()

        if (movement.locationHistory.size < 5) {
            return SOSState(
                isEligible = false,
                eligibilityReason = SOSEligibilityReason.INSUFFICIENT_DATA,
                userMovementData = movement
            )
        }

        if (!movement.isStationary || movement.hoursStationary < MIN_STATIONARY_HOURS) {
            return SOSState(
                isEligible = false,
                eligibilityReason = SOSEligibilityReason.USER_IS_MOVING,
                userMovementData = movement
            )
        }

        // AI behavior analysis
        val behavior = analyzeBehavior(movement, impactAreas, userLat, userLon)

        if (behavior.distressScore < DISTRESS_THRESHOLD || behavior.confidenceLevel < CONFIDENCE_THRESHOLD) {
            return SOSState(
                isEligible = false,
                eligibilityReason = SOSEligibilityReason.AI_LOW_CONFIDENCE,
                userMovementData = movement,
                behaviorAnalysis = behavior
            )
        }

        // All conditions met — SOS eligible
        return SOSState(
            isEligible = true,
            eligibilityReason = SOSEligibilityReason.ELIGIBLE,
            isActivated = false,
            userMovementData = movement,
            behaviorAnalysis = behavior
        )
    }

    // ═══════════════════════════════════════════════════
    //  SOS ACTIVATION / DEACTIVATION
    // ═══════════════════════════════════════════════════

    /**
     * Activate the SOS signal.
     * Returns the SOS message with coordinates.
     */
    fun activateSOS(latitude: Double, longitude: Double): String {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_SOS_ACTIVATED, true)
            .putLong(KEY_SOS_ACTIVATED_AT, now)
            .apply()

        return buildSOSMessage(latitude, longitude)
    }

    /**
     * Deactivate the SOS signal.
     */
    fun deactivateSOS() {
        prefs.edit()
            .putBoolean(KEY_SOS_ACTIVATED, false)
            .putLong(KEY_LAST_SOS_DEACTIVATED, System.currentTimeMillis())
            .apply()
    }

    /**
     * Build SOS emergency message with coordinates.
     */
    fun buildSOSMessage(latitude: Double, longitude: Double): String {
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
        return """
🆘 DARURAT / EMERGENCY 🆘

Saya terjebak di zona bencana dan membutuhkan pertolongan segera!
I am trapped in a disaster zone and need immediate help!

📍 Lokasi / Location:
$latitude, $longitude
$mapsLink

⏰ Waktu / Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}

Harap hubungi layanan darurat / Please contact emergency services:
🇮🇩 Indonesia: 112 (Darurat) | 115 (BASARNAS) | 117 (BNPB)
🌐 International: 112 / 911

Dikirim otomatis oleh aplikasi Cuacaku / Sent automatically by Cuacaku app
        """.trimIndent()
    }

    // ═══════════════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════════════

    private fun getLocationHistory(): List<LocationRecord> {
        val json = prefs.getString(KEY_LOCATION_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LocationRecord>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLocationHistory(history: List<LocationRecord>) {
        prefs.edit()
            .putString(KEY_LOCATION_HISTORY, gson.toJson(history))
            .apply()
    }

    // ═══════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════

    private fun calculateTotalDistance(records: List<LocationRecord>): Double {
        if (records.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until records.size) {
            total += haversineDistance(
                records[i - 1].latitude, records[i - 1].longitude,
                records[i].latitude, records[i].longitude
            )
        }
        return total
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
