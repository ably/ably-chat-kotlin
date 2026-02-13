package com.ably.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A floating pill that indicates new messages have arrived while the user is scrolled up.
 *
 * Typically displayed at the top or bottom of the message list. When clicked, scrolls to
 * the latest messages.
 *
 * @param visible Whether the pill should be visible.
 * @param count Number of new messages. If 0, shows "New messages" without a count.
 * @param modifier Modifier to be applied to the pill.
 * @param onClick Callback invoked when the pill is clicked.
 */
@Composable
public fun NewMessagesPill(
    visible: Boolean,
    count: Int = 0,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Row(
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(colors.sendButton)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.scrollFabContent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = when {
                    count <= 0 -> "New messages"
                    count == 1 -> "1 new message"
                    count > 99 -> "99+ new messages"
                    else -> "$count new messages"
                },
                color = colors.scrollFabContent,
                fontSize = typography.timestamp,
            )
        }
    }
}
