package com.ably.chat

import io.ably.lib.types.ErrorInfo
import io.ably.lib.util.EventEmitter
import kotlinx.coroutines.flow.Flow

/**
 * An interface to be implemented by objects that can emit discontinuities to listeners.
 */
public interface Discontinuity {
    /**
     * Called when a discontinuity is detected on the channel.
     * @param reason The error that caused the discontinuity.
     */
    public fun discontinuityDetected(reason: ErrorInfo?)

    /**
     * Register a listener to be called when a discontinuity is detected.
     * @param listener The listener to be called when a discontinuity is detected.
     */
    public fun onDiscontinuity(listener: Listener): StatusSubscription

    /**
     * An interface for listening when discontinuity happens
     */
    public fun interface Listener {
        /**
         * A function that can be called when discontinuity happens.
         * @param reason reason for discontinuity
         */
        public fun discontinuityEmitted(reason: ErrorInfo?)
    }
}

internal abstract class DiscontinuityImpl(logger: Logger) : Discontinuity {

    private val discontinuityEmitter = DiscontinuityEmitter(logger)

    override fun onDiscontinuity(listener: Discontinuity.Listener): StatusSubscription {
        discontinuityEmitter.on(listener)
        return StatusSubscription {
            discontinuityEmitter.off(listener)
        }
    }

    override fun discontinuityDetected(reason: ErrorInfo?) {
        discontinuityEmitter.emit("discontinuity", reason)
    }
}

/**
 * @return [ConnectionStatusChange] events as a [Flow]
 */
public fun Discontinuity.discontinuityAsFlow(): Flow<ErrorInfo?> = transformStatusCallbackAsFlow {
    onDiscontinuity(it)
}

internal class DiscontinuityEmitter(logger: Logger) : EventEmitter<String, Discontinuity.Listener>() {
    private val logger = logger.withContext("DiscontinuityEmitter")

    override fun apply(listener: Discontinuity.Listener?, event: String?, vararg args: Any?) {
        try {
            val reason = args.firstOrNull() as? ErrorInfo?
            listener?.discontinuityEmitted(reason)
        } catch (t: Throwable) {
            logger.error("Unexpected exception calling Discontinuity Listener", t)
        }
    }
}
