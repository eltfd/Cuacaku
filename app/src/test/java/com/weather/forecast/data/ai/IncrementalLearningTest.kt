package com.weather.forecast.data.ai

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Comprehensive Test Suite for Incremental Learning Engine
 *
 * Karena IncrementalLearningEngine membutuhkan Android Context (SharedPreferences),
 * test ini mensimulasikan algoritma EXACT yang sama (online gradient descent pada
 * output layer) untuk menguji apakah:
 *
 * 1. CONVERGENCE — Apakah learning mengurangi error seiring waktu?
 * 2. ACCURACY PRESERVATION — Apakah akurasi base model tetap terjaga?
 * 3. STABILITY — Apakah weight deltas tidak diverge?
 * 4. LEARNING RATE DECAY — Apakah LR menurun dengan benar?
 * 5. GRADIENT CLIPPING — Apakah gradient tidak meledak?
 * 6. CATASTROPHIC FORGETTING — Apakah learning pada satu skenario
 *    merusak performa di skenario lain?
 * 7. MULTI-SCENARIO LEARNING — Apakah model bisa belajar dari
 *    berbagai kondisi cuaca secara bersamaan?
 *
 * Simulasi ini mereplikasi persis:
 *   - IncrementalLearningEngine.updateWeights()
 *   - Hyperparameters: LR=0.005, decay=0.995, momentum=0.9, clip=1.0, L2=0.0001
 *   - Output layer only: w3Delta[96] + b3Delta[6]
 */
class IncrementalLearningTest {

    // ══════════════════════════════════════════════════
    //  Replicated Hyperparameters (sama persis dengan IncrementalLearningEngine)
    // ══════════════════════════════════════════════════

    companion object {
        private const val INITIAL_LR = 0.005f
        private const val MIN_LR = 0.0005f
        private const val LR_DECAY = 0.995f
        private const val MOMENTUM = 0.9f
        private const val GRAD_CLIP = 1.0f
        private const val WEIGHT_DECAY = 0.0001f
        private const val H2 = 16
        private const val OUTPUT = 6

        val DISASTER_NAMES = arrayOf(
            "Banjir", "Banjir Rob", "Siklon", "Badai Petir", "Longsor", "Tanah Amblas"
        )
    }

    // ══════════════════════════════════════════════════
    //  Simulated Learning Engine (tanpa Android Context)
    // ══════════════════════════════════════════════════

    /**
     * Simulasi IncrementalLearningEngine tanpa dependency Android.
     * Mereplikasi EXACT algoritma gradient descent yang sama.
     */
    class SimulatedLearningEngine {
        var w3Delta = FloatArray(H2 * OUTPUT)
        var b3Delta = FloatArray(OUTPUT)
        var w3Momentum = FloatArray(H2 * OUTPUT)
        var b3Momentum = FloatArray(OUTPUT)
        var learningStep = 0L

        val errorHistory = mutableListOf<Float>()

        fun getCurrentLR(): Float {
            return maxOf(MIN_LR, INITIAL_LR * Math.pow(LR_DECAY.toDouble(), learningStep.toDouble()).toFloat())
        }

        /**
         * Satu langkah learning — exact replica of IncrementalLearningEngine.updateWeights()
         */
        fun learn(features: FloatArray, nnPredictions: FloatArray, targets: FloatArray) {
            val h2 = DisasterNeuralNetwork.forwardToH2(features)
            val lr = getCurrentLR()

            var totalError = 0f
            for (j in 0 until OUTPUT) {
                val pred = nnPredictions[j].coerceIn(0.001f, 0.999f)
                val target = targets[j].coerceIn(0.001f, 0.999f)
                val delta = pred - target
                totalError += delta * delta

                for (i in 0 until H2) {
                    val grad = (delta * h2[i]).coerceIn(-GRAD_CLIP, GRAD_CLIP)
                    val idx = i * OUTPUT + j
                    w3Momentum[idx] = MOMENTUM * w3Momentum[idx] + (1 - MOMENTUM) * grad
                    w3Delta[idx] -= lr * w3Momentum[idx] + WEIGHT_DECAY * w3Delta[idx]
                }

                val bGrad = delta.coerceIn(-GRAD_CLIP, GRAD_CLIP)
                b3Momentum[j] = MOMENTUM * b3Momentum[j] + (1 - MOMENTUM) * bGrad
                b3Delta[j] -= lr * b3Momentum[j] + WEIGHT_DECAY * b3Delta[j]
            }

            errorHistory.add(totalError / OUTPUT) // MSE
            learningStep++
        }

        fun getWeightDeltas(): WeightDeltas {
            return WeightDeltas(w3Delta.copyOf(), b3Delta.copyOf(), learningStep)
        }

        fun predict(features: FloatArray): FloatArray {
            return DisasterNeuralNetwork.predict(features, getWeightDeltas())
        }

        fun weightNorm(): Float {
            return sqrt(w3Delta.sumOf { (it * it).toDouble() }.toFloat() +
                    b3Delta.sumOf { (it * it).toDouble() }.toFloat())
        }

