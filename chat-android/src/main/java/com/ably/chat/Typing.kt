package com.ably.chat

import com.ably.annotations.InternalAPI
import com.ably.pubsub.RealtimeChannel
import com.google.gson.JsonObject
import io.ably.lib.realtime.Channel
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.Message
import io.ably.lib.types.MessageExtras
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * This interface is used to interact with typing in a chat room including subscribing to typing events and
 * fetching the current set of typing clients.
 *
 * Get an instance via [Room.typing].
 */
public interface Typing : EmitsDiscontinuities {

    /**
     * Get the Ably realtime channel underpinning typing events.
     * @returns The Ably realtime channel.
     */
    public val channel: Channel

    /**
     * Subscribe a given listener to all typing events from users in the chat room.
     *
     * @param listener A listener to be called when the typing state of a user in the room changes.
     * @returns A response object that allows you to control the subscription to typing events.
     */
    public fun subscribe(listener: Listener): Subscription

    /**
     * Current typing members.
     * @return set of clientIds that are currently typing.
     */
    public fun get(): Set<String>

    /**
     * This will send a `typing.started` event to the server.
     * Events are throttled according to the `heartbeatThrottleMs` room option.
     * If an event has been sent within the interval, this operation is no-op.
     *
     *
     * Calls to `keystroke()` and `stop()` are serialized and will always be performed in the correct order.
     * - For example, if multiple `keystroke()` calls are made in quick succession before the first `keystroke()` call has
     *   sent a `typing.started` event to the server, followed by one `stop()` call, the `stop()` call will execute
     *   as soon as the first `keystroke()` call completes.
     *   All intermediate `keystroke()` calls will be treated as no-ops.
     * - The most recent operation (`keystroke()` or `stop()`) will always determine the final state, ensuring operations
     *   resolve to a consistent and correct state.
     *
     * @returns when there is a success or throws AblyException upon its failure.
     * @throws AblyException if the `RoomStatus` is not either `Attached` or `Attaching`.
     * @throws AblyException if the operation fails to send the event to the server.
     * @throws AblyException if there is a problem acquiring the mutex that controls serialization.
     */
    public suspend fun keystroke()

    /**
     * This will send a `typing.stopped` event to the server.
     * If the user was not currently typing, this operation is no-op.
     *
     * Calls to `keystroke()` and `stop()` are serialized and will always be performed in the correct order.
     * - For example, if multiple `keystroke()` calls are made in quick succession before the first `keystroke()` call has
     *   sent a `typing.started` event to the server, followed by one `stop()` call, the `stop()` call will execute
     *   as soon as the first `keystroke()` call completes.
     *   All intermediate `keystroke()` calls will be treated as no-ops.
     * - The most recent operation (`keystroke()` or `stop()`) will always determine the final state, ensuring operations
     *   resolve to a consistent and correct state.
     *
     * @returns when there is a success or throws AblyException upon its failure.
     * @throws AblyException if the `RoomStatus` is not either `Attached` or `Attaching`.
     * @throws AblyException if the operation fails to send the event to the server.
     * @throws AblyException if there is a problem acquiring the mutex that controls serialization.
     */
    public suspend fun stop()

    /**
     * An interface for listening to changes for Typing
     */
    public fun interface Listener {
        /**
         * A function that can be called when the new typing event happens.
         * @param event The event that happened.
         */
        public fun onEvent(event: TypingEvent)
    }
}

/**
 * @return [TypingEvent] events as a [Flow]
 */
public fun Typing.asFlow(): Flow<TypingEvent> = transformCallbackAsFlow {
    subscribe(it)
}

/**
 * Represents a typing event.
 */
public interface TypingEvent {
    /**
     * The set of user clientIds that are currently typing.
     */
    public val currentlyTyping: Set<String>

    /**
     * The change that caused this event.
     */
    public val change: Change

    public interface Change {
        /**
         * The client ID of the user who started or stopped typing.
         */
        public val clientId: String

        /**
         * The type of typing event.
         */
        public val type: TypingEventType
    }
}

internal data class DefaultTypingEvent(
    override val currentlyTyping: Set<String>,
    override val change: DefaultTypingEventChange,
) : TypingEvent

