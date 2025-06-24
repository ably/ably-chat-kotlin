package com.ably.chat

import com.google.gson.JsonObject
import io.ably.lib.types.AblyException
import io.ably.lib.types.MessageAction
import io.ably.lib.types.SummaryClientIdCounts
import io.ably.lib.types.SummaryClientIdList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageTest {

    /**
     * Spec: CHA-M11c
     */
    @Test
    fun `with should return the same message if event version is less than or equal to the current message version`() {
        val initialMessage = createMessage(
            text = "Hello",
            serial = "123",
            version = "2",
            reactions = DefaultMessageReactions(
                mapOf("heart" to SummaryClientIdList(1, listOf("user1"))),
            ),
        )
        val eventMessage = createMessage(
            text = "Hello",
            serial = "123",
            version = "1",
            action = MessageAction.MESSAGE_UPDATE,
        )
        val event = DefaultChatMessageEvent(
            type = ChatMessageEventType.Updated,
            message = eventMessage,
        )

        val result = initialMessage.with(event)

        assertSame(initialMessage, result)
    }

    /**
     * Spec: CHA-M11d
     */
    @Test
    fun `with should update the message with the event if version is greater`() {
        val initialMessage = createMessage(
            text = "Hello",
            serial = "123",
            version = "1",
        )
        val eventMessage = createMessage(
            text = "Updated Hello",
            serial = "123",
            version = "2",
        )
        val event = DefaultChatMessageEvent(
            type = ChatMessageEventType.Updated,
            message = eventMessage,
        )

        val result = initialMessage.with(event)

        assertEquals(eventMessage.text, result.text)
    }

    /**
     * Spec: CHA-M11b
     */
    @Test
    fun `with should throw an exception if message serial does not match`() {
        val initialMessage = createMessage(
            text = "Hello",
            serial = "456",
            version = "1",
        )
        val eventMessage = createMessage(
            text = "Hello",
            serial = "457",
            version = "1",
        )

        val event = DefaultChatMessageEvent(
            type = ChatMessageEventType.Updated,
            message = eventMessage,
        )

        val exception = assertThrows(AblyException::class.java) {
            initialMessage.with(event)
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }

    /**
     * Spec: CHA-M11g
     */
    @Test
    fun `with should update the message reactions from a MessageReactionSummaryEvent`() {
        val initialMessage = createMessage(
            text = "Hello",
            serial = "123",
            version = "1",
            reactions = DefaultMessageReactions(),
        )
        val summary = DefaultMessageReactionSummary(
            messageSerial = "123",
            unique = mapOf("like" to SummaryClientIdList(2, listOf("user1", "user2"))),
            distinct = mapOf("like" to SummaryClientIdList(1, listOf("user1"))),
            multiple = mapOf("like" to SummaryClientIdCounts(1, mapOf("user1" to 1))),
        )

        val event = DefaultMessageReactionSummaryEvent(summary = summary)

        assertEquals(
            initialMessage.copy(
                reactions = DefaultMessageReactions(
                    unique = summary.unique,
                    distinct = summary.distinct,
                    multiple = summary.multiple,
                ),
            ),
            initialMessage.with(event),
        )
    }

    /**
     * Spec: CHA-M11e
     */
    @Test
    fun `with should throw an exception when applying a MessageReactionSummaryEvent with a different serial`() {
        val initialMessage = createMessage(
            text = "Hello",
            serial = "123",
            version = "1",
            reactions = DefaultMessageReactions(),
        )
        val summary = DefaultMessageReactionSummary(
            messageSerial = "456",
        )

        val event = DefaultMessageReactionSummaryEvent(summary = summary)

        val exception = assertThrows(AblyException::class.java) {
            initialMessage.with(event)
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }

    /**
     * CHA-M11a
     */
    @Test
    fun `with should throw an exception when applying with create type`() {
        val initialMessage = createMessage(
            text = "Hello",
            serial = "123",
            version = "1",
            reactions = DefaultMessageReactions(),
        )
        val summary = DefaultMessageReactionSummary(
            messageSerial = "456",
        )

        val event = DefaultMessageReactionSummaryEvent(summary = summary)

        val exception = assertThrows(AblyException::class.java) {
            initialMessage.with(event)
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }
}

private fun createMessage(
    text: String,
    serial: String,
    version: String,
    action: MessageAction = MessageAction.MESSAGE_CREATE,
    reactions: MessageReactions = DefaultMessageReactions(),
) = DefaultMessage(
    text = text,
    serial = serial,
    version = version,
    clientId = "client1",
    roomId = "room1",
    createdAt = System.currentTimeMillis(),
    metadata = JsonObject(),
    headers = mapOf(),
    action = action,
    timestamp = System.currentTimeMillis(),
    reactions = reactions,
)
