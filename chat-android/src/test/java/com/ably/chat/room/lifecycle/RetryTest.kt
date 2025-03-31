package com.ably.chat.room.lifecycle

import com.ably.annotations.InternalAPI
import com.ably.chat.DefaultRoomLifecycle
import com.ably.chat.HttpStatusCode
import com.ably.chat.RoomLifecycleManager
import com.ably.chat.RoomStatus
import com.ably.chat.assertWaiter
import com.ably.chat.attachCoroutine
import com.ably.chat.detachCoroutine
import com.ably.chat.room.atomicCoroutineScope
import com.ably.chat.room.createMockLogger
import com.ably.chat.room.createRoomFeatureMocks
import com.ably.chat.room.retry
import com.ably.chat.room.setState
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test

/**
 * Spec: CHA-RL5
 */
class RetryTest {
    private val logger = createMockLogger()

    private val roomScope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1) + CoroutineName("roomId"),
    )

    @After
    fun tearDown() {
        unmockkStatic(RealtimeChannel::attachCoroutine)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `(CHA-RL5a) Retry detaches all contributors except the one that's provided (based on underlying channel CHA-RL5a)`() = runTest {
        val statusLifecycle = spyk(DefaultRoomLifecycle(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        coJustRun { any<RealtimeChannel>().attachCoroutine() }

        val capturedDetachedChannels = mutableListOf<RealtimeChannel>()
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            capturedDetachedChannels.add(firstArg())
        }

        val contributors = createRoomFeatureMocks()
        Assert.assertEquals(5, contributors.size)
        val messagesContributor = contributors.first { it.featureName == "messages" }
        val channel = messagesContributor.channelWrapper
        every { channel.state } returns ChannelState.attached
        channel.javaChannel.setState(ChannelState.attached)

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, statusLifecycle, contributors, logger))

        val result = kotlin.runCatching { roomLifecycle.retry(messagesContributor) }
        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals(RoomStatus.Attached, statusLifecycle.status)

        Assert.assertEquals(2, capturedDetachedChannels.size)

        Assert.assertEquals("1234::\$chat", capturedDetachedChannels[0].name)
        Assert.assertEquals("1234::\$chat::\$reactions", capturedDetachedChannels[1].name)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @OptIn(InternalAPI::class)
    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL5c) If one of the contributor channel goes into failed state during channel windown (CHA-RL5a), then the room enters failed state and retry operation stops`() = runTest {
        val statusLifecycle = spyk(DefaultRoomLifecycle(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        coJustRun { any<RealtimeChannel>().attachCoroutine() }

        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            val channel = firstArg<RealtimeChannel>()
            if (channel.name.contains("typing")) {
                every { channel.state } returns ChannelState.failed
                channel.javaChannel.setState(ChannelState.failed)
                error("${channel.name} went into FAILED state")
            }
        }

        val contributors = createRoomFeatureMocks()

        val messagesContributor = contributors.first { it.featureName == "messages" }
        val channel = messagesContributor.channelWrapper
        every { channel.state } returns ChannelState.attached
        messagesContributor.channelWrapper.javaChannel.setState(ChannelState.failed)

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, statusLifecycle, contributors, logger))

        val result = kotlin.runCatching { roomLifecycle.retry(messagesContributor) }
        Assert.assertTrue(result.isFailure)
        Assert.assertEquals(RoomStatus.Failed, statusLifecycle.status)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL5c) If one of the contributor channel goes into failed state during Retry, then the room enters failed state and retry operation stops`() = runTest {
        val statusLifecycle = spyk(DefaultRoomLifecycle(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        coJustRun { any<RealtimeChannel>().detachCoroutine() }

        coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers {
            val channel = firstArg<RealtimeChannel>()
            if (channel.name.contains("typing")) {
                every { channel.state } returns ChannelState.failed
                error("${channel.name} went into FAILED state")
            }
        }

        val contributors = createRoomFeatureMocks()

        val messagesContributor = contributors.first { it.featureName == "messages" }
        val channel = messagesContributor.channelWrapper
        every { channel.state } returns ChannelState.attached

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, statusLifecycle, contributors, logger))

        roomLifecycle.retry(messagesContributor)
        Assert.assertEquals(RoomStatus.Failed, statusLifecycle.status)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @OptIn(InternalAPI::class)
    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL5d) If all contributor channels goes into detached (except one provided in suspended state), provided contributor starts attach operation and waits for ATTACHED or FAILED state`() = runTest {
        val statusLifecycle = spyk(DefaultRoomLifecycle(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        coJustRun { any<RealtimeChannel>().attachCoroutine() }
        coJustRun { any<RealtimeChannel>().detachCoroutine() }

        val contributors = createRoomFeatureMocks()
        val messagesContributor = contributors.first { it.featureName == "messages" }

        val javaChannel = messagesContributor.channelWrapper.javaChannel
        every {
            javaChannel.once(eq(ChannelState.attached), any<ChannelStateListener>())
        } answers {
            secondArg<ChannelStateListener>().onChannelStateChanged(null)
        }
        justRun {
            javaChannel.once(eq(ChannelState.failed), any<ChannelStateListener>())
        }

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, statusLifecycle, contributors, logger))

        val result = kotlin.runCatching { roomLifecycle.retry(messagesContributor) }

        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals(RoomStatus.Attached, statusLifecycle.status)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        verify {
            javaChannel.once(eq(ChannelState.attached), any<ChannelStateListener>())
        }
        verify {
            javaChannel.once(eq(ChannelState.failed), any<ChannelStateListener>())
        }
    }

    @OptIn(InternalAPI::class)
    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL5e) If, during the CHA-RL5d wait, the contributor channel becomes failed, then the room enters failed state and retry operation stops`() = runTest {
        val statusLifecycle = spyk(DefaultRoomLifecycle(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        coJustRun { any<RealtimeChannel>().attachCoroutine() }
        coJustRun { any<RealtimeChannel>().detachCoroutine() }

        val contributors = createRoomFeatureMocks()
        val messagesContributor = contributors.first { it.featureName == "messages" }

        val errorInfo = ErrorInfo("Failed channel messages", HttpStatusCode.InternalServerError)
        val channel = messagesContributor.channelWrapper
        every { channel.state } returns ChannelState.failed
        every { channel.reason } returns errorInfo
        messagesContributor.channelWrapper.javaChannel.setState(ChannelState.failed, errorInfo)

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, statusLifecycle, contributors, logger))

        val result = kotlin.runCatching { roomLifecycle.retry(messagesContributor) }
        Assert.assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AblyException
        Assert.assertEquals("Failed channel messages", exception.errorInfo.message)
        Assert.assertEquals(RoomStatus.Failed, statusLifecycle.status)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL5f, CHA-RC2e) If, during the CHA-RL5d wait, the contributor channel becomes ATTACHED, then attach operation continues for other contributors as per CHA-RL1e`() = runTest {
        val statusLifecycle = spyk(DefaultRoomLifecycle(logger))

        mockkStatic(RealtimeChannel::attachCoroutine)
        val capturedAttachedChannels = mutableListOf<RealtimeChannel>()
        coEvery { any<RealtimeChannel>().attachCoroutine() } coAnswers {
            capturedAttachedChannels.add(firstArg())
        }

        val capturedDetachedChannels = mutableListOf<RealtimeChannel>()
        coEvery { any<RealtimeChannel>().detachCoroutine() } coAnswers {
            capturedDetachedChannels.add(firstArg())
        }

        val contributors = createRoomFeatureMocks()
        Assert.assertEquals(5, contributors.size)
        val messagesContributor = contributors.first { it.featureName == "messages" }
        val channel = messagesContributor.channelWrapper
        every { channel.state } returns ChannelState.attached

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, statusLifecycle, contributors, logger))

        val result = kotlin.runCatching { roomLifecycle.retry(messagesContributor) }
        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals(RoomStatus.Attached, statusLifecycle.status)

        Assert.assertEquals(2, capturedDetachedChannels.size)

        Assert.assertEquals("1234::\$chat", capturedDetachedChannels[0].name)
        Assert.assertEquals("1234::\$chat::\$reactions", capturedDetachedChannels[1].name)

        Assert.assertEquals(5, capturedAttachedChannels.size)

        Assert.assertEquals("1234::\$chat::\$chatMessages", capturedAttachedChannels[0].name)
        Assert.assertEquals("1234::\$chat::\$chatMessages", capturedAttachedChannels[1].name)
        Assert.assertEquals("1234::\$chat", capturedAttachedChannels[2].name)
        Assert.assertEquals("1234::\$chat::\$reactions", capturedAttachedChannels[3].name)
        Assert.assertEquals("1234::\$chat::\$chatMessages", capturedAttachedChannels[4].name)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }
}
