package com.ably.chat

import app.cash.turbine.test
import com.ably.chat.room.DEFAULT_CLIENT_ID
import com.ably.chat.room.TypingHeartbeatStarted
import com.ably.chat.room.TypingStartEventPrunerJobs
import com.ably.chat.room.createMockChatApi
import com.ably.chat.room.createMockRealtimeChannel
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createTestRoom
import com.ably.chat.room.processEvent
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.realtime.Connection
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.Message
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TypingTest {

    private lateinit var room: DefaultRoom
    private val realtimeChannel: RealtimeChannel = createMockRealtimeChannel()
    private var pubSubTypingListener: PubSubMessageListener? = null
    private lateinit var typingLogger: Logger

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        val realtimeClient = createMockRealtimeClient()
        val connection = mockk<Connection>()
        connection.state = ConnectionState.connected
        every { realtimeClient.connection } returns connection

        val channels = realtimeClient.channels
        every { channels.get(any(), any()) } returns realtimeChannel

        every { realtimeChannel.subscribe(any<List<String>>(), any<PubSubMessageListener>()) } answers {
            pubSubTypingListener = secondArg()
            com.ably.Subscription {
                pubSubTypingListener = null
            }
        }

        val mockChatApi = createMockChatApi(realtimeClient)
        room = spyk(createTestRoom("room1", realtimeClient = realtimeClient, chatApi = mockChatApi))
        typingLogger = room.logger
        every {
            typingLogger.withContext(any<String>())
        } returns typingLogger

        coEvery { room.ensureAttached(any<Logger>()) } returns Unit
    }

    /**
     * @spec CHA-T4, CHA-T4a
     */
    @Test
    fun `when a typing start is called, the client publishes typing start ephemeral message`() = runTest {
        val typing = DefaultTyping(room)
        var publishedMessage: Message? = null
        every {
            realtimeChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }
        assertNull(typing.TypingHeartbeatStarted)
        typing.keystroke()
        assertNotNull(typing.TypingHeartbeatStarted)
        verify(exactly = 1) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)
        val extras = publishedMessage?.extras?.asJsonObject()
        val ephemeral = extras?.get("ephemeral")?.asBoolean!!
        assertTrue(ephemeral)
    }

    /**
     * @spec CHA-T4, CHA-T4a3, CHA-T4a4, CHA-T4c
     */
    @Test
    fun `Multiple calls to typing start within heartbeatThrottle, only one message is published`() = runTest {
        val typing = DefaultTyping(room)
        var publishedMessage: Message? = null

        every {
            realtimeChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }

        val firstKeystrokeTime = TimeSource.Monotonic.markNow()

        typing.keystroke()
        assertNotNull(typing.TypingHeartbeatStarted)

        verify(exactly = 1) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)

        assertTrue(typing.TypingHeartbeatStarted!!.elapsedNow() < 10.seconds) // timer not expired
        repeat(5) {
            typing.keystroke() // no typing event is sent
        }
        verify(exactly = 1) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)

        // Advance heartbeatThrottle by 10 seconds, make it expired
        typing.TypingHeartbeatStarted = firstKeystrokeTime - 10.seconds
        assertFalse(typing.TypingHeartbeatStarted!!.elapsedNow() < 10.seconds) // expired

        // Next keystroke should publish a new typing start event
        typing.keystroke()
        assertNotNull(typing.TypingHeartbeatStarted) // New timer set

        verify(exactly = 2) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)

        assertTrue(typing.TypingHeartbeatStarted!!.elapsedNow() < 10.seconds) // timer not expired
        repeat(5) {
            typing.keystroke() // no typing event is sent
        }

        verify(exactly = 2) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)
    }

    /**
     * @spec CHA-T5, CHA-T5a, CHA-T14, CHA-T5e, CHA-T5d
     */
    @Test
    fun `If typing stop is called, the heartbeatThrottle timeout is cancelled, the client sends stop event`() = runTest {
        val typing = spyk(DefaultTyping(room))

        var publishedMessage: Message? = null
        every {
            realtimeChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }
        assertNull(typing.TypingHeartbeatStarted)

        val firstKeystrokeTime = TimeSource.Monotonic.markNow()
        typing.keystroke()
        assertNotNull(typing.TypingHeartbeatStarted)
        verify(exactly = 1) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)

        // CHA-T5d, CHA-T5e - Typing stop event is published, heartbeatThrottle timer is cancelled
        typing.stop()
        assertNull(typing.TypingHeartbeatStarted)
        verify(exactly = 2) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Stopped.eventName, publishedMessage?.name)
        val extras = publishedMessage?.extras?.asJsonObject()
        val ephemeral = extras?.get("ephemeral")?.asBoolean!!
        assertTrue(ephemeral)

        // typing.TypingHeartbeatStarted set as active, so stop message should be published
        typing.TypingHeartbeatStarted = firstKeystrokeTime
        typing.stop()
        verify(exactly = 3) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Stopped.eventName, publishedMessage?.name)

        // CHA-T5a, typing.TypingHeartbeatStarted set to expired, so no stop message should be published
        typing.TypingHeartbeatStarted = firstKeystrokeTime - 10.seconds
        assertFalse(typing.TypingHeartbeatStarted!!.elapsedNow() < 10.seconds) // expired
        typing.stop()
        verify(exactly = 3) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Stopped.eventName, publishedMessage?.name)

        // CHA-T5a, typing.TypingHeartbeatStarted is null, so no stop message should be published
        typing.TypingHeartbeatStarted = null
        typing.stop()
        verify(exactly = 3) { realtimeChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Stopped.eventName, publishedMessage?.name)
    }

    private fun receiveTypingEvent(eventName: TypingEventType, clientId: String?) {
        val typingStartEvent = Message()
        typingStartEvent.name = eventName.eventName
        typingStartEvent.clientId = clientId
        pubSubTypingListener?.onMessage(typingStartEvent)
    }

    /**
     * Spec: CHA-T13a
     */
    @Test
    fun `on typing event received, if event is malformed, ignore the event`() = runTest {
        val typing = spyk(DefaultTyping(room))

        val typingEvents = mutableListOf<TypingSetEvent>()
        typing.subscribe {
            typingEvents.add(it)
        }

        // Receive mock typing start event with null clientId
        receiveTypingEvent(TypingEventType.Started, null)

        verify(exactly = 1) {
            typingLogger.error("unable to handle typing event; no clientId", any(), any(), any<Map<String, String>>())
        }
        assertWaiter {
            delay(500)
            typingEvents.size == 0
        }
        assertTrue(typing.current().isEmpty())

        // Receive mock typing stop event with empty clientId
        receiveTypingEvent(TypingEventType.Stopped, "")

        verify(exactly = 2) {
            typingLogger.error("unable to handle typing event; no clientId", any(), any(), any<Map<String, String>>())
        }
        assertWaiter {
            delay(500)
            typingEvents.size == 0
        }
        assertTrue(typing.current().isEmpty())

        // Receive mock typing event with valid clientId
        receiveTypingEvent(TypingEventType.Started, DEFAULT_CLIENT_ID)
        assertWaiter { typingEvents.size == 1 }
        assertEquals(1, typing.current().size)

        receiveTypingEvent(TypingEventType.Stopped, DEFAULT_CLIENT_ID)
        assertWaiter { typingEvents.size == 2 }
        assertEquals(0, typing.current().size)

        // No error detected
        verify(exactly = 2) {
            typingLogger.error("unable to handle typing event; no clientId", any(), any(), any<Map<String, String>>())
        }
    }

    /**
     * Spec: CHA-T13b1, CHA-T13b2
     */
    @Test
    fun `Each start typing event should reset the inactivity timeout and set new one`() = runTest {
        val typing = DefaultTyping(room)

        val typingEvents = mutableListOf<TypingSetEvent>()
        typing.subscribe {
            typingEvents.add(it)
        }
        // Initially no timer exists
        val activityTimer1 = typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID]
        assertNull(activityTimer1)

        // Receive mock typing start event
        receiveTypingEvent(TypingEventType.Started, DEFAULT_CLIENT_ID)
        assertWaiter { typingEvents.size == 1 }
        // New inactivity timer started
        val activityTimer2 = typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID]
        assertNotNull(activityTimer2)

        // Receive new mock typing start event
        receiveTypingEvent(TypingEventType.Started, DEFAULT_CLIENT_ID)
        assertWaiter { typingEvents.size == 2 }
        // Old timer is cancelled and new inactivity timer started
        assertTrue(activityTimer2!!.isCancelled)
        val activityTimer3 = typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID]
        assertNotNull(activityTimer3)

        // Receive one more typing start event
        receiveTypingEvent(TypingEventType.Started, DEFAULT_CLIENT_ID)
        assertWaiter { typingEvents.size == 3 }
        // Old timer is cancelled and new inactivity timer started
        assertTrue(activityTimer3!!.isCancelled)
        val activityTimer4 = typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID]
        assertNotNull(activityTimer4)
    }

    /**
     * @spec CHA-T13b3
     */
    @Test
    fun `On typingStart event received, heartbeatThrottle timeout is set, emits self stop event if no more event received`() = runTest {
        val testScheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val typing = DefaultTyping(room, dispatcher)

        val typingEvents = mutableListOf<TypingSetEvent>()
        typing.subscribe {
            typingEvents.add(it)
        }
        // Receive mock typing start event
        typing.processEvent(TypingEventType.Started, DEFAULT_CLIENT_ID)

        assertEquals(1, typingEvents.size)
        assertEquals(setOf(DEFAULT_CLIENT_ID), typingEvents[0].currentlyTyping)
        assertEquals(TypingSetEventType.SetChanged, typingEvents[0].type)
        assertEquals(TypingEventType.Started, typingEvents[0].change.type)
        assertEquals(DEFAULT_CLIENT_ID, typingEvents[0].change.clientId)

        assertEquals(setOf(DEFAULT_CLIENT_ID), typing.current()) // clientId added to internal set
        assertNotNull(typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID]) // self stop timer started

        testScheduler.advanceTimeBy(9.seconds)
        testScheduler.runCurrent()
        assertEquals(1, typingEvents.size)

        // Emits stop event after 12 seconds, heartbeatThrottle (10 sec) + timeoutMs (2 sec) = 12 seconds
        // Previous 9 seconds + current 3 seconds = 12 seconds
        testScheduler.advanceTimeBy(3.seconds)
        testScheduler.runCurrent()

        assertEquals(2, typingEvents.size)
        assertEquals(emptySet<String>(), typingEvents[1].currentlyTyping)
        assertEquals(TypingSetEventType.SetChanged, typingEvents[1].type)
        assertEquals(TypingEventType.Stopped, typingEvents[1].change.type)
        assertEquals(DEFAULT_CLIENT_ID, typingEvents[1].change.clientId)

        assertTrue(typing.TypingStartEventPrunerJobs.isEmpty())
        assertTrue(typing.current().isEmpty())
    }

    /**
     * Spec: CHA-T13b4
     */
    @Test
    fun `Each stop typing event should remove inactivity timeout and emit stop event`() = runTest {
        val typing = DefaultTyping(room)

        val typingEvents = mutableListOf<TypingSetEvent>()
        typing.subscribe {
            typingEvents.add(it)
        }
        // Receive mock typing start event
        receiveTypingEvent(TypingEventType.Started, DEFAULT_CLIENT_ID)

        assertWaiter { typingEvents.size == 1 }
        assertEquals(setOf(DEFAULT_CLIENT_ID), typingEvents[0].currentlyTyping)
        assertEquals(TypingEventType.Started, typingEvents[0].change.type)
        assertEquals(DEFAULT_CLIENT_ID, typingEvents[0].change.clientId)

        assertEquals(setOf(DEFAULT_CLIENT_ID), typing.current())
        assertNotNull(typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID])

        val activityTimer1 = typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID]
        assertNotNull(activityTimer1)
        assertTrue(activityTimer1!!.isActive)

        // Receive mock typing stop event
        receiveTypingEvent(TypingEventType.Stopped, DEFAULT_CLIENT_ID)

        assertWaiter { typingEvents.size == 2 }
        assertTrue(activityTimer1.isCancelled)
        assertEquals(emptySet<String>(), typingEvents[1].currentlyTyping)
        assertEquals(TypingEventType.Stopped, typingEvents[1].change.type)
        assertEquals(DEFAULT_CLIENT_ID, typingEvents[1].change.clientId)

        assertTrue(typing.TypingStartEventPrunerJobs.isEmpty())
        assertTrue(typing.current().isEmpty())

        // If stop event is received again, nothing is emitted
        receiveTypingEvent(TypingEventType.Stopped, DEFAULT_CLIENT_ID)
        assertWaiter {
            delay(500)
            typingEvents.size == 2
        }
        assertTrue(typing.TypingStartEventPrunerJobs.isEmpty())
        assertTrue(typing.current().isEmpty())
    }

    /**
     * @spec CHA-T13b5
     */
    @Test
    fun `On typingStop event is received for client not present in typing set, then event is not emitted`() = runTest {
        val typing = DefaultTyping(room)

        val typingEvents = mutableListOf<TypingSetEvent>()
        typing.subscribe {
            typingEvents.add(it)
        }
        // Receive mock typing start event
        receiveTypingEvent(TypingEventType.Started, DEFAULT_CLIENT_ID)

        assertWaiter { typingEvents.size == 1 }
        assertEquals(setOf(DEFAULT_CLIENT_ID), typingEvents[0].currentlyTyping)
        assertEquals(TypingSetEventType.SetChanged, typingEvents[0].type)
        assertEquals(TypingEventType.Started, typingEvents[0].change.type)
        assertEquals(DEFAULT_CLIENT_ID, typingEvents[0].change.clientId)

        assertEquals(setOf(DEFAULT_CLIENT_ID), typing.current())
        assertNotNull(typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID])

        receiveTypingEvent(TypingEventType.Stopped, "missing-client-Id")

        assertWaiter {
            delay(500)
            typingEvents.size == 1
        }

        assertNotNull(typing.TypingStartEventPrunerJobs[DEFAULT_CLIENT_ID])
        assertEquals(setOf(DEFAULT_CLIENT_ID), typing.current())
    }

    /**
     * @spec CHA-TM14, CHA-TM14a, CHA-TM14b
     */
    @Test
    fun `multiple calls to keystroke and stop should be idempotent and only latest one should be performed`() = runTest {
        val typing = spyk(DefaultTyping(room), recordPrivateCalls = true)
        val recordedEvents = mutableListOf<TypingEventType>()
        coEvery { typing["sendTyping"](any<TypingEventType>()) } coAnswers {
            delay(500)
            val event = firstArg<TypingEventType>()
            recordedEvents.add(event)
        }
        suspend fun concurrentlyPerform(blocks: List<suspend () -> Unit>) {
            withContext(Dispatchers.IO) {
                blocks.forEach { block ->
                    launch { block() }
                    // we expect delay of at least 1ms between keystrokes and stop, without it, sequential locking doesn't work
                    delay(1)
                }
            }
        }

        // First case
        concurrentlyPerform(
            listOf(
                { typing.keystroke() },
                { typing.stop() },
                { typing.keystroke() },
                { typing.stop() },
                { typing.keystroke() },
                { typing.stop() },
                { typing.keystroke() },
                { typing.stop() },
            ),
        )
        assertEquals(listOf(TypingEventType.Started, TypingEventType.Stopped), recordedEvents)

        // Clear states
        typing.stop() // Set typing as off
        recordedEvents.clear()

        // Second case
        concurrentlyPerform(
            listOf(
                { typing.keystroke() },
                { typing.keystroke() },
                { typing.stop() },
                { typing.keystroke() },
                { typing.keystroke() },
                { typing.stop() },
                { typing.keystroke() },
            ),
        )
        assertEquals(listOf(TypingEventType.Started), recordedEvents)

        // Clear states
        typing.stop() // Set typing as off
        recordedEvents.clear()

        // Third case
        recordedEvents.clear()
        concurrentlyPerform(
            listOf(
                { typing.stop() }, // This will be ignored
                { typing.stop() }, // This will be ignored
                { typing.keystroke() },
                { typing.keystroke() },
                { typing.stop() },
                { typing.keystroke() },
                { typing.keystroke() },
                { typing.stop() },
            ),
        )
        assertEquals(listOf(TypingEventType.Started, TypingEventType.Stopped), recordedEvents)
    }

    @Test
    fun `asFlow() should automatically unsubscribe then it's done`() = runTest {
        val typing: Typing = mockk()
        val subscription: Subscription = mockk()
        lateinit var callback: TypingListener

        every { typing.subscribe(any()) } answers {
            callback = firstArg()
            subscription
        }

        typing.asFlow().test {
            val event = mockk<TypingSetEvent>()
            callback.invoke(event)
            assertEquals(event, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }
}
