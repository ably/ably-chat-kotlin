package com.ably.chat.room

import com.ably.chat.ChatApi
import com.ably.chat.DefaultRoom
import com.ably.chat.buildRoomOptions
import com.ably.chat.occupancy
import com.ably.chat.presence
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

/**
 * Test to check shared channel for room features.
 * Spec: CHA-RC3
 */
class RoomFeatureSharedChannelTest {

    @Test
    fun `(CHA-RC3, CHA-RC3c) All features should share same channel and channels#get should be called only once`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val channels = mockRealtimeClient.channels

        every {
            channels.get("1234::\$chat", any<ChannelOptions>())
        } answers {
            createMockRealtimeChannel("1234::\$chat")
        }

        // Create room with all feature enabled,
        val room = createMockRoom(realtimeClient = mockRealtimeClient)

        // All features use the same channel
        Assert.assertEquals(room.messages.channel, room.presence.channel)
        Assert.assertEquals(room.messages.channel, room.occupancy.channel)
        Assert.assertEquals(room.messages.channel, room.typing.channel)
        Assert.assertEquals(room.messages.channel, room.reactions.channel)

        // channels.get is called only once for all room features
        verify(exactly = 1) {
            channels.get(any<String>(), any<ChannelOptions>())
        }
        verify(exactly = 1) {
            channels.get("1234::\$chat", any<ChannelOptions>())
        }
    }

    @Test
    fun `(CHA-RC3, CHA-RC3b) Shared channels should combine modes+options in accordance with room options`() = runTest {
        val mockRealtimeClient = createMockRealtimeClient()
        val capturedChannelOptions = mutableListOf<ChannelOptions>()
        val chatApi = mockk<ChatApi>(relaxed = true)
        val channels = mockRealtimeClient.channels

        every {
            channels.get("1234::\$chat", any<ChannelOptions>())
        } answers {
            capturedChannelOptions.add(secondArg())
            createMockRealtimeChannel("1234::\$chat")
        }

        // Create room with default roomOptions
        createMockRoom(realtimeClient = mockRealtimeClient)
        Assert.assertEquals(1, capturedChannelOptions.size)

        // Check for empty presence modes
        Assert.assertNull(capturedChannelOptions[0].modes)
        // Check for empty params since occupancy subscribe is disabled by default
        Assert.assertNull(capturedChannelOptions[0].params)

        capturedChannelOptions.clear()

        // Create new room with presence disabled and occupancy enabled
        DefaultRoom(
            "1234",
            buildRoomOptions {
                presence { enableEvents = false }
                occupancy { enableEvents = true }
            },
            mockRealtimeClient,
            chatApi,
            "clientId",
            createMockLogger(),
        )
        Assert.assertEquals(1, capturedChannelOptions.size)

        // Check for set presence modes, presence_subscribe flag doesn't exist
        Assert.assertEquals(3, capturedChannelOptions[0].modes.size)
        Assert.assertEquals(ChannelMode.publish, capturedChannelOptions[0].modes[0])
        Assert.assertEquals(ChannelMode.subscribe, capturedChannelOptions[0].modes[1])
        Assert.assertEquals(ChannelMode.presence, capturedChannelOptions[0].modes[2])

        // Check if occupancy matrix is set
        Assert.assertEquals("metrics", capturedChannelOptions[0].params["occupancy"])
    }
}
