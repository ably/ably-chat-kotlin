package com.ably.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ably.chat.ui.helpers.setContentWithTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Room Display Tests ====================

    @Test
    fun `RoomList should display room name`() {
        val rooms = listOf(
            RoomInfo(id = "room-1", displayName = "General Chat"),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("General Chat").assertIsDisplayed()
    }

    @Test
    fun `RoomList should display room id when displayName is null`() {
        val rooms = listOf(
            RoomInfo(id = "room-123"),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("room-123").assertIsDisplayed()
    }

    @Test
    fun `RoomList should display multiple rooms`() {
        val rooms = listOf(
            RoomInfo(id = "room-1", displayName = "General"),
            RoomInfo(id = "room-2", displayName = "Support"),
            RoomInfo(id = "room-3", displayName = "Random"),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("General").assertIsDisplayed()
        composeTestRule.onNodeWithText("Support").assertIsDisplayed()
        composeTestRule.onNodeWithText("Random").assertIsDisplayed()
    }

    @Test
    fun `RoomList should display last message preview`() {
        val rooms = listOf(
            RoomInfo(
                id = "room-1",
                displayName = "General",
                lastMessage = "Hey, how's it going?",
            ),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("Hey, how's it going?").assertIsDisplayed()
    }

    @Test
    fun `RoomList should display last message time`() {
        val rooms = listOf(
            RoomInfo(
                id = "room-1",
                displayName = "General",
                lastMessageTime = "2:30 PM",
            ),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("2:30 PM").assertIsDisplayed()
    }

    // ==================== Unread Badge Tests ====================

    @Test
    fun `RoomList should display unread badge with count`() {
        val rooms = listOf(
            RoomInfo(
                id = "room-1",
                displayName = "General",
                unreadCount = 5,
            ),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun `RoomList should display 99 plus for large unread count`() {
        val rooms = listOf(
            RoomInfo(
                id = "room-1",
                displayName = "General",
                unreadCount = 150,
            ),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("99+").assertIsDisplayed()
    }

    @Test
    fun `RoomList should not display badge when unread count is 0`() {
        val rooms = listOf(
            RoomInfo(
                id = "room-1",
                displayName = "General",
                unreadCount = 0,
            ),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        // No badge should be displayed
        composeTestRule.onNodeWithText("0").assertDoesNotExist()
    }

    // ==================== Header Tests ====================

    @Test
    fun `RoomList should display header with room count`() {
        val rooms = listOf(
            RoomInfo(id = "room-1"),
            RoomInfo(id = "room-2"),
            RoomInfo(id = "room-3"),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("3 Rooms").assertIsDisplayed()
    }

    @Test
    fun `RoomList should display singular room in header for single room`() {
        val rooms = listOf(
            RoomInfo(id = "room-1"),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
            )
        }

        composeTestRule.onNodeWithText("1 Room").assertIsDisplayed()
    }

    // ==================== Selection Callback Tests ====================

    @Test
    fun `RoomList should call onRoomSelected when room clicked`() {
        var selectedRoom: RoomInfo? = null
        val rooms = listOf(
            RoomInfo(id = "room-1", displayName = "General"),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { selectedRoom = it },
            )
        }

        composeTestRule.onNodeWithText("General").performClick()

        assertEquals("room-1", selectedRoom?.id)
    }

    @Test
    fun `RoomList should call onRoomSelected with correct room from multiple`() {
        var selectedRoom: RoomInfo? = null
        val rooms = listOf(
            RoomInfo(id = "room-1", displayName = "General"),
            RoomInfo(id = "room-2", displayName = "Support"),
        )

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { selectedRoom = it },
            )
        }

        composeTestRule.onNodeWithText("Support").performClick()

        assertEquals("room-2", selectedRoom?.id)
    }

    // ==================== Add Room Tests ====================

    @Test
    fun `RoomList should show add room button when showAddRoom is true`() {
        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = emptyList(),
                onRoomSelected = { },
                showAddRoom = true,
                onAddRoom = { },
            )
        }

        composeTestRule.onNodeWithContentDescription("Add room").assertIsDisplayed()
    }

    @Test
    fun `RoomList should not show add room button when showAddRoom is false`() {
        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = emptyList(),
                onRoomSelected = { },
                showAddRoom = false,
            )
        }

        composeTestRule.onNodeWithContentDescription("Add room").assertDoesNotExist()
    }

    @Test
    fun `RoomList should show add room dialog when add button clicked`() {
        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = emptyList(),
                onRoomSelected = { },
                showAddRoom = true,
                onAddRoom = { },
            )
        }

        composeTestRule.onNodeWithContentDescription("Add room").performClick()

        composeTestRule.onNodeWithText("Add Room").assertIsDisplayed()
    }

    @Test
    fun `RoomList should call onAddRoom with room name when add dialog confirmed`() {
        var addedRoomName: String? = null

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = emptyList(),
                onRoomSelected = { },
                showAddRoom = true,
                onAddRoom = { addedRoomName = it },
            )
        }

        // Open dialog
        composeTestRule.onNodeWithContentDescription("Add room").performClick()

        // Enter room name
        composeTestRule.onNodeWithText("Room name").performTextInput("New Room")

        // Confirm
        composeTestRule.onNodeWithText("Add").performClick()

        assertEquals("New Room", addedRoomName)
    }

    // ==================== Leave Room Tests ====================

    @Test
    fun `RoomList should show leave button when showLeaveRoom is true`() {
        val rooms = listOf(RoomInfo(id = "room-1", displayName = "General"))

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
                showLeaveRoom = true,
                onLeaveRoom = { },
            )
        }

        composeTestRule.onNodeWithContentDescription("Leave room").assertIsDisplayed()
    }

    @Test
    fun `RoomList should not show leave button when showLeaveRoom is false`() {
        val rooms = listOf(RoomInfo(id = "room-1", displayName = "General"))

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
                showLeaveRoom = false,
            )
        }

        composeTestRule.onNodeWithContentDescription("Leave room").assertDoesNotExist()
    }

    @Test
    fun `RoomList should show leave confirmation when leave button clicked`() {
        val rooms = listOf(RoomInfo(id = "room-1", displayName = "General"))

        composeTestRule.setContentWithTheme {
            RoomList(
                rooms = rooms,
                onRoomSelected = { },
                showLeaveRoom = true,
                onLeaveRoom = { },
            )
        }

        composeTestRule.onNodeWithContentDescription("Leave room").performClick()

        composeTestRule.onNodeWithText("Leave Room").assertIsDisplayed()
    }

    // ==================== RoomInfo Data Class Tests ====================

    @Test
    fun `RoomInfo should have correct default values`() {
        val room = RoomInfo(id = "test-id")

        assertEquals("test-id", room.id)
        assertNull(room.displayName)
        assertNull(room.lastMessage)
        assertNull(room.lastMessageTime)
        assertEquals(0, room.unreadCount)
        assertEquals(false, room.isActive)
    }

    @Test
    fun `RoomInfo should store all properties correctly`() {
        val room = RoomInfo(
            id = "room-123",
            displayName = "Test Room",
            lastMessage = "Hello!",
            lastMessageTime = "10:00 AM",
            unreadCount = 5,
            isActive = true,
        )

        assertEquals("room-123", room.id)
        assertEquals("Test Room", room.displayName)
        assertEquals("Hello!", room.lastMessage)
        assertEquals("10:00 AM", room.lastMessageTime)
        assertEquals(5, room.unreadCount)
        assertTrue(room.isActive)
    }
}
