package com.ably.chat

import com.ably.chat.annotations.ChatDsl
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the options for a given chat room.
 */
public interface RoomOptions {
    /**
     * The presence options for the room. To enable presence in the room, set this property. You may
     * use `rooms.get("ROOM_NAME") { presence() }` to enable presence with default options.
     * @defaultValue undefined
     */
    public val presence: PresenceOptions

    /**
     * The typing options for the room. To enable typing in the room, set this property. You may use
     * `rooms.get("ROOM_NAME") { typing() }`to enable typing with default options.
     */
    public val typing: TypingOptions

    /**
     * The reactions options for the room. To enable reactions in the room, set this property. You may use
     * `rooms.get("ROOM_NAME") { reactions() }` to enable reactions with default options.
     */
    public val reactions: RoomReactionsOptions

    /**
     * The occupancy options for the room. To enable occupancy in the room, set this property. You may use
     * `rooms.get("ROOM_NAME") { occupancy() }` to enable occupancy with default options.
     */
    public val occupancy: OccupancyOptions

    /**
     * The message options for the room.
     */
    public val messages: MessageOptions
}

/**
 * Represents the presence options for a chat room.
 */
public interface PresenceOptions {
    /**
     * Whether or not the client should receive presence events from the server. This setting
     * can be disabled if you are using presence in your Chat Room, but this particular client does not
     * need to receive the messages.
     * Spec: CHA-PR9c
     *
     * @defaultValue true
     */
    public val enableEvents: Boolean
}

/**
 * Represents the typing options for a chat room.
 */
public interface TypingOptions {
    /**
     * The throttle for typing events in milliseconds. This is the minimum time between typing events being sent.
     * @defaultValue 10 seconds
     */
    public val heartbeatThrottle: Duration
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
public interface OccupancyOptions {
    /**
     * Whether to enable inbound occupancy events.
     */
    public val enableEvents: Boolean
}

/**
 * Represents the message options for a chat room.
 */
public interface MessageOptions {
    /**
     * Whether to enable receiving raw individual message reactions from the
     * realtime channel. Set to true if subscribing to raw message reactions.
     *
     * Note reaction summaries (aggregates) are always available regardless of
     * this setting.
     *
     * @defaultValue false
     */
    public val rawMessageReactions: Boolean

