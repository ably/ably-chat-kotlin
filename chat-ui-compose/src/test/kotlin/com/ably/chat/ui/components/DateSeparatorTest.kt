package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DateSeparatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Today/Yesterday/Date Display Tests ====================

    @Test
    fun `DateSeparator should display Today for current day timestamps`() {
        val today = System.currentTimeMillis()

        composeTestRule.setContentWithTheme {
            DateSeparator(timestamp = today)
        }

        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun `DateSeparator should display Yesterday for previous day timestamps`() {
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

        composeTestRule.setContentWithTheme {
            DateSeparator(timestamp = yesterday)
        }

        composeTestRule.onNodeWithText("Yesterday").assertIsDisplayed()
    }

    @Test
    fun `DateSeparator should display formatted date for older timestamps`() {
        // Use a date far in the past to ensure it's not Today or Yesterday
        val calendar = Calendar.getInstance().apply {
            set(2023, Calendar.JANUARY, 15) // January 15, 2023
        }

        composeTestRule.setContentWithTheme {
            DateSeparator(
                timestamp = calendar.timeInMillis,
                dateFormat = "MMMM d, yyyy",
            )
        }

        composeTestRule.onNodeWithText("January 15, 2023").assertIsDisplayed()
    }

    @Test
    fun `DateSeparator should use custom date format`() {
        val calendar = Calendar.getInstance().apply {
            set(2023, Calendar.MARCH, 20)
        }

        composeTestRule.setContentWithTheme {
            DateSeparator(
                timestamp = calendar.timeInMillis,
                dateFormat = "MM/dd/yyyy",
            )
        }

        composeTestRule.onNodeWithText("03/20/2023").assertIsDisplayed()
    }

    @Test
    fun `DateSeparator should handle different date format patterns`() {
        val calendar = Calendar.getInstance().apply {
            set(2023, Calendar.DECEMBER, 25)
        }

        composeTestRule.setContentWithTheme {
            DateSeparator(
                timestamp = calendar.timeInMillis,
                dateFormat = "d MMM yyyy",
            )
        }

        composeTestRule.onNodeWithText("25 Dec 2023").assertIsDisplayed()
    }

    // ==================== shouldShowDateSeparator Function Tests ====================

    @Test
    fun `shouldShowDateSeparator should return true when previousTimestamp is null`() {
        val currentTimestamp = System.currentTimeMillis()

        assertTrue(shouldShowDateSeparator(currentTimestamp, null))
    }

    @Test
    fun `shouldShowDateSeparator should return false for same day timestamps`() {
        val calendar = Calendar.getInstance()
        val timestamp1 = calendar.timeInMillis

        calendar.add(Calendar.HOUR, 2) // 2 hours later same day
        val timestamp2 = calendar.timeInMillis

        assertFalse(shouldShowDateSeparator(timestamp2, timestamp1))
    }

    @Test
    fun `shouldShowDateSeparator should return true for different day timestamps`() {
        val calendar = Calendar.getInstance()
        val timestamp1 = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1) // Next day
        val timestamp2 = calendar.timeInMillis

        assertTrue(shouldShowDateSeparator(timestamp2, timestamp1))
    }

    @Test
    fun `shouldShowDateSeparator should return true for different year timestamps`() {
        val calendar2023 = Calendar.getInstance().apply {
            set(2023, Calendar.DECEMBER, 31)
        }
        val calendar2024 = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 1)
        }

        assertTrue(shouldShowDateSeparator(calendar2024.timeInMillis, calendar2023.timeInMillis))
    }

    @Test
    fun `shouldShowDateSeparator should return false for messages at midnight boundary same day`() {
        // Start of a day at 00:00:01
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 1)
        }
        val timestamp1 = calendar.timeInMillis

        // Later same day at 23:59:59
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val timestamp2 = calendar.timeInMillis

        assertFalse(shouldShowDateSeparator(timestamp2, timestamp1))
    }

    @Test
    fun `shouldShowDateSeparator should return true for midnight boundary crossing`() {
        // End of day at 23:59:59
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val timestamp1 = calendar.timeInMillis

        // Start of next day at 00:00:01
        calendar.add(Calendar.SECOND, 2)
        val timestamp2 = calendar.timeInMillis

        assertTrue(shouldShowDateSeparator(timestamp2, timestamp1))
    }
}
