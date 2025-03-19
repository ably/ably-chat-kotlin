package com.ably.chat

import app.cash.turbine.test
import com.ably.chat.room.DEFAULT_CLIENT_ID
import com.ably.chat.room.createMockChatApi
import com.ably.chat.room.createMockRealtimeChannel
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createMockRoom
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.Message
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
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
     * @spec CHA-T4a1
     */
    @Test
    fun `when a typing session is started, the client is entered into presence on the typing channel`() = runTest {
        val typing = DefaultTyping(room)
        var publishedMessage: Message? = null
        every {
            typingChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }
        typing.start()
        verify(exactly = 1) { typingChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)
        assertEquals(DEFAULT_CLIENT_ID, publishedMessage?.data)
    }

    /**
     * @spec CHA-T4a2
     */
//    @Test
//    fun `when timeout expires, the typing session is automatically ended by leaving presence`() = runTest {
//        val testScheduler = TestCoroutineScheduler()
//        val dispatcher = StandardTestDispatcher(testScheduler)
//        val scope = CoroutineScope(dispatcher)
//        val typing = DefaultTyping(room, dispatcher)
//
//        scope.launch {
//            typing.start()
//        }
//
//        testScheduler.advanceTimeBy(5000.milliseconds)
//        testScheduler.runCurrent()
//
//        verify(exactly = 1) { typingChannel.enterClient("clientId", any(), any()) }
//        verify(exactly = 1) { typingChannel.leaveClient("clientId", any(), any()) }
//    }

    /**
     * @spec CHA-T4b
     */
//    @Test
//    fun `if typing is already in progress, the timeout is extended to be timeoutMs from now`() = runTest {
//        val testScheduler = TestCoroutineScheduler()
//        val dispatcher = StandardTestDispatcher(testScheduler)
//        val scope = CoroutineScope(dispatcher)
//        val typing = DefaultTyping(room, dispatcher)
//
//        scope.launch {
//            typing.start()
//        }
//
//        testScheduler.advanceTimeBy(3000.milliseconds)
//        testScheduler.runCurrent()
//
//        scope.launch {
//            typing.start()
//        }
//
//        testScheduler.advanceTimeBy(3000.milliseconds)
//        testScheduler.runCurrent()
//
//        verify(exactly = 1) { typingChannel.enterClient("clientId", any(), any()) }
//        verify(exactly = 0) { typingChannel.leaveClient("clientId", any(), any()) }
//    }

    /**
     * @spec CHA-T5b
     */
    @Test
    fun `if typing is in progress, the timeout is cancelled, the client then leaves presence`() = runTest {
        val testScheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val typing = DefaultTyping(room, dispatcher)

        var publishedMessage: Message? = null
        every {
            typingChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }

        scope.launch {
            typing.start()
        }

        testScheduler.advanceTimeBy(1000.milliseconds)
        testScheduler.runCurrent()

        verify(exactly = 1) { typingChannel.publish(any<Message>(), any()) }
        assertEquals(TypingEventType.Started.eventName, publishedMessage?.name)
        assertEquals(DEFAULT_CLIENT_ID, publishedMessage?.data)

        scope.launch {
            typing.stop()
        }

        testScheduler.advanceTimeBy(5000.milliseconds)
        testScheduler.runCurrent()

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
