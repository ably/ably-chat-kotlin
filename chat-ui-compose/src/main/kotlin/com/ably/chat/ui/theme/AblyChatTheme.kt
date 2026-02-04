package com.ably.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal used to pass [AblyChatColors] down the tree.
 */
public val LocalAblyChatColors: androidx.compose.runtime.ProvidableCompositionLocal<AblyChatColors> =
    staticCompositionLocalOf { AblyChatColors.default() }

/**
 * CompositionLocal used to pass [AblyChatTypography] down the tree.
 */
public val LocalAblyChatTypography: androidx.compose.runtime.ProvidableCompositionLocal<AblyChatTypography> =
    staticCompositionLocalOf { AblyChatTypography.default() }

/**
 * Theme wrapper for Ably Chat UI components.
 *
 * Provides [AblyChatColors] and [AblyChatTypography] to all child composables
 * through [LocalAblyChatColors] and [LocalAblyChatTypography].
 *
 * @param darkTheme Whether to use dark theme colors. Defaults to system setting.
 * @param colors The color scheme to use. If null, automatically selects light or dark based on [darkTheme].
 * @param typography The typography configuration to use. Defaults to [AblyChatTypography.default].
 * @param content The composable content to display within the theme.
 */
@Composable
public fun AblyChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colors: AblyChatColors? = null,
    typography: AblyChatTypography = AblyChatTypography.default(),
    content: @Composable () -> Unit,
) {
    val resolvedColors = colors ?: if (darkTheme) AblyChatColors.dark() else AblyChatColors.light()

    CompositionLocalProvider(
        LocalAblyChatColors provides resolvedColors,
        LocalAblyChatTypography provides typography,
        content = content,
    )
}

/**
 * Contains functions to access the current theme values.
 */
public object AblyChatTheme {
    /**
     * Retrieves the current [AblyChatColors] at the call site's position in the hierarchy.
     */
    public val colors: AblyChatColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAblyChatColors.current

    /**
     * Retrieves the current [AblyChatTypography] at the call site's position in the hierarchy.
     */
    public val typography: AblyChatTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAblyChatTypography.current
}
