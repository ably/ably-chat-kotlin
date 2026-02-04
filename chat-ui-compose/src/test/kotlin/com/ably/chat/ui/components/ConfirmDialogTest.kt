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
class ConfirmDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Display Tests ====================

    @Test
    fun `ConfirmDialog should display title`() {
        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "Test message",
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Test Title").assertIsDisplayed()
    }

    @Test
    fun `ConfirmDialog should display message`() {
        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "This is the dialog message",
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("This is the dialog message").assertIsDisplayed()
    }

    @Test
    fun `ConfirmDialog should display default confirm text`() {
        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "Test message",
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun `ConfirmDialog should display custom confirm text`() {
        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "Test message",
                confirmText = "Confirm Action",
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Confirm Action").assertIsDisplayed()
    }

    @Test
    fun `ConfirmDialog should display default dismiss text`() {
        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "Test message",
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `ConfirmDialog should display custom dismiss text`() {
        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "Test message",
                dismissText = "Go Back",
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Go Back").assertIsDisplayed()
    }

    // ==================== Callback Tests ====================

    @Test
    fun `ConfirmDialog should call onConfirm when confirm button clicked`() {
        var confirmCalled = false

        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "Test message",
                onConfirm = { confirmCalled = true },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Delete").performClick()

        assertTrue(confirmCalled)
    }

    @Test
    fun `ConfirmDialog should call onDismiss when dismiss button clicked`() {
        var dismissCalled = false

        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "Test message",
                onConfirm = { },
                onDismiss = { dismissCalled = true },
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue(dismissCalled)
    }

    @Test
    fun `ConfirmDialog should call onDismiss after onConfirm when confirm button clicked`() {
        var confirmCalled = false
        var dismissCalled = false

        composeTestRule.setContentWithTheme {
            ConfirmDialog(
                title = "Test Title",
                message = "Test message",
                onConfirm = { confirmCalled = true },
                onDismiss = { dismissCalled = true },
            )
        }

        composeTestRule.onNodeWithText("Delete").performClick()

        assertTrue(confirmCalled)
        assertTrue(dismissCalled)
    }

    // ==================== DeleteConfirmDialog Tests ====================

    @Test
    fun `DeleteConfirmDialog should display default title`() {
        composeTestRule.setContentWithTheme {
            DeleteConfirmDialog(
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Delete message").assertIsDisplayed()
    }

    @Test
    fun `DeleteConfirmDialog should display default message`() {
        composeTestRule.setContentWithTheme {
            DeleteConfirmDialog(
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Are you sure you want to delete this message? This action cannot be undone.").assertIsDisplayed()
    }

    @Test
    fun `DeleteConfirmDialog should display custom title`() {
        composeTestRule.setContentWithTheme {
            DeleteConfirmDialog(
                title = "Custom Delete Title",
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Custom Delete Title").assertIsDisplayed()
    }

    @Test
    fun `DeleteConfirmDialog should display custom message`() {
        composeTestRule.setContentWithTheme {
            DeleteConfirmDialog(
                message = "Custom delete warning message",
                onConfirm = { },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Custom delete warning message").assertIsDisplayed()
    }

    @Test
    fun `DeleteConfirmDialog should call onConfirm when Delete button clicked`() {
        var confirmCalled = false

        composeTestRule.setContentWithTheme {
            DeleteConfirmDialog(
                onConfirm = { confirmCalled = true },
                onDismiss = { },
            )
        }

        composeTestRule.onNodeWithText("Delete").performClick()

        assertTrue(confirmCalled)
    }
}
