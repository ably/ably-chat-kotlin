package com.ably.chat

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

public fun interface LogHandler {
    public fun log(message: String, level: LogLevel, throwable: Throwable?, context: LogContext)
}

public interface LogContext {
    public val tag: String
    public val staticContext: Map<String, String>
    public val dynamicContext: Map<String, () -> String>
}

internal class DefaultLogContext(
    override val tag: String,
    override val staticContext: Map<String, String> = mapOf(),
    override val dynamicContext: Map<String, () -> String> = mapOf(),
) : LogContext

internal interface Logger {
    val context: LogContext
    fun withContext(
        tag: String? = null,
        staticContext: Map<String, String> = mapOf(),
        dynamicContext: Map<String, () -> String> = mapOf(),
    ): Logger

    fun log(
        message: String,
        level: LogLevel,
        throwable: Throwable? = null,
        tag: String? = null,
        context: Map<String, String> = mapOf(),
    )
}

internal fun Logger.trace(
    message: String,
    throwable: Throwable? = null,
    tag: String? = null,
    context: Map<String, String> = mapOf(),
) {
    log(message, LogLevel.Trace, throwable, tag, context)
}

internal fun Logger.debug(
    message: String,
    throwable: Throwable? = null,
    tag: String? = null,
    context: Map<String, String> = mapOf(),
) {
    log(message, LogLevel.Debug, throwable, tag, context)
}

internal fun Logger.info(message: String, throwable: Throwable? = null, tag: String? = null, context: Map<String, String> = mapOf()) {
    log(message, LogLevel.Info, throwable, tag, context)
}

internal fun Logger.warn(message: String, throwable: Throwable? = null, tag: String? = null, context: Map<String, String> = mapOf()) {
    log(message, LogLevel.Warn, throwable, tag, context)
}

internal fun Logger.error(
    message: String,
    throwable: Throwable? = null,
    tag: String? = null,
    context: Map<String, String> = mapOf(),
) {
    log(message, LogLevel.Error, throwable, tag, context)
}

internal fun LogContext.mergeWith(
    tag: String? = null,
    staticContext: Map<String, String> = mapOf(),
    dynamicContext: Map<String, () -> String> = mapOf(),
): LogContext {
    return DefaultLogContext(
        tag = tag ?: this.tag,
        staticContext = this.staticContext + staticContext,
        dynamicContext = this.dynamicContext + dynamicContext,
    )
}

internal class AndroidLogger(
    private val minimalVisibleLogLevel: LogLevel,
    override val context: LogContext,
) : Logger {

    override fun withContext(tag: String?, staticContext: Map<String, String>, dynamicContext: Map<String, () -> String>): Logger {
        return AndroidLogger(
            minimalVisibleLogLevel = minimalVisibleLogLevel,
            context = context.mergeWith(tag, staticContext, dynamicContext),
        )
    }

    override fun log(message: String, level: LogLevel, throwable: Throwable?, newTag: String?, context: Map<String, String>) {
        if (level.logLevelValue < minimalVisibleLogLevel.logLevelValue) return
        val finalContext = this.context.mergeWith(newTag, context)
        val tag = "ably-chat:${finalContext.tag}"
        val completeContext = finalContext.staticContext + finalContext.dynamicContext.mapValues { it.value() }

        val contextString = ", context: $completeContext"
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.US).format(Date())
        val formattedMessage = "$currentTime (${level.name.uppercase()}) ${finalContext.tag}:${message}$contextString"
        when (level) {
            // We use Logcat's info level for Trace and Debug
            LogLevel.Trace -> Log.i(tag, formattedMessage, throwable)
            LogLevel.Debug -> Log.i(tag, formattedMessage, throwable)
            LogLevel.Info -> Log.i(tag, formattedMessage, throwable)
            LogLevel.Warn -> Log.w(tag, formattedMessage, throwable)
            LogLevel.Error -> Log.e(tag, formattedMessage, throwable)
            LogLevel.Silent -> {}
        }
    }
}

internal class CustomLogger(
    private val logHandler: LogHandler,
    private val minimalVisibleLogLevel: LogLevel,
    override val context: LogContext,
) : Logger {

    override fun withContext(tag: String?, staticContext: Map<String, String>, dynamicContext: Map<String, () -> String>): Logger {
        return CustomLogger(
            logHandler = logHandler,
            minimalVisibleLogLevel = minimalVisibleLogLevel,
            context = context.mergeWith(tag, staticContext, dynamicContext),
        )
    }

    override fun log(message: String, level: LogLevel, throwable: Throwable?, tag: String?, context: Map<String, String>) {
        if (level.logLevelValue < minimalVisibleLogLevel.logLevelValue) return
        val finalContext = this.context.mergeWith(tag, context)
        logHandler.log(
            message = message,
            level = level,
            throwable = throwable,
            context = finalContext,
        )
    }
}
