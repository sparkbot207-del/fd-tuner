package com.bretthalliday.fdtuner.analysis

import com.bretthalliday.fdtuner.ble.SniffPacket
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure Kotlin capture analyzer — no Android dependencies, fully unit-testable.
 *
 * Takes a snapshot of SnifferViewModel's session log + annotation list and produces
 * structured analysis results plus a monospace report string that mirrors the output
 * of the Python fardriver_decode.py diagnostic tool.
 *
 * STRICTLY READ-ONLY: no BLE writes anywhere in this file.
 *
 * Sections implemented:
 *  A. Segmentation          — assign packets to annotation-labelled windows
 *  B. Per-(segment,addr) stats — mean, min, max, populationStdDev, count
 *  C. Static vs Responsive  — spread > max(2.0, 3 × noise)
 *  D. Throttle correlation  — Spearman ρ between throttle level and per-segment mean
 *  E. Brake delta           — brakeSeg vs restSeg delta + bit-flip report
 *  F. Gear / direction      — decode 0xE2 mean for gear-related segments
 */
object CaptureAnalyzer {

    // ── Known field labels ─────────────────────────────────────────────────────

    val FIELD_LABELS: Map<Int, String> = mapOf(
        0xE2 to "status: dir/gear/brake bits",
        0xE3 to "fault/protection flags",
        0xE5 to "MeasureSpeed (RPM x4)",
        0xE6 to "unkE6 <-- UNKNOWN",
        0xE7 to "unkE7 (throttle req?) <-- UNKNOWN",
        0xE8 to "deci_volts (/10 V)",
        0xE9 to "per_mille (throttle ADC?) <-- UNKNOWN",
        0xEA to "lineCurrent (/4 A, signed)",
        0xEB to "unk3 <-- UNKNOWN",
        0xEC to "unk4 (throttle?) <-- UNKNOWN",
        0xED to "ThrottleDepth <-- UNKNOWN",
        0xEE to "pin states <-- UNKNOWN",
        0xEF to "unkEF <-- UNKNOWN",
        0xF0 to "PhaseA (24-bit BE)",
        0xF1 to "PhaseC (24-bit BE)",
        0xF3 to "volts(?) <-- UNKNOWN",
        0xF4 to "motor_temp",
        0xF5 to "lo=unkF5/hi=SOC",
        0x82 to "ThrottleVoltage (x0.01 V)",
        0xDB to "MosTemp"
    )

    private fun label(addr: Int) = FIELD_LABELS[addr] ?: "?? unmapped"

    // ── Result data classes ────────────────────────────────────────────────────

    data class WordStats(
        val addr: Int,
        val mean: Double,
        val min: Int,
        val max: Int,
        val stdDev: Double,
        val count: Int
    )

    /** One annotation-bounded window of packets. */
    data class Segment(
        val tag: String,
        val packetCount: Int,
        val wordStats: Map<Int, WordStats>   // wordAddr → stats
    )

    data class AddrSpread(
        val addr: Int,
        val spread: Double,
        val maxNoise: Double,
        val segmentMeans: Map<String, Double> // segTag → mean
    )

    data class ThrottleCorr(
        val addr: Int,
        val rho: Double,
        val tracksThrottle: Boolean           // |rho| > 0.85
    )

    data class BrakeDelta(
        val addr: Int,
        val brakeMean: Double,
        val restMean: Double,
        val delta: Double,
        val flippedBits: List<Int>            // bit indices 0..15 that differ
    )

    data class GearReport(
        val segmentTag: String,
        val fwd: Int,
        val rev: Int,
        val gear: Int
    )

    data class AnalysisResult(
        val segments: List<Segment>,
        val responsiveAddrs: List<AddrSpread>,
        val staticAddrs: List<AddrSpread>,
        val throttleCorrelations: List<ThrottleCorr>,
        val brakeDeltas: List<BrakeDelta>,
        val gearReports: List<GearReport>,
        val reportText: String
    )

    // ── Public entry point ─────────────────────────────────────────────────────

