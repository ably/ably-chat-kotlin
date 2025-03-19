package com.ably.chat

import com.ably.chat.annotations.ChatDsl
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions

/**
 * Represents the options for a given chat room.
 */
public interface RoomOptions {
    /**
     * The presence options for the room. To enable presence in the room, set this property. You may
     * use `rooms.get("ROOM_NAME") { presence() }` to enable presence with default options.
     * @defaultValue undefined
     */
    public val presence: PresenceOptions?

    /**
     * The typing options for the room. To enable typing in the room, set this property. You may use
     * `rooms.get("ROOM_NAME") { typing() }`to enable typing with default options.
     */
    public val typing: TypingOptions?

    /**
     * The reactions options for the room. To enable reactions in the room, set this property. You may use
     * `rooms.get("ROOM_NAME") { reactions() }` to enable reactions with default options.
     */
    public val reactions: RoomReactionsOptions?

    /**
     * The occupancy options for the room. To enable occupancy in the room, set this property. You may use
     * `rooms.get("ROOM_NAME") { occupancy() }` to enable occupancy with default options.
     */
    public val occupancy: OccupancyOptions?

    public companion object {
        /**
         * Supports all room options with default values
         */
        public val AllFeaturesEnabled: RoomOptions = buildRoomOptions {
            typing()
            presence()
            reactions()
            occupancy()
        }
    }
}

/**
 * Represents the presence options for a chat room.
 */
public interface PresenceOptions {
    /**
     * Whether the underlying Realtime channel should use the presence enter mode, allowing entry into presence.
     * This property does not affect the presence lifecycle, and users must still call [Presence.enter]
     * in order to enter presence.
     * @defaultValue true
     */
    public val enter: Boolean

    /**
     * Whether the underlying Realtime channel should use the presence subscribe mode, allowing subscription to presence.
     * This property does not affect the presence lifecycle, and users must still call [Presence.subscribe]
     * in order to subscribe to presence.
     * @defaultValue true
     */
    public val subscribe: Boolean
}

/**
 * Represents the typing options for a chat room.
 */
public interface TypingOptions {
    /**
     * The timeout for typing events in milliseconds. If typing.start() is not called for this amount of time, a stop
     * typing event will be fired, resulting in the user being removed from the currently typing set.
     * @defaultValue 5000
     */
    public val timeoutMs: Long
}

/**
 * Represents the reactions options for a chat room.
 *
 * Note: This class is currently empty but allows for future extensions
 * while maintaining backward compatibility.
 */
public interface RoomReactionsOptions

/**
 * Represents the occupancy options for a chat room.
 *
 * Note: This class is currently empty but allows for future extensions
 * while maintaining backward compatibility.
 */
public interface OccupancyOptions

@ChatDsl
public class MutableRoomOptions : RoomOptions {
    override var presence: MutablePresenceOptions? = null
    override var typing: MutableTypingOptions? = null
    override var reactions: MutableRoomReactionsOptions? = null
    override var occupancy: MutableOccupancyOptions? = null
}

@ChatDsl
public class MutablePresenceOptions : PresenceOptions {
    override var enter: Boolean = true
    override var subscribe: Boolean = true
}

@ChatDsl
public class MutableTypingOptions : TypingOptions {
    override var timeoutMs: Long = 5000
}

@ChatDsl
public class MutableRoomReactionsOptions : RoomReactionsOptions

@ChatDsl
public class MutableOccupancyOptions : OccupancyOptions

public fun buildRoomOptions(init: MutableRoomOptions.() -> Unit = {}): RoomOptions =
    MutableRoomOptions().apply(init).asEquatable()

public fun MutableRoomOptions.presence(init: MutablePresenceOptions.() -> Unit = {}) {
    this.presence = MutablePresenceOptions().apply(init)
}

public fun MutableRoomOptions.typing(init: MutableTypingOptions.() -> Unit = {}) {
    this.typing = MutableTypingOptions().apply(init)
}

public fun MutableRoomOptions.reactions(init: MutableRoomReactionsOptions.() -> Unit = {}) {
    this.reactions = MutableRoomReactionsOptions().apply(init)
}

public fun MutableRoomOptions.occupancy(init: MutableOccupancyOptions.() -> Unit = {}) {
    this.occupancy = MutableOccupancyOptions().apply(init)
}

internal data class EquatableRoomOptions(
    override val presence: PresenceOptions? = null,
    override val typing: TypingOptions? = null,
    override val reactions: RoomReactionsOptions? = null,
    override val occupancy: OccupancyOptions? = null,
) : RoomOptions

internal data class EquatablePresenceOptions(
    override val enter: Boolean,
    override val subscribe: Boolean,
) : PresenceOptions

internal data class EquatableTypingOptions(
    override val timeoutMs: Long,
) : TypingOptions

internal data object EquatableRoomReactionsOptions : RoomReactionsOptions
internal data object EquatableOccupancyOptions : OccupancyOptions

internal fun MutableRoomOptions.asEquatable() = EquatableRoomOptions(
    presence = presence?.asEquatable(),
    typing = typing?.asEquatable(),
    reactions = reactions?.let { EquatableRoomReactionsOptions },
    occupancy = occupancy?.let { EquatableOccupancyOptions },
)

internal fun MutablePresenceOptions.asEquatable() = EquatablePresenceOptions(
    enter = enter,
    subscribe = subscribe,
)

internal fun MutableTypingOptions.asEquatable() = EquatableTypingOptions(
    timeoutMs = timeoutMs,
)

/**
 * Throws AblyException for invalid room configuration.
 * Spec: CHA-RC2a
 */
internal fun RoomOptions.validateRoomOptions(logger: Logger) {
    typing?.let {
        if (it.timeoutMs <= 0) {
            logger.error("Typing timeout must be greater than 0, found ${it.timeoutMs}")
            throw ablyException("Typing timeout must be greater than 0", ErrorCode.InvalidRequestBody)
        }
    }
}

/**
 * Merges channel options/modes from presence and occupancy to be used for shared channel.
 * This channel is shared by Room messages, presence and occupancy feature.
 * @return channelOptions for shared channel with options/modes from presence and occupancy.
 * Spec: CHA-RC3
 */
internal fun RoomOptions.messagesChannelOptions(): ChannelOptions {
    return ChatChannelOptions {
        presence?.let { presence ->
            val channelModes = buildList {
                // We should have this modes for regular messages
                add(ChannelMode.publish)
                add(ChannelMode.subscribe)

                if (presence.enter) {
                    add(ChannelMode.presence)
                }
                if (presence.subscribe) {
                    add(ChannelMode.presence_subscribe)
                }
            }

            modes = channelModes.toTypedArray()
        }
        occupancy?.let {
            params = mapOf(
                "occupancy" to "metrics",
            )
        }
    }
}
