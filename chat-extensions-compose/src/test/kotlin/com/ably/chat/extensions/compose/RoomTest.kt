package com.ably.chat.extensions.compose

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.ably.chat.Room
import com.ably.chat.RoomStatus
import com.ably.chat.RoomStatusChange
import com.ably.chat.Subscription
import com.ably.chat.annotations.ExperimentalChatApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalChatApi::class)
class RoomTest {

    private val room = EmittingRoom()

    @Test
    fun `should return live value for room status change`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) {
            room.collectAsStatus()
        }.test {
            assertEquals(RoomStatus.Initialized, awaitItem())
            room.emit(RoomStatusChange(current = RoomStatus.Attached, previous = RoomStatus.Initialized))
            assertEquals(RoomStatus.Attached, awaitItem())
            room.emit(RoomStatusChange(current = RoomStatus.Failed, previous = RoomStatus.Attached))
            assertEquals(RoomStatus.Failed, awaitItem())
            cancel()
        }
    }
}

fun EmittingRoom() = EmittingRoom(mockk())

class EmittingRoom(mock: Room) : Room by mock {
    private val listeners = mutableListOf<Room.Listener>()

    override val status: RoomStatus = RoomStatus.Initialized

    override fun onStatusChange(listener: Room.Listener): Subscription {
        listeners.add(listener)
        return Subscription { listeners.remove(listener) }
    }

    fun emit(event: RoomStatusChange) {
        listeners.forEach {
            it.roomStatusChanged(event)
        }
    }
}

fun RoomStatusChange(current: RoomStatus, previous: RoomStatus): RoomStatusChange = mockk {
    every { this@mockk.current } returns current
    every { this@mockk.previous } returns previous
}
