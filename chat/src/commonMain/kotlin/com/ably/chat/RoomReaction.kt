package com.ably.chat

import com.ably.chat.json.JsonObject

/**
 * Represents a room-level reaction.
 */
public interface RoomReaction {
    /**
     * The name of the reaction, for example "like" or "love".
     */
    public val name: String

    /**
     * Metadata of the reaction. If no metadata was set this is an empty object.
     */
    public val metadata: JsonObject

    /**
     * Headers of the reaction. If no headers were set this is an empty object.
     */
    public val headers: Map<String, String>

    /**
     * The timestamp at which the reaction was sent.
     */
    public val createdAt: Long

    /**
     * The clientId of the user who sent the reaction.
     */
    public val clientId: String

    /**
     * Whether the reaction was sent by the current user.
     */
    public val isSelf: Boolean
}

internal data class DefaultRoomReaction(
    override val name: String,
    override val metadata: JsonObject,
    override val headers: Map<String, String>,
    override val createdAt: Long,
    override val clientId: String,
    override val isSelf: Boolean,
) : RoomReaction
