package com.weather.forecast.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.weather.forecast.BuildConfig
import com.weather.forecast.data.api.GitHubApiService
import com.weather.forecast.data.api.GitHubRelease
import com.weather.forecast.data.locale.AppLocaleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * AppUpdateManager — Mengelola pengecekan dan instalasi pembaruan aplikasi
 *
 * Komponen ini bertanggung jawab untuk:
 * 1. Memeriksa versi terbaru di GitHub Releases
 * 2. Membandingkan dengan versi yang terinstall
 * 3. Mengunduh APK terbaru menggunakan DownloadManager
 * 4. Memicu proses instalasi/update APK
 *
 * ## Cara Kerja Update vs Install
 * - Android secara otomatis mendeteksi apakah aplikasi sudah terinstall
 *   berdasarkan applicationId (com.weather.forecast)
 * - Jika applicationId sama & APK ditandatangani dengan signing key yang sama:
 *   → Android melakukan UPDATE (data pengguna tetap terjaga)
 * - Jika aplikasi belum pernah diinstall:
 *   → Android melakukan INSTALL baru
 *
 * ## Alur Pengecekan Update
 * ```
 * checkForUpdate()
 *   → GitHub API: GET /repos/{owner}/{repo}/releases/latest
 *   → Bandingkan tagName (e.g. "v1.1.0") dengan BuildConfig.VERSION_NAME
 *   → Jika lebih baru → UpdateState.Available
 *   → Jika sama/lebih lama → UpdateState.NotAvailable
 * ```
 *
 * ## Alur Download & Install
 * ```
 * downloadAndInstall()
 *   → DownloadManager mengunduh APK ke folder Downloads
 *   → BroadcastReceiver mendeteksi download selesai
 *   → Trigger intent ACTION_VIEW / ACTION_INSTALL_PACKAGE
 *   → Android PackageManager menangani install/update
 * ```
 *
 * ## Perbandingan Versi
 * Format: semantic versioning (major.minor.patch), contoh: "1.1.0"
 * Prefix "v" di tag GitHub akan dihapus sebelum dibandingkan.
 * Perbandingan: split by ".", bandingkan setiap segmen sebagai integer.
 *
 * @see GitHubApiService
 * @see UpdateState
 */
