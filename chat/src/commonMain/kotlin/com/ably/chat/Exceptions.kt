package com.ably.chat

import io.ably.lib.types.ErrorInfo as PubSubErrorInfo

/**
 * Represents detailed information about an error encountered in the system.
 *
 * @property message A description of the error.
 * @property code An Ably-specific code representing the error.
 * @property statusCode The HTTP status code associated with the error, if applicable.
 * @property href An optional url providing additional context or documentation for the error.
 * @property cause An optional nested ErrorInfo object representing the underlying cause of this error.
 * @property requestId An optional requestId for the request associated with the error.
 */
public class ErrorInfo(
    public val message: String, // TI1
    public val code: Int, // TI1
    public val statusCode: Int, // TI1
    public val href: String?, // TI1, TI4
    public val cause: ErrorInfo?, // TI1
    public val requestId: String?, // TI1, RSC7c
) {
    override fun toString(): String =
        "{message='$message', code=$code, statusCode=$statusCode, href=$href, cause=$cause, requestId=$requestId}"
}

internal fun ErrorInfo.copy(
    message: String = this.message,
) = ErrorInfo(message, code, statusCode, href, cause, requestId)

internal fun createErrorInfo(
    message: String,
    code: Int = 0,
    statusCode: Int = 0,
    href: String? = "https://help.ably.io/error/$code",
    cause: ErrorInfo? = null,
    requestId: String? = null,
) = ErrorInfo(
    message = message,
    code = code,
    statusCode = statusCode,
    href = href,
    cause = cause,
    requestId = requestId,
)

internal fun PubSubErrorInfo?.toErrorInfo(): ErrorInfo? = this?.let {
    createErrorInfo(
        message = message,
        code = code,
        statusCode = statusCode,
        href = href,
    )
}

internal val UnknownErrorInfo = createErrorInfo("Unknown error")

/**
 * (TI5)
 */
internal val ErrorInfo.detailedMessage get() = if (code > 0) "$message (See $href)" else message

/**
 * A specialized exception that represents an error occurring within a chat context.
 *
 * @constructor Creates a new instance of ChatException.
 * @property errorInfo The detailed information about the error, encapsulated in an [ErrorInfo] object.
 * @param cause The underlying cause of the exception, if any.
 */
public class ChatException internal constructor(public val errorInfo: ErrorInfo, cause: Throwable? = null) :
    RuntimeException(errorInfo.detailedMessage, cause)

@Suppress("FunctionNaming")
internal fun ChatException(errorInfo: PubSubErrorInfo?) = ChatException(errorInfo.toErrorInfo() ?: UnknownErrorInfo, null)

internal fun lifeCycleErrorInfo(
    errorMessage: String,
    errorCode: ErrorCode,
) = createErrorInfo(errorMessage, code = errorCode.code, statusCode = HttpStatusCode.BadRequest)

internal fun lifeCycleException(
    errorMessage: String,
    errorCode: ErrorCode,
    cause: Throwable? = null,
): ChatException = createChatException(lifeCycleErrorInfo(errorMessage, errorCode), cause)

internal fun roomInvalidStateException(roomName: String, roomStatus: RoomStatus, statusCode: Int) =
    chatException(
        "Can't perform operation; the room '$roomName' is in an invalid state: $roomStatus",
        ErrorCode.RoomInInvalidState,
        statusCode,
    )

internal fun chatException(
    errorMessage: String,
    errorCode: ErrorCode,
    statusCode: Int = HttpStatusCode.BadRequest,
    cause: Throwable? = null,
): ChatException {
    val errorInfo = createErrorInfo(errorMessage, code = errorCode.code, statusCode = statusCode)
    return createChatException(errorInfo, cause)
}

private fun createChatException(
    errorInfo: ErrorInfo,
    cause: Throwable?,
) = cause?.let { ChatException(errorInfo, it) }
    ?: ChatException(errorInfo)

internal fun clientError(errorMessage: String, code: ErrorCode = ErrorCode.BadRequest) = chatException(
    errorMessage,
    code,
    HttpStatusCode.BadRequest,
)

internal fun serverError(errorMessage: String) = chatException(errorMessage, ErrorCode.InternalError, HttpStatusCode.InternalServerError)
