package com.weather.forecast.data.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Incremental Learning Engine — On-Device Continuous Learning
 *
 * Mengimplementasikan online learning untuk mengupdate bobot Neural Network
 * secara bertahap berdasarkan data observasi cuaca yang dikumpulkan.
 *
 * ── Metode ──
 * Menggunakan **Online Gradient Descent** yang dimodifikasi:
 * - Simplified backpropagation (output layer only) untuk efisiensi mobile
 * - Exponential Moving Average (EMA) untuk smoothing gradients
 * - Learning rate decay untuk stabilitas jangka panjang
 * - Gradient clipping untuk mencegah divergence
 *
 * ── Referensi ──
 * [1] Sahoo et al. (2018) "Online Deep Learning: Learning Deep Neural Networks
 *     on the Fly" — ICML — Framework online learning untuk NN
 * [2] McMahan et al. (2013) "Ad Click Prediction: a View from the Trenches"
 *     — KDD — Praktik terbaik online learning di production
 * [3] Bottou (2010) "Large-Scale Machine Learning with Stochastic Gradient
 *     Descent" — COMPSTAT — Konvergensi SGD dengan learning rate scheduling
 *
 * ── Prinsip Desain ──
 * - **Ringan**: Hanya update output layer weights (96 + 6 = 102 parameters)
 *   dibanding full backprop 1,222 parameters → hemat ~12× komputasi
 * - **Hemat storage**: Menyimpan maks 50 sampel pembelajaran (≈15 KB)
 * - **Auto-expire**: Data > 30 hari otomatis dihapus
 * - **Robust**: Gradient clipping + weight decay mencegah overfitting
 *
 * ── Data Flow ──
 * 1. User membuka app → prediksi bencana ditampilkan
 * 2. 24 jam kemudian, app mengambil cuaca aktual
 * 3. Bandingkan prediksi vs kenyataan → hitung target label
 * 4. Update output layer weights via simplified gradient descent
 * 5. Simpan weights yang sudah diupdate ke disk
 */
