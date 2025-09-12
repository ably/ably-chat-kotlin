package com.ably.chat.room.lifecycle

import com.ably.chat.RoomStatus
import com.ably.chat.RoomStatusChange
import com.ably.chat.assertWaiter
import com.ably.chat.attachCoroutine
import com.ably.chat.detachCoroutine
import com.ably.chat.room.LifecycleManager
import com.ably.chat.room.atomicCoroutineScope
import com.ably.chat.room.createRoomFeatureMocks
import com.ably.chat.room.createTestRoom
import com.ably.pubsub.RealtimeChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.spyk
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

    /**
     * 1. RELEASE (CHA-RL7a2) - External operation.
     * 2. ATTACH or DETACH (CHA-RL7a3) - External operation.
     */
    @Suppress("LongMethod")
    @Test
    fun `(CHA-RL7a) If multiple operations are scheduled to run, they run as per LifecycleOperationPrecedence`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = spyk(room.LifecycleManager, recordPrivateCalls = true)

        val roomStatusChanges = mutableListOf<RoomStatusChange>()
        room.onStatusChange {
            roomStatusChanges.add(it)
        }

        val roomFeatures = createRoomFeatureMocks("1234")
        Assert.assertEquals(5, roomFeatures.size)

        // Attach operation
        mockkStatic(RealtimeChannel::attachCoroutine, RealtimeChannel::detachCoroutine)
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
