package com.ably.chat

import io.ably.lib.util.EventEmitter

/**
 * (CHA-RS1)
 * The different states that a room can be in throughout its lifecycle.
 */
public enum class RoomStatus(public val stateName: String) {
    /**
     * (CHA-RS1a)
     * A temporary state for when the library is first initialized.
     */
    Initialized("initialized"),

    /**
     * (CHA-RS1b)
     * The library is currently attempting to attach the room.
     */
    Attaching("attaching"),

    /**
     * (CHA-RS1c)
     * The room is currently attached and receiving events.
     */
    Attached("attached"),

    /**
     * (CHA-RS1d)
     * The room is currently detaching and will not receive events.
     */
    Detaching("detaching"),

    /**
     * (CHA-RS1e)
     * The room is currently detached and will not receive events.
     */
    Detached("detached"),

    /**
     * (CHA-RS1f)
     * The room is in an extended state of detachment, but will attempt to re-attach when able.
     */
    Suspended("suspended"),

    /**
     * (CHA-RS1g)
     * The room is currently detached and will not attempt to re-attach. User intervention is required.
     */
    Failed("failed"),

    /**
     * (CHA-RS1h)
     * The room is in the process of releasing. Attempting to use a room in this state may result in undefined behavior.
     */
    Releasing("releasing"),

    /**
     * (CHA-RS1i)
     * The room has been released and is no longer usable.
     */
    Released("released"),
}

/**
 * Represents a change in the status of the room.
 * (CHA-RS4)
 *
 * ### Not suitable for inheritance
 * This interface is not designed for client implementation or extension. The interface definition may evolve over time
 * with additional properties or methods to support new features, which could break
 * client implementations.
 */
public interface RoomStatusChange {
    /**
     * The new status of the room.
     */
    public val current: RoomStatus

    /**
     * The previous status of the room.
     */
    public val previous: RoomStatus

    /**
     * An error that provides a reason why the room has
     * entered the new status, if applicable.
     */
    public val error: ErrorInfo?
}

internal data class DefaultRoomStatusChange(
    override val current: RoomStatus,
    override val previous: RoomStatus,
    override val error: ErrorInfo? = null,
) : RoomStatusChange

/**
 * Represents the status of a Room.
 */
internal interface RoomLifecycle {
    /**
     * (CHA-RS2a)
     * The current status of the room.
     */
    val status: RoomStatus

    /**
     * (CHA-RS2b)
     * The current error, if any, that caused the room to enter the current status.
     */
    val error: ErrorInfo?

    /**
     * Registers a listener that will be called whenever the room status changes.
     * @param listener The function to call when the status changes.
     * @returns An object that can be used to unregister the listener.
     */
    fun onChange(listener: (RoomStatusChange) -> Unit): Subscription

    /**
     * Registers a listener that will be called once when the room status changes.
     * @param listener The function to call when the status changes.
     */
    fun onChangeOnce(listener: (RoomStatusChange) -> Unit)
}

internal class RoomStatusEventEmitter(logger: Logger) : EventEmitter<RoomStatus, (RoomStatusChange) -> Unit>() {
    private val logger = logger.withContext("RoomStatusEventEmitter")

    override fun apply(listener: ((RoomStatusChange) -> Unit)?, event: RoomStatus?, vararg args: Any?) {
        try {
            if (args.isNotEmpty() && args[0] is RoomStatusChange) {
                listener?.invoke(args[0] as RoomStatusChange)
            } else {
                logger.error("Invalid arguments received in apply method")
            }
        } catch (t: Throwable) {
            logger.error("Unexpected exception calling Room Status Listener", t)
        }
    }
}

internal class DefaultRoomStatusManager(logger: Logger) : RoomLifecycle {

    @Volatile
    private var _status = RoomStatus.Initialized // CHA-RS3
    override val status: RoomStatus
        get() = _status

    @Volatile
    private var _error: ErrorInfo? = null
    override val error: ErrorInfo?
        get() = _error

    private val externalEmitter = RoomStatusEventEmitter(logger)
    private val internalEmitter = RoomStatusEventEmitter(logger)

    override fun onChange(listener: (RoomStatusChange) -> Unit): Subscription {
        externalEmitter.on(listener)
        return Subscription {
            externalEmitter.off(listener)
        }
    }

    fun offAll() {
        externalEmitter.off()
    }

    override fun onChangeOnce(listener: (RoomStatusChange) -> Unit) {
        internalEmitter.once(listener)
    }

    internal fun setStatus(status: RoomStatus, error: ErrorInfo? = null) {
        val change = DefaultRoomStatusChange(status, _status, error)
        _status = change.current
        _error = change.error
        internalEmitter.emit(change.current, change)
        externalEmitter.emit(change.current, change)
    }
}
