package com.ably.chat.integration

import com.ably.annotations.InternalAPI
import com.ably.chat.BuildConfig
import com.ably.chat.ChatMessageEvent
import com.ably.chat.MainDispatcherRule
import com.ably.chat.Message
import com.ably.chat.MessageAction
import com.ably.chat.PlatformSpecificAgent
import com.ably.chat.RoomStatus
import com.ably.chat.assertWaiter
import com.ably.chat.copy
import com.ably.chat.delete
import com.ably.chat.json.jsonObject
import com.ably.chat.room.RoomOptionsWithAllFeatures
import com.ably.chat.update
import io.ably.lib.realtime.channelOptions
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

        val metadata = jsonObject {
            put("foo", "bar")
        }
        val headers = mapOf("headerKey" to "headerValue")
        val sentMessage = room.messages.send("hello", metadata, headers)

        val receivedMessage = messageEvent.await().message

        assertEquals(MessageAction.MessageCreate, receivedMessage.action)
        assertEquals("hello", receivedMessage.text)
        assertEquals("sandbox-client", receivedMessage.clientId)
        assertTrue(receivedMessage.serial.isNotEmpty())
        assertEquals(receivedMessage.serial, receivedMessage.version.serial)
        assertEquals(receivedMessage.timestamp, receivedMessage.version.timestamp)
        assertEquals(metadata, receivedMessage.metadata)
        assertEquals(headers, receivedMessage.headers)

        // check for sentMessage fields against receivedMessage fields
        assertEquals(sentMessage.serial, receivedMessage.serial)
        assertEquals(sentMessage.clientId, receivedMessage.clientId)
        assertEquals(sentMessage.text, receivedMessage.text)
        assertEquals(sentMessage.timestamp, receivedMessage.timestamp)
        assertEquals(sentMessage.metadata, receivedMessage.metadata)
        assertEquals(sentMessage.headers, receivedMessage.headers)
        assertEquals(sentMessage.action, receivedMessage.action)
        assertEquals(sentMessage.version, receivedMessage.version)
        assertEquals(sentMessage.timestamp, receivedMessage.timestamp)
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

        val metadata = jsonObject {
            put("foo", "bar")
        }
        val headers = mapOf("headerKey" to "headerValue")
        val sentMessage = room.messages.send("hello", metadata, headers)

        lateinit var messages: List<Message>

        assertWaiter {
            messages = room.messages.history().items
            messages.isNotEmpty()
        }
        assertEquals(1, messages.size)
        val historyMessage = messages.first()

        assertEquals(MessageAction.MessageCreate, historyMessage.action)
        assertEquals("hello", historyMessage.text)
        assertEquals("sandbox-client", historyMessage.clientId)
        assertTrue(historyMessage.serial.isNotEmpty())
        assertEquals(historyMessage.serial, historyMessage.version.serial)
        assertEquals(historyMessage.timestamp, historyMessage.version.timestamp)
        assertEquals(metadata, historyMessage.metadata)
        assertEquals(headers, historyMessage.headers)

        // check for sentMessage fields against historyMessage fields
        assertEquals(sentMessage.serial, historyMessage.serial)
        assertEquals(sentMessage.clientId, historyMessage.clientId)
        assertEquals(sentMessage.text, historyMessage.text)
        assertEquals(sentMessage.timestamp, historyMessage.timestamp)
        assertEquals(sentMessage.metadata, historyMessage.metadata)
        assertEquals(sentMessage.headers, historyMessage.headers)
        assertEquals(sentMessage.action, historyMessage.action)
        assertEquals(sentMessage.version, historyMessage.version)
        assertEquals(sentMessage.timestamp, historyMessage.timestamp)
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

        val metadata = jsonObject {
            put("foo", "bar")
        }
        val sentMessage = room.messages.send("hello", metadata, mapOf("headerKey" to "headerValue"))
        assertWaiter { receivedMsges.size == 1 }

        val updatedText = "hello updated"
        val updatedMetadata = jsonObject {
            put("foo", "baz")
        }
        val headers = mapOf("headerKey" to "headerValue")

        val opDescription = "Updating message"
        val opMetadata = mapOf("operation" to "update")

        val messageCopy = sentMessage.copy(text = updatedText, metadata = updatedMetadata, headers = headers)
        val updatedMessage = room.messages.update(
            messageCopy,
            opDescription,
            opMetadata,
        )

        assertEquals(MessageAction.MessageUpdate, updatedMessage.action)
        assertEquals(sentMessage.serial, updatedMessage.serial)
        assertEquals(sentMessage.timestamp, updatedMessage.timestamp)

        assertWaiter { receivedMsges.size == 2 }
        val receivedMsg2 = receivedMsges.last()

        assertEquals(updatedMessage.text, receivedMsg2.text)
        assertEquals(updatedMessage.metadata.toString(), receivedMsg2.metadata.toString())
        assertEquals(updatedMessage.version.description, receivedMsg2.version.description)
        assertEquals(updatedMessage.version.metadata, receivedMsg2.version.metadata)
        assertEquals(updatedMessage.version.clientId, receivedMsg2.version.clientId)
        assertEquals(updatedMessage.headers, receivedMsg2.headers)
        assertEquals(updatedMessage.serial, receivedMsg2.serial)
        assertEquals(updatedMessage.version, receivedMsg2.version)
        assertEquals(updatedMessage.timestamp, receivedMsg2.timestamp)
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

        val metadata = jsonObject {
            put("foo", "bar")
        }
        val sentMessage = room.messages.send("hello", metadata, mapOf("headerKey" to "headerValue"))
        assertWaiter { receivedMsges.size == 1 }

        val description = "Deleting message"
        val opMetadata = mapOf("operation" to "delete")

        val deletedMessage = room.messages.delete(
            message = sentMessage,
            operationDescription = description,
            operationMetadata = opMetadata,
        )

        assertEquals(MessageAction.MessageDelete, deletedMessage.action)
        assertEquals(sentMessage.serial, deletedMessage.serial)
        assertEquals(sentMessage.timestamp, deletedMessage.timestamp)

        assertWaiter { receivedMsges.size == 2 }
        val receivedMsg2 = receivedMsges.last()

        assertEquals("", receivedMsg2.text)
        assertEquals(jsonObject {}, receivedMsg2.metadata)
        assertEquals(mapOf<String, String>(), receivedMsg2.headers)
        assertEquals(deletedMessage.version.description, receivedMsg2.version.description)
        assertEquals(deletedMessage.version.metadata, receivedMsg2.version.metadata)
        assertEquals(deletedMessage.version.clientId, receivedMsg2.version.clientId)
        assertEquals(deletedMessage.serial, receivedMsg2.serial)
        assertEquals(deletedMessage.version, receivedMsg2.version)
        assertEquals(deletedMessage.timestamp, receivedMsg2.timestamp)
        assertEquals(deletedMessage.timestamp, receivedMsg2.timestamp)
        assertEquals(deletedMessage.clientId, receivedMsg2.clientId)
        assertEquals(deletedMessage.action, receivedMsg2.action)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `messages channel should include agent channel param`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomName = UUID.randomUUID().toString()
        val room = chatClient.rooms.get(roomName)
        assertEquals(
            "chat-kotlin/${BuildConfig.APP_VERSION} $PlatformSpecificAgent/${BuildConfig.APP_VERSION}",
            room.channel.javaChannel.channelOptions?.params?.get("agent"),
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
