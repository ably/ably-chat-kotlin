package com.ably.chat.ui.components

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
class OccupancyBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Basic Display Tests ====================

    @Test
    fun `OccupancyBadge should display count`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = 5)
        }

        composeTestRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun `OccupancyBadge should display zero count`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = 0)
        }

        composeTestRule.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun `OccupancyBadge should display single digit count`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = 7)
        }

        composeTestRule.onNodeWithText("7").assertIsDisplayed()
    }

    @Test
    fun `OccupancyBadge should display double digit count`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = 42)
        }

        composeTestRule.onNodeWithText("42").assertIsDisplayed()
    }

    // ==================== Large Count Tests ====================

    @Test
    fun `OccupancyBadge should display 99 plus for count over 99`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = 100)
        }

        composeTestRule.onNodeWithText("99+").assertIsDisplayed()
    }

    @Test
    fun `OccupancyBadge should display 99 plus for very large count`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = 1000)
        }

        composeTestRule.onNodeWithText("99+").assertIsDisplayed()
    }

    @Test
    fun `OccupancyBadge should display 99 for count of exactly 99`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = 99)
        }

        composeTestRule.onNodeWithText("99").assertIsDisplayed()
    }

    // ==================== Edge Case Tests ====================

    @Test
    fun `OccupancyBadge should display 0 for negative count`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = -5)
        }

        composeTestRule.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun `OccupancyBadge should display 1 for count of one`() {
        composeTestRule.setContentWithTheme {
            OccupancyBadge(count = 1)
        }

        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }
}
