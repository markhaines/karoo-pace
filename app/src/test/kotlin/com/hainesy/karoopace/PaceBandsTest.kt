package com.hainesy.karoopace

import com.hainesy.karoopace.PaceBands.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The band decision is the whole extension, and it is pure — so it is provable here rather than
 * by riding a bike and squinting at a 2-inch screen.
 *
 * Speeds are m/s, as the Karoo streams them. 8.33 m/s = 30 km/h.
 */
class PaceBandsTest {

    private val avg = 8.0 // 28.8 km/h

    @Test
    fun `no average yet is neutral`() {
        assertEquals(Band.NEUTRAL, PaceBands.bandFor(10.0, null))
        assertEquals(Band.NEUTRAL, PaceBands.bandFor(10.0, 0.0))
        assertEquals(Band.NEUTRAL, PaceBands.bandFor(10.0, PaceBands.MIN_AVERAGE_MPS))
    }

    @Test
    fun `no speed reading is neutral`() {
        assertEquals(Band.NEUTRAL, PaceBands.bandFor(null, avg))
    }

    @Test
    fun `below the minimum speed stays neutral however far off the average`() {
        assertEquals(Band.NEUTRAL, PaceBands.bandFor(1.0, avg))
        // 5 km/h exactly is the first speed that counts
        assertEquals(Band.MUCH_SLOWER, PaceBands.bandFor(PaceBands.MIN_COLOUR_SPEED_MPS, avg))
    }

    @Test
    fun `bands either side of the average`() {
        assertEquals(Band.SAME, PaceBands.bandFor(avg, avg))
        assertEquals(Band.SAME, PaceBands.bandFor(avg * 1.04, avg))
        assertEquals(Band.SAME, PaceBands.bandFor(avg * 0.96, avg))

        assertEquals(Band.FASTER, PaceBands.bandFor(avg * 1.05, avg))
        assertEquals(Band.FASTER, PaceBands.bandFor(avg * 1.14, avg))
        assertEquals(Band.MUCH_FASTER, PaceBands.bandFor(avg * 1.15, avg))

        assertEquals(Band.SLOWER, PaceBands.bandFor(avg * 0.95, avg))
        assertEquals(Band.SLOWER, PaceBands.bandFor(avg * 0.86, avg))
        assertEquals(Band.MUCH_SLOWER, PaceBands.bandFor(avg * 0.85, avg))
    }

    @Test
    fun `bands scale with the average rather than a fixed window`() {
        // 5% of a slow average is a much narrower km/h window than 5% of a fast one,
        // which is the point of using percentages.
        assertEquals(Band.SAME, PaceBands.bandFor(4.0 * 1.02, 4.0))
        assertEquals(Band.SAME, PaceBands.bandFor(12.0 * 1.02, 12.0))
        assertEquals(Band.FASTER, PaceBands.bandFor(12.0 * 1.06, 12.0))
    }

