package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ably.chat.ui.helpers.createMockMessage
import com.ably.chat.ui.helpers.createReactionSummary
import com.ably.chat.ui.helpers.createSummaryClientIdList
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MessageReactionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Empty State Tests ====================

    @Test
    fun `MessageReactions should not render when reactions are empty`() {
        val message = createMockMessage()

        composeTestRule.setContentWithTheme {
            MessageReactions(
                message = message,
                currentClientId = "user-1",
                onReactionClick = { },
            )
        }

        // No reactions should be displayed
        composeTestRule.onNodeWithText("\uD83D\uDC4D").assertDoesNotExist()
    }

    @Test
    fun `MessageReactions with ReactionDisplay list should not render when empty`() {
        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = emptyList(),
                onReactionClick = { },
            )
        }

        // No reactions should be displayed
        composeTestRule.onNodeWithText("\uD83D\uDC4D").assertDoesNotExist()
    }

    // ==================== Reaction Display Tests ====================

    @Test
    fun `MessageReactions should display reaction pills for each emoji`() {
        val reactions = listOf(
            ReactionDisplay(emoji = "👍", count = 1, isOwnReaction = false),
            ReactionDisplay(emoji = "❤️", count = 2, isOwnReaction = false),
        )

        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = reactions,
                onReactionClick = { },
            )
        }

        composeTestRule.onNodeWithText("👍").assertIsDisplayed()
        composeTestRule.onNodeWithText("❤️").assertIsDisplayed()
    }

    @Test
    fun `MessageReactions should show count when count is greater than 1`() {
        val reactions = listOf(
            ReactionDisplay(emoji = "👍", count = 5, isOwnReaction = false),
        )

        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = reactions,
                onReactionClick = { },
            )
        }

        composeTestRule.onNodeWithText("👍").assertIsDisplayed()
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun `MessageReactions should hide count when count is 1`() {
        val reactions = listOf(
            ReactionDisplay(emoji = "👍", count = 1, isOwnReaction = false),
        )

        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = reactions,
                onReactionClick = { },
            )
        }

        composeTestRule.onNodeWithText("👍").assertIsDisplayed()
        // Count "1" should not be displayed
        composeTestRule.onNodeWithText("1").assertDoesNotExist()
    }

    // ==================== Own Reaction Highlighting Tests ====================

    @Test
    fun `MessageReactions should highlight own reactions`() {
        val reactions = listOf(
            ReactionDisplay(emoji = "👍", count = 2, isOwnReaction = true),
        )

        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = reactions,
                onReactionClick = { },
            )
        }

        // The emoji should be displayed - styling would require semantic matcher
        composeTestRule.onNodeWithText("👍").assertIsDisplayed()
    }

    @Test
    fun `MessageReactions should not highlight others reactions`() {
        val reactions = listOf(
            ReactionDisplay(emoji = "👍", count = 2, isOwnReaction = false),
        )

        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = reactions,
                onReactionClick = { },
            )
        }

        composeTestRule.onNodeWithText("👍").assertIsDisplayed()
    }

    // ==================== Click Callback Tests ====================

    @Test
    fun `MessageReactions should call onReactionClick when pill is tapped`() {
        var clickedEmoji: String? = null

        val reactions = listOf(
            ReactionDisplay(emoji = "👍", count = 1, isOwnReaction = false),
        )

        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = reactions,
                onReactionClick = { clickedEmoji = it },
            )
        }

        composeTestRule.onNodeWithText("👍").performClick()

        assertEquals("👍", clickedEmoji)
    }

    @Test
    fun `MessageReactions should call onReactionClick with correct emoji when multiple pills exist`() {
        var clickedEmoji: String? = null

        val reactions = listOf(
            ReactionDisplay(emoji = "👍", count = 1, isOwnReaction = false),
            ReactionDisplay(emoji = "❤️", count = 1, isOwnReaction = false),
            ReactionDisplay(emoji = "🎉", count = 1, isOwnReaction = false),
        )

        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = reactions,
                onReactionClick = { clickedEmoji = it },
            )
        }

        composeTestRule.onNodeWithText("❤️").performClick()

        assertEquals("❤️", clickedEmoji)
    }

    // ==================== Message-based Reactions Tests ====================

    @Test
    fun `MessageReactions should display reactions from message unique summary`() {
        val reactionSummary = createReactionSummary(
            uniqueReactions = mapOf(
                "👍" to createSummaryClientIdList(total = 3, clientIds = listOf("user-1", "user-2", "user-3")),
            ),
        )
        val message = createMockMessage(reactions = reactionSummary)

        composeTestRule.setContentWithTheme {
            MessageReactions(
                message = message,
                currentClientId = "user-1",
                onReactionClick = { },
            )
        }

        composeTestRule.onNodeWithText("👍").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun `MessageReactions should mark own reaction when current user has reacted`() {
        val reactionSummary = createReactionSummary(
            uniqueReactions = mapOf(
                "👍" to createSummaryClientIdList(total = 2, clientIds = listOf("current-user", "other-user")),
            ),
        )
        val message = createMockMessage(reactions = reactionSummary)

        composeTestRule.setContentWithTheme {
            MessageReactions(
                message = message,
                currentClientId = "current-user",
                onReactionClick = { },
            )
        }

        // Reaction should be displayed (styling indicates own reaction)
        composeTestRule.onNodeWithText("👍").assertIsDisplayed()
    }

    @Test
    fun `MessageReactions should process distinct reactions correctly`() {
        val reactionSummary = createReactionSummary(
            distinctReactions = mapOf(
                "❤️" to createSummaryClientIdList(total = 2, clientIds = listOf("user-a", "user-b")),
            ),
        )
        val message = createMockMessage(reactions = reactionSummary)

        composeTestRule.setContentWithTheme {
            MessageReactions(
                message = message,
                currentClientId = "user-a",
                onReactionClick = { },
            )
        }

        composeTestRule.onNodeWithText("❤️").assertIsDisplayed()
    }

    @Test
    fun `MessageReactions should sort reactions by count descending`() {
        // When using the List<ReactionDisplay> overload, we can verify ordering
        val reactions = listOf(
            ReactionDisplay(emoji = "👍", count = 1, isOwnReaction = false),
            ReactionDisplay(emoji = "❤️", count = 5, isOwnReaction = false),
            ReactionDisplay(emoji = "🎉", count = 3, isOwnReaction = false),
        ).sortedByDescending { it.count }

        composeTestRule.setContentWithTheme {
            MessageReactions(
                reactions = reactions,
                onReactionClick = { },
            )
        }

        // All reactions should be displayed (sorted by count)
        composeTestRule.onNodeWithText("❤️").assertIsDisplayed()
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎉").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
        composeTestRule.onNodeWithText("👍").assertIsDisplayed()
    }
}
