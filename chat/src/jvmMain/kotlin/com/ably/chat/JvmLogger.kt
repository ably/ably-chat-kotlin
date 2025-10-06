package com.ably.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class SimpleLogger(
    private val minimalVisibleLogLevel: LogLevel,
    override val context: LogContext,
) : Logger {

    override fun withContext(tag: String?, staticContext: Map<String, Any?>, dynamicContext: Map<String, () -> Any?>): Logger {
        return SimpleLogger(
            minimalVisibleLogLevel = minimalVisibleLogLevel,
            context = context.mergeWith(tag, staticContext, dynamicContext),
        )
    }

    override fun log(message: String, level: LogLevel, throwable: Throwable?, tag: String?, context: Map<String, Any?>) {
        if (level.logLevelValue < minimalVisibleLogLevel.logLevelValue) return
        val finalContext = this.context.mergeWith(tag, context)
        val completeContext = finalContext.compute()
        val threadName = if (minimalVisibleLogLevel.logLevelValue < LogLevel.Warn.logLevelValue) " [${Thread.currentThread().name}]" else ""
        val formattedMessage = " (${level.name.uppercase()})$threadName $message, context: $completeContext"
        when (level) {
            LogLevel.Trace -> log("TRACE", finalContext.tag, formattedMessage, throwable)
            LogLevel.Debug -> log("DEBUG", finalContext.tag, formattedMessage, throwable)
            LogLevel.Info -> log("INFO", finalContext.tag, formattedMessage, throwable)
            LogLevel.Warn -> log("WARN", finalContext.tag, formattedMessage, throwable)
            LogLevel.Error -> log("ERROR", finalContext.tag, formattedMessage, throwable)
            LogLevel.Silent -> {}
        }
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.US).format(Date())
        println("$currentTime ($level) $tag: $message")
        throwable?.printStackTrace(System.out)
    }
}

internal actual val DefaultLoggerFactory: (
    @ParameterName(name = "minimalVisibleLogLevel")
    LogLevel,
    @ParameterName(name = "context")
    LogContext,
) -> Logger = { minimalVisibleLogLevel, context ->
    SimpleLogger(minimalVisibleLogLevel, context)
}
