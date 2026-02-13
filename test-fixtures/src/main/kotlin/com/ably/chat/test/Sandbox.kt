package com.ably.chat.test

import com.ably.chat.ChatClient
import com.ably.chat.MutableRoomOptions
import com.ably.chat.Room
import com.ably.chat.RoomOptions
import com.ably.chat.occupancy
import com.ably.chat.presence
import com.ably.chat.reactions
import com.ably.chat.typing
import com.google.gson.JsonParser
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.ClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * HTTP client for sandbox API calls with retry logic.
 */
private val httpClient = HttpClient(CIO) {
    install(HttpRequestRetry) {
        maxRetries = 5
        retryIf { _, response ->
            !response.status.isSuccess()
        }
        retryOnExceptionIf { _, cause ->
            cause is ConnectTimeoutException ||
                cause is HttpRequestTimeoutException ||
                cause is SocketTimeoutException
        }
        exponentialDelay()
    }
}

/**
 * Represents an ephemeral Ably sandbox app for integration and E2E testing.
 *
 * The sandbox creates a temporary Ably application that can be used for testing
 * without requiring a real API key. The sandbox app is automatically cleaned up
 * after use.
 *
 * Usage:
 * ```kotlin
 * val sandbox = Sandbox.createInstance()
 * val chatClient = sandbox.createSandboxChatClient("my-client-id")
 * val room = chatClient.rooms.get("my-room")
 * room.attach()
 * ```
 */
class Sandbox private constructor(val appId: String, val apiKey: String) {
    companion object {
        /**
         * Creates a new sandbox instance by calling the Ably sandbox API.
         * This creates an ephemeral Ably app for testing purposes.
         */
        suspend fun createInstance(): Sandbox {
            val response: HttpResponse = httpClient.post("https://sandbox.realtime.ably-nonprod.net/apps") {
                contentType(ContentType.Application.Json)
                setBody(loadAppCreationRequestBody())
            }
            val body = JsonParser.parseString(response.bodyAsText())

            return Sandbox(
                appId = body.asJsonObject["appId"].asString,
                // The key at index 5 gives enough permissions for Chat and Channels
                apiKey = body.asJsonObject["keys"].asJsonArray[5].asJsonObject["keyStr"].asString,
            )
        }

        private suspend fun loadAppCreationRequestBody(): String =
            JsonParser.parseString(
                httpClient.get("https://raw.githubusercontent.com/ably/ably-common/refs/heads/main/test-resources/test-app-setup.json") {
                    contentType(ContentType.Application.Json)
                }.bodyAsText(),
            ).asJsonObject.get("post_apps").toString()
    }
}

/**
 * Creates a ChatClient connected to the sandbox environment.
 *
 * @param clientId The client ID to use for the connection. Defaults to "sandbox-client".
 * @return A ChatClient connected to the sandbox.
 */
fun Sandbox.createSandboxChatClient(clientId: String = "sandbox-client"): ChatClient {
    val realtime = AblyRealtime(
        ClientOptions().apply {
            key = apiKey
            environment = "sandbox"
            this.clientId = clientId
        },
    )
    return ChatClient(realtime)
}

/**
 * Room options with all features enabled for testing.
 * Includes: typing, presence, reactions, and occupancy with events.
 */
val RoomOptionsWithAllFeatures: RoomOptions
    get() = MutableRoomOptions().apply {
        typing()
        presence()
        reactions()
        occupancy {
            enableEvents = true
        }
    }

/**
 * Creates a connected room for testing.
 *
 * @param clientId The client ID to use.
 * @param roomName The name of the room to create.
 * @param roomOptions The room options to use. Defaults to [RoomOptionsWithAllFeatures].
 * @return An attached Room ready for testing.
 */
suspend fun Sandbox.createConnectedRoom(
    clientId: String,
    roomName: String,
    roomOptions: RoomOptions = RoomOptionsWithAllFeatures,
): Room {
    val chatClient = createSandboxChatClient(clientId)
    val room = chatClient.rooms.get(roomName, roomOptions)
    room.attach()
    return room
}

/**
 * Waits for a condition to become true, with timeout and polling.
 *
 * @param timeoutInMs The maximum time to wait in milliseconds. Defaults to 10 seconds.
 * @param block The condition to check. Should return true when the condition is met.
 * @throws kotlinx.coroutines.TimeoutCancellationException if the condition is not met within the timeout.
 */
suspend fun assertWaiter(timeoutInMs: Long = 10_000, block: suspend () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeout(timeoutInMs) {
            do {
                val success = block()
                delay(100)
            } while (!success)
        }
    }
}
