package com.ably.chat.room.lifecycle

import com.ably.chat.DefaultStatusManager
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
import io.ably.lib.realtime.ChannelState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test

/**
 * Spec: CHA-RL3
 */
class ReleaseTest {
    private val logger = createMockLogger()

    private val roomScope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1) + CoroutineName("roomId"),
    )

    @After
    fun tearDown() {
        unmockkStatic(RealtimeChannel::attachCoroutine)
    }

    @Test
    fun `(CHA-RL3a) Release success when room is already in released state`() = runTest {
        val statusManager = spyk(DefaultStatusManager(logger)).apply {
            setStatus(RoomStatus.Released)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, createRoomFeatureMocks(), logger))
        val result = kotlin.runCatching { roomLifecycle.release() }
        Assert.assertTrue(result.isSuccess)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL3b) If room is in detached state, room is immediately transitioned to released`() = runTest {
        val statusManager = spyk(DefaultStatusManager(logger)).apply {
            setStatus(RoomStatus.Detached)
        }
        val states = mutableListOf<RoomStatusChange>()
        statusManager.onChange {
            states.add(it)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, createRoomFeatureMocks(), logger))

        val result = kotlin.runCatching { roomLifecycle.release() }
        Assert.assertTrue(result.isSuccess)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
        Assert.assertEquals(1, states.size)
        Assert.assertEquals(RoomStatus.Released, states[0].current)
        Assert.assertEquals(RoomStatus.Detached, states[0].previous)
    }

    @Test
    fun `(CHA-RL3j) If room is in initialized state, room is immediately transitioned to released`() = runTest {
        val statusManager = spyk(DefaultStatusManager(logger)).apply {
            setStatus(RoomStatus.Initialized)
        }
        val states = mutableListOf<RoomStatusChange>()
        statusManager.onChange {
            states.add(it)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, createRoomFeatureMocks(), logger))

        val result = kotlin.runCatching { roomLifecycle.release() }
        Assert.assertTrue(result.isSuccess)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
        Assert.assertEquals(1, states.size)
        Assert.assertEquals(RoomStatus.Released, states[0].current)
        Assert.assertEquals(RoomStatus.Initialized, states[0].previous)
    }

    @Test
    fun `(CHA-RL3k) Release op should wait for existing operation as per (CHA-RL7)`() = runTest {
        val statusManager = spyk(DefaultStatusManager(logger)).apply {
            setStatus(RoomStatus.Attached)
        }

        val roomEvents = mutableListOf<RoomStatusChange>()

        statusManager.onChange {
            roomEvents.add(it)
        }

        mockkStatic(RealtimeChannel::detachCoroutine)
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            val channel = firstArg<RealtimeChannel>()
            every { channel.state } returns ChannelState.detached
        }

        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, createRoomFeatureMocks(), logger))

        val roomAttached = Channel<Boolean>()
        coEvery {
            roomLifecycle.attach()
        } coAnswers {
            roomLifecycle.atomicCoroutineScope().async {
                statusManager.setStatus(RoomStatus.Attaching)
                roomAttached.receive()
                statusManager.setStatus(RoomStatus.Attached)
            }
        }

        // ATTACH op started from separate coroutine
        launch { roomLifecycle.attach() }
        assertWaiter { !roomLifecycle.atomicCoroutineScope().finishedProcessing }
        Assert.assertEquals(0, roomLifecycle.atomicCoroutineScope().pendingJobCount) // no queued jobs, one job running
        assertWaiter { statusManager.status == RoomStatus.Attaching }

        // Release op started from separate coroutine
        val roomReleaseOpDeferred = async { roomLifecycle.release() }
        assertWaiter { roomLifecycle.atomicCoroutineScope().pendingJobCount == 1 } // release op queued
        Assert.assertEquals(RoomStatus.Attaching, statusManager.status)

        // Finish room ATTACH
        roomAttached.send(true)

        val result = kotlin.runCatching { roomReleaseOpDeferred.await() }
        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals(RoomStatus.Released, statusManager.status)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(4, roomEvents.size)
        Assert.assertEquals(RoomStatus.Attaching, roomEvents[0].current)
        Assert.assertEquals(RoomStatus.Attached, roomEvents[1].current)
        Assert.assertEquals(RoomStatus.Releasing, roomEvents[2].current)
        Assert.assertEquals(RoomStatus.Released, roomEvents[3].current)

        coVerify { roomLifecycle.attach() }
    }

    @Test
    fun `(CHA-RL3m) Release op should transition room into RELEASING state`() = runTest {
        val statusLifecycle = spyk(DefaultStatusManager(logger)).apply {
            setStatus(RoomStatus.Attached)
        }
        val roomStatusChanges = mutableListOf<RoomStatusChange>()
        statusLifecycle.onChange {
            roomStatusChanges.add(it)
        }

        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusLifecycle, emptyList(), logger), recordPrivateCalls = true)

        roomLifecycle.release()
        Assert.assertEquals(RoomStatus.Releasing, roomStatusChanges[0].current)
        Assert.assertEquals(RoomStatus.Released, roomStatusChanges[1].current)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL3n2) Release op should detach room channel and room should be considered RELEASED`() =
        runTest {
            val statusLifecycle = spyk(DefaultStatusManager(logger)).apply {
                setStatus(RoomStatus.Attached)
            }

            mockkStatic(RealtimeChannel::detachCoroutine)
            val capturedChannels = mutableListOf<RealtimeChannel>()
            coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
                capturedChannels.add(firstArg())
            }

            val contributors = createRoomFeatureMocks()
            Assert.assertEquals(5, contributors.size)

            val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusLifecycle, contributors, logger))
            val result = kotlin.runCatching { roomLifecycle.release() }
            Assert.assertTrue(result.isSuccess)
            Assert.assertEquals(RoomStatus.Released, statusLifecycle.status)

            Assert.assertEquals(1, capturedChannels.size)
            Assert.assertEquals("1234::\$chat", capturedChannels[0].name)

            assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
        }

    @Test
    fun `(CHA-RL3n3) If channel detach enters failed state, release op finishes with released state`() = runTest {
        val statusLifecycle = spyk(DefaultStatusManager(logger)).apply {
            setStatus(RoomStatus.Attached)
        }

        mockkStatic(RealtimeChannel::detachCoroutine)
        val capturedChannels = mutableListOf<RealtimeChannel>()
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            val channel = firstArg<RealtimeChannel>()
            capturedChannels.add(channel)
            every { channel.state } returns ChannelState.failed
            error("failed to detach channel")
        }

        val contributors = createRoomFeatureMocks("1234")

        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusLifecycle, contributors, logger), recordPrivateCalls = true)

        val result = kotlin.runCatching { roomLifecycle.release() }
        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals(RoomStatus.Released, statusLifecycle.status)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(1, capturedChannels.size)
        Assert.assertEquals("1234::\$chat", capturedChannels[0].name)
        Assert.assertEquals(ChannelState.failed, roomLifecycle.channel.state)
    }

    @Test
    fun `(CHA-RL3n4) If channel detach fails with other state (other than failed), channel detach retried after 250ms delay`() = runTest {
        val statusLifecycle = spyk(DefaultStatusManager(logger)).apply {
            setStatus(RoomStatus.Attached)
        }

        val roomEvents = mutableListOf<RoomStatusChange>()
        statusLifecycle.onChange {
            roomEvents.add(it)
        }

        mockkStatic(RealtimeChannel::detachCoroutine)
        var failDetachTimes = 5
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            delay(200)
            if (--failDetachTimes >= 0) {
                error("failed to detach channel")
            }
            val channel = firstArg<RealtimeChannel>()
            every { channel.state } returns ChannelState.detached
        }

        val contributors = createRoomFeatureMocks("1234")
        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusLifecycle, contributors, logger), recordPrivateCalls = true)

        val result = kotlin.runCatching { roomLifecycle.release() }
        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals(RoomStatus.Released, statusLifecycle.status)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertEquals(2, roomEvents.size)
        Assert.assertEquals(RoomStatus.Releasing, roomEvents[0].current)
        Assert.assertEquals(RoomStatus.Released, roomEvents[1].current)

        // Channel release success on 6th call
        coVerify(exactly = 6) {
            any<RealtimeChannel>().detachCoroutine()
        }
    }

    @Test
    fun `(CHA-RL3n) Release op continues till channel enters either DETACHED or FAILED state`() = runTest {
        val statusLifecycle = spyk(DefaultStatusManager(logger)).apply {
            setStatus(RoomStatus.Attached)
        }

        mockkStatic(RealtimeChannel::detachCoroutine)
        var failDetachTimes = 5
        val capturedChannels = mutableListOf<RealtimeChannel>()
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            delay(200)
            val channel = firstArg<RealtimeChannel>()
            if (--failDetachTimes >= 0) {
                every { channel.state } returns listOf(ChannelState.attached, ChannelState.suspended).random()
                error("failed to detach channel")
            }
            every { channel.state } returns listOf(ChannelState.detached, ChannelState.failed).random()
            capturedChannels.add(channel)
        }

        val contributors = createRoomFeatureMocks()
        Assert.assertEquals(5, contributors.size)

        val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusLifecycle, contributors, logger), recordPrivateCalls = true)
        roomLifecycle.release()
        Assert.assertEquals(RoomStatus.Released, statusLifecycle.status)

        Assert.assertEquals(1, capturedChannels.size)
        Assert.assertEquals("1234::\$chat", capturedChannels[0].name)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        // Channel release success on 6th call
        coVerify(exactly = 6) {
            any<RealtimeChannel>().detachCoroutine()
        }
    }

    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL3h) Upon channel release, underlying room features are released from the core SDK to prevent leakage`() =
        runTest {
            val statusManager = spyk(DefaultStatusManager(logger)).apply {
                setStatus(RoomStatus.Attached)
            }

            mockkStatic(RealtimeChannel::detachCoroutine)
            coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
                val channel = firstArg<RealtimeChannel>()
                every { channel.state } returns ChannelState.detached
            }

            val contributors = createRoomFeatureMocks()
            Assert.assertEquals(5, contributors.size)

            val releasedFeatures = mutableListOf<String>()
            for (contributor in contributors) {
                every { contributor.release() } answers {
                    releasedFeatures.add(contributor.featureName)
                }
            }

            val roomLifecycle = spyk(RoomLifecycleManager(createMockRoom(), roomScope, statusManager, contributors, logger))
            val result = kotlin.runCatching { roomLifecycle.release() }
            Assert.assertTrue(result.isSuccess)
            Assert.assertEquals(RoomStatus.Released, statusManager.status)

            Assert.assertEquals(5, releasedFeatures.size)
            repeat(5) {
                Assert.assertEquals(contributors[it].featureName, releasedFeatures[it])
            }
            Assert.assertEquals("messages", releasedFeatures[0])
            Assert.assertEquals("presence", releasedFeatures[1])
            Assert.assertEquals("occupancy", releasedFeatures[2])
            Assert.assertEquals("typing", releasedFeatures[3])
            Assert.assertEquals("reactions", releasedFeatures[4])

            assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

            for (contributor in contributors) {
                verify(exactly = 1) {
                    contributor.release()
                }
            }
        }
}
