package com.weather.forecast.data.locale

/**
 * Centralized bilingual string provider for Cuacaku.
 *
 * All user-facing strings are defined here with Indonesian (ID) and English (EN)
 * translations. The correct language is selected based on the user's detected
 * country from GPS reverse geocoding.
 *
 * Usage (Compose):
 *   val s = LocalStrings.current
 *   Text(s.retry)
 *
 * Usage (non-Compose — notifications, repos):
 *   val s = AppLocaleManager.strings
 *   s.retry
 *
 * @param locale The active locale for this instance
 */
class AppStrings(val locale: AppLocale) {

    /** Helper: select string by locale */
    private fun s(id: String, en: String) = when (locale) {
        AppLocale.ID -> id
        AppLocale.EN -> en
    }

    /**
     * Select the right value from a bilingual model field.
     * Many data models already have label (EN) + labelId (ID).
     */
    fun localized(en: String, id: String) = when (locale) {
        AppLocale.ID -> id
        AppLocale.EN -> en
    }

    // ═══════════════════════════════════════════════════
    //  COMMON
    // ═══════════════════════════════════════════════════

    val retry = s("Coba Lagi", "Retry")
    val refresh = s("Muat Ulang", "Refresh")
    val close = s("Tutup", "Close")
    val back = s("Kembali", "Back")
    val settings = s("Pengaturan", "Settings")
    val search = s("Cari", "Search")
    val clear = s("Hapus", "Clear")
    val today = s("Hari ini", "Today")
    val tomorrow = s("Besok", "Tomorrow")
    val ok = s("OK", "OK")
    val unknown = s("Tidak diketahui", "Unknown")
    val locationNotAvailable = s("Lokasi tidak tersedia", "Location not available")
    val locationPermissionRequired = s("Izin lokasi diperlukan", "Location permission required")
    val grantLocationPermission = s("Berikan Izin Lokasi", "Grant Location Permission")
    val errorOccurred = s("Terjadi kesalahan", "An error occurred")
    val open = s("Buka", "Open")
    val dataSource = s("Sumber Data", "Data Source")
    val hourly = s("Per Jam", "Hourly")
    val change = s("Perubahan", "Change")

    // ═══════════════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════════════

    val navWeather = s("Cuaca", "Weather")
    val navAir = s("Udara", "Air")
    val navWater = s("Air", "Water")
    val navDisaster = s("Bencana", "Disaster")
    val navMonitor = s("Pantau", "Monitor")

    // ═══════════════════════════════════════════════════
    //  WIND DIRECTIONS
    // ═══════════════════════════════════════════════════

    val windN = s("U", "N")
    val windNE = s("TL", "NE")
    val windE = s("T", "E")
    val windSE = s("TG", "SE")
    val windS = s("S", "S")
    val windSW = s("BD", "SW")
    val windW = s("B", "W")
    val windNW = s("BL", "NW")

    val windNFull = s("Utara", "North")
    val windNEFull = s("Timur Laut", "Northeast")
    val windEFull = s("Timur", "East")
    val windSEFull = s("Tenggara", "Southeast")
    val windSFull = s("Selatan", "South")
    val windSWFull = s("Barat Daya", "Southwest")
    val windWFull = s("Barat", "West")
    val windNWFull = s("Barat Laut", "Northwest")

    fun windDirectionShort(degrees: Int): String {
        val dirs = listOf(windN, windNE, windE, windSE, windS, windSW, windW, windNW)
        val index = ((degrees + 22.5) / 45).toInt() % 8
        return dirs[index]
    }

    fun windDirectionFull(degrees: Int): String {
        val dirs = listOf(windNFull, windNEFull, windEFull, windSEFull, windSFull, windSWFull, windWFull, windNWFull)
        val index = ((degrees + 22.5) / 45).toInt() % 8
        return dirs[index]
    }

    // ═══════════════════════════════════════════════════
    //  HOME SCREEN
    // ═══════════════════════════════════════════════════

    val homeTitle = "Cuacaku" // App name stays constant
    val searchPlaceholder = s("Cari lokasi...", "Search location...")
    val loadingWeather = s("Memuat data cuaca...", "Loading weather data...")
    fun feelsLike(temp: String) = s("Terasa seperti $temp", "Feels like $temp")
    val humidity = s("Kelembaban", "Humidity")
    val wind = s("Angin", "Wind")
    val pressure = s("Tekanan", "Pressure")
    val dewPoint = s("Titik Embun", "Dew Point")
    val gusts = s("Hembusan", "Gusts")
    val clouds = s("Awan", "Clouds")
    val windInfo = s("Informasi Angin", "Wind Information")
    val speed = s("Kecepatan", "Speed")
    val direction = s("Arah", "Direction")
    val extremeWeather = s("Potensi Cuaca Ekstrem", "Extreme Weather Potential")
    val noExtremeWeather = s("Tidak ada potensi cuaca ekstrem saat ini", "No extreme weather potential at this time")
    val thunderstorm = s("Badai Petir", "Thunderstorm")
    val heavyRain = s("Hujan Lebat", "Heavy Rain")
    val hail = s("Hujan Es", "Hail")
    val strongWind = s("Angin Kencang", "Strong Wind")
    val tornado = s("Puting Beliung", "Tornado")
    val activeWarnings = s("Peringatan Aktif", "Active Warnings")
    val precipitationDetail = s("Detail Presipitasi", "Precipitation Detail")
    val total = s("Total", "Total")
    val rain = s("Hujan", "Rain")
    val showers = s("Hujan Deras", "Showers")
    val snow = s("Salju", "Snow")
    val hourlyForecast = s("Prakiraan Per Jam", "Hourly Forecast")
    val sevenDayForecast = s("Prakiraan 7 Hari", "7-Day Forecast")
    val hourlyDetailForecast = s("Prakiraan Tiap Jam", "Hourly Detail Forecast")
    val hourlyDataUnavailable = s("Data per jam tidak tersedia", "Hourly data not available")
    val sunrise = s("Terbit", "Sunrise")
    val sunset = s("Terbenam", "Sunset")
    fun windLabel(dir: String, spd: Double) = s(
        "Angin $dir ${"%.0f".format(spd)} km/h",
        "Wind $dir ${"%.0f".format(spd)} km/h"
    )
    fun gustsLabel(spd: Double) = s("Hembusan ${"%.0f".format(spd)} km/h", "Gusts ${"%.0f".format(spd)} km/h")
    fun maxMinTemp(max: String, min: String) = s("Maks: $max / Min: $min", "Max: $max / Min: $min")
    val cannotGetLocation = s("Tidak dapat mendapatkan lokasi", "Unable to get location")
    val unknownError = s("Terjadi kesalahan", "An error occurred")
    val failedLoadWeather = s("Gagal memuat data cuaca", "Failed to load weather data")
    val closeHourly = s("Tutup prakiraan per jam", "Close hourly forecast")
    val openHourly = s("Buka prakiraan per jam", "Open hourly forecast")

    // ═══════════════════════════════════════════════════
    //  AIR QUALITY SCREEN
    // ═══════════════════════════════════════════════════

    val loadingAirQuality = s("Memuat data kualitas udara...", "Loading air quality data...")
    val failedLoadAirQuality = s("Gagal memuat data kualitas udara", "Failed to load air quality data")
    val airQuality = s("Kualitas Udara", "Air Quality")
    val pollutantDetail = s("Detail Polutan", "Pollutant Detail")
    val dust = s("Debu", "Dust")
    val hourlyAQIForecast = s("Prakiraan AQI Per Jam", "Hourly AQI Forecast")
    val dailyAirQualityForecast = s("Prakiraan Kualitas Udara Harian", "Daily Air Quality Forecast")
    val healthRecommendations = s("Rekomendasi Kesehatan", "Health Recommendations")
    fun uvIndex(value: String) = s("UV Index: $value", "UV Index: $value")
    val aqiMin = s("AQI Min", "AQI Min")
    val aqiMax = s("AQI Max", "AQI Max")
    val uvMax = s("UV Max", "UV Max")

