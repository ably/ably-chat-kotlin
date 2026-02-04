package com.ably.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * @param onClick Callback invoked when the button is clicked. Should scroll to bottom.
 */
@Composable
public fun ScrollToBottomButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    threshold: Int = 3,
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
    }
}
