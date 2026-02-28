package com.weather.forecast.notification

/**
 * Notification Channel IDs
 * 
 * Digunakan untuk mengelompokkan notifikasi berdasarkan tipe:
 * - WEATHER_ALERTS: Peringatan cuaca ekstrem (high priority)
 * - DAILY_FORECAST: Prakiraan cuaca harian (default priority)
 */
object NotificationChannels {
    const val WEATHER_ALERTS = "weather_alerts"
    const val DAILY_FORECAST = "daily_forecast"
    /** Channel darurat — IMPORTANCE_MAX, getaran agresif, bypass DND */
    const val EXTREME_WEATHER_EMERGENCY = "extreme_weather_emergency"
    /** Channel peringatan bencana — AI-based disaster predictions */
    const val DISASTER_ALERTS = "disaster_alerts"
    /** Channel darurat bencana — EXTREME risk, bypass DND */
    const val DISASTER_EMERGENCY = "disaster_emergency"
    /** Channel peringatan gempa bumi — seismic alerts */
    const val EARTHQUAKE_ALERTS = "earthquake_alerts"
    /** Channel darurat tsunami — bypass DND, SOS vibration */
    const val TSUNAMI_EMERGENCY = "tsunami_emergency"
    /** Channel peringatan gunung berapi */
    const val VOLCANO_ALERTS = "volcano_alerts"
    /** Channel peringatan gelombang tinggi — NO vibration (maritime safety) */
    const val HIGH_WAVE_ALERTS = "high_wave_alerts"
    /** Channel SOS darurat — IMPORTANCE_MAX, override everything */
    const val SOS_EMERGENCY = "sos_emergency"
}

/**
 * Notification IDs
 * 
 * Unique ID untuk setiap jenis notifikasi agar bisa di-update atau cancel
 */
object NotificationIds {
    const val DAILY_FORECAST = 1001
    const val RAIN_ALERT = 1002
    const val SEVERE_WEATHER = 1003
    const val TEMPERATURE_ALERT = 1004
    /** Peringatan risiko cuaca TINGGI (dari WeatherPotential) */
    const val HIGH_RISK_ALERT = 1005
    /** Peringatan darurat cuaca EKSTREM — getaran agresif */
    const val EXTREME_RISK_ALERT = 1006
    /** Peringatan bencana (AI-based) — HIGH risk */
    const val DISASTER_HIGH_ALERT = 1007
    /** Peringatan darurat bencana — EXTREME risk, getaran SOS */
    const val DISASTER_EXTREME_ALERT = 1008
    /** Peringatan gempa bumi */
    const val EARTHQUAKE_ALERT = 1009
    /** Peringatan darurat tsunami — SOS vibration */
    const val TSUNAMI_EMERGENCY_ALERT = 1010
    /** Peringatan gunung berapi */
    const val VOLCANO_ALERT = 1011
    /** Peringatan gelombang tinggi — no vibration */
    const val HIGH_WAVE_ALERT = 1012
    /** SOS emergency signal */
    const val SOS_EMERGENCY_ALERT = 1013
    const val WEATHER_UPDATE_SERVICE = 2001
}
