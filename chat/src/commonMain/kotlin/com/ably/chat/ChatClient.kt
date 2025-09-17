package com.ably.chat

import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.pubsub.WrapperSdkProxyOptions
import com.ably.pubsub.createWrapperSdkProxy
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.RealtimeClient

/**
 * This is the core client for Ably chat. It provides access to chat rooms.
 */
public interface ChatClient {
    /**
     * The rooms object, which provides access to chat rooms.
     */
    public val rooms: Rooms

    /**
     * The underlying connection to Ably, which can be used to monitor the clients
     * connection to Ably servers.
     */
    public val connection: Connection

    /**
     * The clientId of the current client.
     */
    public val clientId: String

    /**
     * The underlying Ably Realtime client.
     */
    public val realtime: AblyRealtime

    /**
     * The chat client options for the client, including any defaults that have been set.
     */
    public val clientOptions: ChatClientOptions
}

/**
 * @param realtimeClient The Ably Realtime client
 * @param clientOptions The client options.
 * @return [ChatClient] with the specified options
 */
public fun ChatClient(realtimeClient: AblyRealtime, clientOptions: ChatClientOptions = buildChatClientOptions()): ChatClient =
    DefaultChatClient(realtimeClient, clientOptions)

/**
 * @param realtimeClient The Ably Realtime client
 * @param init Kotlin type-safe builder for client options
 * @return [ChatClient] with the specified options
 */
public fun ChatClient(realtimeClient: AblyRealtime, init: MutableChatClientOptions.() -> Unit): ChatClient =
    ChatClient(realtimeClient, buildChatClientOptions(init))

internal class DefaultChatClient(
    override val realtime: AblyRealtime,
    override val clientOptions: ChatClientOptions,
) : ChatClient {

    @OptIn(ExperimentalChatApi::class)
    val stateDispatcher get() = clientOptions.stateDispatcher

    private val realtimeClientWrapper = RealtimeClient(realtime).createWrapperSdkProxy(
        WrapperSdkProxyOptions(
            agents = mapOf("chat-kotlin" to BuildConfig.APP_VERSION),
        ),
    )

    private val logger: Logger = clientOptions.logHandler?.let {
        CustomLogger(
            it,
            clientOptions.logLevel,
            buildLogContext(),
        )
    } ?: DefaultLoggerFactory(clientOptions.logLevel, buildLogContext())

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
        dispatcher = stateDispatcher,
    )

    override val clientId: String
        get() = realtimeClientWrapper.auth.clientId

    private fun buildLogContext() = DefaultLogContext(
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
