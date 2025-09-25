package com.ably.chat

import app.cash.turbine.test
import com.ably.annotations.InternalAPI
import com.ably.chat.json.jsonObject
import com.ably.chat.room.createMockChatApi
import com.ably.chat.room.createMockRealtimeChannel
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createTestRoom
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.realtime.buildChannelStateChange
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelProperties
import io.ably.lib.types.MessageAction
import io.ably.lib.types.MessageExtras
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MessagesTest {

    private val realtimeClient = createMockRealtimeClient()

    private lateinit var messages: DefaultMessages
    private val channelStateListenerSlots = mutableListOf<ChannelStateListener>()

    @OptIn(InternalAPI::class)
    @Before
    fun setUp() {
        val channel = createMockRealtimeChannel("room1::\$chat")
        val javaChannel = channel.javaChannel
        every { javaChannel.on(capture(channelStateListenerSlots)) } returns mockk()
        val channels = realtimeClient.channels
        every { channels.get("room1::\$chat", any()) } returns channel
        val chatApi = createMockChatApi(realtimeClient)
        val room = createTestRoom("room1", realtimeClient = realtimeClient, chatApi = chatApi)
        messages = room.messages
    }

    /**
     * @spec CHA-M3a
     */
    @Test
    fun `should be able to send message and get it back from response`() = runTest {
        mockSendMessageApiResponse(
            realtimeClient,
            jsonObject {
                put(MessageProperty.Serial, "abcdefghij@1672531200000-123")
                put(MessageProperty.CreatedAt, 1_000_000)
            },
            roomName = "room1",
        )

        val sentMessage = messages.send(
            text = "lala",
            headers = mapOf("foo" to "bar"),
            metadata = jsonObject { put("meta", "data") },
        )

        assertEquals(
            DefaultMessage(
                serial = "abcdefghij@1672531200000-123",
                clientId = "clientId",
                text = "lala",
                createdAt = 1_000_000,
                metadata = jsonObject { put("meta", "data") },
                headers = mapOf("foo" to "bar"),
                action = MessageAction.MESSAGE_CREATE,
                version = "abcdefghij@1672531200000-123",
                timestamp = 1_000_000L,
            ),
            sentMessage,
        )
    }

    /**
     * @spec CHA-M4a
     */
    @Test
    fun `should be able to subscribe to incoming messages`() = runTest {
        val pubSubMessageListenerSlot = slot<PubSubMessageListener>()

        every { messages.channelWrapper.subscribe("chat.message", capture(pubSubMessageListenerSlot)) } returns mockk()

        val deferredValue = CompletableDeferred<ChatMessageEvent>()

        messages.subscribe {
            deferredValue.complete(it)
        }

        verify { messages.channelWrapper.subscribe("chat.message", any()) }

        pubSubMessageListenerSlot.captured.onMessage(
            PubSubMessage().apply {
                data = jsonObject {
                    put("text", "some text")
                    putObject("metadata") {}
                }.toGson()
                serial = "abcdefghij@1672531200000-123"
                clientId = "clientId"
                timestamp = 1000L
                createdAt = 1000L
                extras = MessageExtras(
                    jsonObject {
                        putObject("headers") {
                            put("foo", "bar")
                        }
                    }.toGson().asJsonObject,
                )
                action = MessageAction.MESSAGE_CREATE
                version = "abcdefghij@1672531200000-123"
            },
        )

        val messageEvent = deferredValue.await()

        assertEquals(ChatMessageEventType.Created, messageEvent.type)
        assertEquals(
            DefaultMessage(
                createdAt = 1000L,
                clientId = "clientId",
                serial = "abcdefghij@1672531200000-123",
                text = "some text",
                metadata = MessageMetadata(),
                headers = mapOf("foo" to "bar"),
                action = MessageAction.MESSAGE_CREATE,
                version = "abcdefghij@1672531200000-123",
                timestamp = 1000L,
            ),
            messageEvent.message,
        )
    }

    /**
     * @spec CHA-M4a
     */
    @Test
    fun `should be able to subscribe to incoming delete messages`() = runTest {
        val pubSubMessageListenerSlot = slot<PubSubMessageListener>()

        every { messages.channelWrapper.subscribe("chat.message", capture(pubSubMessageListenerSlot)) } returns mockk()

        val deferredValue = CompletableDeferred<ChatMessageEvent>()

        messages.subscribe {
            deferredValue.complete(it)
        }

        verify { messages.channelWrapper.subscribe("chat.message", any()) }

        pubSubMessageListenerSlot.captured.onMessage(
            PubSubMessage().apply {
                data = jsonObject {
                    putObject("metadata") {}
                }.toGson()
                serial = "abcdefghij@1672531200000-123"
                clientId = "clientId"
                timestamp = 1000L
                createdAt = 1000L
                extras = MessageExtras(
                    jsonObject {
                        putObject("headers") {
                            put("foo", "bar")
                        }
                    }.toGson().asJsonObject,
                )
                action = MessageAction.MESSAGE_DELETE
                version = "abcdefghij@1672531200000-123"
            },
        )

        val messageEvent = deferredValue.await()

        assertEquals(ChatMessageEventType.Deleted, messageEvent.type)
        assertEquals(
            DefaultMessage(
                createdAt = 1000L,
                clientId = "clientId",
                serial = "abcdefghij@1672531200000-123",
                text = "",
                metadata = MessageMetadata(),
                headers = mapOf("foo" to "bar"),
                action = MessageAction.MESSAGE_DELETE,
                version = "abcdefghij@1672531200000-123",
                timestamp = 1000L,
            ),
            messageEvent.message,
        )
    }

    /**
     * @nospec
     */
    @Test
    fun `should throw an exception for listener history if not subscribed`() = runTest {
        val (unsubscribe, subscription) = messages.subscribe {}

        unsubscribe()

        val exception = assertThrows(AblyException::class.java) {
            runBlocking { subscription.historyBeforeSubscribe() }
        }

        assertEquals(40_000, exception.errorInfo.code)
    }

    /**
     * @spec CHA-M5a
     */
    @Test
    fun `every subscription should have own channel serial`() = runTest {
        every { messages.channelWrapper.state } returns ChannelState.attached
        every { messages.channelWrapper.properties } returns ChannelProperties().apply { channelSerial = "channel-serial-1" }

        val subscription1 = (messages.subscribe {}) as DefaultMessagesSubscription
        assertEquals("channel-serial-1", subscription1.fromSerialProvider().await())

        every { messages.channelWrapper.properties } returns ChannelProperties().apply { channelSerial = "channel-serial-2" }
        val subscription2 = (messages.subscribe {}) as DefaultMessagesSubscription

        assertEquals("channel-serial-2", subscription2.fromSerialProvider().await())
        assertEquals("channel-serial-1", subscription1.fromSerialProvider().await())
    }

    /**
     * @spec CHA-M5c
     */
    @Test
    fun `subscription should update channel serial after reattach with resume = false`() = runTest {
        every { messages.channelWrapper.state } returns ChannelState.attached
        every { messages.channelWrapper.properties } returns ChannelProperties().apply { channelSerial = "channel-serial-1" }

        val subscription1 = (messages.subscribe {}) as DefaultMessagesSubscription
        assertEquals("channel-serial-1", subscription1.fromSerialProvider().await())

        every { messages.channelWrapper.properties } returns ChannelProperties().apply {
            channelSerial = "channel-serial-2"
            attachSerial = "attach-serial-2"
        }

        channelStateListenerSlots.first().onChannelStateChanged(
            buildChannelStateChange(
                current = ChannelState.attached,
                previous = ChannelState.attaching,
                resumed = false,
            ),
        )

        assertEquals("attach-serial-2", subscription1.fromSerialProvider().await())

        // Check channelSerial is used at the point of subscription when state is attached
        every { messages.channelWrapper.properties } returns ChannelProperties().apply { channelSerial = "channel-serial-3" }
        every { messages.channelWrapper.state } returns ChannelState.attached

        val subscription2 = (messages.subscribe {}) as DefaultMessagesSubscription
        assertEquals("channel-serial-3", subscription2.fromSerialProvider().await())
    }

    @Test
    fun `subscription should invoke once for each incoming message`() = runTest {
        val listener1 = mockk<Messages.Listener>(relaxed = true)
        val listener2 = mockk<Messages.Listener>(relaxed = true)

        val pubSubMessageListenerSlot = slot<PubSubMessageListener>()

        every { messages.channelWrapper.subscribe("chat.message", capture(pubSubMessageListenerSlot)) } returns mockk()

        messages.subscribe(listener1)
        val capturedSlots = mutableListOf(pubSubMessageListenerSlot.captured)

        capturedSlots.forEach { it.onMessage(buildDummyPubSubMessage()) }

        verify(exactly = 1) { listener1.onEvent(any()) }

        messages.subscribe(listener2)
        capturedSlots.add(pubSubMessageListenerSlot.captured)

        capturedSlots.forEach { it.onMessage(buildDummyPubSubMessage()) }

        verify(exactly = 2) { listener1.onEvent(any()) }
        verify(exactly = 1) { listener2.onEvent(any()) }
    }

    @Test
    fun `asFlow() should automatically unsubscribe then it's done`() = runTest {
        val messages: Messages = mockk()
        val subscription: MessagesSubscription = mockk()
        lateinit var callback: Messages.Listener

        every { messages.subscribe(any()) } answers {
            callback = firstArg()
            subscription
        }

        messages.asFlow().test {
            val event = mockk<ChatMessageEvent>()
            callback.onEvent(event)
            assertEquals(event, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }
}

private fun buildDummyPubSubMessage() = PubSubMessage().apply {
    data = jsonObject {
        put("text", "dummy text")
        putObject("metadata") {}
    }.toGson()
    serial = "abcdefghij@1672531200000-123"
    clientId = "dummy"
    timestamp = 1000L
    createdAt = 1000L
    extras = MessageExtras(
        jsonObject {}.toGson().asJsonObject,
    )
    action = MessageAction.MESSAGE_CREATE
    version = "abcdefghij@1672531200000-123"
}