class IncrementalLearningEngine(private val context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ai_learning_data"
        private const val KEY_SAMPLES = "learning_samples"
        private const val KEY_WEIGHT_DELTAS = "weight_deltas"
        private const val KEY_LEARNING_STEP = "learning_step"
        private const val KEY_LAST_CLEANUP = "last_cleanup"

        // ── Hyperparameters ──
        /** Learning rate awal — kecil karena domain-informed weights sudah bagus */
        private const val INITIAL_LR = 0.005f
        /** Minimum learning rate setelah decay */
        private const val MIN_LR = 0.0005f
        /** Decay rate per step */
        private const val LR_DECAY = 0.995f
        /** Momentum / EMA factor */
        private const val MOMENTUM = 0.9f
        /** Gradient clipping threshold */
        private const val GRAD_CLIP = 1.0f
        /** L2 regularization (weight decay) */
        private const val WEIGHT_DECAY = 0.0001f

        /** Maksimum sampel yang disimpan */
        private const val MAX_SAMPLES = 50
        /** Umur sampel dalam hari sebelum dihapus */
        private const val SAMPLE_EXPIRY_DAYS = 30

        // Output layer dimensions
        private const val H2 = 16
        private const val OUTPUT = 6
    }

    // Delta bobot yang sudah diakumulasi dari learning
    // w3_delta[H2 * OUTPUT] + b3_delta[OUTPUT]
    private var w3Delta = FloatArray(H2 * OUTPUT)
    private var b3Delta = FloatArray(OUTPUT)

    // EMA of gradients (momentum)
    private var w3Momentum = FloatArray(H2 * OUTPUT)
    private var b3Momentum = FloatArray(OUTPUT)

    // Total learning steps
    private var learningStep = 0L

    init {
        loadState()
        cleanupExpiredSamples()
    }

    // ════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════

    /**
     * Mendapatkan delta weights untuk diterapkan ke DisasterNeuralNetwork.
     * Delta ini mewakili pelajaran dari data observasi sebelumnya.
     */
    fun getWeightDeltas(): WeightDeltas {
        return WeightDeltas(
            w3Delta = w3Delta.copyOf(),
            b3Delta = b3Delta.copyOf(),
            totalSteps = learningStep
        )
    }

    /**
     * Merekam sampel prediksi untuk pembelajaran di masa depan.
     *
     * Dipanggil saat app membuat prediksi baru. Sampel disimpan dengan
     * timestamp, dan akan dibandingkan dengan observasi aktual nanti.
     *
     * @param features 20 fitur cuaca ternormalisasi
     * @param predictions 6 skor prediksi NN
     * @param ruleScores 6 skor dari rule-based engine
     */
    fun recordPrediction(
        features: FloatArray,
        predictions: FloatArray,
        ruleScores: FloatArray
    ) {
        val sample = LearningSample(
            timestamp = System.currentTimeMillis(),
            features = features.toList(),
            nnPredictions = predictions.toList(),
            ruleScores = ruleScores.toList(),
            actualOutcome = null // Belum diketahui
        )

        val samples = loadSamples().toMutableList()
        samples.add(sample)

        // Batasi jumlah sampel
        while (samples.size > MAX_SAMPLES) {
            samples.removeFirst()
        }

        saveSamples(samples)
    }

    /**
     * Merekam outcome aktual dan melakukan update bobot.
     *
     * Dipanggil saat app mendeteksi apakah prediksi kemarin terbukti benar.
     * Outcome dihitung dari cuaca aktual 24 jam sebelumnya.
     *
     * @param actualOutcome 6 skor bencana aktual [0, 1] berdasarkan observasi
     */
    fun learnFromOutcome(actualOutcome: FloatArray) {
        val samples = loadSamples().toMutableList()
        if (samples.isEmpty()) return

        // Cari sampel terbaru yang belum punya outcome (dalam 48 jam terakhir)
        val cutoff = System.currentTimeMillis() - 48 * 3600 * 1000L
        val targetIdx = samples.indexOfLast {
            it.actualOutcome == null && it.timestamp > cutoff
        }
        if (targetIdx < 0) return

        // Update sample dengan outcome aktual
        val sample = samples[targetIdx]
        samples[targetIdx] = sample.copy(actualOutcome = actualOutcome.toList())
        saveSamples(samples)

        // ═══ Online Gradient Descent (Output Layer Only) ═══
        updateWeights(
            nnPredictions = sample.nnPredictions.toFloatArray(),
            targets = actualOutcome,
            features = sample.features.toFloatArray()
        )
    }

    /**
     * Menghitung target outcome dari observasi cuaca actual.
     *
     * Menggunakan rule-based scoring sebagai "teacher signal":
     * Cuaca aktual → rule-based score → dijadikan target untuk NN.
     * Ini adalah bentuk **knowledge distillation** ringan dimana rule-based
     * system bertindak sebagai supervisi lemah (weak supervision).
     */
    fun computeActualOutcome(ruleBasedTodayScores: FloatArray): FloatArray {
        // Smooth the rule scores — hindari target 0 atau 1 absolut
        return FloatArray(OUTPUT) { i ->
            if (i < ruleBasedTodayScores.size) {
                (ruleBasedTodayScores[i] * 0.8f + 0.1f).coerceIn(0.05f, 0.95f)
            } else 0.1f
        }
    }

    /**
     * Mengembalikan statistik kondisi learning engine.
     */
    fun getStats(): LearningStats {
        val samples = loadSamples()
        val withOutcome = samples.count { it.actualOutcome != null }
        return LearningStats(
            totalSamples = samples.size,
            samplesWithOutcome = withOutcome,
            totalLearningSteps = learningStep,
            currentLearningRate = getCurrentLR(),
            weightDeltaNorm = computeNorm(w3Delta) + computeNorm(b3Delta),
            lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0)
        )
    }

    /**
     * Reset semua state learning (kembali ke bobot awal domain-informed).
     */
    fun reset() {
        w3Delta = FloatArray(H2 * OUTPUT)
        b3Delta = FloatArray(OUTPUT)
        w3Momentum = FloatArray(H2 * OUTPUT)
        b3Momentum = FloatArray(OUTPUT)
        learningStep = 0
        prefs.edit()
            .remove(KEY_SAMPLES)
            .remove(KEY_WEIGHT_DELTAS)
            .putLong(KEY_LEARNING_STEP, 0)
            .apply()
    }

    // ════════════════════════════════════════════════
    //  Weight Update (Simplified Backprop)
    // ════════════════════════════════════════════════

    /**
     * Update output layer weights berdasarkan prediction error.
     *
     * Hanya output layer (w3, b3) yang diupdate karena:
     * 1. Hidden layers sudah diinisialisasi dengan domain knowledge yang kuat
     * 2. Update seluruh network berisiko catastrophic forgetting
     * 3. Hemat komputasi: 102 vs 1,222 parameter
     *
     * Metode: δ = prediction - target (MSE gradient),
     *         lalu gradient descent dengan momentum dan clipping.
     */
    private fun updateWeights(
        nnPredictions: FloatArray,
        targets: FloatArray,
        features: FloatArray
    ) {
        // Kita perlu h2 (output hidden layer 2) untuk update w3
        // Karena kita hanya menyimpan input features, kita lakukan forward pass sampai h2
        val h2 = DisasterNeuralNetwork.forwardToH2(features)

        val lr = getCurrentLR()

        // Hitung error per output neuron
        for (j in 0 until OUTPUT) {
            val pred = nnPredictions[j].coerceIn(0.001f, 0.999f)
            val target = targets[j].coerceIn(0.001f, 0.999f)

            // Gradient: d(BCE)/d(z) = pred - target (for sigmoid output)
            val delta = pred - target

            // Update w3 weights
            for (i in 0 until H2) {
                val grad = (delta * h2[i]).clip()

                // Momentum update
                val idx = i * OUTPUT + j
                w3Momentum[idx] = MOMENTUM * w3Momentum[idx] + (1 - MOMENTUM) * grad
                w3Delta[idx] -= lr * w3Momentum[idx] + WEIGHT_DECAY * w3Delta[idx]
            }

            // Update b3 bias
            val bGrad = delta.clip()
            b3Momentum[j] = MOMENTUM * b3Momentum[j] + (1 - MOMENTUM) * bGrad
            b3Delta[j] -= lr * b3Momentum[j] + WEIGHT_DECAY * b3Delta[j]
        }

        learningStep++
        saveState()
    }

    private fun getCurrentLR(): Float {
        return maxOf(MIN_LR, INITIAL_LR * Math.pow(LR_DECAY.toDouble(), learningStep.toDouble()).toFloat())
    }

    private fun Float.clip(): Float = this.coerceIn(-GRAD_CLIP, GRAD_CLIP)

    private fun computeNorm(arr: FloatArray): Float {
        return sqrt(arr.sumOf { (it * it).toDouble() }).toFloat()
    }

    // ════════════════════════════════════════════════
    //  Persistence (Lightweight)
    // ════════════════════════════════════════════════

    private fun saveState() {
        val deltaData = mapOf(
            "w3" to w3Delta.toList(),
            "b3" to b3Delta.toList(),
            "w3m" to w3Momentum.toList(),
            "b3m" to b3Momentum.toList()
        )
        prefs.edit()
            .putString(KEY_WEIGHT_DELTAS, gson.toJson(deltaData))
            .putLong(KEY_LEARNING_STEP, learningStep)
            .apply()
    }

    private fun loadState() {
        learningStep = prefs.getLong(KEY_LEARNING_STEP, 0)
        val json = prefs.getString(KEY_WEIGHT_DELTAS, null) ?: return

        try {
            val type = object : TypeToken<Map<String, List<Float>>>() {}.type
            val data: Map<String, List<Float>> = gson.fromJson(json, type)

            data["w3"]?.forEachIndexed { i, v -> if (i < w3Delta.size) w3Delta[i] = v }
            data["b3"]?.forEachIndexed { i, v -> if (i < b3Delta.size) b3Delta[i] = v }
            data["w3m"]?.forEachIndexed { i, v -> if (i < w3Momentum.size) w3Momentum[i] = v }
            data["b3m"]?.forEachIndexed { i, v -> if (i < b3Momentum.size) b3Momentum[i] = v }
        } catch (_: Exception) {
            // Corrupt data — start fresh
        }
    }

    private fun loadSamples(): List<LearningSample> {
        val json = prefs.getString(KEY_SAMPLES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LearningSample>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSamples(samples: List<LearningSample>) {
        prefs.edit()
            .putString(KEY_SAMPLES, gson.toJson(samples))
            .apply()
    }

    /**
     * Membersihkan sampel yang sudah kadaluarsa (> 30 hari).
     */
    private fun cleanupExpiredSamples() {
        val now = System.currentTimeMillis()
        val lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0)

        // Hanya cleanup maksimal 1x per hari
        if (now - lastCleanup < 24 * 3600 * 1000L) return

        val samples = loadSamples()
        val cutoff = now - SAMPLE_EXPIRY_DAYS * 24 * 3600 * 1000L
        val cleaned = samples.filter { it.timestamp > cutoff }

        if (cleaned.size < samples.size) {
            saveSamples(cleaned)
        }

        prefs.edit().putLong(KEY_LAST_CLEANUP, now).apply()
    }
}

