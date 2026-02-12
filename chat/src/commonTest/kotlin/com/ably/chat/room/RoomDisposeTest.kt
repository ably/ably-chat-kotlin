package com.ably.chat.room

import com.ably.chat.ChatApi
import com.ably.chat.ChatException
import com.ably.chat.ClientIdResolver
import com.ably.chat.DefaultRooms
import com.ably.chat.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for Rooms.dispose() functionality.
 * Spec: CHA-CL1a
 */
class RoomDisposeTest {
    private val clientIdResolver = mockk<ClientIdResolver>()
    private val logger = createMockLogger()

    @Before
    fun setUp() {
        every { clientIdResolver.get() } returns DEFAULT_CLIENT_ID
    }

    /**
     * @spec CHA-CL1a
     */
    @Test
    fun `get() throws ResourceDisposed error after dispose`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val chatApi = mockk<ChatApi>(relaxed = true)
        val rooms = DefaultRooms(mockRealtimeClient, chatApi, clientIdResolver, logger)

        // Dispose the rooms
        rooms.dispose()

        // Subsequent get() should throw
        val exception = assertThrows(ChatException::class.java) {
            runBlocking { rooms.get("test-room") }
        }
        assertEquals(ErrorCode.ResourceDisposed.code, exception.errorInfo.code)
        assertEquals("unable to get room; client has been disposed", exception.errorInfo.message)
    }

    /**
     * @spec CHA-CL1a
     */
    @Test
    fun `dispose() is idempotent - multiple calls are safe`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val chatApi = mockk<ChatApi>(relaxed = true)
        val rooms = DefaultRooms(mockRealtimeClient, chatApi, clientIdResolver, logger)

        // Multiple dispose calls should not throw
        rooms.dispose()
        rooms.dispose()
        rooms.dispose()

        // Should still throw ResourceDisposed on get
        val exception = assertThrows(ChatException::class.java) {
            runBlocking { rooms.get("test-room") }
        }
        assertEquals(ErrorCode.ResourceDisposed.code, exception.errorInfo.code)
    }

    /**
     * @spec CHA-CL1a
     */
    @Test
    fun `dispose() releases all rooms and clears internal state`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val chatApi = mockk<ChatApi>(relaxed = true)
        val rooms = spyk(DefaultRooms(mockRealtimeClient, chatApi, clientIdResolver, logger))

        // Create some rooms
        rooms.get("room1")
        rooms.get("room2")
        assertEquals(2, rooms.RoomNameToRoom.size)

        // Dispose - CHA-CL1a: releases all rooms
        rooms.dispose()

        // Internal state should be cleared
        assertTrue(rooms.RoomNameToRoom.isEmpty())
        assertTrue(rooms.RoomGetDeferredMap.isEmpty())
        assertTrue(rooms.RoomReleaseDeferredMap.isEmpty())
    }
}
