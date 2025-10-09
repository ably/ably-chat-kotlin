package com.ably.chat

import com.ably.annotations.InternalAPI
import com.ably.chat.json.JsonObject
import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimePresence
import io.ably.lib.realtime.Channel
import kotlinx.coroutines.flow.Flow
import io.ably.lib.realtime.Presence.PresenceListener as PubSubPresenceListener

/**
 * This interface is used to interact with presence in a chat room: subscribing to presence events,
 * fetching presence members, or sending presence events (join,update,leave).
 *
 * Get an instance via [Room.presence].
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface Presence {

    /**
     *  Method to get list of the current online users and returns the latest presence messages associated to it.
     *  @param waitForSync when false, the current list of members is returned without waiting for a complete synchronization.
     *  @param clientId when provided, will filter array of members returned that match the provided `clientId` string.
     *  @param connectionId when provided, will filter array of members returned that match the provided `connectionId`.
     *  @throws [ChatException] object which explains the error.
     *  @return list of the current online users
     */
    public suspend fun get(waitForSync: Boolean = true, clientId: String? = null, connectionId: String? = null): List<PresenceMember>

    /**
     * Method to check if user with supplied clientId is online
     * @param {string} clientId - The client ID to check if it is present in the room.
     * @returns true if user with specified clientId is present, false otherwise
     */
    public suspend fun isUserPresent(clientId: String): Boolean

    /**
     * Method to join room presence, will emit an enter event to all subscribers. Repeat calls will trigger more enter events.
     * @param data The users data, a JSON serializable object that will be sent to all subscribers.
     * @throws [ChatException] object which explains the error.
     */
    public suspend fun enter(data: JsonObject? = null)

    /**
     * Method to update room presence, will emit an update event to all subscribers. If the user is not present, it will be treated as a join event.
     * @param data The users data, a JSON serializable object that will be sent to all subscribers.
     * @throws [ChatException] object which explains the error.
     */
    public suspend fun update(data: JsonObject? = null)

    /**
     * Method to leave room presence, will emit a leave event to all subscribers. If the user is not present, it will be treated as a no-op.
     * @param data The users data, a JSON serializable object that will be sent to all subscribers.
     * @throws [ChatException] object which explains the error.
     */
    public suspend fun leave(data: JsonObject? = null)

    /**
     * Subscribe the given listener to all presence events.
     * @param listener listener to subscribe
     */
    public fun subscribe(listener: PresenceListener): Subscription
}

/**
 * Type for PresenceListener
 */
public typealias PresenceListener = (PresenceEvent) -> Unit

/**
 * @return [PresenceEvent] events as a [Flow]
 */
public fun Presence.asFlow(): Flow<PresenceEvent> = transformCallbackAsFlow {
    subscribe(it)
}

/**
 * Type for PresenceMember
 */
public interface PresenceMember {
    /**
     * The clientId of the presence member.
     */
    public val clientId: String

    /**
     * The ID of the connection associated with the client that published the `PresenceMessage`.
     */
    public val connectionId: String

    /**
     * The data associated with the presence member.
     */
    public val data: JsonObject?

    /**
     * The timestamp of when the last change in state occurred for this presence member.
     */
    public val updatedAt: Long

    /**
     * The extras associated with the presence member.
     */
    public val extras: JsonObject
}

public interface PresenceEvent {
    public val type: PresenceEventType
    public val member: PresenceMember
}

internal data class DefaultPresenceMember(
    override val clientId: String,
    override val connectionId: String,
    override val data: JsonObject?,
    override val updatedAt: Long,
    override val extras: JsonObject = JsonObject(),
) : PresenceMember

internal data class DefaultPresenceEvent(
    override val type: PresenceEventType,
    override val member: PresenceMember,
) : PresenceEvent

internal class DefaultPresence(
    private val room: DefaultRoom,
) : Presence, RoomFeature {

    override val featureName = "presence"

    private val channelWrapper: RealtimeChannel = room.channel

    @OptIn(InternalAPI::class)
    internal val channel: Channel = channelWrapper.javaChannel

    private val logger = room.logger.withContext(tag = "Presence")

    private val presence: RealtimePresence = channelWrapper.presence

    override suspend fun get(waitForSync: Boolean, clientId: String?, connectionId: String?): List<PresenceMember> {
        room.ensureAttached(logger) // CHA-PR6d, CHA-PR6c, CHA-PR6h
        return presence.getCoroutine(waitForSync, clientId, connectionId).map { user ->
            DefaultPresenceMember(
                clientId = user.clientId,
                connectionId = user.connectionId,
                data = user.data.tryAsJsonValue()?.jsonObjectOrNull(),
                updatedAt = user.timestamp,
            )
        }
    }

    override suspend fun isUserPresent(clientId: String): Boolean = presence.getCoroutine(clientId = clientId).isNotEmpty()

    override suspend fun enter(data: JsonObject?) {
        room.ensureAttached(logger) // CHA-PR3e, CHA-PR3d, CHA-PR3h
        presence.enterClientCoroutine(room.clientId, data)
    }

    override suspend fun update(data: JsonObject?) {
        room.ensureAttached(logger) // CHA-PR10e, CHA-PR10d, CHA-PR10h
        presence.updateClientCoroutine(room.clientId, data)
    }

    override suspend fun leave(data: JsonObject?) {
        room.ensureAttached(logger) // CHA-PR4d, CHA-PR4b, CHA-PR4c
        presence.leaveClientCoroutine(room.clientId, data)
    }

    override fun subscribe(listener: PresenceListener): Subscription {
        logger.trace("Presence.subscribe()")
        // CHA-PR7d - Check if presence events are enabled
        if (!room.options.presence.enableEvents) {
            throw clientError("could not subscribe to presence; presence events are not enabled in room options")
        }

        val presenceListener = PubSubPresenceListener {
            val presenceMember = DefaultPresenceMember(
                clientId = it.clientId,
                connectionId = it.connectionId,
                updatedAt = it.timestamp,
                data = it.data.tryAsJsonValue()?.jsonObjectOrNull(),
            )
            val presenceEvent = DefaultPresenceEvent(
                type = PresenceEventType.fromPresenceAction(it.action),
                member = presenceMember,
            )
            listener.invoke(presenceEvent)
        }

        return presence.subscribe(presenceListener).asChatSubscription()
    }

    override fun dispose() {
        // No need to do anything, since it uses same channel as messages
    }
}
