package com.hainesy.karoopace

import io.hammerhead.karooext.extension.KarooExtension

class PaceExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {

    private val speedVsAverage by lazy { SpeedVsAverageDataType(EXTENSION_ID) }

    override val types by lazy {
        listOf(speedVsAverage)
    }

    override fun onCreate() {
        super.onCreate()
        PaceRuntime.system(this)
    }

    override fun onDestroy() {
        // Views first, so they stop before the service they read from is disconnected.
        speedVsAverage.shutdown()
        PaceRuntime.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTENSION_ID = "karoopace"
    }
}
