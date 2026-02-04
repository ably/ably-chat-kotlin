package com.ably.chat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PresenceIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Online/Offline State Tests ====================

    @Test
    fun `PresenceIndicator should render when online`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(isOnline = true)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `PresenceIndicator should render when offline`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(isOnline = false)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    // ==================== Size Tests ====================

    @Test
    fun `PresenceIndicator should render with default size`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(isOnline = true)
        }

        // Default size is 12.dp
        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `PresenceIndicator should render with custom size`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(
                isOnline = true,
                size = 20.dp,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `PresenceIndicator should render with small size`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(
                isOnline = true,
                size = 8.dp,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    // ==================== Border Tests ====================

    @Test
    fun `PresenceIndicator should render with default border color`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(isOnline = true)
        }

        // Default border color is Color.White
        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `PresenceIndicator should render with custom border color`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(
                isOnline = true,
                borderColor = Color.Black,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `PresenceIndicator should render with custom border width`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(
                isOnline = true,
                borderWidth = 4.dp,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    // ==================== Combination Tests ====================

    @Test
    fun `PresenceIndicator should render with all custom parameters`() {
        composeTestRule.setContentWithTheme {
            PresenceIndicator(
                isOnline = false,
                size = 16.dp,
                borderColor = Color.Gray,
                borderWidth = 3.dp,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }
}
