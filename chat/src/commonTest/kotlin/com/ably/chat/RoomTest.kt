package com.ably.chat

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomTest {

    @Test
    fun `statusAsFlow() should automatically unsubscribe then it's done`() = runTest {
        val room: Room = mockk()
        val subscription: Subscription = mockk()
        lateinit var callback: RoomStatusListener

        every { room.onStatusChange(any()) } answers {
            callback = firstArg()
            subscription
        }

        room.statusAsFlow().test {
            val event = mockk<RoomStatusChange>()
            callback.invoke(event)
            assertEquals(event, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }
}