    /**
     * The default message reaction type to use for sending message reactions.
     *
     * Any message reaction type can be sent regardless of this setting by specifying the `type` parameter
     * in the [MessagesReactions.send] method.
     *
     * @defaultValue [MessageReactionType.Distinct]
     */
    public val defaultMessageReactionType: MessageReactionType
}

@ChatDsl
public class MutableRoomOptions : RoomOptions {
    override var presence: MutablePresenceOptions = MutablePresenceOptions()
    override var typing: MutableTypingOptions = MutableTypingOptions()
    override var reactions: MutableRoomReactionsOptions = MutableRoomReactionsOptions()
    override var occupancy: MutableOccupancyOptions = MutableOccupancyOptions()
    override var messages: MutableMessageOptions = MutableMessageOptions()
}

@ChatDsl
public class MutablePresenceOptions : PresenceOptions {
    override var enableEvents: Boolean = true // CHA-PR9c1
}

@ChatDsl
public class MutableTypingOptions : TypingOptions {
    override var heartbeatThrottle: Duration = 10.seconds
}

@ChatDsl
public class MutableRoomReactionsOptions : RoomReactionsOptions

@ChatDsl
public class MutableOccupancyOptions : OccupancyOptions {
    override var enableEvents: Boolean = false // CHA-O6c
}

@ChatDsl
public class MutableMessageOptions : MessageOptions {
    override var rawMessageReactions: Boolean = false
    override var defaultMessageReactionType: MessageReactionType = MessageReactionType.Distinct
}

internal fun buildRoomOptions(init: (MutableRoomOptions.() -> Unit)? = null): RoomOptions =
    MutableRoomOptions().apply(init ?: {}).asEquatable()

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

public fun MutableRoomOptions.messages(init: MutableMessageOptions.() -> Unit = {}) {
    this.messages = MutableMessageOptions().apply(init)
}

internal data class EquatableRoomOptions(
    override val presence: PresenceOptions,
    override val typing: TypingOptions,
    override val reactions: RoomReactionsOptions,
    override val occupancy: OccupancyOptions,
    override val messages: MessageOptions,
) : RoomOptions {
    override fun toString(): String = "{presence=$presence, typing=$typing, occupancy=$occupancy, messages=$messages}"
}

internal data class EquatablePresenceOptions(
    override val enableEvents: Boolean,
) : PresenceOptions {
    override fun toString(): String = "{enableEvents=$enableEvents}"
}

internal data class EquatableTypingOptions(
    override val heartbeatThrottle: Duration,
) : TypingOptions {
    override fun toString(): String = "{heartbeatThrottle=$heartbeatThrottle}"
}

internal data class EquatableOccupancyOptions(
    override val enableEvents: Boolean,
) : OccupancyOptions {
    override fun toString(): String = "{enableEvents=$enableEvents}"
}

internal data class EquatableMessageOptions(
    override val rawMessageReactions: Boolean,
    override val defaultMessageReactionType: MessageReactionType,
) : MessageOptions {
    override fun toString(): String = "{rawMessageReactions=$rawMessageReactions, defaultMessageReactionType=$defaultMessageReactionType}"
}

internal data object EquatableRoomReactionsOptions : RoomReactionsOptions

internal fun MutableRoomOptions.asEquatable() = EquatableRoomOptions(
    presence = presence.asEquatable(),
    typing = typing.asEquatable(),
    reactions = EquatableRoomReactionsOptions,
    occupancy = occupancy.asEquatable(),
    messages = messages.asEquatable(),
)

internal fun MutablePresenceOptions.asEquatable() = EquatablePresenceOptions(
    enableEvents = enableEvents,
)

internal fun MutableTypingOptions.asEquatable() = EquatableTypingOptions(
    heartbeatThrottle = heartbeatThrottle,
)

internal fun MutableOccupancyOptions.asEquatable() = EquatableOccupancyOptions(
    enableEvents = enableEvents,
)

internal fun MutableMessageOptions.asEquatable() = EquatableMessageOptions(
    rawMessageReactions = rawMessageReactions,
    defaultMessageReactionType = defaultMessageReactionType,
)

/**
 * Throws ChatException for invalid room configuration.
 * Spec: CHA-RC2a
 */
internal fun RoomOptions.validateRoomOptions(logger: Logger) {
    if (typing.heartbeatThrottle.inWholeMilliseconds <= 0) {
        logger.error("Typing heartbeatThrottle must be greater than 0, found ${typing.heartbeatThrottle}")
        throw chatException("Typing heartbeatThrottle must be greater than 0", ErrorCode.InvalidRequestBody)
    }
}

/**
 * Merges channel options/modes from presence and occupancy to be used for shared channel.
 * This channel is shared by Room messages, presence and occupancy feature.
 * @return channelOptions for shared channel with options/modes from presence and occupancy.
 * Spec: CHA-RC3a
 */
internal fun RoomOptions.channelOptions(): ChannelOptions {
    return ChatChannelOptions {
        val channelModes = mutableSetOf(ChannelMode.publish, ChannelMode.subscribe, ChannelMode.presence, ChannelMode.annotation_publish)

        if (presence.enableEvents) { // CHA-PR9c2
            channelModes.add(ChannelMode.presence_subscribe)
        }

        if (occupancy.enableEvents) { // CHA-O6a
            params = mapOf("occupancy" to "metrics")
        }

        if (messages.rawMessageReactions) { // CHA-MR9a
            channelModes.add(ChannelMode.annotation_subscribe)
        }

        modes = channelModes.toTypedArray()
    }
}
