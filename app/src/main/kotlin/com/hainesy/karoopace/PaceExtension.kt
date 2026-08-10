package com.hainesy.karoopace

import io.hammerhead.karooext.extension.KarooExtension

class PaceExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {

    override val types by lazy {
        listOf(SpeedVsAverageDataType(EXTENSION_ID))
    }

    override fun onCreate() {
        super.onCreate()
        PaceRuntime.system(this)
    }

    override fun onDestroy() {
        PaceRuntime.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTENSION_ID = "karoopace"
    }
}
