@file:Suppress("StringLiteralDuplication")

package com.ably.chat

import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimePresence
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ably.lib.realtime.Channel
import io.ably.lib.types.PresenceMessage
import io.ably.lib.realtime.Presence.PresenceListener as PubSubPresenceListener

typealias PresenceData = JsonElement

/**
 * This interface is used to interact with presence in a chat room: subscribing to presence events,
 * fetching presence members, or sending presence events (join,update,leave).
 *
 * Get an instance via [Room.presence].
 */
interface Presence : EmitsDiscontinuities {
    /**
     * Get the underlying Ably realtime channel used for presence in this chat room.
     * @returns The realtime channel.
     */
    val channel: Channel

    /**
     *  Method to get list of the current online users and returns the latest presence messages associated to it.
     *  @param waitForSync when false, the current list of members is returned without waiting for a complete synchronization.
     *  @param clientId when provided, will filter array of members returned that match the provided `clientId` string.
     *  @param connectionId when provided, will filter array of members returned that match the provided `connectionId`.
     *  @throws [io.ably.lib.types.AblyException] object which explains the error.
     *  @return list of the current online users
     */
    suspend fun get(waitForSync: Boolean = true, clientId: String? = null, connectionId: String? = null): List<PresenceMember>

    /**
     * Method to check if user with supplied clientId is online
     * @param {string} clientId - The client ID to check if it is present in the room.
     * @returns true if user with specified clientId is present, false otherwise
     */
    suspend fun isUserPresent(clientId: String): Boolean

    /**
     * Method to join room presence, will emit an enter event to all subscribers. Repeat calls will trigger more enter events.
     * @param data The users data, a JSON serializable object that will be sent to all subscribers.
     * @throws [io.ably.lib.types.AblyException] object which explains the error.
     */
    suspend fun enter(data: PresenceData? = null)

    /**
     * Method to update room presence, will emit an update event to all subscribers. If the user is not present, it will be treated as a join event.
     * @param data The users data, a JSON serializable object that will be sent to all subscribers.
     * @throws [io.ably.lib.types.AblyException] object which explains the error.
     */
    suspend fun update(data: PresenceData? = null)

    /**
     * Method to leave room presence, will emit a leave event to all subscribers. If the user is not present, it will be treated as a no-op.
     * @param data The users data, a JSON serializable object that will be sent to all subscribers.
     * @throws [io.ably.lib.types.AblyException] object which explains the error.
     */
    suspend fun leave(data: PresenceData? = null)

    /**
     * Subscribe the given listener to all presence events.
     * @param listener listener to subscribe
     */
    fun subscribe(listener: Listener): Subscription

    /**
     * An interface for listening to new presence event
     */
    fun interface Listener {
        /**
         * A function that can be called when the new presence event happens.
         * @param event The event that happened.
         */
        fun onEvent(event: PresenceEvent)
    }
}

/**
 * Type for PresenceMember
 */
data class PresenceMember(
    /**
     * The clientId of the presence member.
     */
    val clientId: String,

    /**
     * The data associated with the presence member.
     */
    val data: PresenceData?,

    /**
     * The current state of the presence member.
     */
    val action: PresenceMessage.Action,

    /**
     * The timestamp of when the last change in state occurred for this presence member.
     */
    val updatedAt: Long,

    /**
     * The extras associated with the presence member.
     */
    val extras: Map<String, String>? = null,
)

/**
 * Type for PresenceEvent
 */
data class PresenceEvent(
    /**
     * The type of the presence event.
     */
    val action: PresenceMessage.Action,

    /**
     * The clientId of the client that triggered the presence event.
     */
    val clientId: String,

    /**
     * The timestamp of the presence event.
     */
    val timestamp: Long,

    /**
     * The data associated with the presence event.
     */
    val data: PresenceData?,
)

internal class DefaultPresence(
    private val room: DefaultRoom,
) : Presence, ContributesToRoomLifecycleImpl(room.logger) {

    override val featureName = "presence"

    override val attachmentErrorCode: ErrorCode = ErrorCode.PresenceAttachmentFailed

    override val detachmentErrorCode: ErrorCode = ErrorCode.PresenceDetachmentFailed

    override val channel: Channel = room.messages.channel

    override val channelWrapper: RealtimeChannel = room.messages.channelWrapper

    private val logger = room.logger.withContext(tag = "Presence")

    private val presence: RealtimePresence = channelWrapper.presence

    override suspend fun get(waitForSync: Boolean, clientId: String?, connectionId: String?): List<PresenceMember> {
        room.ensureAttached(logger) // CHA-PR6d, CHA-PR6c, CHA-PR6h
        return presence.getCoroutine(waitForSync, clientId, connectionId).map { user ->
            PresenceMember(
                clientId = user.clientId,
                action = user.action,
                data = (user.data as? JsonObject)?.get("userCustomData"),
                updatedAt = user.timestamp,
            )
        }
    }

    override suspend fun isUserPresent(clientId: String): Boolean = presence.getCoroutine(clientId = clientId).isNotEmpty()

    override suspend fun enter(data: PresenceData?) {
        room.ensureAttached(logger) // CHA-PR3e, CHA-PR3d, CHA-PR3h
        presence.enterClientCoroutine(room.clientId, wrapInUserCustomData(data))
    }

    override suspend fun update(data: PresenceData?) {
        room.ensureAttached(logger) // CHA-PR10e, CHA-PR10d, CHA-PR10h
        presence.updateClientCoroutine(room.clientId, wrapInUserCustomData(data))
    }

    override suspend fun leave(data: PresenceData?) {
        room.ensureAttached(logger) // CHA-PR4d, CHA-PR4b, CHA-PR4c
        presence.leaveClientCoroutine(room.clientId, wrapInUserCustomData(data))
    }

    override fun subscribe(listener: Presence.Listener): Subscription {
        val presenceListener = PubSubPresenceListener {
            val presenceEvent = PresenceEvent(
                action = it.action,
                clientId = it.clientId,
                timestamp = it.timestamp,
                data = (it.data as? JsonObject)?.get("userCustomData"),
            )
            listener.onEvent(presenceEvent)
        }

        return presence.subscribe(presenceListener).asChatSubscription()
    }

    private fun wrapInUserCustomData(data: PresenceData?) = data?.let {
        JsonObject().apply {
            add("userCustomData", data)
        }
    }

    override fun release() {
        // No need to do anything, since it uses same channel as messages
    }
}
