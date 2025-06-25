package com.ably.chat.integration

import com.ably.chat.ConnectionStatus
import com.ably.chat.ConnectionStatusChange
import com.ably.chat.DefaultConnectionStatusChange
import com.ably.chat.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class ConnectionIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `should observe connection status`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val connectionStatusChange = CompletableDeferred<ConnectionStatusChange>()
        chatClient.connection.onStatusChange {
            if (it.current == ConnectionStatus.Connected) connectionStatusChange.complete(it)
        }
        assertEquals(
            DefaultConnectionStatusChange(
                current = ConnectionStatus.Connected,
                previous = ConnectionStatus.Connecting,
                error = null,
                retryIn = 0,
            ),
            connectionStatusChange.await(),
        )
    }

    companion object {
        private lateinit var sandbox: Sandbox

        @JvmStatic
        @BeforeClass
        fun setUp() = runTest {
            sandbox = Sandbox.createInstance()
        }
    }
}
