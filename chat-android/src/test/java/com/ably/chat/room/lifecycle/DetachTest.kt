package com.ably.chat.room.lifecycle

import com.ably.chat.ErrorCode
import com.ably.chat.HttpStatusCode
import com.ably.chat.RoomStatus
import com.ably.chat.RoomStatusChange
import com.ably.chat.assertWaiter
import com.ably.chat.detachCoroutine
import com.ably.chat.room.LifecycleManager
import com.ably.chat.room.StatusManager
import com.ably.chat.room.atomicCoroutineScope
import com.ably.chat.room.createRoomFeatureMocks
import com.ably.chat.room.createTestRoom
import com.ably.chat.room.isExplicitlyDetached
import com.ably.chat.serverError
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test

/**
 * Spec: CHA-RL1
 */
class DetachTest {

    @After
    fun tearDown() {
        unmockkStatic(RealtimeChannel::detachCoroutine)
    }

    @Test
    fun `(CHA-RL2a) Detach success when room is already in detached state`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = room.LifecycleManager
        val statusManager = room.StatusManager

        // Set room status to DETACHED
        statusManager.setStatus(RoomStatus.Detached)

        val result = kotlin.runCatching { roomLifecycle.detach() }
        Assert.assertTrue(result.isSuccess)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL2c) Detach throws exception when room in released state`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = room.LifecycleManager
        val statusManager = room.StatusManager

        // Set room status to RELEASED
        statusManager.setStatus(RoomStatus.Released)

        val exception = Assert.assertThrows(AblyException::class.java) {
            runBlocking {
                roomLifecycle.detach()
            }
        }
        Assert.assertEquals("unable to detach room; room is released", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.RoomIsReleased.code, exception.errorInfo.code)
        Assert.assertEquals(HttpStatusCode.BadRequest, exception.errorInfo.statusCode)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL2d) Detach throws exception when room is in failed state`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = room.LifecycleManager
        val statusManager = room.StatusManager
        // Set room status to FAILED
        statusManager.setStatus(RoomStatus.Failed)

        val exception = Assert.assertThrows(AblyException::class.java) {
            runBlocking {
                roomLifecycle.detach()
            }
        }
        Assert.assertEquals("cannot detach room, room is in failed state", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.RoomInFailedState.code, exception.errorInfo.code)
        Assert.assertEquals(HttpStatusCode.BadRequest, exception.errorInfo.statusCode)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL2i) Detach op should wait for existing operation as per (CHA-RL7)`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = spyk(room.LifecycleManager)
        val statusManager = room.StatusManager
        // Set room status to INITIALIZED
        Assert.assertEquals(RoomStatus.Initialized, room.status) // CHA-RS3

        val roomReleased = Channel<Boolean>()
        coEvery {
            roomLifecycle.release()
        } coAnswers {
            roomLifecycle.atomicCoroutineScope().async {
                statusManager.setStatus(RoomStatus.Releasing)
                roomReleased.receive()
                statusManager.setStatus(RoomStatus.Released)
            }
        }

        // Release op started from separate coroutine
        launch { roomLifecycle.release() }
        assertWaiter { !roomLifecycle.atomicCoroutineScope().finishedProcessing }
        Assert.assertEquals(0, roomLifecycle.atomicCoroutineScope().pendingJobCount) // no queued jobs, one job running
        assertWaiter { room.status == RoomStatus.Releasing }

        // Detach op started from separate coroutine
        val roomDetachOpDeferred = async(SupervisorJob()) { roomLifecycle.detach() }
        assertWaiter { roomLifecycle.atomicCoroutineScope().pendingJobCount == 1 } // detach op queued
        Assert.assertEquals(RoomStatus.Releasing, room.status)

        // Finish release op, so DETACH op can start
        roomReleased.send(true)
        assertWaiter { room.status == RoomStatus.Released }

        val result = kotlin.runCatching { roomDetachOpDeferred.await() }
        Assert.assertTrue(roomLifecycle.atomicCoroutineScope().finishedProcessing)

        Assert.assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AblyException

        Assert.assertEquals("unable to detach room; room is released", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.RoomIsReleased.code, exception.errorInfo.code)
        Assert.assertEquals(HttpStatusCode.BadRequest, exception.errorInfo.statusCode)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        coVerify { roomLifecycle.release() }
    }

    @Test
    fun `(CHA-RL2j) Detach op should transition room into DETACHING state`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = room.LifecycleManager
        val statusManager = room.StatusManager
        // Set room status to ATTACHED
        statusManager.setStatus(RoomStatus.Attached)

        mockkStatic(RealtimeChannel::detachCoroutine)
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {}

        val roomStatusChanges = mutableListOf<RoomStatusChange>()
        room.onStatusChange {
            roomStatusChanges.add(it)
        }
        roomLifecycle.detach()

        Assert.assertEquals(RoomStatus.Detaching, roomStatusChanges[0].current)
        Assert.assertEquals(RoomStatus.Detached, roomStatusChanges[1].current)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL2k, CHA-RL2k1) When detach op is a success, room enters detached state`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = room.LifecycleManager
        val statusManager = room.StatusManager
        // Set room status to ATTACHED
        statusManager.setStatus(RoomStatus.Attached)

        mockkStatic(RealtimeChannel::detachCoroutine)
        val capturedChannels = mutableListOf<RealtimeChannel>()
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            capturedChannels.add(firstArg())
        }

        val roomFeatures = createRoomFeatureMocks()
        Assert.assertEquals(5, roomFeatures.size)

        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)

        val result = kotlin.runCatching { roomLifecycle.detach() }
        Assert.assertTrue(result.isSuccess)

        Assert.assertEquals(1, capturedChannels.size)
        Assert.assertEquals("1234::\$chat", capturedChannels[0].name)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        // Channel is detached
        Assert.assertEquals(RoomStatus.Detached, room.status)
        Assert.assertTrue(roomLifecycle.isExplicitlyDetached)
    }

    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL2k1, CHA-RL2k3) When detach op is a failure (channel suspended), room enters suspended state and op returns error`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = room.LifecycleManager
        val statusManager = room.StatusManager
        // Set room status to ATTACHED
        statusManager.setStatus(RoomStatus.Attached)

        mockkStatic(RealtimeChannel::detachCoroutine)
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            // Throw error for channel detach
            val channel = firstArg<RealtimeChannel>()
            every { channel.state } returns ChannelState.suspended
            throw serverError("error detaching channel ${channel.name}")
        }

        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)
        val result = kotlin.runCatching { roomLifecycle.detach() }

        Assert.assertTrue(result.isFailure)
        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(RoomStatus.Suspended, room.status)

        val exception = result.exceptionOrNull() as AblyException
        Assert.assertEquals("failed to attach room: error detaching channel 1234::\$chat", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.InternalError.code, exception.errorInfo.code)
        Assert.assertEquals(500, exception.errorInfo.statusCode)
    }

    @Test
    fun `(CHA-RL2k1, CHA-RL2k3) When detach op is a failure (channel failed), room status becomes failed and returns error`() = runTest {
        val room = createTestRoom()
        val roomLifecycle = room.LifecycleManager
        val statusManager = room.StatusManager
        // Set room status to ATTACHED
        statusManager.setStatus(RoomStatus.Attached)

        mockkStatic(RealtimeChannel::detachCoroutine)
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            // Throw error for channel detach
            val channel = firstArg<RealtimeChannel>()
            every { channel.state } returns ChannelState.failed
            throw serverError("error detaching channel ${channel.name}")
        }

        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)
        val result = kotlin.runCatching { roomLifecycle.detach() }

        Assert.assertTrue(result.isFailure)
        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(RoomStatus.Failed, room.status)

        val exception = result.exceptionOrNull() as AblyException
        Assert.assertEquals("failed to attach room: error detaching channel 1234::\$chat", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.InternalError.code, exception.errorInfo.code)
        Assert.assertEquals(500, exception.errorInfo.statusCode)
    }
}
