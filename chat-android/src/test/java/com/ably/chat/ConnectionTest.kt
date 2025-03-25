package com.ably.chat

import app.cash.turbine.test
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.realtime.ConnectionStateListener
import io.ably.lib.realtime.ConnectionStateListener.ConnectionStateChange
import io.ably.lib.realtime.buildRealtimeConnection
import io.ably.lib.types.ErrorInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import io.ably.lib.realtime.Connection as PubSubConnection

class ConnectionTest {

    private val pubSubConnection = spyk<PubSubConnection>(buildRealtimeConnection())

    private val pubSubConnectionStateListenerSlot = slot<ConnectionStateListener>()

    @Before
    fun setUp() {
        every { pubSubConnection.on(capture(pubSubConnectionStateListenerSlot)) } returns Unit
        pubSubConnection.state = ConnectionState.initialized
    }

    /**
     * @spec: CHA-CS3
     */
    @Test
    fun `initial status and error of the connection must be whatever status the realtime client returns`() = runTest {
        pubSubConnection.reason = ErrorInfo("some error", 400)
        pubSubConnection.state = ConnectionState.disconnected

        val connection = DefaultConnection(pubSubConnection, EmptyLogger(DefaultLogContext(tag = "TEST")))
        assertEquals(ConnectionStatus.Disconnected, connection.status)
        assertEquals(pubSubConnection.reason, connection.error)
    }

    /**
     * @spec: CHA-CS4a, CHA-CS4b, CHA-CS4c, CHA-CS4d
     */
    @Test
    fun `status update events must contain the newly entered connection status`() = runTest {
        val connection = DefaultConnection(pubSubConnection, EmptyLogger(DefaultLogContext(tag = "TEST")))
        val deferredEvent = CompletableDeferred<ConnectionStatusChange>()

        connection.onStatusChange {
            deferredEvent.complete(it)
        }

        pubSubConnectionStateListenerSlot.captured.onConnectionStateChanged(
            ConnectionStateChange(
                ConnectionState.initialized,
                ConnectionState.connecting,
                0,
                null,
            ),
        )

        assertEquals(
            DefaultConnectionStatusChange(
                current = ConnectionStatus.Connecting,
                previous = ConnectionStatus.Initialized,
                retryIn = 0,
                error = null,
            ),
            deferredEvent.await(),
        )
    }

    /**
     * @spec: CHA-CS5a1
     */
    @Test
    fun `should wait 5 sec if the connection status transitions from CONNECTED to DISCONNECTED`() = runTest {
        val testScheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = DefaultConnection(pubSubConnection, EmptyLogger(DefaultLogContext(tag = "TEST")), dispatcher)

        var status = ConnectionStatus.Initialized

        connection.onStatusChange {
            status = it.current
        }

        fireConnected()

        testScheduler.runCurrent()

        assertEquals(ConnectionStatus.Connected, status)

        fireDisconnected()

        testScheduler.runCurrent()

        assertEquals(ConnectionStatus.Connected, status)

        testScheduler.advanceTimeBy(5000.milliseconds)
        testScheduler.runCurrent()

        assertEquals(ConnectionStatus.Disconnected, status)
    }

    /**
     * @spec: CHA-CS5a3
     */
    @Test
    fun `should cancel the transient disconnect timer IF realtime connections status changes to CONNECTED`() = runTest {
        val testScheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = DefaultConnection(pubSubConnection, EmptyLogger(DefaultLogContext(tag = "TEST")), dispatcher)

        var status = ConnectionStatus.Initialized

        connection.onStatusChange {
            status = it.current
        }

        fireConnected()

        testScheduler.runCurrent()

        assertEquals(ConnectionStatus.Connected, status)

        fireDisconnected()

        testScheduler.runCurrent()

        assertEquals(ConnectionStatus.Connected, status)

        testScheduler.advanceTimeBy(3000.milliseconds)

        fireConnected()

        testScheduler.advanceTimeBy(5000.milliseconds)
        testScheduler.runCurrent()

        assertEquals(ConnectionStatus.Connected, status)
    }

    @Test
    fun `statusAsFlow() should automatically unsubscribe then it's done`() = runTest {
        val connection: Connection = mockk()
        val subscription: Subscription = mockk()
        lateinit var callback: Connection.Listener

        every { connection.onStatusChange(any()) } answers {
            callback = firstArg()
            subscription
        }

        connection.statusAsFlow().test {
            val event = mockk<ConnectionStatusChange>()
            callback.connectionStatusChanged(event)
            assertEquals(event, awaitItem())
            cancel()
        }

        verify(exactly = 1) {
            subscription.unsubscribe()
        }
    }

    private fun fireConnected() = pubSubConnectionStateListenerSlot.captured.onConnectionStateChanged(
        ConnectionStateChange(
            ConnectionState.initialized,
            ConnectionState.connected,
            0,
            null,
        ),
    )

    private fun fireDisconnected() = pubSubConnectionStateListenerSlot.captured.onConnectionStateChanged(
        ConnectionStateChange(
            ConnectionState.connected,
            ConnectionState.disconnected,
            0,
            null,
        ),
    )
}
