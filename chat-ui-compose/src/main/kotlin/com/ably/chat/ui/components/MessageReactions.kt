package com.ably.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ably.chat.Message
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * Represents a single reaction for display.
 *
 * @property emoji The emoji or reaction name.
 * @property count The total count of this reaction.
 * @property isOwnReaction Whether the current user has reacted with this emoji.
 */
public data class ReactionDisplay(
    val emoji: String,
    val count: Int,
    val isOwnReaction: Boolean,
)

/**
 * A composable that displays message reactions as clickable pills.
 *
 * Each reaction pill shows the emoji and its count. The user's own reactions
 * are highlighted with a different background color.
 *
 * @param message The message whose reactions to display.
 * @param currentClientId The clientId of the current user, used to highlight their reactions.
 * @param onReactionClick Callback invoked when a reaction pill is clicked. Receives the emoji name.
 * @param modifier Modifier to be applied to the reactions row.
 */
@Composable
public fun MessageReactions(
    message: Message,
    currentClientId: String,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reactions = buildReactionDisplayList(message, currentClientId)

    if (reactions.isEmpty()) return

    MessageReactionsContent(
        reactions = reactions,
        onReactionClick = onReactionClick,
        modifier = modifier,
    )
}

/**
 * A composable that displays message reactions from a pre-built list.
 *
 * @param reactions List of reactions to display.
 * @param onReactionClick Callback invoked when a reaction pill is clicked. Receives the emoji name.
 * @param modifier Modifier to be applied to the reactions row.
 */
@Composable
public fun MessageReactions(
    reactions: List<ReactionDisplay>,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return

    MessageReactionsContent(
        reactions = reactions,
        onReactionClick = onReactionClick,
        modifier = modifier,
    )
}

@Composable
private fun MessageReactionsContent(
    reactions: List<ReactionDisplay>,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (reaction.isOwnReaction) {
                            colors.reactionBackgroundSelected
                        } else {
                            colors.reactionBackground
                        },
                    )
                    .clickable { onReactionClick(reaction.emoji) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = reaction.emoji,
                    fontSize = typography.reactionCount,
                )
                if (reaction.count > 1) {
                    Text(
                        text = reaction.count.toString(),
                        color = colors.reactionText,
                        fontSize = typography.reactionCount,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Builds a list of [ReactionDisplay] from a message's reaction summary.
 */
private fun buildReactionDisplayList(message: Message, currentClientId: String): List<ReactionDisplay> {
    val reactions = mutableListOf<ReactionDisplay>()
    val summary = message.reactions

    // Process unique reactions (one per client)
    summary.unique.forEach { (emoji, data) ->
        reactions.add(
            ReactionDisplay(
                emoji = emoji,
                count = data.total,
                isOwnReaction = data.clientIds.contains(currentClientId),
            ),
        )
    }

    // Process distinct reactions (one per emoji per client)
    summary.distinct.forEach { (emoji, data) ->
        // Skip if already added from unique
        if (reactions.none { it.emoji == emoji }) {
            reactions.add(
                ReactionDisplay(
                    emoji = emoji,
                    count = data.total,
                    isOwnReaction = data.clientIds.contains(currentClientId),
                ),
            )
        }
    }

    // Process multiple reactions (can have multiple of same emoji per client)
    summary.multiple.forEach { (emoji, data) ->
        // Skip if already added
        if (reactions.none { it.emoji == emoji }) {
            reactions.add(
                ReactionDisplay(
                    emoji = emoji,
                    count = data.total,
                    isOwnReaction = data.clientIds.containsKey(currentClientId),
                ),
            )
        }
    }

    return reactions.sortedByDescending { it.count }
}
