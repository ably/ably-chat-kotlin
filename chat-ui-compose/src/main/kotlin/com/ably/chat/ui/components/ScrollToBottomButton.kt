package com.ably.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A floating action button that scrolls to the bottom of a LazyColumn when clicked.
 *
 * The button automatically shows/hides based on the scroll position. It appears when
 * the user has scrolled away from the bottom by more than [threshold] items.
 *
 * @param listState The LazyListState to monitor and scroll.
 * @param modifier Modifier to be applied to the button.
 * @param threshold Number of items from the bottom before showing the button. Defaults to 3.
 * @param unreadCount Number of unread/new messages to display as a badge. If 0, no badge is shown.
 * @param onClick Callback invoked when the button is clicked. Should scroll to bottom.
 */
@Composable
public fun ScrollToBottomButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    threshold: Int = 3,
    unreadCount: Int = 0,
    onClick: () -> Unit,
) {
    val colors = AblyChatTheme.colors

    val showButton by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > threshold ||
                (listState.firstVisibleItemIndex == threshold && listState.firstVisibleItemScrollOffset > 0)
        }
    }

    AnimatedVisibility(
        visible = showButton,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Box {
            SmallFloatingActionButton(
                onClick = onClick,
                containerColor = colors.scrollFabBackground,
                contentColor = colors.scrollFabContent,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                )
            }

            // Unread count badge
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.sendButton),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = colors.scrollFabContent,
                        fontSize = if (unreadCount > 99) 8.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
