package com.ably.chat.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ably.chat.Room
import com.ably.chat.annotations.InternalChatApi
import com.ably.chat.extensions.compose.collectAsCurrentlyTyping
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A composable that displays a typing indicator showing who is currently typing.
 *
 * This component automatically subscribes to typing events from the room and displays
 * the names of users who are currently typing, including the current user (shown as "You").
 *
 * @param room The chat room to monitor for typing events.
 * @param modifier Modifier to be applied to the indicator.
 * @param includeCurrentUser Whether to show when the current user is typing. Defaults to true.
 */
@OptIn(InternalChatApi::class)
@Composable
public fun TypingIndicator(
    room: Room,
    modifier: Modifier = Modifier,
    includeCurrentUser: Boolean = true,
) {
    val currentlyTyping by room.collectAsCurrentlyTyping()
    val currentClientId = room.clientId

    TypingIndicatorContent(
        typingClientIds = currentlyTyping,
        currentClientId = if (includeCurrentUser) currentClientId else null,
        modifier = modifier,
    )
}

/**
 * A composable that displays a typing indicator using a pre-collected typing state.
 *
 * Use this overload when you need to manage the typing state yourself or share it with other components.
 *
 * @param typingState The state containing the set of currently typing client IDs.
 * @param currentClientId The current user's client ID. If provided and present in typingState, shows "You".
 * @param modifier Modifier to be applied to the indicator.
 */
@Composable
public fun TypingIndicator(
    typingState: State<Set<String>>,
    currentClientId: String? = null,
    modifier: Modifier = Modifier,
) {
    val currentlyTyping by typingState

    TypingIndicatorContent(
        typingClientIds = currentlyTyping,
        currentClientId = currentClientId,
        modifier = modifier,
    )
}

@Composable
private fun TypingIndicatorContent(
    typingClientIds: Set<String>,
    currentClientId: String?,
    modifier: Modifier,
) {
    if (typingClientIds.isEmpty()) return

    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography
    val displayNames = typingClientIds.map { clientId ->
        if (clientId == currentClientId) "You" else clientId
    }

    val text = formatTypingText(displayNames)

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TypingDots()

        Text(
            text = text,
            color = colors.timestamp,
            fontSize = typography.typingIndicator,
        )
    }
}

@Composable
private fun TypingDots() {
    val colors = AblyChatTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.otherMessageBackground.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Staggered wave animation - each dot bounces with a delay
        val animationDuration = 1200
        val dotDelay = 150

        repeat(3) { index ->
            val startDelay = index * dotDelay

            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = animationDuration
                        0f at startDelay // Start at rest
                        -5f at startDelay + 150 // Bounce up
                        0f at startDelay + 300 // Return to rest
                        0f at animationDuration // Stay at rest for remaining time
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dot$index",
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = offsetY.dp)
                    .clip(CircleShape)
                    .background(colors.otherMessageBackground),
            )
        }
    }
}

private fun formatTypingText(names: List<String>): String {
    return when {
        names.isEmpty() -> ""
        names.size == 1 -> "${names[0]} is typing..."
        names.size == 2 -> "${names[0]} and ${names[1]} are typing..."
        names.size == 3 -> "${names[0]}, ${names[1]}, and ${names[2]} are typing..."
        else -> {
            val firstTwo = names.take(2).joinToString(", ")
            val remaining = names.size - 2
            "$firstTwo, and $remaining others are typing..."
        }
    }
}