    // AQI health recommendations by level
    val aqiGoodRec1 = s("Aman untuk aktivitas luar ruangan", "Safe for outdoor activities")
    val aqiGoodRec2 = s("Buka jendela untuk ventilasi segar", "Open windows for fresh ventilation")
    val aqiGoodRec3 = s("Nikmati udara segar di luar", "Enjoy the fresh air outdoors")
    val aqiModerateRec1 = s("Aktivitas luar ruangan masih aman", "Outdoor activities are still safe")
    val aqiModerateRec2 = s("Kelompok sensitif sebaiknya mengurangi aktivitas berat", "Sensitive groups should reduce heavy activities")
    val aqiModerateRec3 = s("Perhatikan kualitas udara jika memiliki gangguan pernapasan", "Monitor air quality if you have respiratory issues")
    val aqiUSensRec1 = s("Gunakan masker saat di luar", "Wear a mask outdoors")
    val aqiUSensRec2 = s("Kurangi aktivitas fisik berat di luar", "Reduce heavy physical activity outdoors")
    val aqiUSensRec3 = s("Anak-anak dan lansia sebaiknya di dalam ruangan", "Children and elderly should stay indoors")
    val aqiUSensRec4 = s("Gunakan air purifier jika tersedia", "Use an air purifier if available")
    val aqiUnhealthyRec1 = s("Wajib gunakan masker N95 di luar", "N95 mask required outdoors")
    val aqiUnhealthyRec2 = s("Batasi aktivitas di luar ruangan", "Limit outdoor activities")
    val aqiUnhealthyRec3 = s("Tutup jendela, gunakan air purifier", "Close windows, use air purifier")
    val aqiUnhealthyRec4 = s("Waspada gejala pernapasan", "Watch for respiratory symptoms")
    val aqiVeryUnhealthyRec1 = s("Hindari aktivitas di luar ruangan", "Avoid outdoor activities")
    val aqiVeryUnhealthyRec2 = s("Tetap di dalam ruangan", "Stay indoors")
    val aqiVeryUnhealthyRec3 = s("Gunakan masker N95 jika harus keluar", "Use N95 mask if you must go out")
    val aqiVeryUnhealthyRec4 = s("Segera ke dokter jika mengalami sesak napas", "See a doctor if you experience shortness of breath")
    val aqiHazardousRec1 = s("BAHAYA: Jangan keluar rumah", "DANGER: Do not leave the house")
    val aqiHazardousRec2 = s("Tutup semua jendela dan pintu", "Close all windows and doors")
    val aqiHazardousRec3 = s("Nyalakan air purifier pada level maksimum", "Set air purifier to maximum")
    val aqiHazardousRec4 = s("Hubungi layanan kesehatan jika mengalami gangguan pernapasan", "Contact health services if you have respiratory problems")

    // ═══════════════════════════════════════════════════
    //  WATER QUALITY SCREEN
    // ═══════════════════════════════════════════════════

    val loadingWaterQuality = s("Memuat data kualitas air...", "Loading water quality data...")
    val failedLoadWaterQuality = s("Gagal memuat data kualitas air", "Failed to load water quality data")
    val waterQuality = s("Kualitas Air", "Water Quality")
    val seaLakeRiver = s("Laut, Sungai & Danau", "Sea, River & Lake")
    val seaConditions = s("Kondisi Laut", "Sea Conditions")
    val riverWaterLevel = s("Tinggi Muka Air Sungai", "River Water Level")
    val currentConditions = s("Kondisi Saat Ini", "Current Conditions")
    val waves = s("Gelombang", "Waves")
    val period = s("Periode", "Period")
    val swell = s("Swell", "Swell")
    val swellDirection = s("Arah Swell", "Swell Direction")
    val swellPeriod = s("Periode Swell", "Swell Period")
    val waterLevelForecast = s("Perkiraan Tinggi Muka Air", "Water Level Forecast")
    val seaWaves = s("Gelombang Laut", "Sea Waves")
    val riverDischarge = s("Debit Sungai", "River Discharge")
    val forecastDisclaimer = s(
        "Perkiraan berdasarkan data 7 hari ke depan. Tren dihitung dari perbandingan paruh pertama vs paruh kedua prakiraan.",
        "Forecast based on 7-day data. Trend calculated by comparing first half vs second half of forecast."
    )
    val waveTrend7Days = s("Tren Gelombang 7 Hari", "7-Day Wave Trend")
    val riverWaterTrend = s("Tren Muka Air Sungai", "River Water Trend")
    val riverDischargeDisclaimer = s(
        "Debit sungai (m³/s) — semakin tinggi debit, semakin tinggi muka air. Warna bar menunjukkan tingkat risiko banjir.",
        "River discharge (m³/s) — higher discharge means higher water level. Bar color indicates flood risk level."
    )
    val seaForecast7Days = s("Prakiraan Laut 7 Hari", "7-Day Sea Forecast")
    val waveMax = s("Gelombang Max", "Max Wave")
    val swellMax = s("Swell Max", "Max Swell")
    val noDataForLocation = s("Data tidak tersedia untuk lokasi ini", "Data not available for this location")
    val dataOnlyNearCoast = s(
        "Data laut hanya tersedia untuk lokasi dekat pantai. Data sungai mungkin tidak tersedia di semua daerah.",
        "Sea data is only available near coastal areas. River data may not be available in all areas."
    )
    val waterDataSourceDetail = s(
        "Laut: Open-Meteo Marine API\nSungai: GloFAS (ECMWF/Copernicus)\nData diperbarui setiap jam",
        "Sea: Open-Meteo Marine API\nRiver: GloFAS (ECMWF/Copernicus)\nData updated hourly"
    )
    fun average(value: String) = s("Rata-rata: $value", "Average: $value")

    // ═══════════════════════════════════════════════════
    //  DISASTER FORECAST SCREEN
    // ═══════════════════════════════════════════════════

    val analyzingDisasters = s("Menganalisis potensi bencana...", "Analyzing disaster potential...")
    val aiProcessing = s("AI sedang memproses data cuaca, laut & sungai", "AI processing weather, sea & river data")
    val failedToLoadAnalysis = s("Gagal Memuat Analisis", "Failed to Load Analysis")
    val todayAnalysis = s("Analisis Hari Ini", "Today's Analysis")
    val disasterForecast = s("Prakiraan Bencana", "Disaster Forecast")
    val neuralNetworkAnalysis = s("Analisis Neural Network", "Neural Network Analysis")
    val model = s("Model", "Model")
    val data = s("Data", "Data")
    val learned = s("Learned", "Learned")
    val samples = s("Sampel", "Samples")
    val storage = s("Storage", "Storage")
    fun score(v: String) = s("Skor: $v%", "Score: $v%")
    fun nnScore(v: String) = s("NN: $v%", "NN: $v%")
    fun confidence(v: String) = s("Keyakinan: $v%", "Confidence: $v%")
    val analysisFactors = s("Faktor Analisis:", "Analysis Factors:")
    val riskMap7Days = s("Peta Risiko 7 Hari", "7-Day Risk Map")
    val low = s("Rendah", "Low")
    val moderate = s("Sedang", "Moderate")
    val high = s("Tinggi", "High")
    val extreme = s("Ekstrem", "Extreme")
    val aboutAnalysis = s("Tentang Analisis", "About Analysis")
    val aboutAnalysisDesc = s(
        "Analisis ini menggunakan Neural Network (MLP) yang dikombinasikan dengan rule-based analysis " +
                "untuk memprediksi potensi bencana berdasarkan data cuaca, laut, dan sungai real-time.\n\n" +
                "Model MLP v1.1 menggunakan 20 fitur input, 2 hidden layer (32 dan 16 neuron), dan " +
                "memprediksi 6 jenis bencana. Total parameter: 1.302. Model ini juga dilengkapi " +
                "Incremental Learning yang memungkinkan model belajar dari data baru secara terus-menerus.\n\n" +
                "⚠️ Analisis ini bersifat estimasi dan tidak menggantikan peringatan resmi dari BMKG atau badan meteorologi setempat.",
        "This analysis uses a Neural Network (MLP) combined with rule-based analysis to predict " +
                "disaster potential based on real-time weather, sea, and river data.\n\n" +
                "MLP v1.1 model uses 20 input features, 2 hidden layers (32 and 16 neurons), and predicts " +
                "6 disaster types. Total parameters: 1,302. The model also features Incremental Learning " +
                "that allows continuous learning from new data.\n\n" +
                "⚠️ This analysis is an estimate and does not replace official warnings from meteorological agencies."
    )
    val aiEngineLine = s(
        "AI Engine: MLP-v1.1 + Incremental Learning • Open-Meteo • GloFAS",
        "AI Engine: MLP-v1.1 + Incremental Learning • Open-Meteo • GloFAS"
    )
    fun statusLabel(label: String) = s("Status: $label", "Status: $label")

    // ═══════════════════════════════════════════════════
    //  DISASTER MONITOR SCREEN
    // ═══════════════════════════════════════════════════

