package com.ably.chat.integration

import com.ably.chat.BuildConfig
import com.ably.chat.ChatMessageEvent
import com.ably.chat.MainDispatcherRule
import com.ably.chat.Message
import com.ably.chat.MessageMetadata
import com.ably.chat.RoomStatus
import com.ably.chat.assertWaiter
import com.ably.chat.copy
import com.ably.chat.room.RoomOptionsWithAllFeatures
import com.google.gson.JsonObject
import io.ably.lib.realtime.channelOptions
import io.ably.lib.types.MessageAction
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class MessagesIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * Spec: CHA-M3, CHA-M4
     */
    @Test
    fun `should be able to send and retrieve messages without room features`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomName = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomName)

        room.attach()

        val messageEvent = CompletableDeferred<ChatMessageEvent>()

        room.messages.subscribe { messageEvent.complete(it) }
        room.messages.send("hello")

        assertEquals(
            "hello",
            messageEvent.await().message.text,
        )
    }

    /**
     * Spec: CHA-M3, CHA-M4
     */
    @Test
    fun `should be able to send and retrieve messages with all room features enabled`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomName = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomName, RoomOptionsWithAllFeatures)

        room.attach()

        val messageEvent = CompletableDeferred<ChatMessageEvent>()
        room.messages.subscribe { messageEvent.complete(it) }

        val metadata = MessageMetadata()
        metadata.addProperty("foo", "bar")
        val headers = mapOf("headerKey" to "headerValue")
        val sentMessage = room.messages.send("hello", metadata, headers)

        val receivedMessage = messageEvent.await().message

        assertEquals(MessageAction.MESSAGE_CREATE, receivedMessage.action)
        assertEquals("hello", receivedMessage.text)
        assertEquals("sandbox-client", receivedMessage.clientId)
        assertTrue(receivedMessage.serial.isNotEmpty())
        assertEquals(receivedMessage.serial, receivedMessage.version)
        assertEquals(receivedMessage.createdAt, receivedMessage.timestamp)
        assertEquals(metadata.toString(), receivedMessage.metadata.toString())
        assertEquals(headers, receivedMessage.headers)
        assertEquals(null, receivedMessage.operation)

        // check for sentMessage fields against receivedMessage fields
        assertEquals(sentMessage.serial, receivedMessage.serial)
        assertEquals(sentMessage.clientId, receivedMessage.clientId)
        assertEquals(sentMessage.text, receivedMessage.text)
        assertEquals(sentMessage.createdAt, receivedMessage.createdAt)
        assertEquals(sentMessage.metadata.toString(), receivedMessage.metadata.toString())
        assertEquals(sentMessage.headers, receivedMessage.headers)
        assertEquals(sentMessage.action, receivedMessage.action)
        assertEquals(sentMessage.version, receivedMessage.version)
        assertEquals(sentMessage.timestamp, receivedMessage.timestamp)
        assertEquals(sentMessage.operation, receivedMessage.operation)
    }

    /**
     * Spec: CHA-M3, CHA-M6
     */
    @Test
    fun `should be able to send and retrieve messages from history`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomName = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomName)

        room.attach()

        val metadata = MessageMetadata()
        metadata.addProperty("foo", "bar")
        val headers = mapOf("headerKey" to "headerValue")
        val sentMessage = room.messages.send("hello", metadata, headers)

        lateinit var messages: List<Message>

        assertWaiter {
            messages = room.messages.history().items
            messages.isNotEmpty()
        }
        assertEquals(1, messages.size)
        val historyMessage = messages.first()

        assertEquals(MessageAction.MESSAGE_CREATE, historyMessage.action)
        assertEquals("hello", historyMessage.text)
        assertEquals("sandbox-client", historyMessage.clientId)
        assertTrue(historyMessage.serial.isNotEmpty())
        assertEquals(historyMessage.serial, historyMessage.version)
        assertEquals(historyMessage.createdAt, historyMessage.timestamp)
        assertEquals(metadata.toString(), historyMessage.metadata.toString())
        assertEquals(headers, historyMessage.headers)
        assertEquals(null, historyMessage.operation)

        // check for sentMessage fields against historyMessage fields
        assertEquals(sentMessage.serial, historyMessage.serial)
        assertEquals(sentMessage.clientId, historyMessage.clientId)
        assertEquals(sentMessage.text, historyMessage.text)
        assertEquals(sentMessage.createdAt, historyMessage.createdAt)
        assertEquals(sentMessage.metadata.toString(), historyMessage.metadata.toString())
        assertEquals(sentMessage.headers, historyMessage.headers)
        assertEquals(sentMessage.action, historyMessage.action)
        assertEquals(sentMessage.version, historyMessage.version)
        assertEquals(sentMessage.timestamp, historyMessage.timestamp)
        assertEquals(sentMessage.operation, historyMessage.operation)
    }

    /**
     * Spec: CHA-M8, CHA-M4
     */
    @Test
    fun `should be able to update a sent message`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomName = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomName)

        room.attach()
        assertWaiter { room.status == RoomStatus.Attached }

        val receivedMsges = mutableListOf<Message>()
        room.messages.subscribe { receivedMsges.add(it.message) }

        val metadata = MessageMetadata()
        metadata.addProperty("foo", "bar")
        val sentMessage = room.messages.send("hello", metadata, mapOf("headerKey" to "headerValue"))
        assertWaiter { receivedMsges.size == 1 }

        val updatedText = "hello updated"
        val updatedMetadata = MessageMetadata()
        updatedMetadata.addProperty("foo", "baz")
        val headers = mapOf("headerKey" to "headerValue")

        val opDescription = "Updating message"
        val opMetadata = mapOf("operation" to "update")

        val messageCopy = sentMessage.copy(text = updatedText, metadata = updatedMetadata, headers = headers)
        val updatedMessage = room.messages.update(
            messageCopy,
            opDescription,
            opMetadata,
        )

        assertEquals(MessageAction.MESSAGE_UPDATE, updatedMessage.action)
        assertEquals(sentMessage.serial, updatedMessage.serial)
        assertEquals(sentMessage.createdAt, updatedMessage.createdAt)

        assertWaiter { receivedMsges.size == 2 }
        val receivedMsg2 = receivedMsges.last()

        assertEquals(updatedMessage.text, receivedMsg2.text)
        assertEquals(updatedMessage.metadata.toString(), receivedMsg2.metadata.toString())
        assertEquals(updatedMessage.operation?.description, receivedMsg2.operation?.description)
        assertEquals(updatedMessage.operation?.metadata, receivedMsg2.operation?.metadata)
        assertEquals(updatedMessage.operation?.clientId, receivedMsg2.operation?.clientId)
        assertEquals(updatedMessage.headers, receivedMsg2.headers)
        assertEquals(updatedMessage.serial, receivedMsg2.serial)
        assertEquals(updatedMessage.version, receivedMsg2.version)
        assertEquals(updatedMessage.createdAt, receivedMsg2.createdAt)
        assertEquals(updatedMessage.timestamp, receivedMsg2.timestamp)
        assertEquals(updatedMessage.clientId, receivedMsg2.clientId)
        assertEquals(updatedMessage.action, receivedMsg2.action)
    }

    /**
     * Spec: CHA-M9, CHA-M4
     */
    @Test
    fun `should be able to delete a sent message`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomName = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomName)

        room.attach()
        assertWaiter { room.status == RoomStatus.Attached }

        val receivedMsges = mutableListOf<Message>()
        room.messages.subscribe { receivedMsges.add(it.message) }

        val metadata = MessageMetadata()
        metadata.addProperty("foo", "bar")
        val sentMessage = room.messages.send("hello", metadata, mapOf("headerKey" to "headerValue"))
        assertWaiter { receivedMsges.size == 1 }

        val description = "Deleting message"
        val opMetadata = mapOf("operation" to "delete")

        val deletedMessage = room.messages.delete(
            message = sentMessage,
            operationDescription = description,
            operationMetadata = opMetadata,
        )

        assertEquals(MessageAction.MESSAGE_DELETE, deletedMessage.action)
        assertEquals(sentMessage.serial, deletedMessage.serial)
        assertEquals(sentMessage.createdAt, deletedMessage.createdAt)

        assertWaiter { receivedMsges.size == 2 }
        val receivedMsg2 = receivedMsges.last()

        assertEquals("", receivedMsg2.text)
        assertEquals(JsonObject(), receivedMsg2.metadata)
        assertEquals(mapOf<String, String>(), receivedMsg2.headers)
        assertEquals(deletedMessage.operation?.description, receivedMsg2.operation?.description)
        assertEquals(deletedMessage.operation?.metadata, receivedMsg2.operation?.metadata)
        assertEquals(deletedMessage.operation?.clientId, receivedMsg2.operation?.clientId)
        assertEquals(deletedMessage.serial, receivedMsg2.serial)
        assertEquals(deletedMessage.version, receivedMsg2.version)
        assertEquals(deletedMessage.createdAt, receivedMsg2.createdAt)
        assertEquals(deletedMessage.timestamp, receivedMsg2.timestamp)
        assertEquals(deletedMessage.clientId, receivedMsg2.clientId)
        assertEquals(deletedMessage.action, receivedMsg2.action)
    }

    @Test
    fun `messages channel should include agent channel param`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomName = UUID.randomUUID().toString()
        val room = chatClient.rooms.get(roomName)
        assertEquals(
            "chat-kotlin/${BuildConfig.APP_VERSION}",
            room.messages.channel.channelOptions?.params?.get("agent"),
        )
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
