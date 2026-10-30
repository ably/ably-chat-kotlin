package io.ably.pubsub.realtime

import io.ably.pubsub.realtime.ChannelStateListener.ChannelStateChange
import io.ably.pubsub.types.ChannelOptions
import io.ably.pubsub.types.ClientOptions
import io.ably.pubsub.types.ErrorInfo

/**
 * This function build ChannelStateChange object, which is package-private in ably-java.
 *
 * We can get rid of it, if we decide to increase constructor visibility
 */
fun buildChannelStateChange(
    current: ChannelState,
    previous: ChannelState,
    reason: ErrorInfo? = null,
    resumed: Boolean = true,
): ChannelStateChange = ChannelStateChange(current, previous, reason, resumed)

/**
 * This function build realtime Channel object, it has backlink to the realtime client
 * and also package-private constructor.
 */
fun buildRealtimeChannel(channelName: String = "channel") = PubSubRealtimeClient(
    ClientOptions().apply {
        key = "dummy-key"
        autoConnect = false
    },
).channels.get(channelName)

/**
 * This function build realtime Connection object, it has backlink to the realtime client
 * and also package-private constructor.
 */
fun buildRealtimeConnection() = PubSubRealtimeClient(
    ClientOptions().apply {
        key = "dummy-key"
        autoConnect = false
    },
).connection

val ChannelBase.channelOptions: ChannelOptions?
    get() = options
