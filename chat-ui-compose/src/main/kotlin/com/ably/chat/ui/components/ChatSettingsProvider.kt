package com.ably.chat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * Settings that control chat UI behavior and permissions.
 *
 * @property allowMessageReactions Whether users can add reactions to messages.
 * @property allowMessageEditOwn Whether users can edit their own messages.
 * @property allowMessageEditAny Whether users can edit any message (admin/moderator).
 * @property allowMessageDeleteOwn Whether users can delete their own messages.
 * @property allowMessageDeleteAny Whether users can delete any message (admin/moderator).
 * @property showTypingIndicator Whether to show the typing indicator.
 * @property showDateSeparators Whether to show date separators between messages.
 * @property showScrollToBottom Whether to show the scroll-to-bottom button.
 * @property showAvatars Whether to show avatars next to messages.
 * @property hideDeletedMessages Whether to hide deleted messages instead of showing "[Message deleted]".
 */
@Immutable
public data class ChatSettings(
    val allowMessageReactions: Boolean = true,
    val allowMessageEditOwn: Boolean = true,
    val allowMessageEditAny: Boolean = false,
    val allowMessageDeleteOwn: Boolean = true,
    val allowMessageDeleteAny: Boolean = false,
    val showTypingIndicator: Boolean = true,
    val showDateSeparators: Boolean = true,
    val showScrollToBottom: Boolean = true,
    val showAvatars: Boolean = true,
    val hideDeletedMessages: Boolean = false,
) {
    public companion object {
        /**
         * Default settings with all features enabled for own messages.
         */
        public fun default(): ChatSettings = ChatSettings()

        /**
         * Read-only settings where users cannot edit, delete, or react.
         */
        public fun readOnly(): ChatSettings = ChatSettings(
            allowMessageReactions = false,
            allowMessageEditOwn = false,
            allowMessageEditAny = false,
            allowMessageDeleteOwn = false,
            allowMessageDeleteAny = false,
        )

        /**
         * Moderator settings with full permissions.
         */
        public fun moderator(): ChatSettings = ChatSettings(
            allowMessageReactions = true,
            allowMessageEditOwn = true,
            allowMessageEditAny = true,
            allowMessageDeleteOwn = true,
            allowMessageDeleteAny = true,
        )
    }
}

/**
 * Options for configuring the [ChatSettingsProvider].
 *
 * @property globalSettings Default settings applied to all rooms.
 * @property roomSettings Room-specific settings that override global defaults. Key is the room ID.
 */
public data class ChatSettingsProviderOptions(
    val globalSettings: ChatSettings = ChatSettings.default(),
    val roomSettings: Map<String, ChatSettings> = emptyMap(),
)

/**
 * Internal state holder for chat settings.
 */
internal class ChatSettingsState(
    private val globalSettings: ChatSettings,
    private val roomSettings: Map<String, ChatSettings>,
) {
    /**
     * Gets the effective settings for a room, merging global and room-specific settings.
     * Room settings override global settings.
     */
    fun getSettingsForRoom(roomId: String?): ChatSettings {
        if (roomId == null) return globalSettings
        return roomSettings[roomId] ?: globalSettings
    }

    /**
     * Gets the global settings.
     */
    fun getGlobalSettings(): ChatSettings = globalSettings
}

/**
 * CompositionLocal for accessing chat settings state.
 */
internal val LocalChatSettingsState = compositionLocalOf<ChatSettingsState?> { null }

/**
 * A provider that manages global and room-level chat settings.
 *
 * Use this provider to set default permissions and UI options for chat components,
 * with the ability to override settings for specific rooms.
 *
 * Example usage:
 * ```kotlin
 * ChatSettingsProvider(
 *     options = ChatSettingsProviderOptions(
 *         globalSettings = ChatSettings(
 *             allowMessageReactions = true,
 *             allowMessageEditOwn = true,
 *         ),
 *         roomSettings = mapOf(
 *             "announcements" to ChatSettings.readOnly(),
 *             "moderator-chat" to ChatSettings.moderator(),
 *         ),
 *     ),
 * ) {
 *     // Child components will use these settings
 *     ChatWindow(room = room)
 * }
 * ```
 *
 * @param options Configuration options including global and room-specific settings.
 * @param content The composable content that will have access to the settings.
 */
@Composable
public fun ChatSettingsProvider(
    options: ChatSettingsProviderOptions,
    content: @Composable () -> Unit,
) {
    val settingsState = remember(options) {
        ChatSettingsState(
            globalSettings = options.globalSettings,
            roomSettings = options.roomSettings,
        )
    }

    CompositionLocalProvider(
        LocalChatSettingsState provides settingsState,
    ) {
        content()
    }
}

/**
 * A provider that manages global chat settings.
 *
 * This is a convenience overload for when you only need global settings without room-specific overrides.
 *
 * @param settings The global settings to apply.
 * @param content The composable content that will have access to the settings.
 */
@Composable
public fun ChatSettingsProvider(
    settings: ChatSettings,
    content: @Composable () -> Unit,
) {
    ChatSettingsProvider(
        options = ChatSettingsProviderOptions(globalSettings = settings),
        content = content,
    )
}

/**
 * Gets the effective chat settings for the given room ID.
 *
 * If no [ChatSettingsProvider] is present in the composition, returns default settings.
 * If a provider is present, returns room-specific settings if available, otherwise global settings.
 *
 * @param roomId The room ID to get settings for, or null to get global settings.
 * @return The effective [ChatSettings] for the room.
 */
@Composable
public fun chatSettingsFor(roomId: String?): ChatSettings {
    val state = LocalChatSettingsState.current
    return state?.getSettingsForRoom(roomId) ?: ChatSettings.default()
}

/**
 * Gets the global chat settings.
 *
 * If no [ChatSettingsProvider] is present in the composition, returns default settings.
 *
 * @return The global [ChatSettings].
 */
@Composable
public fun globalChatSettings(): ChatSettings {
    val state = LocalChatSettingsState.current
    return state?.getGlobalSettings() ?: ChatSettings.default()
}
