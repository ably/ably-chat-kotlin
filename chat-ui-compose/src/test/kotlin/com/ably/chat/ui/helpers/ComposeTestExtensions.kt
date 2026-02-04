package com.ably.chat.ui.helpers

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * Sets content wrapped in the AblyChatTheme for testing.
 *
 * @param darkTheme Whether to use dark theme. Defaults to false.
 * @param content The composable content to test.
 */
fun ComposeContentTestRule.setContentWithTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    setContent {
        AblyChatTheme(darkTheme = darkTheme) {
            content()
        }
    }
}
