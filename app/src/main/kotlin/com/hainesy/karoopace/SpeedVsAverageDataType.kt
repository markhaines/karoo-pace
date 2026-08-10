package com.hainesy.karoopace

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class SpeedVsAverageDataType(extension: String) : DataTypeImpl(extension, TYPE_ID) {

    private val glance = GlanceRemoteViews()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // Draw the whole field ourselves. Asking the host for a header costs more vertical
        // space than drawing our own, and asking it to render the number puts its digits
        // underneath our background rather than on top of it.
        scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(UpdateNumericConfig(DataType.Type.SPEED))
        }

        val karoo = PaceRuntime.system(context)

        val job = scope.launch {
            val speed = karoo.streamDataFlow(DataType.Type.SPEED)
            val smoothed = karoo.streamDataFlow(DataType.Type.SMOOTHED_3S_AVERAGE_SPEED)
            val average = karoo.streamDataFlow(DataType.Type.AVERAGE_SPEED)
            val profile = if (config.preview) flowOf(null) else karoo.streamUserProfile()

            combine(speed, smoothed, average, profile) { s, sm, avg, prof ->
                Frame(s.value, sm.value, avg.value, prof.isImperial())
            }.collect { frame ->
                if (!render(context, config, emitter, frame)) {
                    return@collect
                }
            }
        }

        emitter.setCancellable { job.cancel() }
    }

    private data class Frame(
        val speedMps: Double?,
        val smoothedMps: Double?,
        val averageMps: Double?,
        val imperial: Boolean,
    )

    private fun UserProfile?.isImperial(): Boolean =
        this?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL

    /** Returns false once the host-side binder is gone, which is the only teardown signal. */
    private suspend fun render(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
        frame: Frame,
    ): Boolean {
        // Colour follows the smoothed stream; the number shown is the raw one, so the field
        // still reacts instantly while the colour stays calm.
        val band = if (SIMULATE_BANDS) {
            PaceBands.Band.entries[((System.currentTimeMillis() / 2000) % PaceBands.Band.entries.size).toInt()]
        } else {
            PaceBands.bandFor(frame.smoothedMps, frame.averageMps)
        }
        val display = PaceBands.toDisplayUnits(frame.speedMps ?: 0.0, frame.imperial)

        val result = glance.compose(context, DpSize.Unspecified) {
            PaceView(context, config, band, display)
        }
        return try {
            emitter.updateView(result.remoteViews)
            true
        } catch (e: RemoteException) {
            Log.w(TAG, "host binder dead, stopping view updates", e)
            false
        }
    }

    companion object {
        /**
         * Desk-test switch: cycles the band every 2s so every colour, text colour and chevron
         * can be seen without riding. Must be false in anything that goes on the bike.
         */
        const val SIMULATE_BANDS = false

        const val TYPE_ID = "speed-vs-average"
        private const val TAG = "PaceDataType"
    }
}
