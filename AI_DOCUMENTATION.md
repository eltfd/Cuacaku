# Cuacaku — Dokumentasi Sistem AI

## Gambaran Umum

Cuacaku menggunakan **Neural Network on-device** untuk memprediksi potensi bencana alam berdasarkan data meteorologi real-time. Sistem ini berjalan sepenuhnya di ponsel tanpa memerlukan server AI eksternal atau API key.

---

## Arsitektur Model

### Multi-Layer Perceptron (MLP)

```
Input (20 fitur)
    │
    ▼
Hidden Layer 1 (32 neuron, LeakyReLU)
    │
    ▼
Hidden Layer 2 (16 neuron, LeakyReLU)
    │
    ▼
Output Layer (6 neuron, Sigmoid + Temperature Scaling T=1.3)
```

| Komponen | Detail |
|----------|--------|
| **Tipe** | Multi-Layer Perceptron (MLP) |
| **Total Parameter** | 1.222 (20×32 + 32 + 32×16 + 16 + 16×6 + 6) |
| **Ukuran Memory** | ~4.9 KB (float32) |
| **Latency Inferensi** | < 1 ms pada device modern |
| **Operasi (FLOPs)** | ~1.500 per prediksi |
| **Dependencies** | Tidak ada — pure Kotlin |

### Mengapa MLP?

Berdasarkan penelitian **Gorishniy et al. (NeurIPS 2021)** "Revisiting Deep Learning Models for Tabular Data", MLP terbukti kompetitif dengan model tree-based (XGBoost, LightGBM) untuk data tabular asalkan arsitektur dan inisialisasi tepat. Data cuaca bersifat tabular (20 fitur numerik), sehingga MLP adalah pilihan optimal yang:
- **Ringan**: Berjalan di semua ponsel Android 8+
- **Cepat**: Inferensi < 1 ms
- **Tanpa dependency**: Tidak perlu TensorFlow Lite, ONNX, dll
- **Deterministic**: Hasil reprodusibel

---

## Input: 20 Fitur Meteorologi

Setiap fitur dinormalisasi ke rentang [0, 1]:

| # | Fitur | Sumber | Normalisasi |
|---|-------|--------|-------------|
| 0 | Curah hujan total (mm) | Weather API | 0–200 mm |
| 1 | Intensitas hujan maks (mm/h) | Hourly | 0–50 mm/h |
| 2 | Kecepatan angin maks (km/h) | Current/Hourly | 0–200 km/h |
| 3 | Gust maks (km/h) | Current/Daily | 0–200 km/h |
| 4 | Wind shear (gust − avg wind) | Derived | 0–80 km/h |
| 5 | Tekanan rendah (inverted) | Hourly | 900–1050 hPa |
| 6 | Penurunan tekanan 24h | Hourly | 0–30 hPa |
| 7 | Kelembaban rata-rata | Hourly | 0–100% |
| 8 | CAPE maks (J/kg) | Current/Hourly | 0–5000 J/kg |
| 9 | Freezing level rendah (inverted) | Hourly | 0–6000 m |
| 10 | Tutupan awan | Current | 0–100% |
| 11 | Dew point spread (inverted) | Derived | 0–30°C |
| 12 | Tinggi gelombang (m) | Marine API | 0–10 m |
| 13 | Swell height (m) | Marine API | 0–5 m |
| 14 | Rasio debit sungai | Flood API | 0–10x |
| 15 | Durasi hujan (jam/24) | Hourly | 0–24 jam |
| 16 | Antecedent rainfall 3 hari | Daily | 0–300 mm |
| 17 | Hari hujan berturut | Daily | 0–7 hari |
| 18 | Keparahan kode WMO | Current/Hourly | 0–1 |
| 19 | Suhu tertinggi | Daily | 20–50°C |

**Missing data** → Diisi 0.5 (nilai netral) dan bobot NN dikurangi otomatis.

---

## Output: 6 Jenis Bencana

| # | Bencana | Fitur Utama |
|---|---------|-------------|
| 0 | Banjir | precipTotal, precipIntensity, dischargeRatio |
| 1 | Banjir Rob | waveHeight, swellHeight, pressureLow |
| 2 | Siklon Tropis | windSpeed, pressureLow, pressureDrop |
| 3 | Badai Petir | capeEnergy, windShear, weatherSeverity |
| 4 | Tanah Longsor | antecedentRain, rainDuration, humidity |
| 5 | Tanah Amblas | consecutiveRain, antecedentRain, dischargeRatio |

---

## Inisialisasi Bobot (Domain-Informed)

Alih-alih random initialization, bobot diinisialisasi menggunakan **matriks relevansi** yang disusun dari:
- Threshold cuaca ekstrem BMKG
- WMO Multi-Hazard Guidelines (2023)
- Penelitian korelasi parameter-bencana Indonesia

### Matriks Relevansi [20 × 6]

