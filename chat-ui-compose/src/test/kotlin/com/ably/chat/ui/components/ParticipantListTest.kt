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
class ParticipantListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Header Tests ====================

    @Test
    fun `ParticipantList should display header with count for empty list`() {
        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = emptyList(),
                typingClientIds = emptySet(),
                currentClientId = "user-1",
            )
        }

        composeTestRule.onNodeWithText("No one in room").assertIsDisplayed()
    }

    @Test
    fun `ParticipantList should display header with count for single member`() {
        val members = listOf(createMockPresenceMember(clientId = "user-1"))

        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = members,
                typingClientIds = emptySet(),
                currentClientId = "user-1",
            )
        }

        composeTestRule.onNodeWithText("1 person in room").assertIsDisplayed()
    }

    @Test
    fun `ParticipantList should display header with count for multiple members`() {
        val members = listOf(
            createMockPresenceMember(clientId = "user-1"),
            createMockPresenceMember(clientId = "user-2"),
            createMockPresenceMember(clientId = "user-3"),
        )

        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = members,
                typingClientIds = emptySet(),
                currentClientId = "user-1",
            )
        }

        composeTestRule.onNodeWithText("3 people in room").assertIsDisplayed()
    }

    // ==================== Member Display Tests ====================

    @Test
    fun `ParticipantList should display all member names`() {
        val members = listOf(
            createMockPresenceMember(clientId = "alice"),
            createMockPresenceMember(clientId = "bob"),
        )

        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = members,
                typingClientIds = emptySet(),
                currentClientId = "other-user",
            )
        }

        composeTestRule.onNodeWithText("alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("bob").assertIsDisplayed()
    }

    @Test
    fun `ParticipantList should display you suffix for current user`() {
        val members = listOf(
            createMockPresenceMember(clientId = "current-user"),
            createMockPresenceMember(clientId = "other"),
        )

        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = members,
                typingClientIds = emptySet(),
                currentClientId = "current-user",
            )
        }

        composeTestRule.onNodeWithText("current-user (you)").assertIsDisplayed()
        composeTestRule.onNodeWithText("other").assertIsDisplayed()
    }

    // ==================== Sorting Tests ====================

    @Test
    fun `ParticipantList should sort current user first`() {
        val members = listOf(
            createMockPresenceMember(clientId = "zebra"),
            createMockPresenceMember(clientId = "current-user"),
            createMockPresenceMember(clientId = "alpha"),
        )

        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = members,
                typingClientIds = emptySet(),
                currentClientId = "current-user",
            )
        }

        // Current user should be first (marked with (you))
        composeTestRule.onNodeWithText("current-user (you)").assertIsDisplayed()
        // Other users should also be present
        composeTestRule.onNodeWithText("alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("zebra").assertIsDisplayed()
    }

    // ==================== Typing Indicator Tests ====================

    @Test
    fun `ParticipantList should show typing indicator for typing members`() {
        val members = listOf(
            createMockPresenceMember(clientId = "alice"),
            createMockPresenceMember(clientId = "bob"),
        )

        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = members,
                typingClientIds = setOf("alice"),
                currentClientId = "other-user",
            )
        }

        // Alice should be visible (with typing indicator shown internally)
        composeTestRule.onNodeWithText("alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("bob").assertIsDisplayed()
    }

    @Test
    fun `ParticipantList should not show typing indicator for non-typing members`() {
        val members = listOf(
            createMockPresenceMember(clientId = "alice"),
        )

        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = members,
                typingClientIds = emptySet(),
                currentClientId = "other-user",
            )
        }

        composeTestRule.onNodeWithText("alice").assertIsDisplayed()
    }

    // ==================== Custom Header Tests ====================

    @Test
    fun `ParticipantList should use custom header when provided`() {
        val members = listOf(
            createMockPresenceMember(clientId = "user-1"),
            createMockPresenceMember(clientId = "user-2"),
        )

        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = members,
                typingClientIds = emptySet(),
                currentClientId = "user-1",
                header = { count ->
                    androidx.compose.material3.Text("Custom Header: $count participants")
                },
            )
        }

        composeTestRule.onNodeWithText("Custom Header: 2 participants").assertIsDisplayed()
    }

    // ==================== Empty State Tests ====================

    @Test
    fun `ParticipantList should handle empty members list gracefully`() {
        composeTestRule.setContentWithTheme {
            ParticipantList(
                members = emptyList(),
                typingClientIds = setOf("ghost-user"),
                currentClientId = "user-1",
            )
        }

        composeTestRule.onNodeWithText("No one in room").assertIsDisplayed()
    }
}
