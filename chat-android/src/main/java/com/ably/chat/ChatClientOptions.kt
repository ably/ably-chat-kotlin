package com.ably.chat

import com.ably.chat.annotations.ChatDsl

/**
 * Configuration options for the chat client.
 */
public interface ChatClientOptions {
    /**
     * A custom log handler that will be used to log messages from the client.
     * @defaultValue The client will log messages to the console.
     */
    public val logHandler: LogHandler?

    /**
     * The minimum log level at which messages will be logged.
     * @defaultValue LogLevel.Error
     */
    public val logLevel: LogLevel
}

@ChatDsl
public class MutableChatClientOptions : ChatClientOptions {
    override var logHandler: LogHandler? = null
    override var logLevel: LogLevel = LogLevel.Error
}

public fun buildChatClientOptions(init: MutableChatClientOptions.() -> Unit = {}): ChatClientOptions =
    MutableChatClientOptions().apply(init)
