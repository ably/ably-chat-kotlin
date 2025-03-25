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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    override val change: TypingEvent.Change,
) : TypingEvent

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

    private var typingHeartBeatTimerJob: Job? = null

    private var typingStartDeferred: CompletableDeferred<Unit>? = null

    private var typingStopDeferred: CompletableDeferred<Unit>? = null

    override val channelWrapper: RealtimeChannel = room.realtimeClient.channels.get(typingIndicatorsChannelName, ChatChannelOptions())

    @OptIn(InternalAPI::class)
    override val channel: Channel = channelWrapper.javaChannel // CHA-RC2f

    private val listeners: MutableList<Typing.Listener> = CopyOnWriteArrayList()

    private var currentlyTypingMembers: MutableSet<String> = mutableSetOf()

    private var typingEventPubSubSubscription: Subscription

    /**
     * A mutable map of clientId to TimedTypingStartEventPruner.
     * Each received typing start event is capable of timed death using [TimedTypingStartEventPruner.removeTypingEvent]
     */
    private val typingEvents = mutableMapOf<String, TimedTypingStartEventPruner>()

    /**
     * Defines how often a typing.started heartbeat can be sent (in milliseconds).
     * This does not prevent a client from sending an typing.stopped heartbeat—doing so resets the interval timer
     * and allows another typing.started to be sent.
     * Spec: CHA-T10
     */
    private val heartbeatThrottleMs: Long = room.options.typing?.heartbeatThrottleMs ?: throw AblyException.fromErrorInfo(
        ErrorInfo(
            "Typing options hasn't been initialized",
            ErrorCode.BadRequest.code,
        ),
    )

    /**
     * Defines how long to wait before marking a user as stopped typing.
     * For example, if the last typing.started heartbeat was received 10s ago, the system will wait an additional 2s before declaring
     * the client as no longer typing.
     * @defaultValue 2000ms
     * Spec: CHA-T10a
     */
    private val timeoutMs: Long = 2000

    /**
     * Spec: CHA-T13
     */
    init {
        val typingListener = PubSubMessageListener { msg ->
            val typingEventType = TypingEventType.entries.first { it.eventName == msg.name }
            // CHA-T13a
            if (msg.data == null || msg.data !is String) {
                logger.error("unable to handle typing event; no clientId", context = mapOf("member" to msg.toString()))
                return@PubSubMessageListener
            }
            typingScope.launch {
                processEvent(typingEventType, msg.data as String)
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
        typingScope.async {
            val currentJob = coroutineContext[Job]
            // wait for previous stop operation to complete, join doesn't throw exception for failed job
            typingStopDeferred?.let {
                logger.debug("DefaultTyping.keystroke(); waiting for stop job to complete")
                it.join()
            }
            // When multiple keystrokes are fired before typingHeartBeatTimerJob starts, await on previous active start typing job
            typingStartDeferred?.let {
                if (it.isActive) { // if previous job is active
                    logger.debug("DefaultTyping.keystroke(); existing active typing start job found, waiting result on the same")
                    return@async it.await() // wait for the job
                }
            }
            // CHA-T4c - If heartbeat timer in active return
            if (typingHeartBeatTimerJob?.isActive == true) {
                logger.trace("DefaultTyping.keystroke(); typingHeartBeatTimer is active, so it's a no-op")
                return@async
            }
            typingStartDeferred = CompletableDeferred()
            currentJob?.invokeOnCompletion {
                it?.let {
                    typingStartDeferred?.completeExceptionally(it)
                }
            }
            // start a new job for sending typing.start event
            room.ensureAttached(logger) // CHA-T4a1, CHA-T4a3, CHA-T4a4, CHA-T4d
            sendTyping(TypingEventType.Started, room.clientId) // CHA-T4a3
            startHeartbeatTimer() // CHA-T4a4
            typingStartDeferred?.complete(Unit)
        }.await()
    }

    /**
     * Spec: CHA-T5, CHA-T14
     */
    override suspend fun stop() {
        logger.trace("DefaultTyping.stop()")
        typingScope.async {
            val currentJob = coroutineContext[Job]
            // wait for previous start operation to complete, join doesn't throw exception for failed job
            typingStartDeferred?.let {
                logger.debug("DefaultTyping.stop(); waiting for typing start job to complete")
                it.join()
            }
            // When multiple stops are fired, await on previous active stop typing job
            // Case where sendTyping typing.stop is in progress and typingHeartBeatTimerJob not cancelled
            typingStopDeferred?.let {
                if (it.isActive) { // if previous job is active
                    logger.debug("DefaultTyping.stop(); existing active stop job found, waiting result on the same")
                    return@async it.await() // wait for the job
                }
            }
            // CHA-T5a - If heartbeat timer in off return
            if (typingHeartBeatTimerJob?.isActive == false) {
                logger.trace("DefaultTyping.stop(); typingHeartBeatTimer is inactive, so it's a no-op")
                return@async
            }
            typingStopDeferred = CompletableDeferred()
            currentJob?.invokeOnCompletion {
                it?.let {
                    typingStopDeferred?.completeExceptionally(it)
                }
            }
            // start a new job for sending typing.stop event
            room.ensureAttached(logger) // CHA-T5e, CHA-T5c, CHA-T5d
            sendTyping(TypingEventType.Stopped, room.clientId) // CHA-T5d
            typingHeartBeatTimerJob?.cancel() // CHA-T5e
            typingStopDeferred?.complete(Unit)
        }.await()
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

    private fun startHeartbeatTimer() {
        logger.trace("DefaultTyping.startTypingTimer()")
        typingHeartBeatTimerJob = typingScope.launch {
            delay(heartbeatThrottleMs)
            logger.debug("DefaultTyping.startTypingTimer(); heartbeatThrottleMs expired")
        }
    }

    /**
     * Adds a typing event to [typingEvents].
     *
     * It will cancel the timed death of an typing event with the same key (userId)
     * if such exists.
     *
     * @param eventType The typing event will be added to [typingEvents].
     * @param clientId The ID of the user tied to the typing event.
     * Spec: CHA-T13b
     */
    private fun processEvent(eventType: TypingEventType, clientId: String) {
        when (eventType) {
            TypingEventType.Started -> { // CHA-T13b1
                currentlyTypingMembers.add(clientId)
                // CHA-T13b2 - Cancel the current self stopping event and replace with new one
                typingEvents[clientId]?.cancelJob()
                // CHA-T10a1 - If a typing.start not received within this period, the client shall assume that the user has stopped typing
                val typingEventWaitingTimeout = heartbeatThrottleMs + timeoutMs
                // CHA-T13b3
                val timedTypingStartEvent = TimedTypingStartEventPruner(typingScope, clientId, typingEventWaitingTimeout) {
                    // typingEventWaitingTimeout elapsed, so remove given clientId and emit stop event
                    currentlyTypingMembers.remove(it)
                    typingEvents.remove(it)
                    emit(TypingEventType.Stopped, it)
                }
                typingEvents[clientId] = timedTypingStartEvent
            }
            TypingEventType.Stopped -> { // CHA-T13b4
                val clientIdPresent = currentlyTypingMembers.remove(clientId)
                typingEvents[clientId]?.cancelJob()
                typingEvents.remove(clientId)
                if (!clientIdPresent) { // CHA-T13b5
                    return
                }
            }
        }
        emit(eventType, clientId)
    }

    private fun emit(eventType: TypingEventType, clientId: String) {
        val typingEvent = DefaultTypingEvent(
            currentlyTypingMembers,
            object : TypingEvent.Change {
                override val clientId: String = clientId
                override val type: TypingEventType = eventType
            },
        )
        listeners.forEach {
            it.onEvent(typingEvent)
        }
    }

    /**
     * A [TimedTypingStartEventPruner] wrapper that automatically calls [removeTypingEvent]
     * when a timer set to [delayTimeMs] elapses.
     *
     * @param typingScope The coroutine scope used for executing removeTypingEvent block.
     * @param clientId The ID of the user tied to the typing event.
     * @param delayTimeMs The period of time it takes before the event is removed.
     * @param removeTypingEvent The lambda called when the stale typing event should be removed.
     * Spec: CHA-T10a1
     */
    internal class TimedTypingStartEventPruner(
        private val typingScope: CoroutineScope,
        private val clientId: String,
        private val delayTimeMs: Long,
        private val removeTypingEvent: (userId: String) -> Unit,
    ) {
        /**
         * Starts the "cleaning" job that will call removeTypingEvent method after delayTimeMs.
         */
        private val job: Job = typingScope.launch {
            delay(delayTimeMs)
            removeTypingEvent(clientId)
        }

        /**
         * Cancels the currently running job.
         */
        fun cancelJob() {
            job.cancel()
        }
    }
}
