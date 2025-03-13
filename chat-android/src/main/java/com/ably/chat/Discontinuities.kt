package com.ably.chat

import com.ably.pubsub.RealtimeChannel
import io.ably.lib.types.ErrorInfo
import io.ably.lib.util.EventEmitter
import kotlinx.coroutines.flow.Flow

/**
 * Represents an object that has a channel and therefore may care about discontinuities.
 */
internal interface HandlesDiscontinuity {
    /**
     * The channel that this object is associated with.
     */
    val channelWrapper: RealtimeChannel

    /**
     * Called when a discontinuity is detected on the channel.
     * @param reason The error that caused the discontinuity.
     */
    fun discontinuityDetected(reason: ErrorInfo?)
}

/**
 * An interface to be implemented by objects that can emit discontinuities to listeners.
 */
public interface EmitsDiscontinuities {
    /**
     * Register a listener to be called when a discontinuity is detected.
     * @param listener The listener to be called when a discontinuity is detected.
     */
    public fun onDiscontinuity(listener: Listener): Subscription

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

/**
 * @return [ConnectionStatusChange] events as a [Flow]
 */
public fun EmitsDiscontinuities.discontinuityAsFlow(): Flow<ErrorInfo?> = transformCallbackAsFlow {
    onDiscontinuity(it)
}

internal class DiscontinuityEmitter(logger: Logger) : EventEmitter<String, EmitsDiscontinuities.Listener>() {
    private val logger = logger.withContext("DiscontinuityEmitter")

    override fun apply(listener: EmitsDiscontinuities.Listener?, event: String?, vararg args: Any?) {
        try {
            val reason = args.firstOrNull() as? ErrorInfo?
            listener?.discontinuityEmitted(reason)
        } catch (t: Throwable) {
            logger.error("Unexpected exception calling Discontinuity Listener", t)
        }
    }
}
