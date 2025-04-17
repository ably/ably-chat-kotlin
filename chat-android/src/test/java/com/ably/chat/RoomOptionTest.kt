package com.ably.chat

import com.ably.chat.room.RoomOptionsWithAllFeatures
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomOptionTest {

    @Test
    fun `default occupancy options should be equal`() {
        assertEquals(buildRoomOptions { occupancy() }.occupancy, buildRoomOptions { occupancy() }.occupancy)
    }

    @Test
    fun `default room options should be equal`() {
        assertEquals(buildRoomOptions(), buildRoomOptions())
    }

    @Test
    fun `custom typing options should be equal`() {
        assertEquals(
            buildRoomOptions { typing { heartbeatThrottle = 10.milliseconds } },
            buildRoomOptions { typing { heartbeatThrottle = 10.milliseconds } },
        )
    }

    @Test
    fun `all features room options should be equal`() {
        assertEquals(
            buildRoomOptions(RoomOptionsWithAllFeatures),
            buildRoomOptions {
                typing()
                presence()
                reactions()
                occupancy()
            },
        )
    }
}
