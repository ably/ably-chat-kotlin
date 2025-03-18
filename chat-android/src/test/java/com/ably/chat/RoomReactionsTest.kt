package com.ably.chat

import app.cash.turbine.test
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createMockRoom
import com.google.gson.JsonObject
import io.ably.lib.types.MessageExtras
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoomReactionsTest {
    private lateinit var roomReactions: DefaultRoomReactions
    private lateinit var room: DefaultRoom

    @Before
    fun setUp() {
        val realtimeClient = createMockRealtimeClient()
        room = createMockRoom("room1", "client1", realtimeClient = realtimeClient)
        roomReactions = DefaultRoomReactions(room)
    }

    /**
     * @spec CHA-ER1
     */
    @Test
    fun `channel name is set according to the spec`() = runTest {
        val roomReactions = DefaultRoomReactions(room)

        assertEquals(
            "room1::\$chat::\$reactions",
            roomReactions.channel.name,
        )
    }

    /**
     * @spec CHA-ER3a
     */
    @Test
    fun `should be able to subscribe to incoming reactions`() = runTest {
        val pubSubMessageListenerSlot = slot<PubSubMessageListener>()

        every { roomReactions.channelWrapper.subscribe("roomReaction", capture(pubSubMessageListenerSlot)) } returns mockk(relaxed = true)

        val deferredValue = CompletableDeferred<Reaction>()

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

        val reaction = deferredValue.await()

        assertEquals(
            Reaction(
                type = "like",
                createdAt = 1000L,
                clientId = "clientId",
                metadata = MessageMetadata(),
                headers = mapOf("foo" to "bar"),
                isSelf = false,
            ),
            reaction,
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
            val reaction = mockk<Reaction>()
            callback.onReaction(reaction)
            assertEquals(reaction, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }
}
