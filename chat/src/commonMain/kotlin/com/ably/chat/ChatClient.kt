package com.ably.chat

import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.pubsub.WrapperSdkProxyOptions
import com.ably.pubsub.createWrapperSdkProxy
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.RealtimeClient

/**
 * This is the core client for Ably chat. It provides access to chat rooms.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
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
     *
     * This will be null if the realtime client uses token-based auth
     * without specifying `clientId` in `clientOptions` and has not yet been authenticated.
     */
    public val clientId: String?

    /**
     * The underlying Ably Realtime client.
     */
    @ExperimentalChatApi
    public val realtime: AblyRealtime

    /**
     * The chat client options for the client, including any defaults that have been set.
     */
    public val clientOptions: ChatClientOptions

    /**
     * Disposes of the chat client, releasing all rooms and cleaning up resources.
     * After calling this, the client should not be used.
     * Spec: CHA-CL1
     */
    public suspend fun dispose()
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

    val stateDispatcher get() = clientOptions.stateDispatcher

    private val realtimeClientWrapper = RealtimeClient(realtime).createWrapperSdkProxy(
        WrapperSdkProxyOptions(
            agents = mapOf(
                "chat-kotlin" to BuildConfig.APP_VERSION,
                PlatformSpecificAgent to BuildConfig.APP_VERSION,
            ),
        ),
    )

    private val logger: Logger = clientOptions.logHandler?.let {
        CustomLogger(
            it,
            clientOptions.logLevel,
            buildLogContext(),
        )
    } ?: DefaultLoggerFactory(clientOptions.logLevel, buildLogContext())

    private val clientIdResolver = ClientIdResolver(realtimeClientWrapper)

    private val chatApi = ChatApi(realtimeClientWrapper, logger)

    override val rooms: Rooms = DefaultRooms(
        realtimeClient = realtimeClientWrapper,
        chatApi = chatApi,
        clientIdResolver = clientIdResolver,
        logger = logger,
    )

    override val connection: Connection = DefaultConnection(
        pubSubConnection = realtimeClientWrapper.connection,
        logger = logger.withContext(tag = "RealtimeConnection"),
        dispatcher = stateDispatcher,
    )

    override val clientId: String?
        get() = realtimeClientWrapper.auth.clientId

    // CHA-CL1
    override suspend fun dispose() {
        (rooms as DefaultRooms).dispose() // CHA-CL1a
        (connection as DefaultConnection).dispose() // CHA-CL1b
    }

    private fun buildLogContext() = DefaultLogContext(
        tag = "ChatClient",
        staticContext = mapOf(
            "instanceId" to generateUUID(),
        ),
        dynamicContext = mapOf(
            "clientId" to { clientId },
            "connectionId" to { realtimeClientWrapper.connection.id },
            "connectionState" to { realtimeClientWrapper.connection.state.name },
        ),
    )
}