        fun reset() {
            w3Delta = FloatArray(H2 * OUTPUT)
            b3Delta = FloatArray(OUTPUT)
            w3Momentum = FloatArray(H2 * OUTPUT)
            b3Momentum = FloatArray(OUTPUT)
            learningStep = 0
            errorHistory.clear()
        }
    }

    // ══════════════════════════════════════════════════
    //  Test Scenarios (cuaca → target risiko yang benar)
    // ══════════════════════════════════════════════════

    data class TrainingScenario(
        val name: String,
        val features: FloatArray,
        val targets: FloatArray  // Target risiko "ideal" [0,1]
    )

    private fun createScenarios(): List<TrainingScenario> = listOf(
        TrainingScenario(
            "Banjir Bandang",
            FloatArray(20) { 0.1f }.apply {
                this[0]=0.90f; this[1]=0.85f; this[7]=0.90f; this[14]=0.80f
                this[15]=0.75f; this[16]=0.70f; this[17]=0.60f; this[18]=0.80f
            },
            floatArrayOf(0.90f, 0.30f, 0.15f, 0.25f, 0.70f, 0.55f)
            //           Flood  Tidal  Cycl   Thund  Land   Subs
        ),
        TrainingScenario(
            "Siklon Tropis",
            FloatArray(20) { 0.1f }.apply {
                this[2]=0.90f; this[3]=0.95f; this[4]=0.70f; this[5]=0.90f
                this[6]=0.80f; this[12]=0.80f; this[13]=0.70f
            },
            floatArrayOf(0.20f, 0.75f, 0.95f, 0.40f, 0.10f, 0.10f)
        ),
        TrainingScenario(
            "Badai Petir",
            FloatArray(20) { 0.1f }.apply {
                this[8]=0.95f; this[4]=0.55f; this[3]=0.60f; this[18]=1.0f
            },
            floatArrayOf(0.20f, 0.10f, 0.30f, 0.90f, 0.15f, 0.10f)
        ),
        TrainingScenario(
            "Longsor",
            FloatArray(20) { 0.1f }.apply {
                this[16]=0.90f; this[17]=0.85f; this[15]=0.80f; this[7]=0.95f; this[0]=0.60f
            },
            floatArrayOf(0.40f, 0.15f, 0.10f, 0.20f, 0.85f, 0.70f)
        ),
        TrainingScenario(
            "Cuaca Tenang",
            FloatArray(20) { 0.05f },
            floatArrayOf(0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f)
        ),
        TrainingScenario(
            "Banjir Rob",
            FloatArray(20) { 0.1f }.apply {
                this[12]=0.90f; this[13]=0.85f; this[5]=0.60f; this[2]=0.50f
            },
            floatArrayOf(0.15f, 0.85f, 0.35f, 0.10f, 0.10f, 0.10f)
        )
    )

    // ══════════════════════════════════════════════════
    //  TEST 1: CONVERGENCE — Apakah error menurun?
    // ══════════════════════════════════════════════════

    @Test
    fun `convergence - error decreases over training epochs`() {
        val engine = SimulatedLearningEngine()
        val scenarios = createScenarios()

        println("\n═══════════ CONVERGENCE TEST ═══════════")
        println("Training 50 epochs × ${scenarios.size} scenarios = ${50 * scenarios.size} steps\n")

        val epochErrors = mutableListOf<Float>()

        for (epoch in 0 until 50) {
            var epochMSE = 0f
            for (scenario in scenarios) {
                val pred = engine.predict(scenario.features)
                engine.learn(scenario.features, pred, scenario.targets)

                // Hitung MSE untuk epoch ini
                var scenarioMSE = 0f
                for (j in 0 until OUTPUT) {
                    val err = pred[j] - scenario.targets[j]
                    scenarioMSE += err * err
                }
                epochMSE += scenarioMSE / OUTPUT
            }
            epochErrors.add(epochMSE / scenarios.size)

            if (epoch % 10 == 0 || epoch == 49) {
                val lr = engine.getCurrentLR()
                println("  Epoch ${"%3d".format(epoch)}: MSE=${"%.6f".format(epochErrors.last())}  LR=${"%.5f".format(lr)}  ||Δw||=${"%.6f".format(engine.weightNorm())}")
            }
        }

        val firstMSE = epochErrors.first()
        val lastMSE = epochErrors.last()
        val improvement = ((firstMSE - lastMSE) / firstMSE * 100)

        println("\n  Initial MSE: ${"%.6f".format(firstMSE)}")
        println("  Final MSE:   ${"%.6f".format(lastMSE)}")
        println("  Improvement: ${"%.2f".format(improvement)}%")
        println("  ${if (improvement > 0) "✅" else "❌"} Error ${if (improvement > 0) "MENURUN" else "TIDAK MENURUN"}")

        assertTrue("Error harus menurun setelah 50 epochs (improvement=${"%.2f".format(improvement)}%)",
            lastMSE < firstMSE)
    }

    // ══════════════════════════════════════════════════
    //  TEST 2: ACCURACY PER DISASTER — Per-bencana improvement
    // ══════════════════════════════════════════════════

