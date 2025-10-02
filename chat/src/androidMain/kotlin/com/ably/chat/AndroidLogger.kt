package com.ably.chat

import android.util.Log

internal class AndroidLogger(
    private val minimalVisibleLogLevel: LogLevel,
    override val context: LogContext,
) : Logger {

    override fun withContext(tag: String?, staticContext: Map<String, Any?>, dynamicContext: Map<String, () -> Any?>): Logger {
        return AndroidLogger(
            minimalVisibleLogLevel = minimalVisibleLogLevel,
            context = context.mergeWith(tag, staticContext, dynamicContext),
        )
    }

    override fun log(message: String, level: LogLevel, throwable: Throwable?, tag: String?, context: Map<String, Any?>) {
        if (level.logLevelValue < minimalVisibleLogLevel.logLevelValue) return
        val finalContext = this.context.mergeWith(tag, context)
        val completeContext = finalContext.compute()
        val threadName = if (minimalVisibleLogLevel.logLevelValue < LogLevel.Warn.logLevelValue) " [${Thread.currentThread().name}]" else ""
        val formattedMessage = "(${level.name.uppercase()})$threadName $message; context: $completeContext"
        when (level) {
            // We use Logcat's info level for Trace and Debug
            LogLevel.Trace -> Log.i(finalContext.tag, formattedMessage, throwable)
            LogLevel.Debug -> Log.i(finalContext.tag, formattedMessage, throwable)
            LogLevel.Info -> Log.i(finalContext.tag, formattedMessage, throwable)
            LogLevel.Warn -> Log.w(finalContext.tag, formattedMessage, throwable)
            LogLevel.Error -> Log.e(finalContext.tag, formattedMessage, throwable)
            LogLevel.Silent -> {}
        }
    }
}

internal actual val DefaultLoggerFactory: (
    @ParameterName(name = "minimalVisibleLogLevel")
    LogLevel,
    @ParameterName(name = "context")
    LogContext,
) -> Logger = { minimalVisibleLogLevel, context ->
    AndroidLogger(minimalVisibleLogLevel, context)
}
