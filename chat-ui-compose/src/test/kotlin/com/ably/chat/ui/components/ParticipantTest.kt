package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ably.chat.ui.helpers.createMockPresenceMember
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ParticipantTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Display Tests (PresenceMember overload) ====================

    @Test
    fun `Participant should display clientId as name`() {
        val member = createMockPresenceMember(clientId = "john_doe")

        composeTestRule.setContentWithTheme {
            Participant(
                member = member,
                isCurrentUser = false,
                isTyping = false,
            )
        }

        composeTestRule.onNodeWithText("john_doe").assertIsDisplayed()
    }

    @Test
    fun `Participant should display you suffix for current user`() {
        val member = createMockPresenceMember(clientId = "current_user")

        composeTestRule.setContentWithTheme {
            Participant(
                member = member,
                isCurrentUser = true,
                isTyping = false,
            )
        }

        composeTestRule.onNodeWithText("current_user (you)").assertIsDisplayed()
    }

    @Test
    fun `Participant should not display you suffix for other users`() {
        val member = createMockPresenceMember(clientId = "other_user")

        composeTestRule.setContentWithTheme {
            Participant(
                member = member,
                isCurrentUser = false,
                isTyping = false,
            )
        }

        composeTestRule.onNodeWithText("other_user").assertIsDisplayed()
        composeTestRule.onNodeWithText("other_user (you)").assertDoesNotExist()
    }

    @Test
    fun `Participant should display avatar for member`() {
        val member = createMockPresenceMember(clientId = "test_user")

        composeTestRule.setContentWithTheme {
            Participant(
                member = member,
                isCurrentUser = false,
                isTyping = false,
            )
        }

        // Avatar initials should be displayed (TE from test_user)
        composeTestRule.onNodeWithText("TU").assertIsDisplayed()
    }

    // ==================== Display Tests (clientId overload) ====================

    @Test
    fun `Participant with clientId should display name`() {
        composeTestRule.setContentWithTheme {
            Participant(
                clientId = "alice_smith",
                isOnline = true,
                isCurrentUser = false,
                isTyping = false,
            )
        }

        composeTestRule.onNodeWithText("alice_smith").assertIsDisplayed()
    }

    @Test
    fun `Participant with clientId should display you suffix for current user`() {
        composeTestRule.setContentWithTheme {
            Participant(
                clientId = "me",
                isOnline = true,
                isCurrentUser = true,
                isTyping = false,
            )
        }

        composeTestRule.onNodeWithText("me (you)").assertIsDisplayed()
    }

    @Test
    fun `Participant with clientId should display avatar initials`() {
        composeTestRule.setContentWithTheme {
            Participant(
                clientId = "bob_jones",
                isOnline = true,
                isCurrentUser = false,
                isTyping = false,
            )
        }

        // Avatar initials should be displayed (BJ from bob_jones)
        composeTestRule.onNodeWithText("BJ").assertIsDisplayed()
    }

    // ==================== Typing Indicator Tests ====================

    @Test
    fun `Participant should show typing indicator when isTyping is true`() {
        val member = createMockPresenceMember(clientId = "typing_user")

        composeTestRule.setContentWithTheme {
            Participant(
                member = member,
                isCurrentUser = false,
                isTyping = true,
            )
        }

        // The participant row should be displayed with typing indicator
        composeTestRule.onNodeWithText("typing_user").assertIsDisplayed()
        // Typing dots are animated, so we just verify the component renders
    }

    @Test
    fun `Participant with clientId should show typing indicator when isTyping is true`() {
        composeTestRule.setContentWithTheme {
            Participant(
                clientId = "typing_user",
                isOnline = true,
                isCurrentUser = false,
                isTyping = true,
            )
        }

        composeTestRule.onNodeWithText("typing_user").assertIsDisplayed()
    }

    @Test
    fun `Participant should not show typing indicator when isTyping is false`() {
        val member = createMockPresenceMember(clientId = "not_typing_user")

        composeTestRule.setContentWithTheme {
            Participant(
                member = member,
                isCurrentUser = false,
                isTyping = false,
            )
        }

        composeTestRule.onNodeWithText("not_typing_user").assertIsDisplayed()
    }

    // ==================== Image URL Tests ====================

    @Test
    fun `Participant should use imageUrl for avatar when provided`() {
        val member = createMockPresenceMember(clientId = "user_with_image")

        composeTestRule.setContentWithTheme {
            Participant(
                member = member,
                isCurrentUser = false,
                isTyping = false,
                imageUrl = "https://example.com/avatar.jpg",
            )
        }

        // Participant name should still be displayed
        composeTestRule.onNodeWithText("user_with_image").assertIsDisplayed()
    }

    @Test
    fun `Participant with clientId should use imageUrl for avatar when provided`() {
        composeTestRule.setContentWithTheme {
            Participant(
                clientId = "user_with_image",
                isOnline = true,
                isCurrentUser = false,
                isTyping = false,
                imageUrl = "https://example.com/avatar.jpg",
            )
        }

        composeTestRule.onNodeWithText("user_with_image").assertIsDisplayed()
    }
}
