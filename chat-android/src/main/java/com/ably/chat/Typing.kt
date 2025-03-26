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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * This interface is used to interact with typing in a chat room including subscribing to typing events and
 * fetching the current set of typing clients.
 *
 * Get an instance via [Room.typing].
 */
public interface Typing : EmitsDiscontinuities {
    /**
     * Get the name of the realtime channel underpinning typing events.
     * @returns The name of the realtime channel.
     */
    public val channel: Channel

    /**
     * Subscribe a given listener to all typing events from users in the chat room.
     *
     * @param listener A listener to be called when the typing state of a user in the room changes.
     */
    public fun subscribe(listener: Listener): Subscription

    /**
     * Get the current typers, a set of clientIds.
     * @return set of clientIds that are currently typing.
     */
    public suspend fun get(): Set<String>

    /**
     * Start indicates that the current user is typing. This will emit a typingStarted event to inform listening clients and begin a timer,
     * once the timer expires, a typingStopped event will be emitted. The timeout is configurable through the typingTimeoutMs parameter.
     * If the current user is already typing, it will reset the timer and being counting down again without emitting a new event.
     */
    public suspend fun keystroke()

    /**
     * Stop indicates that the current user has stopped typing. This will emit a typingStopped event to inform listening clients,
     * and immediately clear the typing timeout timer.
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

    private var typingHeartBeatStarted: Long? = null

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

    private val eventBus = MutableSharedFlow<SelfTypingEvent>(extraBufferCapacity = kotlinx.coroutines.channels.Channel.UNLIMITED)

    /**
     * Spec: CHA-T13
     */
    init {
        // Send self typing events
        typingScope.launch {
            eventBus.collect { sendSelfTypingEvent(it) }
        }

        // Process received typing events
        val typingListener = PubSubMessageListener { msg ->
            val typingEventType = TypingEventType.entries.first { it.eventName == msg.name }
            // CHA-T13a
            if (msg.data == null || msg.data !is String) {
                logger.error("unable to handle typing event; no clientId", context = mapOf("member" to msg.toString()))
                return@PubSubMessageListener
            }
            typingScope.launch {
                processReceivedTypingEvents(typingEventType, msg.data as String)
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
    override suspend fun get(): Set<String> {
        logger.trace("DefaultTyping.get()")
        room.ensureAttached(logger) // CHA-T2d, CHA-T2c, CHA-T2g
        return currentlyTypingMembers
    }

    /**
     * Spec: CHA-T4, CHA-T14
     */
    override suspend fun keystroke() {
        logger.trace("DefaultTyping.keystroke()")
        val completion = CompletableDeferred<Unit>()
        eventBus.emit(SelfTypingEvent(TypingEventType.Started, completion))
        completion.await()
    }

    /**
     * Spec: CHA-T5, CHA-T14
     */
    override suspend fun stop() {
        logger.trace("DefaultTyping.stop()")
        val completion = CompletableDeferred<Unit>()
        eventBus.emit(SelfTypingEvent(TypingEventType.Stopped, completion))
        completion.await()
    }

    private data class SelfTypingEvent(
        val event: TypingEventType,
        val completableDeferred: CompletableDeferred<Unit>,
    )

    @Suppress("ReturnCount")
    private suspend fun sendSelfTypingEvent(typingEvent: SelfTypingEvent) {
        when (typingEvent.event) {
            TypingEventType.Started -> {
                // CHA-T4c - If heartbeat timer in active return
                typingHeartBeatStarted?.let {
                    val elapsedTime = System.currentTimeMillis() - it
                    if (elapsedTime < heartbeatThrottle.inWholeMilliseconds) {
                        logger.trace("DefaultTyping.sendSelfTypingEvent(); typingHeartBeat is not elapsed, so it's a no-op")
                        typingEvent.completableDeferred.complete(Unit)
                        return
                    }
                }
                try {
                    // start a new job for sending typing.start event
                    room.ensureAttached(logger) // CHA-T4a1, CHA-T4a3, CHA-T4a4, CHA-T4d
                    sendTyping(TypingEventType.Started, room.clientId) // CHA-T4a3
                    typingHeartBeatStarted = System.currentTimeMillis() // CHA-T4a4
                    typingEvent.completableDeferred.complete(Unit)
                } catch (e: Exception) {
                    typingEvent.completableDeferred.completeExceptionally(e)
                }
            }
            TypingEventType.Stopped -> {
                // CHA-T5a - If heartbeat timer in off return
                if (typingHeartBeatStarted == null) {
                    logger.trace("DefaultTyping.sendSelfTypingEvent(); typing is not started or already stopped, so it's a no-op")
                    typingEvent.completableDeferred.complete(Unit)
                    return
                }
                typingHeartBeatStarted?.let {
                    val elapsedTime = System.currentTimeMillis() - it
                    if (elapsedTime > heartbeatThrottle.inWholeMilliseconds) {
                        logger.trace("DefaultTyping.sendSelfTypingEvent(); typingHeartBeat is elapsed, so it's a no-op")
                        typingEvent.completableDeferred.complete(Unit)
                        return
                    }
                }
                try {
                    // start a new job for sending typing.stop event
                    room.ensureAttached(logger) // CHA-T5e, CHA-T5c, CHA-T5d
                    sendTyping(TypingEventType.Stopped, room.clientId) // CHA-T5d
                    typingHeartBeatStarted = null // CHA-T5e
                    typingEvent.completableDeferred.complete(Unit)
                } catch (e: Exception) {
                    typingEvent.completableDeferred.completeExceptionally(e)
                }
            }
        }
    }

    private suspend fun sendTyping(eventType: TypingEventType, clientId: String) {
        logger.trace("DefaultTyping.sendTyping()")
        val msgExtras = JsonObject().apply {
            addProperty("ephemeral", true)
        }
        try {
            logger.debug("DefaultTyping.sendTyping(); sending typing event $eventType")
            channelWrapper.publishCoroutine(Message(eventType.eventName, clientId, MessageExtras(msgExtras)))
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
            it.onEvent(DefaultTypingEvent(currentlyTypingMembers, typingEventChange))
        }
    }
}
