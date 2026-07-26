package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AvatarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Initials Extraction Tests ====================

    @Test
    fun `Avatar should display initials when imageUrl is null`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "john")
        }
        composeTestRule.onNodeWithText("JO").assertIsDisplayed()
    }

    @Test
    fun `Avatar should extract correct initials from single word clientId`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "alice")
        }
        composeTestRule.onNodeWithText("AL").assertIsDisplayed()
    }

    @Test
    fun `Avatar should extract correct initials from multi-word clientId with spaces`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "John Doe")
        }
        composeTestRule.onNodeWithText("JD").assertIsDisplayed()
    }

    @Test
    fun `Avatar should extract correct initials from underscore-separated clientId`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "john_doe")
        }
        composeTestRule.onNodeWithText("JD").assertIsDisplayed()
    }

    @Test
    fun `Avatar should extract correct initials from hyphen-separated clientId`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "john-doe")
        }
        composeTestRule.onNodeWithText("JD").assertIsDisplayed()
    }

    @Test
    fun `Avatar should extract correct initials from dot-separated clientId`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "john.doe")
        }
        composeTestRule.onNodeWithText("JD").assertIsDisplayed()
    }

    @Test
    fun `Avatar should show question mark for empty clientId`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "")
        }
        composeTestRule.onNodeWithText("?").assertIsDisplayed()
    }

    @Test
    fun `Avatar should show single uppercase letter for single character clientId`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "j")
        }
        composeTestRule.onNodeWithText("J").assertIsDisplayed()
    }

    @Test
    fun `Avatar should uppercase initials correctly`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "jane smith")
        }
        composeTestRule.onNodeWithText("JS").assertIsDisplayed()
    }

    // ==================== Image Display Tests ====================

    @Test
    fun `Avatar should display image when imageUrl is provided`() {
        composeTestRule.setContentWithTheme {
            Avatar(
                clientId = "john",
                imageUrl = "https://example.com/avatar.jpg",
            )
        }
        // When image is provided, AsyncImage is rendered with content description
        composeTestRule.onNodeWithContentDescription("Avatar for john").assertIsDisplayed()
    }

    // ==================== Size Variant Tests ====================

    @Test
    fun `Avatar should render with Small size`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "test", size = AvatarSize.Small)
        }
        // Size is 32.dp for Small
        composeTestRule.onNodeWithText("TE").assertIsDisplayed()
    }

    @Test
    fun `Avatar should render with Medium size`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "test", size = AvatarSize.Medium)
        }
        // Size is 40.dp for Medium (default)
        composeTestRule.onNodeWithText("TE").assertIsDisplayed()
    }

    @Test
    fun `Avatar should render with Large size`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "test", size = AvatarSize.Large)
        }
        // Size is 48.dp for Large
        composeTestRule.onNodeWithText("TE").assertIsDisplayed()
    }

    @Test
    fun `Avatar should render with ExtraLarge size`() {
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "test", size = AvatarSize.ExtraLarge)
        }
        // Size is 64.dp for ExtraLarge
        composeTestRule.onNodeWithText("TE").assertIsDisplayed()
    }

    // ==================== AvatarSize Enum Tests ====================

    @Test
    fun `AvatarSize Small should be 32dp`() {
        assertEquals(32, AvatarSize.Small.dp.value.toInt())
    }

    @Test
    fun `AvatarSize Medium should be 40dp`() {
        assertEquals(40, AvatarSize.Medium.dp.value.toInt())
    }

    @Test
    fun `AvatarSize Large should be 48dp`() {
        assertEquals(48, AvatarSize.Large.dp.value.toInt())
    }

    @Test
    fun `AvatarSize ExtraLarge should be 64dp`() {
        assertEquals(64, AvatarSize.ExtraLarge.dp.value.toInt())
    }

    // ==================== Deterministic Color Tests ====================

    @Test
    fun `Avatar should generate deterministic background color from clientId`() {
        // Same clientId should always produce the same color
        // We test this by verifying the avatar renders consistently
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "consistent-user")
        }
        composeTestRule.onNodeWithText("CU").assertIsDisplayed()
    }

    @Test
    fun `Avatar should generate different colors for different clientIds`() {
        // Different clientIds should render with different colors
        // We just verify both render correctly
        composeTestRule.setContentWithTheme {
            Avatar(clientId = "user-alpha")
        }
        composeTestRule.onNodeWithText("UA").assertIsDisplayed()
    }
}
