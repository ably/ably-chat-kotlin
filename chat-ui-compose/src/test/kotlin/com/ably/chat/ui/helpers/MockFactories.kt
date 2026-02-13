package com.ably.chat.ui.helpers

import com.ably.chat.Message
import com.ably.chat.MessageAction
import com.ably.chat.MessageReactionSummary
import com.ably.chat.PresenceMember
import com.ably.chat.Room
import com.ably.chat.SummaryClientIdCounts
import com.ably.chat.SummaryClientIdList
import com.ably.chat.annotations.InternalChatApi
import com.ably.chat.json.JsonObject
import io.mockk.every
import io.mockk.mockk

/**
 * Creates a mock Message for testing.
 *
 * @param serial The message serial (unique identifier).
 * @param clientId The client ID of the message sender.
 * @param text The message text content.
 * @param timestamp The message timestamp in milliseconds.
 * @param action The message action (create, update, delete).
 * @param reactions Optional mock reaction summary.
 * @return A mocked Message object.
 */
fun createMockMessage(
    serial: String = "msg-001",
    clientId: String = "user-1",
    text: String = "Hello, world!",
    timestamp: Long = System.currentTimeMillis(),
    action: MessageAction = MessageAction.MessageCreate,
    reactions: MessageReactionSummary = createEmptyReactionSummary(),
): Message = mockk {
    every { this@mockk.serial } returns serial
    every { this@mockk.clientId } returns clientId
    every { this@mockk.text } returns text
    every { this@mockk.timestamp } returns timestamp
    every { this@mockk.action } returns action
    every { this@mockk.reactions } returns reactions
}

/**
 * Creates an empty MessageReactionSummary for testing.
 */
fun createEmptyReactionSummary(): MessageReactionSummary = mockk {
    every { unique } returns emptyMap()
    every { distinct } returns emptyMap()
    every { multiple } returns emptyMap()
}

/**
 * Creates a MessageReactionSummary with the specified reactions.
 *
 * @param uniqueReactions Map of emoji to SummaryClientIdList for unique reactions.
 * @param distinctReactions Map of emoji to SummaryClientIdList for distinct reactions.
 * @param multipleReactions Map of emoji to SummaryClientIdCounts for multiple reactions.
 */
fun createReactionSummary(
    uniqueReactions: Map<String, SummaryClientIdList> = emptyMap(),
    distinctReactions: Map<String, SummaryClientIdList> = emptyMap(),
    multipleReactions: Map<String, SummaryClientIdCounts> = emptyMap(),
): MessageReactionSummary = mockk {
    every { unique } returns uniqueReactions
    every { distinct } returns distinctReactions
    every { multiple } returns multipleReactions
}

/**
 * Creates a mock SummaryClientIdList for testing.
 *
 * @param total The total count of this reaction.
 * @param clientIds List of client IDs who reacted.
 * @param clipped Whether the client list was truncated.
 */
fun createSummaryClientIdList(
    total: Int,
    clientIds: List<String>,
    clipped: Boolean = false,
): SummaryClientIdList = mockk {
    every { this@mockk.total } returns total
    every { this@mockk.clientIds } returns clientIds
    every { this@mockk.clipped } returns clipped
}

/**
 * Creates a mock SummaryClientIdCounts for testing.
 *
 * @param total The total count of this reaction.
 * @param clientIds Map of client ID to count.
 * @param totalUnidentified Total from unidentified clients.
 * @param clipped Whether the client list was truncated.
 * @param totalClientIds Number of distinct identified clients.
 */
fun createSummaryClientIdCounts(
    total: Int,
    clientIds: Map<String, Int>,
    totalUnidentified: Int = 0,
    clipped: Boolean = false,
    totalClientIds: Int = clientIds.size,
): SummaryClientIdCounts = mockk {
    every { this@mockk.total } returns total
    every { this@mockk.clientIds } returns clientIds
    every { this@mockk.totalUnidentified } returns totalUnidentified
    every { this@mockk.clipped } returns clipped
    every { this@mockk.totalClientIds } returns totalClientIds
}

/**
 * Creates a mock PresenceMember for testing.
 *
 * @param clientId The client ID of the presence member.
 * @param data Optional data associated with the member.
 * @param updatedAt Timestamp when the member was last updated.
 * @param extras Optional extras associated with the member.
 * @return A mocked PresenceMember object.
 */
fun createMockPresenceMember(
    clientId: String = "user-1",
    data: JsonObject? = null,
    updatedAt: Long = System.currentTimeMillis(),
    extras: JsonObject = JsonObject(),
): PresenceMember = mockk {
    every { this@mockk.clientId } returns clientId
    every { this@mockk.data } returns data
    every { this@mockk.updatedAt } returns updatedAt
    every { this@mockk.extras } returns extras
}

/**
 * Creates a mock Room for testing.
 *
 * @param name The room name/ID.
 * @param clientId The current user's client ID.
 * @return A mocked Room object.
 */
@OptIn(InternalChatApi::class)
fun createMockRoom(
    name: String = "test-room",
    clientId: String = "current-user",
): Room = mockk(relaxed = true) {
    every { this@mockk.name } returns name
    every { this@mockk.clientId } returns clientId
}
