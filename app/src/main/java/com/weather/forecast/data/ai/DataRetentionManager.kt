package com.weather.forecast.data.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * Data Retention Manager — Kebijakan Retensi Data AI
 *
 * Mengelola siklus hidup data yang disimpan oleh AI system:
 * - Learning samples → maks 50 sampel, expire 30 hari
 * - Weight deltas → selalu ada (≈1 KB), tidak expire
 * - Prediction cache → expire 1 hari
 *
 * ── Prinsip ──
 * 1. **Minimasi data**: Hanya simpan yang diperlukan untuk learning
 * 2. **Auto-expire**: Data kadaluarsa otomatis dihapus
 * 3. **Budget**: Total data AI < 20 KB kapan saja
 * 4. **Transparency**: Statistik penggunaan storage bisa diakses
 *
 * ── Storage Breakdown ──
 * | Komponen          | Maks Ukuran | Expiry    |
 * |-------------------|-------------|-----------|
 * | Learning samples  | ~14 KB      | 30 hari   |
 * | Weight deltas     | ~1 KB       | Persisten |
 * | Momentum state    | ~1 KB       | Persisten |
 * | Metadata          | ~0.1 KB     | Persisten |
 * | **TOTAL**         | **~16 KB**  |           |
 */
class DataRetentionManager(private val context: Context) {

    companion object {
        /** Nama SharedPreferences yang dikelola AI */
        private const val AI_PREFS_NAME = "ai_learning_data"

        /** Batas total ukuran data AI (bytes) */
        const val MAX_STORAGE_BYTES = 20_000L  // 20 KB

        /** Interval cleanup otomatis (24 jam) */
        private const val CLEANUP_INTERVAL_MS = 24 * 3600 * 1000L

        /** Umur sampel sebelum expire (30 hari) */
        const val SAMPLE_EXPIRY_DAYS = 30
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(AI_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Jalankan cleanup otomatis jika sudah waktunya.
     * Dipanggil setiap kali app dibuka.
     */
    fun performAutoCleanup() {
        val now = System.currentTimeMillis()
        val lastCleanup = prefs.getLong("last_cleanup", 0)

        if (now - lastCleanup < CLEANUP_INTERVAL_MS) return

        // Hapus sampel kadaluarsa
        cleanupExpiredData()

        // Cek total size
        val usage = getStorageUsage()
        if (usage.totalBytes > MAX_STORAGE_BYTES) {
            trimToFit()
        }

        prefs.edit().putLong("last_cleanup", now).apply()
    }

    /**
     * Mendapatkan detail penggunaan storage oleh AI.
     */
    fun getStorageUsage(): StorageUsage {
        val samplesJson = prefs.getString("learning_samples", "") ?: ""
        val deltasJson = prefs.getString("weight_deltas", "") ?: ""

        val samplesBytes = samplesJson.toByteArray().size.toLong()
        val deltasBytes = deltasJson.toByteArray().size.toLong()
        val metadataBytes = 100L // estimasi keys + values lain

        return StorageUsage(
            learningSamplesBytes = samplesBytes,
            weightDeltasBytes = deltasBytes,
            metadataBytes = metadataBytes,
            totalBytes = samplesBytes + deltasBytes + metadataBytes,
            maxBytes = MAX_STORAGE_BYTES
        )
    }

    /**
     * Hapus semua data AI (reset total).
     */
    fun clearAllAiData() {
        prefs.edit().clear().apply()
    }

    // ════════════════════════════════════════════════
    //  Private cleanup methods
    // ════════════════════════════════════════════════

    private fun cleanupExpiredData() {
        val json = prefs.getString("learning_samples", null) ?: return

        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<LearningSample>>() {}.type
            val samples: List<LearningSample> = gson.fromJson(json, type) ?: return

            val cutoff = System.currentTimeMillis() - SAMPLE_EXPIRY_DAYS * 24 * 3600 * 1000L
            val fresh = samples.filter { it.timestamp > cutoff }

            if (fresh.size < samples.size) {
                prefs.edit()
                    .putString("learning_samples", gson.toJson(fresh))
                    .apply()
            }
        } catch (_: Exception) {
            // Corrupt → clear
            prefs.edit().remove("learning_samples").apply()
        }
    }

    /**
     * Jika masih over-budget, hapus sampel tertua sampai fit.
     */
    private fun trimToFit() {
        val json = prefs.getString("learning_samples", null) ?: return

        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<MutableList<LearningSample>>() {}.type
            val samples: MutableList<LearningSample> = gson.fromJson(json, type) ?: return

            // Hapus sampel tertua sampai ukuran < budget
            while (samples.size > 5) {
                samples.removeFirst()
                val newJson = gson.toJson(samples)
                if (newJson.toByteArray().size < MAX_STORAGE_BYTES - 2000) break
            }

            prefs.edit()
                .putString("learning_samples", gson.toJson(samples))
                .apply()
        } catch (_: Exception) {
            prefs.edit().remove("learning_samples").apply()
        }
    }
}

/**
 * Detail penggunaan storage oleh AI system
 */
data class StorageUsage(
    val learningSamplesBytes: Long,
    val weightDeltasBytes: Long,
    val metadataBytes: Long,
    val totalBytes: Long,
    val maxBytes: Long
) {
    /** Persentase penggunaan storage (0–100) */
    val usagePercent: Int get() = ((totalBytes * 100) / maxBytes).toInt().coerceIn(0, 100)

    /** Format human-readable */
    val formattedTotal: String get() = when {
        totalBytes < 1024 -> "$totalBytes B"
        else -> "${"%.1f".format(totalBytes / 1024.0)} KB"
    }
}
