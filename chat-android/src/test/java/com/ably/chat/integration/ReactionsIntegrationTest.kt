package com.ably.chat.integration

import com.ably.chat.MainDispatcherRule
import com.ably.chat.RoomReactionEvent
import com.ably.chat.get
import com.ably.chat.reactions
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class ReactionsIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `should observe room reactions`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomName = UUID.randomUUID().toString()

        val room = chatClient.rooms.get(roomName) { reactions() }
        room.attach()

        val reactionEvent = CompletableDeferred<RoomReactionEvent>()

        room.reactions.subscribe { reactionEvent.complete(it) }

        room.reactions.send("heart")

        assertEquals(
            "heart",
            reactionEvent.await().reaction.name,
        )
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
