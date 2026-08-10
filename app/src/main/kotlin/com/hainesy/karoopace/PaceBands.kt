package com.hainesy.karoopace

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
     */
    fun bandFor(smoothedMps: Double?, averageMps: Double?): Band {
        if (smoothedMps == null || averageMps == null) return Band.NEUTRAL
        if (averageMps <= MIN_AVERAGE_MPS) return Band.NEUTRAL
        if (smoothedMps < MIN_COLOUR_SPEED_MPS) return Band.NEUTRAL

        val ratio = smoothedMps / averageMps
        return when {
            ratio >= FAR_HIGH -> Band.MUCH_FASTER
            ratio >= NEAR_HIGH -> Band.FASTER
            ratio > NEAR_LOW -> Band.SAME
            ratio > FAR_LOW -> Band.SLOWER
            else -> Band.MUCH_SLOWER
        }
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
}