    val monitoringDisasters = s("Memantau situasi bencana...", "Monitoring disaster situation...")
    val fetchingData = s("Mengambil data dari ReliefWeb & Open-Meteo", "Fetching data from ReliefWeb & Open-Meteo")
    val allClear = s("Situasi Aman", "All Clear")
    val noActiveDisasters = s(
        "Tidak ada bencana aktif yang terdeteksi\ndi area Anda",
        "No active disasters detected\nin your area"
    )
    val sourceReliefWeb = s(
        "Sumber: ReliefWeb (UN OCHA), NASA EONET v3 & Open-Meteo Flood API",
        "Source: ReliefWeb (UN OCHA), NASA EONET v3 & Open-Meteo Flood API"
    )
    val failedToLoadData = s("Gagal Memuat Data", "Failed to Load Data")
    fun monitorTitle(hasActive: Boolean) = s(
        if (hasActive) "⚠️ Pemantauan Bencana" else "📡 Pemantauan Bencana",
        if (hasActive) "⚠️ Disaster Monitor" else "📡 Disaster Monitor"
    )
    val realTimeData = s("Data real-time bencana di area Anda", "Real-time disaster data near you")
    fun lastUpdated(time: String) = s("Terakhir diperbarui: $time", "Last updated: $time")
    val monitorFooter = s(
        "Sumber: ReliefWeb (UN OCHA) & Open-Meteo Flood API\nData diperbarui secara berkala. Event otomatis hilang\nsetelah kondisi kembali normal.",
        "Source: ReliefWeb (UN OCHA) & Open-Meteo Flood API\nData is updated periodically. Events auto-hide\nafter conditions return to normal."
    )
    val active = s("Aktif", "Active")
    val recovery = s("Pemulihan", "Recovery")
    val resolved = s("Pulih", "Resolved")
    fun hiddenInDays(days: Int) = s("Hilang dalam $days hari", "Hidden in $days days")
    val currentSituation = s("Situasi Terkini", "Current Situation")
    val impact = s("Dampak", "Impact")
    fun peopleAffected(count: Int) = s("👥 $count orang terdampak", "👥 $count people affected")
    fun areaAffected(km2: String) = s("📏 $km2 km² area terdampak", "📏 $km2 km² area affected")
    val timeline = s("Timeline", "Timeline")
    fun sourceLabel(name: String) = s("Sumber: $name", "Source: $name")
    val disasterOngoing = s("Bencana berlangsung", "Disaster ongoing")
    fun recoveryPercent(pct: String) = s("Pemulihan: $pct%", "Recovery: $pct%")
    val fullyRecovered = s("Pulih sepenuhnya ✓", "Fully recovered ✓")

    // Duration formatting
    val durationToday = s("Hari ini", "Today")
    val duration1Day = s("1 hari lalu", "1 day ago")
    fun durationDays(d: Int) = s("$d hari lalu", "$d days ago")
    fun durationWeeks(w: Int) = s("$w minggu lalu", "$w weeks ago")
    fun durationMonths(m: Int) = s("$m bulan lalu", "$m months ago")
    fun durationYears(y: Int) = s("$y tahun lalu", "$y years ago")

    // ═══════════════════════════════════════════════════
    //  SETTINGS SCREEN
    // ═══════════════════════════════════════════════════

    val settingsTitle = s("Pengaturan", "Settings")
    val notifications = s("Notifikasi", "Notifications")
    val enableNotifications = s("Aktifkan Notifikasi", "Enable Notifications")
    val enableNotificationsDesc = s("Izinkan aplikasi mengirim notifikasi cuaca", "Allow the app to send weather notifications")
    val dailyForecast = s("Notifikasi Harian", "Daily Forecast")
    val dailyForecastDesc = s("Terima prakiraan cuaca setiap pagi", "Receive weather forecast every morning")
    val severeWeatherAlert = s("Peringatan Cuaca Ekstrem", "Severe Weather Alert")
    val severeWeatherAlertDesc = s("Notifikasi saat cuaca berbahaya (badai, hujan lebat)", "Notifications for dangerous weather (storms, heavy rain)")
    val rainAlert = s("Peringatan Hujan", "Rain Alert")
    val rainAlertDesc = s("Notifikasi saat kemungkinan hujan tinggi", "Notification when rain probability is high")
    val extremeTempAlert = s("Peringatan Suhu Ekstrem", "Extreme Temperature Alert")
    val extremeTempAlertDesc = s("Notifikasi saat suhu sangat tinggi atau rendah", "Notification for very high or low temperatures")
    val about = s("Tentang", "About")
    val openMeteoDesc = s("Open-Meteo API (Gratis, tanpa API key)", "Open-Meteo API (Free, no API key)")
    val geocoding = s("Geocoding", "Geocoding")
    val nominatimDesc = s("Nominatim / OpenStreetMap (Gratis)", "Nominatim / OpenStreetMap (Free)")
    val appVersion = s("Versi Aplikasi", "App Version")

    // ═══════════════════════════════════════════════════
    //  UPDATE DIALOG
    // ═══════════════════════════════════════════════════

    val updateAvailable = s("Pembaruan Tersedia", "Update Available")
    val latestVersion = s("Versi terbaru", "Latest version")
    val size = s("Ukuran", "Size")
    val releaseNotes = s("Catatan Rilis:", "Release Notes:")
    val updateNow = s("Update Sekarang", "Update Now")
    val later = s("Nanti", "Later")
    val downloadingUpdate = s("Mengunduh Pembaruan", "Downloading Update")
    val downloadingUpdateDesc = s(
        "Mohon tunggu, file pembaruan sedang diunduh...\nAnda dapat melihat progres di panel notifikasi.",
        "Please wait, update file is downloading...\nYou can see the progress in the notification panel."
    )
    val readyToInstall = s("Siap Dipasang", "Ready to Install")
    val readyToInstallDesc = s(
        "File pembaruan telah diunduh.\n\nUntuk memasang update:\n1. Ketuk notifikasi download yang selesai\n2. Atau buka file .apk dari Download",
        "Update file has been downloaded.\n\nTo install the update:\n1. Tap the completed download notification\n2. Or open the .apk file from Downloads"
    )
    val updateFailed = s("Gagal Memperbarui", "Update Failed")

    // ═══════════════════════════════════════════════════
    //  NOTIFICATIONS
    // ═══════════════════════════════════════════════════

    val notifDailyTitle = s("Prakiraan Cuaca Hari Ini", "Today's Weather Forecast")
    fun notifMaxMin(max: String, min: String) = s("Maks: $max / Min: $min", "Max: $max / Min: $min")
    fun notifRain(mm: String) = s("Hujan: $mm", "Rain: $mm")
    val notifSevereWeather = s("⚠️ Peringatan Cuaca Ekstrem", "⚠️ Severe Weather Warning")
    fun notifSevereWeatherDesc(loc: String, desc: String) = s(
        "$loc: $desc. Harap berhati-hati!",
        "$loc: $desc. Please be careful!"
    )
    val notifRainWarning = s("🌧️ Peringatan Hujan", "🌧️ Rain Warning")
    fun notifRainDesc(prob: String, time: String) = s(
        "Kemungkinan hujan $prob sekitar $time",
        "Rain probability $prob around $time"
    )
    val notifHighTemp = s("🌡️ Suhu Tinggi", "🌡️ High Temperature")
    val notifLowTemp = s("❄️ Suhu Rendah", "❄️ Low Temperature")
    fun notifTempDesc(temp: String) = s("Suhu saat ini $temp°C.", "Current temperature $temp°C.")
    val notifSunProtection = s("Hindari paparan sinar matahari langsung.", "Avoid direct sun exposure.")
    val notifWarmClothes = s("Gunakan pakaian hangat.", "Wear warm clothes.")
    val notifHighRiskWarning = s("⚠️ Peringatan Cuaca Risiko Tinggi", "⚠️ High Risk Weather Warning")
    fun notifWindWarning(spd: String) = s("Angin hingga $spd km/h", "Wind up to $spd km/h")
    val notifWeatherMonitor = s(
        "Harap berhati-hati dan pantau perkembangan cuaca.",
        "Please be careful and monitor weather developments."
    )
    val notifEmergencyWeather = s("🚨 DARURAT CUACA EKSTREM", "🚨 EXTREME WEATHER EMERGENCY")
    val notifEmergencyTitle = s("SITUASI GAWAT DARURAT", "EMERGENCY SITUATION")
    fun notifEmergencyWind(spd: String) = s("Angin hingga $spd km/h.", "Wind up to $spd km/h.")
    val notifSeekShelter = s(
        "⚠️ SEGERA CARI PERLINDUNGAN ATAU MENGUNGSI!",
        "⚠️ SEEK SHELTER OR EVACUATE IMMEDIATELY!"
    )
    val notifUpdatingWeather = s("Memperbarui data cuaca...", "Updating weather data...")

