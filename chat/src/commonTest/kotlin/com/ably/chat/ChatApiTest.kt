package com.ably.chat

import com.ably.chat.json.JsonObject
import com.ably.chat.json.jsonObject
import com.ably.http.HttpMethod
import com.ably.pubsub.RealtimeClient
import io.mockk.mockk
import io.mockk.verify
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
                    metadata = JsonObject(),
                    headers = mapOf(),
                    action = MessageAction.MessageCreate,
                    version = DefaultMessageVersion(
                        serial = "timeserial",
                        timestamp = 1_000_000L,
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
                    metadata = JsonObject(),
                    headers = mapOf(),
                    action = MessageAction.MessageDelete,
                    version = DefaultMessageVersion(
                        serial = "timeserial",
                        timestamp = 1_000_000L,
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
                metadata = JsonObject(),
                action = MessageAction.MessageCreate,
                version = DefaultMessageVersion(
                    serial = "timeserial",
                    timestamp = 1_000_000L,
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

        assertThrows(ChatException::class.java) {
            runBlocking { chatApi.sendMessage("roomName", SendMessageParams(text = "hello")) }
        }
    }

    /**
     * @nospec
     */
    @Test
    fun `getOccupancy should return 0 if field is not presented`() = runTest {
        mockOccupancyApiResponse(
            realtime,
            jsonObject {
                put("presenceMembers", 1_000)
            },
        )

        assertEquals(
            DefaultOccupancyData(0, 1_000),
            chatApi.getOccupancy("roomName"),
        )
    }

    @Test
    fun `getMessages should encode path segments with special characters`() = runTest {
        val roomName = "name/with spaces, slashes and \""
        mockMessagesApiResponse(realtime, listOf(), roomName)
        chatApi.getMessages(roomName, QueryOptions())
        verify {
            realtime.requestAsync(
                "/chat/v4/rooms/name%2Fwith%20spaces%2C%20slashes%20and%20%22/messages",
                any(),
                HttpMethod.Get,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `sendMessage should encode path segments with special characters`() = runTest {
        val roomName = "name/with spaces, slashes and \""
        mockSendMessageApiResponse(
            realtime,
            jsonObject {
                put(MessageProperty.Serial, "timeserial")
                put(MessageProperty.Timestamp, 1_000_000)
            },
            roomName,
        )
        chatApi.sendMessage(roomName, SendMessageParams(text = "hello"))
        verify {
            realtime.requestAsync(
                "/chat/v4/rooms/name%2Fwith%20spaces%2C%20slashes%20and%20%22/messages",
                any(),
                HttpMethod.Post,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `updateMessage should encode path segments with special characters`() = runTest {
        val roomName = "name/with spaces, slashes and \""
        mockUpdateMessageApiResponse(
            realtime,
            jsonObject {
                put(MessageProperty.Serial, "timeserial")
                put(MessageProperty.Timestamp, 1_000_000)
                put(MessageProperty.ClientId, "clientId")
            },
            roomName,
        )
        chatApi.updateMessage(roomName, "timeserial", UpdateMessageParams(SendMessageParams("hello")))
        verify {
            realtime.requestAsync(
                "/chat/v4/rooms/name%2Fwith%20spaces%2C%20slashes%20and%20%22/messages/timeserial",
                any(),
                HttpMethod.Put,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `deleteMessage should encode path segments with special characters`() = runTest {
        val roomName = "name/with spaces, slashes and \""
        mockDeleteMessageApiResponse(
            realtime,
            jsonObject {
                put(MessageProperty.Serial, "timeserial")
                put(MessageProperty.Timestamp, 1_000_000)
                put(MessageProperty.ClientId, "clientId")
            },
            roomName,
            "0@0:01",
        )
        chatApi.deleteMessage(roomName, "0@0:01", DeleteMessageParams())
        verify {
            realtime.requestAsync(
                "/chat/v4/rooms/name%2Fwith%20spaces%2C%20slashes%20and%20%22/messages/0%400%3A01/delete",
                any(),
                HttpMethod.Post,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `message reactions should encode path segments with special characters`() = runTest {
        val roomName = "name/with spaces, slashes and \""
        val timeserial = "0@0:02"
        mockMessageReactionApiResponse(realtime, roomName, timeserial)
        chatApi.deleteMessageReaction(roomName, timeserial, MessageReactionType.Unique)
        chatApi.sendMessageReaction(roomName, timeserial, MessageReactionType.Unique, "heart")
        verify(exactly = 2) {
            realtime.requestAsync(
                "/chat/v4/rooms/name%2Fwith%20spaces%2C%20slashes%20and%20%22/messages/0%400%3A02/reactions",
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `getOccupancy should encode path segments with special characters`() = runTest {
        val roomName = "name/with spaces, slashes and \""
        mockOccupancyApiResponse(realtime, jsonObject {}, roomName)
        chatApi.getOccupancy(roomName)
        verify {
            realtime.requestAsync(
                "/chat/v4/rooms/name%2Fwith%20spaces%2C%20slashes%20and%20%22/occupancy",
                any(),
                HttpMethod.Get,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `encodePath correctly encodes path segments with special characters`() {
        assertEquals("test%20room%20name%20with%20spaces", encodePath("test room name with spaces"))
        assertEquals("test%20room%2Fname%2Fwith%2Fslashes", encodePath("test room/name/with/slashes"))
        assertEquals("test%20room%20name%20with%20%22quotes%22", encodePath("test room name with \"quotes\""))
        assertEquals("test%20serial%201%401%3A33", encodePath("test serial 1@1:33"))
    }
}
