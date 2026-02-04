package com.ably.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * Default list of common reaction emojis.
 */
public val DefaultReactionEmojis: List<String> = listOf(
    "\uD83D\uDC4D", // 👍
    "\u2764\uFE0F", // ❤️
    "\uD83D\uDE02", // 😂
    "\uD83D\uDE2E", // 😮
    "\uD83D\uDE22", // 😢
    "\uD83D\uDE21", // 😡
    "\uD83D\uDE4F", // 🙏
    "\uD83D\uDD25", // 🔥
    "\uD83C\uDF89", // 🎉
    "\uD83D\uDC4F", // 👏
)

/**
 * A composable that displays a grid of selectable emojis for reactions.
 *
 * @param onEmojiSelected Callback invoked when an emoji is selected.
 * @param modifier Modifier to be applied to the picker.
 * @param emojis List of emojis to display. Defaults to [DefaultReactionEmojis].
 */
@Composable
public fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    emojis: List<String> = DefaultReactionEmojis,
) {
    val colors = AblyChatTheme.colors

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier
            .background(colors.menuBackground, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(emojis) { emoji ->
            EmojiItem(
                emoji = emoji,
                onClick = { onEmojiSelected(emoji) },
            )
        }
    }
}

/**
 * A composable that displays an emoji picker as a dropdown menu.
 *
 * @param expanded Whether the picker is currently visible.
 * @param onDismiss Callback invoked when the picker should be dismissed.
 * @param onEmojiSelected Callback invoked when an emoji is selected.
 * @param modifier Modifier to be applied to the dropdown menu.
 * @param emojis List of emojis to display. Defaults to [DefaultReactionEmojis].
 */
@Composable
public fun EmojiPickerDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    emojis: List<String> = DefaultReactionEmojis,
) {
    val colors = AblyChatTheme.colors

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.background(colors.menuBackground),
    ) {
        // Use Row/Column instead of LazyVerticalGrid to avoid intrinsic measurement issues
        val columns = 5
        val rows = (emojis.size + columns - 1) / columns

        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(rows) { rowIndex ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(columns) { colIndex ->
                        val index = rowIndex * columns + colIndex
                        if (index < emojis.size) {
                            EmojiItem(
                                emoji = emojis[index],
                                onClick = {
                                    onEmojiSelected(emojis[index])
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiItem(
    emoji: String,
    onClick: () -> Unit,
) {
    val colors = AblyChatTheme.colors

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(colors.reactionBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 20.sp,
        )
    }
}
