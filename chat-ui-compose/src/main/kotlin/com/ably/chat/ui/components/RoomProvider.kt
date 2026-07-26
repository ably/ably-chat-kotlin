package com.ably.chat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ably.chat.ChatClient
import com.ably.chat.ChatException
import com.ably.chat.ErrorInfo
import com.ably.chat.Room
import com.ably.chat.RoomOptions
import com.ably.chat.ui.theme.AblyChatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Represents the state of a room during its lifecycle.
 */
public sealed interface RoomState {
    /**
     * The room is being fetched and attached.
     */
    public data object Loading : RoomState

    /**
     * The room is successfully attached and ready for use.
     *
     * @property room The attached room instance.
     */
    public data class Attached(val room: Room) : RoomState

    /**
     * The room failed to attach.
     *
     * @property error The error information describing the failure.
     */
    public data class Error(val error: ErrorInfo) : RoomState
}

/**
 * CompositionLocal to access the currently provided Room.
 *
 * Returns null if no [RoomProvider] is present in the composition hierarchy.
 */
internal val LocalRoom = compositionLocalOf<Room?> { null }

/**
 * Gets the Room provided by the nearest [RoomProvider] in the composition hierarchy.
 *
 * @return The provided Room, or null if no RoomProvider is present.
 */
@Composable
public fun LocalRoom(): Room? = LocalRoom.current

/**
 * Provides a Room to its content, handling the complete lifecycle:
 * - Gets the room from ChatClient.rooms
 * - Attaches the room when composed
 * - Releases the room when disposed
 *
 * The room is re-fetched and re-attached if any of [chatClient], [roomId], or [options] change.
 *
 * Example usage:
 * ```kotlin
 * RoomProvider(
 *     chatClient = chatClient,
 *     roomId = "my-room",
 *     options = RoomOptions.default,
 * ) { room ->
 *     ChatWindow(room = room)
 * }
 * ```
 *
 * @param chatClient The ChatClient instance to get rooms from.
 * @param roomId The room name/ID to get.
 * @param options Room options (typing, presence, reactions, occupancy).
 * @param loadingContent Content shown while room is attaching.
 * @param errorContent Content shown if attachment fails.
 * @param content Content rendered when room is attached, receives the Room.
 */
@Composable
public fun RoomProvider(
    chatClient: ChatClient,
    roomId: String,
    options: RoomOptions = DefaultRoomOptions,
    loadingContent: @Composable () -> Unit = { DefaultRoomLoading() },
    errorContent: @Composable (ErrorInfo) -> Unit = { DefaultRoomError(it) },
    content: @Composable (Room) -> Unit,
) {
    val roomState by rememberRoomState(chatClient, roomId, options)

    when (val state = roomState) {
        is RoomState.Loading -> loadingContent()
        is RoomState.Error -> errorContent(state.error)
        is RoomState.Attached -> {
            CompositionLocalProvider(LocalRoom provides state.room) {
                content(state.room)
            }
        }
    }
}

/**
 * Remembers and manages the room state for the given parameters.
 *
 * This is a lower-level API for advanced use cases where you need direct access
 * to the room state. For most cases, use [RoomProvider] instead.
 *
 * @param chatClient The ChatClient instance to get rooms from.
 * @param roomId The room name/ID to get.
 * @param options Room options (typing, presence, reactions, occupancy).
 * @return A State containing the current [RoomState].
 */
@Composable
public fun rememberRoomState(
    chatClient: ChatClient,
    roomId: String,
    options: RoomOptions = DefaultRoomOptions,
): State<RoomState> {
    val roomState = remember(chatClient, roomId, options) {
        mutableStateOf<RoomState>(RoomState.Loading)
    }

    DisposableEffect(chatClient, roomId, options) {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        scope.launch {
            try {
                val room = chatClient.rooms.get(roomId, options)
                room.attach()
                roomState.value = RoomState.Attached(room)
            } catch (e: ChatException) {
                roomState.value = RoomState.Error(e.errorInfo)
            } catch (e: Exception) {
                roomState.value = RoomState.Error(
                    ErrorInfo(
                        message = e.message ?: "Unknown error",
                        code = 0,
                        statusCode = 0,
                        href = null,
                        cause = null,
                        requestId = null,
                    ),
                )
            }
        }

        onDispose {
            // Cancel the main scope first to stop any pending attach operations
            scope.cancel()
            // Launch release in a new scope that won't be cancelled
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                try {
                    chatClient.rooms.release(roomId)
                } catch (_: Exception) {
                    // Ignore release errors
                }
            }
        }
    }

    return roomState
}

/**
 * Default loading content shown while the room is being fetched and attached.
 */
@Composable
public fun DefaultRoomLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = AblyChatTheme.colors.sendButton,
        )
    }
}

/**
 * Default error content shown when room attachment fails.
 *
 * @param error The error information to display.
 */
@Composable
public fun DefaultRoomError(error: ErrorInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Failed to connect to room",
            fontSize = AblyChatTheme.typography.messageText,
            color = AblyChatTheme.colors.connectionFailed,
        )
        Text(
            text = error.message,
            fontSize = AblyChatTheme.typography.timestamp,
            color = AblyChatTheme.colors.timestamp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Default room options used when none are specified.
 */
private val DefaultRoomOptions: RoomOptions = com.ably.chat.MutableRoomOptions()
