package com.ably.chat

import app.cash.turbine.test
import com.ably.chat.room.createMockChatApi
import com.ably.chat.room.createMockRealtimeChannel
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createTestRoom
import com.google.gson.JsonObject
import io.ably.lib.types.AblyException
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

class OccupancyTest {

    private lateinit var occupancy: DefaultOccupancy
    private val pubSubMessageListenerSlot = slot<PubSubMessageListener>()
    private val realtimeClient = createMockRealtimeClient()

    @Before
    fun setUp() {
        val channel = createMockRealtimeChannel("room1::\$chat")
        every { channel.subscribe(any<String>(), capture(pubSubMessageListenerSlot)) } returns mockk(relaxUnitFun = true)
        val channels = realtimeClient.channels
        every { channels.get("room1::\$chat", any()) } returns channel
        val mockChatApi = createMockChatApi(realtimeClient)
        val room = createTestRoom("room1", realtimeClient = realtimeClient, chatApi = mockChatApi) {
            occupancy {
                enableEvents = true
            }
        }
        occupancy = room.occupancy
    }

    /**
     * @spec CHA-O3
     */
    @Test
    fun `user should be able to receive occupancy via #get()`() = runTest {
        mockOccupancyApiResponse(
            realtimeClient,
            JsonObject().apply {
                addProperty("connections", 2)
                addProperty("presenceMembers", 1)
            },
            roomName = "room1",
        )

        assertEquals(DefaultOccupancyData(connections = 2, presenceMembers = 1), occupancy.get())
    }

    /**
     * @spec CHA-O4a
     * @spec CHA-04c
     */
    @Test
    fun `user should be able to register a listener that receives occupancy events in realtime`() = runTest {
        val occupancyEventMessage = PubSubMessage().apply {
            data = JsonObject().apply {
                add(
                    "metrics",
                    JsonObject().apply {
                        addProperty("connections", 2)
                        addProperty("presenceMembers", 1)
                    },
                )
            }
        }

        val deferredEvent = CompletableDeferred<OccupancyEvent>()
        occupancy.subscribe {
            deferredEvent.complete(it)
        }

        pubSubMessageListenerSlot.captured.onMessage(occupancyEventMessage)

        assertEquals(DefaultOccupancyData(connections = 2, presenceMembers = 1), deferredEvent.await().occupancy)
    }

    /**
     * @spec CHA-04d
     */
    @Test
    fun `invalid occupancy event should be dropped`() = runTest {
        val validOccupancyEvent = PubSubMessage().apply {
            data = JsonObject().apply {
                add(
                    "metrics",
                    JsonObject().apply {
                        addProperty("connections", 1)
                        addProperty("presenceMembers", 1)
                    },
                )
            }
        }

        val invalidOccupancyEvent = PubSubMessage().apply {
            data = JsonObject().apply {
                add("metrics", JsonObject())
            }
        }

        val deferredEvent = CompletableDeferred<OccupancyEvent>()
        occupancy.subscribe {
            deferredEvent.complete(it)
        }

        pubSubMessageListenerSlot.captured.onMessage(invalidOccupancyEvent)
        pubSubMessageListenerSlot.captured.onMessage(validOccupancyEvent)

        assertEquals(DefaultOccupancyData(connections = 1, presenceMembers = 1), deferredEvent.await().occupancy)
    }

    /**
     * @spec CHA-04b
     */
    @Test
    fun `user should be able to remove a listener`() = runTest {
        val subscription = occupancy.subscribe {
            error("Should not be called")
        }
        subscription.unsubscribe()

        val fakeMessage = PubSubMessage().apply {
            data = JsonObject().apply {
                add(
                    "metrics",
                    JsonObject().apply {
                        addProperty("connections", 1)
                        addProperty("presenceMembers", 1)
                    },
                )
            }
        }

        pubSubMessageListenerSlot.captured.onMessage(fakeMessage)

        val deferredEvent = CompletableDeferred<OccupancyEvent>()
        occupancy.subscribe {
            deferredEvent.complete(it)
        }

        pubSubMessageListenerSlot.captured.onMessage(fakeMessage)

        assertEquals(DefaultOccupancyData(connections = 1, presenceMembers = 1), deferredEvent.await().occupancy)
    }

    @Test
    fun `should filter occupancy messages by event name`() = runTest {
        verify(exactly = 1) {
            occupancy.channelWrapper.subscribe("[meta]occupancy", any())
        }
    }

    @Test
    fun `(CHA-O4e) occupancy subscribe should throw error if occupancy events are disabled in room options`() = runTest {
        val room = createTestRoom(realtimeClient = realtimeClient) {
            occupancy {
                enableEvents = false
            }
        }
        val occupancy = DefaultOccupancy(room)

        val exception = assertThrows(AblyException::class.java) {
            occupancy.subscribe { }
        }
        val errorInfo = exception.errorInfo
        assertEquals("cannot subscribe to occupancy; occupancy events are not enabled in room options", errorInfo.message)
        assertEquals(400, errorInfo.statusCode)
        assertEquals(40_000, errorInfo.code)
    }

    @Test
    fun `asFlow() should automatically unsubscribe then it's done`() = runTest {
        val occupancy: Occupancy = mockk()
        val subscription: Subscription = mockk()
        lateinit var callback: Occupancy.Listener

        every { occupancy.subscribe(any()) } answers {
            callback = firstArg()
            subscription
        }

        occupancy.asFlow().test {
            val event = DefaultOccupancyEvent(DefaultOccupancyData(connections = 2, presenceMembers = 1))
            callback.onEvent(event)
            assertEquals(event, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }
}
