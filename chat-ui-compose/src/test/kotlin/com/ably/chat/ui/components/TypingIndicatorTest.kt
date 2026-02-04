package com.ably.chat.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TypingIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Empty State Tests ====================

    @Test
    fun `TypingIndicator should not display when no one is typing`() {
        val typingState = mutableStateOf(emptySet<String>())

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = "user-1",
            )
        }

        composeTestRule.onNodeWithText("is typing...").assertDoesNotExist()
    }

    // ==================== Single User Typing Tests ====================

    @Test
    fun `TypingIndicator should display name is typing for single user`() {
        val typingState = mutableStateOf(setOf("alice"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = null,
            )
        }

        composeTestRule.onNodeWithText("alice is typing...").assertIsDisplayed()
    }

    @Test
    fun `TypingIndicator should display You is typing for current user`() {
        val typingState = mutableStateOf(setOf("current-user"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = "current-user",
            )
        }

        composeTestRule.onNodeWithText("You is typing...").assertIsDisplayed()
    }

    // ==================== Two Users Typing Tests ====================

    @Test
    fun `TypingIndicator should display names with and for two users`() {
        val typingState = mutableStateOf(setOf("alice", "bob"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = null,
            )
        }

        // Order may vary, but should contain "and" and "are typing..."
        composeTestRule.onNodeWithText("alice and bob are typing...", substring = false, ignoreCase = false)
            .assertIsDisplayed()
    }

    @Test
    fun `TypingIndicator should show You and other for current user plus one`() {
        val typingState = mutableStateOf(setOf("current-user", "alice"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = "current-user",
            )
        }

        composeTestRule.onNodeWithText("You and alice are typing...", substring = false, ignoreCase = false)
            .assertIsDisplayed()
    }

    // ==================== Three Users Typing Tests ====================

    @Test
    fun `TypingIndicator should display three names with commas and and`() {
        val typingState = mutableStateOf(setOf("alice", "bob", "charlie"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = null,
            )
        }

        composeTestRule.onNodeWithText("alice, bob, and charlie are typing...", substring = false, ignoreCase = false)
            .assertIsDisplayed()
    }

    // ==================== Four+ Users Typing Tests ====================

    @Test
    fun `TypingIndicator should display N others for four or more users`() {
        val typingState = mutableStateOf(setOf("alice", "bob", "charlie", "dave"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = null,
            )
        }

        // Should show first two names + "and N others"
        composeTestRule.onNodeWithText("alice, bob, and 2 others are typing...", substring = false, ignoreCase = false)
            .assertIsDisplayed()
    }

    @Test
    fun `TypingIndicator should display correct count for many users`() {
        val typingState = mutableStateOf(setOf("a", "b", "c", "d", "e", "f"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = null,
            )
        }

        // Should show first two + "and 4 others"
        composeTestRule.onNodeWithText("a, b, and 4 others are typing...", substring = false, ignoreCase = false)
            .assertIsDisplayed()
    }

    // ==================== Include Current User Tests ====================

    @Test
    fun `TypingIndicator should exclude current user when currentClientId is null`() {
        val typingState = mutableStateOf(setOf("alice"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = null,
            )
        }

        // Should just show alice, not "You"
        composeTestRule.onNodeWithText("alice is typing...").assertIsDisplayed()
    }

    @Test
    fun `TypingIndicator should show You when current user is typing and included`() {
        val typingState = mutableStateOf(setOf("me"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = "me",
            )
        }

        composeTestRule.onNodeWithText("You is typing...").assertIsDisplayed()
    }

    // ==================== State Update Tests ====================

    @Test
    fun `TypingIndicator should update when typing state changes`() {
        val typingState = mutableStateOf(setOf("alice"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = null,
            )
        }

        composeTestRule.onNodeWithText("alice is typing...").assertIsDisplayed()

        // Update state
        typingState.value = setOf("bob")

        composeTestRule.onNodeWithText("bob is typing...").assertIsDisplayed()
    }

    @Test
    fun `TypingIndicator should hide when typing state becomes empty`() {
        val typingState = mutableStateOf(setOf("alice"))

        composeTestRule.setContentWithTheme {
            TypingIndicator(
                typingState = typingState,
                currentClientId = null,
            )
        }

        composeTestRule.onNodeWithText("alice is typing...").assertIsDisplayed()

        // Clear typing state
        typingState.value = emptySet()

        composeTestRule.onNodeWithText("is typing...").assertDoesNotExist()
    }
}
