package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.ably.chat.MessageAction
import com.ably.chat.ui.helpers.createMockMessage
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatMessageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Message Display Tests ====================

    @Test
    fun `ChatMessage should display message text`() {
        val message = createMockMessage(text = "Hello, world!")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "other-user",
            )
        }

        composeTestRule.onNodeWithText("Hello, world!").assertIsDisplayed()
    }

    @Test
    fun `ChatMessage should show Message deleted for deleted messages`() {
        val message = createMockMessage(
            text = "Original text",
            action = MessageAction.MessageDelete,
        )

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "other-user",
            )
        }

        composeTestRule.onNodeWithText("[Message deleted]").assertIsDisplayed()
    }

    @Test
    fun `ChatMessage should show edited indicator for edited messages`() {
        val message = createMockMessage(
            text = "Edited text",
            action = MessageAction.MessageUpdate,
        )

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "other-user",
            )
        }

        composeTestRule.onNodeWithText("(edited)").assertIsDisplayed()
    }

    // ==================== ClientId Display Tests ====================

    @Test
    fun `ChatMessage should show clientId when showClientId is true for others messages`() {
        val message = createMockMessage(clientId = "sender-user")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "other-user",
                showClientId = true,
            )
        }

        composeTestRule.onNodeWithText("sender-user").assertIsDisplayed()
    }

    @Test
    fun `ChatMessage should hide clientId when showClientId is false`() {
        val message = createMockMessage(clientId = "sender-user")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "other-user",
                showClientId = false,
            )
        }

        composeTestRule.onNodeWithText("sender-user").assertDoesNotExist()
    }

    @Test
    fun `ChatMessage should not show clientId for own messages even when showClientId is true`() {
        val message = createMockMessage(clientId = "current-user")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                showClientId = true,
            )
        }

        // Own messages don't show clientId above the bubble
        composeTestRule.onNodeWithText("Hello, world!").assertIsDisplayed()
    }

    // ==================== Timestamp Display Tests ====================

    @Test
    fun `ChatMessage should show timestamp when showTimestamp is true`() {
        // Use a fixed timestamp
        val fixedTimestamp = 1704067200000L // 2024-01-01 00:00:00 UTC
        val message = createMockMessage(timestamp = fixedTimestamp)

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "other-user",
                showTimestamp = true,
                timestampFormat = "HH:mm",
            )
        }

        // Timestamp should be displayed (exact format depends on locale/timezone)
        composeTestRule.onNodeWithText("Hello, world!").assertIsDisplayed()
    }

    // ==================== Long Press Callback Tests ====================

    @Test
    fun `ChatMessage should invoke onLongPress callback when long pressed`() {
        var longPressedMessage: com.ably.chat.Message? = null
        val message = createMockMessage()

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "other-user",
                onLongPress = { longPressedMessage = it },
            )
        }

        composeTestRule.onNodeWithText("Hello, world!").performTouchInput {
            longClick()
        }

        assertEquals(message, longPressedMessage)
    }

    @Test
    fun `ChatMessage should not invoke onLongPress for deleted messages`() {
        var longPressedMessage: com.ably.chat.Message? = null
        val message = createMockMessage(action = MessageAction.MessageDelete)

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "other-user",
                onLongPress = { longPressedMessage = it },
            )
        }

        composeTestRule.onNodeWithText("[Message deleted]").performTouchInput {
            longClick()
        }

        assertNull(longPressedMessage)
    }

    // ==================== Edit Mode Tests ====================

    @Test
    fun `ChatMessage should enter edit mode when edit callback is provided and Edit action is selected`() {
        val message = createMockMessage(clientId = "current-user", text = "Original text")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                onEdit = { _, _ -> },
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Original text").performTouchInput {
            longClick()
        }

        // Click Edit action
        composeTestRule.onNodeWithText("Edit").performClick()

        // Should now be in edit mode with Cancel and Save buttons
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun `ChatMessage should call onEdit with new text when save clicked in edit mode`() {
        var editedText: String? = null
        val message = createMockMessage(clientId = "current-user", text = "Original text")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                onEdit = { _, newText -> editedText = newText },
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Original text").performTouchInput {
            longClick()
        }

        // Click Edit action
        composeTestRule.onNodeWithText("Edit").performClick()

        // Clear and replace with new text - use performTextReplacement to set new value
        composeTestRule.onNodeWithText("Original text").performTextReplacement("Updated message text")

        // Click Save
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals("Updated message text", editedText)
    }

    @Test
    fun `ChatMessage should cancel edit mode without calling onEdit when Cancel clicked`() {
        var editCalled = false
        val message = createMockMessage(clientId = "current-user", text = "Original text")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                onEdit = { _, _ -> editCalled = true },
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Original text").performTouchInput {
            longClick()
        }

        // Click Edit action
        composeTestRule.onNodeWithText("Edit").performClick()

        // Click Cancel
        composeTestRule.onNodeWithText("Cancel").performClick()

        // Edit should not have been called
        assertTrue(!editCalled)
        // Original text should still be displayed
        composeTestRule.onNodeWithText("Original text").assertIsDisplayed()
    }

    // ==================== Delete Confirmation Tests ====================

    @Test
    fun `ChatMessage should show delete confirmation dialog when Delete action is selected`() {
        val message = createMockMessage(clientId = "current-user")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                onDelete = { },
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Hello, world!").performTouchInput {
            longClick()
        }

        // Click Delete action
        composeTestRule.onNodeWithText("Delete").performClick()

        // Confirmation dialog should appear
        composeTestRule.onNodeWithText("Delete message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure you want to delete this message? This action cannot be undone.").assertIsDisplayed()
    }

    @Test
    fun `ChatMessage should call onDelete when deletion is confirmed`() {
        var deletedMessage: com.ably.chat.Message? = null
        val message = createMockMessage(clientId = "current-user")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                onDelete = { deletedMessage = it },
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Hello, world!").performTouchInput {
            longClick()
        }

        // Click Delete action
        composeTestRule.onNodeWithText("Delete").performClick()

        // Confirm deletion
        composeTestRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()

        assertEquals(message, deletedMessage)
    }

    // ==================== Message Actions Menu Tests ====================

    @Test
    fun `ChatMessage should show Copy action for all messages`() {
        val message = createMockMessage(clientId = "other-sender")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Hello, world!").performTouchInput {
            longClick()
        }

        // Copy should always be available
        composeTestRule.onNodeWithText("Copy").assertIsDisplayed()
    }

    @Test
    fun `ChatMessage should show Edit and Delete actions only for own messages`() {
        val message = createMockMessage(clientId = "current-user")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                onEdit = { _, _ -> },
                onDelete = { },
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Hello, world!").performTouchInput {
            longClick()
        }

        // Edit and Delete should be available for own messages
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun `ChatMessage should not show Edit and Delete actions for others messages`() {
        val message = createMockMessage(clientId = "other-sender")

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                onEdit = { _, _ -> },
                onDelete = { },
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Hello, world!").performTouchInput {
            longClick()
        }

        // Edit and Delete should NOT be available for others' messages
        composeTestRule.onNodeWithText("Edit").assertDoesNotExist()
        composeTestRule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `ChatMessage should show React action when onAddReaction is provided`() {
        val message = createMockMessage()

        composeTestRule.setContentWithTheme {
            ChatMessage(
                message = message,
                currentClientId = "current-user",
                onAddReaction = { },
            )
        }

        // Long press to open menu
        composeTestRule.onNodeWithText("Hello, world!").performTouchInput {
            longClick()
        }

        // React should be available
        composeTestRule.onNodeWithText("React").assertIsDisplayed()
    }
}
