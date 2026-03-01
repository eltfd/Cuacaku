package com.weather.forecast.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.weather.forecast.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * SecurityManager — Perlindungan integritas & anti-tampering
 *
 * Lapisan keamanan berlapis:
 * 1. **Signature verification** — Deteksi repackaging/modifikasi APK
 * 2. **Root detection** — Deteksi device yang di-root (risiko data injection)
 * 3. **Debugger detection** — Deteksi debugging di release build
 * 4. **Emulator detection** — Deteksi jika berjalan di emulator
 * 5. **Installer verification** — Deteksi sideloading dari sumber mencurigakan
 *
 * Pendekatan: **Defense-in-depth** — setiap layer independen.
 * Jika satu check di-bypass, layer lain masih melindungi.
 */
object SecurityManager {

    private const val TAG = "SecurityManager"

    /** Threat level dari security check */
    enum class ThreatLevel {
        SAFE,       // Tidak ada ancaman terdeteksi
        WARNING,    // Potensi ancaman (root, emulator) — app tetap jalan
        CRITICAL    // Integritas APK terganggu — pertimbangkan blokir
    }

    data class SecurityReport(
        val threatLevel: ThreatLevel,
        val isRooted: Boolean,
        val isDebugged: Boolean,
        val isEmulator: Boolean,
        val isSignatureValid: Boolean,
        val isTampered: Boolean,
        val installerPackage: String?,
        val warnings: List<String>
    )

    // ══════════════════════════════════════════════════
    //  Main Entry Point
    // ══════════════════════════════════════════════════

    /**
     * Jalankan semua security checks.
     * Dipanggil saat app start (WeatherApplication.onCreate).
     */
    fun performSecurityCheck(context: Context): SecurityReport {
        val warnings = mutableListOf<String>()

        val isRooted = checkRoot()
        val isDebugged = checkDebugger(context)
        val isEmulator = checkEmulator()
        val isSignatureValid = verifySignature(context)
        val installerPackage = getInstallerPackage(context)

        if (isRooted) warnings.add("Device is rooted")
        if (isDebugged) warnings.add("Debugger attached")
        if (isEmulator) warnings.add("Running on emulator")
        if (!isSignatureValid) warnings.add("APK signature mismatch")

        val isTampered = !isSignatureValid
        val threatLevel = when {
            isTampered -> ThreatLevel.CRITICAL
            isDebugged && !BuildConfig.DEBUG -> ThreatLevel.CRITICAL
            isRooted || isEmulator -> ThreatLevel.WARNING
            else -> ThreatLevel.SAFE
        }

        if (!BuildConfig.DEBUG && warnings.isNotEmpty()) {
            Log.w(TAG, "Security: $threatLevel — ${warnings.joinToString(", ")}")
        }

        return SecurityReport(
            threatLevel = threatLevel,
            isRooted = isRooted,
            isDebugged = isDebugged,
            isEmulator = isEmulator,
            isSignatureValid = isSignatureValid,
            isTampered = isTampered,
            installerPackage = installerPackage,
            warnings = warnings
        )
    }

    // ══════════════════════════════════════════════════
    //  1. Root Detection (Multi-layered)
    // ══════════════════════════════════════════════════

    private fun checkRoot(): Boolean {
        return checkSuBinary() || checkRootPaths() || checkBuildTags() || checkRootProperties()
    }

    /** Check for 'su' binary in common paths */
    private fun checkSuBinary(): Boolean {
        val suPaths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/su/bin/su", "/su/bin", "/magisk/.core/bin/su"
        )
        return suPaths.any { File(it).exists() }
    }

    /** Check for root management apps and Magisk */
    private fun checkRootPaths(): Boolean {
        val rootIndicators = arrayOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/system/app/Magisk.apk",
            "/data/adb/magisk",
            "/sbin/.magisk"
        )
        return rootIndicators.any { File(it).exists() }
    }

    /** Check build tags for test-keys (common on custom ROMs) */
    private fun checkBuildTags(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    /** Check dangerous system properties */
    private fun checkRootProperties(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.debuggable"))
            val debuggable = process.inputStream.bufferedReader().readLine()?.trim()
            process.destroy()
            debuggable == "1"
        } catch (e: Exception) {
            false
        }
    }

    // ══════════════════════════════════════════════════
    //  2. Debugger Detection
    // ══════════════════════════════════════════════════

    private fun checkDebugger(context: Context): Boolean {
        // Check if debugger is connected
        if (android.os.Debug.isDebuggerConnected()) return true

        // Check if app is debuggable (should NOT be in release)
        val appInfo = context.applicationInfo
        if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            if (!BuildConfig.DEBUG) return true // Debuggable in release = tampered
        }

        // Check ADB enabled on device
        try {
            val adbEnabled = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED, 0
            )
            // ADB alone is just a warning, not critical
        } catch (_: Exception) {}

        return false
    }

    // ══════════════════════════════════════════════════
    //  3. Emulator Detection
    // ══════════════════════════════════════════════════

    private fun checkEmulator(): Boolean {
        var score = 0

        if (Build.FINGERPRINT.startsWith("generic")) score += 2
        if (Build.FINGERPRINT.startsWith("unknown")) score++
        if (Build.MODEL.contains("google_sdk")) score += 2
        if (Build.MODEL.contains("Emulator")) score += 2
        if (Build.MODEL.contains("Android SDK built for x86")) score += 2
        if (Build.MANUFACTURER.contains("Genymotion")) score += 2
        if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) score += 2
        if ("google_sdk" == Build.PRODUCT) score += 2
        if (Build.HARDWARE.contains("goldfish")) score += 2
        if (Build.HARDWARE.contains("ranchu")) score += 2
        if (Build.BOARD == "unknown") score++
        if (Build.BOOTLOADER == "unknown") score++

        // Threshold: 3+ points = emulator
        return score >= 3
    }

    // ══════════════════════════════════════════════════
    //  4. APK Signature Verification
    // ══════════════════════════════════════════════════

    /**
     * Verify APK signing certificate matches expected hash.
     * Deteksi repackaging: jika seseorang decompile & re-sign APK,
     * certificate hash akan berbeda.
     */
    private fun verifySignature(context: Context): Boolean {
        return try {
            val expectedHash = getExpectedSignatureHash()
            if (expectedHash.isNullOrEmpty()) return true // No hash configured, skip

            val currentHash = getCurrentSignatureHash(context)
            currentHash != null && currentHash == expectedHash
        } catch (e: Exception) {
            true // Jangan block app jika check gagal
        }
    }

    /**
     * Get SHA-256 hash dari signing certificate saat ini.
     */
    @Suppress("DEPRECATION")
    private fun getCurrentSignatureHash(context: Context): String? {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }

            if (signatures.isNullOrEmpty()) return null

            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signatures.first().toByteArray())
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Expected signing certificate hash.
     * Set ini dari output `getCurrentSignatureHash()` setelah
     * pertama kali build release.
     *
     * Kosong = skip verification (first time setup).
     */
    private fun getExpectedSignatureHash(): String? {
        // Will be populated after first release build
        // Run with SIGNATURE_DEBUG=true to log the hash, then paste here
        return null
    }

    // ══════════════════════════════════════════════════
    //  5. Installer Verification
    // ══════════════════════════════════════════════════

    /**
     * Check dari mana app diinstall.
     * Play Store = "com.android.vending"
     * Self-update = null (sideload via PackageInstaller)
     */
    @Suppress("DEPRECATION")
    private fun getInstallerPackage(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
    }
}
