package com.ably.chat.integration

import com.ably.chat.Message
import com.ably.chat.MessageEvent
import com.ably.chat.MessageMetadata
import com.ably.chat.RoomOptions
import com.ably.chat.assertWaiter
import io.ably.lib.types.MessageAction
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class MessagesIntegrationTest {

    @Test
    fun `should be able to send and retrieve messages without room features`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomId = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomId)

        room.attach()

        val messageEvent = CompletableDeferred<MessageEvent>()

        room.messages.subscribe { messageEvent.complete(it) }
        room.messages.send("hello")

        assertEquals(
            "hello",
            messageEvent.await().message.text,
        )
    }

    @Test
    fun `should be able to send and retrieve messages with all room features enabled`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomId = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomId, RoomOptions.default)

        room.attach()

        val messageEvent = CompletableDeferred<MessageEvent>()

        room.messages.subscribe { messageEvent.complete(it) }
        room.messages.send("hello")

        assertEquals(
            "hello",
            messageEvent.await().message.text,
        )
    }

    @Test
    fun `should be able to send and retrieve messages from history`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomId = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomId)

        room.attach()
        val metadata = MessageMetadata()
        metadata.addProperty("foo", "bar")

        room.messages.send("hello", metadata)

        lateinit var messages: List<Message>

        assertWaiter {
            messages = room.messages.get().items
            messages.isNotEmpty()
        }
        assertEquals(1, messages.size)
        val message = messages.first()

        assertEquals(roomId, message.roomId)
        assertEquals(MessageAction.MESSAGE_CREATE, message.action)
        assertEquals("hello", message.text)
        assertEquals("sandbox-client", message.clientId)
        assertTrue(message.serial.isNotEmpty())
        assertEquals(message.serial, message.version)
        assertEquals(message.createdAt, message.timestamp)
        assertEquals(metadata.toString(), message.metadata.toString())
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