    // ═══ Disaster Prediction Notification Strings ═══
    val notifDisasterHighTitle = s(
        "⚠️ Peringatan Potensi Bencana",
        "⚠️ Disaster Risk Warning"
    )
    fun notifDisasterHighBody(types: String) = s(
        "Terdeteksi risiko TINGGI: $types. Harap waspada dan pantau perkembangan.",
        "HIGH risk detected: $types. Stay alert and monitor developments."
    )
    val notifDisasterCheck = s(
        "Buka aplikasi untuk detail analisis AI.",
        "Open app for detailed AI analysis."
    )
    val notifDisasterExtremeTitle = s(
        "🚨 DARURAT BENCANA — RISIKO EKSTREM",
        "🚨 DISASTER EMERGENCY — EXTREME RISK"
    )
    val notifDisasterExtremeHeader = s(
        "POTENSI BENCANA LEVEL EKSTREM",
        "EXTREME DISASTER POTENTIAL"
    )
    val notifDisasterExtremeAction = s(
        "⚠️ Segera evakuasi jika berada di area rawan bencana!",
        "⚠️ Evacuate immediately if in disaster-prone area!"
    )
    val disasterAlertSetting = s("Peringatan Bencana AI", "AI Disaster Alert")
    val disasterAlertSettingDesc = s(
        "Notifikasi saat AI mendeteksi risiko bencana tinggi/ekstrem (longsor, banjir, dll)",
        "Notification when AI detects high/extreme disaster risk (landslide, flood, etc)"
    )

    // ═══════════════════════════════════════════════════
    //  APP UPDATE MANAGER
    // ═══════════════════════════════════════════════════

    fun updateCheckFailed(err: String) = s("Gagal memeriksa update: $err", "Failed to check update: $err")
    fun updateDownloadTitle(ver: String) = s("Update Cuacaku v$ver", "Update Cuacaku v$ver")
    val updateDownloading = s("Mengunduh pembaruan aplikasi...", "Downloading app update...")
    fun updateDownloadFailed(err: String) = s("Gagal mengunduh update: $err", "Failed to download update: $err")
    val updateFileNotFound = s("File update tidak ditemukan", "Update file not found")
    fun updateInstallFailed(err: String) = s("Gagal menginstall update: $err", "Failed to install update: $err")

    // ═══════════════════════════════════════════════════
    //  WEATHER REPOSITORY — Alert descriptions
    // ═══════════════════════════════════════════════════

    fun alertThunderstorm(cape: Int) = s(
        "Potensi badai petir berdasarkan CAPE $cape J/kg",
        "Thunderstorm potential based on CAPE $cape J/kg"
    )
    fun alertHeavyRain(mm: Double) = s(
        "Hujan lebat diperkirakan, maks ${"%.1f".format(mm)} mm/jam",
        "Heavy rain expected, max ${"%.1f".format(mm)} mm/hour"
    )
    fun alertHail(freezingLevel: Int) = s(
        "Potensi hujan es dengan level beku di $freezingLevel km",
        "Hail potential with freezing level at $freezingLevel km"
    )
    fun alertStrongWind(spd: Double) = s(
        "Hembusan angin kencang hingga ${"%.0f".format(spd)} km/jam",
        "Strong wind gusts up to ${"%.0f".format(spd)} km/h"
    )
    fun alertTornado(cape: Int, shear: String) = s(
        "Kondisi puting beliung: CAPE $cape, geser angin $shear",
        "Tornado conditions: CAPE $cape, wind shear $shear"
    )
    fun alertBlizzard(spd: Double) = s(
        "Kondisi badai salju dengan hembusan angin ${"%.0f".format(spd)} km/jam",
        "Blizzard conditions with wind gusts ${"%.0f".format(spd)} km/h"
    )

    // ═══════════════════════════════════════════════════
    //  DISASTER MONITOR REPOSITORY
    // ═══════════════════════════════════════════════════

    val floodRiskTitle = s("Potensi Banjir (Debit Sungai Tinggi)", "Flood Risk (High River Discharge)")
    val yourLocation = s("Lokasi Anda", "Your Location")
    fun riverDischargeDesc(discharge: String, max: String) = s(
        "Debit sungai terdekat: $discharge m³/s (maks: $max m³/s). ",
        "Nearby river discharge: $discharge m³/s (max: $max m³/s). "
    )
    val dischargeDecreasing = s("Debit mulai menurun, waspada.", "Discharge decreasing, stay alert.")
    val dischargeVeryHigh = s("Debit sangat tinggi, potensi banjir!", "Discharge very high, flood risk!")
    val disasterIsOngoing = s("Bencana masih berlangsung.", "Disaster is ongoing.")
    fun areaInRecovery(days: Int) = s(
        "Area dalam proses pemulihan ($days hari sejak kejadian).",
        "Area is in recovery ($days days since event)."
    )
    val conditionsNormal = s("Kondisi sudah kembali normal.", "Conditions have returned to normal.")
    fun noUpdateRecovery(days: Int) = s(
        "\n⏳ Tidak ada update > $days hari, kemungkinan dalam pemulihan.",
        "\n⏳ No updates in ${days}+ days, likely in recovery."
    )
    fun recoveryComplete(days: Int) = s(
        "Pemulihan dianggap selesai (tidak ada update > $days hari).",
        "Recovery complete (no updates in ${days}+ days)."
    )
    fun statusChanged(from: String, to: String) = s("Status berubah: $from → $to", "Status changed: $from → $to")
    fun disasterReported(title: String) = s("Bencana dilaporkan: $title", "Disaster reported: $title")
    val latestUpdateLabel = s("Update terbaru", "Latest update")
    val aidInProgress = s("Bantuan sedang disalurkan", "Aid response in progress")
    val reliefActive = s("Operasi bantuan aktif", "Relief operations active")
    val failedLoadDisasterForecast = s(
        "Gagal memuat data cuaca untuk analisis bencana",
        "Failed to load weather data for disaster analysis"
    )
    val failedLoadDisasterMonitor = s(
        "Gagal memuat data pemantauan bencana",
        "Failed to load disaster monitoring data"
    )

    // ═══════════════════════════════════════════════════
    //  DISASTER ANALYSIS ENGINE
    // ═══════════════════════════════════════════════════

    // General recommendations
    val monitorWeather = s("Tetap pantau pembaruan cuaca secara berkala.", "Monitor weather updates regularly.")
    fun alsoWatchFor(types: String) = s("Perhatikan juga potensi $types.", "Also watch for potential $types.")
    val followAuthorities = s("Harap waspada dan ikuti arahan pihak berwenang.", "Stay alert and follow instructions from authorities.")

    // Factor names
    val factorRainfall = s("Curah Hujan", "Rainfall")
    val factorRainIntensity = s("Intensitas Hujan Maks", "Max Rain Intensity")
    val factorRiverDischarge = s("Debit Sungai", "River Discharge")
    val factorAtmosphere = s("Atmosfer", "Atmosphere")
    fun factorAtmosphereDesc(humidity: String, pressure: String) = s(
        "Kelembaban $humidity%, Tekanan $pressure hPa",
        "Humidity $humidity%, Pressure $pressure hPa"
    )
    val factorWaveHeight = s("Tinggi Gelombang", "Wave Height")
    val factorSwell = s("Swell (Gelombang Laut Lepas)", "Swell (Open Sea Waves)")
    val factorWindGust = s("Angin & Gust", "Wind & Gust")
    val factorAirPressure = s("Tekanan Udara", "Air Pressure")
    val factorMinPressure = s("Tekanan Minimum", "Minimum Pressure")
    val factorWindSpeed = s("Kecepatan Angin", "Wind Speed")
    val factorCAPE = s("CAPE", "CAPE")
    val factorWMO = s("Indikator WMO", "WMO Indicator")
    val wmoDetected = s("Badai petir terdeteksi", "Thunderstorm detected")
    val wmoNone = s("Tidak ada indikasi", "No indication")
    val factorWindShear = s("Wind Shear", "Wind Shear")
    val factorHailPotential = s("Potensi Hujan Es", "Hail Potential")
    val factorRainfallToday = s("Curah Hujan Hari Ini", "Rainfall Today")
    val factorRainDuration = s("Durasi Hujan", "Rain Duration")
    val factorRain3Day = s("Akumulasi Hujan 3 Hari", "3-Day Rain Accumulation")
    val factorAvgHumidity = s("Kelembaban Rata-rata", "Average Humidity")
    val factorRain7Day = s("Akumulasi Hujan 7 Hari", "7-Day Rain Accumulation")
    val factorDischargeRatio = s("Rasio Debit Sungai", "River Discharge Ratio")
    val factorConsecutiveRain = s("Hari Hujan Berturut", "Consecutive Rain Days")
    val factorHumidity = s("Kelembaban", "Humidity")

