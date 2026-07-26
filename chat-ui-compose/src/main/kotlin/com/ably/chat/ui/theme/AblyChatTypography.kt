package com.ably.chat.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Defines the typography configuration for Ably Chat UI components.
 *
 * @property messageText Font size for message text content.
 * @property clientId Font size for client ID labels.
 * @property timestamp Font size for timestamp text.
 * @property inputText Font size for the message input field.
 * @property dateSeparator Font size for date separator labels.
 * @property typingIndicator Font size for typing indicator text.
 * @property participantName Font size for participant names in lists.
 * @property occupancyBadge Font size for occupancy badge count.
 * @property reactionCount Font size for reaction count text.
 * @property avatarInitials Font size for avatar initials.
 */
@Immutable
public class AblyChatTypography(
    public val messageText: TextUnit,
    public val clientId: TextUnit,
    public val timestamp: TextUnit,
    public val inputText: TextUnit,
    public val dateSeparator: TextUnit,
    public val typingIndicator: TextUnit,
    public val participantName: TextUnit,
    public val occupancyBadge: TextUnit,
    public val reactionCount: TextUnit,
    public val avatarInitials: TextUnit,
) {
    public companion object {
        /**
         * Creates the default Ably Chat typography configuration.
         */
        public fun default(): AblyChatTypography = AblyChatTypography(
            messageText = 14.sp,
            clientId = 12.sp,
            timestamp = 10.sp,
            inputText = 14.sp,
            dateSeparator = 12.sp,
            typingIndicator = 12.sp,
            participantName = 14.sp,
            occupancyBadge = 12.sp,
            reactionCount = 12.sp,
            avatarInitials = 14.sp,
        )
    }
}
