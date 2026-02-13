package com.ably.chat.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Defines the color scheme for Ably Chat UI components.
 *
 * @property ownMessageBackground Background color for messages sent by the current user.
 * @property ownMessageContent Text color for messages sent by the current user.
 * @property otherMessageBackground Background color for messages sent by other users.
 * @property otherMessageContent Text color for messages sent by other users.
 * @property timestamp Color for timestamp text.
 * @property clientId Color for client ID text.
 * @property inputBackground Background color for the message input field.
 * @property inputContent Text color for the message input field.
 * @property inputPlaceholder Placeholder text color for the message input field.
 * @property sendButton Color for the send button.
 * @property sendButtonDisabled Color for the send button when disabled.
 * @property dateSeparatorBackground Background color for date separator labels.
 * @property dateSeparatorText Text color for date separator labels.
 * @property scrollFabBackground Background color for the scroll-to-bottom FAB.
 * @property scrollFabContent Content color for the scroll-to-bottom FAB icon.
 * @property menuBackground Background color for dropdown menus.
 * @property menuContent Text color for dropdown menu items.
 * @property avatarBackground Default background color for avatar initials.
 * @property avatarText Text color for avatar initials.
 * @property presenceOnline Color for online presence indicator.
 * @property presenceOffline Color for offline presence indicator.
 * @property reactionBackground Background color for reaction pills.
 * @property reactionBackgroundSelected Background color for user's own reaction pills.
 * @property reactionText Text color for reaction count.
 * @property dialogBackground Background color for confirmation dialogs.
 * @property dialogDestructive Color for destructive action buttons in dialogs.
 * @property connectionConnecting Color for connecting/initializing connection status.
 * @property connectionDisconnected Color for disconnected/suspended connection status.
 * @property connectionFailed Color for failed connection status.
 * @property shimmerBase Base color for skeleton loading shimmer effect.
 * @property shimmerHighlight Highlight color for skeleton loading shimmer effect.
 */
@Immutable
public class AblyChatColors(
    public val ownMessageBackground: Color,
    public val ownMessageContent: Color,
    public val otherMessageBackground: Color,
    public val otherMessageContent: Color,
    public val timestamp: Color,
    public val clientId: Color,
    public val inputBackground: Color,
    public val inputContent: Color,
    public val inputPlaceholder: Color,
    public val sendButton: Color,
    public val sendButtonDisabled: Color,
    public val dateSeparatorBackground: Color,
    public val dateSeparatorText: Color,
    public val scrollFabBackground: Color,
    public val scrollFabContent: Color,
    public val menuBackground: Color,
    public val menuContent: Color,
    public val avatarBackground: Color,
    public val avatarText: Color,
    public val presenceOnline: Color,
    public val presenceOffline: Color,
    public val reactionBackground: Color,
    public val reactionBackgroundSelected: Color,
    public val reactionText: Color,
    public val dialogBackground: Color,
    public val dialogDestructive: Color,
    public val connectionConnecting: Color,
    public val connectionDisconnected: Color,
    public val connectionFailed: Color,
    public val shimmerBase: Color,
    public val shimmerHighlight: Color,
) {
    public companion object {
        /**
         * Creates the default Ably Chat color scheme.
         * Delegates to [light] for backward compatibility.
         */
        public fun default(): AblyChatColors = light()

        /**
         * Creates the light theme Ably Chat color scheme.
         */
        public fun light(): AblyChatColors = AblyChatColors(
            ownMessageBackground = Color(0xFF6B7280),
            ownMessageContent = Color.White,
            otherMessageBackground = Color(0xFF3B82F6),
            otherMessageContent = Color.White,
            timestamp = Color(0xFF9CA3AF),
            clientId = Color(0xFF6B7280),
            inputBackground = Color.White,
            inputContent = Color(0xFF1F2937),
            inputPlaceholder = Color(0xFF9CA3AF),
            sendButton = Color(0xFF3B82F6),
            sendButtonDisabled = Color(0xFF9CA3AF),
            dateSeparatorBackground = Color(0xFFF3F4F6),
            dateSeparatorText = Color(0xFF6B7280),
            scrollFabBackground = Color(0xFF3B82F6),
            scrollFabContent = Color.White,
            menuBackground = Color.White,
            menuContent = Color(0xFF1F2937),
            avatarBackground = Color(0xFF9CA3AF),
            avatarText = Color.White,
            presenceOnline = Color(0xFF22C55E),
            presenceOffline = Color(0xFF9CA3AF),
            reactionBackground = Color(0xFFF3F4F6),
            reactionBackgroundSelected = Color(0xFFDBEAFE),
            reactionText = Color(0xFF374151),
            dialogBackground = Color.White,
            dialogDestructive = Color(0xFFDC2626),
            connectionConnecting = Color(0xFFF59E0B),
            connectionDisconnected = Color(0xFFF97316),
            connectionFailed = Color(0xFFDC2626),
            shimmerBase = Color(0xFFE5E7EB),
            shimmerHighlight = Color(0xFFF9FAFB),
        )

        /**
         * Creates the dark theme Ably Chat color scheme.
         */
        public fun dark(): AblyChatColors = AblyChatColors(
            ownMessageBackground = Color(0xFF4B5563),
            ownMessageContent = Color.White,
            otherMessageBackground = Color(0xFF2563EB),
            otherMessageContent = Color.White,
            timestamp = Color(0xFF9CA3AF),
            clientId = Color(0xFF9CA3AF),
            inputBackground = Color(0xFF374151),
            inputContent = Color(0xFFF9FAFB),
            inputPlaceholder = Color(0xFF9CA3AF),
            sendButton = Color(0xFF3B82F6),
            sendButtonDisabled = Color(0xFF6B7280),
            dateSeparatorBackground = Color(0xFF374151),
            dateSeparatorText = Color(0xFF9CA3AF),
            scrollFabBackground = Color(0xFF3B82F6),
            scrollFabContent = Color.White,
            menuBackground = Color(0xFF374151),
            menuContent = Color(0xFFF9FAFB),
            avatarBackground = Color(0xFF6B7280),
            avatarText = Color.White,
            presenceOnline = Color(0xFF22C55E),
            presenceOffline = Color(0xFF6B7280),
            reactionBackground = Color(0xFF374151),
            reactionBackgroundSelected = Color(0xFF1E3A5F),
            reactionText = Color(0xFFF9FAFB),
            dialogBackground = Color(0xFF374151),
            dialogDestructive = Color(0xFFEF4444),
            connectionConnecting = Color(0xFFFBBF24),
            connectionDisconnected = Color(0xFFFB923C),
            connectionFailed = Color(0xFFEF4444),
            shimmerBase = Color(0xFF374151),
            shimmerHighlight = Color(0xFF4B5563),
        )
    }
}
