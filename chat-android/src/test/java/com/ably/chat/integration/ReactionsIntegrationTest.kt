package com.ably.chat.integration

import com.ably.chat.Reaction
import com.ably.chat.RoomOptions
import com.ably.chat.RoomReactionsOptions
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class ReactionsIntegrationTest {

    @Test
    fun `should observe room reactions`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomId = UUID.randomUUID().toString()
        val roomOptions = RoomOptions(reactions = RoomReactionsOptions())

        val room = chatClient.rooms.get(roomId, roomOptions)
        room.attach()

        val reactionEvent = CompletableDeferred<Reaction>()

        room.reactions.subscribe { reactionEvent.complete(it) }

        room.reactions.send("heart")

        assertEquals(
            "heart",
            reactionEvent.await().type,
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
