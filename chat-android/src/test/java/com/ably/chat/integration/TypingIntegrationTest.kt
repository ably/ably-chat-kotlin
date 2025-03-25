package com.ably.chat.integration

import com.ably.chat.TypingEvent
import com.ably.chat.buildRoomOptions
import com.ably.chat.typing
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class TypingIntegrationTest {

    @Test
    fun `should return typing indication for client`() = runTest {
        val chatClient1 = sandbox.createSandboxChatClient("client1")
        val chatClient2 = sandbox.createSandboxChatClient("client2")
        val roomId = UUID.randomUUID().toString()
        val roomOptions = buildRoomOptions { typing { heartbeatThrottle = 10.seconds } }
        val chatClient1Room = chatClient1.rooms.get(roomId, roomOptions)
        chatClient1Room.attach()
        val chatClient2Room = chatClient2.rooms.get(roomId, roomOptions)
        chatClient2Room.attach()

        val deferredValue = CompletableDeferred<TypingEvent>()
        chatClient2Room.typing.subscribe {
            deferredValue.complete(it)
        }
        chatClient1Room.typing.keystroke()
        val typingEvent = deferredValue.await()
        assertEquals(setOf("client1"), typingEvent.currentlyTyping)
        assertEquals(setOf("client1"), chatClient2Room.typing.get())
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