// ════════════════════════════════════════════════
//  Data Classes
// ════════════════════════════════════════════════

/**
 * Satu sampel pembelajaran.
 *
 * Ukuran per sampel: ≈ 280 bytes JSON
 * → 50 sampel = ≈ 14 KB (sangat ringan)
 */
data class LearningSample(
    val timestamp: Long,
    /** 20 fitur input ternormalisasi */
    val features: List<Float>,
    /** 6 prediksi NN */
    val nnPredictions: List<Float>,
    /** 6 skor rule-based */
    val ruleScores: List<Float>,
    /** 6 outcome aktual (null jika belum diketahui) */
    val actualOutcome: List<Float>?
)

/**
 * Delta bobot dari incremental learning
 */
data class WeightDeltas(
    /** Delta untuk w3 (H2 × OUTPUT = 96 floats) */
    val w3Delta: FloatArray,
    /** Delta untuk b3 (OUTPUT = 6 floats) */
    val b3Delta: FloatArray,
    val totalSteps: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeightDeltas) return false
        return w3Delta.contentEquals(other.w3Delta) && b3Delta.contentEquals(other.b3Delta)
    }
    override fun hashCode() = w3Delta.contentHashCode() + b3Delta.contentHashCode()
}

/**
 * Statistik learning engine
 */
data class LearningStats(
    val totalSamples: Int,
    val samplesWithOutcome: Int,
    val totalLearningSteps: Long,
    val currentLearningRate: Float,
    val weightDeltaNorm: Float,
    val lastCleanup: Long
)