    // Disaster daily descriptions
    fun floodAnalysis(rain: String) = s("Curah hujan $rain mm", "Rainfall $rain mm")
    fun tidalFloodAnalysis(wave: String, swell: String) = s("Gelombang $wave m, swell $swell m", "Waves $wave m, swell $swell m")
    fun cycloneAnalysis(wind: String, pressure: String) = s("Angin $wind km/h, tekanan $pressure hPa", "Wind $wind km/h, pressure $pressure hPa")
    fun thunderstormAnalysis(cape: String) = s("CAPE $cape J/kg ⛈ Badai petir", "CAPE $cape J/kg ⛈ Thunderstorm")
    fun landslideAnalysis(rain: String, accum: String) = s("Hujan $rain mm, akumulasi $accum mm", "Rain $rain mm, accumulated $accum mm")
    fun subsidenceAnalysis(accum: String, days: Int) = s("Akumulasi $accum mm / $days hari hujan", "Accumulated $accum mm / $days rain days")

    // Disaster recommendations per risk level (4 levels × 6 types)
    val floodRecLow = s("Pantau ketinggian air sungai.", "Monitor river water levels.")
    val floodRecMod = s("Siapkan jalur evakuasi jika tinggal di dataran rendah.", "Prepare evacuation routes if you live in low-lying areas.")
    val floodRecHigh = s("Waspada banjir! Hindari area rawan dan siapkan barang penting.", "Flood alert! Avoid risk areas and prepare essentials.")
    val floodRecExtreme = s("EVAKUASI! Segera jauhi area bantaran sungai dan dataran rendah.", "EVACUATE! Immediately leave riverbanks and low-lying areas.")

    val tidalRecLow = s("Pantau informasi pasang surut laut.", "Monitor tidal information.")
    val tidalRecMod = s("Jauhi area pesisir saat pasang tinggi.", "Stay away from coastal areas during high tide.")
    val tidalRecHigh = s("Waspada banjir rob! Amankan kendaraan dan persediaan.", "Tidal flood alert! Secure vehicles and supplies.")
    val tidalRecExtreme = s("BAHAYA BANJIR ROB EKSTREM! Segera evakuasi dari pesisir.", "EXTREME TIDAL FLOOD DANGER! Evacuate from coast immediately.")

    val cycloneRecLow = s("Pantau informasi cuaca maritim.", "Monitor maritime weather information.")
    val cycloneRecMod = s("Nelayan sebaiknya tidak melaut, amankan perahu.", "Fishermen should stay ashore, secure boats.")
    val cycloneRecHigh = s("Hindari aktivitas di laut dan pesisir. Amankan rumah.", "Avoid sea and coastal activities. Secure your home.")
    val cycloneRecExtreme = s("BAHAYA SIKLON! Evakuasi dari pesisir, cari perlindungan kokoh.", "CYCLONE DANGER! Evacuate from coast, seek sturdy shelter.")

    val stormRecLow = s("Sediakan payung atau jas hujan.", "Prepare an umbrella or raincoat.")
    val stormRecMod = s("Hindari area terbuka saat hujan petir.", "Avoid open areas during thunderstorms.")
    val stormRecHigh = s("Tetap di dalam ruangan! Jauhi pohon tinggi dan tiang listrik.", "Stay indoors! Avoid tall trees and power lines.")
    val stormRecExtreme = s("BAHAYA PETIR EKSTREM! Jangan keluar, matikan perangkat elektronik.", "EXTREME LIGHTNING DANGER! Stay inside, turn off electronics.")

    val slideRecLow = s("Perhatikan tanda-tanda pergerakan tanah.", "Watch for signs of ground movement.")
    val slideRecMod = s("Waspada jika tinggal di lereng atau kaki bukit.", "Be cautious if you live on slopes or hillsides.")
    val slideRecHigh = s("Siapkan tas darurat dan jalur evakuasi! Jauhi lereng.", "Prepare emergency bag and evacuation route! Stay away from slopes.")
    val slideRecExtreme = s("EVAKUASI SEGERA dari area lereng dan lembah!", "EVACUATE IMMEDIATELY from slope and valley areas!")

    val subsidRecLow = s("Perhatikan kondisi tanah di sekitar rumah.", "Monitor ground conditions around your home.")
    val subsidRecMod = s("Periksa fondasi bangunan dan drainase.", "Check building foundations and drainage.")
    val subsidRecHigh = s("Waspada amblesan tanah! Laporkan retakan pada bangunan.", "Ground subsidence alert! Report building cracks.")
    val subsidRecExtreme = s("BAHAYA AMBLESAN! Evakuasi jika muncul retakan besar.", "SUBSIDENCE DANGER! Evacuate if large cracks appear.")

    // Detailed analysis descriptions
    fun floodDetailAnalysis(discharge: String) = s("Debit sungai: $discharge m³/s", "River discharge: $discharge m³/s")
    fun tidalDetailAnalysis(wave: String, swell: String) = s(
        "Gelombang: $wave m, Swell: $swell m",
        "Waves: $wave m, Swell: $swell m"
    )
    fun cycloneDetailAnalysis(wind: String, pressure: String) = s(
        "Kecepatan angin: $wind km/h, Tekanan: $pressure hPa",
        "Wind speed: $wind km/h, Pressure: $pressure hPa"
    )
    fun thunderstormDetailAnalysis(cape: String) = s(
        "CAPE: $cape J/kg, Potensi badai petir",
        "CAPE: $cape J/kg, Thunderstorm potential"
    )
    fun landslideDetailAnalysis(rain: String, accum: String) = s(
        "Hujan harian: $rain mm, Akumulasi: $accum mm",
        "Daily rain: $rain mm, Accumulated: $accum mm"
    )
    fun subsidenceDetailAnalysis(accum: String, days: Int) = s(
        "Akumulasi hujan: $accum mm dalam $days hari",
        "Rain accumulated: $accum mm in $days days"
    )

    // ═══════════════════════════════════════════════════
    //  DESCRIPTION BUILDERS (used by DisasterAnalysisEngine)
    // ═══════════════════════════════════════════════════

    private fun riskPrefix(type: String, idLow: String, enLow: String, idMod: String, enMod: String,
                           idHigh: String, enHigh: String, idExtreme: String, enExtreme: String, risk: Int) = when (risk) {
        3 -> s(idExtreme, enExtreme)
        2 -> s(idHigh, enHigh)
        1 -> s(idMod, enMod)
        else -> s(idLow, enLow)
    }

    fun floodDesc(risk: Int, precip: String, maxRain: String, ratioStr: String?): String {
        val prefix = riskPrefix("flood",
            "🟢 Risiko banjir rendah — ", "🟢 Low flood risk — ",
            "🟡 Potensi banjir — ", "🟡 Flood potential — ",
            "🟠 Waspada banjir — ", "🟠 Flood alert — ",
            "🔴 BAHAYA BANJIR — ", "🔴 FLOOD DANGER — ", risk)
        val data = s(
            "Curah hujan prakiraan $precip mm dengan intensitas hingga $maxRain mm/jam. ",
            "Estimated rainfall $precip mm with intensity up to $maxRain mm/hour. ")
        val river = ratioStr?.let {
            s("Debit sungai ${it}x dari rata-rata normal.", "River discharge ${it}x above normal.")
        } ?: ""
        return "$prefix$data$river"
    }

    fun floodRec(risk: Int) = when (risk) { 3 -> floodRecExtreme; 2 -> floodRecHigh; 1 -> floodRecMod; else -> floodRecLow }

    fun tidalFloodDesc(risk: Int, wave: String, swell: String, hasMarine: Boolean): String {
        val prefix = riskPrefix("tidal",
            "🟢 Risiko banjir rob rendah — ", "🟢 Low tidal flood risk — ",
            "🟡 Potensi banjir rob — ", "🟡 Tidal flood potential — ",
            "🟠 Waspada banjir rob — ", "🟠 Tidal flood alert — ",
            "🔴 BAHAYA BANJIR ROB — ", "🔴 TIDAL FLOOD DANGER — ", risk)
        return if (hasMarine) {
            prefix + s(
                "Gelombang hingga $wave m dengan swell $swell m. Daerah pesisir dan pelabuhan perlu waspada.",
                "Waves up to $wave m with swell $swell m. Coastal areas and ports should be alert.")
        } else {
            prefix + s(
                "Data laut tidak tersedia untuk lokasi ini. Analisis berdasarkan data angin dan tekanan.",
                "Marine data not available for this location. Analysis based on wind and pressure data.")
        }
    }

