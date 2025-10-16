package com.ably.chat

/**
 * Represents a log entry that contains information about an event for logging purposes.
 *
 * @property tag A string that identifies the source or category of the log entry.
 * @property message A description of the event or action being logged.
 * @property level The severity level of the log entry, represented by the [LogLevel] enum.
 * @property context A map containing additional contextual information for the log entry, with key-value pairs
 * representing attributes related to the log event.
 * @property throwable An optional [Throwable] associated with the log entry, providing details about an exception
 * or error if applicable.
 */
public class LogEntry internal constructor(
    public val tag: String,
    public val message: String,
    public val level: LogLevel,
    public val context: Map<String, String>,
    public val throwable: Throwable?,
)

internal interface LogContext {
    val tag: String
    val staticContext: Map<String, Any?>
    val dynamicContext: Map<String, () -> Any?>
}

internal class DefaultLogContext(
    override val tag: String,
    override val staticContext: Map<String, Any?> = mapOf(),
    override val dynamicContext: Map<String, () -> Any?> = mapOf(),
) : LogContext

internal interface Logger {
    val context: LogContext
    fun withContext(
        tag: String? = null,
        staticContext: Map<String, Any?> = mapOf(),
        dynamicContext: Map<String, () -> Any?> = mapOf(),
    ): Logger

    fun log(
        message: String,
        level: LogLevel,
        throwable: Throwable? = null,
        tag: String? = null,
        context: Map<String, Any?> = mapOf(),
    )
}

internal fun Logger.trace(
    message: String,
    throwable: Throwable? = null,
    tag: String? = null,
    context: Map<String, Any?> = mapOf(),
) {
    log(message, LogLevel.Trace, throwable, tag, context)
}

internal fun Logger.debug(
    message: String,
    throwable: Throwable? = null,
    tag: String? = null,
    context: Map<String, Any?> = mapOf(),
) {
    log(message, LogLevel.Debug, throwable, tag, context)
}

internal fun Logger.info(message: String, throwable: Throwable? = null, tag: String? = null, context: Map<String, Any?> = mapOf()) {
    log(message, LogLevel.Info, throwable, tag, context)
}

internal fun Logger.warn(message: String, throwable: Throwable? = null, tag: String? = null, context: Map<String, Any?> = mapOf()) {
    log(message, LogLevel.Warn, throwable, tag, context)
}

internal fun Logger.error(
    message: String,
    throwable: Throwable? = null,
    tag: String? = null,
    context: Map<String, Any?> = mapOf(),
) {
    log(message, LogLevel.Error, throwable, tag, context)
}

internal fun LogContext.mergeWith(
    tag: String? = null,
    staticContext: Map<String, Any?> = mapOf(),
    dynamicContext: Map<String, () -> Any?> = mapOf(),
): LogContext {
    return DefaultLogContext(
        tag = tag ?: this.tag,
        staticContext = this.staticContext + staticContext,
        dynamicContext = this.dynamicContext + dynamicContext,
    )
}

internal fun LogContext.compute(): Map<String, String> =
    this.dynamicContext.mapValues { it.value().toString() } + this.staticContext.mapValues { it.value.toString() }

internal expect val DefaultLoggerFactory: (minimalVisibleLogLevel: LogLevel, context: LogContext) -> Logger

internal class CustomLogger(
    private val logHandler: (logEntry: LogEntry) -> Unit,
    private val minimalVisibleLogLevel: LogLevel,
    override val context: LogContext,
) : Logger {

    override fun withContext(tag: String?, staticContext: Map<String, Any?>, dynamicContext: Map<String, () -> Any?>): Logger {
        return CustomLogger(
            logHandler = logHandler,
            minimalVisibleLogLevel = minimalVisibleLogLevel,
            context = context.mergeWith(tag, staticContext, dynamicContext),
        )
    }

    override fun log(message: String, level: LogLevel, throwable: Throwable?, tag: String?, context: Map<String, Any?>) {
        if (level.logLevelValue < minimalVisibleLogLevel.logLevelValue) return
        val finalContext = this.context.mergeWith(tag, context)

        logHandler(
            LogEntry(
                tag = finalContext.tag,
                message = message,
                level = level,
                throwable = throwable,
                context = finalContext.compute(),
            ),
        )
    }
}
