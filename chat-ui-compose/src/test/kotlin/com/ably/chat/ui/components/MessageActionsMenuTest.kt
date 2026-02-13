package com.ably.chat.ui.components

import androidx.compose.ui.graphics.Color
import com.ably.chat.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MessageActionsMenuTest {

    // ==================== ChatMessageAction Tests ====================

    @Test
    fun `ChatMessageAction should store label correctly`() {
        val action = ChatMessageAction(
            label = "Test Action",
            onClick = { },
        )

        assertEquals("Test Action", action.label)
    }

    @Test
    fun `ChatMessageAction should have null textColor by default`() {
        val action = ChatMessageAction(
            label = "Test Action",
            onClick = { },
        )

        assertNull(action.textColor)
    }

    @Test
    fun `ChatMessageAction should store custom textColor`() {
        val customColor = Color.Red
        val action = ChatMessageAction(
            label = "Test Action",
            onClick = { },
            textColor = customColor,
        )

        assertEquals(customColor, action.textColor)
    }

    // ==================== Default Action Factory Tests ====================

    @Test
    fun `defaultCopyAction should create action with Copy label`() {
        val action = defaultCopyAction { }

        assertEquals("Copy", action.label)
    }

    @Test
    fun `defaultCopyAction should invoke callback with message text`() {
        var copiedText: String? = null
        val action = defaultCopyAction { copiedText = it }

        val mockMessage = io.mockk.mockk<Message> {
            io.mockk.every { text } returns "Test message"
        }
        action.onClick(mockMessage)

        assertEquals("Test message", copiedText)
    }

    @Test
    fun `editAction should create action with Edit label`() {
        val action = editAction { }

        assertEquals("Edit", action.label)
    }

    @Test
    fun `editAction should invoke callback with message`() {
        var editedMessage: Message? = null
        val action = editAction { editedMessage = it }

        val mockMessage = io.mockk.mockk<Message>()
        action.onClick(mockMessage)

        assertEquals(mockMessage, editedMessage)
    }

    @Test
    fun `deleteAction should create action with Delete label`() {
        val action = deleteAction({ }, Color.Red)

        assertEquals("Delete", action.label)
    }

    @Test
    fun `deleteAction should have destructive color`() {
        val destructiveColor = Color.Red
        val action = deleteAction({ }, destructiveColor)

        assertEquals(destructiveColor, action.textColor)
    }

    @Test
    fun `deleteAction should invoke callback with message`() {
        var deletedMessage: Message? = null
        val action = deleteAction({ deletedMessage = it }, Color.Red)

        val mockMessage = io.mockk.mockk<Message>()
        action.onClick(mockMessage)

        assertEquals(mockMessage, deletedMessage)
    }

    @Test
    fun `reactAction should create action with React label`() {
        val action = reactAction { }

        assertEquals("React", action.label)
    }

    @Test
    fun `reactAction should invoke callback with message`() {
        var reactedMessage: Message? = null
        val action = reactAction { reactedMessage = it }

        val mockMessage = io.mockk.mockk<Message>()
        action.onClick(mockMessage)

        assertEquals(mockMessage, reactedMessage)
    }

    // ==================== defaultMessageActions Tests ====================

    @Test
    fun `defaultMessageActions should include Copy for all messages`() {
        val actions = defaultMessageActions(
            isOwnMessage = false,
            onCopy = { },
            destructiveColor = Color.Red,
        )

        val copyAction = actions.find { it.label == "Copy" }
        assertNotNull(copyAction)
    }

    @Test
    fun `defaultMessageActions should include Edit for own messages when callback provided`() {
        val actions = defaultMessageActions(
            isOwnMessage = true,
            onCopy = { },
            onEdit = { },
            destructiveColor = Color.Red,
        )

        val editAction = actions.find { it.label == "Edit" }
        assertNotNull(editAction)
    }

    @Test
    fun `defaultMessageActions should not include Edit for others messages`() {
        val actions = defaultMessageActions(
            isOwnMessage = false,
            onCopy = { },
            onEdit = { },
            destructiveColor = Color.Red,
        )

        val editAction = actions.find { it.label == "Edit" }
        assertNull(editAction)
    }

    @Test
    fun `defaultMessageActions should include Delete for own messages when callback provided`() {
        val actions = defaultMessageActions(
            isOwnMessage = true,
            onCopy = { },
            onDelete = { },
            destructiveColor = Color.Red,
        )

        val deleteAction = actions.find { it.label == "Delete" }
        assertNotNull(deleteAction)
    }

    @Test
    fun `defaultMessageActions should not include Delete for others messages`() {
        val actions = defaultMessageActions(
            isOwnMessage = false,
            onCopy = { },
            onDelete = { },
            destructiveColor = Color.Red,
        )

        val deleteAction = actions.find { it.label == "Delete" }
        assertNull(deleteAction)
    }

    @Test
    fun `defaultMessageActions should include React when callback provided`() {
        val actions = defaultMessageActions(
            isOwnMessage = false,
            onCopy = { },
            onReact = { },
            destructiveColor = Color.Red,
        )

        val reactAction = actions.find { it.label == "React" }
        assertNotNull(reactAction)
    }

    @Test
    fun `defaultMessageActions should not include React when callback not provided`() {
        val actions = defaultMessageActions(
            isOwnMessage = false,
            onCopy = { },
            onReact = null,
            destructiveColor = Color.Red,
        )

        val reactAction = actions.find { it.label == "React" }
        assertNull(reactAction)
    }

    @Test
    fun `defaultMessageActions should not include Edit when callback not provided`() {
        val actions = defaultMessageActions(
            isOwnMessage = true,
            onCopy = { },
            onEdit = null,
            destructiveColor = Color.Red,
        )

        val editAction = actions.find { it.label == "Edit" }
        assertNull(editAction)
    }

    @Test
    fun `defaultMessageActions should not include Delete when callback not provided`() {
        val actions = defaultMessageActions(
            isOwnMessage = true,
            onCopy = { },
            onDelete = null,
            destructiveColor = Color.Red,
        )

        val deleteAction = actions.find { it.label == "Delete" }
        assertNull(deleteAction)
    }

    @Test
    fun `defaultMessageActions should place React first when provided`() {
        val actions = defaultMessageActions(
            isOwnMessage = true,
            onCopy = { },
            onReact = { },
            onEdit = { },
            onDelete = { },
            destructiveColor = Color.Red,
        )

        assertTrue(actions.isNotEmpty())
        assertEquals("React", actions.first().label)
    }

    @Test
    fun `defaultMessageActions should order actions as React, Copy, Edit, Delete`() {
        val actions = defaultMessageActions(
            isOwnMessage = true,
            onCopy = { },
            onReact = { },
            onEdit = { },
            onDelete = { },
            destructiveColor = Color.Red,
        )

        assertEquals(4, actions.size)
        assertEquals("React", actions[0].label)
        assertEquals("Copy", actions[1].label)
        assertEquals("Edit", actions[2].label)
        assertEquals("Delete", actions[3].label)
    }
}