    fun tidalFloodRec(risk: Int, hasMarine: Boolean): String {
        if (!hasMarine) return s(
            "Data laut tidak tersedia. Pantau informasi BMKG untuk peringatan gelombang tinggi.",
            "Marine data not available. Monitor weather agency for wave warnings.")
        return when (risk) { 3 -> tidalRecExtreme; 2 -> tidalRecHigh; 1 -> tidalRecMod; else -> tidalRecLow }
    }

    fun cycloneDesc(risk: Int, pressure: String, wind: String, gusts: String): String {
        val prefix = riskPrefix("cyclone",
            "🟢 Tidak ada indikasi siklon — ", "🟢 No cyclone indication — ",
            "🟡 Indikasi cuaca siklonik — ", "🟡 Cyclonic weather indication — ",
            "🟠 Waspada siklon/badai — ", "🟠 Cyclone/storm alert — ",
            "🔴 BAHAYA SIKLON — ", "🔴 CYCLONE DANGER — ", risk)
        val data = s(
            "Tekanan $pressure hPa, angin $wind km/h (gust $gusts km/h). ",
            "Pressure $pressure hPa, wind $wind km/h (gust $gusts km/h). ")
        val extra = if (risk >= 2) s(
            "Kondisi atmosfer menunjukkan pola siklonik.",
            "Atmospheric conditions show cyclonic pattern.") else ""
        return "$prefix$data$extra"
    }

    fun cycloneRec(risk: Int) = when (risk) { 3 -> cycloneRecExtreme; 2 -> cycloneRecHigh; 1 -> cycloneRecMod; else -> cycloneRecLow }

    fun thunderstormDesc(risk: Int, cape: String, gusts: String, hasTs: Boolean): String {
        val prefix = riskPrefix("storm",
            "🟢 Risiko badai petir rendah — ", "🟢 Low thunderstorm risk — ",
            "🟡 Potensi badai petir — ", "🟡 Thunderstorm potential — ",
            "🟠 Waspada badai petir — ", "🟠 Thunderstorm alert — ",
            "🔴 BADAI PETIR HEBAT — ", "🔴 SEVERE THUNDERSTORM — ", risk)
        val data = s("CAPE $cape J/kg, gust hingga $gusts km/h. ", "CAPE $cape J/kg, gusts up to $gusts km/h. ")
        val extra = if (hasTs) s(
            "Kode cuaca mengindikasikan badai petir aktif.",
            "Weather code indicates active thunderstorm.") else ""
        return "$prefix$data$extra"
    }

    fun thunderstormRec(risk: Int) = when (risk) { 3 -> stormRecExtreme; 2 -> stormRecHigh; 1 -> stormRecMod; else -> stormRecLow }

    fun landslideDesc(risk: Int, precip: String, antecedent: String, hours: Int,
                      slopeAngle: Double = 0.0, saturation: Double = 0.0, hasTerrain: Boolean = false): String {
        val prefix = riskPrefix("slide",
            "🟢 Risiko longsor rendah — ", "🟢 Low landslide risk — ",
            "🟡 Potensi longsor — ", "🟡 Landslide potential — ",
            "🟠 Waspada longsor — ", "🟠 Landslide alert — ",
            "🔴 BAHAYA LONGSOR — ", "🔴 LANDSLIDE DANGER — ", risk)
        val data = s("Curah hujan $precip mm selama $hours jam. ", "Rainfall $precip mm over $hours hours. ")
        val terrainInfo = if (hasTerrain) {
            val slopeStr = "%.1f".format(slopeAngle)
            val satStr = "%.0f".format(saturation * 100)
            s("Kemiringan lereng: $slopeStr°, kejenuhan tanah: $satStr%. ",
              "Slope gradient: $slopeStr°, soil saturation: $satStr%. ")
        } else ""
        val extra = if (antecedent.toDoubleOrNull()?.let { it > 50 } == true)
            s("Akumulasi 3 hari: $antecedent mm — tanah mulai jenuh air.",
              "3-day accumulation: $antecedent mm — soil becoming saturated.") else ""
        return "$prefix$data$terrainInfo$extra"
    }

    fun landslideRec(risk: Int) = when (risk) { 3 -> slideRecExtreme; 2 -> slideRecHigh; 1 -> slideRecMod; else -> slideRecLow }

    fun subsidenceDesc(risk: Int, weeklyPrecip: String, rainDays: Int): String {
        val prefix = riskPrefix("subsid",
            "🟢 Risiko amblas rendah — ", "🟢 Low subsidence risk — ",
            "🟡 Indikasi tanah amblas — ", "🟡 Ground subsidence indication — ",
            "🟠 Potensi tanah amblas — ", "🟠 Ground subsidence potential — ",
            "🔴 WASPADA AMBLASAN — ", "🔴 SUBSIDENCE ALERT — ", risk)
        val data = s(
            "Akumulasi hujan 7 hari: $weeklyPrecip mm, $rainDays hari hujan berturut-turut. ",
            "7-day rain accumulation: $weeklyPrecip mm, $rainDays consecutive rain days. ")
        val extra = if (risk >= 1) s(
            "Genangan berkepanjangan dapat melemahkan struktur tanah.",
            "Prolonged waterlogging can weaken soil structure.") else ""
        return "$prefix$data$extra"
    }

    fun subsidenceRec(risk: Int) = when (risk) { 3 -> subsidRecExtreme; 2 -> subsidRecHigh; 1 -> subsidRecMod; else -> subsidRecLow }

    // ═══ NASA EONET strings ═══
    val eonetLandslideDetected = s("Longsor terdeteksi oleh NASA EONET.", "Landslide detected by NASA EONET.")
    val eonetFloodDetected = s("Banjir terdeteksi oleh NASA EONET.", "Flood detected by NASA EONET.")
    fun eonetDistance(km: String) = s("Jarak: $km km dari lokasi Anda.", "Distance: $km km from your location.")

    // ═══ Terrain Analysis strings ═══
    val terrainAnalysis = s("Analisis Terrain", "Terrain Analysis")
    val slopeGradient = s("Kemiringan Lereng", "Slope Gradient")
    val soilSaturation = s("Kejenuhan Tanah", "Soil Saturation")
    val soilMoisture = s("Kelembaban Tanah", "Soil Moisture")
    val vegetationCover = s("Tutupan Vegetasi", "Vegetation Cover")
    val elevation = s("Elevasi", "Elevation")
    val factorSlopeGradient = s("Kemiringan Lereng", "Slope Gradient")
    val factorSoilSaturation = s("Kejenuhan Tanah", "Soil Saturation")
    val factorRainIntensityMax = s("Intensitas Hujan Maks", "Max Rain Intensity")
    val factorVegetation = s("Tutupan Vegetasi", "Vegetation Cover")
    val terrainDataUnavailable = s("Data terrain tidak tersedia", "Terrain data unavailable")
    fun slopeCategory(name: String) = s("Kategori: $name", "Category: $name")
    fun soilSaturationPercent(pct: String) = s("Kejenuhan: $pct%", "Saturation: $pct%")
    fun elevationValue(m: String) = s("Ketinggian: $m m dpl", "Elevation: $m m ASL")
    fun vegetationIndex(value: String) = s("Indeks vegetasi (proxy): $value", "Vegetation index (proxy): $value")
    val soilMoistureShallow = s("Dangkal (0-7 cm)", "Shallow (0-7 cm)")
    val soilMoistureMedium = s("Menengah (7-28 cm)", "Medium (7-28 cm)")
    val soilMoistureDeep = s("Dalam (28-100 cm)", "Deep (28-100 cm)")
    val soilTemperature = s("Suhu Tanah", "Soil Temperature")
    val landslideRiskFactors = s("Faktor Risiko Longsor", "Landslide Risk Factors")

