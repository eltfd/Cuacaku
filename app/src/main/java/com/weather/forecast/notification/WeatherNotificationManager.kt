package com.weather.forecast.notification

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.weather.forecast.MainActivity
import com.weather.forecast.R
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.model.CurrentWeatherData
import com.weather.forecast.data.model.DailyWeatherData
import com.weather.forecast.data.model.RiskLevel
import com.weather.forecast.data.model.WeatherAlert
import com.weather.forecast.data.model.WeatherCondition
import com.weather.forecast.data.model.WeatherPotential
import com.weather.forecast.data.model.DisasterPrediction
import com.weather.forecast.data.model.RiskLevel as DisasterRiskLevel
import com.weather.forecast.data.model.EarthquakeEvent
import com.weather.forecast.data.model.TsunamiRiskAssessment
import com.weather.forecast.data.model.TsunamiRiskLevel
import com.weather.forecast.data.model.VolcanicEvent
import com.weather.forecast.data.model.VolcanoAlertLevel
import com.weather.forecast.data.model.HighWaveWarning
import com.weather.forecast.data.model.WaveWarningLevel

/**
 * Weather Notification Manager
 * 
 * Mengelola semua notifikasi cuaca:
 * - Daily forecast notification
 * - Severe weather alerts
 * - Rain alerts
 * - Temperature alerts
 * - **HIGH risk weather alerts (vibration)**
 * - **EXTREME risk emergency alerts (aggressive SOS vibration + bypass DND)**
 */
