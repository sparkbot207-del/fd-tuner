package com.bretthalliday.fdtuner

import com.bretthalliday.fdtuner.analysis.CaptureAnalyzer
import com.bretthalliday.fdtuner.ble.SniffPacket
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CaptureAnalyzer.
 *
 * Synthetic session covers:
 *  1. Throttle sweep  — asserts throttle-tracking addr ranks above speed addr
 *  2. Brake bit-flip  — asserts bit 2 flipped between brake vs (untagged) rest
 *  3. Gear segments   — asserts 0xE2 decodes to gear 1 / 2 / 3
 *  4. Spearman ranks  — unit test for the rank helper (ties included)
 *  5. throttleLevel() — tag parsing edge cases
 */
class CaptureAnalyzerTest {

    // ── Helper ─────────────────────────────────────────────────────────────────

    private var tsCounter = 0L

    /** Each call returns a monotonically increasing timestamp (ms). */
    private fun ts() = ++tsCounter * 100L

    private fun packet(words: Map<Int, Int>, at: Long = ts()) = SniffPacket(
        timestampMs = at,
        id          = 0,
        baseAddr    = words.keys.first(),
        rawHex      = "-- test --",
        words       = words
    )

    /** Emit `count` identical packets for a segment that starts at `segStartTs`. */
    private fun segPackets(words: Map<Int, Int>, segStartTs: Long, count: Int = 3) =
        (1..count).map { i -> packet(words, segStartTs + i * 10L) }

    // ── 1. Throttle sweep + speed addr non-correlation ─────────────────────────

    @Test
    fun throttleSweep_throttleAddrRanksTop_speedAddrDoesNot() {
        tsCounter = 0L

        // Segment timestamps (each annotation fires before its packets)
        val tStop  = ts()   //  100 — "throttle 0" while spinning
        val tT25   = ts()   //  200 — "throttle 25%"
        val tT50   = ts()   //  300 — "throttle 50%"
        val tT75   = ts()   //  400 — "throttle 75%"
        val tWot   = ts()   //  500 — "wot"

        // Addr 0xE7 = "throttle req" — tracks throttle linearly (rho should be ~1.0)
        // Addr 0xE5 = "RPM"         — high at throttle 0 (coasting), breaks monotonicity
        val packets = mutableListOf<SniffPacket>().apply {
            addAll(segPackets(mapOf(0xE5 to 2000, 0xE7 to 0),   tStop))  // coasting
            addAll(segPackets(mapOf(0xE5 to 500,  0xE7 to 25),  tT25))
            addAll(segPackets(mapOf(0xE5 to 1000, 0xE7 to 50),  tT50))
            addAll(segPackets(mapOf(0xE5 to 1500, 0xE7 to 75),  tT75))
            addAll(segPackets(mapOf(0xE5 to 2000, 0xE7 to 100), tWot))
        }

        val annotations = listOf(
            tStop to "throttle 0",
            tT25  to "throttle 25%",
            tT50  to "throttle 50%",
            tT75  to "throttle 75%",
            tWot  to "wot"
        )

        val result = CaptureAnalyzer.analyze(packets, annotations)

        // At least 2 correlated addresses returned
        assertTrue("Expected throttle correlations", result.throttleCorrelations.isNotEmpty())

        // 0xE7 must rank first (highest |rho|)
        assertEquals(
            "0xE7 (throttle req) should rank #1 in throttle correlation",
            0xE7,
            result.throttleCorrelations.first().addr
        )

        // 0xE7 must be flagged TRACKS THROTTLE
        assertTrue(
            "0xE7 should be flagged TRACKS THROTTLE (|rho| > 0.85)",
            result.throttleCorrelations.first().tracksThrottle
        )

        // 0xE7 rho must be very high (perfect linear match → ≥ 0.99)
        assertTrue(
            "0xE7 rho should be >= 0.99, was ${result.throttleCorrelations.first().rho}",
            result.throttleCorrelations.first().rho >= 0.99
        )

        // 0xE5 must rank below 0xE7
        val e5Corr = result.throttleCorrelations.firstOrNull { it.addr == 0xE5 }
        assertNotNull("0xE5 should appear in throttle correlations", e5Corr)
        assertTrue(
            "0xE5 |rho| should be < 0xE7 |rho|",
            Math.abs(e5Corr!!.rho) < result.throttleCorrelations.first().rho
        )

        // 0xE5 must NOT be flagged as TRACKS THROTTLE (its rho ≈ 0.2)
        assertFalse(
            "0xE5 should NOT be flagged TRACKS THROTTLE",
            e5Corr.tracksThrottle
        )

        // Sanity: report text must contain the TRACKS THROTTLE flag
        assertTrue(result.reportText.contains("TRACKS THROTTLE"))
    }

