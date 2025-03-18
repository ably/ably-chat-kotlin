package com.ably.chat

/**
 * Error codes for the Chat SDK.
 */
public enum class ErrorCode(public val code: Int) {

    /**
     * The messages feature failed to attach.
     */
    MessagesAttachmentFailed(102_001),

    /**
     * The presence feature failed to attach.
     */
    PresenceAttachmentFailed(102_002),

    /**
     * The reactions feature failed to attach.
     */
    ReactionsAttachmentFailed(102_003),

    /**
     * The occupancy feature failed to attach.
     */
    OccupancyAttachmentFailed(102_004),

    /**
     * The typing feature failed to attach.
     */
    TypingAttachmentFailed(102_005),
    // 102_006 - 102_049 reserved for future use for attachment errors

    /**
     * The messages feature failed to detach.
     */
    MessagesDetachmentFailed(102_050),

    /**
     * The presence feature failed to detach.
     */
    PresenceDetachmentFailed(102_051),

    /**
     * The reactions feature failed to detach.
     */
    ReactionsDetachmentFailed(102_052),

    /**
     * The occupancy feature failed to detach.
     */
    OccupancyDetachmentFailed(102_053),

    /**
     * The typing feature failed to detach.
     */
    TypingDetachmentFailed(102_054),
    // 102_055 - 102_099 reserved for future use for detachment errors

    /**
     * The room has experienced a discontinuity.
     */
    RoomDiscontinuity(102_100),

    // Unable to perform operation;

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
     * Cannot perform operation because the previous operation failed.
     */
    PreviousOperationFailed(102_104),

    /**
     * An unknown error has happened in the room lifecycle.
     */
    RoomLifecycleError(102_105),

    /**
     * The request cannot be understood
     */
    BadRequest(40_000),

    /**
     * Invalid request body
     */
    InvalidRequestBody(40_001),

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
