package com.ably.chat.integration

import com.ably.chat.RoomOptions
import com.ably.chat.TypingEvent
import com.ably.chat.TypingOptions
import com.ably.chat.assertWaiter
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class TypingIntegrationTest {

    @Test
    fun `should receive typing start indication for client`() = runTest {
        val chatClient1 = sandbox.createSandboxChatClient("client1")
        val chatClient2 = sandbox.createSandboxChatClient("client2")
        val roomId = UUID.randomUUID().toString()
        val roomOptions = RoomOptions(typing = TypingOptions(timeoutMs = 10_000))
        val chatClient1Room = chatClient1.rooms.get(roomId, roomOptions)
        chatClient1Room.attach()
        val chatClient2Room = chatClient2.rooms.get(roomId, roomOptions)
        chatClient2Room.attach()

        val deferredValue = CompletableDeferred<TypingEvent>()
        chatClient2Room.typing.subscribe {
            deferredValue.complete(it)
        }
        chatClient1Room.typing.start()
        val typingEvent = deferredValue.await()
        assertEquals(setOf("client1"), typingEvent.currentlyTyping)
        assertEquals(setOf("client1"), chatClient2Room.typing.get())
    }

    /**
     * @spec CHA-T5, CHA-T9
     */
    @Test
    fun `should receive consecutive typing start and stop indication for clients`() = runTest {
        // Set up 3 chat Clients
        val chatClient1 = sandbox.createSandboxChatClient("client1")
        val chatClient2 = sandbox.createSandboxChatClient("client2")
        val chatClient3 = sandbox.createSandboxChatClient("client3")
        val roomId = UUID.randomUUID().toString()
        val chatClient1Room = chatClient1.rooms.get(roomId, RoomOptions.default)
        chatClient1Room.attach()
        val chatClient2Room = chatClient2.rooms.get(roomId, RoomOptions.default)
        chatClient2Room.attach()
        val chatClient3Room = chatClient3.rooms.get(roomId, RoomOptions.default)
        chatClient3Room.attach()

        val startTypingEvents = mutableListOf<TypingEvent>()
        chatClient2Room.typing.subscribe {
            startTypingEvents.add(it)
        }
        // Client 1, Client 2 starts typing
        chatClient1Room.typing.start()
        chatClient2Room.typing.start()

        // Two start typing events received
        assertWaiter { startTypingEvents.size == 2 }

        val startTypingEvent1 = startTypingEvents[0]
        assertEquals(setOf("client1"), startTypingEvent1.currentlyTyping)

        val startTypingEvent2 = startTypingEvents[1]
        assertEquals(setOf("client1", "client2"), startTypingEvent2.currentlyTyping)

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

        assertWaiter { chatClient1Room.typing.get().isEmpty() }
        assertWaiter { chatClient2Room.typing.get().isEmpty() }
        assertWaiter { chatClient3Room.typing.get().isEmpty() }

        // Client 1, Client 2 starts typing again
        chatClient1Room.typing.start()
        chatClient2Room.typing.start()

        // Two start typing events received
        assertWaiter { startTypingEvents.size == 6 }

        assertWaiter { chatClient1Room.typing.get() == setOf("client1", "client2") }
        assertWaiter { chatClient2Room.typing.get() == setOf("client1", "client2") }
        assertWaiter { chatClient3Room.typing.get() == setOf("client1", "client2") }

        // Client 1 stops typing
        chatClient1Room.typing.stop()

        assertWaiter { startTypingEvents.size == 7 }

        assertWaiter { chatClient1Room.typing.get() == setOf("client2") }
        assertWaiter { chatClient2Room.typing.get() == setOf("client2") }
        assertWaiter { chatClient3Room.typing.get() == setOf("client2") }
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
