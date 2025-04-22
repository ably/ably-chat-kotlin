package com.ably.chat.room.lifecycle

import com.ably.chat.DefaultRoomStatusManager
import com.ably.chat.ErrorCode
import com.ably.chat.HttpStatusCode
import com.ably.chat.RoomLifecycleManager
import com.ably.chat.RoomStatus
import com.ably.chat.RoomStatusChange
import com.ably.chat.assertWaiter
import com.ably.chat.attachCoroutine
import com.ably.chat.room.atomicCoroutineScope
import com.ably.chat.room.createMockLogger
import com.ably.chat.room.createMockRoom
import com.ably.chat.room.createRoomFeatureMocks
import com.ably.chat.room.hasAttachedOnce
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
class AttachTest {

    private val logger = createMockLogger()

    private val roomScope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1) + CoroutineName("roomId"),
    )

    @After
    fun tearDown() {
        unmockkStatic(RealtimeChannel::attachCoroutine)
    }

    @Test
    fun `(CHA-RL1a) Attach success when room is already in attached state`() = runTest {
        val statusManager = spyk(DefaultRoomStatusManager(logger)).apply {
            setStatus(RoomStatus.Attached)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, createRoomFeatureMocks(), logger))
        val result = kotlin.runCatching { roomLifecycle.attach() }
        Assert.assertTrue(result.isSuccess)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL1c) Attach throws exception when room in released state`() = runTest {
        val statusManager = spyk(DefaultRoomStatusManager(logger)).apply {
            setStatus(RoomStatus.Released)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, listOf(), logger))
        val exception = Assert.assertThrows(AblyException::class.java) {
            runBlocking {
                roomLifecycle.attach()
            }
        }
        Assert.assertEquals("unable to attach room; room is released", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.RoomIsReleased.code, exception.errorInfo.code)
        Assert.assertEquals(HttpStatusCode.BadRequest, exception.errorInfo.statusCode)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL1d) Attach op should wait for existing operation as per (CHA-RL7)`() = runTest {
        val statusManager = spyk(DefaultRoomStatusManager(logger))
        Assert.assertEquals(RoomStatus.Initialized, statusManager.status) // CHA-RS3

        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, createRoomFeatureMocks(), logger))

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
        assertWaiter { statusManager.status == RoomStatus.Releasing }

        // Attach op started from separate coroutine
        val roomAttachOpDeferred = async(SupervisorJob()) { roomLifecycle.attach() }
        assertWaiter { roomLifecycle.atomicCoroutineScope().pendingJobCount == 1 } // attach op queued
        Assert.assertEquals(RoomStatus.Releasing, statusManager.status)

        // Finish release op, so ATTACH op can start
        roomReleased.send(true)
        assertWaiter { statusManager.status == RoomStatus.Released }

        val result = kotlin.runCatching { roomAttachOpDeferred.await() }
        Assert.assertTrue(roomLifecycle.atomicCoroutineScope().finishedProcessing)

        Assert.assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AblyException

        Assert.assertEquals("unable to attach room; room is released", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.RoomIsReleased.code, exception.errorInfo.code)
        Assert.assertEquals(HttpStatusCode.BadRequest, exception.errorInfo.statusCode)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        coVerify { roomLifecycle.release() }
    }

    @Test
    fun `(CHA-RL1e) Attach op should transition room into ATTACHING state`() = runTest {
        val statusManager = spyk(DefaultRoomStatusManager(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers { }

        val roomStatusChanges = mutableListOf<RoomStatusChange>()
        statusManager.onChange {
            roomStatusChanges.add(it)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, emptyList(), logger))
        roomLifecycle.attach()

        Assert.assertEquals(RoomStatus.Attaching, roomStatusChanges[0].current)
        Assert.assertEquals(RoomStatus.Attached, roomStatusChanges[1].current)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL1k, CHA-RL1k1) When attach op is a success, room enters attached state`() = runTest {
        val statusManager = spyk(DefaultRoomStatusManager(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        val capturedChannels = mutableListOf<RealtimeChannel>()
        coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers {
            capturedChannels.add(firstArg())
        }

        val contributors = createRoomFeatureMocks()
        Assert.assertEquals(5, contributors.size)

        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, contributors, logger))
        Assert.assertFalse(roomLifecycle.hasAttachedOnce)
        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)

        val result = kotlin.runCatching { roomLifecycle.attach() }
        Assert.assertTrue(result.isSuccess)

        Assert.assertEquals(1, capturedChannels.size)
        Assert.assertEquals("1234::\$chat", capturedChannels[0].name)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        // Channel is attached
        Assert.assertEquals(RoomStatus.Attached, statusManager.status)
        Assert.assertTrue(roomLifecycle.hasAttachedOnce)
        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)
    }

    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL1k2, CHA-RL1k3) When attach op is a failure (channel suspended), room enters suspended state and op returns error`() = runTest {
        val statusManager = spyk(DefaultRoomStatusManager(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers {
            // Throw error for channel attach
            val channel = firstArg<RealtimeChannel>()
            every { channel.state } returns ChannelState.suspended
            throw serverError("error attaching channel ${channel.name}")
        }

        val contributors = createRoomFeatureMocks("1234")
        val roomLifecycle =
            spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, contributors, logger), recordPrivateCalls = true)

        Assert.assertFalse(roomLifecycle.hasAttachedOnce)
        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)

        val result = kotlin.runCatching { roomLifecycle.attach() }

        Assert.assertTrue(result.isFailure)
        Assert.assertFalse(roomLifecycle.hasAttachedOnce)
        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(RoomStatus.Suspended, statusManager.status)

        val exception = result.exceptionOrNull() as AblyException
        Assert.assertEquals("failed to attach room: error attaching channel 1234::\$chat", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.InternalError.code, exception.errorInfo.code)
        Assert.assertEquals(HttpStatusCode.InternalServerError, exception.errorInfo.statusCode)
    }

    @Test
    fun `(CHA-RL1k2, CHA-RL1k3) When attach op is a failure (channel failed), room status becomes failed and returns error`() = runTest {
        val statusManager = spyk(DefaultRoomStatusManager(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers {
            // Throw error for channel attach
            val channel = firstArg<RealtimeChannel>()
            every { channel.state } returns ChannelState.failed
            throw serverError("error attaching channel ${channel.name}")
        }

        val contributors = createRoomFeatureMocks("1234")
        val roomLifecycle =
            spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, contributors, logger), recordPrivateCalls = true)

        Assert.assertFalse(roomLifecycle.hasAttachedOnce)
        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)

        val result = kotlin.runCatching { roomLifecycle.attach() }

        Assert.assertTrue(result.isFailure)
        Assert.assertFalse(roomLifecycle.hasAttachedOnce)
        Assert.assertFalse(roomLifecycle.isExplicitlyDetached)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(RoomStatus.Failed, statusManager.status)

        val exception = result.exceptionOrNull() as AblyException
        Assert.assertEquals("failed to attach room: error attaching channel 1234::\$chat", exception.errorInfo.message)
        Assert.assertEquals(ErrorCode.InternalError.code, exception.errorInfo.code)
        Assert.assertEquals(HttpStatusCode.InternalServerError, exception.errorInfo.statusCode)
    }
}