    fun analyze(
        packets: List<SniffPacket>,
        annotations: List<Pair<Long, String>>
    ): AnalysisResult {
        val segs        = segmentPackets(packets, annotations)
        val (responsive, static) = computeResponsiveness(segs)
        val throttle    = computeThrottleCorrelation(segs)
        val brake       = computeBrakeDeltas(segs)
        val gear        = computeGearDirection(segs)
        val report      = buildReport(packets.size, annotations.size,
                                      segs, responsive, static, throttle, brake, gear)
        return AnalysisResult(segs, responsive, static, throttle, brake, gear, report)
    }

    // ── A + B. Segmentation + word stats ──────────────────────────────────────

    private fun segmentPackets(
        packets: List<SniffPacket>,
        annotations: List<Pair<Long, String>>
    ): List<Segment> {
        val sortedAnns = annotations.sortedBy { it.first }
        val sortedPkts = packets.sortedBy { it.timestampMs }

        // Build an ordered map so segments appear in annotation order
        val groups = LinkedHashMap<String, MutableList<SniffPacket>>()
        groups["(untagged)"] = mutableListOf()
        sortedAnns.forEach { (_, tag) ->
            if (tag.isNotBlank()) groups.getOrPut(tag) { mutableListOf() }
        }

        for (pkt in sortedPkts) {
            val tag = sortedAnns
                .lastOrNull { it.first <= pkt.timestampMs }
                ?.second
                ?: "(untagged)"
            groups.getOrPut(tag) { mutableListOf() }.add(pkt)
        }

        return groups.entries
            .filter { it.value.isNotEmpty() }
            .map { (tag, pkts) -> Segment(tag, pkts.size, wordStats(pkts)) }
    }

    private fun wordStats(packets: List<SniffPacket>): Map<Int, WordStats> {
        val byAddr = mutableMapOf<Int, MutableList<Int>>()
        for (pkt in packets) {
            for ((addr, value) in pkt.words) {
                byAddr.getOrPut(addr) { mutableListOf() }.add(value)
            }
        }
        return byAddr.mapValues { (addr, vals) ->
            val mean = vals.average()
            val stdDev = if (vals.size < 2) 0.0
                         else sqrt(vals.sumOf { (it - mean).pow(2) } / vals.size)
            WordStats(addr, mean, vals.min(), vals.max(), stdDev, vals.size)
        }
    }

    // ── C. Static vs Responsive ────────────────────────────────────────────────

    private fun computeResponsiveness(segs: List<Segment>): Pair<List<AddrSpread>, List<AddrSpread>> {
        val segMeansByAddr  = mutableMapOf<Int, MutableMap<String, Double>>()
        val noiseByAddr     = mutableMapOf<Int, MutableList<Double>>()

        for (seg in segs) {
            for ((addr, stats) in seg.wordStats) {
                segMeansByAddr.getOrPut(addr) { mutableMapOf() }[seg.tag] = stats.mean
                noiseByAddr.getOrPut(addr) { mutableListOf() }.add(stats.stdDev)
            }
        }

        val all = segMeansByAddr.entries
            .filter { it.value.size >= 2 }
            .map { (addr, meanMap) ->
                val spread   = meanMap.values.max() - meanMap.values.min()
                val maxNoise = noiseByAddr[addr]?.maxOrNull() ?: 0.0
                AddrSpread(addr, spread, maxNoise, meanMap.toMap())
            }

        val threshold = { a: AddrSpread -> a.spread > max(2.0, 3.0 * a.maxNoise) }
        return Pair(
            all.filter(threshold).sortedByDescending { it.spread },
            all.filterNot(threshold).sortedBy { it.addr }
        )
    }

    // ── D. Throttle correlation ────────────────────────────────────────────────

    /** Extract numeric throttle level (0–100) from an annotation tag, or null. */
    internal fun throttleLevel(tag: String): Double? {
        val lower = tag.lowercase()
        if ("wot" in lower) return 100.0
        if ("throttle" in lower) {
            val after = lower.substringAfter("throttle")
            val m = Regex("""\d+(\.\d+)?""").find(after) ?: return null
            return m.value.toDouble()
        }
        return null
    }

