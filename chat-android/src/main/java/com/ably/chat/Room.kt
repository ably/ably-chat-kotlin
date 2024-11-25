@file:Suppress("StringLiteralDuplication", "NotImplementedDeclaration")

package com.ably.chat

import io.ably.lib.types.ErrorInfo
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Represents a chat room.
 */
interface Room {
    /**
     * The unique identifier of the room.
     * @returns The room identifier.
     */
    val roomId: String

    /**
     * Allows you to send, subscribe-to and query messages in the room.
     *
     * @returns The messages instance for the room.
     */
    val messages: Messages

    /**
     * Allows you to subscribe to presence events in the room.
     *
     * @throws {@link ErrorInfo}} if presence is not enabled for the room.
     * @returns The presence instance for the room.
     */
    val presence: Presence

    /**
     * Allows you to interact with room-level reactions.
     *
     * @throws {@link ErrorInfo} if reactions are not enabled for the room.
     * @returns The room reactions instance for the room.
     */
    val reactions: RoomReactions

    /**
     * Allows you to interact with typing events in the room.
     *
     * @throws {@link ErrorInfo} if typing is not enabled for the room.
     * @returns The typing instance for the room.
     */
    val typing: Typing

    /**
     * Allows you to interact with occupancy metrics for the room.
     *
     * @throws {@link ErrorInfo} if occupancy is not enabled for the room.
     * @returns The occupancy instance for the room.
     */
    val occupancy: Occupancy

    /**
     * Returns the room options.
     *
     * @returns A copy of the options used to create the room.
     */
    val options: RoomOptions

    /**
     * (CHA-RS2)
     * The current status of the room.
     *
     * @returns The current status.
     */
    val status: RoomStatus

    /**
     * The current error, if any, that caused the room to enter the current status.
     */
    val error: ErrorInfo?

    /**
     * Registers a listener that will be called whenever the room status changes.
     * @param listener The function to call when the status changes.
     * @returns An object that can be used to unregister the listener.
     */
    fun onStatusChange(listener: RoomLifecycle.Listener): Subscription

    /**
     * Removes all listeners that were added by the `onStatusChange` method.
     */
    fun offAllStatusChange()

    /**
     * Attaches to the room to receive events in realtime.
     *
     * If a room fails to attach, it will enter either the {@link RoomLifecycle.Suspended} or {@link RoomLifecycle.Failed} state.
     *
     * If the room enters the failed state, then it will not automatically retry attaching and intervention is required.
     *
     * If the room enters the suspended state, then the call to attach will reject with the {@link ErrorInfo} that caused the suspension. However,
     * the room will automatically retry attaching after a delay.
     */
    suspend fun attach()

    /**
     * Detaches from the room to stop receiving events in realtime.
     */
    suspend fun detach()
}

internal class DefaultRoom(
    override val roomId: String,
    override val options: RoomOptions,
    private val realtimeClient: RealtimeClient,
    chatApi: ChatApi,
    clientId: String,
    private val logger: Logger,
) : Room {

    /**
     * RoomScope is a crucial part of the Room lifecycle. It manages sequential and atomic operations.
     * Parallelism is intentionally limited to 1 to ensure that only one coroutine runs at a time,
     * preventing concurrency issues. Every operation within Room must be performed through this scope.
     */
    private val roomScope =
        CoroutineScope(Dispatchers.Default.limitedParallelism(1) + CoroutineName(roomId) + SupervisorJob())

    override val messages = DefaultMessages(
        roomId = roomId,
        realtimeChannels = realtimeClient.channels,
        chatApi = chatApi,
        logger = logger.withContext(tag = "Messages"),
    )

    private var _presence: Presence? = null
    override val presence: Presence
        get() {
            if (_presence == null) { // CHA-RC2b
                throw ablyException("Presence is not enabled for this room", ErrorCode.BadRequest)
            }
            return _presence as Presence
        }

    private var _reactions: RoomReactions? = null
    override val reactions: RoomReactions
        get() {
            if (_reactions == null) { // CHA-RC2b
                throw ablyException("Reactions are not enabled for this room", ErrorCode.BadRequest)
            }
            return _reactions as RoomReactions
        }

    private var _typing: Typing? = null
    override val typing: Typing
        get() {
            if (_typing == null) { // CHA-RC2b
                throw ablyException("Typing is not enabled for this room", ErrorCode.BadRequest)
            }
            return _typing as Typing
        }

    private var _occupancy: Occupancy? = null
    override val occupancy: Occupancy
        get() {
            if (_occupancy == null) { // CHA-RC2b
                throw ablyException("Occupancy is not enabled for this room", ErrorCode.BadRequest)
            }
            return _occupancy as Occupancy
        }

    private val statusLifecycle = DefaultRoomLifecycle(logger)

    override val status: RoomStatus
        get() = statusLifecycle.status

    override val error: ErrorInfo?
        get() = statusLifecycle.error

    private var lifecycleManager: RoomLifecycleManager

    init {
        options.validateRoomOptions() // CHA-RC2a

        val roomFeatures = mutableListOf<ContributesToRoomLifecycle>(messages)

        options.presence?.let {
            val presenceContributor = DefaultPresence(
                clientId = clientId,
                channel = messages.channel,
                presence = messages.channel.presence,
                logger = logger.withContext(tag = "Presence"),
            )
            roomFeatures.add(presenceContributor)
            _presence = presenceContributor
        }

        options.typing?.let {
            val typingContributor = DefaultTyping(
                roomId = roomId,
                realtimeClient = realtimeClient,
                clientId = clientId,
                options = options.typing,
                logger = logger.withContext(tag = "Typing"),
            )
            roomFeatures.add(typingContributor)
            _typing = typingContributor
        }

        options.reactions?.let {
            val reactionsContributor = DefaultRoomReactions(
                roomId = roomId,
                clientId = clientId,
                realtimeChannels = realtimeClient.channels,
                logger = logger.withContext(tag = "Reactions"),
            )
            roomFeatures.add(reactionsContributor)
            _reactions = reactionsContributor
        }

        options.occupancy?.let {
            val occupancyContributor = DefaultOccupancy(
                roomId = roomId,
                realtimeChannels = realtimeClient.channels,
                chatApi = chatApi,
                logger = logger.withContext(tag = "Occupancy"),
            )
            roomFeatures.add(occupancyContributor)
            _occupancy = occupancyContributor
        }

        lifecycleManager = RoomLifecycleManager(roomScope, statusLifecycle, roomFeatures, logger)
    }

    override fun onStatusChange(listener: RoomLifecycle.Listener): Subscription =
        statusLifecycle.onChange(listener)

    override fun offAllStatusChange() {
        statusLifecycle.offAll()
    }

    override suspend fun attach() {
        lifecycleManager.attach()
    }

    override suspend fun detach() {
        lifecycleManager.detach()
    }

    /**
     * Releases the room, underlying channels are removed from the core SDK to prevent leakage.
     * This is an internal method and only called from Rooms interface implementation.
     */
    internal suspend fun release() {
        lifecycleManager.release()
    }
}
