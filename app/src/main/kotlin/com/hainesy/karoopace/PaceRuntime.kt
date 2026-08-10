package com.hainesy.karoopace

import android.content.Context
import android.util.Log
import io.hammerhead.karooext.KarooSystemService

/**
 * One KarooSystemService for the process. Connected from [PaceExtension.onCreate] rather than
 * lazily on first use: the service silently drops work until the binding is up, so connecting
 * late loses the first updates.
 */
object PaceRuntime {

    private const val TAG = "PaceRuntime"

    @Volatile
    private var service: KarooSystemService? = null

    @Synchronized
    fun system(context: Context): KarooSystemService {
        service?.let { return it }
        val created = KarooSystemService(context.applicationContext)
        created.connect { connected -> Log.d(TAG, "Karoo system connected=$connected") }
        service = created
        return created
    }

    @Synchronized
    fun shutdown() {
        service?.disconnect()
        service = null
    }
}
