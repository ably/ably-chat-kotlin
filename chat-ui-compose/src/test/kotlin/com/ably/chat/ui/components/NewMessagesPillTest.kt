package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NewMessagesPillTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Visibility Tests ====================

    @Test
    fun `NewMessagesPill should display when visible is true`() {
        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = true,
                count = 5,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("5 new messages").assertIsDisplayed()
    }

    @Test
    fun `NewMessagesPill should not display when visible is false`() {
        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = false,
                count = 5,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("5 new messages").assertDoesNotExist()
    }

    // ==================== Count Formatting Tests ====================

    @Test
    fun `NewMessagesPill should display New messages when count is 0`() {
        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = true,
                count = 0,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("New messages").assertIsDisplayed()
    }

    @Test
    fun `NewMessagesPill should display 1 new message for single message`() {
        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = true,
                count = 1,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("1 new message").assertIsDisplayed()
    }

    @Test
    fun `NewMessagesPill should display N new messages for multiple messages`() {
        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = true,
                count = 42,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("42 new messages").assertIsDisplayed()
    }

    @Test
    fun `NewMessagesPill should display 99 plus for count over 99`() {
        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = true,
                count = 150,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("99+ new messages").assertIsDisplayed()
    }

    @Test
    fun `NewMessagesPill should display New messages for negative count`() {
        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = true,
                count = -5,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("New messages").assertIsDisplayed()
    }

    // ==================== Click Callback Tests ====================

    @Test
    fun `NewMessagesPill should call onClick when clicked`() {
        var clicked = false

        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = true,
                count = 5,
                onClick = { clicked = true },
            )
        }

        composeTestRule.onNodeWithText("5 new messages").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `NewMessagesPill should call onClick when clicked with zero count`() {
        var clicked = false

        composeTestRule.setContentWithTheme {
            NewMessagesPill(
                visible = true,
                count = 0,
                onClick = { clicked = true },
            )
        }

        composeTestRule.onNodeWithText("New messages").performClick()

        assertTrue(clicked)
    }
}
