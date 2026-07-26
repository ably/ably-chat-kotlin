package com.ably.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ably.chat.ui.R
import com.ably.chat.ui.theme.AblyChatTheme
import com.ably.chat.ui.theme.AblyChatThemeState
import com.ably.chat.ui.theme.ThemeMode

/**
 * A simple icon button that toggles between light and dark themes.
 *
 * Shows a sun icon in dark mode and a moon icon in light mode.
 *
 * @param themeState The theme state to control.
 * @param modifier Modifier to be applied to the button.
 * @param size Size of the icon. Defaults to 24.dp.
 */
@Composable
public fun ThemeToggleButton(
    themeState: AblyChatThemeState,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val colors = AblyChatTheme.colors

    IconButton(
        onClick = { themeState.toggle() },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(
                id = if (themeState.isDarkTheme) R.drawable.ic_sun else R.drawable.ic_moon,
            ),
            contentDescription = if (themeState.isDarkTheme) "Switch to light mode" else "Switch to dark mode",
            tint = colors.menuContent,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * A segmented control for selecting between System, Light, and Dark theme modes.
 *
 * @param themeState The theme state to control.
 * @param modifier Modifier to be applied to the control.
 * @param showLabels Whether to show text labels alongside icons. Defaults to false.
 */
@Composable
public fun ThemeModeSelector(
    themeState: AblyChatThemeState,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false,
) {
    val colors = AblyChatTheme.colors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.inputBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeModeOption(
            icon = R.drawable.ic_system_theme,
            label = "Auto",
            isSelected = themeState.themeMode == ThemeMode.System,
            showLabel = showLabels,
            onClick = { themeState.setMode(ThemeMode.System) },
        )
        ThemeModeOption(
            icon = R.drawable.ic_sun,
            label = "Light",
            isSelected = themeState.themeMode == ThemeMode.Light,
            showLabel = showLabels,
            onClick = { themeState.setMode(ThemeMode.Light) },
        )
        ThemeModeOption(
            icon = R.drawable.ic_moon,
            label = "Dark",
            isSelected = themeState.themeMode == ThemeMode.Dark,
            showLabel = showLabels,
            onClick = { themeState.setMode(ThemeMode.Dark) },
        )
    }
}

@Composable
private fun ThemeModeOption(
    icon: Int,
    label: String,
    isSelected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val colors = AblyChatTheme.colors

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) colors.reactionBackgroundSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                tint = if (isSelected) colors.sendButton else colors.menuContent,
                modifier = Modifier.size(20.dp),
            )
            if (showLabel) {
                Text(
                    text = label,
                    color = if (isSelected) colors.sendButton else colors.menuContent,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
