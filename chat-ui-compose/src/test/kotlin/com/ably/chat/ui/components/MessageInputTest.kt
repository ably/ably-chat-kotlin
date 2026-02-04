package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MessageInputTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Placeholder Tests ====================

    @Test
    fun `MessageInput should display placeholder text when empty`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        composeTestRule.onNodeWithText("Type a message...").assertIsDisplayed()
    }

    @Test
    fun `MessageInput should display custom placeholder text`() {
        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                placeholder = "Write something...",
            )
        }

        composeTestRule.onNodeWithText("Write something...").assertIsDisplayed()
    }

    // ==================== Text Input Tests ====================

    @Test
    fun `MessageInput should update text field on input`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        composeTestRule.onNodeWithText("Type a message...").performTextInput("Hello")
        composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test
    fun `MessageInput should call onTextChanged when text changes`() {
        var changedText: String? = null

        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                onTextChanged = { changedText = it },
            )
        }

        composeTestRule.onNodeWithText("Type a message...").performTextInput("Test message")

        assertEquals("Test message", changedText)
    }

    // ==================== Send Button Tests ====================

    @Test
    fun `MessageInput should disable send button when text is empty`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        composeTestRule.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun `MessageInput should disable send button when text is only whitespace`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        composeTestRule.onNodeWithText("Type a message...").performTextInput("   ")
        composeTestRule.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun `MessageInput should enable send button when text is not empty`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        composeTestRule.onNodeWithText("Type a message...").performTextInput("Hello")
        composeTestRule.onNodeWithText("Send").assertIsEnabled()
    }

    @Test
    fun `MessageInput should call onSend with trimmed text when send clicked`() {
        var sentText: String? = null

        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { sentText = it })
        }

        composeTestRule.onNodeWithText("Type a message...").performTextInput("  Hello World  ")
        composeTestRule.onNodeWithText("Send").performClick()

        assertEquals("Hello World", sentText)
    }

    @Test
    fun `MessageInput should clear text after sending`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        composeTestRule.onNodeWithText("Type a message...").performTextInput("Hello")
        composeTestRule.onNodeWithText("Send").performClick()

        // Text should be cleared and placeholder should be visible again
        composeTestRule.onNodeWithText("Type a message...").assertIsDisplayed()
    }

    @Test
    fun `MessageInput should call onTextChanged with empty string after sending`() {
        val textChanges = mutableListOf<String>()

        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                onTextChanged = { textChanges.add(it) },
            )
        }

        composeTestRule.onNodeWithText("Type a message...").performTextInput("Hi")
        composeTestRule.onNodeWithText("Send").performClick()

        assertTrue(textChanges.contains(""))
    }

    @Test
    fun `MessageInput should display custom send button text`() {
        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                sendButtonText = "Submit",
            )
        }

        composeTestRule.onNodeWithText("Submit").assertIsDisplayed()
    }

    // ==================== Enabled/Disabled State Tests ====================

    @Test
    fun `MessageInput should disable input when enabled is false`() {
        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                enabled = false,
            )
        }

        // Send button should be disabled when component is disabled
        composeTestRule.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun `MessageInput should disable send button when enabled is false even with text`() {
        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                enabled = false,
            )
        }

        // Even if we could input text, send should still be disabled
        composeTestRule.onNodeWithText("Send").assertIsNotEnabled()
    }

    // ==================== Emoji Picker Tests ====================

    @Test
    fun `MessageInput should show emoji picker button by default`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        composeTestRule.onNodeWithContentDescription("Emoji picker").assertIsDisplayed()
    }

    @Test
    fun `MessageInput should hide emoji picker button when showEmojiPicker is false`() {
        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                showEmojiPicker = false,
            )
        }

        composeTestRule.onNodeWithContentDescription("Emoji picker").assertDoesNotExist()
    }

    @Test
    fun `MessageInput should open emoji picker dropdown on button click`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        composeTestRule.onNodeWithContentDescription("Emoji picker").performClick()

        // Emoji picker dropdown should be visible with default emojis
        // Check for one of the default emojis (thumbs up)
        composeTestRule.onNodeWithText("\uD83D\uDC4D").assertIsDisplayed()
    }

    @Test
    fun `MessageInput should append emoji to text when selected from picker`() {
        composeTestRule.setContentWithTheme {
            MessageInput(onSend = { })
        }

        // First type some text
        composeTestRule.onNodeWithText("Type a message...").performTextInput("Hello ")

        // Open emoji picker
        composeTestRule.onNodeWithContentDescription("Emoji picker").performClick()

        // Select an emoji (thumbs up)
        composeTestRule.onNodeWithText("\uD83D\uDC4D").performClick()

        // Text should now contain the emoji
        composeTestRule.onNodeWithText("Hello \uD83D\uDC4D").assertIsDisplayed()
    }

    @Test
    fun `MessageInput should call onTextChanged when emoji is selected`() {
        var lastText: String? = null

        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                onTextChanged = { lastText = it },
            )
        }

        // Open emoji picker
        composeTestRule.onNodeWithContentDescription("Emoji picker").performClick()

        // Select an emoji
        composeTestRule.onNodeWithText("\uD83D\uDC4D").performClick()

        assertTrue(lastText?.contains("\uD83D\uDC4D") == true)
    }

    @Test
    fun `MessageInput should use custom emojis list when provided`() {
        val customEmojis = listOf("🎉", "🚀", "💯")

        composeTestRule.setContentWithTheme {
            MessageInput(
                onSend = { },
                emojis = customEmojis,
            )
        }

        // Open emoji picker
        composeTestRule.onNodeWithContentDescription("Emoji picker").performClick()

        // Custom emojis should be visible
        composeTestRule.onNodeWithText("🎉").assertIsDisplayed()
        composeTestRule.onNodeWithText("🚀").assertIsDisplayed()
        composeTestRule.onNodeWithText("💯").assertIsDisplayed()
    }
}
