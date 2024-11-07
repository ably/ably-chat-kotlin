package com.ably.chat

import com.ably.utils.atomicCoroutineScope
import com.ably.utils.createRoomFeatureMocks
import com.ably.utils.setState
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

class RoomLifecycleManagerTest {

    private val roomScope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1) + CoroutineName("roomId"),
    )

    @Test
    fun `(CHA-RL1a) Attach success when channel in already in attached state`() = runTest {
        val status = spyk<DefaultStatus>().apply {
            setStatus(RoomLifecycle.Attached)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, emptyList()))
        val result = kotlin.runCatching { roomLifecycle.attach() }
        Assert.assertTrue(result.isSuccess)
    }

    @Test
    fun `(CHA-RL1b) Attach throws exception when channel in releasing state`() = runTest {
        val status = spyk<DefaultStatus>().apply {
            setStatus(RoomLifecycle.Releasing)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, emptyList()))
        val exception = Assert.assertThrows(AblyException::class.java) {
            runBlocking {
                roomLifecycle.attach()
            }
        }
        Assert.assertEquals("unable to attach room; room is releasing", exception.errorInfo.message)
        Assert.assertEquals(102_102, exception.errorInfo.code)
        Assert.assertEquals(500, exception.errorInfo.statusCode)
    }

    @Test
    fun `(CHA-RL1c) Attach throws exception when channel in released state`() = runTest {
        val status = spyk<DefaultStatus>().apply {
            setStatus(RoomLifecycle.Released)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, listOf()))
        val exception = Assert.assertThrows(AblyException::class.java) {
            runBlocking {
                roomLifecycle.attach()
            }
        }
        Assert.assertEquals("unable to attach room; room is released", exception.errorInfo.message)
        Assert.assertEquals(102_103, exception.errorInfo.code)
        Assert.assertEquals(500, exception.errorInfo.statusCode)
    }

    @Test
    fun `(CHA-RL1d, CHA-RL1j) Attach op should wait for existing operation as per (CHA-RL7)`() = runTest {
        val status = spyk<DefaultStatus>()
        Assert.assertEquals(RoomLifecycle.Initializing, status.current)

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, emptyList()))

        val roomReleased = Channel<Boolean>()
        coEvery {
            roomLifecycle.release()
        } coAnswers {
            roomLifecycle.atomicCoroutineScope().async {
                status.setStatus(RoomLifecycle.Releasing)
                roomReleased.receive()
                status.setStatus(RoomLifecycle.Released)
            }
        }

        // Release op started from separate coroutine
        launch { roomLifecycle.release() }
        assertWaiter { !roomLifecycle.atomicCoroutineScope().finishedProcessing }
        Assert.assertEquals(0, roomLifecycle.atomicCoroutineScope().pendingJobCount) // no queued jobs, one job running
        assertWaiter { status.current == RoomLifecycle.Releasing }

        // Attach op started from separate coroutine
        val roomAttachOpDeferred = async(SupervisorJob()) { roomLifecycle.attach() }
        assertWaiter { roomLifecycle.atomicCoroutineScope().pendingJobCount == 1 } // attach op queued
        Assert.assertEquals(RoomLifecycle.Releasing, status.current)

        // Finish release op, so ATTACH op can start
        roomReleased.send(true)
        assertWaiter { status.current == RoomLifecycle.Released }

        val result = kotlin.runCatching { roomAttachOpDeferred.await() }
        Assert.assertTrue(roomLifecycle.atomicCoroutineScope().finishedProcessing)

        Assert.assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AblyException

        Assert.assertEquals("unable to attach room; room is released", exception.errorInfo.message)
        Assert.assertEquals(102_103, exception.errorInfo.code)
        Assert.assertEquals(500, exception.errorInfo.statusCode)

        coVerify { roomLifecycle.release() }
    }

    @Test
    fun `(CHA-RL1e) Attach op should transition room into ATTACHING state`() = runTest {
        val status = spyk<DefaultStatus>()
        val roomStatusChanges = mutableListOf<RoomStatusChange>()
        status.on {
            roomStatusChanges.add(it)
        }
        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, emptyList()))
        roomLifecycle.attach()
        Assert.assertEquals(RoomLifecycle.Attaching, roomStatusChanges[0].current)
        Assert.assertEquals(RoomLifecycle.Attached, roomStatusChanges[1].current)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }
    }

    @Test
    fun `(CHA-RL1f) Attach op should attach each contributor channel sequentially`() = runTest {
        val status = spyk<DefaultStatus>()

        mockkStatic(io.ably.lib.realtime.Channel::attachCoroutine)
        val capturedChannels = mutableListOf<io.ably.lib.realtime.Channel>()
        coEvery { any<io.ably.lib.realtime.Channel>().attachCoroutine() } coAnswers {
            capturedChannels.add(firstArg())
        }

        val contributors = createRoomFeatureMocks()
        Assert.assertEquals(5, contributors.size)

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, contributors))
        roomLifecycle.attach()
        val result = kotlin.runCatching { roomLifecycle.attach() }
        Assert.assertTrue(result.isSuccess)

        Assert.assertEquals(5, capturedChannels.size)
        repeat(5) {
            Assert.assertEquals(contributors[it].channel.name, capturedChannels[it].name)
        }
        Assert.assertEquals("1234::\$chat::\$chatMessages", capturedChannels[0].name)
        Assert.assertEquals("1234::\$chat::\$chatMessages", capturedChannels[1].name)
        Assert.assertEquals("1234::\$chat::\$chatMessages", capturedChannels[2].name)
        Assert.assertEquals("1234::\$chat::\$typingIndicators", capturedChannels[3].name)
        Assert.assertEquals("1234::\$chat::\$reactions", capturedChannels[4].name)
    }

    @Test
    fun `(CHA-RL1g) When all contributor channels ATTACH, op is complete and room should be considered ATTACHED`() = runTest {
        val status = spyk<DefaultStatus>()

        mockkStatic(io.ably.lib.realtime.Channel::attachCoroutine)
        coEvery { any<io.ably.lib.realtime.Channel>().attachCoroutine() } returns Unit

        val contributors = createRoomFeatureMocks("1234")
        val contributorErrors = mutableListOf<ErrorInfo>()
        for (contributor in contributors) {
            every {
                contributor.contributor.discontinuityDetected(capture(contributorErrors))
            } returns Unit
        }
        Assert.assertEquals(5, contributors.size)

        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, contributors), recordPrivateCalls = true) {
            val pendingDiscontinuityEvents = mutableMapOf<ResolvedContributor, ErrorInfo?>().apply {
                for (contributor in contributors) {
                    put(contributor, ErrorInfo("${contributor.channel.name} error", 500))
                }
            }
            this.setPrivateField("_pendingDiscontinuityEvents", pendingDiscontinuityEvents)
        }
        justRun { roomLifecycle invokeNoArgs "clearAllTransientDetachTimeouts" }

        roomLifecycle.attach()
        val result = kotlin.runCatching { roomLifecycle.attach() }

        // CHA-RL1g1
        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals(RoomLifecycle.Attached, status.current)
        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        // CHA-RL1g2
        verify(exactly = 1) {
            for (contributor in contributors) {
                contributor.contributor.discontinuityDetected(any<ErrorInfo>())
            }
        }
        Assert.assertEquals(5, contributorErrors.size)

        // CHA-RL1g3
        verify(exactly = 1) {
            roomLifecycle invokeNoArgs "clearAllTransientDetachTimeouts"
        }
    }

    // All of the following tests cover sub-spec points under CHA-RL1h ( channel attach failure )
    @Test
    fun `(CHA-RL1h1, CHA-RL1h2) If a one of the contributors fails to attach (enters suspended state), attach throws related error and room enters suspended state`() = runTest {
        val status = spyk<DefaultStatus>()

        mockkStatic(io.ably.lib.realtime.Channel::attachCoroutine)
        coEvery { any<io.ably.lib.realtime.Channel>().attachCoroutine() } coAnswers {
            val channel = firstArg<io.ably.lib.realtime.Channel>()
            if ("reactions" in channel.name) {
                // Throw error for typing contributor, likely to throw because it uses different channel
                channel.setState(ChannelState.suspended)
                throw AblyException.fromErrorInfo(ErrorInfo("error attaching channel ${channel.name}", 500))
            }
            Unit
        }

        val contributors = createRoomFeatureMocks("1234")
        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, contributors), recordPrivateCalls = true)

        val result = kotlin.runCatching { roomLifecycle.attach() }

        Assert.assertTrue(result.isFailure)
        Assert.assertEquals(RoomLifecycle.Suspended, status.current)

        val exception = result.exceptionOrNull() as AblyException

        Assert.assertEquals("failed to attach reactions feature", exception.errorInfo.message)
        Assert.assertEquals(ErrorCodes.ReactionsAttachmentFailed.errorCode, exception.errorInfo.code)
        Assert.assertEquals(500, exception.errorInfo.statusCode)
    }

    @Test
    fun `(CHA-RL1h1, CHA-RL1h4) If a one of the contributors fails to attach (enters failed state), attach throws related error and room enters failed state`() = runTest {
        val status = spyk<DefaultStatus>()

        mockkStatic(io.ably.lib.realtime.Channel::attachCoroutine)
        coEvery { any<io.ably.lib.realtime.Channel>().attachCoroutine() } coAnswers {
            val channel = firstArg<io.ably.lib.realtime.Channel>()
            if ("typing" in channel.name) {
                // Throw error for typing contributor, likely to throw because it uses different channel
                val error = ErrorInfo("error attaching channel ${channel.name}", 500)
                channel.setState(ChannelState.failed, error)
                throw AblyException.fromErrorInfo(error)
            }
            Unit
        }

        val contributors = createRoomFeatureMocks("1234")
        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, contributors), recordPrivateCalls = true)

        val result = kotlin.runCatching { roomLifecycle.attach() }

        Assert.assertTrue(result.isFailure)
        Assert.assertEquals(RoomLifecycle.Failed, status.current)

        val exception = result.exceptionOrNull() as AblyException

        Assert.assertEquals(
            "failed to attach typing feature, error attaching channel 1234::\$chat::\$typingIndicators",
            exception.errorInfo.message,
        )
        Assert.assertEquals(ErrorCodes.TypingAttachmentFailed.errorCode, exception.errorInfo.code)
        Assert.assertEquals(500, exception.errorInfo.statusCode)
    }

    @Test
    fun `(CHA-RL1h3) When room enters suspended state (CHA-RL1h2), it should enter recovery loop as per (CHA-RL5)`() = runTest {
        val status = spyk<DefaultStatus>()

        mockkStatic(io.ably.lib.realtime.Channel::attachCoroutine)
        coEvery { any<io.ably.lib.realtime.Channel>().attachCoroutine() } coAnswers {
            val channel = firstArg<io.ably.lib.realtime.Channel>()
            if ("reactions" in channel.name) {
                // Throw error for typing contributor, likely to throw because it uses different channel
                channel.setState(ChannelState.suspended)
                throw AblyException.fromErrorInfo(ErrorInfo("error attaching channel ${channel.name}", 500))
            }
            Unit
        }

        val contributors = createRoomFeatureMocks("1234")
        val roomLifecycle = spyk(RoomLifecycleManager(roomScope, status, contributors), recordPrivateCalls = true)

        val resolvedContributor = slot<ResolvedContributor>()

        // Behaviour for CHA-RL5 will be tested as a part of sub spec for the same
        coEvery { roomLifecycle["doRetry"](capture(resolvedContributor)) } coAnswers {
            delay(1000)
        }

        val result = kotlin.runCatching { roomLifecycle.attach() }
        assertWaiter { !roomLifecycle.atomicCoroutineScope().finishedProcessing }

        Assert.assertTrue(result.isFailure)
        Assert.assertEquals(RoomLifecycle.Suspended, status.current)

        assertWaiter { roomLifecycle.atomicCoroutineScope().finishedProcessing }

        coVerify(exactly = 1) {
            roomLifecycle["doRetry"](resolvedContributor.captured)
        }
    }

    @Test
    fun `(CHA-RL1h5) When room enters failed state, room detach all channels not in failed state`() = runTest {
    }

    @Test
    fun `(CHA-RL1h6) When room enters failed state, when CHA-RL1h5 fails to detach, op will be repeated till all channels are detached`() = runTest {
    }
}
