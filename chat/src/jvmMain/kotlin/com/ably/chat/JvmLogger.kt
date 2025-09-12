package com.ably.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class SimpleLogger(
    private val minimalVisibleLogLevel: LogLevel,
    override val context: LogContext,
) : Logger {

    override fun withContext(tag: String?, staticContext: Map<String, String>, dynamicContext: Map<String, () -> String>): Logger {
        return SimpleLogger(
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
            LogLevel.Trace -> log("TRACE", tag, formattedMessage, throwable)
            LogLevel.Debug -> log("DEBUG", tag, formattedMessage, throwable)
            LogLevel.Info -> log("INFO", tag, formattedMessage, throwable)
            LogLevel.Warn -> log("WARN", tag, formattedMessage, throwable)
            LogLevel.Error -> log("ERROR", tag, formattedMessage, throwable)
            LogLevel.Silent -> {}
        }
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        println("($level) $tag: $message")
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
