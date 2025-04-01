package com.ably.chat

import io.ably.lib.types.MessageAction

/**
 * All chat message events.
 */
public enum class MessageEventType(public val eventName: String) {
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
    "message.create" to MessageAction.MESSAGE_CREATE,

    /** Action applied to an updated message. */
    "message.update" to MessageAction.MESSAGE_UPDATE,

    /** Action applied to a deleted message. */
    "message.delete" to MessageAction.MESSAGE_DELETE,

    /** Action applied to a meta occupancy message. */
    "meta.occupancy" to MessageAction.META_OCCUPANCY,

    /** Action applied to a message summary. */
    "message.summary" to MessageAction.MESSAGE_SUMMARY,
)

internal val messageActionToEventType = mapOf(
    MessageAction.MESSAGE_CREATE to MessageEventType.Created,
    MessageAction.MESSAGE_UPDATE to MessageEventType.Updated,
    MessageAction.MESSAGE_DELETE to MessageEventType.Deleted,
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
public enum class RoomEvents(public val event: String) {
    /**
     * Event triggered when a discontinuity is detected in the room's channel connection.
     * A discontinuity occurs when an attached or update event comes from the channel with resume=false,
     * except for the first attach or attaches after explicit detach calls.
     */
    Discontinuity("room.discontinuity")
}
