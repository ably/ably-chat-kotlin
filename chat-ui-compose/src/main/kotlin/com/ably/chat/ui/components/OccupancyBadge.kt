package com.ably.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ably.chat.OccupancyData
import com.ably.chat.Room
import com.ably.chat.extensions.compose.collectAsOccupancy
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A composable that displays the current occupancy count of a chat room.
 *
 * Automatically subscribes to occupancy updates from the provided [Room].
 *
 * @param room The chat room to display occupancy for.
 * @param modifier Modifier to be applied to the badge.
 */
@Composable
public fun OccupancyBadge(
    room: Room,
    modifier: Modifier = Modifier,
) {
    val occupancy by room.collectAsOccupancy()

    OccupancyBadgeContent(
        count = occupancy.connections,
        modifier = modifier,
    )
}

/**
 * A composable that displays an occupancy count badge using pre-collected state.
 *
 * Use this overload when you need to manage the occupancy state yourself
 * or share it with other components.
 *
 * @param occupancy The occupancy state containing connection/subscriber counts.
 * @param modifier Modifier to be applied to the badge.
 */
@Composable
public fun OccupancyBadge(
    occupancy: State<OccupancyData>,
    modifier: Modifier = Modifier,
) {
    val occupancyData by occupancy

    OccupancyBadgeContent(
        count = occupancyData.connections,
        modifier = modifier,
    )
}

/**
 * A composable that displays an occupancy count badge with a static count.
 *
 * @param count The number to display in the badge.
 * @param modifier Modifier to be applied to the badge.
 */
@Composable
public fun OccupancyBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    OccupancyBadgeContent(
        count = count,
        modifier = modifier,
    )
}

@Composable
private fun OccupancyBadgeContent(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    val displayText = when {
        count > 99 -> "99+"
        count < 0 -> "0"
        else -> count.toString()
    }

    Box(
        modifier = modifier
            .background(
                color = colors.dateSeparatorBackground,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            color = colors.dateSeparatorText,
            fontSize = typography.occupancyBadge,
            fontWeight = FontWeight.Medium,
        )
    }
}
