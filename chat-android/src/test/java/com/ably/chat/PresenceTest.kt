package com.ably.chat

import app.cash.turbine.test
import com.ably.chat.room.DEFAULT_ROOM_ID
import com.ably.chat.room.createMockRealtimeChannel
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createTestRoom
import com.ably.pubsub.RealtimePresence
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.ably.lib.realtime.Presence.PresenceListener
import io.ably.lib.types.AblyException
import io.ably.lib.types.PresenceMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class PresenceTest {

    private val realtimeClient = createMockRealtimeClient()
    private lateinit var pubSubPresence: RealtimePresence
    private lateinit var presence: DefaultPresence

    @Before
    fun setUp() {
        val channel = createMockRealtimeChannel("$DEFAULT_ROOM_ID::\$chat")
        val channels = realtimeClient.channels
        every { channels.get("$DEFAULT_ROOM_ID::\$chat", any()) } returns channel
        pubSubPresence = channel.presence
        presence = DefaultPresence(createTestRoom(realtimeClient = realtimeClient))
    }

    /**
     * @spec CHA-PR2a
     */
    @Test
    fun `should transform PresenceMessage into Chat's PresenceEvent if there is no data`() = runTest {
        val presenceListenerSlot = slot<PresenceListener>()

        every { pubSubPresence.subscribe(capture(presenceListenerSlot)) } returns mockk(relaxed = true)

        val deferredValue = CompletableDeferred<PresenceEvent>()

        presence.subscribe {
            deferredValue.complete(it)
        }

        presenceListenerSlot.captured.onPresenceMessage(
            PresenceMessage().apply {
                action = PresenceMessage.Action.leave
                connectionId = "foobar"
                clientId = "client1"
                timestamp = 100_000L
            },
        )

        val presenceEvent = deferredValue.await()

        assertEquals(
            DefaultPresenceEvent(
                type = PresenceEventType.Leave,
                DefaultPresenceMember(
                    clientId = "client1",
                    connectionId = "foobar",
                    updatedAt = 100_000L,
                    data = null,
                ),
            ),
            presenceEvent,
        )
    }

    /**
     * @spec CHA-PR2a
     */
    @Test
    fun `should transform PresenceMessage into Chat's PresenceEvent if there is empty data`() = runTest {
        val presenceListenerSlot = slot<PresenceListener>()

        every { pubSubPresence.subscribe(capture(presenceListenerSlot)) } returns mockk(relaxUnitFun = true)

        val deferredValue = CompletableDeferred<PresenceEvent>()

        presence.subscribe {
            deferredValue.complete(it)
        }

        presenceListenerSlot.captured.onPresenceMessage(
            PresenceMessage().apply {
                action = PresenceMessage.Action.leave
                clientId = "client1"
                connectionId = "bar"
                timestamp = 100_000L
                data = JsonObject()
            },
        )

        val presenceEvent = deferredValue.await()

        assertEquals(
            DefaultPresenceEvent(
                type = PresenceEventType.Leave,
                DefaultPresenceMember(
                    clientId = "client1",
                    connectionId = "bar",
                    updatedAt = 100_000L,
                    data = JsonObject(),
                ),
            ),
            presenceEvent,
        )
    }

    /**
     * @spec CHA-PR2a
     */
    @Test
    fun `should transform PresenceMessage into Chat's PresenceEvent if data is primitive`() = runTest {
        val presenceListenerSlot = slot<PresenceListener>()

        every { pubSubPresence.subscribe(capture(presenceListenerSlot)) } returns mockk(relaxUnitFun = true)

        val deferredValue = CompletableDeferred<PresenceEvent>()

        presence.subscribe {
            deferredValue.complete(it)
        }

        presenceListenerSlot.captured.onPresenceMessage(
            PresenceMessage().apply {
                action = PresenceMessage.Action.leave
                connectionId = "foo"
                clientId = "client1"
                timestamp = 100_000L
                data = JsonPrimitive("user")
            },
        )

        val presenceEvent = deferredValue.await()

        assertEquals(
            DefaultPresenceEvent(
                type = PresenceEventType.Leave,
                DefaultPresenceMember(
                    clientId = "client1",
                    connectionId = "foo",
                    updatedAt = 100_000L,
                    data = JsonPrimitive("user"),
                ),
            ),
            presenceEvent,
        )
    }

    @Test
    fun `(CHA-PR7d) presence subscribe should throw error if presence events are disabled in room options`() = runTest {
        val room = createTestRoom(realtimeClient = realtimeClient) {
            presence {
                enableEvents = false
            }
        }
        val presence = DefaultPresence(room)

        val exception = assertThrows(AblyException::class.java) {
            presence.subscribe { }
        }
        val errorInfo = exception.errorInfo
        assertEquals("could not subscribe to presence; presence events are not enabled in room options", errorInfo.message)
        assertEquals(400, errorInfo.statusCode)
        assertEquals(40_000, errorInfo.code)
    }

    @Test
    fun `asFlow() should automatically unsubscribe then it's done`() = runTest {
        val presence: Presence = mockk()
        val subscription: Subscription = mockk()
        lateinit var callback: Presence.Listener

        every { presence.subscribe(any()) } answers {
            callback = firstArg()
            subscription
        }

        presence.asFlow().test {
            val event = mockk<PresenceEvent>()
            callback.onEvent(event)
            assertEquals(event, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }
}
