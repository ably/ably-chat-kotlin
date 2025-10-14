package com.ably.chat.extensions.compose

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.ably.chat.Occupancy
import com.ably.chat.OccupancyData
import com.ably.chat.OccupancyEvent
import com.ably.chat.OccupancyEventType
import com.ably.chat.OccupancyListener
import com.ably.chat.Room
import com.ably.chat.RoomStatus
import com.ably.chat.Subscription
import com.ably.chat.annotations.ExperimentalChatApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalChatApi::class)
class OccupancyTest {
    private val occupancy = EmittingOccupancy()
    private val room = mockk<Room>(relaxed = true) {
        every { occupancy } returns this@OccupancyTest.occupancy
        every { status } returns RoomStatus.Attached
    }

    @Test
    fun `should return active occupancy set`() = runTest {
        occupancy.emit(OccupancyEvent(1, 0))
        moleculeFlow(RecompositionMode.Immediate) {
            room.collectAsOccupancy().value
        }.test {
            assertEquals(Pair(0, 0), awaitItem().toPair())
            assertEquals(Pair(1, 0), awaitItem().toPair())
            occupancy.emit(OccupancyEvent(1, 1))
            assertEquals(Pair(1, 1), awaitItem().toPair())
            occupancy.emit(OccupancyEvent(2, 1))
            assertEquals(Pair(2, 1), awaitItem().toPair())
        }
    }

    @Test
    fun `should cancel getting the initial occupancy if event comes faster`() = runTest {
        occupancy.emit(OccupancyEvent(100, 50))
        occupancy.pause()
        moleculeFlow(RecompositionMode.Immediate) {
            room.collectAsOccupancy().value
        }.test {
            assertEquals(Pair(0, 0), awaitItem().toPair())
            occupancy.emit(OccupancyEvent(1, 0))
            assertEquals(Pair(1, 0), awaitItem().toPair())
            occupancy.resume()
            occupancy.emit(OccupancyEvent(2, 1))
            assertEquals(Pair(2, 1), awaitItem().toPair())
        }
    }
}

fun EmittingOccupancy() = EmittingOccupancy(mockk())

class EmittingOccupancy(val mock: Occupancy) : Occupancy by mock {
    private val listeners = mutableListOf<OccupancyListener>()
    private var currentOccupancyEvent = OccupancyEvent(0, 0)
    private val mutex = Mutex(locked = false)

    override fun subscribe(listener: OccupancyListener): Subscription {
        listeners.add(listener)
        return Subscription { listeners.remove(listener) }
    }

    override suspend fun get(): OccupancyData = mutex.withLock {
        currentOccupancyEvent.occupancy
    }

    suspend fun pause() {
        mutex.lock()
    }

    fun resume() {
        mutex.unlock()
    }

    fun emit(event: OccupancyEvent) {
        currentOccupancyEvent = event
        listeners.forEach {
            it.invoke(event)
        }
    }
}

private fun OccupancyEvent(connections: Int, presenceMembers: Int): OccupancyEvent = mockk occupancyEvent@{
    val occupancy = mockk<OccupancyData> occupancyData@{
        every { this@occupancyData.connections } returns connections
        every { this@occupancyData.presenceMembers } returns presenceMembers
    }
    every { this@occupancyEvent.type } returns OccupancyEventType.Updated
    every { this@occupancyEvent.occupancy } returns occupancy
}

private fun OccupancyData.toPair(): Pair<Int, Int> = Pair(connections, presenceMembers)