class WeatherNotificationManager(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    // ── Vibration patterns ────────────────────────────────────────

    /**
     * Pola getar untuk risiko TINGGI — 3× getar tegas
     * [delay, vibrate, pause, vibrate, pause, vibrate]
     */
    private val highRiskVibrationPattern = longArrayOf(
        0, 400, 300, 400, 300, 400
    )

    /**
     * Pola getar SOS untuk risiko EKSTREM — panjang & agresif
     * Meniru sinyal darurat; ponsel bergetar berkali-kali agar user
     * segera sadar dan mencari perlindungan.
     *
     * S (···)  O (–––)  S (···)
     */
    private val extremeVibrationPattern = longArrayOf(
        0, 500, 200, 500, 200, 500,       // S  ···
        400, 1000, 200, 1000, 200, 1000,   // O  –––
        400, 500, 200, 500, 200, 500       // S  ···
    )

    // ── Permission check ─────────────────────────────────────────

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            mgr.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Trigger vibration with a given pattern.
     * Repeat = -1 → play once.
     * Amplitude = max for EXTREME to make it really felt.
     */
    private fun vibrate(pattern: LongArray, isExtreme: Boolean = false) {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = if (isExtreme) 255 else VibrationEffect.DEFAULT_AMPLITUDE
            val amplitudes = IntArray(pattern.size) { amplitude }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /**
     * Shared builder for extreme/emergency notifications with full-screen intent,
     * aggressive vibration, and persistent banner.
     */
    private fun sendExtremeNotification(
        channelId: String,
        title: String,
        content: String,
        extraKey: String,
        requestCodeBase: Int,
        notifId: Int,
        autoCancel: Boolean = true
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(extraKey, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, requestCodeBase, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = PendingIntent.getActivity(
            context, requestCodeBase + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_weather_splash)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setVibrate(extremeVibrationPattern)
            .setLights(android.graphics.Color.RED, 500, 200)
            .setAutoCancel(autoCancel)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notificationManager.notify(notifId, notification)
        vibrate(extremeVibrationPattern, isExtreme = true)
    }

    // ── Standard notifications ───────────────────────────────────

    fun sendDailyForecastNotification(
        locationName: String,
        current: CurrentWeatherData,
        today: DailyWeatherData?
    ) {
        if (!hasNotificationPermission()) return

        val s = AppLocaleManager.strings
        val title = s.notifDailyTitle
        val content = buildString {
            append("$locationName: ${current.temperatureFormatted}")
            append(" • ${s.localized(current.weatherCondition.description, current.weatherCondition.descriptionId)}")
            today?.let {
                append("\n${s.notifMaxMin(it.temperatureMaxFormatted, it.temperatureMinFormatted)}")
                if (it.precipitationProbabilityMax > 30) {
                    append(" • ${s.notifRain(it.precipitationProbabilityFormatted)}")
                }
            }
        }

        val notification = createNotification(
            channelId = NotificationChannels.DAILY_FORECAST,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        notificationManager.notify(NotificationIds.DAILY_FORECAST, notification)
    }

    fun sendSevereWeatherAlert(
        locationName: String,
        weatherCode: Int,
        description: String
    ) {
        if (!hasNotificationPermission()) return
        if (!WeatherCondition.isSevereWeather(weatherCode)) return

        val s = AppLocaleManager.strings
        val title = s.notifSevereWeather
        val content = s.notifSevereWeatherDesc(locationName, description)

        val notification = createNotification(
            channelId = NotificationChannels.WEATHER_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_HIGH
        )

        notificationManager.notify(NotificationIds.SEVERE_WEATHER, notification)
    }

    fun sendRainAlert(
        locationName: String,
        precipitationProbability: Int,
        expectedTime: String
    ) {
        if (!hasNotificationPermission()) return
        if (precipitationProbability < 50) return

        val s = AppLocaleManager.strings
        val title = s.notifRainWarning
        val content = "$locationName: ${s.notifRainDesc(precipitationProbability.toString() + "%", expectedTime)}"

        val notification = createNotification(
            channelId = NotificationChannels.WEATHER_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        notificationManager.notify(NotificationIds.RAIN_ALERT, notification)
    }

    fun sendTemperatureAlert(
        locationName: String,
        temperature: Double,
        isHigh: Boolean
    ) {
        if (!hasNotificationPermission()) return

        val s = AppLocaleManager.strings
        val title = if (isHigh) s.notifHighTemp else s.notifLowTemp
        val content = "$locationName: ${s.notifTempDesc(temperature.toInt().toString())} " +
            if (isHigh) s.notifSunProtection else s.notifWarmClothes

        val notification = createNotification(
            channelId = NotificationChannels.WEATHER_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        notificationManager.notify(NotificationIds.TEMPERATURE_ALERT, notification)
    }

    // ── Risk-based alerts (HIGH & EXTREME) ───────────────────────

    /**
     * Kirim notifikasi peringatan berdasarkan [WeatherPotential].
     *
     * - **HIGH** → channel WEATHER_ALERTS, priority HIGH, vibration 3×
     * - **EXTREME** → channel EXTREME_WEATHER_EMERGENCY, priority MAX,
     *   aggressive SOS vibration, fullscreen intent, bypass DND
     *
     * Dipanggil oleh [WeatherUpdateWorker] setelah menghitung potensi cuaca.
     */
    fun sendWeatherRiskAlert(
        locationName: String,
        potential: WeatherPotential
    ) {
        if (!hasNotificationPermission()) return

        // Tentukan level tertinggi dari semua risiko
        val maxRisk = listOf(
            potential.stormRisk,
            potential.heavyRainRisk,
            potential.hailRisk,
            potential.strongWindRisk,
            potential.tornadoRisk
        ).maxByOrNull { it.ordinal } ?: return

        // Hanya kirim untuk HIGH & EXTREME
        if (maxRisk != RiskLevel.HIGH && maxRisk != RiskLevel.EXTREME) return

        // Kumpulkan alert yang relevan (HIGH/EXTREME saja)
        val relevantAlerts = potential.alerts.filter {
            it.risk == RiskLevel.HIGH || it.risk == RiskLevel.EXTREME
        }

        if (relevantAlerts.isEmpty()) return

        when (maxRisk) {
            RiskLevel.EXTREME -> sendExtremeEmergencyNotification(locationName, relevantAlerts, potential)
            RiskLevel.HIGH -> sendHighRiskNotification(locationName, relevantAlerts, potential)
            else -> { /* nothing */ }
        }
    }

    /**
     * Notifikasi risiko TINGGI — getaran tegas, priority HIGH
     */
    private fun sendHighRiskNotification(
        locationName: String,
        alerts: List<WeatherAlert>,
        potential: WeatherPotential
    ) {
        val s = AppLocaleManager.strings
        val alertSummary = alerts.joinToString(", ") { s.localized(it.description, it.descriptionId) }

        val title = s.notifHighRiskWarning
        val content = buildString {
            append("$locationName — ")
            append(alertSummary)
            if (potential.maxWindGusts > 0) {
                append("\n${s.notifWindWarning(potential.maxWindGusts.toInt().toString())}")
            }
            append("\n${s.notifWeatherMonitor}")
        }

        val notification = createNotification(
            channelId = NotificationChannels.WEATHER_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_ALARM,
            vibrationPattern = highRiskVibrationPattern
        )

        notificationManager.notify(NotificationIds.HIGH_RISK_ALERT, notification)

        // Trigger vibration manually as extra measure
        vibrate(highRiskVibrationPattern, isExtreme = false)
    }

    /**
     * 🚨 Notifikasi darurat EKSTREM
     *
     * - Channel khusus EXTREME_WEATHER_EMERGENCY (IMPORTANCE_MAX, bypass DND)
     * - Getaran SOS agresif agar user segera sadar
     * - Full-screen intent → langsung muncul di lock screen
     * - Informasi singkat & jelas: SEGERA cari perlindungan
     */
    private fun sendExtremeEmergencyNotification(
        locationName: String,
        alerts: List<WeatherAlert>,
        potential: WeatherPotential
    ) {
        val extremeAlerts = alerts.filter { it.risk == RiskLevel.EXTREME }
        val highAlerts = alerts.filter { it.risk == RiskLevel.HIGH }

        val s = AppLocaleManager.strings
        val alertSummary = (extremeAlerts + highAlerts).joinToString(", ") {
            "${s.localized(it.type.label, it.type.labelId)} (${s.localized(it.risk.label, it.risk.labelId)})"
        }

        val title = s.notifEmergencyWeather
        val content = buildString {
            append("$locationName — ${s.notifEmergencyTitle}\n")
            append(alertSummary)
            append("\n")
            if (potential.maxWindGusts > 0) {
                append("${s.notifEmergencyWind(potential.maxWindGusts.toInt().toString())} ")
            }
            if (potential.maxCape > 0) {
                append("CAPE: ${potential.maxCape.toInt()} J/kg. ")
            }
            append("\n${s.notifSeekShelter}")
        }

        sendExtremeNotification(
            channelId = NotificationChannels.EXTREME_WEATHER_EMERGENCY,
            title = title, content = content,
            extraKey = "open_weather_alert",
            requestCodeBase = 1,
            notifId = NotificationIds.EXTREME_RISK_ALERT
        )
    }

    // ── Disaster risk alerts (AI-based predictions) ────────────

    /**
     * Kirim notifikasi peringatan bencana berdasarkan [DisasterPrediction].
     *
     * - **HIGH** → channel DISASTER_ALERTS, priority HIGH, vibration tegas
     * - **EXTREME** → channel DISASTER_EMERGENCY, priority MAX,
     *   SOS vibration agresif, fullscreen intent, bypass DND
     *
     * Dipanggil oleh [WeatherUpdateWorker] setelah AI menganalisis potensi bencana.
     */
    fun sendDisasterRiskAlert(
        locationName: String,
        predictions: List<DisasterPrediction>
    ) {
        if (!hasNotificationPermission()) return

        val highRisks = predictions.filter { it.riskLevel == RiskLevel.HIGH }
        val extremeRisks = predictions.filter { it.riskLevel == RiskLevel.EXTREME }

        if (extremeRisks.isNotEmpty()) {
            sendExtremeDisasterNotification(locationName, extremeRisks, highRisks)
        } else if (highRisks.isNotEmpty()) {
            sendHighDisasterNotification(locationName, highRisks)
        }
    }

    /**
     * Notifikasi bencana risiko TINGGI — getaran tegas, priority HIGH
     */
    private fun sendHighDisasterNotification(
        locationName: String,
        highRisks: List<DisasterPrediction>
    ) {
        val s = AppLocaleManager.strings
        val disasterNames = highRisks.joinToString(", ") { s.localized(it.type.label, it.type.labelId) }

        val title = s.notifDisasterHighTitle
        val content = buildString {
            append("⚠️ $locationName\n")
            append(s.notifDisasterHighBody(disasterNames))
            append("\n")
            highRisks.forEach { pred ->
                val confidence = "%.0f".format(pred.confidence * 100)
                append("\n${pred.type.icon} ${s.localized(pred.type.label, pred.type.labelId)}: ")
                append("${s.localized(pred.riskLevel.label, pred.riskLevel.labelId)} ")
                append("($confidence%)")
            }
            append("\n\n${s.notifDisasterCheck}")
        }

        val notification = createNotification(
            channelId = NotificationChannels.DISASTER_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_ALARM,
            vibrationPattern = highRiskVibrationPattern
        )

        notificationManager.notify(NotificationIds.DISASTER_HIGH_ALERT, notification)
        vibrate(highRiskVibrationPattern, isExtreme = false)
    }

    /**
     * 🚨 Notifikasi darurat bencana EKSTREM
     *
     * - Channel DISASTER_EMERGENCY (IMPORTANCE_MAX, bypass DND)
     * - Getaran SOS agresif
     * - Full-screen intent
     * - Ongoing — tidak bisa di-swipe
     */
    private fun sendExtremeDisasterNotification(
        locationName: String,
        extremeRisks: List<DisasterPrediction>,
        highRisks: List<DisasterPrediction>
    ) {
        val s = AppLocaleManager.strings

        val title = s.notifDisasterExtremeTitle
        val content = buildString {
            append("🚨 $locationName — ${s.notifDisasterExtremeHeader}\n\n")
            extremeRisks.forEach { pred ->
                append("🔴 ${pred.type.icon} ${s.localized(pred.type.label, pred.type.labelId)}: ")
                append("${s.localized(pred.riskLevel.label, pred.riskLevel.labelId)} (${("%.0f".format(pred.confidence * 100))}%)\n")
                append("   ${pred.description.take(120)}\n\n")
            }
            if (highRisks.isNotEmpty()) {
                highRisks.forEach { pred ->
                    append("🟠 ${pred.type.icon} ${s.localized(pred.type.label, pred.type.labelId)}: ")
                    append("${s.localized(pred.riskLevel.label, pred.riskLevel.labelId)} (${("%.0f".format(pred.confidence * 100))}%)\n")
                }
                append("\n")
            }
            append("${s.notifDisasterExtremeAction}\n")
            append(s.notifSeekShelter)
        }

        sendExtremeNotification(
            channelId = NotificationChannels.DISASTER_EMERGENCY,
            title = title, content = content,
            extraKey = "open_disaster_alert",
            requestCodeBase = 3,
            notifId = NotificationIds.DISASTER_EXTREME_ALERT
        )
    }

    // ── Seismic & volcanic alerts ──────────────────────────────

    /**
     * Send earthquake alert notification.
     * Nearby significant earthquakes trigger HIGH priority alerts with vibration.
     */
    fun sendEarthquakeAlert(
        locationName: String,
        earthquake: EarthquakeEvent
    ) {
        if (!hasNotificationPermission()) return
        if (earthquake.magnitude < 4.0) return

        val s = AppLocaleManager.strings
        val title = s.notifEarthquakeTitle
        val content = buildString {
            append("📳 M${"%.1f".format(earthquake.magnitude)} — ${earthquake.place}\n")
            append("${s.earthquakeDepth}: ${"%.1f".format(earthquake.depthKm)} km\n")
            append("${s.distance}: ${"%.0f".format(earthquake.distanceFromUserKm)} km\n")
            append("${s.intensity}: ${s.localized(earthquake.intensity.label, earthquake.intensity.labelId)}\n")
            if (earthquake.tsunamiFlag) {
                append("\n⚠️ ${s.tsunamiPotentialFlag}")
            }
        }

        if (earthquake.magnitude >= 7.0 && earthquake.distanceFromUserKm <= 200) {
            // EXTREME — major earthquake very close
            sendExtremeSeismicNotification(title, content, "open_earthquake_alert")
        } else {
            val notification = createNotification(
                channelId = NotificationChannels.EARTHQUAKE_ALERTS,
                title = title,
                content = content,
                priority = NotificationCompat.PRIORITY_HIGH,
                category = NotificationCompat.CATEGORY_ALARM,
                vibrationPattern = highRiskVibrationPattern
            )
            notificationManager.notify(NotificationIds.EARTHQUAKE_ALERT, notification)
            vibrate(highRiskVibrationPattern, isExtreme = false)
        }
    }

    /**
     * Send tsunami warning notification.
     * Uses TSUNAMI_EMERGENCY channel with SOS vibration if WARNING level.
     */
    fun sendTsunamiAlert(
        locationName: String,
        risk: TsunamiRiskAssessment
    ) {
        if (!hasNotificationPermission()) return
        if (risk.riskLevel < TsunamiRiskLevel.ADVISORY) return

        val s = AppLocaleManager.strings
        val title = when (risk.riskLevel) {
            TsunamiRiskLevel.WARNING -> s.notifTsunamiWarningTitle
            TsunamiRiskLevel.WATCH -> s.notifTsunamiWatchTitle
            else -> s.notifTsunamiAdvisoryTitle
        }

        val content = buildString {
            append("🌊 $locationName\n")
            append("${risk.description}\n")
            risk.estimatedArrivalMinutes?.let {
                append("${s.estimatedArrival}: ~$it ${s.minutes}\n")
            }
            append("\n${risk.recommendation}")
        }

        if (risk.riskLevel == TsunamiRiskLevel.WARNING) {
            sendExtremeSeismicNotification(title, content, "open_tsunami_alert")
        } else {
            val notification = createNotification(
                channelId = NotificationChannels.TSUNAMI_EMERGENCY,
                title = title,
                content = content,
                priority = NotificationCompat.PRIORITY_HIGH,
                category = NotificationCompat.CATEGORY_ALARM,
                vibrationPattern = highRiskVibrationPattern
            )
            notificationManager.notify(NotificationIds.TSUNAMI_EMERGENCY_ALERT, notification)
            vibrate(highRiskVibrationPattern, isExtreme = false)
        }
    }

    /**
     * Send volcano alert notification.
     */
    fun sendVolcanoAlert(
        locationName: String,
        volcano: VolcanicEvent
    ) {
        if (!hasNotificationPermission()) return
        if (volcano.alertLevel < VolcanoAlertLevel.ADVISORY) return

        val s = AppLocaleManager.strings
        val title = s.notifVolcanoTitle
        val content = buildString {
            append("🌋 ${volcano.name}\n")
            append("${s.alertLevel}: ${s.localized(volcano.alertLevel.label, volcano.alertLevel.labelId)}\n")
            append("${s.distance}: ${"%.0f".format(volcano.distanceFromUserKm)} km\n")
            append("${volcano.description.take(150)}")
        }

        if (volcano.alertLevel == VolcanoAlertLevel.WARNING && volcano.distanceFromUserKm <= 100) {
            sendExtremeSeismicNotification(title, content, "open_volcano_alert")
        } else {
            val notification = createNotification(
                channelId = NotificationChannels.VOLCANO_ALERTS,
                title = title,
                content = content,
                priority = NotificationCompat.PRIORITY_HIGH,
                category = NotificationCompat.CATEGORY_ALARM,
                vibrationPattern = highRiskVibrationPattern
            )
            notificationManager.notify(NotificationIds.VOLCANO_ALERT, notification)
            vibrate(highRiskVibrationPattern, isExtreme = false)
        }
    }

    /**
     * Send high wave warning notification.
     * Uses DEFAULT priority, NO vibration (not a disaster category).
     */
    fun sendHighWaveAlert(
        locationName: String,
        warning: HighWaveWarning
    ) {
        if (!hasNotificationPermission()) return
        if (warning.warningLevel <= WaveWarningLevel.CALM) return

        val s = AppLocaleManager.strings
        val title = s.notifHighWaveTitle
        val content = buildString {
            append("🌊 $locationName\n")
            append("${s.waveHeight}: ${"%.1f".format(warning.maxWaveHeightForecast)} m\n")
            append("${s.localized(warning.warningLevel.label, warning.warningLevel.labelId)}\n")
            append(warning.recommendation)
        }

        // NOTE: No vibration — high waves are maritime safety info, not disaster
        val notification = createNotification(
            channelId = NotificationChannels.HIGH_WAVE_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
        notificationManager.notify(NotificationIds.HIGH_WAVE_ALERT, notification)
    }

    /**
     * Extreme seismic emergency — delegates to shared extreme notification builder.
     */
    private fun sendExtremeSeismicNotification(
        title: String,
        content: String,
        extraKey: String
    ) {
        sendExtremeNotification(
            channelId = NotificationChannels.TSUNAMI_EMERGENCY,
            title = title, content = content,
            extraKey = extraKey,
            requestCodeBase = 5,
            notifId = NotificationIds.TSUNAMI_EMERGENCY_ALERT
        )
    }

    // ── Notification builder ─────────────────────────────────────

    private fun createNotification(
        channelId: String,
        title: String,
        content: String,
        priority: Int,
        category: String? = null,
        vibrationPattern: LongArray? = null
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_weather_splash)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .apply {
                category?.let { setCategory(it) }
                vibrationPattern?.let { setVibrate(it) }
            }
            .build()
    }

    // ── Cancel helpers ───────────────────────────────────────────

    /**
     * Send SOS emergency notification — highest priority, bypass DND,
     * fullscreen intent, SOS vibration pattern.
     */
    fun sendSOSNotification(
        latitude: Double,
        longitude: Double,
        message: String
    ) {
        val strings = com.weather.forecast.data.locale.AppLocaleManager.strings

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("sos_active", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 9, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = PendingIntent.getActivity(
            context, 10, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.SOS_EMERGENCY
        )
            .setSmallIcon(R.drawable.ic_weather_splash)
            .setContentTitle(strings.notifSOSTitle)
            .setContentText(strings.notifSOSBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setVibrate(extremeVibrationPattern)
            .setLights(android.graphics.Color.RED, 500, 200)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NotificationIds.SOS_EMERGENCY_ALERT, notification)
        vibrate(extremeVibrationPattern, isExtreme = true)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