    // ── 2. Brake bit-flip ──────────────────────────────────────────────────────

    @Test
    fun brakeDelta_bit2Flips_isReported() {
        tsCounter = 0L

        // Packets before first annotation → "(untagged)" rest state; 0xE2 = 0
        val restPackets = (1..5).map { i ->
            packet(mapOf(0xE2 to 0, 0xE5 to 0), at = i * 10L)
        }

        // Brake annotation then packets; 0xE2 = 4 (bit 2 set: 0100 binary)
        val tBrake = 200L
        val brakePackets = segPackets(mapOf(0xE2 to 4, 0xE5 to 0), tBrake)

        val packets     = restPackets + brakePackets
        val annotations = listOf(tBrake to "brake on")

        val result = CaptureAnalyzer.analyze(packets, annotations)

        // Must report at least one brake delta
        assertTrue("Expected brake deltas", result.brakeDeltas.isNotEmpty())

        // 0xE2 must appear in brake deltas
        val e2Delta = result.brakeDeltas.firstOrNull { it.addr == 0xE2 }
        assertNotNull("0xE2 should appear in brake deltas", e2Delta)

        // delta = brakeMean – restMean = 4.0 – 0.0 = +4.0
        assertEquals("Brake delta for 0xE2 should be +4.0", 4.0, e2Delta!!.delta, 0.01)

        // Bit 2 (index 2) must be in the flipped-bits list
        assertTrue(
            "Bit 2 must be in flippedBits, got: ${e2Delta.flippedBits}",
            e2Delta.flippedBits.contains(2)
        )

        // Only bit 2 should have flipped (4 XOR 0 = 4 = 0b0100)
        assertEquals(
            "Only bit 2 should flip for 0xE2 delta",
            listOf(2),
            e2Delta.flippedBits
        )
    }

    // ── 3. Gear decoding ───────────────────────────────────────────────────────

    @Test
    fun gearSegments_decodeTo123() {
        tsCounter = 0L

        // 0xE2 value encoding: fwd=(v and 1), rev=(v>>1 and 1), gear=(v>>2 and 0b11)
        //  gear=1, fwd=1, rev=0 → v = (1<<2) or 1 = 5
        //  gear=2, fwd=1, rev=0 → v = (2<<2) or 1 = 9
        //  gear=3, fwd=1, rev=0 → v = (3<<2) or 1 = 13
        val tG1 = ts()  // 100
        val tG2 = ts()  // 200
        val tG3 = ts()  // 300

        val packets = mutableListOf<SniffPacket>().apply {
            addAll(segPackets(mapOf(0xE2 to 5),  tG1))
            addAll(segPackets(mapOf(0xE2 to 9),  tG2))
            addAll(segPackets(mapOf(0xE2 to 13), tG3))
        }

        val annotations = listOf(
            tG1 to "gear 1",
            tG2 to "gear 2",
            tG3 to "gear 3"
        )

        val result = CaptureAnalyzer.analyze(packets, annotations)

        assertEquals("Expected 3 gear reports", 3, result.gearReports.size)

        val g = result.gearReports.associate { it.segmentTag to it }
        assertTrue("'gear 1' segment must exist",  g.containsKey("gear 1"))
        assertTrue("'gear 2' segment must exist",  g.containsKey("gear 2"))
        assertTrue("'gear 3' segment must exist",  g.containsKey("gear 3"))

        assertEquals("gear 1: gear should be 1", 1, g["gear 1"]!!.gear)
        assertEquals("gear 2: gear should be 2", 2, g["gear 2"]!!.gear)
        assertEquals("gear 3: gear should be 3", 3, g["gear 3"]!!.gear)

        assertEquals("gear 1: fwd=1", 1, g["gear 1"]!!.fwd)
        assertEquals("gear 1: rev=0", 0, g["gear 1"]!!.rev)
        assertEquals("gear 3: fwd=1", 1, g["gear 3"]!!.fwd)
        assertEquals("gear 3: rev=0", 0, g["gear 3"]!!.rev)
    }

