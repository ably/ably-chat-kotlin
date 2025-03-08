package com.ably.chat.extensions.compose

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.ably.chat.Presence
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
            assertEquals(
                listOf(
                    PresenceMember(
                        clientId = "client1",
                        action = PresenceMessage.Action.enter,
                        data = null,
                        extras = emptyMap(),
                        updatedAt = 0L,
                    ),
                ),
                awaitItem(),
            )
            presence.emit(
                PresenceEvent(
                    action = PresenceMessage.Action.update,
                    clientId = "client1",
                    data = JsonObject(),
                    timestamp = 1L,
                ),
            )
            assertEquals(
                listOf(
                    PresenceMember(
                        clientId = "client1",
                        action = PresenceMessage.Action.update,
                        data = JsonObject(),
                        extras = emptyMap(),
                        updatedAt = 1L,
                    ),
                ),
                awaitItem(),
            )
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
            assertEquals(
                listOf(
                    PresenceMember(
                        clientId = "client1",
                        action = PresenceMessage.Action.update,
                        data = JsonObject(),
                        extras = emptyMap(),
                        updatedAt = 1L,
                    ),
                ),
                awaitItem(),
            )
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
            extras = emptyMap(),
            updatedAt = event.timestamp,
        )
        listeners.forEach {
            it.onEvent(event)
        }
    }
}
