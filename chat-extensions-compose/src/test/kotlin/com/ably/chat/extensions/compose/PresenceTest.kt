package com.ably.chat.extensions.compose

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.ably.chat.Presence
import com.ably.chat.PresenceEvent
import com.ably.chat.PresenceEventType
import com.ably.chat.PresenceListener
import com.ably.chat.PresenceMember
import com.ably.chat.Room
import com.ably.chat.RoomStatus
import com.ably.chat.Subscription
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.json.JsonObject
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
            room.collectAsPresenceMembers().value
        }.test {
            assertEquals(emptyList<PresenceMember>(), awaitItem())
            presence.emit(
                PresenceEvent(
                    type = PresenceEventType.Enter,
                    clientId = "client1",
                    data = null,
                    updatedAt = 0L,
                ),
            )
            var members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("client1", members.first().clientId)
            assertNull(members.first().data)
            assertEquals(JsonObject(), members.first().extras)
            assertEquals(0, members.first().updatedAt)

            presence.emit(
                PresenceEvent(
                    type = PresenceEventType.Enter,
                    clientId = "client1",
                    data = JsonObject(),
                    updatedAt = 1L,
                ),
            )
            members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("client1", members.first().clientId)
            assertEquals(JsonObject(), members.first().data)
            assertEquals(JsonObject(), members.first().extras)
            assertEquals(1, members.first().updatedAt)
        }
    }

    @Test
    fun `should cancel getting the initial presence set if event comes faster`() = runTest {
        presence.emit(
            PresenceEvent(
                type = PresenceEventType.Enter,
                clientId = "client1",
                data = null,
                updatedAt = 0L,
            ),
        )
        presence.pause()
        moleculeFlow(RecompositionMode.Immediate) {
            room.collectAsPresenceMembers().value
        }.test {
            assertEquals(emptyList<PresenceMember>(), awaitItem())
            presence.emit(
                PresenceEvent(
                    type = PresenceEventType.Update,
                    clientId = "client1",
                    data = JsonObject(),
                    updatedAt = 1L,
                ),
            )
            presence.resume()
            val members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("client1", members.first().clientId)
            assertEquals(JsonObject(), members.first().data)
            assertEquals(JsonObject(), members.first().extras)
            assertEquals(1, members.first().updatedAt)
        }
    }
}

fun EmittingPresence() = EmittingPresence(mockk())

class EmittingPresence(val mock: Presence) : Presence by mock {
    private val listeners = mutableListOf<PresenceListener>()
    private val clientIdToPresenceMember = mutableMapOf<String, PresenceMember>()
    private val mutex = Mutex(locked = false)

    override fun subscribe(listener: PresenceListener): Subscription {
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
        clientIdToPresenceMember[event.member.clientId] = event.member
        listeners.forEach {
            it.invoke(event)
        }
    }
}

fun PresenceEvent(
    type: PresenceEventType,
    clientId: String,
    data: JsonObject?,
    updatedAt: Long,
    extras: JsonObject = JsonObject(),
): PresenceEvent = mockk {
    every { this@mockk.type } returns type
    every { this@mockk.member } returns PresenceMember(clientId, data, updatedAt, extras)
}

fun PresenceMember(
    clientId: String,
    data: JsonObject?,
    updatedAt: Long,
    extras: JsonObject,
): PresenceMember = mockk {
    every { this@mockk.clientId } returns clientId
    every { this@mockk.data } returns data
    every { this@mockk.updatedAt } returns updatedAt
    every { this@mockk.extras } returns extras
}
