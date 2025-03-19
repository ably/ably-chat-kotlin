package com.ably.chat.extensions.compose

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.ably.chat.Connection
import com.ably.chat.ConnectionStatus
import com.ably.chat.ConnectionStatusChange
import com.ably.chat.Subscription
import com.ably.chat.annotations.ExperimentalChatApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalChatApi::class)
class ConnectionTest {

    val connection = EmittingConnection()

    @Test
    fun `should return live value for the current connection`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) {
            connection.collectAsStatus()
        }.test {
            assertEquals(ConnectionStatus.Initialized, awaitItem())
            connection.emit(ConnectionStatusChange(current = ConnectionStatus.Connecting, previous = ConnectionStatus.Initialized))
            assertEquals(ConnectionStatus.Connecting, awaitItem())
            connection.emit(ConnectionStatusChange(current = ConnectionStatus.Connected, previous = ConnectionStatus.Connecting))
            assertEquals(ConnectionStatus.Connected, awaitItem())
            cancel()
        }
    }
}

fun EmittingConnection() = EmittingConnection(mockk())

class EmittingConnection(mock: Connection) : Connection by mock {
    private val listeners = mutableListOf<Connection.Listener>()

    override val status: ConnectionStatus = ConnectionStatus.Initialized

    override fun onStatusChange(listener: Connection.Listener): Subscription {
        listeners.add(listener)
        return Subscription { listeners.remove(listener) }
    }

    fun emit(event: ConnectionStatusChange) {
        listeners.forEach {
            it.connectionStatusChanged(event)
        }
    }
}

fun ConnectionStatusChange(current: ConnectionStatus, previous: ConnectionStatus): ConnectionStatusChange = mockk {
    every { this@mockk.previous } returns previous
    every { this@mockk.current } returns current
}
