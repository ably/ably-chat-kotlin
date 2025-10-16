package com.ably.chat.integration

import com.ably.chat.MainDispatcherRule
import com.ably.chat.TypingEventType
import com.ably.chat.TypingSetEvent
import com.ably.chat.TypingSetEventType
import com.ably.chat.assertWaiter
import com.ably.chat.get
import com.ably.chat.typing
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class TypingIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * @spec CHA-T4, CHA-T9
     */
    @Test
    fun `should return typing start indication for client`() = runTest {
        val chatClient1 = sandbox.createSandboxChatClient("client1")
        val chatClient2 = sandbox.createSandboxChatClient("client2")
        val roomName = UUID.randomUUID().toString()

        val chatClient1Room = chatClient1.rooms.get(roomName) { typing { heartbeatThrottle = 10.seconds } }
        chatClient1Room.attach()
        val chatClient2Room = chatClient2.rooms.get(roomName) { typing { heartbeatThrottle = 10.seconds } }
        chatClient2Room.attach()

        assertEquals(emptySet<String>(), chatClient1Room.typing.current)
        assertEquals(emptySet<String>(), chatClient2Room.typing.current)

        val startTypingEventDeferred = CompletableDeferred<TypingSetEvent>()
        chatClient2Room.typing.subscribe {
            startTypingEventDeferred.complete(it)
        }
        chatClient1Room.typing.keystroke()
        val startTypingEvent = startTypingEventDeferred.await()

        assertEquals(setOf("client1"), startTypingEvent.currentlyTyping)
        assertEquals(TypingSetEventType.SetChanged, startTypingEvent.type)
        assertEquals("client1", startTypingEvent.change.clientId)
        assertEquals(TypingEventType.Started, startTypingEvent.change.type)

        assertEquals(setOf("client1"), chatClient1Room.typing.current)
        assertEquals(setOf("client1"), chatClient2Room.typing.current)
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
        val roomName = UUID.randomUUID().toString()
        val chatClient1Room = chatClient1.rooms.get(roomName) { typing { heartbeatThrottle = 10.seconds } }
        chatClient1Room.attach()
        val chatClient2Room = chatClient2.rooms.get(roomName) { typing { heartbeatThrottle = 10.seconds } }
        chatClient2Room.attach()
        val chatClient3Room = chatClient3.rooms.get(roomName) { typing { heartbeatThrottle = 10.seconds } }
        chatClient3Room.attach()

        // Client 1, Client 2 starts typing
        val startTypingEvents = mutableListOf<TypingSetEvent>()
        chatClient2Room.typing.subscribe {
            startTypingEvents.add(it)
        }
        chatClient1Room.typing.keystroke()
        chatClient2Room.typing.keystroke()

        // Two start typing events received
        assertWaiter { startTypingEvents.size == 2 }

        val startTypingEvent1 = startTypingEvents[0]
        assertEquals(setOf("client1"), startTypingEvent1.currentlyTyping)
        assertEquals(TypingSetEventType.SetChanged, startTypingEvent1.type)
        assertEquals("client1", startTypingEvent1.change.clientId)
        assertEquals(TypingEventType.Started, startTypingEvent1.change.type)

        val startTypingEvent2 = startTypingEvents[1]
        assertEquals(setOf("client1", "client2"), startTypingEvent2.currentlyTyping)
        assertEquals(TypingSetEventType.SetChanged, startTypingEvent2.type)
        assertEquals("client2", startTypingEvent2.change.clientId)
        assertEquals(TypingEventType.Started, startTypingEvent2.change.type)

        assertWaiter { chatClient1Room.typing.current == setOf("client1", "client2") }
        assertWaiter { chatClient2Room.typing.current == setOf("client1", "client2") }
        assertWaiter { chatClient3Room.typing.current == setOf("client1", "client2") }

        // Client 1 stops typing
        val stopTypingEventDeferred1 = CompletableDeferred<TypingSetEvent>()
        chatClient2Room.typing.subscribe {
            stopTypingEventDeferred1.complete(it)
        }
        chatClient1Room.typing.stop()

        val stopTypingEvent1 = stopTypingEventDeferred1.await()
        assertEquals(setOf("client2"), stopTypingEvent1.currentlyTyping)
        assertEquals(TypingSetEventType.SetChanged, stopTypingEvent1.type)
        assertEquals("client1", stopTypingEvent1.change.clientId)
        assertEquals(TypingEventType.Stopped, stopTypingEvent1.change.type)

        assertWaiter { chatClient1Room.typing.current == setOf("client2") }
        assertWaiter { chatClient2Room.typing.current == setOf("client2") }
        assertWaiter { chatClient3Room.typing.current == setOf("client2") }

        // Client 2 stops typing
        val stopTypingEventDeferred2 = CompletableDeferred<TypingSetEvent>()
        chatClient2Room.typing.subscribe {
            stopTypingEventDeferred2.complete(it)
        }
        chatClient2Room.typing.stop()

        val stopTypingEvent2 = stopTypingEventDeferred2.await()
        assertEquals(emptySet<String>(), stopTypingEvent2.currentlyTyping)
        assertEquals(TypingSetEventType.SetChanged, stopTypingEvent2.type)
        assertEquals("client2", stopTypingEvent2.change.clientId)
        assertEquals(TypingEventType.Stopped, stopTypingEvent2.change.type)

        assertWaiter { chatClient1Room.typing.current.isEmpty() }
        assertWaiter { chatClient2Room.typing.current.isEmpty() }
        assertWaiter { chatClient3Room.typing.current.isEmpty() }
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