internal data class DefaultTypingEventChange(
    override val type: TypingEventType,
    override val clientId: String,
) : TypingEvent.Change

internal class DefaultTyping(
    private val room: DefaultRoom,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Typing, ContributesToRoomLifecycleImpl(room.logger) {
    private val typingIndicatorsChannelName = "${room.roomId}::\$chat::\$typingIndicators"

    override val featureName = "typing"

    override val attachmentErrorCode: ErrorCode = ErrorCode.TypingAttachmentFailed

    override val detachmentErrorCode: ErrorCode = ErrorCode.TypingDetachmentFailed

    private val logger = room.logger.withContext(tag = "Typing")

    private val typingScope = CoroutineScope(dispatcher.limitedParallelism(1) + SupervisorJob())

    private var typingHeartbeatStarted: ValueTimeMark? = null

    override val channelWrapper: RealtimeChannel = room.realtimeClient.channels.get(typingIndicatorsChannelName, ChatChannelOptions())

    @OptIn(InternalAPI::class)
    override val channel: Channel = channelWrapper.javaChannel // CHA-RC2f

    private val listeners: MutableList<Typing.Listener> = CopyOnWriteArrayList()

    private var currentlyTypingMembers: MutableSet<String> = mutableSetOf()

    private var typingEventPubSubSubscription: Subscription

    /**
     * A mutable map of clientId to TimedTypingStopEvent job.
     * Each received typing start event is capable of timed death after a certain period (heartbeatThrottle + timeout).
     */
    private val typingStartEventPrunerJobs = mutableMapOf<String, Job>()

    /**
     * Defines how often a typing.started heartbeat can be sent (in milliseconds).
     * This does not prevent a client from sending an typing.stopped heartbeat—doing so resets the interval timer
     * and allows another typing.started to be sent.
     * Spec: CHA-T10
     */
    private val heartbeatThrottle: Duration = room.options.typing?.heartbeatThrottle ?: throw AblyException.fromErrorInfo(
        ErrorInfo(
            "Typing options hasn't been initialized",
            ErrorCode.BadRequest.code,
        ),
    )

    /**
     * Defines how long to wait before marking a user as stopped typing.
     * For example, if the last typing.started heartbeat was received 10s ago, the system will wait an additional 2s before declaring
     * the client as no longer typing.
     * @defaultValue 2 seconds
     * Spec: CHA-T10a
     */
    private val timeout: Duration = 2.seconds

    /**
     * Time source for measuring elapsed time.
     */
    private val timeSource = TimeSource.Monotonic

    /**
     * Spec: CHA-TM14 - Only latest job is processed, old queued jobs are dropped/ignored.
     */
    private val latestJobExecutor = LatestJobExecutor()

    /**
     * Spec: CHA-T13
     */
    init {
        // Process received typing events
        val typingListener = PubSubMessageListener { msg ->
            val typingEventType = TypingEventType.entries.first { it.eventName == msg.name }
            // CHA-T13a
            if (msg.clientId == null || msg.clientId.isEmpty()) {
                logger.error("unable to handle typing event; no clientId", context = mapOf("message" to msg.toString()))
                return@PubSubMessageListener
            }
            typingScope.launch {
                processReceivedTypingEvents(typingEventType, msg.clientId)
            }
        }
        val typingEvents = listOf(TypingEventType.Started.eventName, TypingEventType.Stopped.eventName)
        typingEventPubSubSubscription = channelWrapper.subscribe(typingEvents, typingListener).asChatSubscription()
    }

    /**
     * Spec: CHA-T6a
     */
    override fun subscribe(listener: Typing.Listener): Subscription {
        logger.trace("DefaultTyping.subscribe()")
        listeners.add(listener)
        // CHA-T6b
        return Subscription {
            logger.trace("DefaultTyping.unsubscribe()")
            listeners.remove(listener)
        }
    }

    /**
     * Spec: CHA-T9
     */
    override fun get(): Set<String> {
        logger.trace("DefaultTyping.get()")
        return currentlyTypingMembers.toSet()
    }

    /**
     * Spec: CHA-T4, CHA-T14
     */
    override suspend fun keystroke() {
        latestJobExecutor.run {
            // CHA-T4c - If heartbeat is active, it's a no-op
            typingHeartbeatStarted?.let {
                if (it.elapsedNow() < heartbeatThrottle) {
                    logger.trace("DefaultTyping.sendSelfTypingEvent(); typingHeartbeat is not elapsed, so it's a no-op")
                    return@run
                }
            }
            // send typing.start event
            room.ensureAttached(logger) // CHA-T4a1, CHA-T4a3, CHA-T4a4, CHA-T4d
            sendTyping(TypingEventType.Started) // CHA-T4a3
            typingHeartbeatStarted = timeSource.markNow() // CHA-T4a4
        }
    }

    /**
     * Spec: CHA-T5, CHA-T14
     */
    override suspend fun stop() {
        latestJobExecutor.run {
            // CHA-T5a - If heartbeat is off, it's a no-op
            if (typingHeartbeatStarted == null) {
                logger.trace("DefaultTyping.sendSelfTypingEvent(); typing is not started or already stopped, so it's a no-op")
                return@run
            }
            // CHA-T5a - If heartbeat is expired, it's a no-op
            typingHeartbeatStarted?.let {
                if (it.elapsedNow() > heartbeatThrottle) {
                    logger.trace("DefaultTyping.sendSelfTypingEvent(); typingHeartbeat is elapsed, so it's a no-op")
                    return@run
                }
            }
            // send typing.stop event
            room.ensureAttached(logger) // CHA-T5e, CHA-T5c, CHA-T5d
            sendTyping(TypingEventType.Stopped) // CHA-T5d
            typingHeartbeatStarted = null // CHA-T5e
        }
    }

    private suspend fun sendTyping(eventType: TypingEventType) {
        logger.trace("DefaultTyping.sendTyping()")
        val msgExtras = JsonObject().apply {
            addProperty("ephemeral", true)
        }
        try {
            logger.debug("DefaultTyping.sendTyping(); sending typing event $eventType")
            channelWrapper.publishCoroutine(Message(eventType.eventName, "", MessageExtras(msgExtras)))
        } catch (e: Exception) {
            logger.error("DefaultTyping.sendTyping(); failed to publish typing event", e)
            throw e
        }
    }

    override fun release() {
        typingEventPubSubSubscription.unsubscribe()
        typingScope.cancel()
        room.realtimeClient.channels.release(channelWrapper.name)
    }

    /**
     * Processes a typing event by updating the set of currently typing members and scheduling or canceling
     * the corresponding timed event pruner jobs.
     * Emits the typing event to all listeners.
     *
     * @param eventType The type of typing event (started or stopped).
     * @param clientId The ID of the user who triggered the typing event.
     * Spec: CHA-T13
     */
    private fun processReceivedTypingEvents(eventType: TypingEventType, clientId: String) {
        when (eventType) {
            TypingEventType.Started -> { // CHA-T13b1
                currentlyTypingMembers.add(clientId)
                // CHA-T13b2 - Cancel the current self stopping event and replace with new one
                typingStartEventPrunerJobs[clientId]?.cancel()
                // CHA-T10a1, CHA-T13b3 - If a typing.start not received within this period, the client shall assume that the user has stopped typing
                val timedTypingStopEvent: Job = typingScope.launch {
                    val typingEventWaitingTimeout = heartbeatThrottle + timeout
                    delay(typingEventWaitingTimeout)
                    // typingEventWaitingTimeout elapsed, so remove given clientId and emit stop event
                    currentlyTypingMembers.remove(clientId)
                    typingStartEventPrunerJobs.remove(clientId)
                    emit(TypingEventType.Stopped, clientId)
                }
                typingStartEventPrunerJobs[clientId] = timedTypingStopEvent
            }
            TypingEventType.Stopped -> { // CHA-T13b4
                val clientIdPresent = currentlyTypingMembers.remove(clientId)
                typingStartEventPrunerJobs[clientId]?.cancel()
                typingStartEventPrunerJobs.remove(clientId)
                if (!clientIdPresent) { // CHA-T13b5
                    return
                }
            }
        }
        emit(eventType, clientId)
    }

    private fun emit(eventType: TypingEventType, clientId: String) {
        val typingEventChange = DefaultTypingEventChange(eventType, clientId)
        listeners.forEach {
            it.onEvent(DefaultTypingEvent(get(), typingEventChange))
        }
    }
}