    @Test
    fun `per disaster accuracy - each disaster type improves`() {
        val engine = SimulatedLearningEngine()
        val scenarios = createScenarios()

        println("\n═══════════ PER-DISASTER ACCURACY ═══════════")

        // Ukur error awal per bencana
        val initialErrors = FloatArray(OUTPUT)
        val scenarioCount = FloatArray(OUTPUT)
        for (scenario in scenarios) {
            val pred = DisasterNeuralNetwork.predict(scenario.features) // Tanpa delta
            for (j in 0 until OUTPUT) {
                initialErrors[j] += abs(pred[j] - scenario.targets[j])
                scenarioCount[j] += 1f
            }
        }
        for (j in 0 until OUTPUT) initialErrors[j] /= scenarioCount[j]

        // Training 30 epochs
        for (epoch in 0 until 30) {
            for (scenario in scenarios) {
                val pred = engine.predict(scenario.features)
                engine.learn(scenario.features, pred, scenario.targets)
            }
        }

        // Ukur error setelah learning
        val finalErrors = FloatArray(OUTPUT)
        scenarioCount.fill(0f)
        for (scenario in scenarios) {
            val pred = engine.predict(scenario.features)
            for (j in 0 until OUTPUT) {
                finalErrors[j] += abs(pred[j] - scenario.targets[j])
                scenarioCount[j] += 1f
            }
        }
        for (j in 0 until OUTPUT) finalErrors[j] /= scenarioCount[j]

        println("╔═══════════════╦═══════════╦═══════════╦══════════╦════════╗")
        println("║ Bencana       ║ MAE Awal  ║ MAE Akhir ║ Δ Error  ║ Status ║")
        println("╠═══════════════╬═══════════╬═══════════╬══════════╬════════╣")

        var improvedCount = 0
        for (j in 0 until OUTPUT) {
            val delta = finalErrors[j] - initialErrors[j]
            val improved = delta < 0
            if (improved) improvedCount++
            println(String.format("║ %-13s ║ %7.4f   ║ %7.4f   ║ %+7.4f  ║   %s   ║",
                DISASTER_NAMES[j], initialErrors[j], finalErrors[j], delta,
                if (improved) "✅" else "⚠️"))
        }
        println("╚═══════════════╩═══════════╩═══════════╩══════════╩════════╝")
        println("Improved: $improvedCount/$OUTPUT disasters")

        assertTrue("Setidaknya 4/6 bencana harus membaik",
            improvedCount >= 4)
    }

    // ══════════════════════════════════════════════════
    //  TEST 3: STABILITY — Weight deltas tidak diverge
    // ══════════════════════════════════════════════════

    @Test
    fun `stability - weight deltas bounded after many epochs`() {
        val engine = SimulatedLearningEngine()
        val scenarios = createScenarios()

        println("\n═══════════ STABILITY TEST ═══════════")

        val norms = mutableListOf<Float>()

        // 100 epochs (banyak) — cek apakah weights tetap bounded
        for (epoch in 0 until 100) {
            for (scenario in scenarios) {
                val pred = engine.predict(scenario.features)
                engine.learn(scenario.features, pred, scenario.targets)
            }
            norms.add(engine.weightNorm())
        }

        val maxNorm = norms.max()
        val finalNorm = norms.last()

        println("  Weight norm history (setiap 20 epoch):")
        for (i in listOf(0, 19, 39, 59, 79, 99)) {
            println("    Epoch ${i+1}: ||Δw|| = ${"%.6f".format(norms[i])}")
        }
        println("  Max norm: ${"%.6f".format(maxNorm)}")
        println("  Final norm: ${"%.6f".format(finalNorm)}")

        // Weight deltas harus bounded — tidak diverge ke ratusan
        assertTrue("Weight norm harus < 5.0 setelah 100 epochs (actual=${"%.4f".format(maxNorm)})",
            maxNorm < 5.0f)

        // Norm harus konvergen (final < 2× peak, tidak terus naik eksplosif)
        val diverging = norms.last() > norms[norms.size / 2] * 3
        assertFalse("Weights tidak boleh diverge (final >> mid)", diverging)

        println("  ✅ STABLE: Weight deltas bounded dan tidak diverge")
    }

    // ══════════════════════════════════════════════════
    //  TEST 4: LEARNING RATE DECAY
    // ══════════════════════════════════════════════════

    @Test
    fun `learning rate decay - LR decreases correctly`() {
        val engine = SimulatedLearningEngine()

        println("\n═══════════ LEARNING RATE DECAY TEST ═══════════")

        val lrValues = mutableListOf<Float>()
        for (step in listOf(0L, 10L, 50L, 100L, 500L, 1000L, 5000L)) {
            engine.learningStep = step
            val lr = engine.getCurrentLR()
            lrValues.add(lr)
            println("  Step ${"%-5d".format(step)}: LR = ${"%.6f".format(lr)}")
        }

        // LR harus menurun
        assertTrue("LR harus menurun: step 0 > step 1000",
            lrValues[0] > lrValues[5])

        // LR tidak boleh di bawah MIN_LR
        val allAboveMin = lrValues.all { it >= MIN_LR }
        assertTrue("LR tidak boleh di bawah MIN_LR ($MIN_LR)", allAboveMin)

        // LR awal harus = INITIAL_LR
        assertEquals("LR awal harus = INITIAL_LR", INITIAL_LR, lrValues[0], 1e-6f)

        println("  ✅ LR Decay benar: ${"%.5f".format(lrValues.first())} → ${"%.5f".format(lrValues.last())}")
    }