    private fun computeThrottleCorrelation(segs: List<Segment>): List<ThrottleCorr> {
        val throttleSegs = segs.mapNotNull { seg ->
            val level = throttleLevel(seg.tag) ?: return@mapNotNull null
            level to seg
        }
        if (throttleSegs.size < 3) return emptyList()

        // Only consider addresses present in >= 3 throttle segments
        val addrCount = mutableMapOf<Int, Int>()
        for ((_, seg) in throttleSegs) {
            for (addr in seg.wordStats.keys) {
                addrCount[addr] = (addrCount[addr] ?: 0) + 1
            }
        }

        return addrCount.entries
            .filter { it.value >= 3 }
            .mapNotNull { (addr, _) ->
                val pairs = throttleSegs.mapNotNull { (level, seg) ->
                    val mean = seg.wordStats[addr]?.mean ?: return@mapNotNull null
                    level to mean
                }
                if (pairs.size < 3) return@mapNotNull null
                val rho = spearmanRho(pairs.map { it.first }, pairs.map { it.second })
                ThrottleCorr(addr, rho, abs(rho) > 0.85)
            }
            .sortedByDescending { abs(it.rho) }
    }

    // ── E. Brake delta ─────────────────────────────────────────────────────────

    private fun computeBrakeDeltas(segs: List<Segment>): List<BrakeDelta> {
        val brakeSeg = segs.firstOrNull { "brake" in it.tag.lowercase() }
                       ?: return emptyList()
        val restSeg  = segs.firstOrNull {
                           "stopped" in it.tag.lowercase() || it.tag == "(untagged)"
                       } ?: return emptyList()

        return brakeSeg.wordStats.entries.mapNotNull { (addr, brStat) ->
            val reStat = restSeg.wordStats[addr] ?: return@mapNotNull null
            val delta  = brStat.mean - reStat.mean
            if (abs(delta) <= 2.0) return@mapNotNull null
            val xor      = brStat.mean.toInt() xor reStat.mean.toInt()
            val flipped  = (0..15).filter { (xor shr it) and 1 == 1 }
            BrakeDelta(addr, brStat.mean, reStat.mean, delta, flipped)
        }.sortedByDescending { abs(it.delta) }
    }

    // ── F. Gear / direction ────────────────────────────────────────────────────

    private val GEAR_REGEX = Regex("gear|reverse|forward|neutral", RegexOption.IGNORE_CASE)

    private fun computeGearDirection(segs: List<Segment>): List<GearReport> {
        return segs.mapNotNull { seg ->
            if (!GEAR_REGEX.containsMatchIn(seg.tag)) return@mapNotNull null
            val e2 = seg.wordStats[0xE2] ?: return@mapNotNull null
            val v  = e2.mean.toInt()
            GearReport(seg.tag, fwd = v and 1, rev = (v shr 1) and 1, gear = (v shr 2) and 0b11)
        }
    }

    // ── Spearman ρ (Pearson on ranks; average ranks for ties) ─────────────────

    /** Visible for testing. */
    internal fun spearmanRho(x: List<Double>, y: List<Double>): Double {
        require(x.size == y.size && x.size >= 2)
        return pearsonR(computeRanks(x), computeRanks(y))
    }

    /** Visible for testing. Returns 1-based average ranks (ties share average rank). */
    internal fun computeRanks(values: List<Double>): List<Double> {
        val n      = values.size
        val sorted = values.mapIndexed { i, v -> i to v }.sortedBy { it.second }
        val ranks  = DoubleArray(n)
        var i = 0
        while (i < n) {
            var j = i
            while (j < n && sorted[j].second == sorted[i].second) j++
            // Ranks at positions i..j-1 (0-indexed) become 1-based (i+1)..(j);
            // average = (i+1 + j) / 2  [since j is exclusive, last rank = j]
            val avgRank = (i.toDouble() + 1.0 + j.toDouble()) / 2.0
            for (k in i until j) ranks[sorted[k].first] = avgRank
            i = j
        }
        return ranks.toList()
    }

    private fun pearsonR(x: List<Double>, y: List<Double>): Double {
        val n  = x.size
        val mx = x.average()
        val my = y.average()
        val cov = (0 until n).sumOf { (x[it] - mx) * (y[it] - my) }
        val sx  = sqrt(x.sumOf { (it - mx).pow(2) })
        val sy  = sqrt(y.sumOf { (it - my).pow(2) })
        return if (sx < 1e-10 || sy < 1e-10) 0.0 else cov / (sx * sy)
    }

