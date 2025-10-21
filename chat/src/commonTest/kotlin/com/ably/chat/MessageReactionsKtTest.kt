package com.ably.chat

import com.ably.chat.annotations.InternalChatApi
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageReactionsKtTest {

    @OptIn(InternalChatApi::class)
    @Test
    fun `mergeWith should not combine two MessageReactionSummaryEvent objects with non-overlapping data`() {
        val summary1 = DefaultMessageReactionSummary(
            unique = mapOf(
                "reaction1" to SummaryClientIdList(
                    total = 5,
                    clientIds = listOf("user1", "user2"),
                    clipped = true,
                ),
            ),
            distinct = mapOf(),
            multiple = mapOf(),
        )

        val summary2 = DefaultMessageReactionSummary(
            unique = mapOf(
                "reaction2" to SummaryClientIdList(
                    total = 3,
                    clientIds = listOf("user3"),
                    clipped = true,
                ),
            ),
            distinct = mapOf(),
            multiple = mapOf(),
        )

        val event1 = DefaultMessageReactionSummaryEvent(
            messageSerial = "123",
            reactions = summary1,
        )

        val mergedEvent = event1.mergeWith(summary2)

        assertEquals(1, mergedEvent.reactions.unique.size)
        assertEquals(5, mergedEvent.reactions.unique["reaction1"]?.total)
    }

    @OptIn(InternalChatApi::class)
    @Test
    fun `mergeWith should do nothing if clipped is false`() {
        val summary1 = DefaultMessageReactionSummary(
            unique = mapOf(
                "reaction1" to SummaryClientIdList(
                    total = 5,
                    clientIds = listOf("user1", "user2"),
                    clipped = false,
                ),
            ),
            distinct = mapOf(),
            multiple = mapOf(),
        )

        val summary2 = DefaultMessageReactionSummary(
            unique = mapOf(
                "reaction1" to SummaryClientIdList(
                    total = 5,
                    clientIds = listOf("user3"),
                    clipped = true,
                ),
            ),
            distinct = mapOf(),
            multiple = mapOf(),
        )

        val event1 = DefaultMessageReactionSummaryEvent(
            messageSerial = "123",
            reactions = summary1,
        )

        val mergedEvent = event1.mergeWith(summary2)

        assertEquals(1, mergedEvent.reactions.unique.size)
        assertEquals(5, mergedEvent.reactions.unique["reaction1"]?.total)
        assertEquals(
            setOf("user1", "user2"),
            mergedEvent.reactions.unique["reaction1"]?.clientIds?.toSet(),
        )
    }

    @OptIn(InternalChatApi::class)
    @Test
    fun `mergeWith should handle clipped reactions in unique type by merging data`() {
        val summary1 = DefaultMessageReactionSummary(
            unique = mapOf(
                "reaction1" to SummaryClientIdList(
                    total = 5,
                    clientIds = listOf("user1"),
                    clipped = true,
                ),
            ),
            distinct = mapOf(),
            multiple = mapOf(),
        )

        val summary2 = DefaultMessageReactionSummary(
            unique = mapOf(
                "reaction1" to SummaryClientIdList(
                    total = 5,
                    clientIds = listOf("user2"),
                    clipped = true,
                ),
            ),
            distinct = mapOf(),
            multiple = mapOf(),
        )

        val event1 = DefaultMessageReactionSummaryEvent(
            messageSerial = "123",
            reactions = summary1,
        )

        val mergedEvent = event1.mergeWith(summary2)

        assertEquals(1, mergedEvent.reactions.unique.size)
        assertEquals(
            setOf("user1", "user2"),
            mergedEvent.reactions.unique["reaction1"]?.clientIds?.toSet(),
        )
    }

    @OptIn(InternalChatApi::class)
    @Test
    fun `mergeWith should handle merging distinct reactions without conflicts`() {
        val summary1 = DefaultMessageReactionSummary(
            unique = mapOf(),
            distinct = mapOf(
                "reaction1" to SummaryClientIdList(
                    total = 10,
                    clientIds = listOf("user1"),
                    clipped = true,
                ),
            ),
            multiple = mapOf(),
        )

        val summary2 = DefaultMessageReactionSummary(
            unique = mapOf(),
            distinct = mapOf(
                "reaction2" to SummaryClientIdList(
                    total = 15,
                    clientIds = listOf("user2"),
                    clipped = true,
                ),
            ),
            multiple = mapOf(),
        )

        val event1 = DefaultMessageReactionSummaryEvent(
            messageSerial = "123",
            reactions = summary1,
        )

        val mergedEvent = event1.mergeWith(summary2)

        assertEquals(1, mergedEvent.reactions.distinct.size)
        assertEquals(10, mergedEvent.reactions.distinct["reaction1"]?.total)
    }

    @OptIn(InternalChatApi::class)
    @Test
    fun `mergeWith should merge multiple type reactions with overlapping clientIds`() {
        val summary1 = DefaultMessageReactionSummary(
            unique = mapOf(),
            distinct = mapOf(),
            multiple = mapOf(
                "reaction1" to SummaryClientIdCounts(
                    total = 20,
                    clientIds = mapOf("user1" to 10),
                    totalUnidentified = 5,
                    clipped = true,
                    totalClientIds = 1,
                ),
            ),
        )

        val summary2 = DefaultMessageReactionSummary(
            unique = mapOf(),
            distinct = mapOf(),
            multiple = mapOf(
                "reaction1" to SummaryClientIdCounts(
                    total = 15,
                    clientIds = mapOf("user2" to 5),
                    totalUnidentified = 3,
                    clipped = true,
                    totalClientIds = 1,
                ),
            ),
        )

        val event1 = DefaultMessageReactionSummaryEvent(
            messageSerial = "123",
            reactions = summary1,
        )

        val mergedEvent = event1.mergeWith(summary2)

        assertEquals(1, mergedEvent.reactions.multiple.size)
        assertEquals(
            mapOf("user1" to 10, "user2" to 5),
            mergedEvent.reactions.multiple["reaction1"]?.clientIds,
        )
        assertEquals(20, mergedEvent.reactions.multiple["reaction1"]?.total)
        assertEquals(5, mergedEvent.reactions.multiple["reaction1"]?.totalUnidentified)
    }
}
