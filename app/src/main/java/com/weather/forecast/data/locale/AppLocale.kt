package com.weather.forecast.data.locale

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App Locale — auto-detected from user's GPS location.
 *
 * - ID: Bahasa Indonesia (user di wilayah Indonesia)
 * - EN: English (user di luar Indonesia)
 *
 * Deteksi otomatis via reverse geocode → country code.
 * Hasil di-cache di SharedPreferences agar langsung tersedia saat app start.
 */
enum class AppLocale { ID, EN }

/**
 * CompositionLocal for providing AppStrings throughout the Compose tree.
 * Default: English (overridden in WeatherNavigation based on detected locale).
 */
val LocalStrings = staticCompositionLocalOf { AppStrings.EN }

/**
 * Singleton manager for app locale detection and caching.
 *
 * ── How it works ──
 * 1. On app start, reads cached country code from SharedPreferences
 * 2. When WeatherRepository does reverse geocode, it calls updateCountry()
 * 3. All UI reacts via localeFlow / LocalStrings CompositionLocal
 * 4. Non-Compose code (notifications, repos) accesses via AppLocaleManager.strings
 *
 * ── Location-based, not device-based ──
 * This is intentionally location-based, not device locale-based.
 * A Japanese user visiting Indonesia will see Indonesian UI.
 * An Indonesian user visiting Japan will see English UI.
 */
object AppLocaleManager {

    private const val PREFS_NAME = "app_locale"
    private const val KEY_COUNTRY_CODE = "country_code"

    private lateinit var prefs: SharedPreferences
    private val _locale = MutableStateFlow(AppLocale.EN)

    /** Reactive locale flow — for Compose to collect */
    val localeFlow: StateFlow<AppLocale> = _locale.asStateFlow()

    /** Current locale value — for non-Compose code */
    val locale: AppLocale get() = _locale.value

    /** Current strings instance — for non-Compose code (notifications, repos) */
    val strings: AppStrings get() = AppStrings.get(_locale.value)

    /**
     * Initialize from Application.onCreate().
     * Reads cached country code to set locale immediately.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_COUNTRY_CODE, null)
        _locale.value = resolveLocale(cached)
    }

    /**
     * Update country code from reverse geocode result.
     * Called by WeatherRepository when location name is resolved.
     *
     * @param countryCode ISO 3166-1 alpha-2 code (e.g., "id", "us", "jp")
     */
    fun updateCountry(countryCode: String?) {
        val newLocale = resolveLocale(countryCode)
        if (_locale.value != newLocale) {
            _locale.value = newLocale
        }
        prefs.edit().putString(KEY_COUNTRY_CODE, countryCode?.lowercase()).apply()
    }

    private fun resolveLocale(countryCode: String?): AppLocale {
        return if (countryCode?.lowercase() == "id") AppLocale.ID else AppLocale.EN
    }
}
