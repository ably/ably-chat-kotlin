package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShimmerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== ChatMessageSkeleton Tests ====================

    @Test
    fun `ChatMessageSkeleton should render for own message`() {
        composeTestRule.setContentWithTheme {
            ChatMessageSkeleton(isOwnMessage = true)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `ChatMessageSkeleton should render for others message`() {
        composeTestRule.setContentWithTheme {
            ChatMessageSkeleton(isOwnMessage = false)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `ChatMessageSkeleton should render with avatar`() {
        composeTestRule.setContentWithTheme {
            ChatMessageSkeleton(
                isOwnMessage = false,
                showAvatar = true,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `ChatMessageSkeleton should render without avatar`() {
        composeTestRule.setContentWithTheme {
            ChatMessageSkeleton(
                isOwnMessage = false,
                showAvatar = false,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    // ==================== ChatMessageListSkeleton Tests ====================

    @Test
    fun `ChatMessageListSkeleton should render with default item count`() {
        composeTestRule.setContentWithTheme {
            ChatMessageListSkeleton()
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `ChatMessageListSkeleton should render with custom item count`() {
        composeTestRule.setContentWithTheme {
            ChatMessageListSkeleton(itemCount = 3)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `ChatMessageListSkeleton should render with avatars enabled`() {
        composeTestRule.setContentWithTheme {
            ChatMessageListSkeleton(showAvatars = true)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `ChatMessageListSkeleton should render with avatars disabled`() {
        composeTestRule.setContentWithTheme {
            ChatMessageListSkeleton(showAvatars = false)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    // ==================== ParticipantSkeleton Tests ====================

    @Test
    fun `ParticipantSkeleton should render`() {
        composeTestRule.setContentWithTheme {
            ParticipantSkeleton()
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    // ==================== ParticipantListSkeleton Tests ====================

    @Test
    fun `ParticipantListSkeleton should render with default item count`() {
        composeTestRule.setContentWithTheme {
            ParticipantListSkeleton()
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `ParticipantListSkeleton should render with custom item count`() {
        composeTestRule.setContentWithTheme {
            ParticipantListSkeleton(itemCount = 10)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    // ==================== RoomItemSkeleton Tests ====================

    @Test
    fun `RoomItemSkeleton should render`() {
        composeTestRule.setContentWithTheme {
            RoomItemSkeleton()
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    // ==================== RoomListSkeleton Tests ====================

    @Test
    fun `RoomListSkeleton should render with default item count`() {
        composeTestRule.setContentWithTheme {
            RoomListSkeleton()
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `RoomListSkeleton should render with custom item count`() {
        composeTestRule.setContentWithTheme {
            RoomListSkeleton(itemCount = 8)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `RoomListSkeleton should render with single item`() {
        composeTestRule.setContentWithTheme {
            RoomListSkeleton(itemCount = 1)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }
}
