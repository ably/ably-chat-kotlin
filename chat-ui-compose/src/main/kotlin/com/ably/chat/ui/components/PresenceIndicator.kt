package com.ably.chat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A composable that displays a presence status indicator dot.
 *
 * The indicator shows green when online and gray when offline,
 * with a smooth color transition animation between states.
 *
 * This component is typically used as an overlay on top of an [Avatar].
 *
 * @param isOnline Whether the user is currently online.
 * @param modifier Modifier to be applied to the indicator.
 * @param size The diameter of the indicator dot. Defaults to 12.dp.
 * @param borderColor Optional border color to provide contrast against the avatar. Defaults to white.
 * @param borderWidth Width of the border. Defaults to 2.dp.
 */
@Composable
public fun PresenceIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    borderColor: Color = Color.White,
    borderWidth: Dp = 2.dp,
) {
    val colors = AblyChatTheme.colors

    val indicatorColor by animateColorAsState(
        targetValue = if (isOnline) colors.presenceOnline else colors.presenceOffline,
        animationSpec = tween(durationMillis = 300),
        label = "presence_color",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(indicatorColor)
            .border(borderWidth, borderColor, CircleShape),
    )
}