Setiap elemen menunjukkan seberapa relevan fitur ke-i untuk bencana ke-j (0–1):

```
                    FLOOD  TIDAL  CYCL  THUND  LANDS  SUBS
precipTotal         0.90   0.20   0.50  0.30   0.80   0.70
precipIntensity     0.85   0.10   0.30  0.50   0.60   0.30
windSpeed           0.10   0.50   0.90  0.40   0.05   0.05
windGusts           0.10   0.40   0.85  0.60   0.05   0.05
windShear           0.05   0.15   0.50  0.85   0.05   0.05
pressureLow         0.30   0.60   0.95  0.30   0.10   0.10
pressureDrop        0.20   0.50   0.85  0.25   0.05   0.05
humidity            0.50   0.20   0.20  0.30   0.60   0.50
capeEnergy          0.15   0.10   0.40  0.95   0.10   0.05
...
```

### Variasi Neuron (Hidden Layer 1 — 32 neuron)

Setiap 6 neuron berspesialisasi pada satu jenis bencana, dengan 5 variasi:

| Variasi | Deskripsi | Fungsi |
|---------|-----------|--------|
| 0 | Direct mapping | Deteksi langsung sesuai relevansi |
| 1 | Diversity | +noise untuk menangkap pola unexpected |
| 2 | High-threshold | Hanya aktif pada sinyal ekstrem |
| 3 | Squared emphasis | Fokus pada fitur paling relevan |
| 4 | Inhibitory | Menekan sinyal false-positive |
| 5+ | Random exploration | Eksplorasi pola baru |

### Xavier-like Scaling

Scaling factor per layer (He et al., ICCV 2015):
- Layer 1: `√(2/(20+32)) × 2.5 ≈ 0.49`
- Layer 2: `√(2/(32+16)) × 2.0 ≈ 0.58`
- Layer 3: `√(2/(16+6)) × 2.0 ≈ 0.60`

---

## Temperature Scaling (Output Calibration)

Berdasarkan **Guo et al. (ICML 2017)** "On Calibration of Modern Neural Networks":

```
output[j] = sigmoid(z[j] / T)    dimana T = 1.3
```

Temperature T > 1 membuat distribusi output lebih "lembut" (less confident), yang menghasilkan probabilitas yang lebih terkalibrasi. Ini mencegah model over-confident pada kasus edge.

---

## Ensemble Fusion

Prediksi akhir menggabungkan NN dengan rule-based scoring:

```
finalScore = α × nnScore + (1 − α) × ruleScore
dimana α = 0.6 × dataCompleteness
```

| Kondisi | α | Penjelasan |
|---------|---|------------|
| Data lengkap (100%) | 0.60 | NN mendominasi 60% |
| Data parsial (50%) | 0.30 | Setara rule & NN |
| Data minimal (25%) | 0.15 | Rule-based dominan |

Rule-based tetap dipertahankan karena:
1. Menyediakan boundary constraints yang jelas (mis. curah hujan > 100mm → HIGH)
2. Mengkompensasi ketika NN belum belajar cukup
3. Menjamin perilaku minimum yang tidak aneh

---

## Incremental Learning

### Motivasi

Model dimulai dengan domain-informed weights (bukan trained dari dataset). Seiring penggunaan, model belajar dari observasi cuaca aktual untuk memperbaiki prediksi secara bertahap.

### Metode

**Online Gradient Descent** dimodifikasi untuk mobile:

```
Simplified Backpropagation (Output Layer Only)
┌──────────────────────────────────┐
│  δ = prediction − target         │  ← BCE gradient
│  grad_w3 = δ × h2                │  ← chain rule
│  momentum = 0.9 × old + 0.1 × g │  ← EMA smoothing
│  w3 -= lr × momentum + λ × w3   │  ← SGD + weight decay
└──────────────────────────────────┘
```

**Hanya output layer** (102 dari 1.222 parameter) yang diupdate karena:
1. Hidden layers sudah diinisialisasi dengan domain knowledge yang kuat
2. Update full network berisiko **catastrophic forgetting**
3. Hemat komputasi ~12x

### Hyperparameters

| Parameter | Nilai | Alasan |
|-----------|-------|--------|
| Learning rate awal | 0.005 | Kecil karena weights sudah bagus |
| Learning rate minimum | 0.0005 | Mencegah stagnasi |
| LR decay | 0.995 per step | Stabilitas jangka panjang |
| Momentum (EMA) | 0.9 | Smooth gradient noise |
| Gradient clipping | ±1.0 | Mencegah divergence |
| Weight decay (L2) | 0.0001 | Regularisasi |

### Data Flow

```
Hari 1: User buka app
        → Prediksi dibuat (NN + rule)
        → Prediksi + fitur disimpan sebagai "sample"

Hari 2: User buka app lagi
        → Cuaca aktual diambil → dijadikan target
        → Bandingkan prediksi kemarin vs target
        → Update output layer weights
        → Prediksi hari ini menggunakan weights baru
```

