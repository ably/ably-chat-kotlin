package com.ably.chat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AvatarProviderTest {

    // ==================== AvatarData Tests ====================

    @Test
    fun `AvatarData should store imageUrl correctly`() {
        val avatarData = AvatarData(imageUrl = "https://example.com/avatar.jpg")

        assertEquals("https://example.com/avatar.jpg", avatarData.imageUrl)
    }

    @Test
    fun `AvatarData should store displayName correctly`() {
        val avatarData = AvatarData(displayName = "John Doe")

        assertEquals("John Doe", avatarData.displayName)
    }

    @Test
    fun `AvatarData should have null imageUrl by default`() {
        val avatarData = AvatarData()

        assertNull(avatarData.imageUrl)
    }

    @Test
    fun `AvatarData should have null displayName by default`() {
        val avatarData = AvatarData()

        assertNull(avatarData.displayName)
    }

    @Test
    fun `AvatarData should store both imageUrl and displayName`() {
        val avatarData = AvatarData(
            imageUrl = "https://example.com/avatar.jpg",
            displayName = "Jane Smith",
        )

        assertEquals("https://example.com/avatar.jpg", avatarData.imageUrl)
        assertEquals("Jane Smith", avatarData.displayName)
    }

    // ==================== AvatarProviderOptions Tests ====================

    @Test
    fun `AvatarProviderOptions should have cacheEnabled true by default`() {
        val options = AvatarProviderOptions(
            resolver = { null },
        )

        assertEquals(true, options.cacheEnabled)
    }

    @Test
    fun `AvatarProviderOptions should allow disabling cache`() {
        val options = AvatarProviderOptions(
            resolver = { null },
            cacheEnabled = false,
        )

        assertEquals(false, options.cacheEnabled)
    }

    @Test
    fun `AvatarProviderOptions should store resolver function`() {
        val resolver: AvatarResolver = { clientId ->
            AvatarData(displayName = clientId.uppercase())
        }

        val options = AvatarProviderOptions(resolver = resolver)

        assertEquals(resolver, options.resolver)
    }
}
