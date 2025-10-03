package com.ably.chat

import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class AndroidLoggerTest {

    @Test
    fun `should call toString if logLevel is higher than minimum`() {
        val logger: Logger = AndroidLogger(LogLevel.Trace, DefaultLogContext("test"))
        val element = mockk<Any>()
        logger.debug("test", context = mapOf("element" to element))
        verify { element.toString() }
    }

    @Test
    fun `should add current thread name if minimum visible level less than Warn`() {
        val logger: Logger = AndroidLogger(LogLevel.Trace, DefaultLogContext("test"))

        val logged = captureLog {
            logger.warn("test")
        }

        assertTrue(
            "Logged message should contain thread name: $logged",
            logged.contains("[${Thread.currentThread().name}]"),
        )
    }

    @Test
    fun `should not add current thread name if minimum visible level more than Warn`() {
        val logger: Logger = AndroidLogger(LogLevel.Warn, DefaultLogContext("test"))

        val logged = captureLog {
            logger.warn("test")
        }

        assertFalse(
            "Logged message should contain thread name: $logged",
            logged.contains("[${Thread.currentThread().name}]"),
        )
    }
}

private fun captureLog(block: () -> Unit): String {
    ShadowLog.clear()
    block()
    return ShadowLog.getLogs().first().msg
}
