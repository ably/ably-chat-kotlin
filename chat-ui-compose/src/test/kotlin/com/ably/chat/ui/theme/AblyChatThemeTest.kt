package com.ably.chat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AblyChatThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== AblyChatColors Tests ====================

    @Test
    fun `AblyChatColors default returns light colors`() {
        val defaultColors = AblyChatColors.default()
        val lightColors = AblyChatColors.light()

        assertEquals(lightColors.ownMessageBackground, defaultColors.ownMessageBackground)
        assertEquals(lightColors.otherMessageBackground, defaultColors.otherMessageBackground)
    }

    @Test
    fun `AblyChatColors light has correct own message background`() {
        val colors = AblyChatColors.light()

        assertEquals(Color(0xFF6B7280), colors.ownMessageBackground)
    }

    @Test
    fun `AblyChatColors light has correct other message background`() {
        val colors = AblyChatColors.light()

        assertEquals(Color(0xFF3B82F6), colors.otherMessageBackground)
    }

    @Test
    fun `AblyChatColors dark has different colors from light`() {
        val lightColors = AblyChatColors.light()
        val darkColors = AblyChatColors.dark()

        assertNotEquals(lightColors.inputBackground, darkColors.inputBackground)
        assertNotEquals(lightColors.menuBackground, darkColors.menuBackground)
    }

    @Test
    fun `AblyChatColors light has white message content`() {
        val colors = AblyChatColors.light()

        assertEquals(Color.White, colors.ownMessageContent)
        assertEquals(Color.White, colors.otherMessageContent)
    }

    @Test
    fun `AblyChatColors dark has white message content`() {
        val colors = AblyChatColors.dark()

        assertEquals(Color.White, colors.ownMessageContent)
        assertEquals(Color.White, colors.otherMessageContent)
    }

    @Test
    fun `AblyChatColors has correct presence colors`() {
        val colors = AblyChatColors.light()

        assertEquals(Color(0xFF22C55E), colors.presenceOnline)
        assertEquals(Color(0xFF9CA3AF), colors.presenceOffline)
    }

    @Test
    fun `AblyChatColors has correct destructive dialog color`() {
        val lightColors = AblyChatColors.light()
        val darkColors = AblyChatColors.dark()

        assertEquals(Color(0xFFDC2626), lightColors.dialogDestructive)
        assertEquals(Color(0xFFEF4444), darkColors.dialogDestructive)
    }

    // ==================== AblyChatTypography Tests ====================

    @Test
    fun `AblyChatTypography default has correct message text size`() {
        val typography = AblyChatTypography.default()

        assertEquals(14.sp, typography.messageText)
    }

    @Test
    fun `AblyChatTypography default has correct client id size`() {
        val typography = AblyChatTypography.default()

        assertEquals(12.sp, typography.clientId)
    }

    @Test
    fun `AblyChatTypography default has correct timestamp size`() {
        val typography = AblyChatTypography.default()

        assertEquals(10.sp, typography.timestamp)
    }

    @Test
    fun `AblyChatTypography default has correct input text size`() {
        val typography = AblyChatTypography.default()

        assertEquals(14.sp, typography.inputText)
    }

    @Test
    fun `AblyChatTypography default has correct avatar initials size`() {
        val typography = AblyChatTypography.default()

        assertEquals(14.sp, typography.avatarInitials)
    }

    // ==================== ThemeMode Tests ====================

    @Test
    fun `ThemeMode has System option`() {
        assertEquals(ThemeMode.System, ThemeMode.valueOf("System"))
    }

    @Test
    fun `ThemeMode has Light option`() {
        assertEquals(ThemeMode.Light, ThemeMode.valueOf("Light"))
    }

    @Test
    fun `ThemeMode has Dark option`() {
        assertEquals(ThemeMode.Dark, ThemeMode.valueOf("Dark"))
    }

    // ==================== AblyChatThemeState Tests ====================

    @Test
    fun `AblyChatThemeState isDarkTheme returns true for Dark mode`() {
        val state = AblyChatThemeState(
            initialMode = ThemeMode.Dark,
            isSystemDark = false,
        )

        assertTrue(state.isDarkTheme)
    }

    @Test
    fun `AblyChatThemeState isDarkTheme returns false for Light mode`() {
        val state = AblyChatThemeState(
            initialMode = ThemeMode.Light,
            isSystemDark = true, // System is dark but mode is Light
        )

        assertFalse(state.isDarkTheme)
    }

    @Test
    fun `AblyChatThemeState isDarkTheme follows system when System mode`() {
        val stateWithDarkSystem = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = true,
        )

        val stateWithLightSystem = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = false,
        )

        assertTrue(stateWithDarkSystem.isDarkTheme)
        assertFalse(stateWithLightSystem.isDarkTheme)
    }

    @Test
    fun `AblyChatThemeState setMode changes themeMode`() {
        val state = AblyChatThemeState(
            initialMode = ThemeMode.Light,
            isSystemDark = false,
        )

        state.setMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, state.themeMode)
    }

    @Test
    fun `AblyChatThemeState toggle switches from light to dark`() {
        val state = AblyChatThemeState(
            initialMode = ThemeMode.Light,
            isSystemDark = false,
        )

        state.toggle()

        assertEquals(ThemeMode.Dark, state.themeMode)
    }

    @Test
    fun `AblyChatThemeState toggle switches from dark to light`() {
        val state = AblyChatThemeState(
            initialMode = ThemeMode.Dark,
            isSystemDark = false,
        )

        state.toggle()

        assertEquals(ThemeMode.Light, state.themeMode)
    }

    @Test
    fun `AblyChatThemeState cycle goes through all modes`() {
        val state = AblyChatThemeState(
            initialMode = ThemeMode.System,
            isSystemDark = false,
        )

        state.cycle()
        assertEquals(ThemeMode.Light, state.themeMode)

        state.cycle()
        assertEquals(ThemeMode.Dark, state.themeMode)

        state.cycle()
        assertEquals(ThemeMode.System, state.themeMode)
    }

    @Test
    fun `AblyChatThemeState calls onModeChanged when mode changes`() {
        var changedMode: ThemeMode? = null

        val state = AblyChatThemeState(
            initialMode = ThemeMode.Light,
            isSystemDark = false,
            onModeChanged = { changedMode = it },
        )

        state.setMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, changedMode)
    }

    // ==================== Theme Composable Access Tests ====================

    @Test
    fun `AblyChatTheme provides colors to children`() {
        var accessedColors: AblyChatColors? = null

        composeTestRule.setContent {
            AblyChatTheme {
                accessedColors = AblyChatTheme.colors
            }
        }

        assertTrue(accessedColors != null)
    }

    @Test
    fun `AblyChatTheme provides typography to children`() {
        var accessedTypography: AblyChatTypography? = null

        composeTestRule.setContent {
            AblyChatTheme {
                accessedTypography = AblyChatTheme.typography
            }
        }

        assertTrue(accessedTypography != null)
    }
}
