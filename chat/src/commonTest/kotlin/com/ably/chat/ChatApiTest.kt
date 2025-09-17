package com.ably.chat

import com.ably.pubsub.RealtimeClient
import com.google.gson.JsonObject
import io.ably.lib.types.AblyException
import io.ably.lib.types.MessageAction
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatApiTest {

    private val realtime = mockk<RealtimeClient>(relaxed = true)
    private val chatApi =
        ChatApi(realtime, "clientId", parentLogger = EmptyLogger(DefaultLogContext(tag = "TEST")))

    /**
     * @nospec
     */
    @Test
    fun `getMessages should ignore unknown fields for Chat Backend`() = runTest {
        mockMessagesApiResponse(
            realtime,
            listOf(
                JsonObject().apply {
                    addProperty("foo", "bar")
                    add(MessageProperty.Metadata, JsonObject())
                    addProperty(MessageProperty.Serial, "timeserial")
                    addProperty(MessageProperty.ClientId, "clientId")
                    addProperty(MessageProperty.Text, "hello")
                    addProperty(MessageProperty.CreatedAt, 1_000_000)
                    addProperty(MessageProperty.Action, "message.create")
                    addProperty(MessageProperty.Version, "timeserial")
                    addProperty(MessageProperty.Timestamp, 1_000_000)
                },
            ),
        )

        val messages = chatApi.getMessages("roomName", QueryOptions())

        assertEquals(
            listOf(
                DefaultMessage(
                    serial = "timeserial",
                    clientId = "clientId",
                    text = "hello",
                    createdAt = 1_000_000L,
                    metadata = MessageMetadata(),
                    headers = mapOf(),
                    action = MessageAction.MESSAGE_CREATE,
                    version = "timeserial",
                    timestamp = 1_000_000L,
                ),
            ),
            messages.items,
        )
    }

    /**
     * @nospec
     */
    @Test
    fun `getMessages should throws AblyException if some required fields are missing`() = runTest {
        mockMessagesApiResponse(
            realtime,
            listOf(
                JsonObject().apply {
                    addProperty("foo", "bar")
                    addProperty(MessageProperty.Action, "message.create")
                },
            ),
        )

        val exception = assertThrows(AblyException::class.java) {
            runBlocking { chatApi.getMessages("roomName", QueryOptions()) }
        }

        assertTrue(exception.message!!.matches(""".*Required field "\w+" is missing""".toRegex()))
    }

    /**
     * @nospec
     */
    @Test
    fun `getMessages should process message without text for deletes`() = runTest {
        mockMessagesApiResponse(
            realtime,
            listOf(
                JsonObject().apply {
                    add(MessageProperty.Metadata, JsonObject())
                    addProperty(MessageProperty.Serial, "timeserial")
                    addProperty(MessageProperty.ClientId, "clientId")
                    addProperty(MessageProperty.CreatedAt, 1_000_000)
                    addProperty(MessageProperty.Action, "message.delete")
                    addProperty(MessageProperty.Version, "timeserial")
                    addProperty(MessageProperty.Timestamp, 1_000_000)
                },
            ),
        )

        assertEquals(
            listOf(
                DefaultMessage(
                    serial = "timeserial",
                    clientId = "clientId",
                    text = "",
                    createdAt = 1_000_000L,
                    metadata = MessageMetadata(),
                    headers = mapOf(),
                    action = MessageAction.MESSAGE_DELETE,
                    version = "timeserial",
                    timestamp = 1_000_000L,
                ),
            ),
            chatApi.getMessages("roomName", QueryOptions()).items,
        )
    }

    /**
     * @nospec
     */
    @Test
    fun `sendMessage should ignore unknown fields for Chat Backend`() = runTest {
        mockSendMessageApiResponse(
            realtime,
            JsonObject().apply {
                addProperty("foo", "bar")
                addProperty(MessageProperty.Serial, "timeserial")
                addProperty(MessageProperty.CreatedAt, 1_000_000)
            },
        )

        val message = chatApi.sendMessage("roomName", SendMessageParams(text = "hello"))

        assertEquals(
            DefaultMessage(
                serial = "timeserial",
                clientId = "clientId",
                text = "hello",
                createdAt = 1_000_000L,
                headers = mapOf(),
                metadata = MessageMetadata(),
                action = MessageAction.MESSAGE_CREATE,
                version = "timeserial",
                timestamp = 1_000_000L,
            ),
            message,
        )
    }

    /**
     * @nospec
     */
    @Test
    fun `sendMessage should throw exception if 'serial' field is not presented`() = runTest {
        mockSendMessageApiResponse(
            realtime,
            JsonObject().apply {
                addProperty("foo", "bar")
                addProperty(MessageProperty.CreatedAt, 1_000_000)
            },
        )

        assertThrows(AblyException::class.java) {
            runBlocking { chatApi.sendMessage("roomName", SendMessageParams(text = "hello")) }
        }
    }

    /**
     * @nospec
     */
    @Test
    fun `getOccupancy should throw exception if 'connections' field is not presented`() = runTest {
        mockOccupancyApiResponse(
            realtime,
            JsonObject().apply {
                addProperty("presenceMembers", 1_000)
            },
        )

        assertThrows(AblyException::class.java) {
            runBlocking { chatApi.getOccupancy("roomName") }
        }
    }
}
