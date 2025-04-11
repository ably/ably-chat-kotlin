package com.ably.chat

/**
 * Error codes for the Chat SDK.
 */
public enum class ErrorCode(public val code: Int) {

    /**
     * The request cannot be understood
     */
    BadRequest(40_000),

    /**
     * Invalid request body
     */
    InvalidRequestBody(40_001),

    /**
     * The message was rejected before publishing by a rule on the chat room.
     */
    MessageRejectedByBeforePublishRule(42_211),

    /**
     * The message was rejected before publishing by a moderation rule on the chat room.
     */
    MessageRejectedByModeration(42_213),

    /**
     * The room has experienced a discontinuity.
     */
    RoomDiscontinuity(102_100),

    /**
     * Cannot perform operation because the room is in a failed state.
     */
    RoomInFailedState(102_101),

    /**
     * Cannot perform operation because the room is in a releasing state.
     */
    RoomIsReleasing(102_102),

    /**
     * Cannot perform operation because the room is in a released state.
     */
    RoomIsReleased(102_103),

    /**
     * Room was released before the operation could complete.
     */
    RoomReleasedBeforeOperationCompleted(102_106),

    /**
     * Room is not in valid state to perform any realtime operation.
     */
    RoomInInvalidState(102_107),

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
