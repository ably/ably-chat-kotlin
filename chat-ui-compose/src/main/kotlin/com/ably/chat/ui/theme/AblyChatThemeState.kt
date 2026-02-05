package com.ably.chat.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Represents the theme mode for Ably Chat components.
 */
public enum class ThemeMode {
    /**
     * Follow the system's dark/light mode setting.
     */
    System,

    /**
     * Always use light theme.
     */
    Light,

    /**
     * Always use dark theme.
     */
    Dark,
}

/**
 * State holder for managing the Ably Chat theme.
 *
 * This class maintains the current theme mode and provides methods to toggle or set it.
 * When used with [rememberAblyChatThemeState], the preference is persisted to SharedPreferences.
 *
 * @param initialMode The initial theme mode to use.
 * @param isSystemDark Whether the system is currently in dark mode.
 * @param onModeChanged Callback invoked when the theme mode changes.
 */
@Stable
public class AblyChatThemeState internal constructor(
    initialMode: ThemeMode,
    private val isSystemDark: Boolean,
    private val onModeChanged: ((ThemeMode) -> Unit)? = null,
) {
    /**
     * The current theme mode.
     */
    public var themeMode: ThemeMode by mutableStateOf(initialMode)
        private set

    /**
     * Whether the current resolved theme is dark.
     */
    public val isDarkTheme: Boolean
        get() = when (themeMode) {
            ThemeMode.System -> isSystemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

    /**
     * Sets the theme mode.
     */
    public fun setMode(mode: ThemeMode) {
        themeMode = mode
        onModeChanged?.invoke(mode)
    }

    /**
     * Toggles between light and dark mode.
     * If currently in system mode, switches to the opposite of the current system theme.
     */
    public fun toggle() {
        val newMode = if (isDarkTheme) ThemeMode.Light else ThemeMode.Dark
        setMode(newMode)
    }

    /**
     * Cycles through theme modes: System → Light → Dark → System.
     */
    public fun cycle() {
        val newMode = when (themeMode) {
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.System
        }
        setMode(newMode)
    }
}

private const val PREFS_NAME = "ably_chat_theme"
private const val KEY_THEME_MODE = "theme_mode"

/**
 * Creates and remembers an [AblyChatThemeState] that persists the theme preference to SharedPreferences.
 *
 * @param defaultMode The default theme mode to use if no preference is saved.
 * @return An [AblyChatThemeState] instance that automatically persists changes.
 */
@Composable
public fun rememberAblyChatThemeState(
    defaultMode: ThemeMode = ThemeMode.System,
): AblyChatThemeState {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()

    return remember {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_THEME_MODE, null)?.let { name ->
            try {
                ThemeMode.valueOf(name)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: defaultMode

        AblyChatThemeState(
            initialMode = savedMode,
            isSystemDark = isSystemDark,
            onModeChanged = { mode ->
                prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
            },
        )
    }
}

/**
 * Creates and remembers an [AblyChatThemeState] without persistence.
 *
 * Use this when you don't need to persist the theme preference or want to manage
 * persistence yourself.
 *
 * @param initialMode The initial theme mode.
 * @return An [AblyChatThemeState] instance.
 */
@Composable
public fun rememberAblyChatThemeStateWithoutPersistence(
    initialMode: ThemeMode = ThemeMode.System,
): AblyChatThemeState {
    val isSystemDark = isSystemInDarkTheme()

    return remember {
        AblyChatThemeState(
            initialMode = initialMode,
            isSystemDark = isSystemDark,
        )
    }
}
