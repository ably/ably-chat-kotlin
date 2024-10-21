package com.ably.chat

/**
 * Error codes for the Chat SDK.
 */
enum class ErrorCodes (val errorCode: Int) {

    /**
     * The messages feature failed to attach.
     */
    MessagesAttachmentFailed(102001),

    /**
     * The presence feature failed to attach.
     */
    PresenceAttachmentFailed(102002),

    /**
     * The reactions feature failed to attach.
     */
    ReactionsAttachmentFailed(102003),

    /**
     * The occupancy feature failed to attach.
     */
    OccupancyAttachmentFailed(102004),

    /**
     * The typing feature failed to attach.
     */
    TypingAttachmentFailed(102005),
    // 102006 - 102049 reserved for future use for attachment errors

    /**
     * The messages feature failed to detach.
     */
    MessagesDetachmentFailed(102050),

    /**
     * The presence feature failed to detach.
     */
    PresenceDetachmentFailed(102051),

    /**
     * The reactions feature failed to detach.
     */
    ReactionsDetachmentFailed(102052),

    /**
     * The occupancy feature failed to detach.
     */
    OccupancyDetachmentFailed(102053),

    /**
     * The typing feature failed to detach.
     */
    TypingDetachmentFailed(102054),
    // 102055 - 102099 reserved for future use for detachment errors

    /**
     * The room has experienced a discontinuity.
     */
    RoomDiscontinuity(102100),

    // Unable to perform operation;

    /**
     * Cannot perform operation because the room is in a failed state.
     */
    RoomInFailedState(102101),

    /**
     * Cannot perform operation because the room is in a releasing state.
     */
    RoomIsReleasing(102102),

    /**
     * Cannot perform operation because the room is in a released state.
     */
    RoomIsReleased(102103),

    /**
     * Cannot perform operation because the previous operation failed.
     */
    PreviousOperationFailed(102104),

    /**
     * An unknown error has happened in the room lifecycle.
     */
    RoomLifecycleError(102105),

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
    InternalError(50_000)
}

/**
 * Http Status Codes
 */
object HttpStatusCodes {

    const val BadRequest = 400

    const val Unauthorized = 401

    const val InternalServerError = 500

    const val NotImplemented = 501

    const val ServiceUnavailable = 502

    const val GatewayTimeout = 503

    const val Timeout = 504
}
