package com.ably.chat.integration

import com.ably.chat.MainDispatcherRule
import com.ably.chat.MessageReactionSummaryEvent
import com.ably.chat.MessageReactionType
import com.ably.chat.MessagesReactions
import com.ably.chat.RetryTestRule
import com.ably.chat.Room
import com.ably.chat.Subscription
import com.ably.chat.get
import com.ably.chat.messages
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class MessageReactionsIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val retryTestRule = RetryTestRule(3)

    /**
     * Spec: CHA-MR4, CHA-MR7
     */
    @Test
    fun `should correctly send message reaction`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomId = UUID.randomUUID().toString()
        val room = chatClient.rooms.get(roomId) {
            messages {
                rawMessageReactions = true
            }
        }

        room.attach()

        val message = room.messages.send("test")

        val receiveReactionsDeferred = waitForRawReactions(room, 4)

        listOf("like", "smile", "like", "heart").forEach { reactionName ->
            room.messages.reactions.send(message.serial, reactionName)
        }

        val receivedReactions = receiveReactionsDeferred.await()

        assertEquals(listOf("like", "smile", "like", "heart"), receivedReactions)
    }

    /**
     * Spec: CHA-MR11, CHA-MR6
     */
    @Test
    fun `should correctly delete message reaction`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomId = UUID.randomUUID().toString()
        val room = chatClient.rooms.get(roomId)
        room.attach()
        val message = room.messages.send("test")

        val event1Deferred = room.messages.reactions.subscribeOnce()
        room.messages.reactions.send(message.serial, "like")
        val event1 = event1Deferred.await()
        assertEquals(message.serial, event1.summary.messageSerial)
        assertEquals(1, event1.summary.distinct["like"]?.total)
        assertEquals(listOf("sandbox-client"), event1.summary.distinct["like"]?.clientIds)

        val event2Deferred = room.messages.reactions.subscribeOnce()
        room.messages.reactions.delete(message.serial, "like")
        val event2 = event2Deferred.await()
        assertEquals(message.serial, event2.summary.messageSerial)
        assertEquals(null, event2.summary.distinct["like"])
    }

    /**
     * Spec: CHA-MR11, CHA-MR6
     */
    @Test
    fun `should delete with multiple summary type`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomId = UUID.randomUUID().toString()
        val room = chatClient.rooms.get(roomId)
        room.attach()
        val message = room.messages.send("test")

        val event1Deferred = room.messages.reactions.subscribeOnce()
        room.messages.reactions.send(message.serial, "like", MessageReactionType.Multiple)
        val event1 = event1Deferred.await()
        assertEquals(1, event1.summary.multiple["like"]?.total)

        val event2 = room.messages.reactions.subscribeOnce()
        room.messages.reactions.send(message.serial, "like", MessageReactionType.Multiple, 2)
        assertEquals(3, event2.await().summary.multiple["like"]?.total)

        val event3Deferred = room.messages.reactions.subscribeOnce()
        room.messages.reactions.delete(message.serial, "like", MessageReactionType.Multiple)
        val event3 = event3Deferred.await()
        assertEquals(message.serial, event3.summary.messageSerial)
        assertEquals(null, event3.summary.multiple["like"])
    }

    /**
     * Spec: CHA-MR5
     */
    @Test
    fun `should use unique summary type`() = runTest {
        val chatClient = sandbox.createSandboxChatClient()
        val roomId = UUID.randomUUID().toString()
        val room = chatClient.rooms.get(roomId) {
            messages {
                defaultMessageReactionType = MessageReactionType.Unique
            }
        }

        room.attach()

        val message = room.messages.send("test")

        val event1Deferred = room.messages.reactions.subscribeOnce()
        room.messages.reactions.send(message.serial, "like")
        val event1 = event1Deferred.await()
        assertEquals(message.serial, event1.summary.messageSerial)
        assertEquals(1, event1.summary.unique["like"]?.total)
        assertEquals(listOf("sandbox-client"), event1.summary.unique["like"]?.clientIds)
    }

    private fun waitForRawReactions(room: Room, size: Int): CompletableDeferred<List<String>> {
        val messageEvent = CompletableDeferred<List<String>>()
        val receivedReactions = mutableListOf<String>()
        lateinit var subscription: Subscription
        subscription = room.messages.reactions.subscribeRaw { event ->
            receivedReactions.add(event.reaction.name)
            if (receivedReactions.size == size) {
                subscription.unsubscribe()
                messageEvent.complete(receivedReactions)
            }
        }
        return messageEvent
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

private fun MessagesReactions.subscribeOnce(): CompletableDeferred<MessageReactionSummaryEvent> {
    val deferredEvent = CompletableDeferred<MessageReactionSummaryEvent>()
    lateinit var subscription: Subscription
    subscription = subscribe { event ->
        subscription.unsubscribe()
        deferredEvent.complete(event)
    }
    return deferredEvent
}
