package com.ably.chat

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

internal actual val DefaultLoggerFactory: (
    @ParameterName(name = "minimalVisibleLogLevel")
    LogLevel,
    @ParameterName(name = "context")
    LogContext,
) -> Logger = { minimalVisibleLogLevel, context ->
    AndroidLogger(minimalVisibleLogLevel, context)
}