class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"

        /** Nama file APK yang didownload */
        private const val APK_FILE_NAME = "cuacaku-update.apk"

        /** MIME type untuk file APK */
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }

    /**
     * GitHub API service instance (lazy-initialized)
     * Menggunakan OkHttpClient terpisah dari weather API
     * karena base URL dan header berbeda.
     */
    private val gitHubApi: GitHubApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Cuacaku/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl(GitHubApiService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
    }

    /** State update saat ini — di-observe oleh UI */
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** Download ID dari DownloadManager, disimpan untuk tracking */
    private var currentDownloadId: Long = -1L

    /**
     * Periksa apakah ada versi terbaru di GitHub Releases.
     *
     * Langkah:
     * 1. Set state → Checking
     * 2. Panggil GitHub API → releases/latest
     * 3. Parse tag_name → hapus prefix "v"
     * 4. Bandingkan dengan BuildConfig.VERSION_NAME
     * 5. Set state → Available (jika lebih baru) atau NotAvailable
     *
     * @return true jika ada update tersedia, false jika tidak atau error
     */
    suspend fun checkForUpdate(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Checking
                Log.d(TAG, "Memeriksa update... Versi saat ini: ${BuildConfig.VERSION_NAME}")

                val release = gitHubApi.getLatestRelease(
                    owner = GitHubApiService.REPO_OWNER,
                    repo = GitHubApiService.REPO_NAME
                )

                val latestVersion = release.tagName.removePrefix("v")
                val currentVersion = BuildConfig.VERSION_NAME

                Log.d(TAG, "Versi terbaru di GitHub: $latestVersion, Versi terinstall: $currentVersion")

                if (isNewerVersion(latestVersion, currentVersion)) {
                    // Cari asset APK dari release
                    val apkAsset = release.assets.find { it.name.endsWith(".apk") }

                    if (apkAsset != null) {
                        Log.d(TAG, "Update tersedia! APK: ${apkAsset.name} (${apkAsset.size} bytes)")
                        _updateState.value = UpdateState.Available(
                            latestVersion = latestVersion,
                            downloadUrl = apkAsset.downloadUrl,
                            releaseNotes = release.body ?: "",
                            fileSize = apkAsset.size
                        )
                        true
                    } else {
                        Log.w(TAG, "Release tersedia tapi tidak ada file APK sebagai asset")
                        _updateState.value = UpdateState.NotAvailable
                        false
                    }
                } else {
                    Log.d(TAG, "Aplikasi sudah versi terbaru")
                    _updateState.value = UpdateState.NotAvailable
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memeriksa update: ${e.message}", e)
                _updateState.value = UpdateState.Error(
                    message = AppLocaleManager.strings.updateCheckFailed(e.localizedMessage ?: "Unknown error")
                )
                false
            }
        }
    }

    /**
     * Download APK terbaru dan trigger instalasi.
     *
     * Menggunakan Android DownloadManager untuk:
     * - Download di background dengan notifikasi progress
     * - Resume otomatis jika terputus
     * - Menyimpan file di folder Downloads publik
     *
     * Setelah download selesai, BroadcastReceiver akan
     * memicu proses instalasi via [installApk].
     */
    fun downloadAndInstall() {
        val state = _updateState.value
        if (state !is UpdateState.Available) {
            Log.w(TAG, "downloadAndInstall() dipanggil tapi state bukan Available: $state")
            return
        }

        try {
            _updateState.value = UpdateState.Downloading

            // Hapus file lama jika ada
            val oldFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APK_FILE_NAME
            )
            if (oldFile.exists()) {
                oldFile.delete()
                Log.d(TAG, "File APK lama dihapus: ${oldFile.absolutePath}")
            }

            // Buat request download
            val request = DownloadManager.Request(Uri.parse(state.downloadUrl)).apply {
                setTitle(AppLocaleManager.strings.updateDownloadTitle(state.latestVersion))
                setDescription(AppLocaleManager.strings.updateDownloading)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
                setMimeType(APK_MIME_TYPE)
                // Allow download over mobile & WiFi
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )
            }

            // Mulai download
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            currentDownloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download dimulai. ID: $currentDownloadId, URL: ${state.downloadUrl}")

            // Register receiver untuk mendeteksi download selesai
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (downloadId == currentDownloadId) {
                        Log.d(TAG, "Download selesai. ID: $downloadId")
                        context.unregisterReceiver(this)
                        installApk()
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai download: ${e.message}", e)
            _updateState.value = UpdateState.Error(
                message = AppLocaleManager.strings.updateDownloadFailed(e.localizedMessage ?: "Unknown error")
            )
        }
    }

    /**
     * Trigger instalasi APK yang sudah didownload.
     *
     * ## Mekanisme Install vs Update
     * Android PackageManager otomatis mendeteksi:
     * - Jika `applicationId` sudah terinstall DAN signing key cocok
     *   → Aplikasi akan di-UPDATE (data tetap tersimpan)
     * - Jika belum terinstall
     *   → Aplikasi akan di-INSTALL baru
     * - Jika signing key berbeda
     *   → Instalasi ditolak (keamanan Android)
     *
     * Pengguna akan melihat dialog konfirmasi dari sistem:
     * "Do you want to update this app?" atau "Do you want to install this app?"
     */
    private fun installApk() {
        try {
            val apkFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APK_FILE_NAME
            )

            if (!apkFile.exists()) {
                Log.e(TAG, "File APK tidak ditemukan: ${apkFile.absolutePath}")
                _updateState.value = UpdateState.Error(message = AppLocaleManager.strings.updateFileNotFound)
                return
            }

            Log.d(TAG, "Memulai instalasi APK: ${apkFile.absolutePath} (${apkFile.length()} bytes)")

            // Gunakan FileProvider untuk Android 7.0+ (API 24+)
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            _updateState.value = UpdateState.ReadyToInstall
            context.startActivity(installIntent)

            Log.d(TAG, "Intent instalasi diluncurkan")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menginstall APK: ${e.message}", e)
            _updateState.value = UpdateState.Error(
                message = AppLocaleManager.strings.updateInstallFailed(e.localizedMessage ?: "Unknown error")
            )
        }
    }

    /**
     * Reset state ke Idle.
     * Dipanggil saat user menutup dialog update.
     */
    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * Bandingkan dua versi semantic versioning.
     *
     * Format: "major.minor.patch" (contoh: "1.1.0")
     *
     * @param latest Versi terbaru dari GitHub (tanpa prefix "v")
     * @param current Versi yang terinstall (dari BuildConfig.VERSION_NAME)
     * @return true jika [latest] lebih baru dari [current]
     *
     * Contoh:
     * - isNewerVersion("1.1.0", "1.0.0") → true
     * - isNewerVersion("1.0.0", "1.0.0") → false
     * - isNewerVersion("0.9.0", "1.0.0") → false
     * - isNewerVersion("2.0.0", "1.9.9") → true
     */
    internal fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".").map { it.toInt() }
            val currentParts = current.split(".").map { it.toInt() }

            // Bandingkan setiap segmen: major → minor → patch
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false // Versi sama persis
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing versi: latest=$latest, current=$current", e)
            false
        }
    }
}

/**
 * State machine untuk alur update.
 *
 * ```
 * Idle → Checking → Available → Downloading → ReadyToInstall
 *                 ↘ NotAvailable
 *                 ↘ Error
 *         Available ↘ Error (jika download gagal)
 * ```
 *
 * - [Idle]: Belum melakukan pengecekan
 * - [Checking]: Sedang memeriksa GitHub Releases
 * - [Available]: Ada versi baru, siap didownload
 * - [NotAvailable]: Sudah versi terbaru
 * - [Downloading]: APK sedang diunduh via DownloadManager
 * - [ReadyToInstall]: APK sudah didownload, intent install diluncurkan
 * - [Error]: Terjadi kesalahan di salah satu tahap
 */
sealed class UpdateState {
    /** Belum melakukan pengecekan */
    data object Idle : UpdateState()

    /** Sedang memeriksa versi terbaru */
    data object Checking : UpdateState()

    /** Versi terbaru tersedia untuk didownload */
    data class Available(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val fileSize: Long
    ) : UpdateState()

    /** Sudah versi terbaru, tidak perlu update */
    data object NotAvailable : UpdateState()

    /** Sedang mengunduh APK */
    data object Downloading : UpdateState()

    /** APK siap diinstall, dialog sistem akan muncul */
    data object ReadyToInstall : UpdateState()

    /** Terjadi error */
    data class Error(val message: String) : UpdateState()
}
