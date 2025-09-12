package com.ably.chat.integration

import com.ably.chat.DefaultOccupancyData
import com.ably.chat.MainDispatcherRule
import com.ably.chat.OccupancyEvent
import com.ably.chat.get
import com.ably.chat.occupancy
import com.ably.chat.subscribeOnce
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class OccupancyIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `should return occupancy for the client`() = runTest {
        val chatClient = sandbox.createSandboxChatClient("client1")
        val roomName = UUID.randomUUID().toString()
        val chatClientRoom = chatClient.rooms.get(roomName) { occupancy { enableEvents = true } }

        val firstOccupancyEvent = CompletableDeferred<OccupancyEvent>()
        chatClientRoom.occupancy.subscribeOnce {
            firstOccupancyEvent.complete(it)
        }

        chatClientRoom.attach()
        assertEquals(DefaultOccupancyData(1, 0), firstOccupancyEvent.await().occupancy)
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
