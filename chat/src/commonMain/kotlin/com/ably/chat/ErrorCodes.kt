package com.ably.chat

/**
 * Error codes for the Chat SDK.
 */
public enum class ErrorCode(public val code: Int) {

    /**
     * The request was invalid.
     */
    BadRequest(40_000),

    /**
     * Invalid argument provided.
     */
    InvalidArgument(40_003),

    /**
     * Invalid client ID.
     */
    InvalidClientId(40_012),

    /**
     * Resource has been disposed.
     */
    ResourceDisposed(40_014),

    /**
     * The message was rejected before publishing by a rule on the chat room.
     */
    MessageRejectedByBeforePublishRule(42_211),

    /**
     * The message was rejected before publishing by a moderation rule on the chat room.
     */
    MessageRejectedByModeration(42_213),

    /**
     * The client is not connected to Ably.
     */
    Disconnected(80_003),

    /**
     * Could not re-enter presence automatically after a room re-attach occurred.
     */
    PresenceAutoReentryFailed(91_004),

    /**
     * The room has experienced a discontinuity.
     */
    RoomDiscontinuity(102_100),

    // Unable to perform operation;

    /**
     * Room was released before the operation could complete.
     */
    RoomReleasedBeforeOperationCompleted(102_106),

    /**
     * A room already exists with different options.
     */
    RoomExistsWithDifferentOptions(102_107),

    /**
     * Feature is not enabled in room options.
     */
    FeatureNotEnabledInRoom(102_108),

    /**
     * Listener has not been subscribed yet.
     */
    ListenerNotSubscribed(102_109),

    /**
     * Channel serial is not defined when expected.
     */
    ChannelSerialNotDefined(102_110),

    /**
     * Channel options cannot be modified after the channel has been requested.
     */
    ChannelOptionsCannotBeModified(102_111),

    /**
     * Cannot perform operation because the room is in an invalid state.
     */
    RoomInInvalidState(102_112),

    /**
     * Failed to enforce sequential execution of the operation.
     */
    OperationSerializationFailed(102_113),

    /**
     * Internal error
     */
    InternalError(50_000),
}

/**
 * Http Status Codes
 */
public object HttpStatusCode {

    public const val BadRequest: Int = 400

    public const val Unauthorized: Int = 401

    public const val InternalServerError: Int = 500

    public const val NotImplemented: Int = 501

    public const val ServiceUnavailable: Int = 502

    public const val GatewayTimeout: Int = 503

    public const val Timeout: Int = 504
}
