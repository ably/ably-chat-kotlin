package com.ably.chat

import com.ably.chat.room.createMockChatApi
import com.ably.chat.room.createMockRealtimeClient
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.lib.realtime.RealtimeAnnotations
import io.ably.lib.realtime.buildRealtimeChannel
import io.ably.lib.types.AblyException
import io.ably.lib.types.Annotation
import io.ably.lib.types.AnnotationAction
import io.ably.lib.types.MessageAction
import io.ably.lib.types.Summary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MessagesReactionsTest {
    private val realtimeClient = createMockRealtimeClient()
    private val chatApi = createMockChatApi(realtimeClient)
    private val channel = spyk(buildRealtimeChannel("room1::\$chat"))
    private val annotations = spyk(channel.annotations)

    @Before
    fun setUp() {
        coEvery { chatApi.sendMessageReaction(any(), any(), any(), any()) } returns Unit
    }

    /**
     * Spec: CHA-MR4, CHA-MR4b2
     */
    @Test
    fun `should propagate reactions type from options`() = runTest {
        val messagesReactions = createMessagesReaction()
        messagesReactions.send("messageSerial", "heart")
        coVerify { chatApi.sendMessageReaction("room1", "messageSerial", MessageReactionType.Distinct, "heart") }
    }

    /**
     * Spec: CHA-MR4
     */
    @Test
    fun `specified reactions type should take precedent over type from options`() = runTest {
        val messagesReactions = createMessagesReaction()
        messagesReactions.send("messageSerial", "heart", MessageReactionType.Multiple)
        coVerify { chatApi.sendMessageReaction("room1", "messageSerial", MessageReactionType.Multiple, "heart") }
    }

    /**
     * Spec: CHA-MR6
     */
    @Test
    fun `should be able to subscribe to incoming summaries`() = runTest {
        val pubSubMessageListenerSlot = slot<PubSubMessageListener>()
        every { channel.subscribe(capture(pubSubMessageListenerSlot)) } returns mockk()
        val deferredValue = CompletableDeferred<MessageReactionSummaryEvent>()

        val messagesReactions = createMessagesReaction()

        messagesReactions.subscribe {
            deferredValue.complete(it)
        }

        pubSubMessageListenerSlot.captured.onMessage(
            PubSubMessage().apply {
                serial = "abcdefghij@1672531200000-123"
                timestamp = 1000L
                createdAt = 1000L
                summary = Summary(
                    mapOf(
                        "reaction:distinct.v1" to JsonObject().apply {
                            add(
                                "heart",
                                JsonObject().apply {
                                    addProperty("total", 1)
                                    add(
                                        "clientIds",
                                        JsonArray().apply {
                                            add("clientId")
                                        },
                                    )
                                },
                            )
                        },
                    ),
                )
                action = MessageAction.MESSAGE_SUMMARY
            },
        )

        val event = deferredValue.await()

        assertEquals(MessageReactionSummaryEventType.Summary, event.type)
        assertEquals("abcdefghij@1672531200000-123", event.summary.messageSerial)
        assertEquals(1, event.summary.distinct["heart"]?.total)
        assertEquals(listOf("clientId"), event.summary.distinct["heart"]?.clientIds)
    }

    /**
     * Spec: CHA-MR7
     */
    @Test
    fun `should be able to subscribe to raw events`() = runTest {
        val annotationsListenerSlot = slot<RealtimeAnnotations.AnnotationListener>()

        every { annotations.subscribe(capture(annotationsListenerSlot)) } returns mockk()
        val deferredValue = CompletableDeferred<MessageReactionRawEvent>()

        val messagesReactions = createMessagesReaction(
            MutableMessageOptions().apply {
                rawMessageReactions = true
            },
        )

        messagesReactions.subscribeRaw {
            deferredValue.complete(it)
        }

        annotationsListenerSlot.captured.onAnnotation(
            Annotation().apply {
                serial = "abcdefghij@1672531200000-456"
                messageSerial = "abcdefghij@1672531200000-123"
                timestamp = 1000L
                action = AnnotationAction.ANNOTATION_CREATE
                type = "reaction:distinct.v1"
                name = "heart"
                clientId = "clientId"
            },
        )

        val event = deferredValue.await()

        assertEquals(MessageReactionEventType.Create, event.type)
        assertEquals(
            DefaultMessageReaction(
                messageSerial = "abcdefghij@1672531200000-123",
                name = "heart",
                clientId = "clientId",
                type = MessageReactionType.Distinct,
                count = null,
            ),
            event.reaction,
        )
    }

    /**
     * Spec: CHA-MR4a1
     */
    @Test
    fun `send should throw an exception if messageSerial is empty string`() = runTest {
        val messagesReactions = createMessagesReaction()

        val exception = assertThrows(AblyException::class.java) {
            runBlocking {
                messagesReactions.send("", "heart")
            }
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }

    /**
     * Spec: CHA-MR11a1
     */
    @Test
    fun `delete should throw an exception if messageSerial is empty string`() = runTest {
        val messagesReactions = createMessagesReaction()

        val exception = assertThrows(AblyException::class.java) {
            runBlocking {
                messagesReactions.delete("", "heart")
            }
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }

    /**
     * Spec: CHA-MR7a
     */
    @Test
    fun `subscribeRaw should throw an exception if rawMessageReactions is not enabled`() = runTest {
        val messagesReactions = createMessagesReaction(
            MutableMessageOptions().apply {
                rawMessageReactions = false
            },
        )

        val exception = assertThrows(AblyException::class.java) {
            messagesReactions.subscribeRaw { }
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }

    /**
     * Spec: CHA-MR4b3
     */
    @Test
    fun `send should throw exception if count specified for any type other than multiple`() = runTest {
        val messagesReactions = createMessagesReaction()

        val exception = assertThrows(AblyException::class.java) {
            runBlocking {
                messagesReactions.send("abcdefghij@1672531200000-123", "heart", count = 3)
            }
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }

    /**
     * Spec: CHA-MR4b3
     */
    @Test
    fun `send should throw exception if count is not positive`() = runTest {
        val messagesReactions = createMessagesReaction()

        messagesReactions.send("abcdefghij@1672531200000-123", "heart", MessageReactionType.Multiple, count = 1)
        val exception = assertThrows(AblyException::class.java) {
            runBlocking {
                messagesReactions.send("abcdefghij@1672531200000-123", "heart", MessageReactionType.Multiple, count = 0)
            }
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }

    /**
     * Spec: CHA-MR4b3
     */
    @Test
    fun `send should throw exception if reaction name is empty`() = runTest {
        val messagesReactions = createMessagesReaction()

        val exception = assertThrows(AblyException::class.java) {
            runBlocking {
                messagesReactions.send("abcdefghij@1672531200000-123", "")
            }
        }
        assertEquals(exception.errorInfo.code, 40_000)
        assertEquals(exception.errorInfo.statusCode, 400)
    }

    private fun createMessagesReaction(options: MessageOptions = MutableMessageOptions()) = DefaultMessagesReactions(
        chatApi = chatApi,
        roomName = "room1",
        channel = channel,
        annotations = annotations,
        options = options,
        parentLogger = EmptyLogger(DefaultLogContext("TEST")),
    )
}
