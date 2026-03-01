package com.weather.forecast.data.repository

import kotlin.math.*

/**
 * Resolves the nearest sea/ocean name based on coordinates.
 *
 * Uses a database of known seas/oceans with their approximate center coordinates
 * and coverage radius. This is more reliable than API-based lookup for seas
 * since Nominatim doesn't support bounded search by feature type.
 *
 * Coverage: Major seas worldwide, with detailed entries for Indonesian waters.
 */
object NearbySeaResolver {

    /**
     * Known sea/ocean with center coordinates and approximate radius in km.
     * 
     * @param nameId Indonesian name
     * @param nameEn English name
     * @param lat Center latitude
     * @param lon Center longitude
     * @param radiusKm Approximate coverage radius
     */
    private data class SeaEntry(
        val nameId: String,
        val nameEn: String,
        val lat: Double,
        val lon: Double,
        val radiusKm: Double
    )

    // Indonesian waters (detailed)
    private val seas = listOf(
        // ── Indonesian Seas ──────────────────────────
        SeaEntry("Laut Jawa", "Java Sea", -5.0, 111.5, 600.0),
        SeaEntry("Laut Bali", "Bali Sea", -7.5, 115.5, 200.0),
        SeaEntry("Laut Flores", "Flores Sea", -7.0, 121.0, 400.0),
        SeaEntry("Laut Banda", "Banda Sea", -5.5, 129.0, 500.0),
        SeaEntry("Laut Sawu", "Savu Sea", -10.0, 122.0, 300.0),
        SeaEntry("Laut Timor", "Timor Sea", -10.5, 127.0, 400.0),
        SeaEntry("Laut Arafura", "Arafura Sea", -8.0, 136.0, 500.0),
        SeaEntry("Selat Sunda", "Sunda Strait", -6.2, 105.5, 150.0),
        SeaEntry("Selat Makassar", "Makassar Strait", -1.5, 118.0, 400.0),
        SeaEntry("Laut Sulawesi", "Celebes Sea", 2.5, 122.0, 400.0),
        SeaEntry("Laut Maluku", "Molucca Sea", 0.5, 126.5, 300.0),
        SeaEntry("Laut Halmahera", "Halmahera Sea", 0.5, 129.5, 200.0),
        SeaEntry("Selat Malaka", "Malacca Strait", 3.0, 100.0, 400.0),
        SeaEntry("Laut Natuna", "Natuna Sea", 3.0, 108.5, 300.0),
        SeaEntry("Selat Karimata", "Karimata Strait", -1.5, 108.5, 200.0),
        SeaEntry("Laut Seram", "Ceram Sea", -2.5, 130.0, 250.0),
        SeaEntry("Teluk Tomini", "Tomini Bay", 0.0, 121.5, 200.0),
        SeaEntry("Teluk Bone", "Bone Bay", -3.5, 121.0, 200.0),
        SeaEntry("Selat Lombok", "Lombok Strait", -8.5, 115.7, 100.0),
        SeaEntry("Laut Cina Selatan", "South China Sea", 12.0, 113.0, 1500.0),

        // ── Indian/Pacific Ocean (Indonesia facing) ──
        SeaEntry("Samudra Hindia", "Indian Ocean", -12.0, 108.0, 2000.0),
        SeaEntry("Samudra Pasifik", "Pacific Ocean", 2.0, 140.0, 3000.0),

        // ── Southeast Asian Seas ────────────────────
        SeaEntry("Laut Sulu", "Sulu Sea", 8.0, 120.5, 400.0),
        SeaEntry("Laut Filipina", "Philippine Sea", 18.0, 130.0, 1500.0),
        SeaEntry("Teluk Thailand", "Gulf of Thailand", 9.5, 101.0, 500.0),
        SeaEntry("Laut Andaman", "Andaman Sea", 10.0, 96.0, 600.0),

        // ── East Asian Seas ─────────────────────────
        SeaEntry("Laut Cina Timur", "East China Sea", 28.0, 125.0, 800.0),
        SeaEntry("Laut Kuning", "Yellow Sea", 35.0, 123.0, 500.0),
        SeaEntry("Laut Jepang", "Sea of Japan", 40.0, 135.0, 800.0),

        // ── South Asian Seas ────────────────────────
        SeaEntry("Laut Arab", "Arabian Sea", 15.0, 65.0, 1500.0),
        SeaEntry("Teluk Benggala", "Bay of Bengal", 14.0, 88.0, 1200.0),

        // ── European/Mediterranean Seas ─────────────
        SeaEntry("Laut Tengah", "Mediterranean Sea", 35.0, 20.0, 2000.0),
        SeaEntry("Laut Utara", "North Sea", 56.0, 3.0, 600.0),
        SeaEntry("Laut Baltik", "Baltic Sea", 58.0, 19.0, 800.0),
        SeaEntry("Laut Hitam", "Black Sea", 43.0, 35.0, 600.0),
        SeaEntry("Laut Merah", "Red Sea", 20.0, 38.5, 1000.0),
        SeaEntry("Laut Kaspia", "Caspian Sea", 41.0, 51.0, 600.0),

        // ── Americas ────────────────────────────────
        SeaEntry("Teluk Meksiko", "Gulf of Mexico", 25.0, -90.0, 1000.0),
        SeaEntry("Laut Karibia", "Caribbean Sea", 15.0, -75.0, 1500.0),

        // ── Major Oceans (fallbacks) ────────────────
        SeaEntry("Samudra Hindia", "Indian Ocean", -20.0, 75.0, 5000.0),
        SeaEntry("Samudra Pasifik", "Pacific Ocean", 0.0, -160.0, 8000.0),
        SeaEntry("Samudra Atlantik", "Atlantic Ocean", 15.0, -35.0, 5000.0),
        SeaEntry("Samudra Arktik", "Arctic Ocean", 85.0, 0.0, 3000.0),
    )

    /**
     * Find the nearest sea/ocean name for given coordinates.
     * Only returns a name if the coordinate is within the sea's coverage radius.
     *
     * @param latitude User latitude
     * @param longitude User longitude
     * @param locale "id" for Indonesian, "en" for English
     * @return Sea name or null if no sea is within range
     */
    fun findNearestSea(latitude: Double, longitude: Double, locale: String = "id"): String? {
        var bestMatch: SeaEntry? = null
        var bestScore = Double.MAX_VALUE

        for (sea in seas) {
            val distance = haversineKm(latitude, longitude, sea.lat, sea.lon)
            // Only consider if within coverage radius
            if (distance <= sea.radiusKm) {
                // Score: prefer closer seas with smaller radius (more specific)
                val score = distance / sea.radiusKm
                if (score < bestScore) {
                    bestScore = score
                    bestMatch = sea
                }
            }
        }

        return bestMatch?.let {
            if (locale == "id") it.nameId else it.nameEn
        }
    }

    /**
     * Haversine distance between two coordinates in kilometers
     */
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
