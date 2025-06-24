package com.ably.chat.integration

import com.ably.chat.MainDispatcherRule
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class PresenceIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `should return empty list of presence members if nobody is entered`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val room = chatClient.rooms.get(UUID.randomUUID().toString())
        room.attach()
        val members = room.presence.get()
        assertEquals(0, members.size)
    }

    @Test
    fun `should return yourself as presence member after you entered`() = runTest {
        val chatClient = sandbox.createSandboxChatClient("sandbox-client")
        val room = chatClient.rooms.get(UUID.randomUUID().toString())
        room.attach()
        room.presence.enter()
        val members = room.presence.get()
        assertEquals(1, members.size)
        assertEquals("sandbox-client", members.first().clientId)
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
