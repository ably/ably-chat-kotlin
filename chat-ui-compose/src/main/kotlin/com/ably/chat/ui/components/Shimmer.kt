package com.ably.chat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ably.chat.ui.theme.AblyChatTheme
import kotlin.random.Random

/**
 * A modifier that applies a shimmer effect to the composable.
 *
 * Use this on placeholder elements to indicate loading state.
 */
public fun Modifier.shimmer(): Modifier = composed {
    val colors = AblyChatTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")

    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    val shimmerColors = listOf(
        colors.shimmerBase,
        colors.shimmerHighlight,
        colors.shimmerBase,
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim),
    )

    background(brush)
}

/**
 * A skeleton placeholder for a single message bubble.
 *
 * @param isOwnMessage Whether to show as own message (right-aligned) or other's message (left-aligned).
 * @param showAvatar Whether to show an avatar placeholder.
 * @param modifier Modifier to be applied to the skeleton.
 */
@Composable
public fun MessageBubbleSkeleton(
    isOwnMessage: Boolean = false,
    showAvatar: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (showAvatar && !isOwnMessage) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .shimmer(),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
        ) {
            // Client ID placeholder (only for other's messages)
            if (!isOwnMessage) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(12.dp)
                        .padding(bottom = 2.dp, start = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer(),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Message bubble placeholder
            Box(
                modifier = Modifier
                    .width(randomWidth())
                    .height(randomHeight())
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                            bottomEnd = if (isOwnMessage) 4.dp else 16.dp,
                        ),
                    )
                    .shimmer(),
            )

            // Timestamp placeholder
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(10.dp)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer(),
            )
        }
    }
}

/**
 * A skeleton placeholder for a list of messages.
 *
 * @param itemCount Number of skeleton items to show.
 * @param showAvatars Whether to show avatar placeholders.
 * @param modifier Modifier to be applied to the list.
 */
@Composable
public fun MessageListSkeleton(
    itemCount: Int = 6,
    showAvatars: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 8.dp,
        ),
    ) {
        items(itemCount) { index ->
            // Alternate between own and other messages for visual variety
            MessageBubbleSkeleton(
                isOwnMessage = index % 3 == 0,
                showAvatar = showAvatars && index % 3 != 0,
            )
        }
    }
}

/**
 * A skeleton placeholder for a participant list item.
 *
 * @param modifier Modifier to be applied to the skeleton.
 */
@Composable
public fun ParticipantSkeleton(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .shimmer(),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Name placeholder
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer(),
        )
    }
}

/**
 * A skeleton placeholder for a participant list.
 *
 * @param itemCount Number of skeleton items to show.
 * @param modifier Modifier to be applied to the list.
 */
@Composable
public fun ParticipantListSkeleton(
    itemCount: Int = 5,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        repeat(itemCount) {
            ParticipantSkeleton()
        }
    }
}

/**
 * A skeleton placeholder for a room list item.
 *
 * @param modifier Modifier to be applied to the skeleton.
 */
@Composable
public fun RoomItemSkeleton(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .shimmer(),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Room name placeholder
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer(),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Last message placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer(),
            )
        }

        // Time placeholder
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer(),
        )
    }
}

/**
 * A skeleton placeholder for a room list.
 *
 * @param itemCount Number of skeleton items to show.
 * @param modifier Modifier to be applied to the list.
 */
@Composable
public fun RoomListSkeleton(
    itemCount: Int = 5,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        repeat(itemCount) {
            RoomItemSkeleton()
        }
    }
}

// Helper functions for randomized skeleton dimensions
@Composable
private fun randomWidth(): Dp {
    val widths = listOf(120.dp, 150.dp, 180.dp, 200.dp, 220.dp)
    return widths[Random.nextInt(widths.size)]
}

@Composable
private fun randomHeight(): Dp {
    val heights = listOf(36.dp, 44.dp, 52.dp, 60.dp)
    return heights[Random.nextInt(heights.size)]
}
