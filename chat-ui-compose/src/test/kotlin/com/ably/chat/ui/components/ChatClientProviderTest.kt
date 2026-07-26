package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ably.chat.ChatClient
import com.ably.chat.ui.helpers.setContentWithTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatClientProviderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ChatClientProvider provides ChatClient to content`() {
        val mockChatClient = mockk<ChatClient>(relaxed = true)
        var capturedChatClient: ChatClient? = null

        composeTestRule.setContentWithTheme {
            ChatClientProvider(chatClient = mockChatClient) {
                capturedChatClient = LocalChatClient()
                androidx.compose.material3.Text("Content with client")
            }
        }

        composeTestRule.onNodeWithText("Content with client").assertIsDisplayed()
        assertNotNull(capturedChatClient)
        assertEquals(mockChatClient, capturedChatClient)
    }

    @Test
    fun `LocalChatClient returns ChatClient from provider`() {
        val mockChatClient = mockk<ChatClient>(relaxed = true)
        var capturedChatClient: ChatClient? = null

        composeTestRule.setContentWithTheme {
            ChatClientProvider(chatClient = mockChatClient) {
                capturedChatClient = LocalChatClient()
                val client = LocalChatClient()
                androidx.compose.material3.Text(
                    if (client != null) "Client available" else "No client",
                )
            }
        }

        composeTestRule.onNodeWithText("Client available").assertIsDisplayed()
        assertEquals(mockChatClient, capturedChatClient)
    }

    @Test
    fun `LocalChatClient returns null when no provider`() {
        var capturedChatClient: ChatClient? = mockk() // Initialize with non-null to verify it becomes null

        composeTestRule.setContentWithTheme {
            capturedChatClient = LocalChatClient()
            androidx.compose.material3.Text("No provider")
        }

        composeTestRule.onNodeWithText("No provider").assertIsDisplayed()
        assertNull(capturedChatClient)
    }

    @Test
    fun `Nested ChatClientProvider overrides parent`() {
        val parentChatClient = mockk<ChatClient>(relaxed = true)
        val childChatClient = mockk<ChatClient>(relaxed = true)
        var capturedOuterClient: ChatClient? = null
        var capturedInnerClient: ChatClient? = null

        composeTestRule.setContentWithTheme {
            ChatClientProvider(chatClient = parentChatClient) {
                capturedOuterClient = LocalChatClient()

                ChatClientProvider(chatClient = childChatClient) {
                    capturedInnerClient = LocalChatClient()
                    androidx.compose.material3.Text("Nested content")
                }
            }
        }

        composeTestRule.onNodeWithText("Nested content").assertIsDisplayed()
        assertEquals(parentChatClient, capturedOuterClient)
        assertEquals(childChatClient, capturedInnerClient)
    }
}
