package com.ably.chat

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
        assertEquals(buildRoomOptions { typing { heartbeatThrottleMs = 10 } }, buildRoomOptions { typing { heartbeatThrottleMs = 10 } })
    }

    @Test
    fun `all features room options should be equal`() {
        assertEquals(
            RoomOptions.AllFeaturesEnabled,
            buildRoomOptions {
                typing()
                presence()
                reactions()
                occupancy()
            },
        )
    }
}
