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
 * Menggunakan **Online Gradient Descent** yang dimodifikasi dengan 3 mekanisme
 * anti-catastrophic forgetting:
 *
 * 1. **Elastic Weight Consolidation (EWC)** — Melindungi bobot penting
 *    Fisher Information Matrix mengukur "pentingnya" setiap bobot.
 *    Bobot yang penting untuk task lama akan di-penalize jika diubah terlalu besar.
 *    [Kirkpatrick et al. 2017, "Overcoming catastrophic forgetting in NNs" — PNAS]
 *
 * 2. **Experience Replay Buffer** — Melatih ulang dari sampel lama
 *    Saat learning dari data baru, sebagian sampel lama juga di-replay
 *    sehingga model tidak melupakan pola yang sudah dipelajari.
 *    [Rolnick et al. 2019, "Experience Replay for Continual Learning" — NeurIPS]
 *
 * 3. **Concept Drift Detection** — Mendeteksi perubahan pola cuaca
 *    Exponential Moving Average (EMA) dari error rate dipantau terus.
 *    Jika error naik signifikan → drift terdeteksi → LR dinaikkan sementara
 *    agar model cepat beradaptasi, lalu Fisher Matrix di-reset.
 *    [Gama et al. 2004, "Learning with Drift Detection" — SBIA]
 *
 * ── Referensi Tambahan ──
 * [4] Sahoo et al. (2018) "Online Deep Learning" — ICML
 * [5] McMahan et al. (2013) "Ad Click Prediction" — KDD
 * [6] Bottou (2010) "Large-Scale Machine Learning with SGD" — COMPSTAT
 *
 * ── Prinsip Desain ──
 * - **Ringan**: Hanya update output layer weights (96 + 6 = 102 parameters)
 * - **Hemat storage**: Menyimpan maks 50 sampel + Fisher (~1 KB) ≈ 17 KB total
 * - **Auto-expire**: Data > 30 hari otomatis dihapus
 * - **Robust**: EWC + Replay + Drift Detection + Gradient Clipping
 * - **Adaptif**: Drift detection otomatis menyesuaikan learning rate
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
        private const val KEY_FISHER = "fisher_information"
        private const val KEY_DRIFT_STATE = "drift_state"

        // ── Core Hyperparameters ──
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

        // ── EWC Hyperparameters ──
        /** EWC penalty strength — seberapa kuat melindungi bobot lama */
        private const val EWC_LAMBDA = 0.4f
        /** Fisher decay — Fisher Info di-decay setiap step agar tidak terlalu rigid */
        private const val FISHER_DECAY = 0.99f
        /** Fisher update interval — update Fisher setiap N step untuk hemat CPU */
        private const val FISHER_UPDATE_INTERVAL = 5

        // ── Replay Buffer Hyperparameters ──
        /** Jumlah sampel lama yang di-replay setiap learning step */
        private const val REPLAY_COUNT = 3
        /** Bobot replay relatif terhadap sampel baru (0.3 = 30% kekuatan) */
        private const val REPLAY_WEIGHT = 0.3f

        // ── Drift Detection Hyperparameters ──
        /** EMA smoothing factor untuk error tracking */
        private const val DRIFT_EMA_ALPHA = 0.15f
        /** Threshold: jika error naik > X × std_dev, drift terdeteksi */
        private const val DRIFT_THRESHOLD = 2.0f
        /** LR boost saat drift (sementara, agar model cepat adaptasi) */
        private const val DRIFT_LR_BOOST = 3.0f
        /** Cooldown steps setelah drift terdeteksi */
        private const val DRIFT_COOLDOWN_STEPS = 10

        /** Maksimum sampel yang disimpan */
        private const val MAX_SAMPLES = 50
        /** Umur sampel dalam hari sebelum dihapus */
        private const val SAMPLE_EXPIRY_DAYS = 30

        // Output layer dimensions
        private const val H2 = 16
        private const val OUTPUT = 6
    }

    // ── Core State ──
    private var w3Delta = FloatArray(H2 * OUTPUT)
    private var b3Delta = FloatArray(OUTPUT)
    private var w3Momentum = FloatArray(H2 * OUTPUT)
    private var b3Momentum = FloatArray(OUTPUT)
    private var learningStep = 0L

    // ── EWC State ──
    // Fisher Information Matrix diagonal approximation (per parameter)
    // Mengukur seberapa "penting" setiap bobot untuk task-task sebelumnya
    private var fisherW3 = FloatArray(H2 * OUTPUT)
    private var fisherB3 = FloatArray(OUTPUT)
    // "Anchor" weights — titik referensi optimal dari task sebelumnya
    private var anchorW3 = FloatArray(H2 * OUTPUT)
    private var anchorB3 = FloatArray(OUTPUT)

    // ── Drift Detection State ──
    private var driftErrorEma = 0f           // EMA error saat ini
    private var driftErrorVariance = 0f      // Variance of error (untuk threshold)
    private var driftStepsSinceDetected = Int.MAX_VALUE  // Cooldown counter
    private var driftDetectedCount = 0       // Total drift events
    private var driftInitialized = false     // Apakah EMA sudah di-init

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
    @Synchronized
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
     * Sampel ini juga menjadi kandidat untuk Experience Replay:
     * sampel dengan outcome akan di-replay saat learning dari data baru.
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
            actualOutcome = null
        )

        val samples = loadSamples().toMutableList()
        samples.add(sample)

        while (samples.size > MAX_SAMPLES) {
            samples.removeFirst()
        }

        saveSamples(samples)
    }

    /**
     * Merekam outcome aktual dan melakukan update bobot.
     *
     * Flow lengkap:
     * 1. Cari sampel yang cocok → update dengan outcome
     * 2. Drift Detection: cek apakah error naik signifikan
     * 3. Update weights dari sampel baru (gradient descent + EWC penalty)
     * 4. Experience Replay: replay N sampel lama dengan bobot lebih kecil
     * 5. Update Fisher Information Matrix (periodik)
     */
    fun learnFromOutcome(actualOutcome: FloatArray) {
        val samples = loadSamples().toMutableList()
        if (samples.isEmpty()) return

        // Cari sampel terbaru yang belum punya outcome
        val cutoff = System.currentTimeMillis() - 48 * 3600 * 1000L
        val targetIdx = samples.indexOfLast {
            it.actualOutcome == null && it.timestamp > cutoff
        }
        if (targetIdx < 0) return

        // Update sample dengan outcome aktual
        val sample = samples[targetIdx]
        samples[targetIdx] = sample.copy(actualOutcome = actualOutcome.toList())
        saveSamples(samples)

        // ══ Step 1: Drift Detection ══
        val currentError = computeSampleError(sample.nnPredictions.toFloatArray(), actualOutcome)
        updateDriftDetection(currentError)

        // ══ Step 2: Update dari sampel baru (full weight) ══
        updateWeights(
            nnPredictions = sample.nnPredictions.toFloatArray(),
            targets = actualOutcome,
            features = sample.features.toFloatArray(),
            replayWeight = 1.0f  // Full weight untuk sampel baru
        )

        // ══ Step 3: Experience Replay ══
        replayOldSamples(samples, excludeIdx = targetIdx)

        // ══ Step 4: Update Fisher (periodik) ══
        if (learningStep % FISHER_UPDATE_INTERVAL == 0L) {
            updateFisherInformation(samples)
        }
    }

    /**
     * Menghitung target outcome dari observasi cuaca actual.
     * Weak supervision: rule-based → label smoothing → target.
     */
    fun computeActualOutcome(ruleBasedTodayScores: FloatArray): FloatArray {
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
            currentLearningRate = getEffectiveLR(),
            weightDeltaNorm = computeNorm(w3Delta) + computeNorm(b3Delta),
            lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0),
            driftDetectedCount = driftDetectedCount,
            isDrifting = isDrifting(),
            fisherNorm = computeNorm(fisherW3) + computeNorm(fisherB3)
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
        fisherW3 = FloatArray(H2 * OUTPUT)
        fisherB3 = FloatArray(OUTPUT)
        anchorW3 = FloatArray(H2 * OUTPUT)
        anchorB3 = FloatArray(OUTPUT)
        driftErrorEma = 0f
        driftErrorVariance = 0f
        driftStepsSinceDetected = Int.MAX_VALUE
        driftDetectedCount = 0
        driftInitialized = false
        learningStep = 0
        prefs.edit()
            .remove(KEY_SAMPLES)
            .remove(KEY_WEIGHT_DELTAS)
            .remove(KEY_FISHER)
            .remove(KEY_DRIFT_STATE)
            .putLong(KEY_LEARNING_STEP, 0)
            .apply()
    }

    // ════════════════════════════════════════════════
    //  Weight Update (SGD + EWC Penalty)
    // ════════════════════════════════════════════════

    /**
     * Update output layer weights berdasarkan prediction error.
     *
     * Gradient descent + EWC penalty:
     *   total_grad = task_grad + λ × Fisher × (w - w_anchor)
     *
     * EWC penalty memastikan bobot yang penting untuk task lama
     * tidak berubah terlalu drastis saat belajar dari data baru.
     *
     * @param replayWeight Bobot relatif dari update ini (1.0 = sampel baru, 0.3 = replay)
     */
    @Synchronized
    private fun updateWeights(
        nnPredictions: FloatArray,
        targets: FloatArray,
        features: FloatArray,
        replayWeight: Float = 1.0f
    ) {
        val h2 = DisasterNeuralNetwork.forwardToH2(features)
        val lr = getEffectiveLR() * replayWeight

        for (j in 0 until OUTPUT) {
            val pred = nnPredictions[j].coerceIn(0.001f, 0.999f)
            val target = targets[j].coerceIn(0.001f, 0.999f)
            val delta = pred - target

            // Update w3 weights
            for (i in 0 until H2) {
                val idx = i * OUTPUT + j

                // Task gradient
                val taskGrad = (delta * h2[i]).clip()

                // EWC penalty: λ × F_i × (θ_i - θ*_i), clipped to prevent freezing
                val ewcPenalty = (EWC_LAMBDA * fisherW3[idx] * (w3Delta[idx] - anchorW3[idx])).clip()

                // Total gradient = task + EWC + L2
                val totalGrad = taskGrad + ewcPenalty

                // Momentum update
                w3Momentum[idx] = MOMENTUM * w3Momentum[idx] + (1 - MOMENTUM) * totalGrad
                w3Delta[idx] -= lr * w3Momentum[idx] + WEIGHT_DECAY * w3Delta[idx]
            }

            // Update b3 bias
            val bGrad = delta.clip()
            val ewcBiasPenalty = (EWC_LAMBDA * fisherB3[j] * (b3Delta[j] - anchorB3[j])).clip()
            val totalBGrad = bGrad + ewcBiasPenalty

            b3Momentum[j] = MOMENTUM * b3Momentum[j] + (1 - MOMENTUM) * totalBGrad
            b3Delta[j] -= lr * b3Momentum[j] + WEIGHT_DECAY * b3Delta[j]
        }

        learningStep++
        if (driftStepsSinceDetected < Int.MAX_VALUE) {
            driftStepsSinceDetected++
        }

        saveState()
    }

    // ════════════════════════════════════════════════
    //  Experience Replay Buffer
    // ════════════════════════════════════════════════

    /**
     * Replay sampel lama yang sudah punya outcome.
     *
     * Strategi pemilihan: Reservoir Sampling dengan prioritas pada
     * sampel yang beragam (berbeda jenis bencana dominan).
     * Replay menggunakan bobot lebih rendah (REPLAY_WEIGHT) agar
     * tidak mendominasi learning dari sampel baru.
     */
    private fun replayOldSamples(
        allSamples: List<LearningSample>,
        excludeIdx: Int
    ) {
        // Filter: hanya sampel yang sudah punya outcome
        val replayCandidates = allSamples
            .filterIndexed { idx, s -> idx != excludeIdx && s.actualOutcome != null }

        if (replayCandidates.isEmpty()) return

        // Pilih sampel untuk replay — diversifikasi berdasarkan dominant disaster
        val selected = selectDiverseReplaySamples(replayCandidates)

        for (sample in selected) {
            // Re-predict menggunakan current weights untuk mendapatkan predictions terkini
            val currentPredictions = DisasterNeuralNetwork.predict(
                sample.features.toFloatArray(),
                getWeightDeltas()
            )

            updateWeights(
                nnPredictions = currentPredictions,
                targets = sample.actualOutcome!!.toFloatArray(),
                features = sample.features.toFloatArray(),
                replayWeight = REPLAY_WEIGHT
            )
        }
    }

    /**
     * Pilih sampel replay yang beragam.
     * Mengelompokkan sampel berdasarkan bencana dominan, lalu sampling
     * dari tiap kelompok secara bergantian (round-robin).
     */
    private fun selectDiverseReplaySamples(
        candidates: List<LearningSample>
    ): List<LearningSample> {
        if (candidates.size <= REPLAY_COUNT) return candidates

        // Kelompokkan berdasarkan dominant disaster type
        val grouped = candidates.groupBy { sample ->
            sample.actualOutcome?.indices?.maxByOrNull { i ->
                sample.actualOutcome[i]
            } ?: 0
        }

        // Round-robin dari setiap kelompok
        val selected = mutableListOf<LearningSample>()
        val iterators = grouped.values.map { it.shuffled().iterator() }.toMutableList()

        while (selected.size < REPLAY_COUNT && iterators.isNotEmpty()) {
            val toRemove = mutableListOf<Iterator<LearningSample>>()
            for (iter in iterators) {
                if (selected.size >= REPLAY_COUNT) break
                if (iter.hasNext()) {
                    selected.add(iter.next())
                } else {
                    toRemove.add(iter)
                }
            }
            iterators.removeAll(toRemove)
        }

        return selected
    }

    // ════════════════════════════════════════════════
    //  Elastic Weight Consolidation (EWC)
    // ════════════════════════════════════════════════

    /**
     * Update Fisher Information Matrix (diagonal approximation).
     *
     * Fisher I_i ≈ E[(∂L/∂θ_i)²] — rata-rata kuadrat gradient per bobot.
     * Semakin besar Fisher, semakin penting bobot tersebut untuk task yang
     * sudah dipelajari → EWC penalty akan lebih besar jika bobot itu diubah.
     *
     * Implementasi efisien:
     * - Hanya diagonal (102 values, bukan 102×102 matrix)
     * - Computed dari sampel dengan outcome (bukan semua)
     * - Di-decay setiap update agar tidak terlalu rigid (plastisitas terjaga)
     */
    private fun updateFisherInformation(allSamples: List<LearningSample>) {
        val samplesWithOutcome = allSamples.filter { it.actualOutcome != null }
        if (samplesWithOutcome.isEmpty()) return

        // Decay existing Fisher (agar model tetap plastis)
        scaleFisher(FISHER_DECAY)

        // Akumulasi gradient² dari sampel dengan outcome
        val n = samplesWithOutcome.size.toFloat()
        for (sample in samplesWithOutcome) {
            val features = sample.features.toFloatArray()
            val targets = sample.actualOutcome!!.toFloatArray()
            val h2 = DisasterNeuralNetwork.forwardToH2(features)
            val predictions = DisasterNeuralNetwork.predict(features, getWeightDeltas())

            for (j in 0 until OUTPUT) {
                val pred = predictions[j].coerceIn(0.001f, 0.999f)
                val target = targets[j].coerceIn(0.001f, 0.999f)
                val delta = pred - target

                for (i in 0 until H2) {
                    val idx = i * OUTPUT + j
                    val grad = delta * h2[i]
                    // Fisher ≈ E[grad²] — akumulasi rata-rata kuadrat gradient
                    fisherW3[idx] += (grad * grad) / n
                }

                fisherB3[j] += (delta * delta) / n
            }
        }

        // Update anchor point (titik optimal saat ini)
        anchorW3 = w3Delta.copyOf()
        anchorB3 = b3Delta.copyOf()

        saveFisher()
    }

    // ════════════════════════════════════════════════
    //  Concept Drift Detection
    // ════════════════════════════════════════════════

    /**
     * Deteksi perubahan distribusi data (concept drift).
     *
     * Metode: Monitoring EMA dari prediction error.
     * Jika error naik > DRIFT_THRESHOLD × std_dev → DRIFT detected.
     *
     * Respon saat drift:
     * 1. LR dinaikkan sementara (DRIFT_LR_BOOST) agar model cepat adaptasi
     * 2. Fisher di-reset (pengetahuan lama mungkin sudah tidak relevan)
     * 3. Setelah DRIFT_COOLDOWN_STEPS, kembali ke mode normal
     *
     * Contoh: Perubahan musim (kemarau → hujan) mengubah pola cuaca.
     * Model perlu beradaptasi lebih cepat tanpa terlalu terikat pola lama.
     */
    private fun updateDriftDetection(currentError: Float) {
        if (!driftInitialized) {
            driftErrorEma = currentError
            driftErrorVariance = 0.01f  // Initial variance
            driftInitialized = true
            return
        }

        // Update EMA dan variance
        val prevEma = driftErrorEma
        driftErrorEma = DRIFT_EMA_ALPHA * currentError + (1 - DRIFT_EMA_ALPHA) * driftErrorEma
        val diff = currentError - prevEma
        driftErrorVariance = DRIFT_EMA_ALPHA * (diff * diff) +
                (1 - DRIFT_EMA_ALPHA) * driftErrorVariance

        // Deteksi drift: error jauh di atas rata-rata (use prevEma for unbiased check)
        val stdDev = sqrt(driftErrorVariance.toDouble()).toFloat().coerceAtLeast(0.01f)
        val deviation = (currentError - prevEma) / stdDev

        if (deviation > DRIFT_THRESHOLD && driftStepsSinceDetected > DRIFT_COOLDOWN_STEPS) {
            // DRIFT DETECTED!
            onDriftDetected()
        }

        saveDriftState()
    }

    /**
     * Handler saat drift terdeteksi.
     * Reset Fisher agar model tidak terlalu terikat pada pola lama,
     * dan boost LR sementara agar bisa beradaptasi cepat.
     */
    private fun onDriftDetected() {
        driftDetectedCount++
        driftStepsSinceDetected = 0

        // Soft-reset Fisher: kurangi 50% agar model lebih plastis
        // Tidak full reset karena masih ada pengetahuan yang berguna
        scaleFisher(0.5f)

        saveFisher()
    }

    /**
     * Apakah model sedang dalam mode adaptasi drift.
     */
    private fun isDrifting(): Boolean {
        return driftStepsSinceDetected < DRIFT_COOLDOWN_STEPS
    }

    /**
     * Learning rate efektif — mempertimbangkan drift boost.
     */
    private fun getEffectiveLR(): Float {
        val baseLR = getCurrentLR()
        return if (isDrifting()) {
            // Boost LR sementara, decay seiring cooldown
            val boostFactor = DRIFT_LR_BOOST *
                    (1f - driftStepsSinceDetected.toFloat() / DRIFT_COOLDOWN_STEPS)
            (baseLR * (1f + boostFactor)).coerceAtMost(INITIAL_LR * DRIFT_LR_BOOST)
        } else {
            baseLR
        }
    }

    // ════════════════════════════════════════════════
    //  Utilities
    // ════════════════════════════════════════════════

    private fun getCurrentLR(): Float {
        return maxOf(MIN_LR, INITIAL_LR * Math.pow(LR_DECAY.toDouble(), learningStep.toDouble()).toFloat())
    }

    private fun Float.clip(): Float = this.coerceIn(-GRAD_CLIP, GRAD_CLIP)

    private fun computeNorm(arr: FloatArray): Float {
        return sqrt(arr.sumOf { (it * it).toDouble() }).toFloat()
    }

    /**
     * Hitung MSE antara prediksi dan target untuk drift detection.
     */
    private fun computeSampleError(predictions: FloatArray, targets: FloatArray): Float {
        var mse = 0f
        for (j in 0 until minOf(predictions.size, targets.size, OUTPUT)) {
            val diff = predictions[j] - targets[j]
            mse += diff * diff
        }
        return mse / OUTPUT
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

    private fun saveFisher() {
        val fisherData = mapOf(
            "fw3" to fisherW3.toList(),
            "fb3" to fisherB3.toList(),
            "aw3" to anchorW3.toList(),
            "ab3" to anchorB3.toList()
        )
        prefs.edit()
            .putString(KEY_FISHER, gson.toJson(fisherData))
            .apply()
    }

    private fun saveDriftState() {
        val driftData = mapOf(
            "ema" to driftErrorEma,
            "var" to driftErrorVariance,
            "steps" to driftStepsSinceDetected.toFloat(),
            "count" to driftDetectedCount.toFloat(),
            "init" to if (driftInitialized) 1f else 0f
        )
        prefs.edit()
            .putString(KEY_DRIFT_STATE, gson.toJson(driftData))
            .apply()
    }

    /** Load a JSON map from prefs and populate float arrays */
    private fun Map<String, List<Float>>.loadInto(key: String, target: FloatArray) {
        this[key]?.forEachIndexed { i, v -> if (i < target.size) target[i] = v }
    }

    private fun scaleFisher(factor: Float) {
        for (i in fisherW3.indices) fisherW3[i] *= factor
        for (i in fisherB3.indices) fisherB3[i] *= factor
    }

    private inline fun <reified T> loadPrefsJson(key: String): T? {
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson<T>(json, object : TypeToken<T>() {}.type)
        } catch (_: Exception) { null }
    }

    private fun loadState() {
        learningStep = prefs.getLong(KEY_LEARNING_STEP, 0)

        // Load weight deltas + momentum
        loadPrefsJson<Map<String, List<Float>>>(KEY_WEIGHT_DELTAS)?.let { data ->
            data.loadInto("w3", w3Delta)
            data.loadInto("b3", b3Delta)
            data.loadInto("w3m", w3Momentum)
            data.loadInto("b3m", b3Momentum)
        }

        // Load Fisher Information + Anchors
        loadPrefsJson<Map<String, List<Float>>>(KEY_FISHER)?.let { data ->
            data.loadInto("fw3", fisherW3)
            data.loadInto("fb3", fisherB3)
            data.loadInto("aw3", anchorW3)
            data.loadInto("ab3", anchorB3)
        }

        // Load Drift State
        loadPrefsJson<Map<String, Float>>(KEY_DRIFT_STATE)?.let { data ->
            driftErrorEma = data["ema"] ?: 0f
            driftErrorVariance = data["var"] ?: 0f
            driftStepsSinceDetected = (data["steps"] ?: Int.MAX_VALUE.toFloat()).toInt()
            driftDetectedCount = (data["count"] ?: 0f).toInt()
            driftInitialized = (data["init"] ?: 0f) > 0.5f
        }
    }

    private fun loadSamples(): List<LearningSample> =
        loadPrefsJson<List<LearningSample>>(KEY_SAMPLES) ?: emptyList()

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

data class LearningSample(
    val timestamp: Long,
    val features: List<Float>,
    val nnPredictions: List<Float>,
    val ruleScores: List<Float>,
    val actualOutcome: List<Float>?
)

data class WeightDeltas(
    val w3Delta: FloatArray,
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

data class LearningStats(
    val totalSamples: Int,
    val samplesWithOutcome: Int,
    val totalLearningSteps: Long,
    val currentLearningRate: Float,
    val weightDeltaNorm: Float,
    val lastCleanup: Long,
    val driftDetectedCount: Int = 0,
    val isDrifting: Boolean = false,
    val fisherNorm: Float = 0f
)
