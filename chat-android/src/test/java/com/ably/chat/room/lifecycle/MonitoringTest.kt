package com.ably.chat.room.lifecycle

import com.ably.annotations.InternalAPI
import com.ably.chat.DefaultRoom
import com.ably.chat.ErrorCode
import com.ably.chat.RoomStatus
import com.ably.chat.assertWaiter
import com.ably.chat.attachCoroutine
import com.ably.chat.detachCoroutine
import com.ably.chat.room.EventCompletionDeferred
import com.ably.chat.room.LifecycleManager
import com.ably.chat.room.StatusManager
import com.ably.chat.room.atomicCoroutineScope
import com.ably.chat.room.channelStateToRoomStatus
import com.ably.chat.room.constructChannelStateChangeEvent
import com.ably.chat.room.createMockRealtimeChannel
import com.ably.chat.room.createMockRealtimeClient
import com.ably.chat.room.createTestRoom
import com.ably.chat.room.hasAttachedOnce
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ErrorInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MonitoringTest {

    private val channelStateListeners = mutableListOf<ChannelStateListener>()
    private lateinit var room: DefaultRoom
    private val lifecycleListener get() = channelStateListeners.last()

    @OptIn(InternalAPI::class)
    @Before
    fun setUp() {
        val sharedChannel = createMockRealtimeChannel()
        val javaChannel = sharedChannel.javaChannel
        every { javaChannel.on(capture(channelStateListeners)) } returns mockk()
        every { javaChannel.off(any()) } returns Unit

        val realtimeClient = createMockRealtimeClient()
        every {
            realtimeClient.channels.get(any<String>(), any<ChannelOptions>())
        } answers {
            sharedChannel
        }
        every { realtimeClient.channels.release(any<String>()) } returns Unit

        room = createTestRoom("room1", realtimeClient = realtimeClient)
    }

    @After
    fun tearDown() {
        unmockkStatic(RealtimeChannel::attachCoroutine, RealtimeChannel::detachCoroutine)
    }

    @Test
    @Suppress("MaximumLineLength")
    fun `(CHA-RL11a, CHA-RL11b) If a room lifecycle operation is in progress and a channel state change is received, the operation is no-op`() = runTest {
        val roomLifecycle = spyk(room.LifecycleManager)
        val statusManager = room.StatusManager
        val stateChanges = mutableListOf<RoomStatus>()
        room.onStatusChange {
            stateChanges.add(it.current)
        }

        // Check Room.status to be Initialized
        Assert.assertEquals(RoomStatus.Initialized, room.status) // CHA-RS3

        val roomReleased = Channel<Boolean>()
        coEvery {
            roomLifecycle.attach()
        } coAnswers {
            roomLifecycle.atomicCoroutineScope().async {
                statusManager.setStatus(RoomStatus.Attaching)
                roomReleased.receive()
                roomLifecycle.EventCompletionDeferred.get().await()
                statusManager.setStatus(RoomStatus.Attached)
            }
        }

        // Release op started from separate coroutine
        launch { roomLifecycle.attach() }
        assertWaiter { !roomLifecycle.atomicCoroutineScope().finishedProcessing }
        Assert.assertEquals(0, roomLifecycle.atomicCoroutineScope().pendingJobCount) // no queued jobs, one job running
        assertWaiter { room.status == RoomStatus.Attaching }

        // Emit state change event
        lifecycleListener.onChannelStateChanged(constructChannelStateChangeEvent(ChannelState.detaching, ChannelState.attaching))
        lifecycleListener.onChannelStateChanged(constructChannelStateChangeEvent(ChannelState.detached, ChannelState.detaching))

        // Complete ongoing lifecycle op
        roomReleased.send(true)
        assertWaiter { room.status == RoomStatus.Attached }
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(2, stateChanges.size)
        Assert.assertEquals(RoomStatus.Attaching, stateChanges[0])
        Assert.assertEquals(RoomStatus.Attached, stateChanges[1])
    }

    @Test
    @Suppress("MaximumLineLength")
    fun `(CHA-RL11c) If a room lifecycle operation is not in progress, then room status is set in accordance with channel state`() = runTest {
        val roomLifecycle = spyk(room.LifecycleManager)
        val stateChanges = mutableListOf<RoomStatus>()
        room.onStatusChange {
            stateChanges.add(it.current)
        }
        // Check Room.status to be Initialized
        Assert.assertEquals(RoomStatus.Initialized, room.status) // CHA-RS3

        mockkStatic(RealtimeChannel::attachCoroutine, RealtimeChannel::detachCoroutine)
        coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers {}
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {}

        val attachResult = runCatching { roomLifecycle.attach() }
        Assert.assertTrue(attachResult.isSuccess)
        Assert.assertEquals(RoomStatus.Attached, room.status) // CHA-RS3
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        // Emit state change event
        lifecycleListener.onChannelStateChanged(constructChannelStateChangeEvent(ChannelState.detaching, ChannelState.attaching))
        lifecycleListener.onChannelStateChanged(constructChannelStateChangeEvent(ChannelState.detached, ChannelState.detaching))

        // Waits for events to be processed
        assertWaiter { room.status == RoomStatus.Detached }

        val releaseResult = runCatching { roomLifecycle.release() }
        Assert.assertTrue(releaseResult.isSuccess)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(5, stateChanges.size)
        Assert.assertEquals(RoomStatus.Attaching, stateChanges[0])
        Assert.assertEquals(RoomStatus.Attached, stateChanges[1])
        Assert.assertEquals(RoomStatus.Detaching, stateChanges[2])
        Assert.assertEquals(RoomStatus.Detached, stateChanges[3])
        Assert.assertEquals(RoomStatus.Released, stateChanges[4]) // CHA-RS3
    }

    @Test
    fun `(CHA-RL15, CHA-RL12a, CHA-RL12b) If channel attach event with resume as false received, then discontinuity event is emitted`() =
        runTest {
            val roomLifecycle = room.LifecycleManager
            val discontinuityDeferred = CompletableDeferred<ErrorInfo>()

            // Check Room.status to be Initialized
            Assert.assertEquals(RoomStatus.Initialized, room.status) // CHA-RS3

            mockkStatic(RealtimeChannel::attachCoroutine)
            coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers {}

            val attachResult = runCatching { roomLifecycle.attach() }
            Assert.assertTrue(attachResult.isSuccess)
            Assert.assertEquals(RoomStatus.Attached, room.status)
            Assert.assertTrue(roomLifecycle.hasAttachedOnce)

            // listen to discontinuity
            room.onDiscontinuity {
                discontinuityDeferred.complete(it)
            }

            val channelDiscontinuityEvent = constructChannelStateChangeEvent(
                ChannelState.attached,
                ChannelState.attached,
                ErrorInfo("publish rate limit exceeded", ErrorCode.InternalError.code),
            )
            lifecycleListener.onChannelStateChanged(channelDiscontinuityEvent)

            val discontinuity = discontinuityDeferred.await()
            Assert.assertEquals(discontinuity.message, "Discontinuity detected, publish rate limit exceeded")
            Assert.assertEquals(discontinuity.code, ErrorCode.RoomDiscontinuity.code)
        }

    @Test
    fun `All channel states should be mapped to room status`() = runTest {
        val roomLifecycle = room.LifecycleManager
        ChannelState.entries.forEach { channelState ->
            val roomStatus = roomLifecycle.channelStateToRoomStatus(channelState)
            val expectedRoomStatus = when (channelState) {
                ChannelState.initialized -> RoomStatus.Initialized
                ChannelState.attaching -> RoomStatus.Attaching
                ChannelState.attached -> RoomStatus.Attached
                ChannelState.detaching -> RoomStatus.Detaching
                ChannelState.detached -> RoomStatus.Detached
                ChannelState.failed -> RoomStatus.Failed
                ChannelState.suspended -> RoomStatus.Suspended
                else -> null
            }
            Assert.assertEquals(expectedRoomStatus, roomStatus)
        }
    }
}