    // ── Monospace report ───────────────────────────────────────────────────────

    private val DIV = "-".repeat(58)

    private fun buildReport(
        packetCount: Int,
        annotationCount: Int,
        segs: List<Segment>,
        responsive: List<AddrSpread>,
        static: List<AddrSpread>,
        throttle: List<ThrottleCorr>,
        brake: List<BrakeDelta>,
        gear: List<GearReport>
    ): String = buildString {

        appendLine("=== FARDRIVER CAPTURE ANALYSIS ===")
        appendLine("Packets  : $packetCount")
        appendLine("Segments : ${segs.size}")
        appendLine("Tags     : $annotationCount")
        appendLine()

        // --- Segments
        appendLine(DIV)
        appendLine(" SEGMENTS")
        appendLine(DIV)
        for (seg in segs) {
            appendLine("  %-38s  %d pkts".format("[${seg.tag}]".take(38), seg.packetCount))
        }
        appendLine()

        // --- Responsive
        appendLine(DIV)
        appendLine(" RESPONSIVE ADDRESSES  (spread > max(2, 3 x noise))")
        appendLine(DIV)
        if (responsive.isEmpty()) {
            appendLine("  (none — all addresses stable across segments)")
        } else {
            for (a in responsive) {
                appendLine("  0x%02X  %-36s  spread=%.1f  noise=%.2f"
                    .format(a.addr, label(a.addr).take(36), a.spread, a.maxNoise))
                val means = a.segmentMeans.entries
                    .joinToString("  ") { (t, m) -> "[${t.take(10)}]=%.1f".format(m) }
                appendLine("        $means")
            }
        }
        appendLine()

        // --- Static
        appendLine(DIV)
        appendLine(" STATIC ADDRESSES")
        appendLine(DIV)
        if (static.isEmpty()) {
            appendLine("  (none)")
        } else {
            for (a in static) {
                appendLine("  0x%02X  %-36s  spread=%.1f  noise=%.2f"
                    .format(a.addr, label(a.addr).take(36), a.spread, a.maxNoise))
            }
        }
        appendLine()

        // --- Throttle correlation
        appendLine(DIV)
        appendLine(" THROTTLE CORRELATION  (Spearman |rho| ranked)")
        appendLine(DIV)
        if (throttle.isEmpty()) {
            appendLine("  (fewer than 3 throttle segments —")
            appendLine("   use tags like 'throttle 25%', 'throttle 50%', 'wot', etc.)")
        } else {
            for (c in throttle) {
                val flag = if (c.tracksThrottle) "  ** TRACKS THROTTLE **" else ""
                appendLine("  0x%02X  %-36s  rho=%+.3f%s"
                    .format(c.addr, label(c.addr).take(36), c.rho, flag))
            }
        }
        appendLine()

        // --- Brake delta
        appendLine(DIV)
        appendLine(" BRAKE DELTA  (brake segment vs rest/untagged)")
        appendLine(DIV)
        if (brake.isEmpty()) {
            appendLine("  (no 'brake' segment found, or no |delta| > 2)")
        } else {
            for (d in brake) {
                appendLine("  0x%02X  %s".format(d.addr, label(d.addr)))
                appendLine("        brake=%.1f  rest=%.1f  delta=%+.1f"
                    .format(d.brakeMean, d.restMean, d.delta))
                if (d.flippedBits.isNotEmpty()) {
                    appendLine("        bits flipped: ${d.flippedBits}")
                }
            }
        }
        appendLine()

        // --- Gear / direction
        appendLine(DIV)
        appendLine(" GEAR / DIRECTION  (0xE2 decoded per segment)")
        appendLine(DIV)
        if (gear.isEmpty()) {
            appendLine("  (no segments matching gear|reverse|forward|neutral)")
        } else {
            for (g in gear) {
                appendLine("  [%-22s]  fwd=%d  rev=%d  gear=%d"
                    .format(g.segmentTag.take(22), g.fwd, g.rev, g.gear))
            }
        }
    }
}
