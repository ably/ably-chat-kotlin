package com.ably.chat.room.lifecycle

import com.ably.chat.DefaultRoomStatusManager
import com.ably.chat.RoomLifecycleManager
import com.ably.chat.RoomStatus
import com.ably.chat.RoomStatusChange
import com.ably.chat.assertWaiter
import com.ably.chat.attachCoroutine
import com.ably.chat.detachCoroutine
import com.ably.chat.room.atomicCoroutineScope
import com.ably.chat.room.createMockLogger
import com.ably.chat.room.createMockRoom
import com.ably.chat.room.createRoomFeatureMocks
import com.ably.pubsub.RealtimeChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert
import org.junit.Test

/**
 * Room lifecycle operations are atomic and exclusive operations: one operation must complete (whether that’s failure or success) before the next one may begin.
 * Spec: CHA-RL7
 */
class PrecedenceTest {
    private val logger = createMockLogger()

    private val roomScope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1) + CoroutineName("roomId"),
    )

    /**
     * 1. RELEASE (CHA-RL7a2) - External operation.
     * 2. ATTACH or DETACH (CHA-RL7a3) - External operation.
     */
    @Suppress("LongMethod")
    @Test
    fun `(CHA-RL7a) If multiple operations are scheduled to run, they run as per LifecycleOperationPrecedence`() = runTest {
        val statusManager = spyk(DefaultRoomStatusManager(logger))
        val roomStatusChanges = mutableListOf<RoomStatusChange>()
        statusManager.onChange {
            roomStatusChanges.add(it)
        }

        val contributors = createRoomFeatureMocks("1234")
        Assert.assertEquals(5, contributors.size)

        val roomLifecycle = spyk(
            RoomLifecycleManager(createMockRoom(), roomScope, statusManager, contributors, logger),
            recordPrivateCalls = true,
        )

        // Attach operation
        mockkStatic(RealtimeChannel::attachCoroutine)
        coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers {
            delay(500)
        }

        // Detach operation
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            delay(500)
        }

        // Release operation
        coEvery { roomLifecycle invokeNoArgs "retryUntilChannelDetachedOrFailed" } coAnswers {
            delay(200)
        }

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            launch {
                roomLifecycle.attach()
            }
            assertWaiter { !roomLifecycle.atomicCoroutineScope().finishedProcessing } // Get attach into processing
            launch {
                kotlin.runCatching { roomLifecycle.detach() } // Attach in process, Queue -> Detach
            }
            launch {
                kotlin.runCatching { roomLifecycle.attach() } // Attach in process, Queue ->  Detach, Attach
            }
            // Because of release, detach and attach won't be able to execute their operations
            launch {
                roomLifecycle.release() // Attach in process, Queue -> Release, Detach, Attach
            }
        }

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(4, roomStatusChanges.size)
        Assert.assertEquals(RoomStatus.Attaching, roomStatusChanges[0].current)
        Assert.assertEquals(RoomStatus.Attached, roomStatusChanges[1].current)
        Assert.assertEquals(RoomStatus.Releasing, roomStatusChanges[2].current)
        Assert.assertEquals(RoomStatus.Released, roomStatusChanges[3].current)

        coVerify {
            any<RealtimeChannel>().attachCoroutine()
            roomLifecycle invokeNoArgs "retryUntilChannelDetachedOrFailed"
        }

        coVerify(exactly = 0) {
            any<RealtimeChannel>().detachCoroutine()
        }
    }
}