    @Test
    fun `coloured bands are black text, uncoloured are white`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        listOf(Band.MUCH_FASTER, Band.FASTER, Band.SLOWER, Band.MUCH_SLOWER).forEach {
            assertEquals("$it should use black text", black, PaceBands.textArgb(it))
        }
        listOf(Band.SAME, Band.NEUTRAL).forEach {
            assertEquals("$it should be an ordinary black card", black, PaceBands.backgroundArgb(it))
            assertEquals("$it should use white text", white, PaceBands.textArgb(it))
        }
    }

    @Test
    fun `arrows point the right way`() {
        assertEquals(2, PaceBands.arrowLevel(Band.MUCH_FASTER))
        assertEquals(1, PaceBands.arrowLevel(Band.FASTER))
        assertEquals(0, PaceBands.arrowLevel(Band.SAME))
        assertEquals(0, PaceBands.arrowLevel(Band.NEUTRAL))
        assertEquals(-1, PaceBands.arrowLevel(Band.SLOWER))
        assertEquals(-2, PaceBands.arrowLevel(Band.MUCH_SLOWER))
    }

    @Test
    fun `unit conversion`() {
        assertEquals(36.0, PaceBands.toDisplayUnits(10.0, imperial = false), 0.001)
        assertEquals(22.3694, PaceBands.toDisplayUnits(10.0, imperial = true), 0.001)
    }

    // --- Invalid telemetry (issue #2) ---

    private val invalid = listOf(
        null,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        -0.1,
        -avg,
    )

    @Test
    fun `invalid speeds are no reading`() {
        invalid.forEach { assertNull("$it should be no reading", PaceBands.validSpeed(it)) }
        assertEquals(0.0, PaceBands.validSpeed(0.0)!!, 0.0)
        assertEquals(avg, PaceBands.validSpeed(avg)!!, 0.0)
        // -0.0 is a valid stop, but must never be shown as "-0"
        assertEquals("0.0", PaceBands.validSpeed(-0.0).toString())
    }

    @Test
    fun `invalid smoothed speed is neutral whatever was shown before`() {
        invalid.forEach { bad ->
            Band.entries.forEach { previous ->
                assertEquals("smoothed=$bad after $previous", Band.NEUTRAL, PaceBands.bandFor(bad, avg, previous))
            }
        }
    }

    @Test
    fun `invalid average is neutral whatever was shown before`() {
        invalid.forEach { bad ->
            Band.entries.forEach { previous ->
                assertEquals("average=$bad after $previous", Band.NEUTRAL, PaceBands.bandFor(avg, bad, previous))
            }
        }
    }

    @Test
    fun `missing or invalid raw speed shows the no-reading dashes, not zero`() {
        invalid.forEach { bad ->
            assertEquals("raw=$bad", PaceBands.NO_READING, PaceBands.speedText(bad, imperial = false, Locale.UK))
            assertEquals("raw=$bad", PaceBands.NO_READING, PaceBands.speedText(bad, imperial = true, Locale.UK))
        }
        // finite in m/s but infinite once converted
        assertEquals(PaceBands.NO_READING, PaceBands.speedText(Double.MAX_VALUE, imperial = false, Locale.UK))
        assertEquals("--", PaceBands.NO_READING)
    }

    @Test
    fun `valid raw speed keeps native formatting`() {
        assertEquals("0", PaceBands.speedText(0.0, imperial = false, Locale.UK))
        assertEquals("0", PaceBands.speedText(-0.0, imperial = false, Locale.UK))
        assertEquals("36", PaceBands.speedText(10.0, imperial = false, Locale.UK))
        assertEquals("22.4", PaceBands.speedText(10.0, imperial = true, Locale.UK))
        assertEquals("22,4", PaceBands.speedText(10.0, imperial = true, Locale.GERMANY))
    }

    // --- Loss and recovery ---

    /** Feeds smoothed speeds through bandFor the way the field does, carrying the shown band. */
    private fun ride(smoothed: List<Double?>, average: Double? = avg, start: Band = Band.NEUTRAL): List<Band> {
        var shown = start
        return smoothed.map { s -> PaceBands.bandFor(s, average, shown).also { shown = it } }
    }

    @Test
    fun `dropout goes neutral and recovery is decided afresh`() {
        val bands = ride(listOf(avg * 1.10, Double.NaN, null, avg * 1.045, avg * 1.10))
        assertEquals(
            listOf(Band.FASTER, Band.NEUTRAL, Band.NEUTRAL, Band.SAME, Band.FASTER),
            bands,
        )
        // Without the dropout the same 1.045 reading is inside FASTER's hysteresis and holds it.
        assertEquals(listOf(Band.FASTER, Band.FASTER), ride(listOf(avg * 1.10, avg * 1.045)))
    }

    @Test
    fun `average dropout goes neutral and recovery is decided afresh`() {
        var shown = PaceBands.bandFor(avg * 0.80, avg)
        assertEquals(Band.MUCH_SLOWER, shown)
        shown = PaceBands.bandFor(avg * 0.80, Double.NaN, shown)
        assertEquals(Band.NEUTRAL, shown)
        assertEquals(Band.MUCH_SLOWER, PaceBands.bandFor(avg * 0.80, avg, shown))
    }

    // --- Hysteresis: oscillation around every threshold ---

    private val h = PaceBands.BAND_HYSTERESIS

    /** For each ratio edge: the band below it and the band above it. */
    private val edges = listOf(
        Triple(PaceBands.FAR_LOW, Band.MUCH_SLOWER, Band.SLOWER),
        Triple(PaceBands.NEAR_LOW, Band.SLOWER, Band.SAME),
        Triple(PaceBands.NEAR_HIGH, Band.SAME, Band.FASTER),
        Triple(PaceBands.FAR_HIGH, Band.FASTER, Band.MUCH_FASTER),
    )

    @Test
    fun `wobbling across a band edge holds the band already shown`() {
        edges.forEach { (edge, below, above) ->
            val wobble = List(20) { i -> avg * (if (i % 2 == 0) edge - h / 2 else edge + h / 2) }
            assertEquals("from below $edge", List(20) { below }, ride(wobble, start = below))
            assertEquals("from above $edge", List(20) { above }, ride(wobble.reversed(), start = above))
        }
    }

    @Test
    fun `stateless decision on the plain edges would have flickered`() {
        // Guards the test above: the wobble really does straddle each edge.
        edges.forEach { (edge, below, above) ->
            assertEquals(below, PaceBands.bandFor(avg * (edge - h / 2), avg))
            assertEquals(above, PaceBands.bandFor(avg * (edge + h / 2), avg))
        }
    }

    @Test
    fun `crossing an edge by more than the hysteresis changes band`() {
        edges.forEach { (edge, below, above) ->
            assertEquals("up through $edge", above, PaceBands.bandFor(avg * (edge + h * 1.5), avg, below))
            assertEquals("down through $edge", below, PaceBands.bandFor(avg * (edge - h * 1.5), avg, above))
        }
    }

    @Test
    fun `a big jump skips straight to the right band`() {
        assertEquals(Band.MUCH_FASTER, PaceBands.bandFor(avg * 1.30, avg, Band.MUCH_SLOWER))
        assertEquals(Band.MUCH_SLOWER, PaceBands.bandFor(avg * 0.70, avg, Band.MUCH_FASTER))
    }

    @Test
    fun `wobbling around the minimum colour speed does not flicker`() {
        val kmh = 1 / 3.6
        val average = 5.0 * kmh // ratio near 1, so a coloured reading is SAME
        val wobble = List(20) { i -> if (i % 2 == 0) 5.1 * kmh else 4.9 * kmh }
        assertEquals(List(20) { Band.SAME }, ride(wobble, average))
        // Starting below the edge stays neutral until 5 km/h is properly reached.
        assertEquals(List(20) { Band.NEUTRAL }, ride(List(20) { i -> if (i % 2 == 0) 4.6 * kmh else 4.9 * kmh }, average))
        // Dropping clear of the margin (under 4 km/h) does go neutral.
        assertEquals(listOf(Band.SAME, Band.NEUTRAL), ride(listOf(5.1 * kmh, 3.9 * kmh), average))
    }

    @Test
    fun `wobbling around the minimum average does not flicker`() {
        val smoothed = 2.0 // m/s, above the minimum colour speed
        var shown = Band.NEUTRAL
        val bands = List(20) { i ->
            val average = if (i % 2 == 0) 0.55 else 0.45
            PaceBands.bandFor(smoothed, average, shown).also { shown = it }
        }
        assertEquals(List(20) { Band.MUCH_FASTER }, bands)
        assertEquals(Band.NEUTRAL, PaceBands.bandFor(smoothed, 0.45))
        assertEquals(Band.NEUTRAL, PaceBands.bandFor(smoothed, 0.35, Band.MUCH_FASTER))
    }

    @Test
    fun `every margin is under half its band so every band stays reachable`() {
        val bandWidths = listOf(
            PaceBands.NEAR_LOW - PaceBands.FAR_LOW,
            PaceBands.NEAR_HIGH - PaceBands.NEAR_LOW,
            PaceBands.FAR_HIGH - PaceBands.NEAR_HIGH,
        )
        bandWidths.forEach { assert(h < it / 2) { "hysteresis $h too wide for band $it" } }
        assert(PaceBands.MIN_COLOUR_SPEED_HYSTERESIS_MPS < PaceBands.MIN_COLOUR_SPEED_MPS)
        assert(PaceBands.MIN_AVERAGE_HYSTERESIS_MPS < PaceBands.MIN_AVERAGE_MPS)
    }
}
