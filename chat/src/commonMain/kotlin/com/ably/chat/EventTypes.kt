package com.ably.chat

import io.ably.lib.types.PresenceMessage

/**
 * All chat message events.
 */
public enum class ChatMessageEventType(public val eventName: String) {
    /** Fires when a new chat message is received. */
    Created("message.created"),

    /** Fires when a chat message is updated. */
    Updated("message.updated"),

    /** Fires when a chat message is deleted. */
    Deleted("message.deleted"),
}

/**
 * Realtime chat message names.
 */
public object PubSubEventName {
    /** Represents a regular chat message. */
    public const val ChatMessage: String = "chat.message"
}

internal val messageActionNameToAction = mapOf(

    /** Action applied to a new message. */
    "message.create" to MessageAction.MessageCreate,

    /** Action applied to an updated message. */
    "message.update" to MessageAction.MessageUpdate,

    /** Action applied to a deleted message. */
    "message.delete" to MessageAction.MessageDelete,
)

internal val messageActionToEventType = mapOf(
    MessageAction.MessageCreate to ChatMessageEventType.Created,
    MessageAction.MessageUpdate to ChatMessageEventType.Updated,
    MessageAction.MessageDelete to ChatMessageEventType.Deleted,
)

/**
 * Enum representing presence events.
 */
public enum class PresenceEventType(public val eventName: String) {
    /**
     * Event triggered when a user enters.
     */
    Enter("enter"),

    /**
     * Event triggered when a user leaves.
     */
    Leave("leave"),

    /**
     * Event triggered when a user updates their presence data.
     */
    Update("update"),

    /**
     * Event triggered when a user initially subscribes to presence.
     */
    Present("present"),
    ;

    internal companion object {
        fun fromPresenceAction(action: PresenceMessage.Action): PresenceEventType {
            return when (action) {
                PresenceMessage.Action.absent -> throw clientError("event with type absent can't be received from the realtime")
                PresenceMessage.Action.present -> Present
                PresenceMessage.Action.enter -> Enter
                PresenceMessage.Action.leave -> Leave
                PresenceMessage.Action.update -> Update
            }
        }
    }
}

/**
 * Enum representing typing events.
 */
public enum class TypingEventType(public val eventName: String) {
    /**
     * Event triggered when a user starts typing.
     */
    Started("typing.started"),

    /**
     * Event triggered when a user stops typing.
     */
    Stopped("typing.stopped"),
}

/**
 * Enum representing the typing set event types.
 */
public enum class TypingSetEventType(public val eventName: String) {
    /**
     * Event triggered when a change occurs in the set of typers.
     */
    SetChanged("typing.set.changed"),
}

/**
 * Room reaction events. This is used for the realtime system since room reactions
 * have only one event: "roomReaction".
 */
public enum class RoomReactionEventType(public val eventName: String) {
    /**
     * Event triggered when a room reaction was received.
     */
    Reaction("roomReaction"),
}

/**
 * Room events.
 */
public enum class RoomEvent(public val event: String) {
    /**
     * Event triggered when a discontinuity is detected in the room's channel connection.
     * A discontinuity occurs when an attached or update event comes from the channel with resume=false,
     * except for the first attach or attaches after explicit detach calls.
     */
    Discontinuity("room.discontinuity"),
}
