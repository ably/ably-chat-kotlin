package com.ably.chat

import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class LoggerTest {

    @Test
    fun `should not call toString if logLevel is lower than minimum`() {
        val logger: Logger = DefaultLoggerFactory(LogLevel.Warn, DefaultLogContext("test"))
        val element = mockk<Any>()
        every { element.toString() } throws Exception("toString should not be called")
        logger.debug("test", context = mapOf("element" to element))
    }
}
