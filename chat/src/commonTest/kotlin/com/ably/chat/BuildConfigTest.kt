package com.ably.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {

    @Test
    fun `should hold app version without extra quotes`() {
        assertTrue("""^\d+.\d+.\d+$""".toRegex().matches(BuildConfig.APP_VERSION))
    }
}