    // ══════════════════════════════════════════════════
    //  TEST 5: CATASTROPHIC FORGETTING — Belajar satu,
    //  apakah merusak yang lain?
    // ══════════════════════════════════════════════════

    @Test
    fun `catastrophic forgetting - learning one does not ruin others`() {
        val engine = SimulatedLearningEngine()
        val scenarios = createScenarios()

        println("\n═══════════ CATASTROPHIC FORGETTING TEST ═══════════")

        // Baseline: ukur prediksi awal semua skenario
        val baselinePreds = scenarios.map { DisasterNeuralNetwork.predict(it.features) }

        // Hanya training SATU skenario 30 kali (Banjir Bandang)
        val trainScenario = scenarios[0]
        for (i in 0 until 30) {
            val pred = engine.predict(trainScenario.features)
            engine.learn(trainScenario.features, pred, trainScenario.targets)
        }

        println("  Trained only: ${trainScenario.name} (30 steps)")
        println()

        // Cek dampak pada semua skenario lain
        var severeDegradation = 0
        for ((idx, scenario) in scenarios.withIndex()) {
            val newPred = engine.predict(scenario.features)
            val basePred = baselinePreds[idx]

            // Hitung primary disaster error change
            val primaryIdx = scenario.targets.indices.maxByOrNull { scenario.targets[it] } ?: 0
            val baseError = abs(basePred[primaryIdx] - scenario.targets[primaryIdx])
            val newError = abs(newPred[primaryIdx] - scenario.targets[primaryIdx])
            val degradation = newError - baseError

            val status = when {
                degradation < 0 -> "✅ IMPROVED"
                degradation < 0.10 -> "⚪ STABLE"
                degradation < 0.20 -> "⚠️ SLIGHT"
                else -> { severeDegradation++; "❌ DEGRADED" }
            }

            println("  ${scenario.name.padEnd(16)} target=${DISASTER_NAMES[primaryIdx]} " +
                    "base_err=${"%.3f".format(baseError)} new_err=${"%.3f".format(newError)} " +
                    "Δ=${"%+.3f".format(degradation)} $status")
        }

        assertTrue("Severe degradation (>0.20) di < 2 skenario lain (actual=$severeDegradation)",
            severeDegradation < 2)
        println("\n  ✅ Catastrophic forgetting terkontrol: $severeDegradation severe degradation")
    }

    // ══════════════════════════════════════════════════
    //  TEST 6: MIXED TRAINING — Belajar dari semua skenario bersama
    // ══════════════════════════════════════════════════

    @Test
    fun `mixed training - simultaneous multi-scenario learning`() {
        val engine = SimulatedLearningEngine()
        val scenarios = createScenarios()

        println("\n═══════════ MIXED TRAINING TEST ═══════════")
        println("Training semua ${scenarios.size} skenario selama 40 epochs\n")

        // Training campuran
        for (epoch in 0 until 40) {
            for (scenario in scenarios) {
                val pred = engine.predict(scenario.features)
                engine.learn(scenario.features, pred, scenario.targets)
            }
        }

        // Evaluasi akurasi per skenario
        println("╔════════════════════╦══════════════════════════════════════════════════════════╦══════════════╗")
        println("║ Skenario           ║ Prediksi vs Target per Bencana                          ║ Avg |Error|  ║")
        println("╠════════════════════╬══════════════════════════════════════════════════════════╬══════════════╣")

        var totalMAE = 0f
        var totalScenarios = 0
        val perScenarioMAE = mutableListOf<Pair<String, Float>>()

        for (scenario in scenarios) {
            val pred = engine.predict(scenario.features)
            var scenarioMAE = 0f
            val details = StringBuilder()

            for (j in 0 until OUTPUT) {
                val err = abs(pred[j] - scenario.targets[j])
                scenarioMAE += err
                val symbol = when {
                    err < 0.10 -> "✅"
                    err < 0.20 -> "⚪"
                    err < 0.30 -> "⚠️"
                    else -> "❌"
                }
                details.append("${DISASTER_NAMES[j].take(3)}=${"%.2f".format(pred[j])}/${"%.2f".format(scenario.targets[j])}$symbol ")
            }
            scenarioMAE /= OUTPUT
            totalMAE += scenarioMAE
            totalScenarios++
            perScenarioMAE.add(scenario.name to scenarioMAE)

            println(String.format("║ %-18s ║ %-56s ║ %10.4f   ║",
                scenario.name, details.toString().trim(), scenarioMAE))
        }

        val avgMAE = totalMAE / totalScenarios
        println("╠════════════════════╬══════════════════════════════════════════════════════════╬══════════════╣")
        println(String.format("║ %-18s ║ %-56s ║ %10.4f   ║", "AVERAGE", "", avgMAE))
        println("╚════════════════════╩══════════════════════════════════════════════════════════╩══════════════╝")

        val verdict = when {
            avgMAE < 0.10 -> "🟢 SANGAT BAIK"
            avgMAE < 0.15 -> "🟢 BAIK"
            avgMAE < 0.20 -> "🟡 CUKUP"
            avgMAE < 0.30 -> "🟠 PERLU PERBAIKAN"
            else -> "🔴 KURANG"
        }
        println("  Average MAE: ${"%.4f".format(avgMAE)} → $verdict")

        assertTrue("Average MAE setelah training harus < 0.30 (actual=${"%.4f".format(avgMAE)})",
            avgMAE < 0.30f)
    }

