package com.ably.chat.integration

import com.ably.chat.OccupancyEvent
import com.ably.chat.OccupancyOptions
import com.ably.chat.RoomOptions
import com.ably.chat.subscribeOnce
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class OccupancyIntegrationTest {

    @Test
    fun `should return occupancy for the client`() = runTest {
        val chatClient = sandbox.createSandboxChatClient("client1")
        val roomId = UUID.randomUUID().toString()
        val roomOptions = RoomOptions(occupancy = OccupancyOptions())

        val chatClientRoom = chatClient.rooms.get(roomId, roomOptions)

        val firstOccupancyEvent = CompletableDeferred<OccupancyEvent>()
        chatClientRoom.occupancy.subscribeOnce {
            firstOccupancyEvent.complete(it)
        }

        chatClientRoom.attach()
        assertEquals(OccupancyEvent(1, 0), firstOccupancyEvent.await())
    }

    companion object {
        private lateinit var sandbox: Sandbox

        @JvmStatic
        @BeforeClass
        fun setUp() = runTest {
            sandbox = Sandbox.createInstance()
        }
    }
}
