package com.ably.chat

import com.ably.annotations.InternalAPI
import com.ably.chat.annotations.InternalChatApi
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.realtime.Channel
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * This interface is used to interact with occupancy in a chat room: subscribing to occupancy updates and
 * fetching the current room occupancy metrics.
 *
 * Get an instance via [Room.occupancy].
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface Occupancy {

    /**
     * Subscribe a given listener to occupancy updates of the chat room.
     *
     * @param listener A listener to be called when the occupancy of the room changes.
     */
    public fun subscribe(listener: OccupancyListener): Subscription

    /**
     * Get the current occupancy of the chat room.
     *
     * @return the current occupancy of the chat room.
     */
    public suspend fun get(): OccupancyData

    /**
     * Get the latest occupancy data received from realtime events.
     *
     * @return The latest occupancy data, or undefined if no realtime events have been received yet.
     * @throws [ChatException] If occupancy events are not enabled for this room.
     */
    public fun current(): OccupancyData?
}

/**
 * A listener that is called when the occupancy of a chat room changes.
 */
public typealias OccupancyListener = (event: OccupancyEvent) -> Unit

/**
 * @return [OccupancyEvent] events as a [Flow]
 */
public fun Occupancy.asFlow(): Flow<OccupancyEvent> = transformCallbackAsFlow {
    subscribe(it)
}

/**
 * Represents the occupancy event.
 *
 * (CHA-O2)
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface OccupancyEvent {
    /**
     * Defines the type of the occupancy event, represented by an instance of [OccupancyEventType].
     */
    public val type: OccupancyEventType

    /**
     * Represents data about the occupancy of a chat room, including connection and presence details.
     *
     * Provides information such as the number of active connections and the number of presence members in the chat room.
     */
    public val occupancy: OccupancyData
}

public enum class OccupancyEventType(public val eventName: String) {
    Updated("occupancy.updated"),
}

/**
 * Represents the occupancy of a chat room.
 *
 * (CHA-O2)
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface OccupancyData {
    /**
     * The number of connections to the chat room.
     */
    public val connections: Int

    /**
     * The number of presence members in the chat room - members who have entered presence.
     */
    public val presenceMembers: Int
}

internal data class DefaultOccupancyData(
    override val connections: Int,
    override val presenceMembers: Int,
) : OccupancyData

@InternalChatApi
public val EmptyOccupancyData: OccupancyData = DefaultOccupancyData(0, 0)

internal data class DefaultOccupancyEvent(
    override val occupancy: DefaultOccupancyData,
    override val type: OccupancyEventType = OccupancyEventType.Updated,
) : OccupancyEvent

private const val META_OCCUPANCY_EVENT_NAME = "[meta]occupancy"

internal class DefaultOccupancy(
    private val room: DefaultRoom,
) : Occupancy, RoomFeature {

    override val featureName: String = "occupancy"

    private val logger = room.logger.withContext(tag = "Occupancy")

    val channelWrapper: RealtimeChannel = room.channel

    @OptIn(InternalAPI::class)
    internal val channel: Channel = channelWrapper.javaChannel

    private var latestOccupancyData: OccupancyData? = null

    private val listeners: MutableList<OccupancyListener> = CopyOnWriteArrayList()

    private val eventBus = MutableSharedFlow<OccupancyEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val occupancyScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private val occupancySubscription: Subscription

    init {
        occupancyScope.launch {
            eventBus.collect { occupancyEvent ->
                listeners.forEach {
                    it.invoke(occupancyEvent)
                }
            }
        }

        val occupancyListener = PubSubMessageListener {
            internalChannelListener(it)
        }

        occupancySubscription = channelWrapper.subscribe(META_OCCUPANCY_EVENT_NAME, occupancyListener).asChatSubscription()
    }

    // Spec: CHA-O4
    override fun subscribe(listener: OccupancyListener): Subscription {
        logger.trace("Occupancy.subscribe()")
        if (!room.options.occupancy.enableEvents) { // CHA-O4e
            throw clientError("cannot subscribe to occupancy; occupancy events are not enabled in room options")
        }

        listeners.add(listener)
        return Subscription {
            logger.trace("Occupancy.unsubscribe()")
            // (CHA-04b)
            listeners.remove(listener)
        }
    }

    // (CHA-O3)
    override suspend fun get(): OccupancyData {
        logger.trace("Occupancy.get()")
        return room.chatApi.getOccupancy(room.name)
    }

    override fun current(): OccupancyData? {
        logger.trace("Occupancy.current()")
        if (!room.options.occupancy.enableEvents) { // CHA-O7c
            throw clientError("cannot get current occupancy; occupancy events are not enabled in room options")
        }
        // CHA-O7a
        // CHA-O7b
        return latestOccupancyData
    }

    override fun dispose() {
        occupancySubscription.unsubscribe()
        occupancyScope.cancel()
    }

    /**
     * An internal listener that listens for occupancy events from the underlying channel and translates them into
     * occupancy events for the public API.
     */
    private fun internalChannelListener(message: PubSubMessage) {
        val data = message.data?.tryAsJsonValue()

        if (data == null) {
            logger.error(
                "invalid occupancy event received; data is not an object",
                context = mapOf(
                    "message" to message,
                ),
            )
            // (CHA-04d)
            return
        }

        val metrics = data.jsonObjectOrNull()?.get("metrics")

        if (metrics == null) {
            logger.error(
                "invalid occupancy event received; metrics is missing",
                context = mapOf(
                    "data" to data,
                ),
            )
            // (CHA-04d)
            return
        }

        val connections = metrics.jsonObjectOrNull()?.get("connections")?.intOrNull()

        if (connections == null) {
            logger.error(
                "invalid occupancy event received; connections is missing",
                context = mapOf(
                    "data" to data,
                ),
            )
            // (CHA-04d)
            return
        }

        val presenceMembers = metrics.jsonObjectOrNull()?.get("presenceMembers")?.intOrNull()

        if (presenceMembers == null) {
            logger.error(
                "invalid occupancy event received; presenceMembers is missing",
                context = mapOf(
                    "data" to data,
                ),
            )
            // (CHA-04d)
            return
        }

        val occupancyData = DefaultOccupancyData(
            connections = connections,
            presenceMembers = presenceMembers,
        )

        latestOccupancyData = occupancyData

        eventBus.tryEmit(
            // (CHA-04c)
            DefaultOccupancyEvent(occupancyData),
        )
    }
}
