package com.ably.chat.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.ably.chat.ConnectionStatus
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConnectionStatusIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Status Text Tests ====================

    @Test
    fun `ConnectionStatusIndicator should display Connected for connected status`() {
        val statusState = mutableStateOf(ConnectionStatus.Connected)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
                showWhenConnected = true,
            )
        }

        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator should display Connecting for connecting status`() {
        val statusState = mutableStateOf(ConnectionStatus.Connecting)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
            )
        }

        composeTestRule.onNodeWithText("Connecting").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator should display Offline for disconnected status`() {
        val statusState = mutableStateOf(ConnectionStatus.Disconnected)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
            )
        }

        composeTestRule.onNodeWithText("Offline").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator should display Reconnecting for suspended status`() {
        val statusState = mutableStateOf(ConnectionStatus.Suspended)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
            )
        }

        composeTestRule.onNodeWithText("Reconnecting").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator should display Failed for failed status`() {
        val statusState = mutableStateOf(ConnectionStatus.Failed)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
            )
        }

        composeTestRule.onNodeWithText("Failed").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator should display Starting for initialized status`() {
        val statusState = mutableStateOf(ConnectionStatus.Initialized)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
            )
        }

        composeTestRule.onNodeWithText("Starting").assertIsDisplayed()
    }

    // ==================== Style Tests ====================

    @Test
    fun `ConnectionStatusIndicator should render in Dot style`() {
        val statusState = mutableStateOf(ConnectionStatus.Connected)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Dot,
                showWhenConnected = true,
            )
        }

        // Dot style renders without text
        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator should render in Badge style`() {
        val statusState = mutableStateOf(ConnectionStatus.Connecting)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
            )
        }

        composeTestRule.onNodeWithText("Connecting").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator should render in Banner style`() {
        val statusState = mutableStateOf(ConnectionStatus.Disconnected)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Banner,
            )
        }

        // Banner style shows longer text
        composeTestRule.onNodeWithText("Connection lost. Reconnecting...").assertIsDisplayed()
    }

    // ==================== Show When Connected Tests ====================

    @Test
    fun `ConnectionStatusIndicator should not show when connected and showWhenConnected is false`() {
        val statusState = mutableStateOf(ConnectionStatus.Connected)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
                showWhenConnected = false,
            )
        }

        composeTestRule.onNodeWithText("Connected").assertDoesNotExist()
    }

    @Test
    fun `ConnectionStatusIndicator should show when connected and showWhenConnected is true`() {
        val statusState = mutableStateOf(ConnectionStatus.Connected)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Badge,
                showWhenConnected = true,
            )
        }

        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator Banner style should hide when connected by default`() {
        val statusState = mutableStateOf(ConnectionStatus.Connected)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Banner,
            )
        }

        // Banner style defaults to showWhenConnected = false
        composeTestRule.onNodeWithText("Connected").assertDoesNotExist()
    }

    // ==================== Banner Text Tests ====================

    @Test
    fun `ConnectionStatusIndicator Banner should show detailed connecting message`() {
        val statusState = mutableStateOf(ConnectionStatus.Connecting)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Banner,
            )
        }

        composeTestRule.onNodeWithText("Connecting to chat...").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator Banner should show detailed suspended message`() {
        val statusState = mutableStateOf(ConnectionStatus.Suspended)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Banner,
            )
        }

        composeTestRule.onNodeWithText("Connection suspended. Retrying...").assertIsDisplayed()
    }

    @Test
    fun `ConnectionStatusIndicator Banner should show detailed failed message`() {
        val statusState = mutableStateOf(ConnectionStatus.Failed)

        composeTestRule.setContentWithTheme {
            ConnectionStatusIndicator(
                statusState = statusState,
                style = ConnectionStatusStyle.Banner,
            )
        }

        composeTestRule.onNodeWithText("Connection failed. Please try again.").assertIsDisplayed()
    }
}
