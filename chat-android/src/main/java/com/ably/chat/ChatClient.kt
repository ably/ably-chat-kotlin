package com.ably.chat

import com.ably.pubsub.WrapperSdkProxyOptions
import com.ably.pubsub.createWrapperSdkProxy
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.RealtimeClient

/**
 * This is the core client for Ably chat. It provides access to chat rooms.
 */
interface ChatClient {
    /**
     * The rooms object, which provides access to chat rooms.
     */
    val rooms: Rooms

    /**
     * The underlying connection to Ably, which can be used to monitor the clients
     * connection to Ably servers.
     */
    val connection: Connection

    /**
     * The clientId of the current client.
     */
    val clientId: String

    /**
     * The underlying Ably Realtime client.
     */
    val realtime: AblyRealtime

    /**
     * The chat client options for the client, including any defaults that have been set.
     */
    val clientOptions: ChatClientOptions
}

fun ChatClient(realtimeClient: AblyRealtime, clientOptions: ChatClientOptions = ChatClientOptions()): ChatClient =
    DefaultChatClient(realtimeClient, clientOptions)

internal class DefaultChatClient(
    override val realtime: AblyRealtime,
    override val clientOptions: ChatClientOptions,
) : ChatClient {

    private val realtimeClientWrapper = RealtimeClient(realtime).createWrapperSdkProxy(
        WrapperSdkProxyOptions(
            agents = mapOf("chat-kotlin" to BuildConfig.APP_VERSION),
        ),
    )

    private val logger: Logger = if (clientOptions.logHandler != null) {
        CustomLogger(
            clientOptions.logHandler,
            clientOptions.logLevel,
            buildLogContext(),
        )
    } else {
        AndroidLogger(clientOptions.logLevel, buildLogContext())
    }

    private val chatApi = ChatApi(realtimeClientWrapper, clientId, logger)

    override val rooms: Rooms = DefaultRooms(
        realtimeClient = realtimeClientWrapper,
        chatApi = chatApi,
        clientOptions = clientOptions,
        clientId = clientId,
        logger = logger,
    )

    override val connection: Connection = DefaultConnection(
        pubSubConnection = realtimeClientWrapper.connection,
        logger = logger.withContext(tag = "RealtimeConnection"),
    )

    override val clientId: String
        get() = realtimeClientWrapper.auth.clientId

    private fun buildLogContext() = LogContext(
        tag = "ChatClient",
        staticContext = mapOf(
            "clientId" to clientId,
            "instanceId" to generateUUID(),
        ),
        dynamicContext = mapOf(
            "connectionId" to { realtimeClientWrapper.connection.id },
            "connectionState" to { realtimeClientWrapper.connection.state.name },
        ),
    )
}
