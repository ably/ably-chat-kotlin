package com.ably.chat

import com.ably.chat.json.jsonObject
import com.ably.pubsub.RealtimeClient
import io.ably.lib.types.AblyException
import io.ably.lib.types.MessageAction
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
                jsonObject {
                    put("foo", "bar")
                    putObject(MessageProperty.Metadata) {
                    }
                    put(MessageProperty.Serial, "timeserial")
                    put(MessageProperty.ClientId, "clientId")
                    put(MessageProperty.Text, "hello")
                    put(MessageProperty.Timestamp, 1_000_000)
                    put(MessageProperty.Action, "message.create")
                    putObject(MessageProperty.Version) {
                        put(MessageProperty.Serial, "timeserial")
                        put(MessageProperty.Timestamp, 1_000_000)
                    }
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
                    timestamp = 1_000_000L,
                    metadata = MessageMetadata(),
                    headers = mapOf(),
                    action = MessageAction.MESSAGE_CREATE,
                    version = DefaultMessageVersion(
                        serial = "timeserial",
                        timestamp = 1_000_000L,
                        clientId = "clientId",
                    ),
                ),
            ),
            messages.items,
        )
    }

    /**
     * @nospec
     */
    @Test
    fun `getMessages should ignore messages if some required fields are missing`() = runTest {
        mockMessagesApiResponse(
            realtime,
            listOf(
                jsonObject {
                    put("foo", "bar")
                    put(MessageProperty.Action, "message.create")
                },
            ),
        )

        assertEquals(listOf<Message>(), chatApi.getMessages("roomName", QueryOptions()).items)
    }

    /**
     * @nospec
     */
    @Test
    fun `getMessages should process message without text for deletes`() = runTest {
        mockMessagesApiResponse(
            realtime,
            listOf(
                jsonObject {
                    putObject(MessageProperty.Metadata) {}
                    put(MessageProperty.Serial, "timeserial")
                    put(MessageProperty.ClientId, "clientId")
                    put(MessageProperty.Timestamp, 1_000_000)
                    put(MessageProperty.Action, "message.delete")
                    putObject(MessageProperty.Version) {
                        put(MessageProperty.Serial, "timeserial")
                        put(MessageProperty.Timestamp, 1_000_000)
                    }
                },
            ),
        )

        assertEquals(
            listOf(
                DefaultMessage(
                    serial = "timeserial",
                    clientId = "clientId",
                    text = "",
                    timestamp = 1_000_000L,
                    metadata = MessageMetadata(),
                    headers = mapOf(),
                    action = MessageAction.MESSAGE_DELETE,
                    version = DefaultMessageVersion(
                        serial = "timeserial",
                        timestamp = 1_000_000L,
                        clientId = "clientId",
                    ),
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
            jsonObject {
                put("foo", "bar")
                put(MessageProperty.Serial, "timeserial")
                put(MessageProperty.Timestamp, 1_000_000)
            },
        )

        val message = chatApi.sendMessage("roomName", SendMessageParams(text = "hello"))

        assertEquals(
            DefaultMessage(
                serial = "timeserial",
                clientId = "clientId",
                text = "hello",
                timestamp = 1_000_000L,
                headers = mapOf(),
                metadata = MessageMetadata(),
                action = MessageAction.MESSAGE_CREATE,
                version = DefaultMessageVersion(
                    serial = "timeserial",
                    timestamp = 1_000_000L,
                    clientId = "clientId",
                ),
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
            jsonObject {
                put("foo", "bar")
                put(MessageProperty.Timestamp, 1_000_000)
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
            jsonObject {
                put("presenceMembers", 1_000)
            },
        )

        assertThrows(AblyException::class.java) {
            runBlocking { chatApi.getOccupancy("roomName") }
        }
    }
}
