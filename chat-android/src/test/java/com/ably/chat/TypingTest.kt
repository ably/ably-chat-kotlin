package com.ably.chat

import app.cash.turbine.test
import com.ably.chat.room.DEFAULT_CLIENT_ID
import com.ably.chat.room.createMockChatApi
import com.ably.chat.room.createMockRealtimeChannel
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createMockRoom
import com.ably.chat.room.processEvent
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.Message
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TypingTest {

    private lateinit var room: DefaultRoom
    private val typingChannel: RealtimeChannel = createMockRealtimeChannel()

    @Before
    fun setUp() {
        val realtimeClient = createMockRealtimeClient()
        every { typingChannel.publish(any(), DEFAULT_CLIENT_ID, any()) } answers {
            val completionListener = arg<CompletionListener>(2)
            completionListener.onSuccess()
        }

        val channels = realtimeClient.channels
        every { channels.get(any(), any()) } returns typingChannel

        val mockChatApi = createMockChatApi(realtimeClient)
        room = spyk(createMockRoom("room1", realtimeClient = realtimeClient, chatApi = mockChatApi))

        coEvery { room.ensureAttached(any<Logger>()) } returns Unit
    }

    /**
     * @spec CHA-T4
     */
    @Test
    fun `when a typing start is called, the client publishes typing start ephemeral message`() = runTest {
        val typing = DefaultTyping(room)
        var publishedMessage: Message? = null
        every {
            typingChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }
        typing.keystroke()
        verify(exactly = 1) { typingChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)
        assertEquals(DEFAULT_CLIENT_ID, publishedMessage?.data)
    }

    /**
     * @spec CHA-T4, CHA-T4a4, CHA-T4c
     */
    @Test
    fun `Multiple calls to typing start within heartbeatThrottle, only one message is published`() = runTest {
        val testScheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val typing = spyk(DefaultTyping(room, dispatcher))
        var publishedMessage: Message? = null

        every {
            typingChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }

        scope.launch {
            repeat(5) {
                typing.keystroke()
            }
        }
        testScheduler.runCurrent()

        coVerify(exactly = 5) { typing.keystroke() }

        verify(exactly = 1) { typingChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)
        assertEquals(DEFAULT_CLIENT_ID, publishedMessage?.data)

        // Advance time by 10 seconds ( heartbeatThrottle )
        testScheduler.advanceTimeBy(11.seconds)
        testScheduler.runCurrent()

        // Only one message should be published, since 10 second heartbeatThrottle is passed
        scope.launch {
            repeat(5) {
                typing.keystroke()
            }
        }
        testScheduler.runCurrent()

        coVerify(exactly = 10) { typing.keystroke() }

        verify(exactly = 2) { typingChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)
        assertEquals(DEFAULT_CLIENT_ID, publishedMessage?.data)
    }

    /**
     * @spec CHA-T13, CHA-T10a1, CHA-T13b3
     */
    @Test
    fun `On typingStart event received, heartbeatThrottle timeout is set, emits self stop event if no more event received`() = runTest {
        val testScheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val typing = DefaultTyping(room, dispatcher)

        val typingEvents = mutableListOf<TypingEvent>()
        typing.subscribe {
            typingEvents.add(it)
        }
        // Receive mock typing start event
        typing.processEvent(TypingEventType.Started, DEFAULT_CLIENT_ID)

        assertWaiter { typingEvents.size == 1 }
        assertEquals(setOf(DEFAULT_CLIENT_ID), typingEvents[0].currentlyTyping)
        assertEquals(TypingEventType.Started, typingEvents[0].change.type)
        assertEquals(DEFAULT_CLIENT_ID, typingEvents[0].change.clientId)

        // Emits stop event after 12 seconds, heartbeatThrottle (10 sec) + timeoutMs (2 sec) = 12 seconds
        testScheduler.advanceTimeBy(12.seconds)
        testScheduler.runCurrent()

        assertWaiter { typingEvents.size == 2 }
        assertEquals(emptySet<String>(), typingEvents[1].currentlyTyping)
        assertEquals(TypingEventType.Stopped, typingEvents[1].change.type)
        assertEquals(DEFAULT_CLIENT_ID, typingEvents[1].change.clientId)
    }

    /**
     * @spec CHA-T5, CHA-T14, CHA-T5e, CHA-T5d
     */
    @Test
    fun `If typing stop is called, the heartbeatThrottle timeout is cancelled, the client sends stop event`() = runTest {
        val typing = spyk(DefaultTyping(room))

        var publishedMessage: Message? = null
        every {
            typingChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }

        typing.keystroke()

        coVerify(exactly = 1) { typing.keystroke() }

        verify(exactly = 1) { typingChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)
        assertEquals(DEFAULT_CLIENT_ID, publishedMessage?.data)

        typing.stop()

        coVerify(exactly = 1) { typing.stop() }

        verify(exactly = 2) { typingChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Stopped.eventName, publishedMessage?.name)
        assertEquals(DEFAULT_CLIENT_ID, publishedMessage?.data)
    }

    @Test
    fun `asFlow() should automatically unsubscribe then it's done`() = runTest {
        val typing: Typing = mockk()
        val subscription: Subscription = mockk()
        lateinit var callback: Typing.Listener

        every { typing.subscribe(any()) } answers {
            callback = firstArg()
            subscription
        }

        typing.asFlow().test {
            val event = mockk<TypingEvent>()
            callback.onEvent(event)
            assertEquals(event, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }
}
