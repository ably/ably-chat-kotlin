package com.ably.chat.integration

import com.ably.chat.DefaultChatClient
import com.ably.chat.buildChatClientOptions
import com.ably.chat.serverError
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.ConnectionEvent
import io.ably.lib.realtime.ConnectionState
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
import kotlinx.coroutines.CompletableDeferred

private val client = HttpClient(CIO) {
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

class Sandbox private constructor(val appId: String, val apiKey: String) {
    companion object {
        suspend fun createInstance(): Sandbox {
            val response: HttpResponse = client.post("https://sandbox.realtime.ably-nonprod.net/apps") {
                contentType(ContentType.Application.Json)
                setBody(loadAppCreationRequestBody().toString())
            }
            val body = JsonParser.parseString(response.bodyAsText())

            return Sandbox(
                appId = body.asJsonObject["appId"].asString,
                // From JS chat repo at 7985ab7 — "The key we need to use is the one at index 5, which gives enough permissions to interact with Chat and Channels"
                apiKey = body.asJsonObject["keys"].asJsonArray[5].asJsonObject["keyStr"].asString,
            )
        }
    }
}

internal fun Sandbox.createSandboxChatClient(chatClientId: String = "sandbox-client"): DefaultChatClient {
    val realtime = createSandboxRealtime(chatClientId)
    return DefaultChatClient(realtime, buildChatClientOptions())
}

internal fun Sandbox.createSandboxRealtime(chatClientId: String): AblyRealtime =
    AblyRealtime(
        io.ably.lib.types.ClientOptions().apply {
            key = apiKey
            environment = "sandbox"
            clientId = chatClientId
        },
    )

internal suspend fun Sandbox.getConnectedChatClient(chatClientId: String = "sandbox-client"): DefaultChatClient {
    val realtime = createSandboxRealtime(chatClientId)
    realtime.ensureConnected()
    return DefaultChatClient(realtime, buildChatClientOptions())
}

private suspend fun AblyRealtime.ensureConnected() {
    if (this.connection.state == ConnectionState.connected) {
        return
    }
    val connectedDeferred = CompletableDeferred<Unit>()
    this.connection.on {
        if (it.event == ConnectionEvent.connected) {
            connectedDeferred.complete(Unit)
            this.connection.off()
        } else if (it.event != ConnectionEvent.connecting) {
            connectedDeferred.completeExceptionally(serverError("ably connection failed"))
            this.connection.off()
            this.close()
        }
    }
    connectedDeferred.await()
}

private suspend fun loadAppCreationRequestBody(): JsonElement =
    JsonParser.parseString(
        client.get("https://raw.githubusercontent.com/ably/ably-common/refs/heads/main/test-resources/test-app-setup.json") {
            contentType(ContentType.Application.Json)
        }.bodyAsText(),
    ).asJsonObject.get("post_apps")
