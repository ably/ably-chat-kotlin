package com.ably.chat.extensions.compose

import com.ably.chat.MessageReactionSummary
import com.ably.chat.MessageReactionSummaryEvent
import com.ably.chat.SummaryClientIdCounts
import com.ably.chat.SummaryClientIdList
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesTest {

    @Test
    fun `hasClippedWithoutMyClientId should return false when nothing is clipped`() {
        val clientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns false
            every { clientIds } returns listOf("user1", "user2")
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf("like" to clientIdList)
            every { distinct } returns mapOf()
            every { multiple } returns mapOf()
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { messageSerial } returns "123"
            every { reactions } returns summary
        }

        assertFalse(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return false when clipped but myClientId is present in unique`() {
        val clientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user1", "myClientId", "user2")
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf("like" to clientIdList)
            every { distinct } returns mapOf()
            every { multiple } returns mapOf()
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertFalse(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return true when clipped and myClientId is not present in unique`() {
        val clientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user1", "user2")
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf("like" to clientIdList)
            every { distinct } returns mapOf()
            every { multiple } returns mapOf()
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertTrue(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return false when clipped but myClientId is present in distinct`() {
        val clientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user1", "myClientId", "user3")
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf()
            every { distinct } returns mapOf("heart" to clientIdList)
            every { multiple } returns mapOf()
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertFalse(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return true when clipped and myClientId is not present in distinct`() {
        val clientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user1", "user2", "user3")
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf()
            every { distinct } returns mapOf("heart" to clientIdList)
            every { multiple } returns mapOf()
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertTrue(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return false when clipped but myClientId is present in multiple`() {
        val clientIdCounts = mockk<SummaryClientIdCounts> {
            every { clipped } returns true
            every { clientIds } returns mapOf("user1" to 5, "myClientId" to 3, "user2" to 7)
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf()
            every { distinct } returns mapOf()
            every { multiple } returns mapOf("fire" to clientIdCounts)
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertFalse(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return true when clipped and myClientId is not present in multiple`() {
        val clientIdCounts = mockk<SummaryClientIdCounts> {
            every { clipped } returns true
            every { clientIds } returns mapOf("user1" to 5, "user2" to 7)
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf()
            every { distinct } returns mapOf()
            every { multiple } returns mapOf("fire" to clientIdCounts)
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertTrue(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return true when any reaction type is clipped without myClientId`() {
        val uniqueClientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns false
            every { clientIds } returns listOf("user1", "myClientId")
        }

        val distinctClientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user1", "user2")
        }

        val clientIdCounts = mockk<SummaryClientIdCounts> {
            every { clipped } returns true
            every { clientIds } returns mapOf("user1" to 5, "myClientId" to 3)
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf("like" to uniqueClientIdList)
            every { distinct } returns mapOf("heart" to distinctClientIdList)
            every { multiple } returns mapOf("fire" to clientIdCounts)
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertTrue(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return false when all clipped reactions contain myClientId`() {
        val uniqueClientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user1", "myClientId", "user2")
        }

        val distinctClientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("myClientId", "user1", "user2")
        }

        val clientIdCounts = mockk<SummaryClientIdCounts> {
            every { clipped } returns true
            every { clientIds } returns mapOf("myClientId" to 8, "user1" to 5, "user2" to 7)
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf("like" to uniqueClientIdList)
            every { distinct } returns mapOf("heart" to distinctClientIdList)
            every { multiple } returns mapOf("fire" to clientIdCounts)
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertFalse(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return false when reactions are empty`() {
        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf()
            every { distinct } returns mapOf()
            every { multiple } returns mapOf()
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertFalse(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should handle multiple reactions with mixed clipped states`() {
        val likeClientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns false
            every { clientIds } returns listOf("user1", "user2")
        }

        val loveClientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user3", "user4", "myClientId")
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf("like" to likeClientIdList, "love" to loveClientIdList)
            every { distinct } returns mapOf()
            every { multiple } returns mapOf()
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertFalse(event.hasClippedWithoutMyClientId("myClientId"))
    }

    @Test
    fun `hasClippedWithoutMyClientId should return true when multiple reactions include one clipped without myClientId`() {
        val likeClientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user1", "myClientId")
        }

        val loveClientIdList = mockk<SummaryClientIdList> {
            every { clipped } returns true
            every { clientIds } returns listOf("user3", "user4")
        }

        val summary = mockk<MessageReactionSummary> {
            every { unique } returns mapOf("like" to likeClientIdList, "love" to loveClientIdList)
            every { distinct } returns mapOf()
            every { multiple } returns mapOf()
        }

        val event = mockk<MessageReactionSummaryEvent> {
            every { reactions } returns summary
        }

        assertTrue(event.hasClippedWithoutMyClientId("myClientId"))
    }
}
