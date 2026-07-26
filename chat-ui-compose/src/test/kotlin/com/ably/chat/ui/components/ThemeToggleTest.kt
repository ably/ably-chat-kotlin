package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ably.chat.ui.helpers.setContentWithTheme
import com.ably.chat.ui.theme.AblyChatThemeState
import com.ably.chat.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeToggleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== ThemeToggleButton Tests ====================

    @Test
    fun `ThemeToggleButton should display sun icon when in dark mode`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.Dark,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeToggleButton(themeState = themeState)
        }

        composeTestRule.onNodeWithContentDescription("Switch to light mode").assertIsDisplayed()
    }

    @Test
    fun `ThemeToggleButton should display moon icon when in light mode`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.Light,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeToggleButton(themeState = themeState)
        }

        composeTestRule.onNodeWithContentDescription("Switch to dark mode").assertIsDisplayed()
    }

    @Test
    fun `ThemeToggleButton should toggle theme when clicked`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.Light,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeToggleButton(themeState = themeState)
        }

        // Initially in light mode
        assertFalse(themeState.isDarkTheme)

        // Click to toggle
        composeTestRule.onNodeWithContentDescription("Switch to dark mode").performClick()

        // Should now be in dark mode
        assertTrue(themeState.isDarkTheme)
        assertEquals(ThemeMode.Dark, themeState.themeMode)
    }

    @Test
    fun `ThemeToggleButton should toggle from dark to light when clicked`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.Dark,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeToggleButton(themeState = themeState)
        }

        // Initially in dark mode
        assertTrue(themeState.isDarkTheme)

        // Click to toggle
        composeTestRule.onNodeWithContentDescription("Switch to light mode").performClick()

        // Should now be in light mode
        assertFalse(themeState.isDarkTheme)
        assertEquals(ThemeMode.Light, themeState.themeMode)
    }

    // ==================== ThemeModeSelector Tests ====================

    @Test
    fun `ThemeModeSelector should display Auto option`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeModeSelector(
                themeState = themeState,
                showLabels = true,
            )
        }

        composeTestRule.onNodeWithText("Auto").assertIsDisplayed()
    }

    @Test
    fun `ThemeModeSelector should display Light option`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeModeSelector(
                themeState = themeState,
                showLabels = true,
            )
        }

        composeTestRule.onNodeWithText("Light").assertIsDisplayed()
    }

    @Test
    fun `ThemeModeSelector should display Dark option`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeModeSelector(
                themeState = themeState,
                showLabels = true,
            )
        }

        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun `ThemeModeSelector should set mode to Light when Light clicked`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeModeSelector(
                themeState = themeState,
                showLabels = true,
            )
        }

        composeTestRule.onNodeWithText("Light").performClick()

        assertEquals(ThemeMode.Light, themeState.themeMode)
    }

    @Test
    fun `ThemeModeSelector should set mode to Dark when Dark clicked`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeModeSelector(
                themeState = themeState,
                showLabels = true,
            )
        }

        composeTestRule.onNodeWithText("Dark").performClick()

        assertEquals(ThemeMode.Dark, themeState.themeMode)
    }

    @Test
    fun `ThemeModeSelector should set mode to System when Auto clicked`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.Light,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeModeSelector(
                themeState = themeState,
                showLabels = true,
            )
        }

        composeTestRule.onNodeWithText("Auto").performClick()

        assertEquals(ThemeMode.System, themeState.themeMode)
    }

    @Test
    fun `ThemeModeSelector should not show labels when showLabels is false`() {
        val themeState = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = false,
        )

        composeTestRule.setContentWithTheme {
            ThemeModeSelector(
                themeState = themeState,
                showLabels = false,
            )
        }

        composeTestRule.onNodeWithText("Auto").assertDoesNotExist()
        composeTestRule.onNodeWithText("Light").assertDoesNotExist()
        composeTestRule.onNodeWithText("Dark").assertDoesNotExist()
    }
}
