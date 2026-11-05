package com.hainesy.karoopace

import java.text.NumberFormat
import java.util.Locale

/**
 * Decides which band the current speed falls into relative to the ride average, and what that
 * band looks like.
 *
 * Deliberately free of Android imports so the whole decision can be unit tested on the JVM —
 * it is the entire point of the extension and proving it needs no bike.
 */
object PaceBands {

    /**
     * Band edges as a fraction of the ride average, so they scale with the ride rather than
     * being a fixed km/h window that is too tight at 15km/h and too loose at 40km/h.
     */
    const val NEAR_LOW = 0.95
    const val NEAR_HIGH = 1.05
    const val FAR_LOW = 0.85
    const val FAR_HIGH = 1.15

    /** Below this there is no useful comparison to make, so the field stays neutral. */
    const val MIN_COLOUR_SPEED_MPS = 5.0 / 3.6 // 5 km/h

    /** Ride average below this means the ride has not really started. */
    const val MIN_AVERAGE_MPS = 0.5

    /**
     * Hysteresis: how far past an edge a reading has to go before it leaves the band it is
     * already in. Entering a band from neutral uses the plain edges above; only *leaving* one
     * needs the extra margin, so a reading sitting on an edge holds its colour instead of
     * flipping the whole card every update. See ADR-0002.
     *
     * Each margin is well under half the gap between its edges, so every band stays reachable.
     */
    const val BAND_HYSTERESIS = 0.01 // 1 percentage point of the average
    const val MIN_COLOUR_SPEED_HYSTERESIS_MPS = 1.0 / 3.6 // colour holds down to 4 km/h
    const val MIN_AVERAGE_HYSTERESIS_MPS = 0.1 // colour holds down to a 0.4 m/s average

    /** What the field shows with no usable speed: native fields show this, never a fake 0. */
    const val NO_READING = "--"

    /**
     * A speed the Karoo reported that can be trusted, or null. Null, NaN, infinite and negative
     * all mean "no reading": NaN in particular compares false against every edge and would
     * otherwise fall through to the slowest band. -0.0 is folded to 0.0 so it never shows "-0".
     */
    fun validSpeed(mps: Double?): Double? =
        if (mps == null || !mps.isFinite() || mps < 0.0) null else mps + 0.0

    enum class Band {
        MUCH_FASTER,
        FASTER,
        SAME,
        SLOWER,
        MUCH_SLOWER,

        /** Stopped, crawling, or no ride average yet. Renders exactly like a native field. */
        NEUTRAL,
    }

    /**
     * @param smoothedMps speed driving the colour. Use the 3s smoothed stream, not the raw one:
     *   raw GPS speed wanders by 1-2 km/h at steady effort, which is wider than the near band
     *   and makes the colour strobe.
     * @param averageMps the ride average, as the Karoo reports it.
     * @param previous the band last shown on this field, for hysteresis. Pass [Band.NEUTRAL]
     *   (the default) for a stateless decision on the plain edges.
     *
     * Invalid input (see [validSpeed]) is always [Band.NEUTRAL] whatever [previous] was, and
     * since neutral carries no hysteresis, the first valid reading after a dropout is decided
     * afresh on the plain edges.
     */
    fun bandFor(smoothedMps: Double?, averageMps: Double?, previous: Band = Band.NEUTRAL): Band {
        val smoothed = validSpeed(smoothedMps) ?: return Band.NEUTRAL
        val average = validSpeed(averageMps) ?: return Band.NEUTRAL

        val comparing = previous != Band.NEUTRAL
        val minAverage = if (comparing) MIN_AVERAGE_MPS - MIN_AVERAGE_HYSTERESIS_MPS else MIN_AVERAGE_MPS
        val minSpeed =
            if (comparing) MIN_COLOUR_SPEED_MPS - MIN_COLOUR_SPEED_HYSTERESIS_MPS else MIN_COLOUR_SPEED_MPS
        if (average <= minAverage) return Band.NEUTRAL
        if (smoothed < minSpeed) return Band.NEUTRAL

        val ratio = smoothed / average
        if (comparing && holds(previous, ratio)) return previous
        return when {
            ratio >= FAR_HIGH -> Band.MUCH_FASTER
            ratio >= NEAR_HIGH -> Band.FASTER
            ratio > NEAR_LOW -> Band.SAME
            ratio > FAR_LOW -> Band.SLOWER
            else -> Band.MUCH_SLOWER
        }
    }

    /** Whether [ratio] is still inside [band] once its edges are widened by [BAND_HYSTERESIS]. */
    private fun holds(band: Band, ratio: Double): Boolean {
        val (low, high) = when (band) {
            Band.MUCH_FASTER -> FAR_HIGH to Double.POSITIVE_INFINITY
            Band.FASTER -> NEAR_HIGH to FAR_HIGH
            Band.SAME -> NEAR_LOW to NEAR_HIGH
            Band.SLOWER -> FAR_LOW to NEAR_LOW
            Band.MUCH_SLOWER -> Double.NEGATIVE_INFINITY to FAR_LOW
            Band.NEUTRAL -> return false
        }
        return ratio > low - BAND_HYSTERESIS && ratio < high + BAND_HYSTERESIS
    }

    /**
     * Background for a band, from the Karoo's own design system (sampled out of `ride.apk`'s
     * resources, not invented). Native zone-coloured fields use these light tints, which is why
     * they can carry black text.
     *
     * SAME and NEUTRAL are deliberately the ordinary black card: "about the same" needs no
     * colour, and an uncoloured field is the most native thing we can draw.
     */
    fun backgroundArgb(band: Band): Int = when (band) {
        Band.MUCH_FASTER -> 0xFF32E09A.toInt() // success_green_500
        Band.FASTER -> 0xFF8CF2C9.toInt() // success_green_400
        Band.SLOWER -> 0xFFFEB07F.toInt() // strava_orange_400
        Band.MUCH_SLOWER -> 0xFFFF5252.toInt() // red_500
        Band.SAME, Band.NEUTRAL -> 0xFF000000.toInt()
    }

    /**
     * Native rule, and the thing upstream gets wrong: a coloured field is always black text, an
     * uncoloured one always white. The text colour never tracks the value.
     */
    fun textArgb(band: Band): Int = when (band) {
        Band.SAME, Band.NEUTRAL -> 0xFFFFFFFF.toInt()
        else -> 0xFF000000.toInt()
    }

    /** -2..+2, driving the chevron shown to the left of the number. 0 draws nothing. */
    fun arrowLevel(band: Band): Int = when (band) {
        Band.MUCH_FASTER -> 2
        Band.FASTER -> 1
        Band.SLOWER -> -1
        Band.MUCH_SLOWER -> -2
        Band.SAME, Band.NEUTRAL -> 0
    }

    fun toDisplayUnits(mps: Double, imperial: Boolean): Double =
        if (imperial) mps * 2.23694 else mps * 3.6

    /**
     * The number the field shows for the raw speed: [NO_READING] when there is no usable
     * reading, otherwise native formatting — "0", not "0.0", and one decimal only when there is
     * one to show.
     */
    fun speedText(rawMps: Double?, imperial: Boolean, locale: Locale = Locale.getDefault()): String {
        val mps = validSpeed(rawMps) ?: return NO_READING
        val display = toDisplayUnits(mps, imperial)
        if (!display.isFinite()) return NO_READING
        return NumberFormat.getInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(display)
    }
}
