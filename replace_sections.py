#!/usr/bin/env python3
"""Replace monolithic CrowdsourcedReportSection with per-type interactive sections."""

import os

FILE = "app/src/main/java/com/weather/forecast/ui/screens/SeismicMonitorScreen.kt"

with open(FILE, "r") as f:
    lines = f.readlines()

print(f"Total lines before: {len(lines)}")
print(f"Line 1053 (0-idx 1052): {lines[1052].rstrip()}")
print(f"Line 1298 (0-idx 1297): {lines[1297].rstrip()}")
print(f"Line 1299 (0-idx 1298): {lines[1298].rstrip()}")

NEW_CODE = r'''
// ═══════════════════════════════════════════════════
//  PER-CATEGORY DISASTER REPORT SECTIONS (PetaBencana.id)
// ═══════════════════════════════════════════════════

// ─── FLOOD REPORTS (Banjir) ───

@Composable
private fun FloodReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    val nearbyReports = reports.filter { it.isNearby }
    val maxDepth = reports.mapNotNull { it.floodDepthCm }.maxOrNull() ?: 0
    val avgDepth = reports.mapNotNull { it.floodDepthCm }.let { list ->
        if (list.isNotEmpty()) list.average().toInt() else 0
    }

    // Depth distribution
    val depthCounts = mapOf(
        "Rendah" to reports.count { (it.floodDepthCm ?: 0) in 1..29 },
        "Sedang" to reports.count { (it.floodDepthCm ?: 0) in 30..69 },
        "Dalam" to reports.count { (it.floodDepthCm ?: 0) in 70..149 },
        "Sangat Dalam" to reports.count { (it.floodDepthCm ?: 0) >= 150 }
    )

    val floodBlue = Color(0xFF2196F3)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = floodBlue.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83C\uDF0A", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${strings.localized("Flood Reports", "Laporan Banjir")} (${reports.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "PetaBencana.id \u2022 ${strings.localized("7 days", "7 hari")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            // Nearby alert
            if (nearbyReports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "\u26A0\uFE0F ${nearbyReports.size} ${strings.localized("reports within 100 km", "laporan dalam 100 km")}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // ── Flood Summary Stats ──
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FloodStatBox(
                            label = strings.localized("Max Depth", "Kedalaman Maks"),
                            value = "${maxDepth} cm",
                            color = when {
                                maxDepth >= 150 -> Color(0xFFF44336)
                                maxDepth >= 70 -> Color(0xFFFF9800)
                                maxDepth >= 30 -> Color(0xFFFFEB3B)
                                else -> floodBlue
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloodStatBox(
                            label = strings.localized("Avg Depth", "Kedalaman Rata\u00B2"),
                            value = "${avgDepth} cm",
                            color = floodBlue,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloodStatBox(
                            label = strings.localized("Nearby", "Terdekat"),
                            value = "${nearbyReports.size}",
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ── Depth Severity Distribution ──
                    if (depthCounts.values.any { it > 0 }) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.localized("Depth Distribution", "Distribusi Kedalaman"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val maxCount = depthCounts.values.maxOrNull()?.toFloat() ?: 1f
                        val depthColors = listOf(
                            Color(0xFF4CAF50), Color(0xFFFFEB3B),
                            Color(0xFFFF9800), Color(0xFFF44336)
                        )
                        depthCounts.entries.forEachIndexed { index, (label, count) ->
                            if (count > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.width(90.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction = (count / maxCount).coerceIn(0.05f, 1f))
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(depthColors[index])
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // ── Individual Flood Report Cards ──
                    Spacer(modifier = Modifier.height(12.dp))
                    reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                        FloodReportCard(report)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FloodStatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun FloodReportCard(report: CrowdsourcedDisasterReport) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)
    val floodBlue = Color(0xFF2196F3)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = floodBlue.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Water level gauge
                report.floodDepthCm?.let { depth ->
                    val gaugeHeight = ((depth.coerceIn(0, 300)) / 300f * 40f).coerceAtLeast(8f)
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gaugeHeight.dp)
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    when {
                                        depth >= 150 -> Color(0xFFF44336)
                                        depth >= 70 -> Color(0xFFFF9800)
                                        depth >= 30 -> Color(0xFFFFEB3B)
                                        else -> floodBlue
                                    }.copy(alpha = 0.8f)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                } ?: run {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(floodBlue.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) { Text("\uD83C\uDF0A", fontSize = 20.sp) }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.floodDepthCm?.let { depth ->
                            Text(
                                text = "${depth}cm",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = floodBlue
                            )
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        report.floodSeverityLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Row {
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "$timeAgo \u2022 ${"%.0f".format(report.distanceFromUserKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = report.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── CROWDSOURCED EARTHQUAKE REPORTS (Gempa - Laporan Warga) ───

@Composable
private fun CrowdsourcedEarthquakeReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    val nearbyReports = reports.filter { it.isNearby }
    val quakeOrange = Color(0xFFFF9800)

    // Structure damage distribution
    val damageCounts = (0..4).associateWith { level ->
        reports.count { it.structureDamage == level }
    }
    val damageLabels = listOf(
        strings.localized("None", "Tidak Ada"),
        strings.localized("Light", "Ringan"),
        strings.localized("Moderate", "Sedang"),
        strings.localized("Heavy", "Berat"),
        strings.localized("Severe", "Sangat Berat")
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = quakeOrange.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83C\uDF0D", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${strings.localized("Earthquake Reports", "Laporan Gempa Warga")} (${reports.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "PetaBencana.id \u2022 ${strings.localized("Crowdsourced", "Laporan Warga")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            if (nearbyReports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "\u26A0\uFE0F ${nearbyReports.size} ${strings.localized("reports within 100 km", "laporan dalam 100 km")}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // ── Structure Damage Distribution ──
                    val hasStructureData = damageCounts.values.any { it > 0 }
                    if (hasStructureData) {
                        Text(
                            text = strings.localized("Structure Damage", "Kerusakan Struktur"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val maxDmg = damageCounts.values.maxOrNull()?.toFloat() ?: 1f
                        val dmgColors = listOf(
                            Color(0xFF4CAF50), Color(0xFFCDDC39), Color(0xFFFFEB3B),
                            Color(0xFFFF9800), Color(0xFFF44336)
                        )
                        damageCounts.entries.forEachIndexed { index, (_, count) ->
                            if (count > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = damageLabels[index],
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.width(80.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction = (count / maxDmg).coerceIn(0.05f, 1f))
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(dmgColors[index])
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ── Individual Cards ──
                    reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                        CrowdsourcedQuakeCard(report)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CrowdsourcedQuakeCard(report: CrowdsourcedDisasterReport) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)
    val quakeOrange = Color(0xFFFF9800)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = quakeOrange.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Damage level badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (report.structureDamage) {
                                4 -> Color(0xFFF44336)
                                3 -> Color(0xFFFF9800)
                                2 -> Color(0xFFFFEB3B)
                                1 -> Color(0xFFCDDC39)
                                else -> quakeOrange
                            }.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = report.structureDamage?.let { "$it/4" } ?: "\uD83C\uDF0D",
                        fontSize = if (report.structureDamage != null) 14.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.structureDamageLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = quakeOrange)
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        text = "$timeAgo \u2022 ${"%.0f".format(report.distanceFromUserKm)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Extra chips
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                report.accessibilityFailure?.let { acc ->
                    if (acc > 0) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF44336).copy(alpha = 0.2f)) {
                            Text(
                                text = "\uD83D\uDEA7 ${strings.localized("Road Access", "Akses Jalan")}: $acc/4",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEF9A9A),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (report.evacuationArea == true) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF44336).copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83D\uDEA8 ${strings.localized("Evacuation", "Evakuasi")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── WIND REPORTS (Angin Kencang) ───

@Composable
private fun WindReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    val nearbyReports = reports.filter { it.isNearby }
    val windGrey = Color(0xFF78909C)
    val significantImpact = reports.count { (it.windImpact ?: 0) >= 1 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = windGrey.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDCA8", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${strings.localized("Strong Wind Reports", "Laporan Angin Kencang")} (${reports.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "PetaBencana.id \u2022 ${strings.localized("7 days", "7 hari")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            // Summary chips row
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.12f)) {
                    Text(
                        text = "\uD83D\uDCCD ${strings.localized("Nearby", "Terdekat")}: ${nearbyReports.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (significantImpact > 0) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFF44336).copy(alpha = 0.2f)) {
                        Text(
                            text = "\u26A0\uFE0F ${strings.localized("Significant Impact", "Dampak Signifikan")}: $significantImpact",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Province distribution
                    val provinceCounts = reports.groupBy { it.cityName ?: strings.localized("Unknown", "Tidak Diketahui") }
                        .mapValues { it.value.size }
                        .entries.sortedByDescending { it.value }
                        .take(5)
                    if (provinceCounts.isNotEmpty()) {
                        Text(
                            text = strings.localized("Affected Areas", "Wilayah Terdampak"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(provinceCounts) { (city, count) ->
                                Surface(shape = RoundedCornerShape(20.dp), color = windGrey.copy(alpha = 0.2f)) {
                                    Text(
                                        text = "$city ($count)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                        GenericDisasterReportCard(report, windGrey)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ─── HAZE REPORTS (Kabut Asap) ───

@Composable
private fun HazeReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    val nearbyReports = reports.filter { it.isNearby }
    val hazeGrey = Color(0xFF9E9E9E)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = hazeGrey.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83C\uDF2B\uFE0F", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${strings.localized("Haze Reports", "Laporan Kabut Asap")} (${reports.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "PetaBencana.id \u2022 ${strings.localized("7 days", "7 hari")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            if (nearbyReports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "\u26A0\uFE0F ${nearbyReports.size} ${strings.localized("reports within 100 km", "laporan dalam 100 km")}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Haze visibility impact warning
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = hazeGrey.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\uD83D\uDC41\uFE0F", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.localized("Visibility Warning", "Peringatan Jarak Pandang"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = strings.localized(
                                        "Haze may reduce visibility and affect air quality. Use mask when outdoors.",
                                        "Kabut asap dapat mengurangi jarak pandang dan mempengaruhi kualitas udara. Gunakan masker saat beraktivitas di luar."
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Province distribution
                    val areaCounts = reports.groupBy { it.cityName ?: strings.localized("Unknown", "Tidak Diketahui") }
                        .mapValues { it.value.size }
                        .entries.sortedByDescending { it.value }
                        .take(5)
                    if (areaCounts.isNotEmpty()) {
                        Text(
                            text = strings.localized("Affected Areas", "Wilayah Terdampak"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(areaCounts) { (city, count) ->
                                Surface(shape = RoundedCornerShape(20.dp), color = hazeGrey.copy(alpha = 0.2f)) {
                                    Text(
                                        text = "$city ($count)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                        GenericDisasterReportCard(report, hazeGrey)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ─── FIRE REPORTS (Kebakaran Hutan) ───

@Composable
private fun FireReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    val nearbyReports = reports.filter { it.isNearby }
    val fireRed = Color(0xFFF44336)
    val evacuationReports = reports.filter { it.evacuationArea == true }
    val totalEvacuees = reports.mapNotNull { it.evacuationNumber }.sum()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = fireRed.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDD25", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${strings.localized("Forest Fire Reports", "Laporan Kebakaran Hutan")} (${reports.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "PetaBencana.id \u2022 ${strings.localized("7 days", "7 hari")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            // Summary row
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nearbyReports.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFF9800).copy(alpha = 0.2f)) {
                        Text(
                            text = "\u26A0\uFE0F ${strings.localized("Nearby", "Terdekat")}: ${nearbyReports.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB74D),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (evacuationReports.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = fireRed.copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83D\uDEA8 ${strings.localized("Evacuation", "Evakuasi")}: ${evacuationReports.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (totalEvacuees > 0) {
                    Surface(shape = RoundedCornerShape(20.dp), color = fireRed.copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83E\uDDD1 ${strings.localized("Evacuees", "Pengungsi")}: $totalEvacuees",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Fire safety warning
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = fireRed.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\uD83D\uDEA8", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.localized("Fire Safety", "Keselamatan Kebakaran"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = strings.localized(
                                        "Stay away from fire areas. Follow evacuation instructions from authorities.",
                                        "Jauhi area kebakaran. Ikuti instruksi evakuasi dari pihak berwenang."
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Area distribution
                    val areaCounts = reports.groupBy { it.cityName ?: strings.localized("Unknown", "Tidak Diketahui") }
                        .mapValues { it.value.size }
                        .entries.sortedByDescending { it.value }
                        .take(5)
                    if (areaCounts.isNotEmpty()) {
                        Text(
                            text = strings.localized("Fire Locations", "Lokasi Kebakaran"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(areaCounts) { (city, count) ->
                                Surface(shape = RoundedCornerShape(20.dp), color = fireRed.copy(alpha = 0.2f)) {
                                    Text(
                                        text = "$city ($count)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                        FireReportCard(report)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FireReportCard(report: CrowdsourcedDisasterReport) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)
    val fireRed = Color(0xFFF44336)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = fireRed.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(fireRed.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) { Text("\uD83D\uDD25", fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = fireRed)
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "${"%.0f".format(report.distanceFromUserKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(timeAgo, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }

            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.evacuationArea == true) {
                    Surface(shape = RoundedCornerShape(12.dp), color = fireRed.copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83D\uDEA8 ${strings.localized("Evacuation Zone", "Zona Evakuasi")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                report.evacuationNumber?.let { num ->
                    if (num > 0) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF9800).copy(alpha = 0.2f)) {
                            Text(
                                text = "\uD83E\uDDD1 $num ${strings.localized("evacuees", "pengungsi")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFCC80),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── CROWDSOURCED VOLCANO REPORTS (Gunung Api - Laporan Warga) ───

@Composable
private fun CrowdsourcedVolcanoReportSection(reports: List<CrowdsourcedDisasterReport>) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    val nearbyReports = reports.filter { it.isNearby }
    val volcanoRed = Color(0xFFD32F2F)
    val evacuationReports = reports.filter { it.evacuationArea == true }

    // Aggregate volcanic signs
    val allSigns = reports.flatMap { it.volcanicSignsLabels }.groupBy { it }.mapValues { it.value.size }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = volcanoRed.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83C\uDF0B", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${strings.localized("Volcano Reports", "Laporan Gunung Api")} (${reports.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "PetaBencana.id \u2022 ${strings.localized("Crowdsourced", "Laporan Warga")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            // Summary chips
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nearbyReports.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFF9800).copy(alpha = 0.2f)) {
                        Text(
                            text = "\u26A0\uFE0F ${strings.localized("Nearby", "Terdekat")}: ${nearbyReports.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB74D),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (evacuationReports.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = volcanoRed.copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83D\uDEA8 ${strings.localized("Evacuation", "Evakuasi")}: ${evacuationReports.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Observed volcanic signs summary
                    if (allSigns.isNotEmpty()) {
                        Text(
                            text = strings.localized("Observed Volcanic Signs", "Tanda Vulkanik Teramati"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(allSigns.entries.toList()) { (sign, count) ->
                                Surface(shape = RoundedCornerShape(20.dp), color = volcanoRed.copy(alpha = 0.2f)) {
                                    Text(
                                        text = "$sign ($count)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    reports.sortedBy { it.distanceFromUserKm }.take(10).forEach { report ->
                        VolcanoReportCard(report)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VolcanoReportCard(report: CrowdsourcedDisasterReport) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)
    val volcanoRed = Color(0xFFD32F2F)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = volcanoRed.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(volcanoRed.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) { Text("\uD83C\uDF0B", fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = volcanoRed)
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "${"%.0f".format(report.distanceFromUserKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(timeAgo, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Volcanic signs chips
            if (report.volcanicSignsLabels.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.volcanicSignsLabels.forEach { sign ->
                        Surface(shape = RoundedCornerShape(12.dp), color = volcanoRed.copy(alpha = 0.2f)) {
                            Text(
                                text = sign,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEF9A9A),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Evacuation chips
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.evacuationArea == true) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF44336).copy(alpha = 0.2f)) {
                        Text(
                            text = "\uD83D\uDEA8 ${strings.localized("Evacuation Zone", "Zona Evakuasi")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                report.evacuationNumber?.let { num ->
                    if (num > 0) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF9800).copy(alpha = 0.2f)) {
                            Text(
                                text = "\uD83E\uDDD1 $num ${strings.localized("evacuees", "pengungsi")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFCC80),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── GENERIC DISASTER REPORT CARD (for Wind / Haze) ───

@Composable
private fun GenericDisasterReportCard(report: CrowdsourcedDisasterReport, themeColor: Color) {
    val strings = LocalStrings.current
    val timeAgo = formatTimeAgo(report.time, strings)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(themeColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) { Text(report.emoji, fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.cityName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = themeColor)
                            Text(" \u2022 ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "${"%.0f".format(report.distanceFromUserKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(timeAgo, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Wind impact chip
            report.windImpact?.let { impact ->
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = (if (impact >= 1) Color(0xFFF44336) else Color(0xFFFF9800)).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = report.windImpactLabel ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (impact >= 1) Color(0xFFEF9A9A) else Color(0xFFFFCC80),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (report.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
'''

# Replace lines 1053 to 1298 (1-indexed) = 1052 to 1297 (0-indexed)
new_lines = lines[:1052] + [NEW_CODE + '\n'] + lines[1298:]

with open(FILE, 'w') as f:
    f.writelines(new_lines)

# Verify
with open(FILE, 'r') as f:
    verify_lines = f.readlines()
print(f"Done! Old: {len(lines)} lines, New: {len(verify_lines)} lines")
