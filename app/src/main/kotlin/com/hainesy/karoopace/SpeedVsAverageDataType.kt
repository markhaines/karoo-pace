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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class SpeedVsAverageDataType(extension: String) : DataTypeImpl(extension, TYPE_ID) {

    private val glance = GlanceRemoteViews()

    // Unexpected errors are logged and end only the view they came from; an uncaught exception
    // here would otherwise take down the whole extension process.
    private val views = ViewHost(Dispatchers.IO) { e -> Log.e(TAG, "view failed", e) }

    /** Extension teardown: stops every view of this type and unregisters their consumers. */
    fun shutdown() = views.shutdown()

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // After teardown, start nothing: PaceRuntime.system would otherwise bind a fresh
        // KarooSystemService that nothing disconnects until the next teardown.
        if (views.isShutDown) return

        val karoo = PaceRuntime.system(context)

        val speed = karoo.streamDataFlow(DataType.Type.SPEED)
        val smoothed = karoo.streamDataFlow(DataType.Type.SMOOTHED_3S_AVERAGE_SPEED)
        val average = karoo.streamDataFlow(DataType.Type.AVERAGE_SPEED)
        val profile = if (config.preview) flowOf(null) else karoo.streamUserProfile()
        val frames = combine(speed, smoothed, average, profile) { s, sm, avg, prof ->
            Frame(s.value, sm.value, avg.value, prof.isImperial())
        }

        // Last band shown on this view, for PaceBands hysteresis. Renders are sequential, so a
        // plain local is safe.
        var shown = PaceBands.Band.NEUTRAL

        // Config and rendering share one job, so the emitter's cancel stops both. Rendering is
        // throttled to the emitter's 1Hz window, latest frame wins. A dead host binder, seen by
        // either config or render, ends the view for good and unregisters all four consumers.
        val job = views.launchView(
            configure = {
                // Draw the whole field ourselves. Asking the host for a header costs more
                // vertical space than drawing our own, and asking it to render the number puts
                // its digits underneath our background rather than on top of it.
                try {
                    emitter.onNext(UpdateGraphicConfig(showHeader = false))
                    emitter.onNext(UpdateNumericConfig(DataType.Type.SPEED))
                    true
                } catch (e: RemoteException) {
                    Log.w(TAG, "host binder dead before the view was configured, stopping", e)
                    false
                }
            },
            frames = frames,
        ) { frame ->
            val band = render(context, config, emitter, frame, shown) ?: return@launchView false
            shown = band
            true
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

    /**
     * Returns the band drawn, or null once the host-side binder is gone, which is the only
     * teardown signal.
     */
    private suspend fun render(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
        frame: Frame,
        previous: PaceBands.Band,
    ): PaceBands.Band? {
        // Colour follows the smoothed stream; the number shown is the raw one, so the field
        // still reacts instantly while the colour stays calm. BuildConfig.SIMULATE_BANDS is the
        // desk-test switch: debug builds only (-PsimulateBands=true), always false in release.
        val band = if (BuildConfig.SIMULATE_BANDS) {
            PaceBands.Band.entries[((System.currentTimeMillis() / 2000) % PaceBands.Band.entries.size).toInt()]
        } else {
            PaceBands.bandFor(frame.smoothedMps, frame.averageMps, previous)
        }
        val text = PaceBands.speedText(frame.speedMps, frame.imperial)

        val result = glance.compose(context, DpSize.Unspecified) {
            PaceView(context, config, band, text)
        }
        return try {
            emitter.updateView(result.remoteViews)
            band
        } catch (e: RemoteException) {
            Log.w(TAG, "host binder dead, stopping view updates", e)
            null
        }
    }

    companion object {
        const val TYPE_ID = "speed-vs-average"
        private const val TAG = "PaceDataType"
    }
}
