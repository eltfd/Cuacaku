package com.weather.forecast

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.weather.forecast.ui.components.UpdateDialog
import com.weather.forecast.ui.navigation.WeatherNavigation
import com.weather.forecast.ui.theme.WeatherForecastTheme
import com.weather.forecast.update.AppUpdateManager
import com.weather.forecast.update.UpdateState

/**
 * MainActivity - Entry point aplikasi Cuacaku
 *
 * ## Responsibilities
 * - Splash screen dengan animasi
 * - Edge-to-edge display
 * - Jetpack Compose UI
 * - Pengecekan update otomatis saat aplikasi dibuka
 *
 * ## Alur Update Otomatis
 * Setiap kali aplikasi dibuka:
 * 1. [AppUpdateManager.checkForUpdate] dipanggil via LaunchedEffect
 * 2. Jika ada versi baru → [UpdateDialog] ditampilkan
 * 3. User klik "Update Sekarang" → APK diunduh & dipasang
 * 4. Android PackageManager otomatis mendeteksi:
 *    - Jika APK sudah terinstall → UPDATE (data tetap ada)
 *    - Jika belum terinstall → INSTALL baru
 *
 * @see AppUpdateManager
 * @see UpdateDialog
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    /** Manager untuk pengecekan dan instalasi update */
    private lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen sebelum super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inisialisasi AppUpdateManager
        updateManager = AppUpdateManager(applicationContext)
        Log.d(TAG, "AppUpdateManager diinisialisasi. Versi: ${BuildConfig.VERSION_NAME}")

        setContent {
            WeatherForecastTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Observe update state
                    val updateState by updateManager.updateState.collectAsState()

                    // Cek update otomatis saat pertama kali dibuka
                    LaunchedEffect(Unit) {
                        Log.d(TAG, "Memulai pengecekan update otomatis...")
                        updateManager.checkForUpdate()
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main content — navigasi utama
                        WeatherNavigation()

                        // Update dialog — muncul overlay jika ada update
                        if (updateState is UpdateState.Available ||
                            updateState is UpdateState.Downloading ||
                            updateState is UpdateState.ReadyToInstall ||
                            updateState is UpdateState.Error
                        ) {
                            UpdateDialog(
                                state = updateState,
                                onUpdate = {
                                    Log.d(TAG, "User memilih update. Memulai download...")
                                    updateManager.downloadAndInstall()
                                },
                                onDismiss = {
                                    Log.d(TAG, "User menutup dialog update.")
                                    updateManager.dismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
