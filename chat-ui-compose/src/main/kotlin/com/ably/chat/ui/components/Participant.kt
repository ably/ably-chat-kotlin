package com.ably.chat.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ably.chat.PresenceMember
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A composable that displays a single participant row.
 *
 * Shows the participant's avatar with presence indicator overlay,
 * their name/clientId, and a typing indicator if they're typing.
 *
 * @param member The presence member to display.
 * @param isCurrentUser Whether this participant is the current user.
 * @param isTyping Whether this participant is currently typing.
 * @param modifier Modifier to be applied to the row.
 * @param imageUrl Optional URL for the participant's avatar image.
 */
@Composable
public fun Participant(
    member: PresenceMember,
    isCurrentUser: Boolean,
    isTyping: Boolean,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    // Check for avatar provider to get display name
    val avatarProvider = LocalAvatarProvider.current
    val resolvedData = avatarProvider?.getAvatarData(member.clientId)
    val displayName = resolvedData?.displayName ?: member.clientId

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar with presence indicator
        Box {
            Avatar(
                clientId = member.clientId,
                imageUrl = imageUrl,
                size = AvatarSize.Medium,
            )

            // Presence indicator overlay
            PresenceIndicator(
                isOnline = true, // Member is present, so they're online
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
                size = 12.dp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name with optional "(you)" suffix
        Text(
            text = if (isCurrentUser) "$displayName (you)" else displayName,
            color = colors.menuContent,
            fontSize = typography.participantName,
            fontWeight = if (isCurrentUser) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )

        // Typing indicator
        if (isTyping) {
            ParticipantTypingDots()
        }
    }
}

/**
 * A composable that displays a single participant row with presence status.
 *
 * @param clientId The client ID of the participant.
 * @param isOnline Whether this participant is currently online.
 * @param isCurrentUser Whether this participant is the current user.
 * @param isTyping Whether this participant is currently typing.
 * @param modifier Modifier to be applied to the row.
 * @param imageUrl Optional URL for the participant's avatar image.
 */
@Composable
public fun Participant(
    clientId: String,
    isOnline: Boolean,
    isCurrentUser: Boolean,
    isTyping: Boolean,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    // Check for avatar provider to get display name
    val avatarProvider = LocalAvatarProvider.current
    val resolvedData = avatarProvider?.getAvatarData(clientId)
    val displayName = resolvedData?.displayName ?: clientId

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar with presence indicator
        Box {
            Avatar(
                clientId = clientId,
                imageUrl = imageUrl,
                size = AvatarSize.Medium,
            )

            // Presence indicator overlay
            PresenceIndicator(
                isOnline = isOnline,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
                size = 12.dp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name with optional "(you)" suffix
        Text(
            text = if (isCurrentUser) "$displayName (you)" else displayName,
            color = colors.menuContent,
            fontSize = typography.participantName,
            fontWeight = if (isCurrentUser) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )

        // Typing indicator
        if (isTyping) {
            ParticipantTypingDots()
        }
    }
}

/**
 * Small typing indicator dots shown next to a participant.
 */
@Composable
private fun ParticipantTypingDots() {
    val colors = AblyChatTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "participant_typing_dots")

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0
                        -4f at 200 + (index * 150)
                        0f at 400 + (index * 150)
                        0f at 1200
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dot_$index",
            )

            Surface(
                modifier = Modifier
                    .size(4.dp)
                    .offset(y = offsetY.dp),
                shape = CircleShape,
                color = colors.timestamp,
            ) {}
        }
    }
}
