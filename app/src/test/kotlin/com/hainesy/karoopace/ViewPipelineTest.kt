package com.hainesy.karoopace

import com.hainesy.karoopace.ViewPipeline.RENDER_WINDOW_MS
import com.hainesy.karoopace.ViewPipeline.launchView
import com.hainesy.karoopace.ViewPipeline.renderThrottled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The view pipeline on virtual time: bursty fake consumers stand in for the Karoo, and a
 * recording renderer stands in for the ViewEmitter, failing on demand the way a dead binder does.
 * No Android, no device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewPipelineTest {

    /** A fake karoo-ext consumer registry for one stream. */
    private class FakeSource(private val name: String) {
        private var callback: ((Int) -> Unit)? = null
        val registered = mutableListOf<String>()
        val unregistered = mutableListOf<String>()

        val flow = ViewPipeline.consumerFlow<Int>(
            register = { cb ->
                callback = cb
                "$name-${registered.size}".also { registered += it }
            },
            unregister = { id ->
                unregistered += id
                callback = null
            },
        )

        val live: Boolean get() = callback != null

        fun push(value: Int) {
            callback?.invoke(value)
        }
    }

    /** Records what was drawn and when; returns false (dead binder) from call [failOn] on. */
    private class FakeEmitter(private val scope: TestScope, private val failOn: Int = Int.MAX_VALUE) {
        val drawn = mutableListOf<Pair<Long, Any?>>()
        var calls = 0

        fun render(frame: Any?): Boolean {
            calls++
            if (calls >= failOn) return false
            drawn += scope.currentTime to frame
            return true
        }
    }

    @Test
    fun `a burst inside one window draws once, then the latest frame when the window ends`() = runTest {
        val source = FakeSource("speed")
        val emitter = FakeEmitter(this)
        backgroundScope.launch { source.flow.renderThrottled(RENDER_WINDOW_MS, emitter::render) }
        runCurrent()

        source.push(0)
        runCurrent()
        (1..50).forEach { source.push(it) } // all inside the first window
        runCurrent()
        assertEquals(listOf(0L to 0), emitter.drawn)

        advanceTimeBy(RENDER_WINDOW_MS + 1)
        assertEquals(listOf(0L to 0, RENDER_WINDOW_MS to 50), emitter.drawn)
    }

    @Test
    fun `a steady 10Hz stream is drawn at most once per window and ends on the newest frame`() = runTest {
        val source = FakeSource("speed")
        val emitter = FakeEmitter(this)
        backgroundScope.launch { source.flow.renderThrottled(RENDER_WINDOW_MS, emitter::render) }
        runCurrent()

        repeat(53) { i ->
            source.push(i)
            advanceTimeBy(100)
        }
        advanceTimeBy(RENDER_WINDOW_MS * 2)

        val times = emitter.drawn.map { it.first }
        times.zipWithNext().forEach { (a, b) ->
            assertTrue("renders $a and $b are closer than the window", b - a >= RENDER_WINDOW_MS)
        }
        assertTrue("drew ${times.size} frames for 5.3s of input", times.size <= 7)
        assertEquals("latest frame is eventually drawn", 52, emitter.drawn.last().second)
    }

    @Test
    fun `a single isolated update is drawn immediately`() = runTest {
        val source = FakeSource("speed")
        val emitter = FakeEmitter(this)
        backgroundScope.launch { source.flow.renderThrottled(RENDER_WINDOW_MS, emitter::render) }
        advanceTimeBy(10_000)
        source.push(7)
        runCurrent()
        assertEquals(listOf(10_000L to 7), emitter.drawn)
    }

    @Test
    fun `source flows are conflated so a slow collector never sees a backlog`() = runTest {
        val source = FakeSource("speed")
        val seen = mutableListOf<Int>()
        val job = launch { source.flow.onEach { delay(1000) }.toList(seen) }
        runCurrent()
        (0..99).forEach { source.push(it) }
        advanceTimeBy(5_000)
        job.cancel()
        runCurrent()
        assertTrue("saw $seen", seen.size <= 2)
        assertEquals(99, seen.last())
        assertEquals(source.registered, source.unregistered)
    }

    /** Four sources combined, exactly as the data type combines its four Karoo streams. */
    private fun fourSources() = List(4) { FakeSource("stream$it") }

    private fun framesOf(sources: List<FakeSource>) =
        combine(sources.map { it.flow }) { it.toList() }

    @Test
    fun `binder death stops collection and unregisters every consumer`() = runTest {
        val sources = fourSources()
        val emitter = FakeEmitter(this, failOn = 2)
        val job = backgroundScope.launchView(configure = {}, frames = framesOf(sources)) { emitter.render(it) }
        runCurrent()
        assertTrue(sources.all { it.live })

        sources.forEach { it.push(1) }
        runCurrent()
        assertEquals(1, emitter.drawn.size)

        sources.forEach { it.push(2) }
        advanceTimeBy(RENDER_WINDOW_MS + 1) // second render hits the dead binder
        assertEquals(2, emitter.calls)

        assertTrue("view job should have finished", job.isCompleted)
        sources.forEach {
            assertEquals("${it.registered} should all be unregistered", it.registered, it.unregistered)
            assertTrue(!it.live)
        }

        // Telemetry after death goes nowhere: no render attempts into the dead emitter.
        repeat(10) { i ->
            sources.forEach { it.push(3 + i) }
            advanceTimeBy(RENDER_WINDOW_MS)
        }
        assertEquals(2, emitter.calls)
    }

    @Test
    fun `binder dead on the very first frame never retries`() = runTest {
        val sources = fourSources()
        val emitter = FakeEmitter(this, failOn = 1)
        val job = backgroundScope.launchView(configure = {}, frames = framesOf(sources)) { emitter.render(it) }
        runCurrent()
        repeat(5) { i ->
            sources.forEach { it.push(i) }
            advanceTimeBy(RENDER_WINDOW_MS)
        }
        assertEquals(1, emitter.calls)
        assertTrue(job.isCompleted)
        sources.forEach { assertEquals(it.registered, it.unregistered) }
    }

    @Test
    fun `emitter cancel stops configuration work and rendering, and unregisters consumers`() = runTest {
        val sources = fourSources()
        val emitter = FakeEmitter(this)
        var configStarted = false
        var configCancelled = false
        val job = backgroundScope.launchView(
            configure = {
                configStarted = true
                try {
                    awaitCancellation()
                } catch (e: CancellationException) {
                    configCancelled = true
                    throw e
                }
            },
            frames = framesOf(sources),
        ) { emitter.render(it) }
        runCurrent()
        assertTrue(configStarted)

        sources.forEach { it.push(1) }
        runCurrent()
        assertEquals(1, emitter.drawn.size)

        job.cancel() // what emitter.setCancellable runs
        runCurrent()

        assertTrue("configuration work should be cancelled", configCancelled)
        assertTrue(job.isCancelled)
        sources.forEach { assertEquals(it.registered, it.unregistered) }

        sources.forEach { it.push(2) }
        advanceUntilIdle()
        assertEquals(1, emitter.calls)
    }

    @Test
    fun `binder death does not cut short configuration already in flight`() = runTest {
        val sources = fourSources()
        val emitter = FakeEmitter(this, failOn = 1)
        var configDone = false
        backgroundScope.launchView(
            configure = {
                delay(500)
                configDone = true
            },
            frames = framesOf(sources),
        ) { emitter.render(it) }
        runCurrent()
        sources.forEach { it.push(1) }
        advanceTimeBy(1_000)
        assertTrue(configDone)
        sources.forEach { assertEquals(it.registered, it.unregistered) }
    }
}
