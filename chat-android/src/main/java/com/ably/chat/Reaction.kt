package com.ably.chat

/**
 * [Headers] type for chat messages.
 */
public typealias ReactionHeaders = Headers

/**
 * [Metadata] type for chat messages.
 */
public typealias ReactionMetadata = Metadata

/**
 * Represents a room-level reaction.
 */
public interface Reaction {
    /**
     * The type of the reaction, for example "like" or "love".
     */
    public val type: String

    /**
     * Metadata of the reaction. If no metadata was set this is an empty object.
     */
    public val metadata: ReactionMetadata

    /**
     * Headers of the reaction. If no headers were set this is an empty object.
     */
    public val headers: ReactionHeaders

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

internal data class DefaultReaction(
    override val type: String,
    override val metadata: ReactionMetadata,
    override val headers: ReactionHeaders,
    override val createdAt: Long,
    override val clientId: String,
    override val isSelf: Boolean,
) : Reaction