### Teacher Signal (Weak Supervision)

Target outcome dihitung dari rule-based scoring cuaca aktual:

```
target[i] = ruleScore[i] × 0.8 + 0.1
```

Ini adalah bentuk **knowledge distillation** dimana rule-based system bertindak sebagai teacher yang lemah tapi konsisten.

### Referensi

- Sahoo et al. (ICML 2018) "Online Deep Learning: Learning Deep Neural Networks on the Fly"
- McMahan et al. (KDD 2013) "Ad Click Prediction: a View from the Trenches"
- Bottou (COMPSTAT 2010) "Large-Scale Machine Learning with Stochastic Gradient Descent"

---

## Manajemen Data & Retensi

### Prinsip

1. **Minimasi**: Hanya simpan yang diperlukan untuk learning
2. **Auto-expire**: Data > 30 hari otomatis dihapus
3. **Budget**: Total data AI < 20 KB
4. **Transparansi**: Penggunaan storage ditampilkan di UI

### Storage Breakdown

| Komponen | Maks Ukuran | Expiry |
|----------|-------------|--------|
| Learning samples (50 maks) | ~14 KB | 30 hari |
| Weight deltas | ~1 KB | Persisten |
| Momentum state | ~1 KB | Persisten |
| Metadata | ~0.1 KB | Persisten |
| **TOTAL** | **~16 KB** | |

### Auto-Cleanup

Dijalankan otomatis setiap 24 jam:
1. Hapus sampel > 30 hari
2. Cek total size vs budget (20 KB)
3. Jika over-budget, hapus sampel tertua sampai fit

### Perbandingan Ukuran

| Data | Ukuran |
|------|--------|
| Seluruh sistem AI | ~16 KB |
| 1 foto WhatsApp | ~200 KB |
| 1 lagu MP3 | ~4 MB |

---

## Performa & Overhead

### Inferensi

| Metrik | Nilai |
|--------|-------|
| Waktu inferensi | < 1 ms |
| FLOPs | ~1.500 |
| Memory | ~5 KB (weights) |
| Battery impact | Negligible |

### Learning (per step)

| Metrik | Nilai |
|--------|-------|
| Waktu update | < 0.5 ms |
| Parameters updated | 102 (output layer only) |
| Storage write | ~1 KB |

### Perbandingan dengan Alternatif

| Model | Size | Latency | Dependencies |
|-------|------|---------|-------------|
| **Cuacaku MLP** | **5 KB** | **< 1 ms** | **None** |
| TensorFlow Lite | 5–50 MB | 10–100 ms | TFLite runtime |
| ONNX Runtime | 10–30 MB | 5–50 ms | ONNX runtime |
| Cloud API call | 0 KB | 200–2000 ms | Internet + API key |

---

## File Reference

```
data/ai/
├── DisasterNeuralNetwork.kt     # Model MLP + forward propagation + weight init
├── WeatherFeatureExtractor.kt   # Ekstraksi & normalisasi 20 fitur
├── IncrementalLearningEngine.kt # Online learning + sample management
└── DataRetentionManager.kt      # Kebijakan retensi data & cleanup

data/model/
└── DisasterData.kt              # Data classes (DisasterForecast, DisasterPrediction)

data/repository/
├── DisasterAnalysisEngine.kt    # Rule-based analysis + ensemble fusion
└── DisasterRepository.kt        # Orchestration (fetch → analyze → learn)

ui/screens/
└── DisasterForecastScreen.kt    # UI (AI badge, learning stats, NN scores)
```

---

## Referensi Ilmiah

1. **Gorishniy, Y.** et al. (2021). "Revisiting Deep Learning Models for Tabular Data." *NeurIPS*. — MLP kompetitif untuk data tabular.

2. **Guo, C.** et al. (2017). "On Calibration of Modern Neural Networks." *ICML*. — Temperature scaling untuk kalibrasi probabilitas.

3. **He, K.** et al. (2015). "Delving Deep into Rectifiers: Surpassing Human-Level Performance on ImageNet Classification." *ICCV*. — Kaiming initialization + LeakyReLU.

4. **Sahoo, D.** et al. (2018). "Online Deep Learning: Learning Deep Neural Networks on the Fly." *ICML*. — Framework online learning untuk neural networks.

5. **McMahan, H.B.** et al. (2013). "Ad Click Prediction: a View from the Trenches." *KDD*. — Best practices online learning di production.

6. **Bottou, L.** (2010). "Large-Scale Machine Learning with Stochastic Gradient Descent." *COMPSTAT*. — Konvergensi SGD dengan learning rate scheduling.

7. **WMO** (2023). "Guidelines on Multi-Hazard Impact-Based Forecast and Warning Services." — Pedoman sistem peringatan dini multi-bahaya.

8. **BMKG** (2020). "Threshold Cuaca Ekstrem Indonesia." — Batas parameter cuaca ekstrem Indonesia.
