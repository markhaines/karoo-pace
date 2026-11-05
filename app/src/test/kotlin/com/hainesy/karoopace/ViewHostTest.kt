package com.hainesy.karoopace

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extension-level lifecycle: several views of the data type at once, extension teardown, and an
 * unexpected failure in one view. Fake consumers stand in for the Karoo, as in ViewPipelineTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewHostTest {

    /** A fake karoo-ext consumer registry for one stream, tracking every add and remove. */
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

    /** One view's four streams, combined the way the data type combines them. */
    private class FakeView(name: String) {
        val sources = List(4) { FakeSource("$name-stream$it") }
        val frames = combine(sources.map { it.flow }) { it.toList() }
        val drawn = mutableListOf<List<Int>>()

        fun push(value: Int) = sources.forEach { it.push(value) }
        val allLive: Boolean get() = sources.all { it.live }
        val allUnregistered: Boolean
            get() = sources.all { it.registered.isNotEmpty() && it.registered == it.unregistered }
    }

    private fun TestScope.host(errors: MutableList<Throwable> = mutableListOf()) =
        ViewHost(StandardTestDispatcher(testScheduler)) { errors += it }

    @Test
    fun `extension teardown stops every view and unregisters every consumer`() = runTest {
        val host = host()
        val views = List(3) { FakeView("view$it") }
        var configCancelled = 0
        val jobs = views.map { view ->
            host.launchView(
                configure = {
                    try {
                        awaitCancellation()
                    } finally {
                        configCancelled++
                    }
                },
                frames = view.frames,
            ) { frame -> view.drawn += frame; true }
        }
        runCurrent()
        views.forEach { it.push(1) }
        runCurrent()
        assertTrue(views.all { it.allLive && it.drawn.size == 1 })

        host.shutdown()
        runCurrent()

        assertTrue(host.isShutDown)
        assertTrue(jobs.all { it.isCancelled })
        assertEquals("config work of every view cancelled", 3, configCancelled)
        views.forEach { assertTrue("${it.sources.map { s -> s.unregistered }}", it.allUnregistered) }

        // Telemetry after teardown goes nowhere.
        views.forEach { it.push(2) }
        advanceTimeBy(ViewPipeline.RENDER_WINDOW_MS * 3)
        assertTrue(views.all { it.drawn.size == 1 })
    }

    @Test
    fun `a view started after teardown never registers a consumer`() = runTest {
        val host = host()
        host.shutdown()
        val view = FakeView("late")
        var configRan = false
        val job = host.launchView(configure = { configRan = true }, frames = view.frames) { true }
        runCurrent()
        assertTrue(job.isCancelled)
        assertFalse(configRan)
        assertTrue(view.sources.all { it.registered.isEmpty() })
    }

    @Test
    fun `binder death in one view leaves the others running`() = runTest {
        val host = host()
        val dying = FakeView("dying")
        val healthy = FakeView("healthy")
        val dyingJob = host.launchView(configure = {}, frames = dying.frames) { false }
        val healthyJob = host.launchView(configure = {}, frames = healthy.frames) { f -> healthy.drawn += f; true }
        runCurrent()
        dying.push(1)
        healthy.push(1)
        runCurrent()

        assertTrue(dyingJob.isCompleted)
        assertTrue(dying.allUnregistered)
        assertTrue(healthyJob.isActive)
        assertTrue(healthy.allLive)

        healthy.push(2)
        advanceTimeBy(ViewPipeline.RENDER_WINDOW_MS + 1)
        assertEquals(listOf(listOf(1, 1, 1, 1), listOf(2, 2, 2, 2)), healthy.drawn)
    }

    @Test
    fun `an unexpected error is reported, ends only its own view and unregisters it`() = runTest {
        val errors = mutableListOf<Throwable>()
        val host = host(errors)
        val broken = FakeView("broken")
        val healthy = FakeView("healthy")
        host.launchView(configure = {}, frames = broken.frames) { error("render blew up") }
        val healthyJob = host.launchView(configure = {}, frames = healthy.frames) { f -> healthy.drawn += f; true }
        runCurrent()
        broken.push(1)
        healthy.push(1)
        runCurrent()

        assertEquals(listOf("render blew up"), errors.map { it.message })
        assertTrue(broken.allUnregistered)
        assertTrue(healthyJob.isActive)
        assertEquals(1, healthy.drawn.size)
        assertFalse(host.isShutDown)
    }

    @Test
    fun `an error in configuration is reported and stops that view`() = runTest {
        val errors = mutableListOf<Throwable>()
        val host = host(errors)
        val view = FakeView("v")
        val job = host.launchView(configure = { error("config blew up") }, frames = view.frames) { true }
        runCurrent()
        assertEquals(listOf("config blew up"), errors.map { it.message })
        assertTrue(job.isCancelled)
        assertTrue(view.sources.all { it.registered == it.unregistered })
    }
}