    // ── 4. Spearman rank helper ────────────────────────────────────────────────

    @Test
    fun spearmanRanks_noTies_1to5() {
        val ranks = CaptureAnalyzer.computeRanks(listOf(3.0, 1.0, 4.0, 1.5, 2.0))
        // Sorted: 1.0(idx1), 1.5(idx3), 2.0(idx4), 3.0(idx0), 4.0(idx2)
        // → ranks: idx0=4, idx1=1, idx2=5, idx3=2, idx4=3
        assertEquals(4.0, ranks[0], 0.001)
        assertEquals(1.0, ranks[1], 0.001)
        assertEquals(5.0, ranks[2], 0.001)
        assertEquals(2.0, ranks[3], 0.001)
        assertEquals(3.0, ranks[4], 0.001)
    }

    @Test
    fun spearmanRanks_tiesGetAverageRank() {
        // [2, 2, 1] → sorted: 1.0(idx2), 2.0(idx0), 2.0(idx1)
        // 1.0 → rank 1;  2.0,2.0 → average(2,3)=2.5
        val ranks = CaptureAnalyzer.computeRanks(listOf(2.0, 2.0, 1.0))
        assertEquals(2.5, ranks[0], 0.001)
        assertEquals(2.5, ranks[1], 0.001)
        assertEquals(1.0, ranks[2], 0.001)
    }

    @Test
    fun spearmanRho_perfectPositive_returns1() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(1.0, CaptureAnalyzer.spearmanRho(x, y), 0.001)
    }

    @Test
    fun spearmanRho_perfectNegative_returnsMinus1() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = listOf(5.0, 4.0, 3.0, 2.0, 1.0)
        assertEquals(-1.0, CaptureAnalyzer.spearmanRho(x, y), 0.001)
    }

    // ── 5. throttleLevel() tag parsing ────────────────────────────────────────

    @Test
    fun throttleLevel_parsing() {
        assertEquals(100.0, CaptureAnalyzer.throttleLevel("wot"),          0.001)
        assertEquals(100.0, CaptureAnalyzer.throttleLevel("WOT"),          0.001)
        assertEquals(100.0, CaptureAnalyzer.throttleLevel("wot full"),     0.001)
        assertEquals(25.0,  CaptureAnalyzer.throttleLevel("throttle 25%"), 0.001)
        assertEquals(50.0,  CaptureAnalyzer.throttleLevel("throttle 50"),  0.001)
        assertEquals(0.0,   CaptureAnalyzer.throttleLevel("throttle 0"),   0.001)
        assertNull(          CaptureAnalyzer.throttleLevel("brake on"))
        assertNull(          CaptureAnalyzer.throttleLevel("stopped"))
        assertNull(          CaptureAnalyzer.throttleLevel("(untagged)"))
        assertNull(          CaptureAnalyzer.throttleLevel("gear 1"))
    }
}