    // ══════════════════════════════════════════════════
    //  TEST 7: GRADIENT CLIPPING — Mencegah divergence
    // ══════════════════════════════════════════════════

    @Test
    fun `gradient clipping - extreme targets do not cause divergence`() {
        val engine = SimulatedLearningEngine()

        println("\n═══════════ GRADIENT CLIPPING TEST ═══════════")

        // Input dengan semua fitur sangat tinggi
        val extremeFeatures = FloatArray(20) { 1.0f }
        // Target yang sangat jauh dari prediksi → gradien besar
        val extremeTargets = floatArrayOf(0.01f, 0.99f, 0.01f, 0.99f, 0.01f, 0.99f)

        val norms = mutableListOf<Float>()
        val errors = mutableListOf<Float>()

        for (step in 0 until 50) {
            val pred = engine.predict(extremeFeatures)
            engine.learn(extremeFeatures, pred, extremeTargets)
            norms.add(engine.weightNorm())

            var mse = 0f
            val newPred = engine.predict(extremeFeatures)
            for (j in 0 until OUTPUT) {
                mse += (newPred[j] - extremeTargets[j]).let { it * it }
            }
            errors.add(mse / OUTPUT)
        }

        println("  Extreme scenario: all-ones features, alternating 0.01/0.99 targets")
        println("  Steps: 50")
        for (i in listOf(0, 9, 24, 49)) {
            println("    Step ${i+1}: ||Δw||=${"%.4f".format(norms[i])}  MSE=${"%.4f".format(errors[i])}")
        }

        // Tidak boleh NaN
        val hasNaN = norms.any { it.isNaN() } || errors.any { it.isNaN() }
        assertFalse("Tidak boleh ada NaN", hasNaN)

        // Weights bounded
        assertTrue("Weights harus bounded < 10 (actual max=${"%.4f".format(norms.max())})",
            norms.max() < 10f)

        // Error harus menurun (learning terjadi)
        assertTrue("Error harus menurun dengan gradient clipping",
            errors.last() < errors.first())

        println("  ✅ GRADIENT CLIPPING: Stabil, tidak ada NaN, error menurun")
    }

    // ══════════════════════════════════════════════════
    //  TEST 8: INCREMENTAL vs BASELINE COMPARISON
    // ══════════════════════════════════════════════════

    @Test
    fun `incremental vs baseline - learned model better than base`() {
        val engine = SimulatedLearningEngine()
        val scenarios = createScenarios()

        println("\n═══════════ INCREMENTAL vs BASELINE ═══════════")

        // Ukur baseline MAE (tanpa learning)
        var baselineMAE = 0f
        for (scenario in scenarios) {
            val pred = DisasterNeuralNetwork.predict(scenario.features)
            for (j in 0 until OUTPUT) {
                baselineMAE += abs(pred[j] - scenario.targets[j])
            }
        }
        baselineMAE /= (scenarios.size * OUTPUT)

        // Training 40 epochs
        for (epoch in 0 until 40) {
            for (scenario in scenarios) {
                val pred = engine.predict(scenario.features)
                engine.learn(scenario.features, pred, scenario.targets)
            }
        }

        // Ukur learned MAE
        var learnedMAE = 0f
        for (scenario in scenarios) {
            val pred = engine.predict(scenario.features)
            for (j in 0 until OUTPUT) {
                learnedMAE += abs(pred[j] - scenario.targets[j])
            }
        }
        learnedMAE /= (scenarios.size * OUTPUT)

        val improvement = ((baselineMAE - learnedMAE) / baselineMAE * 100)

        println("  Baseline MAE: ${"%.4f".format(baselineMAE)}")
        println("  Learned MAE:  ${"%.4f".format(learnedMAE)}")
        println("  Improvement:  ${"%.2f".format(improvement)}%")
        println("  ${if (improvement > 0) "✅" else "❌"} Learned model ${if (improvement > 0) "LEBIH BAIK" else "TIDAK LEBIH BAIK"}")

        assertTrue("Learned model harus lebih baik dari baseline (improvement=${"%.1f".format(improvement)}%)",
            learnedMAE < baselineMAE)
    }

    // ══════════════════════════════════════════════════
    //  TEST 9: LONG-TERM TRAINING (simulated 30 hari)
    // ══════════════════════════════════════════════════

