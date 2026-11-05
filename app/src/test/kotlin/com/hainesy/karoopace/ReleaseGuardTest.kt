package com.hainesy.karoopace

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Runs in every unit-test variant. In the release one (testReleaseUnitTest, which the release
 * workflow runs before it signs anything) it fails the build if the desk-test switch is on.
 */
class ReleaseGuardTest {

    @Test
    fun `release builds never simulate bands`() {
        if (!BuildConfig.DEBUG) {
            assertFalse("SIMULATE_BANDS must be false in a release build", BuildConfig.SIMULATE_BANDS)
        }
    }
}
