package com.hainesy.karoopace

import com.hainesy.karoopace.PaceBands.Band
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
