package com.ably.chat

import io.ably.lib.realtime.ConnectionState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import io.ably.lib.realtime.Connection as PubSubConnection

/**
 * (CHA-CS1) The different states that the connection can be in through its lifecycle.
 */
public enum class ConnectionStatus(public val stateName: String) {
    /**
     * (CHA-CS1a) A temporary state for when the library is first initialized.
     */
    Initialized("initialized"),

    /**
     * (CHA-CS1b) The library is currently connecting to Ably.
     */
    Connecting("connecting"),

    /**
     * (CHA-CS1c) The library is currently connected to Ably.
     */
    Connected("connected"),

    /**
     * (CHA-CS1d) The library is currently disconnected from Ably, but will attempt to reconnect.
     */
    Disconnected("disconnected"),

    /**
     * (CHA-CS1e) The library is in an extended state of disconnection, but will attempt to reconnect.
     */
    Suspended("suspended"),

    /**
     * (CHA-CS1f) The library is currently disconnected from Ably and will not attempt to reconnect.
     */
    Failed("failed"),
}

/**
 * Represents a change in the status of the connection.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface ConnectionStatusChange {
    /**
     * The new status of the connection.
     */
    public val current: ConnectionStatus

    /**
     * The previous status of the connection.
     */
    public val previous: ConnectionStatus

    /**
     * An error that provides a reason why the connection has
     * entered the new status, if applicable.
     */
    public val error: ErrorInfo?

    /**
     * The time in milliseconds that the client will wait before attempting to reconnect.
     */
    public val retryIn: Long?
}

internal data class DefaultConnectionStatusChange(
    override val current: ConnectionStatus,
    override val previous: ConnectionStatus,
    override val error: ErrorInfo? = null,
    override val retryIn: Long? = null,
) : ConnectionStatusChange

/**
 * Represents a connection to Ably.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface Connection {
    /**
     * (CHA-CS2a) The current status of the connection.
     */
    public val status: ConnectionStatus

    /**
     * (CHA-CS2b) The current error, if any, that caused the connection to enter the current status.
     */
    public val error: ErrorInfo?

    /**
     * (CHA-CS4) Registers a listener that will be called whenever the connection status changes.
     * @param listener The function to call when the status changes.
     * @returns An object that can be used to unregister the listener.
     */
    public fun onStatusChange(listener: ConnectionStatusListener): Subscription
}

/**
 * A type alias representing a listener function that reacts to changes in connection status.
 * The listener receives a [ConnectionStatusChange] object, which contains details about the
 * status transition, including the current and previous connection statuses, any associated
 * error information, and a possible retry delay.
 */
public typealias ConnectionStatusListener = (ConnectionStatusChange) -> Unit

/**
 * @return [ConnectionStatusChange] events as a [Flow]
 */
public fun Connection.statusAsFlow(): Flow<ConnectionStatusChange> = transformCallbackAsFlow {
    onStatusChange(it)
}

internal class DefaultConnection(
    pubSubConnection: PubSubConnection,
    private val logger: Logger,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Connection {

    private val connectionScope = CoroutineScope(dispatcher + SupervisorJob())

    private val listeners: MutableList<ConnectionStatusListener> = CopyOnWriteArrayList()

    // (CHA-CS3)
    override var status: ConnectionStatus = mapPubSubStatusToChat(pubSubConnection.state)
        private set

    override var error: ErrorInfo? = pubSubConnection.reason.toErrorInfo()
        private set

    init {
        pubSubConnection.on { stateChange ->
            val nextStatus = mapPubSubStatusToChat(stateChange.current)
            applyStatusChange(nextStatus, stateChange.reason.toErrorInfo(), stateChange.retryIn)
        }
    }

    override fun onStatusChange(listener: ConnectionStatusListener): Subscription {
        logger.trace("Connection.onStatusChange()")
        listeners.add(listener)

        return Subscription {
            logger.trace("Connection.offStatusChange()")
            listeners.remove(listener)
        }
    }

    private fun applyStatusChange(nextStatus: ConnectionStatus, nextError: ErrorInfo?, retryIn: Long?) = connectionScope.launch {
        val previous = status
        status = nextStatus
        error = nextError
        logger.info("Connection state changed from ${previous.stateName} to ${nextStatus.stateName}")
        emitStateChange(
            DefaultConnectionStatusChange(
                current = status,
                previous = previous,
                error = error,
                retryIn = retryIn,
            ),
        )
    }

    private fun emitStateChange(statusChange: ConnectionStatusChange) {
        listeners.forEach { it.invoke(statusChange) }
    }
}

private fun mapPubSubStatusToChat(status: ConnectionState): ConnectionStatus {
    return when (status) {
        ConnectionState.initialized -> ConnectionStatus.Initialized
        ConnectionState.connecting -> ConnectionStatus.Connecting
        ConnectionState.connected -> ConnectionStatus.Connected
        ConnectionState.disconnected -> ConnectionStatus.Disconnected
        ConnectionState.suspended -> ConnectionStatus.Suspended
        ConnectionState.failed -> ConnectionStatus.Failed
        ConnectionState.closing -> ConnectionStatus.Failed
        ConnectionState.closed -> ConnectionStatus.Failed
    }
}
