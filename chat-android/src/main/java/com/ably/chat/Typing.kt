@file:Suppress("StringLiteralDuplication")

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
    public suspend fun start()

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
public data class TypingEvent(
    /**
     * The set of user clientIds that are currently typing.
     */
    val currentlyTyping: Set<String>,

    /**
     * The change that caused this event.
     */
    val change: TypingChange,
) {
    public data class TypingChange(
        /**
         * The client ID of the user who started or stopped typing.
         */
        val clientId: String,

        /**
         * The type of typing event.
         */
        val type: TypingEventType,
    )
}

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

    private var isTypingInProgress = false

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
     */
    private val timeoutMs: Long = 2000

    init {
        val typingListener = PubSubMessageListener { msg ->
            val typingEventType = TypingEventType.entries.first { it.eventName == msg.name }
            if (msg.data == null || msg.data !is String) {
                logger.error("unable to handle typing event; no clientId", staticContext = mapOf("member" to msg.toString()))
                return@PubSubMessageListener
            }
            typingScope.launch {
                processEvent(typingEventType, msg.data as String)
            }
        }
        val typingEvents = listOf(TypingEventType.Started.eventName, TypingEventType.Stopped.eventName)
        typingEventPubSubSubscription = channelWrapper.subscribe(typingEvents, typingListener).asChatSubscription()
    }

    override fun subscribe(listener: Typing.Listener): Subscription {
        logger.trace("DefaultTyping.subscribe()")
        listeners.add(listener)
        return Subscription {
            logger.trace("DefaultTyping.unsubscribe()")
            listeners.remove(listener)
        }
    }

    override suspend fun get(): Set<String> {
        logger.trace("DefaultTyping.get()")
        room.ensureAttached(logger) // CHA-T2d, CHA-T2c, CHA-T2g
        return currentlyTypingMembers
    }

    override suspend fun start() {
        logger.trace("DefaultTyping.start()")
        typingScope.async {
            room.ensureAttached(logger) // CHA-T4a1, CHA-T4a3, CHA-T4a4
            // If the user is already typing, return
            if (isTypingInProgress) {
                logger.warn("DefaultTyping.start(); already typing, so it's a no-op")
                return@async
            }
            isTypingInProgress = true
            startTypingTimer()
            val msgExtras = JsonObject().apply {
                addProperty("ephemeral", true)
            }
            try {
                channelWrapper.publishCoroutine(Message(TypingEventType.Started.eventName, room.clientId, MessageExtras(msgExtras)))
            } catch (e: Exception) {
                logger.error("DefaultTyping.start(); failed to publish start typing event", e)
                isTypingInProgress = false
                throw e
            }
        }.await()
    }

    override suspend fun stop() {
        logger.trace("DefaultTyping.stop()")
        typingScope.async {
            room.ensureAttached(logger) // CHA-T5e, CHA-T5c, CHA-T5d
            isTypingInProgress = false
            val msgExtras = JsonObject().apply {
                addProperty("ephemeral", true)
            }
            channelWrapper.publishCoroutine(Message(TypingEventType.Stopped.eventName, room.clientId, MessageExtras(msgExtras)))
        }.await()
    }

    override fun release() {
        typingEventPubSubSubscription.unsubscribe()
        typingScope.cancel()
        room.realtimeClient.channels.release(channelWrapper.name)
    }

    private fun startTypingTimer() {
        logger.trace("DefaultTyping.startTypingTimer()")
        typingScope.launch {
            delay(heartbeatThrottleMs)
            logger.debug("DefaultTyping.startTypingTimer(); timeout expired")
            isTypingInProgress = false
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
     */
    private fun processEvent(eventType: TypingEventType, clientId: String) {
        when (eventType) {
            TypingEventType.Started -> {
                currentlyTypingMembers.add(clientId)
                // Cancel the current self stopping event and replace with new one
                typingEvents[clientId]?.cancelJob()
                val typingEventWaitingTimeout = heartbeatThrottleMs + timeoutMs
                val timedTypingStartEvent = TimedTypingStartEventPruner(typingScope, clientId, typingEventWaitingTimeout) {
                    // typingEventWaitingTimeout elapsed, so remove given clientId and emit stop event
                    currentlyTypingMembers.remove(it)
                    typingEvents.remove(it)
                    emit(TypingEventType.Stopped, it)
                }
                typingEvents[clientId] = timedTypingStartEvent
            }
            TypingEventType.Stopped -> {
                currentlyTypingMembers.remove(clientId)
                typingEvents[clientId]?.cancelJob()
                typingEvents.remove(clientId)
            }
        }
        emit(eventType, clientId)
    }

    private fun emit(eventType: TypingEventType, clientId: String) {
        val change = TypingEvent.TypingChange(clientId, eventType)
        listeners.forEach {
            it.onEvent(TypingEvent(currentlyTypingMembers, change))
        }
    }

    /**
     * A [TimedTypingStartEventPruner] wrapper that automatically calls [removeTypingEvent]
     * when a timer set to [delayTimeMs] elapses.
     *
     * @param coroutineScope The coroutine scope used for executing removeTypingEvent block.
     * @param clientId The ID of the user tied to the typing event.
     * @param delayTimeMs The period of time it takes before the event is removed.
     * @param removeTypingEvent The lambda called when the stale typing event should be removed.
     */
    internal class TimedTypingStartEventPruner(
        private val coroutineScope: CoroutineScope,
        private val clientId: String,
        private val delayTimeMs: Long,
        private val removeTypingEvent: (userId: String) -> Unit,
    ) {
        /**
         * The current job.
         * Cancel it before removing an instance.
         */
        private var job: Job? = null

        /**
         * Starts the "cleaning" job.
         */
        init {
            job = CoroutineScope(Dispatchers.Default).launch {
                delay(delayTimeMs)
                coroutineScope.launch {
                    removeTypingEvent(clientId)
                }
            }
        }

        /**
         * Cancels the currently running job.
         */
        fun cancelJob() {
            job?.cancel()
        }
    }
}