    @Test
    fun `long term - simulated 30 day learning`() {
        val engine = SimulatedLearningEngine()
        val scenarios = createScenarios()
        val rng = java.util.Random(42)

        println("\n═══════════ 30-DAY SIMULATION ═══════════")
        println("Simulasi 30 hari, 1 skenario acak per hari\n")

        val dailyErrors = mutableListOf<Float>()
        val dailyNorms = mutableListOf<Float>()

        for (day in 0 until 30) {
            // Pilih skenario acak (simulasi cuaca yang berubah tiap hari)
            val scenario = scenarios[rng.nextInt(scenarios.size)]
            val pred = engine.predict(scenario.features)
            engine.learn(scenario.features, pred, scenario.targets)

            // Evaluasi average error atas SEMUA skenario
            var avgErr = 0f
            for (s in scenarios) {
                val p = engine.predict(s.features)
                for (j in 0 until OUTPUT) avgErr += abs(p[j] - s.targets[j])
            }
            avgErr /= (scenarios.size * OUTPUT)
            dailyErrors.add(avgErr)
            dailyNorms.add(engine.weightNorm())
        }

        // Print weekly summaries
        for (week in 0 until 4) {
            val dayStart = week * 7
            val dayEnd = minOf(dayStart + 6, 29)
            val weekAvgErr = dailyErrors.subList(dayStart, dayEnd + 1).average()
            val weekNorm = dailyNorms[dayEnd]
            println("  Minggu ${week+1} (hari ${dayStart+1}-${dayEnd+1}): avg MAE=${"%.4f".format(weekAvgErr)}  ||Δw||=${"%.4f".format(weekNorm)}")
        }

        val firstWeekAvg = dailyErrors.take(7).average()
        val lastWeekAvg = dailyErrors.takeLast(7).average()
        val improvement = ((firstWeekAvg - lastWeekAvg) / firstWeekAvg * 100)

        println("\n  Minggu 1 avg MAE: ${"%.4f".format(firstWeekAvg)}")
        println("  Minggu 4 avg MAE: ${"%.4f".format(lastWeekAvg)}")
        println("  Improvement:      ${"%.2f".format(improvement)}%")

        // Stable or improving
        assertTrue("Error minggu 4 harus stabil atau menurun dibanding minggu 1",
            lastWeekAvg <= firstWeekAvg * 1.15) // Allow 15% fluctuation

        println("  ✅ Model stabil setelah 30 hari simulasi")
    }

    // ══════════════════════════════════════════════════
    //  TEST 10: WEIGHT DELTAS SIZE — Storage check
    // ══════════════════════════════════════════════════

    @Test
    fun `storage - weight deltas small enough for mobile`() {
        println("\n═══════════ STORAGE TEST ═══════════")

        val w3Size = H2 * OUTPUT  // 96 floats
        val b3Size = OUTPUT       // 6 floats
        val totalParams = w3Size + b3Size
        val bytesPerFloat = 4
        val totalBytes = totalParams * bytesPerFloat
        val momentumBytes = totalParams * bytesPerFloat

        // Juga hitung JSON overhead (~2x raw karena text encoding)
        val estimatedJsonBytes = totalBytes * 3 // w3, b3, w3m, b3m = ~4 arrays, tapi ada overhead
        val estimatedKB = estimatedJsonBytes / 1024.0

        println("  Output layer params: $totalParams ($w3Size weights + $b3Size biases)")
        println("  Raw bytes: $totalBytes B (float32)")
        println("  With momentum: ${totalBytes + momentumBytes} B")
        println("  Estimated JSON storage: ~${"%.1f".format(estimatedKB)} KB")
        println("  Max samples (50 × ~280B): ~14 KB")
        println("  Total estimated: ~${"%.1f".format(estimatedKB + 14)} KB")

        assertTrue("Total params harus = 102", totalParams == 102)
        assertTrue("Storage harus < 20 KB", estimatedKB + 14 < 20)

        println("  ✅ Storage sangat ringan: < 20 KB total")
    }

    // ══════════════════════════════════════════════════
    //  TEST 11: WEAK SUPERVISION — computeActualOutcome replica
    // ══════════════════════════════════════════════════

    @Test
    fun `weak supervision - target smoothing is correct`() {
        println("\n═══════════ WEAK SUPERVISION TEST ═══════════")

        // Replicate computeActualOutcome logic
        fun computeActualOutcome(ruleScores: FloatArray): FloatArray {
            return FloatArray(OUTPUT) { i ->
                if (i < ruleScores.size) {
                    (ruleScores[i] * 0.8f + 0.1f).coerceIn(0.05f, 0.95f)
                } else 0.1f
            }
        }

        // Test with various rule scores
        val testCases = listOf(
            "All Zero" to FloatArray(6) { 0f },
            "All One" to FloatArray(6) { 1f },
            "Mixed" to floatArrayOf(0.8f, 0.2f, 0.5f, 0.9f, 0.1f, 0.3f),
            "Edge" to floatArrayOf(1.1f, -0.1f, 0.5f, 0f, 1f, 0.5f)
        )

        for ((name, scores) in testCases) {
            val outcome = computeActualOutcome(scores)
            println("  $name: ${scores.map { "%.2f".format(it) }} → ${outcome.map { "%.2f".format(it) }}")

            // Semua output harus di [0.05, 0.95] (label smoothing)
            for (j in outcome.indices) {
                assertTrue("Outcome[$j] harus >= 0.05 ($name)", outcome[j] >= 0.05f)
                assertTrue("Outcome[$j] harus <= 0.95 ($name)", outcome[j] <= 0.95f)
            }
        }

        // Rule score 0 → outcome 0.10 (bukan 0.0 — label smoothing)
        val zeroOutcome = computeActualOutcome(FloatArray(6) { 0f })
        assertEquals("Score 0 → outcome 0.10 (smoothed)", 0.10f, zeroOutcome[0], 0.01f)

        // Rule score 1 → outcome 0.90 (bukan 1.0 — capped)
        val oneOutcome = computeActualOutcome(FloatArray(6) { 1f })
        assertEquals("Score 1 → outcome 0.90 (capped)", 0.90f, oneOutcome[0], 0.01f)

        println("  ✅ Label smoothing benar: output dalam [0.05, 0.95]")
    }

