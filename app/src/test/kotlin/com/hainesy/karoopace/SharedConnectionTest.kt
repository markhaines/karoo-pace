package com.hainesy.karoopace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The process-wide Karoo connection lifecycle, with a fake standing in for KarooSystemService. */
class SharedConnectionTest {

    private class FakeService(val id: Int)

    private var created = 0
    private val connected = mutableListOf<Int>()
    private val disconnected = mutableListOf<Int>()

    private val connection = SharedConnection<FakeService>(
        connect = { connected += it.id },
        disconnect = { disconnected += it.id },
    )

    private fun get() = connection.get { FakeService(++created) }

    @Test
    fun `first use creates and connects once, later uses share it`() {
        val a = get()
        val b = get()
        assertSame(a, b)
        assertEquals(1, created)
        assertEquals(listOf(1), connected)
        assertTrue(connection.isConnected)
    }

    @Test
    fun `extension teardown disconnects exactly once`() {
        get()
        connection.shutdown()
        connection.shutdown()
        assertEquals(listOf(1), disconnected)
        assertFalse(connection.isConnected)
    }

    @Test
    fun `teardown before any use does nothing`() {
        connection.shutdown()
        assertEquals(0, created)
        assertEquals(emptyList<Int>(), disconnected)
    }

    @Test
    fun `use after teardown gets a fresh connection`() {
        val first = get()
        connection.shutdown()
        val second = get()
        assertNotSame(first, second)
        assertEquals(listOf(1, 2), connected)
        assertEquals(listOf(1), disconnected)
    }
}
