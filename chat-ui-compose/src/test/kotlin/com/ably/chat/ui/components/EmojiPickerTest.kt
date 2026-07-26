package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmojiPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Grid Display Tests ====================

    @Test
    fun `EmojiPicker should display default emojis`() {
        composeTestRule.setContentWithTheme {
            EmojiPicker(onEmojiSelected = { })
        }

        // Check for some default emojis (thumbs up, heart, etc.)
        composeTestRule.onNodeWithText("\uD83D\uDC4D").assertIsDisplayed() // 👍
        composeTestRule.onNodeWithText("\u2764\uFE0F").assertIsDisplayed() // ❤️
        composeTestRule.onNodeWithText("\uD83D\uDE02").assertIsDisplayed() // 😂
    }

    @Test
    fun `EmojiPicker should display custom emojis list`() {
        val customEmojis = listOf("🎉", "🚀", "💯", "⭐", "🔥")

        composeTestRule.setContentWithTheme {
            EmojiPicker(
                emojis = customEmojis,
                onEmojiSelected = { },
            )
        }

        composeTestRule.onNodeWithText("🎉").assertIsDisplayed()
        composeTestRule.onNodeWithText("🚀").assertIsDisplayed()
        composeTestRule.onNodeWithText("💯").assertIsDisplayed()
        composeTestRule.onNodeWithText("⭐").assertIsDisplayed()
        composeTestRule.onNodeWithText("🔥").assertIsDisplayed()
    }

    @Test
    fun `EmojiPicker should handle single emoji in list`() {
        val singleEmoji = listOf("🎉")

        composeTestRule.setContentWithTheme {
            EmojiPicker(
                emojis = singleEmoji,
                onEmojiSelected = { },
            )
        }

        composeTestRule.onNodeWithText("🎉").assertIsDisplayed()
    }

    // ==================== Selection Callback Tests ====================

    @Test
    fun `EmojiPicker should call onEmojiSelected when emoji clicked`() {
        var selectedEmoji: String? = null

        composeTestRule.setContentWithTheme {
            EmojiPicker(onEmojiSelected = { selectedEmoji = it })
        }

        composeTestRule.onNodeWithText("\uD83D\uDC4D").performClick() // 👍

        assertEquals("\uD83D\uDC4D", selectedEmoji)
    }

    @Test
    fun `EmojiPicker should call onEmojiSelected with correct emoji from custom list`() {
        var selectedEmoji: String? = null
        val customEmojis = listOf("🎉", "🚀", "💯")

        composeTestRule.setContentWithTheme {
            EmojiPicker(
                emojis = customEmojis,
                onEmojiSelected = { selectedEmoji = it },
            )
        }

        composeTestRule.onNodeWithText("🚀").performClick()

        assertEquals("🚀", selectedEmoji)
    }

    // ==================== Dropdown Tests ====================

    @Test
    fun `EmojiPickerDropdown should not display when expanded is false`() {
        composeTestRule.setContentWithTheme {
            EmojiPickerDropdown(
                expanded = false,
                onDismiss = { },
                onEmojiSelected = { },
            )
        }

        // Emojis should not be visible
        composeTestRule.onNodeWithText("\uD83D\uDC4D").assertDoesNotExist()
    }

    @Test
    fun `EmojiPickerDropdown should display emojis when expanded is true`() {
        composeTestRule.setContentWithTheme {
            EmojiPickerDropdown(
                expanded = true,
                onDismiss = { },
                onEmojiSelected = { },
            )
        }

        composeTestRule.onNodeWithText("\uD83D\uDC4D").assertIsDisplayed()
    }

    @Test
    fun `EmojiPickerDropdown should call onEmojiSelected when emoji clicked`() {
        var selectedEmoji: String? = null

        composeTestRule.setContentWithTheme {
            EmojiPickerDropdown(
                expanded = true,
                onDismiss = { },
                onEmojiSelected = { selectedEmoji = it },
            )
        }

        composeTestRule.onNodeWithText("\uD83D\uDE02").performClick() // 😂

        assertEquals("\uD83D\uDE02", selectedEmoji)
    }

    @Test
    fun `EmojiPickerDropdown should call onDismiss after emoji selection`() {
        var dismissCalled = false

        composeTestRule.setContentWithTheme {
            EmojiPickerDropdown(
                expanded = true,
                onDismiss = { dismissCalled = true },
                onEmojiSelected = { },
            )
        }

        composeTestRule.onNodeWithText("\uD83D\uDC4D").performClick()

        assertEquals(true, dismissCalled)
    }

    @Test
    fun `EmojiPickerDropdown should use custom emojis list`() {
        val customEmojis = listOf("🌟", "🎯", "🏆")

        composeTestRule.setContentWithTheme {
            EmojiPickerDropdown(
                expanded = true,
                onDismiss = { },
                onEmojiSelected = { },
                emojis = customEmojis,
            )
        }

        composeTestRule.onNodeWithText("🌟").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎯").assertIsDisplayed()
        composeTestRule.onNodeWithText("🏆").assertIsDisplayed()
    }

    // ==================== DefaultReactionEmojis Tests ====================

    @Test
    fun `DefaultReactionEmojis should contain expected emojis`() {
        assertEquals(10, DefaultReactionEmojis.size)
        assertEquals("\uD83D\uDC4D", DefaultReactionEmojis[0]) // 👍
        assertEquals("\u2764\uFE0F", DefaultReactionEmojis[1]) // ❤️
        assertEquals("\uD83D\uDE02", DefaultReactionEmojis[2]) // 😂
    }
}
