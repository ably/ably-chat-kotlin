package com.ably.chat.integration

import com.ably.chat.TypingEvent
import com.ably.chat.TypingEventType
import com.ably.chat.assertWaiter
import com.ably.chat.typing
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class TypingIntegrationTest {

    /**
     * @spec CHA-T4, CHA-T9
     */
    @Test
    fun `should return typing start indication for client`() = runTest {
        val chatClient1 = sandbox.createSandboxChatClient("client1")
        val chatClient2 = sandbox.createSandboxChatClient("client2")
        val roomId = UUID.randomUUID().toString()

        val chatClient1Room = chatClient1.rooms.get(roomId) { typing { heartbeatThrottle = 10.seconds } }
        chatClient1Room.attach()
        val chatClient2Room = chatClient2.rooms.get(roomId) { typing { heartbeatThrottle = 10.seconds } }
        chatClient2Room.attach()

        assertEquals(emptySet<String>(), chatClient1Room.typing.get())
        assertEquals(emptySet<String>(), chatClient2Room.typing.get())

        val startTypingEventDeferred = CompletableDeferred<TypingEvent>()
        chatClient2Room.typing.subscribe {
            startTypingEventDeferred.complete(it)
        }
        chatClient1Room.typing.keystroke()
        val startTypingEvent = startTypingEventDeferred.await()

        assertEquals(setOf("client1"), startTypingEvent.currentlyTyping)
        assertEquals("client1", startTypingEvent.change.clientId)
        assertEquals(TypingEventType.Started, startTypingEvent.change.type)

        assertEquals(setOf("client1"), chatClient1Room.typing.get())
        assertEquals(setOf("client1"), chatClient2Room.typing.get())
    }

    /**
     * @spec CHA-T5, CHA-T9
     */
    @Test
    fun `should return typing stop indication for clients`() = runTest {
        // Set up 3 chat Clients
        val chatClient1 = sandbox.createSandboxChatClient("client1")
        val chatClient2 = sandbox.createSandboxChatClient("client2")
        val chatClient3 = sandbox.createSandboxChatClient("client3")
        val roomId = UUID.randomUUID().toString()
        val chatClient1Room = chatClient1.rooms.get(roomId) { typing { heartbeatThrottle = 10.seconds } }
        chatClient1Room.attach()
        val chatClient2Room = chatClient2.rooms.get(roomId) { typing { heartbeatThrottle = 10.seconds } }
        chatClient2Room.attach()
        val chatClient3Room = chatClient3.rooms.get(roomId) { typing { heartbeatThrottle = 10.seconds } }
        chatClient3Room.attach()

        // Client 1, Client 2 starts typing
        val startTypingEvents = mutableListOf<TypingEvent>()
        chatClient2Room.typing.subscribe {
            startTypingEvents.add(it)
        }
        chatClient1Room.typing.keystroke()
        chatClient2Room.typing.keystroke()

        // Two start typing events received
        assertWaiter { startTypingEvents.size == 2 }

        val startTypingEvent1 = startTypingEvents[0]
        assertEquals(setOf("client1"), startTypingEvent1.currentlyTyping)
        assertEquals("client1", startTypingEvent1.change.clientId)
        assertEquals(TypingEventType.Started, startTypingEvent1.change.type)

        val startTypingEvent2 = startTypingEvents[1]
        assertEquals(setOf("client1", "client2"), startTypingEvent2.currentlyTyping)
        assertEquals("client2", startTypingEvent2.change.clientId)
        assertEquals(TypingEventType.Started, startTypingEvent2.change.type)

        assertWaiter { chatClient1Room.typing.get() == setOf("client1", "client2") }
        assertWaiter { chatClient2Room.typing.get() == setOf("client1", "client2") }
        assertWaiter { chatClient3Room.typing.get() == setOf("client1", "client2") }

        // Client 1 stops typing
        val stopTypingEventDeferred1 = CompletableDeferred<TypingEvent>()
        chatClient2Room.typing.subscribe {
            stopTypingEventDeferred1.complete(it)
        }
        chatClient1Room.typing.stop()

        val stopTypingEvent1 = stopTypingEventDeferred1.await()
        assertEquals(setOf("client2"), stopTypingEvent1.currentlyTyping)
        assertEquals("client1", stopTypingEvent1.change.clientId)
        assertEquals(TypingEventType.Stopped, stopTypingEvent1.change.type)

        assertWaiter { chatClient1Room.typing.get() == setOf("client2") }
        assertWaiter { chatClient2Room.typing.get() == setOf("client2") }
        assertWaiter { chatClient3Room.typing.get() == setOf("client2") }

        // Client 2 stops typing
        val stopTypingEventDeferred2 = CompletableDeferred<TypingEvent>()
        chatClient2Room.typing.subscribe {
            stopTypingEventDeferred2.complete(it)
        }
        chatClient2Room.typing.stop()

        val stopTypingEvent2 = stopTypingEventDeferred2.await()
        assertEquals(emptySet<String>(), stopTypingEvent2.currentlyTyping)
        assertEquals("client2", stopTypingEvent2.change.clientId)
        assertEquals(TypingEventType.Stopped, stopTypingEvent2.change.type)

        assertWaiter { chatClient1Room.typing.get().isEmpty() }
        assertWaiter { chatClient2Room.typing.get().isEmpty() }
        assertWaiter { chatClient3Room.typing.get().isEmpty() }
    }

    companion object {
        private lateinit var sandbox: Sandbox

        @JvmStatic
        @BeforeClass
        fun setUp() = runTest {
            sandbox = Sandbox.createInstance()
        }
    }
}