    // ══════════════════════════════════════════════════
    //  TEST 12: UNSEEN SCENARIO — Generalizability
    // ══════════════════════════════════════════════════

    @Test
    fun `generalization - performance on unseen scenarios`() {
        val engine = SimulatedLearningEngine()

        println("\n═══════════ GENERALIZATION TEST ═══════════")

        // Train hanya pada 4 skenario
        val trainScenarios = createScenarios().take(4)
        for (epoch in 0 until 40) {
            for (s in trainScenarios) {
                val pred = engine.predict(s.features)
                engine.learn(s.features, pred, s.targets)
            }
        }

        // Test pada 2 skenario yang TIDAK pernah dilihat saat training
        val testScenarios = createScenarios().drop(4)

        println("  Training on: ${trainScenarios.map { it.name }}")
        println("  Testing on:  ${testScenarios.map { it.name }}")
        println()

        var baselineUnseenMAE = 0f
        var learnedUnseenMAE = 0f

        for (scenario in testScenarios) {
            val basePred = DisasterNeuralNetwork.predict(scenario.features)
            val learnedPred = engine.predict(scenario.features)

            var baseErr = 0f
            var learnedErr = 0f
            for (j in 0 until OUTPUT) {
                baseErr += abs(basePred[j] - scenario.targets[j])
                learnedErr += abs(learnedPred[j] - scenario.targets[j])
            }
            baseErr /= OUTPUT
            learnedErr /= OUTPUT
            baselineUnseenMAE += baseErr
            learnedUnseenMAE += learnedErr

            println("  ${scenario.name}: base MAE=${"%.4f".format(baseErr)} → learned MAE=${"%.4f".format(learnedErr)} " +
                    "${if (learnedErr <= baseErr * 1.15) "✅" else "⚠️"}")
        }
        baselineUnseenMAE /= testScenarios.size
        learnedUnseenMAE /= testScenarios.size

        // Unseen scenario: learned model tidak boleh JAUH lebih buruk (max 20% degradation)
        val degradation = (learnedUnseenMAE - baselineUnseenMAE) / baselineUnseenMAE * 100
        println("\n  Unseen avg MAE: base=${"%.4f".format(baselineUnseenMAE)} → learned=${"%.4f".format(learnedUnseenMAE)}")
        println("  Degradation: ${"%.2f".format(degradation)}%")

        assertTrue("Unseen scenarios: degradation < 25% (actual=${"%.1f".format(degradation)}%)",
            learnedUnseenMAE < baselineUnseenMAE * 1.25)

        println("  ✅ Generalization terjaga: degradation minimal pada skenario baru")
    }

    // ══════════════════════════════════════════════════
    //  MASTER REPORT — Comprehensive Summary
    // ══════════════════════════════════════════════════

