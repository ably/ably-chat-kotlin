package com.ably.chat.extensions.compose

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.ably.chat.Room
import com.ably.chat.Subscription
import com.ably.chat.Typing
import com.ably.chat.TypingEvent
import com.ably.chat.annotations.ExperimentalChatApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalChatApi::class)
class TypingTest {

    private val typing = EmittingTyping()
    private val room = mockk<Room>(relaxed = true) {
        every { typing } returns this@TypingTest.typing
    }

    @Test
    fun `should return live value for each typing update`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) {
            room.collectAsCurrentlyTyping()
        }.test {
            assertEquals(emptySet<String>(), awaitItem())
            typing.emit(TypingEvent(currentlyTyping = setOf("client_1", "client_2")))
            assertEquals(setOf("client_1", "client_2"), awaitItem())
            typing.emit(TypingEvent(currentlyTyping = setOf("client_3")))
            assertEquals(setOf("client_3"), awaitItem())
            cancel()
        }
    }
}

fun EmittingTyping() = EmittingTyping(mockk())

class EmittingTyping(mock: Typing) : Typing by mock {
    private val listeners = mutableListOf<Typing.Listener>()

    override fun subscribe(listener: Typing.Listener): Subscription {
        listeners.add(listener)
        return Subscription { listeners.remove(listener) }
    }

    fun emit(event: TypingEvent) {
        listeners.forEach {
            it.onEvent(event)
        }
    }
}

fun TypingEvent(currentlyTyping: Set<String>): TypingEvent = mockk {
    every { this@mockk.currentlyTyping } returns currentlyTyping
}
