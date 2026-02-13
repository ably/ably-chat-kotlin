package com.ably.chat.ui.components

import com.ably.chat.Message
import com.ably.chat.MessageAction
import com.ably.chat.ui.helpers.createMockMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatMessageListTest {

    // ==================== MessageGroupInfo Tests ====================

    @Test
    fun `MessageGroupInfo should store isFirstInGroup correctly`() {
        val groupInfo = MessageGroupInfo(isFirstInGroup = true, isLastInGroup = false)

        assertTrue(groupInfo.isFirstInGroup)
    }

    @Test
    fun `MessageGroupInfo should store isLastInGroup correctly`() {
        val groupInfo = MessageGroupInfo(isFirstInGroup = false, isLastInGroup = true)

        assertTrue(groupInfo.isLastInGroup)
    }

    @Test
    fun `MessageGroupInfo should allow both true`() {
        val groupInfo = MessageGroupInfo(isFirstInGroup = true, isLastInGroup = true)

        assertTrue(groupInfo.isFirstInGroup)
        assertTrue(groupInfo.isLastInGroup)
    }

    @Test
    fun `MessageGroupInfo should allow both false`() {
        val groupInfo = MessageGroupInfo(isFirstInGroup = false, isLastInGroup = false)

        assertFalse(groupInfo.isFirstInGroup)
        assertFalse(groupInfo.isLastInGroup)
    }

    // ==================== shouldGroupMessages Tests ====================

    @Test
    fun `shouldGroupMessages should return false when previous is null`() {
        val current = createMockMessage(clientId = "user-1", timestamp = 1000L)

        assertFalse(shouldGroupMessages(current, null))
    }

    @Test
    fun `shouldGroupMessages should return false for different clientIds`() {
        val current = createMockMessage(clientId = "user-1", timestamp = 1000L)
        val previous = createMockMessage(clientId = "user-2", timestamp = 900L)

        assertFalse(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should return true for same clientId within threshold`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "user-1", timestamp = timestamp)
        val previous = createMockMessage(clientId = "user-1", timestamp = timestamp - 60000L) // 1 minute ago

        assertTrue(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should return false when time exceeds threshold`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "user-1", timestamp = timestamp)
        val previous = createMockMessage(clientId = "user-1", timestamp = timestamp - 180000L) // 3 minutes ago

        assertFalse(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should return false when current message is deleted`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(
            clientId = "user-1",
            timestamp = timestamp,
            action = MessageAction.MessageDelete,
        )
        val previous = createMockMessage(clientId = "user-1", timestamp = timestamp - 60000L)

        assertFalse(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should return false when previous message is deleted`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "user-1", timestamp = timestamp)
        val previous = createMockMessage(
            clientId = "user-1",
            timestamp = timestamp - 60000L,
            action = MessageAction.MessageDelete,
        )

        assertFalse(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should return true at exactly 2 minute threshold`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "user-1", timestamp = timestamp)
        val previous = createMockMessage(clientId = "user-1", timestamp = timestamp - 120000L) // Exactly 2 minutes

        assertTrue(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should return false just over 2 minute threshold`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "user-1", timestamp = timestamp)
        val previous = createMockMessage(clientId = "user-1", timestamp = timestamp - 120001L) // Just over 2 minutes

        assertFalse(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should handle messages with same timestamp`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "user-1", timestamp = timestamp)
        val previous = createMockMessage(clientId = "user-1", timestamp = timestamp)

        assertTrue(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should handle future previous timestamp`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "user-1", timestamp = timestamp)
        val previous = createMockMessage(clientId = "user-1", timestamp = timestamp + 60000L) // 1 minute in future

        // Uses absolute difference, so should still work
        assertTrue(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should return true for edited messages from same user within threshold`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(
            clientId = "user-1",
            timestamp = timestamp,
            action = MessageAction.MessageUpdate,
        )
        val previous = createMockMessage(clientId = "user-1", timestamp = timestamp - 60000L)

        assertTrue(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should handle very old messages`() {
        val current = createMockMessage(clientId = "user-1", timestamp = 1000000000000L) // Some fixed time
        val previous = createMockMessage(clientId = "user-1", timestamp = 0L) // Very old

        assertFalse(shouldGroupMessages(current, previous))
    }

    // ==================== Edge Cases ====================

    @Test
    fun `shouldGroupMessages should be symmetric for same clientId`() {
        val timestamp = System.currentTimeMillis()
        val msg1 = createMockMessage(clientId = "user-1", timestamp = timestamp)
        val msg2 = createMockMessage(clientId = "user-1", timestamp = timestamp - 60000L)

        // Both directions should give same result
        assertTrue(shouldGroupMessages(msg1, msg2))
        assertTrue(shouldGroupMessages(msg2, msg1))
    }

    @Test
    fun `shouldGroupMessages should handle empty string clientId`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "", timestamp = timestamp)
        val previous = createMockMessage(clientId = "", timestamp = timestamp - 60000L)

        assertTrue(shouldGroupMessages(current, previous))
    }

    @Test
    fun `shouldGroupMessages should handle whitespace clientIds correctly`() {
        val timestamp = System.currentTimeMillis()
        val current = createMockMessage(clientId = "user 1", timestamp = timestamp)
        val previous = createMockMessage(clientId = "user 1", timestamp = timestamp - 60000L)

        assertTrue(shouldGroupMessages(current, previous))
    }
}
