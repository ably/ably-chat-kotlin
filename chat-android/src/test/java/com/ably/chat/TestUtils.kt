package com.ably.chat

import com.ably.http.HttpMethod
import com.ably.pubsub.RealtimeClient
import com.google.gson.JsonElement
import io.ably.lib.types.AsyncHttpPaginatedResponse
import io.mockk.every
import io.mockk.mockk
import java.lang.reflect.Field
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

fun buildAsyncHttpPaginatedResponse(items: List<JsonElement>): AsyncHttpPaginatedResponse {
    val response = mockk<AsyncHttpPaginatedResponse>()
    every {
        response.items()
    } returns items.toTypedArray()
    return response
}

fun mockMessagesApiResponse(realtimeClientMock: RealtimeClient, response: List<JsonElement>, roomId: String = "roomId") {
    every {
        realtimeClientMock.requestAsync("/chat/v3/rooms/$roomId/messages", any(), HttpMethod.Get, any(), any(), any())
    } answers {
        val callback = secondArg<AsyncHttpPaginatedResponse.Callback>()
        callback.onResponse(
            buildAsyncHttpPaginatedResponse(response),
        )
    }
}

fun mockSendMessageApiResponse(realtimeClientMock: RealtimeClient, response: JsonElement, roomId: String = "roomId") {
    every {
        realtimeClientMock.requestAsync("/chat/v3/rooms/$roomId/messages", any(), HttpMethod.Post, any(), any(), any())
    } answers {
        val callback = secondArg<AsyncHttpPaginatedResponse.Callback>()
        callback.onResponse(
            buildAsyncHttpPaginatedResponse(
                listOf(response),
            ),
        )
    }
}

fun mockOccupancyApiResponse(realtimeClientMock: RealtimeClient, response: JsonElement, roomId: String = "roomId") {
    every {
        realtimeClientMock.requestAsync("/chat/v3/rooms/$roomId/occupancy", any(), HttpMethod.Get, any(), any(), any())
    } answers {
        val callback = secondArg<AsyncHttpPaginatedResponse.Callback>()
        callback.onResponse(
            buildAsyncHttpPaginatedResponse(
                listOf(response),
            ),
        )
    }
}

internal class EmptyLogger(override val context: LogContext) : Logger {
    override fun withContext(tag: String?, staticContext: Map<String, String>, dynamicContext: Map<String, () -> String>): Logger = this
    override fun log(message: String, level: LogLevel, throwable: Throwable?, tag: String?, context: Map<String, String>) = Unit
}

fun Occupancy.subscribeOnce(listener: Occupancy.Listener) {
    lateinit var subscription: Subscription
    subscription = subscribe {
        listener.onEvent(it)
        subscription.unsubscribe()
    }
}

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

fun Any.setPrivateField(name: String, value: Any?) {
    val valueField = javaClass.findField(name)
    valueField.isAccessible = true
    valueField.set(this, value)
}

fun <T>Any.getPrivateField(name: String): T {
    val valueField = javaClass.findField(name)
    valueField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return valueField.get(this) as T
}

private fun Class<*>.findField(name: String): Field {
    var result = kotlin.runCatching { getDeclaredField(name) }
    var currentClass = this
    while (result.isFailure && currentClass.superclass != null) // stop when we got field or reached top of class hierarchy
    {
        currentClass = currentClass.superclass!!
        result = kotlin.runCatching { currentClass.getDeclaredField(name) }
    }
    if (result.isFailure) {
        throw result.exceptionOrNull() as Exception
    }
    return result.getOrNull() as Field
}

suspend fun <T>Any.invokePrivateSuspendMethod(methodName: String, vararg args: Any?) = suspendCancellableCoroutine<T> { cont ->
    val suspendMethod = javaClass.declaredMethods.find { it.name == methodName }
    suspendMethod?.let {
        it.isAccessible = true
        it.invoke(this, *args, cont)
    }
}

fun <T> Any.invokePrivateMethod(methodName: String, vararg args: Any?): T {
    val method = javaClass.declaredMethods.find { it.name == methodName }
    method?.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return method?.invoke(this, *args) as T
}