    // ═══ Terrain soil status ═══
    val soilSaturated = s("Jenuh", "Saturated")
    val soilWet = s("Basah", "Wet")
    val soilNormal = s("Normal", "Normal")
    val terrainDataSourceLabel = s("Open-Elevation SRTM + Open-Meteo Soil", "Open-Elevation SRTM + Open-Meteo Soil")
    val rainfallTodayShort = s("🌧️ Hari Ini", "🌧️ Today")
    val rain3DayShort = s("📊 3 Hari", "📊 3-Day")
    val maxPerHourShort = s("⚡ Maks/jam", "⚡ Max/hr")
    val elevationGridLabel = s("📍 Grid Elevasi (SRTM 30m)", "📍 Elevation Grid (SRTM 30m)")
    val aboveSeaLevel = s("dpl", "ASL")

    // ═══ Disaster Forecast Screen ═══
    val nnSubtitle = s("Neural Network + Analisis Berbasis Aturan", "Neural Network + Rule-Based Ensemble")
    val aboutAnalysisDescV2 = s(
        "Analisis menggunakan Neural Network (MLP 22→32→16→6) " +
                "dengan domain-informed initialization + incremental learning. " +
                "Model belajar otomatis dari data harian (maks 50 sampel, ~16 KB). " +
                "Data > 30 hari otomatis dihapus. Terrain analysis via Open-Elevation (SRTM 30m), " +
                "soil moisture via Open-Meteo, event monitoring via NASA EONET v3. " +
                "Referensi: Gorishniy et al. (NeurIPS 2021), Guo et al. (ICML 2017), " +
                "Sahoo et al. (ICML 2018). " +
                "Data dari Open-Meteo, Marine API, GloFAS/ECMWF. " +
                "Prakiraan bersifat indikatif — ikuti peringatan resmi BMKG.",
        "Analysis uses Neural Network (MLP 22→32→16→6) " +
                "with domain-informed initialization + incremental learning. " +
                "Model auto-learns from daily data (max 50 samples, ~16 KB). " +
                "Data older than 30 days is auto-deleted. Terrain analysis via Open-Elevation (SRTM 30m), " +
                "soil moisture via Open-Meteo, event monitoring via NASA EONET v3. " +
                "References: Gorishniy et al. (NeurIPS 2021), Guo et al. (ICML 2017), " +
                "Sahoo et al. (ICML 2018). " +
                "Data from Open-Meteo, Marine API, GloFAS/ECMWF. " +
                "Forecast is indicative — follow official warnings from meteorological agencies."
    )
    val aiEngineLineV2 = s(
        "AI Engine: MLP-v1.2 + Incremental Learning • Open-Meteo • GloFAS • NASA EONET",
        "AI Engine: MLP-v1.2 + Incremental Learning • Open-Meteo • GloFAS • NASA EONET"
    )

    // ═══ DisasterAnalysisEngine summary ═══
    val summaryAllClear = s(
        "✅ Kondisi aman — tidak terdeteksi potensi bencana signifikan dalam 24 jam ke depan. Tetap pantau pembaruan cuaca secara berkala.",
        "✅ All clear — no significant disaster potential detected in the next 24 hours. Continue to monitor weather updates regularly."
    )
    fun summaryWarning(types: String, risk: String) = s(
        "⚠️ PERINGATAN: Terdeteksi potensi $types dengan risiko $risk. ",
        "⚠️ WARNING: Potential $types detected with $risk risk. "
    )
    fun summaryAlsoWatch(types: String) = s(
        "Perhatikan juga potensi $types (risiko sedang). ",
        "Also watch for $types potential (moderate risk). "
    )
    val summaryStayAlert = s(
        "Harap waspada dan ikuti arahan pihak berwenang.",
        "Stay alert and follow instructions from authorities."
    )

    // ═══ DisasterType bilingual descriptions ═══
    val descFlood = s("Banjir akibat curah hujan tinggi dan debit sungai meningkat", "Flooding due to high rainfall and increased river discharge")
    val descTidalFlood = s("Banjir rob akibat gelombang tinggi dan pasang air laut", "Tidal flooding due to high waves and sea level rise")
    val descCyclone = s("Siklon tropis atau angin topan dengan angin kencang & tekanan rendah", "Tropical cyclone or typhoon with strong winds & low pressure")
    val descThunderstorm = s("Badai petir disertai hujan lebat, angin kencang, dan kemungkinan hujan es", "Severe thunderstorm with heavy rain, strong winds, and possible hail")
    val descLandslide = s("Potensi longsor akibat hujan terus-menerus yang meresap ke tanah", "Landslide potential from continuous rain seeping into the soil")
    val descSubsidence = s("Potensi amblasan tanah akibat genangan air berkepanjangan", "Ground subsidence potential from prolonged waterlogging")

    // ═══ ActiveDisasterType bilingual labels ═══
    val activeFlood = s("Banjir", "Flood")
    val activeFlashFlood = s("Banjir Bandang", "Flash Flood")
    val activeLandslide = s("Tanah Longsor", "Landslide")
    val activeTidalFlood = s("Banjir Rob", "Tidal Flood")
    val activeCyclone = s("Siklon Tropis", "Tropical Cyclone")
    val activeEarthquake = s("Gempa Bumi", "Earthquake")
    val activeVolcanic = s("Erupsi Gunung Api", "Volcanic Eruption")
    val activeTsunami = s("Tsunami", "Tsunami")
    val activeDrought = s("Kekeringan", "Drought")
    val activeOther = s("Bencana Lainnya", "Other Disaster")

    // ═══ SeaCondition bilingual descriptions ═══
    fun seaCondDesc(minH: String, maxH: String) = s("Gelombang $minH-$maxH m", "Waves $minH-$maxH m")
    val seaCondCalm = s("Gelombang < 0.1 m", "Waves < 0.1 m")
    val seaCondVeryHigh = s("Gelombang > 9 m", "Waves > 9 m")

    // ═══════════════════════════════════════════════════════════════
    //  SEISMIC & VOLCANIC MONITORING STRINGS
    // ═══════════════════════════════════════════════════════════════

    // ── Navigation / Screen titles ──
    val navSeismic = s("Seismik", "Seismic")
    val seismicMonitorTitle = s("Pemantau Seismik", "Seismic Monitor")
    val earthquakeMonitor = s("Pemantau Gempa", "Earthquake Monitor")
    val tsunamiMonitor = s("Pemantau Tsunami", "Tsunami Monitor")
    val volcanoMonitor = s("Pemantau Gunung Api", "Volcano Monitor")
    val highWaveWarningTitle = s("Peringatan Gelombang", "Wave Warning")
    val impactAreaTitle = s("Area Terdampak", "Impact Areas")

    // ── Earthquake fields ──
    val earthquakeDepth = s("Kedalaman", "Depth")
    val earthquakeMagnitude = s("Magnitudo", "Magnitude")
    val earthquakeTime = s("Waktu", "Time")
    val earthquakeDistance = s("Jarak", "Distance")
    val earthquakeIntensity = s("Intensitas", "Intensity")
    val earthquakeFelt = s("Terasa oleh", "Felt by")
    val earthquakePeople = s("orang", "people")
    val distance = s("Jarak", "Distance")
    val intensity = s("Intensitas", "Intensity")
    val alertLevel = s("Level Peringatan", "Alert Level")
    val nearbyEarthquakes = s("Gempa Terdekat", "Nearby Earthquakes")
    val significantEarthquakes = s("Gempa Signifikan", "Significant Earthquakes")
    val recentEarthquakes = s("Gempa Terkini", "Recent Earthquakes")
    val noEarthquakesDetected = s("Tidak ada gempa terdeteksi", "No earthquakes detected")
    val earthquakeDetails = s("Detail Gempa", "Earthquake Details")
    val earthquakeReviewed = s("Diverifikasi", "Reviewed")
    val earthquakeAutomatic = s("Otomatis", "Automatic")
    fun earthquakeAgo(time: String) = s("$time yang lalu", "$time ago")
    val minutesAgo = s("menit", "minutes")
    val hoursAgo = s("jam", "hours")
    val daysAgo = s("hari", "days")
    val justNow = s("Baru saja", "Just now")
    val estimatedMMI = s("Perkiraan Intensitas MMI di Lokasi Anda", "Estimated MMI Intensity at Your Location")
    val impactRadius = s("Radius Dampak", "Impact Radius")

