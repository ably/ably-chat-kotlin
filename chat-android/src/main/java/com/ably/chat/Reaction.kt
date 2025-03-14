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
public data class Reaction(
    /**
     * The type of the reaction, for example "like" or "love".
     */
    val type: String,

    /**
     * Metadata of the reaction. If no metadata was set this is an empty object.
     */
    val metadata: ReactionMetadata?,

    /**
     * Headers of the reaction. If no headers were set this is an empty object.
     */
    val headers: ReactionHeaders = mapOf(),

    /**
     * The timestamp at which the reaction was sent.
     */
    val createdAt: Long,

    /**
     * The clientId of the user who sent the reaction.
     */
    val clientId: String,

    /**
     * Whether the reaction was sent by the current user.
     */
    val isSelf: Boolean,
)
