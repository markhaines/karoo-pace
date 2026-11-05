package com.hainesy.karoopace

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Flow wrappers over the karoo-ext consumer API, which is callback-based. Registration survives
 * reconnects, so the only teardown needed is removeConsumer on cancellation. Both are conflated
 * (see [ViewPipeline.consumerFlow]): only the newest reading matters, never a backlog.
 */
fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> =
    ViewPipeline.consumerFlow<StreamState>(
        register = { onValue ->
            addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                onValue(event.state)
            }
        },
        unregister = { id -> removeConsumer(id) },
    )

fun KarooSystemService.streamUserProfile(): Flow<UserProfile> =
    ViewPipeline.consumerFlow<UserProfile>(
        register = { onValue -> addConsumer<UserProfile> { profile -> onValue(profile) } },
        unregister = { id -> removeConsumer(id) },
    )

/** Streaming value, or null for Idle/Searching/NotAvailable — all of which mean "no reading". */
val StreamState.value: Double?
    get() = (this as? StreamState.Streaming)?.dataPoint?.singleValue
