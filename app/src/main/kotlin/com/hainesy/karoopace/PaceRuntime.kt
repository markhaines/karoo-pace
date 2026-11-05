package com.hainesy.karoopace

import android.content.Context
import android.util.Log
import io.hammerhead.karooext.KarooSystemService

/**
 * One KarooSystemService for the process. Connected from [PaceExtension.onCreate] rather than
 * lazily on first use: the service silently drops work until the binding is up, so connecting
 * late loses the first updates. The lifecycle itself lives in [SharedConnection], which is
 * unit tested.
 */
object PaceRuntime {

    private const val TAG = "PaceRuntime"

    private val connection = SharedConnection<KarooSystemService>(
        connect = { it.connect { connected -> Log.d(TAG, "Karoo system connected=$connected") } },
        disconnect = { it.disconnect() },
    )

    fun system(context: Context): KarooSystemService =
        connection.get { KarooSystemService(context.applicationContext) }

    fun shutdown() = connection.shutdown()
}
