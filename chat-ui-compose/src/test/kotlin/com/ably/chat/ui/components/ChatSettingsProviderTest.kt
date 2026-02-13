package com.ably.chat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatSettingsProviderTest {

    // ==================== ChatSettings Default Tests ====================

    @Test
    fun `ChatSettings default should allow message reactions`() {
        val settings = ChatSettings.default()

        assertTrue(settings.allowMessageReactions)
    }

    @Test
    fun `ChatSettings default should allow editing own messages`() {
        val settings = ChatSettings.default()

        assertTrue(settings.allowMessageEditOwn)
    }

    @Test
    fun `ChatSettings default should not allow editing any messages`() {
        val settings = ChatSettings.default()

        assertFalse(settings.allowMessageEditAny)
    }

    @Test
    fun `ChatSettings default should allow deleting own messages`() {
        val settings = ChatSettings.default()

        assertTrue(settings.allowMessageDeleteOwn)
    }

    @Test
    fun `ChatSettings default should not allow deleting any messages`() {
        val settings = ChatSettings.default()

        assertFalse(settings.allowMessageDeleteAny)
    }

    @Test
    fun `ChatSettings default should show typing indicator`() {
        val settings = ChatSettings.default()

        assertTrue(settings.showTypingIndicator)
    }

    @Test
    fun `ChatSettings default should show date separators`() {
        val settings = ChatSettings.default()

        assertTrue(settings.showDateSeparators)
    }

    @Test
    fun `ChatSettings default should show scroll to bottom`() {
        val settings = ChatSettings.default()

        assertTrue(settings.showScrollToBottom)
    }

    @Test
    fun `ChatSettings default should show avatars`() {
        val settings = ChatSettings.default()

        assertTrue(settings.showAvatars)
    }

    @Test
    fun `ChatSettings default should not hide deleted messages`() {
        val settings = ChatSettings.default()

        assertFalse(settings.hideDeletedMessages)
    }

    // ==================== ChatSettings ReadOnly Tests ====================

    @Test
    fun `ChatSettings readOnly should not allow message reactions`() {
        val settings = ChatSettings.readOnly()

        assertFalse(settings.allowMessageReactions)
    }

    @Test
    fun `ChatSettings readOnly should not allow editing`() {
        val settings = ChatSettings.readOnly()

        assertFalse(settings.allowMessageEditOwn)
        assertFalse(settings.allowMessageEditAny)
    }

    @Test
    fun `ChatSettings readOnly should not allow deleting`() {
        val settings = ChatSettings.readOnly()

        assertFalse(settings.allowMessageDeleteOwn)
        assertFalse(settings.allowMessageDeleteAny)
    }

    // ==================== ChatSettings Moderator Tests ====================

    @Test
    fun `ChatSettings moderator should allow all reactions`() {
        val settings = ChatSettings.moderator()

        assertTrue(settings.allowMessageReactions)
    }

    @Test
    fun `ChatSettings moderator should allow editing any message`() {
        val settings = ChatSettings.moderator()

        assertTrue(settings.allowMessageEditOwn)
        assertTrue(settings.allowMessageEditAny)
    }

    @Test
    fun `ChatSettings moderator should allow deleting any message`() {
        val settings = ChatSettings.moderator()

        assertTrue(settings.allowMessageDeleteOwn)
        assertTrue(settings.allowMessageDeleteAny)
    }

    // ==================== ChatSettingsProviderOptions Tests ====================

    @Test
    fun `ChatSettingsProviderOptions should have default settings by default`() {
        val options = ChatSettingsProviderOptions()

        assertEquals(ChatSettings.default(), options.globalSettings)
    }

    @Test
    fun `ChatSettingsProviderOptions should have empty room settings by default`() {
        val options = ChatSettingsProviderOptions()

        assertTrue(options.roomSettings.isEmpty())
    }

    @Test
    fun `ChatSettingsProviderOptions should store global settings`() {
        val customSettings = ChatSettings(
            allowMessageReactions = false,
            showTypingIndicator = false,
        )
        val options = ChatSettingsProviderOptions(globalSettings = customSettings)

        assertEquals(customSettings, options.globalSettings)
    }

    @Test
    fun `ChatSettingsProviderOptions should store room settings`() {
        val roomSettings = mapOf(
            "room-1" to ChatSettings.readOnly(),
            "room-2" to ChatSettings.moderator(),
        )
        val options = ChatSettingsProviderOptions(roomSettings = roomSettings)

        assertEquals(ChatSettings.readOnly(), options.roomSettings["room-1"])
        assertEquals(ChatSettings.moderator(), options.roomSettings["room-2"])
    }

    // ==================== ChatSettings Custom Instance Tests ====================

    @Test
    fun `ChatSettings should allow custom configuration`() {
        val settings = ChatSettings(
            allowMessageReactions = false,
            allowMessageEditOwn = true,
            allowMessageEditAny = false,
            allowMessageDeleteOwn = false,
            allowMessageDeleteAny = false,
            showTypingIndicator = false,
            showDateSeparators = true,
            showScrollToBottom = false,
            showAvatars = false,
            hideDeletedMessages = true,
        )

        assertFalse(settings.allowMessageReactions)
        assertTrue(settings.allowMessageEditOwn)
        assertFalse(settings.allowMessageEditAny)
        assertFalse(settings.allowMessageDeleteOwn)
        assertFalse(settings.allowMessageDeleteAny)
        assertFalse(settings.showTypingIndicator)
        assertTrue(settings.showDateSeparators)
        assertFalse(settings.showScrollToBottom)
        assertFalse(settings.showAvatars)
        assertTrue(settings.hideDeletedMessages)
    }
}
