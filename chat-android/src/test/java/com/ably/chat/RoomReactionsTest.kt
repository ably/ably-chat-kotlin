package com.ably.chat

import app.cash.turbine.test
import com.ably.chat.room.createMockRealtimeChannel
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createTestRoom
import com.ably.pubsub.RealtimeChannel
import com.google.gson.JsonObject
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.Message
import io.ably.lib.types.MessageExtras
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomReactionsTest {
    private lateinit var roomReactions: DefaultRoomReactions
    private lateinit var room: DefaultRoom
    private val realtimeChannel: RealtimeChannel = createMockRealtimeChannel()

    @Before
    fun setUp() {
        val realtimeClient = createMockRealtimeClient()

        val channels = realtimeClient.channels
        every { channels.get(any(), any()) } returns realtimeChannel

        room = spyk(createTestRoom("room1", "client1", realtimeClient = realtimeClient))
        coEvery { room.ensureAttached(any<Logger>()) } returns Unit

        roomReactions = DefaultRoomReactions(room)
    }

    /**
     * @spec CHA-ER3a
     */
    @Test
    fun `should be able to subscribe to incoming reactions`() = runTest {
        val pubSubMessageListenerSlot = slot<PubSubMessageListener>()

        every { roomReactions.channelWrapper.subscribe("roomReaction", capture(pubSubMessageListenerSlot)) } returns mockk(relaxed = true)

        val deferredValue = CompletableDeferred<RoomReactionEvent>()

        roomReactions.subscribe {
            deferredValue.complete(it)
        }

        verify { roomReactions.channelWrapper.subscribe("roomReaction", any()) }

        pubSubMessageListenerSlot.captured.onMessage(
            PubSubMessage().apply {
                data = JsonObject().apply {
                    addProperty("type", "like")
                    add("metadata", JsonObject())
                }
                clientId = "clientId"
                timestamp = 1000L
                extras = MessageExtras(
                    JsonObject().apply {
                        add(
                            "headers",
                            JsonObject().apply {
                                addProperty("foo", "bar")
                            },
                        )
                    },
                )
            },
        )

        val reactionEvent = deferredValue.await()

        assertEquals(
            DefaultReaction(
                type = "like",
                createdAt = 1000L,
                clientId = "clientId",
                metadata = MessageMetadata(),
                headers = mapOf("foo" to "bar"),
                isSelf = false,
            ),
            reactionEvent.reaction,
        )
    }

    @Test
    fun `asFlow() should automatically unsubscribe then it's done`() = runTest {
        val roomReactions: RoomReactions = mockk()
        val subscription: Subscription = mockk()
        lateinit var callback: RoomReactions.Listener

        every { roomReactions.subscribe(any()) } answers {
            callback = firstArg()
            subscription
        }

        roomReactions.asFlow().test {
            val reactionEvent = mockk<RoomReactionEvent>()
            callback.onReaction(reactionEvent)
            assertEquals(reactionEvent, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }

    @Test
    fun `(CHA-ER3d) Reactions are sent on the channel using an @ephemeral@ message`() = runTest {
        var publishedMessage: Message? = null
        every {
            realtimeChannel.publish(any<Message>(), any<CompletionListener>())
        } answers {
            publishedMessage = firstArg()
            secondArg<CompletionListener>().onSuccess()
        }

        roomReactions.send("smile")

        verify(exactly = 1) { realtimeChannel.publish(any<Message>(), any()) }

        assertEquals(RoomReactionEventType.Reaction.eventName, publishedMessage?.name)

        val extras = publishedMessage?.extras?.asJsonObject()
        val ephemeral = extras?.get("ephemeral")?.asBoolean!!
        assertTrue(ephemeral)
    }
}
