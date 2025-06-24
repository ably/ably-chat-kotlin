package com.ably.chat.integration

import app.cash.turbine.test
import com.ably.chat.ChatClient
import com.ably.chat.assertWaiter
import fi.iki.elonen.NanoHTTPD
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.ClientOptions
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

class RequestHeaderTest {

    @Test
    fun `should use additional agents in Realtime wrapper SDK client calls`() = runTest {
        val ablyRealtime = createAblyRealtime()
        val chatClient = ChatClient(ablyRealtime)
        val roomId = UUID.randomUUID().toString()

        server.servedRequests.test {
            chatClient.rooms.get(roomId).messages.history()
            val agents = awaitItem().headers["ably-agent"]?.split(" ") ?: setOf()
            Assert.assertTrue(
                agents.contains("chat-kotlin/${com.ably.chat.BuildConfig.APP_VERSION}"),
            )
        }
    }

    companion object {

        const val PORT = 27_332
        lateinit var server: EmbeddedServer

        @JvmStatic
        @BeforeClass
        fun setUp() = runTest {
            server = EmbeddedServer(PORT) {
                when (it.path) {
                    "/time" -> json("[1739551931167]")
                    else -> json("[]")
                }
            }
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            assertWaiter { server.wasStarted() }
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            server.stop()
        }
    }
}

private fun createAblyRealtime(): AblyRealtime {
    val options = ClientOptions("xxxxx:yyyyyyy").apply {
        port = RequestHeaderTest.PORT
        useBinaryProtocol = false
        realtimeHost = "localhost"
        restHost = "localhost"
        tls = false
        autoConnect = false
    }

    return AblyRealtime(options)
}
