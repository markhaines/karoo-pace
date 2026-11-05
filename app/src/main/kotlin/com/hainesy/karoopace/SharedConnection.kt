package com.hainesy.karoopace

/**
 * One shared connection for the process, created and connected on first use and disconnected
 * once at teardown. Free of Android types so the lifecycle can be proven on the JVM; see
 * [PaceRuntime] for the KarooSystemService it holds.
 */
class SharedConnection<T : Any>(
    private val connect: (T) -> Unit,
    private val disconnect: (T) -> Unit,
) {
    private var current: T? = null

    val isConnected: Boolean
        @Synchronized get() = current != null

    /** The live connection, creating and connecting one with [create] if there is none. */
    @Synchronized
    fun get(create: () -> T): T = current ?: create().also {
        connect(it)
        current = it
    }

    /** Disconnects the live connection, if any. Safe to call more than once. */
    @Synchronized
    fun shutdown() {
        val live = current ?: return
        current = null
        disconnect(live)
    }
}