    // ── Tsunami ──
    val tsunamiRisk = s("Risiko Tsunami", "Tsunami Risk")
    val tsunamiNoRisk = s("Tidak ada risiko tsunami saat ini", "No tsunami risk at this time")
    val tsunamiNoRiskAdvice = s("Tidak ada gempa bawah laut signifikan terdeteksi", "No significant submarine earthquakes detected")
    val tsunamiInfoDesc = s("Gempa besar terdeteksi, namun risiko tsunami rendah", "Large earthquake detected, but tsunami risk is low")
    val tsunamiInfoAdvice = s("Pantau informasi resmi dari BMKG/pihak berwenang", "Monitor official information from authorities")
    val tsunamiAdvisoryDesc = s("Potensi tsunami ringan terdeteksi dari gempa bawah laut", "Potential minor tsunami detected from submarine earthquake")
    val tsunamiAdvisoryAdvice = s("Jauhi pantai dan perairan dangkal. Ikuti arahan BMKG", "Stay away from beaches and shallow waters. Follow official guidance")
    val tsunamiWatchDesc = s("Risiko tsunami SIGNIFIKAN dari gempa bawah laut kuat", "SIGNIFICANT tsunami risk from strong submarine earthquake")
    val tsunamiWatchAdvice = s("SEGERA jauhi pantai! Menuju ke tempat tinggi. Ikuti jalur evakuasi tsunami", "IMMEDIATELY move away from coast! Head to high ground. Follow tsunami evacuation routes")
    val tsunamiWarningDesc = s("PERINGATAN TSUNAMI! Gempa sangat kuat di bawah laut terdeteksi", "TSUNAMI WARNING! Very strong submarine earthquake detected")
    val tsunamiWarningAdvice = s("🚨 EVAKUASI SEGERA ke tempat tinggi! Jangan tunggu peringatan resmi. Setiap detik berharga!", "🚨 EVACUATE IMMEDIATELY to high ground! Don't wait for official warnings. Every second counts!")
    val tsunamiPotentialFlag = s("USGS menandai potensi tsunami", "USGS flagged tsunami potential")
    val tsunamiTriggeredBy = s("Dipicu oleh gempa", "Triggered by earthquake")
    val estimatedArrival = s("Perkiraan Tiba", "Est. Arrival")
    val minutes = s("menit", "min")

    // ── Tsunami risk factors ──
    val tsunamiFactorMagnitude = s("Magnitudo Gempa", "Earthquake Magnitude")
    val tsunamiFactorDepth = s("Kedalaman", "Depth")
    val tsunamiFactorDistance = s("Jarak dari User", "Distance from User")
    val tsunamiFactorUSGSFlag = s("Flag Tsunami USGS", "USGS Tsunami Flag")
    val riskFactors = s("Faktor Risiko", "Risk Factors")

    // ── Volcano ──
    val volcanoActivity = s("Aktivitas Gunung Api", "Volcanic Activity")
    val nearbyVolcanoes = s("Gunung Api Terdekat", "Nearby Volcanoes")
    val volcanoName = s("Nama", "Name")
    val volcanoElevation = s("Ketinggian", "Elevation")
    val volcanoLastEruption = s("Erupsi Terakhir", "Last Eruption")
    val volcanoType = s("Tipe", "Type")
    val volcanoAlertLevel = s("Level Peringatan", "Alert Level")
    val volcanoColorCode = s("Kode Warna", "Color Code")
    val volcanoSource = s("Sumber", "Source")
    val volcanoNoActivity = s("Tidak ada aktivitas vulkanik signifikan", "No significant volcanic activity")
    val volcanoNearbyList = s("Gunung api dalam radius 300 km", "Volcanoes within 300 km radius")
    val volcanoActive = s("Aktif", "Active")
    val volcanoNormal = s("Normal", "Normal")
    val volcanoDangerZone = s("Zona Bahaya", "Danger Zone")

    // ── High Wave Warning ──
    val highWaveWarning = s("Peringatan Gelombang Tinggi", "High Wave Warning")
    val waveHeight = s("Tinggi Gelombang", "Wave Height")
    val swellHeight = s("Tinggi Swell", "Swell Height")
    val maxWaveForecast = s("Maks. Gelombang Prakiraan", "Max. Wave Forecast")
    val peakWaveTime = s("Waktu Puncak Gelombang", "Peak Wave Time")
    val waveHighDesc = s("Laut sangat ganas! Gelombang sangat tinggi berbahaya bagi semua kapal", "Extremely rough seas! Very high waves dangerous for all vessels")
    val waveVeryRoughDesc = s("Laut ganas! Gelombang tinggi, berbahaya untuk kapal kecil", "Rough seas! High waves, dangerous for small vessels")
    val waveRoughDesc = s("Gelombang cukup tinggi, hati-hati bagi nelayan dan kapal kecil", "Moderate-high waves, caution for fishermen and small boats")
    val waveModerateDesc = s("Gelombang sedang, kondisi laut cukup aman untuk berlayar", "Moderate waves, sea conditions fairly safe for sailing")
    val waveCalmDesc = s("Laut tenang, aman untuk berlayar", "Calm seas, safe for sailing")
    val waveHighAdvice = s("⛔ JANGAN melaut! Semua kapal dianjurkan tetap di pelabuhan", "⛔ DO NOT set sail! All vessels advised to stay in port")
    val waveVeryRoughAdvice = s("⚠️ Kapal kecil & nelayan DILARANG melaut. Kapal besar berhati-hati", "⚠️ Small vessels & fishermen PROHIBITED from sailing. Large vessels use caution")
    val waveRoughAdvice = s("Nelayan dan kapal kecil harap berhati-hati. Pantau prakiraan gelombang", "Fishermen and small boats exercise caution. Monitor wave forecast")
    val waveModerateAdvice = s("Kondisi cukup aman. Tetap pantau prakiraan cuaca laut", "Conditions fairly safe. Continue monitoring marine weather forecast")
    val waveCalmAdvice = s("Aman untuk berlayar. Selamat melaut!", "Safe for sailing. Have a safe voyage!")
    val waveHourlyForecast = s("Prakiraan Gelombang Per Jam", "Hourly Wave Forecast")
    val maritimeSafety = s("🚢 Keselamatan Maritim", "🚢 Maritime Safety")
    val marineConditions = s("Kondisi Laut", "Marine Conditions")

    // ── Impact Area ──
    val impactAreas = s("Area Terdampak Bencana", "Disaster Impact Areas")
    val noImpactAreas = s("Tidak ada area terdampak aktif", "No active impact areas")
    val impactZones = s("Zona Dampak", "Impact Zones")
    val userInImpactZone = s("⚠️ Anda berada di zona dampak!", "⚠️ You are in the impact zone!")
    val userSafeFromImpact = s("✅ Anda di luar zona dampak", "✅ You are outside the impact zone")
    val viewImpactAreas = s("Lihat Area Terdampak", "View Impact Areas")
    val totalActiveEvents = s("Total Event Aktif", "Total Active Events")
    val activeThreats = s("Ancaman Aktif", "Active Threats")
    val noActiveThreats = s("Tidak ada ancaman aktif", "No active threats")

    // ── Notification titles ──
    val notifEarthquakeTitle = s("⚠️ Peringatan Gempa Bumi", "⚠️ Earthquake Alert")
    val notifTsunamiWarningTitle = s("🚨 DARURAT TSUNAMI!", "🚨 TSUNAMI EMERGENCY!")
    val notifTsunamiWatchTitle = s("⚠️ Siaga Tsunami", "⚠️ Tsunami Watch")
    val notifTsunamiAdvisoryTitle = s("ℹ️ Peringatan Dini Tsunami", "ℹ️ Tsunami Advisory")
    val notifVolcanoTitle = s("🌋 Peringatan Gunung Api", "🌋 Volcano Alert")
    val notifHighWaveTitle = s("🌊 Peringatan Gelombang Tinggi", "🌊 High Wave Warning")

    // ── General ──
    val lastUpdated = s("Terakhir diperbarui", "Last updated")
    val loading = s("Memuat...", "Loading...")
    val failedLoadSeismicData = s("Gagal memuat data seismik", "Failed to load seismic data")
    val seismicDataNote = s(
        "Data gempa dari USGS (real-time). Risiko tsunami dihitung dari parameter gempa bawah laut. Data gunung api dari USGS & NASA EONET.",
        "Earthquake data from USGS (real-time). Tsunami risk calculated from submarine earthquake parameters. Volcano data from USGS & NASA EONET."
    )
    val waveDataNote = s(
        "Data gelombang dari Open-Meteo Marine API. Peringatan ini untuk keselamatan pelaut & nelayan.",
        "Wave data from Open-Meteo Marine API. This warning is for maritime safety of sailors & fishermen."
    )
    val km = s("km", "km")
    val meters = s("m", "m")

    companion object {
        val ID = AppStrings(AppLocale.ID)
        val EN = AppStrings(AppLocale.EN)

        fun get(locale: AppLocale) = when (locale) {
            AppLocale.ID -> ID
            AppLocale.EN -> EN
        }
    }
}
