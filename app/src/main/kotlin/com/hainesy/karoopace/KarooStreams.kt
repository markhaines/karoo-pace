package com.hainesy.karoopace

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Flow wrappers over the karoo-ext consumer API, which is callback-based. Registration survives
 * reconnects, so the only teardown needed is removeConsumer on cancellation.
 */
fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val consumerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
        trySend(event.state)
    }
    awaitClose { removeConsumer(consumerId) }
}

fun KarooSystemService.streamUserProfile(): Flow<UserProfile> = callbackFlow {
    val consumerId = addConsumer<UserProfile> { profile -> trySend(profile) }
    awaitClose { removeConsumer(consumerId) }
}

/** Streaming value, or null for Idle/Searching/NotAvailable — all of which mean "no reading". */
val StreamState.value: Double?
    get() = (this as? StreamState.Streaming)?.dataPoint?.singleValue
