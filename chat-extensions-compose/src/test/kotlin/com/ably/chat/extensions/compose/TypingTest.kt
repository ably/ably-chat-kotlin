package com.ably.chat.extensions.compose

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.ably.chat.Room
import com.ably.chat.Subscription
import com.ably.chat.Typing
import com.ably.chat.TypingEventType
import com.ably.chat.TypingSetEvent
import com.ably.chat.TypingSetEventType
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
            val change = object : TypingSetEvent.Change {
                override val type: TypingEventType = TypingEventType.Started
                override val clientId: String = "client_1"
            }
            val typingEvent1 = object : TypingSetEvent {
                override val currentlyTyping: Set<String> = setOf("client_1", "client_2")
                override val change: TypingSetEvent.Change = change
                override val type: TypingSetEventType = TypingSetEventType.SetChanged
            }
            typing.emit(typingEvent1)
            assertEquals(setOf("client_1", "client_2"), awaitItem())

            val typingEvent2 = object : TypingSetEvent {
                override val currentlyTyping: Set<String> = setOf("client_3")
                override val change: TypingSetEvent.Change = change
                override val type: TypingSetEventType = TypingSetEventType.SetChanged
            }
            typing.emit(typingEvent2)
            assertEquals(setOf("client_3"), awaitItem())
            cancel()
        }
    }
}

fun EmittingTyping() = EmittingTyping(mockk())

class EmittingTyping(mock: Typing) : Typing by mock {
    private val listeners = mutableListOf<(TypingSetEvent) -> Unit>()

    override fun subscribe(listener: (TypingSetEvent) -> Unit): Subscription {
        listeners.add(listener)
        return Subscription { listeners.remove(listener) }
    }

    fun emit(event: TypingSetEvent) {
        listeners.forEach {
            it.invoke(event)
        }
    }
}
