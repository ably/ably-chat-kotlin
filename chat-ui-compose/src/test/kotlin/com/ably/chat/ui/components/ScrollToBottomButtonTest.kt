package com.ably.chat.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScrollToBottomButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Visibility Tests ====================

    @Test
    fun `ScrollToBottomButton should not display when at bottom of list`() {
        composeTestRule.setContentWithTheme {
            val listState = rememberLazyListState()

            LazyColumn(state = listState) {
                items(5) { index ->
                    Text("Item $index")
                }
            }

            ScrollToBottomButton(
                listState = listState,
                threshold = 3,
                onClick = { },
            )
        }

        // Button should not be visible when at bottom
        composeTestRule.onNodeWithContentDescription("Scroll to bottom").assertDoesNotExist()
    }

    @Test
    fun `ScrollToBottomButton should display when scrolled past threshold`() {
        composeTestRule.setContentWithTheme {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 10)

            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            ScrollToBottomButton(
                listState = listState,
                threshold = 3,
                onClick = { },
            )
        }

        // Button should be visible when scrolled
        composeTestRule.onNodeWithContentDescription("Scroll to bottom").assertIsDisplayed()
    }

    // ==================== Click Callback Tests ====================

    @Test
    fun `ScrollToBottomButton should call onClick when clicked`() {
        var clicked = false

        composeTestRule.setContentWithTheme {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 10)

            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            ScrollToBottomButton(
                listState = listState,
                threshold = 3,
                onClick = { clicked = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Scroll to bottom").performClick()

        assertTrue(clicked)
    }

    // ==================== Unread Badge Tests ====================

    @Test
    fun `ScrollToBottomButton should not show badge when unreadCount is 0`() {
        composeTestRule.setContentWithTheme {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 10)

            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            ScrollToBottomButton(
                listState = listState,
                threshold = 3,
                unreadCount = 0,
                onClick = { },
            )
        }

        // Button should be visible but badge should not
        composeTestRule.onNodeWithContentDescription("Scroll to bottom").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertDoesNotExist()
    }

    @Test
    fun `ScrollToBottomButton should show badge with unread count`() {
        composeTestRule.setContentWithTheme {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 10)

            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            ScrollToBottomButton(
                listState = listState,
                threshold = 3,
                unreadCount = 5,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun `ScrollToBottomButton should show 99 plus for large unread count`() {
        composeTestRule.setContentWithTheme {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 10)

            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            ScrollToBottomButton(
                listState = listState,
                threshold = 3,
                unreadCount = 150,
                onClick = { },
            )
        }

        composeTestRule.onNodeWithText("99+").assertIsDisplayed()
    }

    // ==================== Threshold Tests ====================

    @Test
    fun `ScrollToBottomButton should use custom threshold`() {
        composeTestRule.setContentWithTheme {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 2)

            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            ScrollToBottomButton(
                listState = listState,
                threshold = 1, // Lower threshold
                onClick = { },
            )
        }

        // With firstVisibleItemIndex = 2 and threshold = 1, button should show
        composeTestRule.onNodeWithContentDescription("Scroll to bottom").assertIsDisplayed()
    }

    @Test
    fun `ScrollToBottomButton should not show when just at threshold`() {
        composeTestRule.setContentWithTheme {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 3)

            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            ScrollToBottomButton(
                listState = listState,
                threshold = 3,
                onClick = { },
            )
        }

        // With firstVisibleItemIndex = 3 and threshold = 3, button should NOT show (need > threshold)
        composeTestRule.onNodeWithContentDescription("Scroll to bottom").assertDoesNotExist()
    }
}
