package com.ably.chat.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.ably.chat.ChatClient
import com.ably.chat.ErrorInfo
import com.ably.chat.Room
import com.ably.chat.RoomOptions
import com.ably.chat.Rooms
import com.ably.chat.annotations.InternalChatApi
import com.ably.chat.ui.helpers.setContentWithTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
class RoomProviderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Loading State Tests ====================

    @Test
    fun `RoomProvider shows loading initially`() {
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.NeverComplete,
        )

        composeTestRule.setContentWithTheme {
            RoomProvider(
                chatClient = chatClient,
                roomId = "test-room",
            ) {
                // Content should not be shown during loading
            }
        }

        // Default loading shows a CircularProgressIndicator (no text to check)
        // Just verify that content is not displayed
        composeTestRule.onNodeWithText("Room Content").assertDoesNotExist()
    }

    @Test
    fun `RoomProvider shows custom loading content`() {
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.NeverComplete,
        )

        composeTestRule.setContentWithTheme {
            RoomProvider(
                chatClient = chatClient,
                roomId = "test-room",
                loadingContent = {
                    androidx.compose.material3.Text("Loading room...")
                },
            ) {
                androidx.compose.material3.Text("Room Content")
            }
        }

        composeTestRule.onNodeWithText("Loading room...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Room Content").assertDoesNotExist()
    }

    // ==================== Success State Tests ====================

    @Test
    fun `RoomProvider shows content when room attaches`() = runTest {
        val mockRoom = createMockRoom("test-room")
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.SucceedWithRoom(mockRoom),
        )

        composeTestRule.setContentWithTheme {
            RoomProvider(
                chatClient = chatClient,
                roomId = "test-room",
            ) { room ->
                androidx.compose.material3.Text("Connected to ${room.name}")
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Connected to test-room")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Connected to test-room").assertIsDisplayed()
    }

    // ==================== Error State Tests ====================

    @Test
    fun `RoomProvider shows error on attachment failure`() = runTest {
        val errorInfo = ErrorInfo(
            message = "Connection failed",
            code = 40000,
            statusCode = 400,
            href = null,
            cause = null,
            requestId = null,
        )
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.FailWithError(errorInfo),
        )

        composeTestRule.setContentWithTheme {
            RoomProvider(
                chatClient = chatClient,
                roomId = "test-room",
            ) {
                androidx.compose.material3.Text("Room Content")
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Connection failed")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Connection failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Room Content").assertDoesNotExist()
    }

    @Test
    fun `RoomProvider shows custom error content`() = runTest {
        val errorInfo = ErrorInfo(
            message = "Network error",
            code = 50000,
            statusCode = 500,
            href = null,
            cause = null,
            requestId = null,
        )
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.FailWithError(errorInfo),
        )

        composeTestRule.setContentWithTheme {
            RoomProvider(
                chatClient = chatClient,
                roomId = "test-room",
                errorContent = { error ->
                    // Using message since we can't propagate errorInfo via RuntimeException
                    androidx.compose.material3.Text("Custom Error: ${error.message}")
                },
            ) {
                androidx.compose.material3.Text("Room Content")
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Custom Error: Network error")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Custom Error: Network error").assertIsDisplayed()
    }

    // ==================== Room Release Tests ====================

    @Test
    fun `RoomProvider releases room on dispose`() = runTest {
        val mockRoom = createMockRoom("test-room")
        val mockRooms = mockk<Rooms> {
            coEvery { get(any(), any<RoomOptions>()) } returns mockRoom
            coEvery { release(any()) } returns Unit
        }
        val chatClient = mockk<ChatClient> {
            every { rooms } returns mockRooms
        }

        var showProvider by mutableStateOf(true)

        composeTestRule.setContentWithTheme {
            if (showProvider) {
                RoomProvider(
                    chatClient = chatClient,
                    roomId = "test-room",
                ) { room ->
                    androidx.compose.material3.Text("Connected to ${room.name}")
                }
            }
        }

        // Wait for room to attach
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Connected to test-room")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Remove provider from composition
        showProvider = false
        composeTestRule.waitForIdle()

        // Verify room was released
        coVerify(timeout = 5_000) { mockRooms.release("test-room") }
    }

    // ==================== Room Re-attachment Tests ====================

    @Test
    fun `RoomProvider re-attaches when roomId changes`() = runTest {
        val mockRoom1 = createMockRoom("room-1")
        val mockRoom2 = createMockRoom("room-2")
        val mockRooms = mockk<Rooms> {
            coEvery { get("room-1", any<RoomOptions>()) } returns mockRoom1
            coEvery { get("room-2", any<RoomOptions>()) } returns mockRoom2
            coEvery { release(any()) } returns Unit
        }
        val chatClient = mockk<ChatClient> {
            every { rooms } returns mockRooms
        }

        var currentRoomId by mutableStateOf("room-1")

        composeTestRule.setContentWithTheme {
            RoomProvider(
                chatClient = chatClient,
                roomId = currentRoomId,
            ) { room ->
                androidx.compose.material3.Text("Connected to ${room.name}")
            }
        }

        // Wait for first room to attach
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Connected to room-1")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Connected to room-1").assertIsDisplayed()

        // Change room ID
        currentRoomId = "room-2"

        // Wait for second room to attach
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Connected to room-2")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Connected to room-2").assertIsDisplayed()

        // Verify first room was released
        coVerify(timeout = 5_000) { mockRooms.release("room-1") }
    }

    // ==================== LocalRoom Tests ====================

    @Test
    fun `LocalRoom provides room to nested composables`() = runTest {
        val mockRoom = createMockRoom("nested-test-room")
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.SucceedWithRoom(mockRoom),
        )

        var capturedLocalRoom: Room? = null

        composeTestRule.setContentWithTheme {
            RoomProvider(
                chatClient = chatClient,
                roomId = "nested-test-room",
            ) {
                // Capture LocalRoom in nested composable
                capturedLocalRoom = LocalRoom()
                val room = LocalRoom()
                androidx.compose.material3.Text("Room: ${room?.name ?: "null"}")
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Room: nested-test-room")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Room: nested-test-room").assertIsDisplayed()
        assertNotNull(capturedLocalRoom)
        assertEquals("nested-test-room", capturedLocalRoom?.name)
    }

    @Test
    fun `LocalRoom returns null when no RoomProvider`() {
        var capturedLocalRoom: Room? = mockk() // Initialize with non-null to verify it becomes null

        composeTestRule.setContentWithTheme {
            capturedLocalRoom = LocalRoom()
            androidx.compose.material3.Text("No provider")
        }

        composeTestRule.onNodeWithText("No provider").assertIsDisplayed()
        assertNull(capturedLocalRoom)
    }

    // ==================== rememberRoomState Tests ====================

    @Test
    fun `rememberRoomState returns Loading initially`() {
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.NeverComplete,
        )

        var capturedState: RoomState? = null

        composeTestRule.setContentWithTheme {
            val state by rememberRoomState(chatClient, "test-room")
            capturedState = state
            androidx.compose.material3.Text("State captured")
        }

        composeTestRule.onNodeWithText("State captured").assertIsDisplayed()
        assertEquals(RoomState.Loading, capturedState)
    }

    @Test
    fun `rememberRoomState returns Attached when room connects`() = runTest {
        val mockRoom = createMockRoom("test-room")
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.SucceedWithRoom(mockRoom),
        )

        var capturedState: RoomState? = null

        composeTestRule.setContentWithTheme {
            val state by rememberRoomState(chatClient, "test-room")
            capturedState = state
            when (state) {
                is RoomState.Loading -> androidx.compose.material3.Text("Loading")
                is RoomState.Attached -> androidx.compose.material3.Text("Attached")
                is RoomState.Error -> androidx.compose.material3.Text("Error")
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Attached")
                .fetchSemanticsNodes().isNotEmpty()
        }

        assert(capturedState is RoomState.Attached)
        assertEquals(mockRoom, (capturedState as RoomState.Attached).room)
    }

    @Test
    fun `rememberRoomState returns Error when room fails`() = runTest {
        val errorInfo = ErrorInfo(
            message = "Test error",
            code = 12345,
            statusCode = 500,
            href = null,
            cause = null,
            requestId = null,
        )
        val chatClient = createMockChatClient(
            roomGetBehavior = RoomGetBehavior.FailWithError(errorInfo),
        )

        var capturedState: RoomState? = null

        composeTestRule.setContentWithTheme {
            val state by rememberRoomState(chatClient, "test-room")
            capturedState = state
            when (state) {
                is RoomState.Loading -> androidx.compose.material3.Text("Loading")
                is RoomState.Attached -> androidx.compose.material3.Text("Attached")
                is RoomState.Error -> androidx.compose.material3.Text("Error: ${(state as RoomState.Error).error.message}")
            }
        }

        // Wait for error state - looking for the error message from TestChatException
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Error: Test error")
                .fetchSemanticsNodes().isNotEmpty()
        }

        assert(capturedState is RoomState.Error)
        assertEquals("Test error", (capturedState as RoomState.Error).error.message)
    }

    // ==================== Helper Functions ====================

    private sealed interface RoomGetBehavior {
        data object NeverComplete : RoomGetBehavior
        data class SucceedWithRoom(val room: Room) : RoomGetBehavior
        data class FailWithError(val errorInfo: ErrorInfo) : RoomGetBehavior
    }

    @OptIn(InternalChatApi::class)
    private fun createMockRoom(
        name: String = "test-room",
        clientId: String = "test-client",
    ): Room = mockk(relaxed = true) {
        every { this@mockk.name } returns name
        every { this@mockk.clientId } returns clientId
        coEvery { attach() } returns Unit
    }

    private fun createMockChatClient(
        roomGetBehavior: RoomGetBehavior,
    ): ChatClient {
        val mockRooms = mockk<Rooms> {
            when (roomGetBehavior) {
                is RoomGetBehavior.NeverComplete -> {
                    coEvery { get(any(), any<RoomOptions>()) } coAnswers {
                        // Suspend indefinitely
                        kotlinx.coroutines.suspendCancellableCoroutine { }
                    }
                }
                is RoomGetBehavior.SucceedWithRoom -> {
                    coEvery { get(any(), any<RoomOptions>()) } returns roomGetBehavior.room
                }
                is RoomGetBehavior.FailWithError -> {
                    // Throw a RuntimeException - RoomProvider's generic handler will use message
                    coEvery { get(any(), any<RoomOptions>()) } throws RuntimeException(
                        roomGetBehavior.errorInfo.message,
                    )
                }
            }
            coEvery { release(any()) } returns Unit
        }

        return mockk {
            every { rooms } returns mockRooms
        }
    }
}
