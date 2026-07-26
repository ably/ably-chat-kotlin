package com.ably.chat.ui.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ably.chat.copy
import com.ably.chat.delete
import com.ably.chat.test.RoomOptionsWithAllFeatures
import com.ably.chat.test.Sandbox
import com.ably.chat.test.createConnectedRoom
import com.ably.chat.test.createSandboxChatClient
import com.ably.chat.ui.components.ChatWindow
import com.ably.chat.ui.components.RoomProvider
import com.ably.chat.ui.theme.AblyChatTheme
import com.ably.chat.update
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end tests for ChatWindow that connect to the real Ably sandbox.
 *
 * These tests verify complete user journeys through the UI components,
 * using real network connections to the Ably sandbox environment.
 *
 * Run with: ./gradlew :chat-ui-compose:e2eTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatWindowE2ETest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Test: Send a message and verify it appears in the message list.
     *
     * User journey:
     * 1. User types a message in the input field
     * 2. User clicks the send button
     * 3. Message appears in the message list
     */
    @Test
    fun `send message and verify it appears in list`() = runTest {
        val roomName = UUID.randomUUID().toString()
        val room = sandbox.createConnectedRoom("test-user", roomName)

        composeTestRule.setContent {
            AblyChatTheme {
                ChatWindow(room = room)
            }
        }

        // Type message in input field
        composeTestRule.onNodeWithText("Type a message...").performTextInput("Hello E2E!")

        // Click send button
        composeTestRule.onNodeWithText("Send").performClick()

        // Wait for message to appear in the list
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Hello E2E!").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Hello E2E!").assertIsDisplayed()
    }

    /**
     * Test: Typing indicator shows when user types.
     *
     * User journey:
     * 1. Client2 starts typing
     * 2. Client1's UI shows typing indicator with Client2's name
     */
    @Test
    fun `typing indicator shows when another user types`() = runTest {
        val roomName = UUID.randomUUID().toString()

        // Client 1 - observing the UI
        val room1 = sandbox.createConnectedRoom("client-1", roomName)

        // Client 2 - will trigger typing
        val room2 = sandbox.createConnectedRoom("client-2", roomName)

        composeTestRule.setContent {
            AblyChatTheme {
                ChatWindow(
                    room = room1,
                    showTypingIndicator = true,
                )
            }
        }

        // Client 2 triggers a keystroke
        room2.typing.keystroke()

        // Wait for typing indicator to show client-2 is typing
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(hasText("client-2 is typing...", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNode(hasText("client-2 is typing...", substring = true)).assertIsDisplayed()
    }

    /**
     * Test: Receive a message from another client.
     *
     * User journey:
     * 1. Client2 sends a message
     * 2. Client1's UI shows the new message
     */
    @Test
    fun `receive message from another client`() = runTest {
        val roomName = UUID.randomUUID().toString()

        // Client 1 - observing the UI
        val room1 = sandbox.createConnectedRoom("client-1", roomName)

        // Client 2 - will send the message
        val room2 = sandbox.createConnectedRoom("client-2", roomName)

        composeTestRule.setContent {
            AblyChatTheme {
                ChatWindow(room = room1)
            }
        }

        // Wait for room to be ready
        composeTestRule.waitForIdle()

        // Client 2 sends a message
        room2.messages.send("Hello from client-2!")

        // Wait for message to appear in Client 1's UI
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Hello from client-2!").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Hello from client-2!").assertIsDisplayed()
    }

    /**
     * Test: Edit own message.
     *
     * User journey:
     * 1. User sends a message
     * 2. Message is updated via SDK
     * 3. Updated text appears with "(edited)" indicator
     */
    @Test
    fun `edit own message`() = runTest {
        val roomName = UUID.randomUUID().toString()
        val room = sandbox.createConnectedRoom("test-user", roomName)

        composeTestRule.setContent {
            AblyChatTheme {
                ChatWindow(
                    room = room,
                    enableEditing = true,
                )
            }
        }

        // Send initial message
        val sentMessage = room.messages.send("Original message")

        // Wait for message to appear
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Original message").fetchSemanticsNodes().isNotEmpty()
        }

        // Update the message via SDK
        room.messages.update(
            sentMessage.copy(text = "Updated message"),
        )

        // Wait for updated message to appear
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Updated message").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Updated message").assertIsDisplayed()

        // Verify "(edited)" indicator appears
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("(edited)", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Test: Delete own message.
     *
     * User journey:
     * 1. User sends a message
     * 2. Message is deleted via SDK
     * 3. "[Message deleted]" text appears
     */
    @Test
    fun `delete own message`() = runTest {
        val roomName = UUID.randomUUID().toString()
        val room = sandbox.createConnectedRoom("test-user", roomName)

        composeTestRule.setContent {
            AblyChatTheme {
                ChatWindow(
                    room = room,
                    enableDeletion = true,
                )
            }
        }

        // Send initial message
        val sentMessage = room.messages.send("Message to delete")

        // Wait for message to appear
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Message to delete").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Message to delete").assertIsDisplayed()

        // Delete the message via SDK
        room.messages.delete(sentMessage)

        // Wait for "[Message deleted]" to appear
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("[Message deleted]").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("[Message deleted]").assertIsDisplayed()
    }

    /**
     * Test: RoomProvider attaches and provides room to ChatWindow.
     *
     * User journey:
     * 1. RoomProvider is rendered with chatClient and roomId
     * 2. RoomProvider shows loading state initially
     * 3. Room attaches successfully
     * 4. ChatWindow is rendered with the attached room
     */
    @Test
    fun `RoomProvider attaches and provides room to ChatWindow`() = runTest {
        val chatClient = sandbox.createSandboxChatClient("test-user")
        val roomId = UUID.randomUUID().toString()

        composeTestRule.setContent {
            AblyChatTheme {
                RoomProvider(
                    chatClient = chatClient,
                    roomId = roomId,
                    options = RoomOptionsWithAllFeatures,
                ) { room ->
                    ChatWindow(room = room)
                }
            }
        }

        // Wait for room to attach and UI to render
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Type a message...")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify ChatWindow is displayed
        composeTestRule.onNodeWithText("Type a message...").assertIsDisplayed()
    }

    companion object {
        private lateinit var sandbox: Sandbox

        @JvmStatic
        @BeforeClass
        fun setUp() = runTest {
            sandbox = Sandbox.createInstance()
        }
    }
}
