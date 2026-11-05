package com.hainesy.karoopace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/**
 * How a field's telemetry gets to the screen, kept free of Android and karoo-ext types so the
 * timing and teardown rules can be proven on the JVM.
 */
object ViewPipeline {

    /**
     * Minimum gap between view updates. karoo-ext's `ViewEmitter.updateView` "can only be called
     * at 1Hz": anything within 900ms of the last update is silently dropped. Waiting the full
     * second after each update keeps every frame we send clear of that.
     */
    const val RENDER_WINDOW_MS = 1000L

    /**
     * A callback-style consumer as a Flow. Conflated, so a slow collector only ever sees the
     * newest value and stale telemetry cannot queue up behind it. Cancelling the collection
     * always unregisters the consumer.
     *
     * @param register adds the consumer, delivering each value to the callback; returns its id.
     * @param unregister removes the consumer with that id.
     */
    fun <T> consumerFlow(
        register: (onValue: (T) -> Unit) -> String,
        unregister: (id: String) -> Unit,
    ): Flow<T> = callbackFlow {
        val id = register { value -> trySend(value) }
        awaitClose { unregister(id) }
    }.conflate()

    /**
     * Renders frames no more than once per [windowMs], always moving on to the newest frame:
     * whatever arrives during a window replaces anything older, and is drawn when the window
     * ends. Returns when [render] reports false (the host binder is gone), which cancels the
     * upstream and so unregisters every consumer feeding it.
     */
    suspend fun <T> Flow<T>.renderThrottled(windowMs: Long, render: suspend (T) -> Boolean) {
        conflate()
            .transformWhile<T, Unit> { frame ->
                val alive = render(frame)
                if (alive) delay(windowMs)
                alive
            }
            .collect()
    }

    /**
     * Starts a view's one-off configuration and its render loop under a single job, so one
     * cancel (from the emitter) stops both.
     */
    fun <T> CoroutineScope.launchView(
        configure: suspend () -> Unit,
        frames: Flow<T>,
        windowMs: Long = RENDER_WINDOW_MS,
        render: suspend (T) -> Boolean,
    ): Job = launch {
        launch { configure() }
        frames.renderThrottled(windowMs, render)
    }
}