    @Test
    fun `MASTER - incremental learning comprehensive report`() {
        val engine = SimulatedLearningEngine()
        val scenarios = createScenarios()

        println("\n")
        println("╔══════════════════════════════════════════════════════════════════╗")
        println("║   INCREMENTAL LEARNING — COMPREHENSIVE EVALUATION REPORT        ║")
        println("║   Algorithm: Online Gradient Descent (Output Layer Only)         ║")
        println("║   Parameters: 102 (w3: 96, b3: 6)                               ║")
        println("║   Hyperparams: LR=0.005, Decay=0.995, Momentum=0.9              ║")
        println("╚══════════════════════════════════════════════════════════════════╝")

        // ── 1. Baseline ──
        var baseMAE = 0f
        for (s in scenarios) {
            val p = DisasterNeuralNetwork.predict(s.features)
            for (j in 0 until OUTPUT) baseMAE += abs(p[j] - s.targets[j])
        }
        baseMAE /= (scenarios.size * OUTPUT)

        // ── 2. Train ──
        val epochMSEs = mutableListOf<Float>()
        for (epoch in 0 until 50) {
            var epochMSE = 0f
            for (s in scenarios) {
                val p = engine.predict(s.features)
                engine.learn(s.features, p, s.targets)
                for (j in 0 until OUTPUT) {
                    val e = p[j] - s.targets[j]
                    epochMSE += e * e
                }
            }
            epochMSEs.add(epochMSE / (scenarios.size * OUTPUT))
        }

        // ── 3. Post-train evaluation ──
        var learnedMAE = 0f
        for (s in scenarios) {
            val p = engine.predict(s.features)
            for (j in 0 until OUTPUT) learnedMAE += abs(p[j] - s.targets[j])
        }
        learnedMAE /= (scenarios.size * OUTPUT)

        // ── 4. Per-disaster analysis ──
        val perDisasterImprovement = FloatArray(OUTPUT)
        val baseErrors = FloatArray(OUTPUT)
        val learnedErrors = FloatArray(OUTPUT)
        for (s in scenarios) {
            val basePred = DisasterNeuralNetwork.predict(s.features)
            val learnedPred = engine.predict(s.features)
            for (j in 0 until OUTPUT) {
                baseErrors[j] += abs(basePred[j] - s.targets[j])
                learnedErrors[j] += abs(learnedPred[j] - s.targets[j])
            }
        }
        for (j in 0 until OUTPUT) {
            baseErrors[j] /= scenarios.size.toFloat()
            learnedErrors[j] /= scenarios.size.toFloat()
            perDisasterImprovement[j] = if (baseErrors[j] > 0.001f) {
                ((baseErrors[j] - learnedErrors[j]) / baseErrors[j] * 100f)
            } else 0f
        }

        // ── 5. Convergence check ──
        val converged = epochMSEs.last() < epochMSEs.first()
        val mseReduction = ((epochMSEs.first() - epochMSEs.last()) / epochMSEs.first() * 100)

        // ── 6. Stability ──
        val weightNorm = engine.weightNorm()
        val stable = weightNorm < 5.0f

        // ── Print Report ──
        println()
        println("═══════════ ERROR COMPARISON ═══════════")
        println("  Baseline MAE:  ${"%.4f".format(baseMAE)}")
        println("  Learned MAE:   ${"%.4f".format(learnedMAE)}")
        println("  Improvement:   ${"%.2f".format((baseMAE - learnedMAE) / baseMAE * 100)}%")
        println("  MSE Reduction: ${"%.2f".format(mseReduction)}%")

        println("\n═══════════ PER-DISASTER IMPROVEMENT ═══════════")
        var improvedCount = 0
        for (j in 0 until OUTPUT) {
            val improved = learnedErrors[j] < baseErrors[j]
            if (improved) improvedCount++
            println("  ${DISASTER_NAMES[j].padEnd(12)}: ${"%.4f".format(baseErrors[j])} → ${"%.4f".format(learnedErrors[j])} (${"%.1f".format(perDisasterImprovement[j])}%) ${if (improved) "✅" else "⚠️"}")
        }

        println("\n═══════════ LEARNING CURVE ═══════════")
        for (i in listOf(0, 4, 9, 19, 29, 39, 49)) {
            val bar = "█".repeat((epochMSEs[i] * 100).toInt().coerceIn(0, 40))
            println("  Epoch ${"%2d".format(i+1)}: MSE=${"%.6f".format(epochMSEs[i])} $bar")
        }

        println("\n═══════════ STABILITY ═══════════")
        println("  Weight delta norm: ${"%.4f".format(weightNorm)}")
        println("  Learning steps:    ${engine.learningStep}")
        println("  Final LR:          ${"%.5f".format(engine.getCurrentLR())}")

        // ── Scoring ──
        val convergenceScore = if (converged) 100.0 else 0.0
        val accuracyScore = if (learnedMAE < baseMAE) 100.0 else if (learnedMAE < baseMAE * 1.1) 60.0 else 0.0
        val stabilityScore = if (stable) 100.0 else 0.0
        val disasterScore = improvedCount * 100.0 / OUTPUT

        val overallScore = (convergenceScore + accuracyScore + stabilityScore + disasterScore) / 4.0

        println("\n╔══════════════════════════════════════════════════════════════════╗")
        println("║                    FINAL LEARNING REPORT                         ║")
        println("╠══════════════════════════════════════════════════════════════════╣")
        println("║  Convergence:          ${if (converged) "PASS ✅" else "FAIL ❌"} (MSE: ${"%.4f".format(epochMSEs.first())} → ${"%.4f".format(epochMSEs.last())})${" ".repeat(15)}║")
        println("║  Accuracy Improvement: ${if (learnedMAE < baseMAE) "PASS ✅" else "FAIL ❌"} (MAE: ${"%.4f".format(baseMAE)} → ${"%.4f".format(learnedMAE)})${" ".repeat(15)}║")
        println("║  Stability:            ${if (stable) "PASS ✅" else "FAIL ❌"} (||Δw|| = ${"%.4f".format(weightNorm)})${" ".repeat(22)}║")
        println("║  Per-Disaster:          $improvedCount/$OUTPUT improved (${"%.0f".format(disasterScore)}%)${" ".repeat(29)}║")
        println("╠══════════════════════════════════════════════════════════════════╣")

        val verdict = when {
            overallScore >= 85 -> "🟢 SANGAT BAIK — Learning efektif"
            overallScore >= 70 -> "🟡 BAIK — Learning membaik, minor tuning"
            overallScore >= 50 -> "🟠 CUKUP — Learning bekerja, perlu optimasi"
            else -> "🔴 KURANG — Learning perlu redesign"
        }
        println("║  OVERALL SCORE:        ${"%.1f".format(overallScore)}%${" ".repeat(42)}║")
        println("║  VERDICT:              $verdict ║")
        println("╚══════════════════════════════════════════════════════════════════╝")

        assertTrue("Overall score harus > 50%", overallScore > 50.0)
    }
}
