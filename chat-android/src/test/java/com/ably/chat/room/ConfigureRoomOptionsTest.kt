package com.ably.chat.room

import com.ably.chat.ChatApi
import com.ably.chat.DefaultRoom
import com.ably.chat.RoomStatus
import com.ably.chat.buildRoomOptions
import com.ably.chat.occupancy
import com.ably.chat.presence
import com.ably.chat.typing
import io.ably.lib.types.AblyException
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Chat rooms are configurable, so as to enable or disable certain features.
 * When requesting a room, options as to which features should be enabled, and
 * the configuration they should take, must be provided
 * Spec: CHA-RC2
 */
class ConfigureRoomOptionsTest {

    private val clientId = "clientId"
    private val logger = createMockLogger()

    @Test
        fun `(CHA-RC2a) If a room is requested with a negative typing timeout, an ErrorInfo with code 40001 must be thrown`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val chatApi = mockk<ChatApi>(relaxed = true)

        // Room success when positive typing timeout
        val room = DefaultRoom(
            "1234",
            buildRoomOptions { typing { heartbeatThrottle = 100.milliseconds } },
            mockRealtimeClient,
            chatApi,
            clientId,
            logger,
        )
        Assert.assertNotNull(room)
        Assert.assertEquals(RoomStatus.Initialized, room.status)

        // Room failure when negative timeout
        val exception = assertThrows(AblyException::class.java) {
            DefaultRoom(
                "1234",
                buildRoomOptions { typing { heartbeatThrottle = 1.seconds.unaryMinus() } },
                mockRealtimeClient,
                chatApi,
                clientId,
                logger,
            )
        }
        Assert.assertEquals("Typing heartbeatThrottle must be greater than 0", exception.errorInfo.message)
        Assert.assertEquals(40_001, exception.errorInfo.code)
        Assert.assertEquals(400, exception.errorInfo.statusCode)
    }

    @Test
    fun `(CHA-RC2g) No feature should throw any exception on accessing it`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val chatApi = mockk<ChatApi>(relaxed = true)

        // Room only supports messages feature, since by default other features are turned off
        var room = DefaultRoom("1234", buildRoomOptions {}, mockRealtimeClient, chatApi, clientId, logger)
        Assert.assertNotNull(room)
        Assert.assertEquals(RoomStatus.Initialized, room.status)

        // By default all features should be enabled
        var ex = runCatching {
            room.messages
            room.typing
            room.presence
            room.reactions
            room.occupancy
        }.exceptionOrNull()
        Assert.assertNull(ex)

        room = DefaultRoom("1234", buildRoomOptions {
            presence { enableEvents = false }
            occupancy { enableEvents = false }
        }, mockRealtimeClient, chatApi, clientId, logger)

        Assert.assertNotNull(room)
        Assert.assertEquals(RoomStatus.Initialized, room.status)

        // By default all features should be enabled
        ex = runCatching {
            room.messages
            room.typing
            room.presence
            room.reactions
            room.occupancy
        }.exceptionOrNull()
        Assert.assertNull(ex)
    }
}
