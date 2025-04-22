package com.ably.chat.room

import com.ably.chat.ChatApi
import com.ably.chat.DefaultRoom
import com.ably.chat.DefaultRooms
import com.ably.chat.MutableRoomOptions
import com.ably.chat.RoomOptions
import com.ably.chat.RoomStatus
import com.ably.chat.buildChatClientOptions
import com.ably.chat.buildRoomOptions
import com.ably.chat.occupancy
import com.ably.chat.presence
import com.ably.chat.typing
import io.ably.lib.types.AblyException
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
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
 * Spec: CHA-RC2, CHA-RC4
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
    fun `(CHA-RC5, CHA-RC2g) No feature should throw any exception on accessing it`() = runTest {
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

        room = DefaultRoom(
            "1234",
            buildRoomOptions {
                presence { enableEvents = false }
                occupancy { enableEvents = false }
            },
            mockRealtimeClient, chatApi, clientId, logger,
        )

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

    @Test
    fun `(CHA-RC4a) When room options have not been provided, the client shall provide defaults`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val chatApi = mockk<ChatApi>(relaxed = true)
        val rooms = spyk(DefaultRooms(mockRealtimeClient, chatApi, buildChatClientOptions(), clientId, logger), recordPrivateCalls = true)
        val roomOptions = mutableListOf<RoomOptions>()
        every { rooms["makeRoom"](any<String>(), any<RoomOptions>()) } answers {
            roomOptions.add(secondArg())
            createMockRoom()
        }
        rooms.get(DEFAULT_ROOM_ID)

        Assert.assertEquals(1, roomOptions.size)
        val actualRoomOptions = roomOptions[0]

        Assert.assertTrue("Expected presence.enableEvents to be true", actualRoomOptions.presence!!.enableEvents)
        Assert.assertEquals("Expected typing.heartbeatThrottle to be 10.seconds", 10.seconds, actualRoomOptions.typing!!.heartbeatThrottle)
        Assert.assertNotNull("Expected reactions to be non-null", actualRoomOptions.reactions)
        Assert.assertFalse("Expected occupancy.enableEvents to be false", actualRoomOptions.occupancy!!.enableEvents)
    }

    @Test
    fun `(CHA-RC4b) When partial room options have been provided, client shall deep-merge the provided values with the defaults`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val chatApi = mockk<ChatApi>(relaxed = true)
        val rooms = spyk(DefaultRooms(mockRealtimeClient, chatApi, buildChatClientOptions(), clientId, logger), recordPrivateCalls = true)
        val roomOptions = mutableListOf<RoomOptions>()
        every { rooms["makeRoom"](any<String>(), any<RoomOptions>()) } answers {
            roomOptions.add(secondArg())
            createMockRoom()
        }
        rooms.get(DEFAULT_ROOM_ID) {
            occupancy { enableEvents = true }
        }

        Assert.assertEquals(1, roomOptions.size)
        val actualRoomOptions = roomOptions[0]

        Assert.assertTrue("Expected presence.enableEvents to be true", actualRoomOptions.presence!!.enableEvents)
        Assert.assertEquals("Expected typing.heartbeatThrottle to be 10.seconds", 10.seconds, actualRoomOptions.typing!!.heartbeatThrottle)
        Assert.assertNotNull("Expected reactions to be non-null", actualRoomOptions.reactions)
        Assert.assertTrue("Expected occupancy.enableEvents to be true", actualRoomOptions.occupancy!!.enableEvents)
    }
}
