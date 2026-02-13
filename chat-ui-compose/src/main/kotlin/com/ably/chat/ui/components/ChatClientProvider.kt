package com.ably.chat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.ably.chat.ChatClient

/**
 * CompositionLocal to access the currently provided ChatClient.
 *
 * Returns null if no [ChatClientProvider] is present in the composition hierarchy.
 */
internal val LocalChatClient = compositionLocalOf<ChatClient?> { null }

/**
 * Gets the ChatClient provided by the nearest [ChatClientProvider] in the composition hierarchy.
 *
 * @return The provided ChatClient, or null if no ChatClientProvider is present.
 */
@Composable
public fun LocalChatClient(): ChatClient? = LocalChatClient.current

/**
 * Provides a ChatClient to its content via CompositionLocal.
 *
 * This allows nested components to access the ChatClient without
 * requiring it to be passed through every intermediate composable.
 *
 * Example usage:
 * ```kotlin
 * // At the top of your app
 * ChatClientProvider(chatClient = chatClient) {
 *     // All nested composables can access chatClient via LocalChatClient()
 *     MainScreen()
 * }
 *
 * // Deep in the component tree - no need to pass chatClient through props
 * @Composable
 * fun SomeNestedComponent() {
 *     val chatClient = LocalChatClient()
 *     chatClient?.let { client ->
 *         RoomProvider(
 *             chatClient = client,
 *             roomId = "my-room",
 *         ) { room ->
 *             ChatWindow(room = room)
 *         }
 *     }
 * }
 * ```
 *
 * @param chatClient The ChatClient instance to provide.
 * @param content Content that can access the ChatClient via [LocalChatClient].
 */
@Composable
public fun ChatClientProvider(
    chatClient: ChatClient,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalChatClient provides chatClient) {
        content()
    }
}
