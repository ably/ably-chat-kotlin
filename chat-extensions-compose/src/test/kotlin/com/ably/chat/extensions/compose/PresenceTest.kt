package com.ably.chat.extensions.compose

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.ably.chat.Presence
import com.ably.chat.PresenceData
import com.ably.chat.PresenceEvent
import com.ably.chat.PresenceMember
import com.ably.chat.Room
import com.ably.chat.RoomStatus
import com.ably.chat.Subscription
import com.ably.chat.annotations.ExperimentalChatApi
import com.google.gson.JsonObject
import io.ably.lib.types.PresenceMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalChatApi::class)
class PresenceTest {
    private val presence = EmittingPresence()
    private val room = mockk<Room>(relaxed = true) {
        every { presence } returns this@PresenceTest.presence
        every { status } returns RoomStatus.Attached
    }

    @Test
    fun `should return active presence set`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) {
            room.collectAsPresenceMembers()
        }.test {
            assertEquals(emptyList<PresenceMember>(), awaitItem())
            presence.emit(
                PresenceEvent(
                    action = PresenceMessage.Action.enter,
                    clientId = "client1",
                    data = null,
                    timestamp = 0L,
                ),
            )
            var members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("client1", members.first().clientId)
            assertEquals(PresenceMessage.Action.enter, members.first().action)
            assertNull(members.first().data)
            assertEquals(JsonObject(), members.first().extras)
            assertEquals(0, members.first().updatedAt)

            presence.emit(
                PresenceEvent(
                    action = PresenceMessage.Action.update,
                    clientId = "client1",
                    data = JsonObject(),
                    timestamp = 1L,
                ),
            )
            members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("client1", members.first().clientId)
            assertEquals(PresenceMessage.Action.update, members.first().action)
            assertEquals(JsonObject(), members.first().data)
            assertEquals(JsonObject(), members.first().extras)
            assertEquals(1, members.first().updatedAt)
        }
    }

    @Test
    fun `should cancel getting the initial presence set if event comes faster`() = runTest {
        presence.emit(
            PresenceEvent(
                action = PresenceMessage.Action.enter,
                clientId = "client1",
                data = null,
                timestamp = 0L,
            ),
        )
        presence.pause()
        moleculeFlow(RecompositionMode.Immediate) {
            room.collectAsPresenceMembers()
        }.test {
            assertEquals(emptyList<PresenceMember>(), awaitItem())
            presence.emit(
                PresenceEvent(
                    action = PresenceMessage.Action.update,
                    clientId = "client1",
                    data = JsonObject(),
                    timestamp = 1L,
                ),
            )
            presence.resume()
            val members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("client1", members.first().clientId)
            assertEquals(PresenceMessage.Action.update, members.first().action)
            assertEquals(JsonObject(), members.first().data)
            assertEquals(JsonObject(), members.first().extras)
            assertEquals(1, members.first().updatedAt)
        }
    }
}

fun EmittingPresence() = EmittingPresence(mockk())

class EmittingPresence(val mock: Presence) : Presence by mock {
    private val listeners = mutableListOf<Presence.Listener>()
    private val clientIdToPresenceMember = mutableMapOf<String, PresenceMember>()
    private val mutex = Mutex(locked = false)

    override fun subscribe(listener: Presence.Listener): Subscription {
        listeners.add(listener)
        return Subscription { listeners.remove(listener) }
    }

    override suspend fun get(waitForSync: Boolean, clientId: String?, connectionId: String?): List<PresenceMember> = mutex.withLock {
        clientIdToPresenceMember.values.toList()
    }

    suspend fun pause() {
        mutex.lock()
    }

    fun resume() {
        mutex.unlock()
    }

    fun emit(event: PresenceEvent) {
        clientIdToPresenceMember[event.clientId] = PresenceMember(
            clientId = event.clientId,
            action = event.action,
            data = event.data,
            extras = JsonObject(),
            updatedAt = event.timestamp,
        )
        listeners.forEach {
            it.onEvent(event)
        }
    }
}

fun PresenceEvent(action: PresenceMessage.Action, clientId: String, data: PresenceData?, timestamp: Long): PresenceEvent = mockk {
    every { this@mockk.action } returns action
    every { this@mockk.clientId } returns clientId
    every { this@mockk.data } returns data
    every { this@mockk.timestamp } returns timestamp
}

fun PresenceMember(
    clientId: String,
    data: PresenceData?,
    action: PresenceMessage.Action,
    updatedAt: Long,
    extras: JsonObject,
): PresenceMember = mockk {
    every { this@mockk.clientId } returns clientId
    every { this@mockk.data } returns data
    every { this@mockk.action } returns action
    every { this@mockk.updatedAt } returns updatedAt
    every { this@mockk.extras } returns extras
}
