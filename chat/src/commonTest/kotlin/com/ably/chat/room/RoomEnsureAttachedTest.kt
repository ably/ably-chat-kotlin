package com.ably.chat.room

import com.ably.chat.ChatException
import com.ably.chat.DefaultRoomStatusChange
import com.ably.chat.ErrorCode
import com.ably.chat.HttpStatusCode
import com.ably.chat.RoomStatus
import com.ably.chat.RoomStatusChange
import com.ably.chat.assertWaiter
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

/**
 * Spec: CHA-RL9
 * Common spec: CHA-PR3d, CHA-PR3h, CHA-PR10d, CHA-PR10h, CHA-PR6c, CHA-PR6h, CHA-PR6c, CHA-T2g
 * All of the remaining spec items are specified at Room#ensureAttached method
 */
class RoomEnsureAttachedTest {

    private val logger = createMockLogger()

    @Test
    fun `(CHA-PR3d, CHA-PR10d, CHA-PR6c, CHA-PR6c) When room is already ATTACHED, ensureAttached is a success`() = runTest {
        val room = createTestRoom()
        Assert.assertEquals(RoomStatus.Initialized, room.status)

        val statusManager = room.StatusManager

        // Set room status to ATTACHED
        statusManager.setStatus(RoomStatus.Attached)
        Assert.assertEquals(RoomStatus.Attached, room.status)

        val result = kotlin.runCatching { room.ensureAttached(logger) }
        Assert.assertTrue(result.isSuccess)
    }

    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-PR3h, CHA-PR10h, CHA-PR6h, CHA-T2g) When room is not ATTACHED or ATTACHING, ensureAttached throws error with code RoomInInvalidState`() = runTest {
        val room = createTestRoom()
        val statusManager = room.StatusManager

        Assert.assertEquals(RoomStatus.Initialized, room.status)

        // List of room status other than ATTACHED/ATTACHING
        val invalidStatuses = listOf(
            RoomStatus.Initialized,
            RoomStatus.Detaching,
            RoomStatus.Detached,
            RoomStatus.Suspended,
            RoomStatus.Failed,
            RoomStatus.Releasing,
            RoomStatus.Released,
        )

        for (invalidStatus in invalidStatuses) {
            statusManager.setStatus(invalidStatus)
            Assert.assertEquals(invalidStatus, room.status)

            // Check for exception when ensuring room ATTACHED
            val result = kotlin.runCatching { room.ensureAttached(room.logger) }
            Assert.assertTrue(result.isFailure)
            val exception = result.exceptionOrNull() as ChatException
            Assert.assertEquals(ErrorCode.RoomInInvalidState.code, exception.errorInfo.code)
            Assert.assertEquals(HttpStatusCode.BadRequest, exception.errorInfo.statusCode)
            val errMsg = "Can't perform operation; the room '${room.name}' is in an invalid state: $invalidStatus"
            Assert.assertEquals(errMsg, exception.errorInfo.message)
        }
    }

    @Test
    fun `(CHA-RL9a) When room is ATTACHING, subscribe once for next room status`() = runTest {
        val room = createTestRoom()
        Assert.assertEquals(RoomStatus.Initialized, room.status)

        val statusManager = spyk(room.StatusManager)
        every {
            statusManager.onChangeOnce(any<(RoomStatusChange) -> Unit>())
        } answers {
            val listener = firstArg<(RoomStatusChange) -> Unit>()
            listener.invoke(DefaultRoomStatusChange(RoomStatus.Attached, RoomStatus.Attaching))
        }
        room.StatusManager = statusManager

        // Set room status to ATTACHING
        statusManager.setStatus(RoomStatus.Attaching)
        Assert.assertEquals(RoomStatus.Attaching, room.status)

        room.ensureAttached(logger)

        verify(exactly = 1) {
            statusManager.onChangeOnce(any<(RoomStatusChange) -> Unit>())
        }
    }

    @Test
    fun `(CHA-RL9b) When room is ATTACHING, subscription is registered, ensureAttached is a success`() = runTest {
        val room = createTestRoom()
        Assert.assertEquals(RoomStatus.Initialized, room.status)

        val statusManager = room.StatusManager

        // Set room status to ATTACHING
        statusManager.setStatus(RoomStatus.Attaching)
        Assert.assertEquals(RoomStatus.Attaching, room.status)

        val ensureAttachJob = async { room.ensureAttached(logger) }

        // Wait for listener to be registered
        assertWaiter { statusManager.InternalEmitter.Filters.size == 1 }

        // Set ATTACHED status
        statusManager.setStatus(RoomStatus.Attached)

        val result = kotlin.runCatching { ensureAttachJob.await() }
        Assert.assertTrue(result.isSuccess)

        Assert.assertEquals(0, statusManager.InternalEmitter.Filters.size) // Emitted event processed
    }

    @Suppress("MaximumLineLength")
    @Test
    fun `(CHA-RL9c) When room is ATTACHING and subscription is registered and fails, ensureAttached throws error with code RoomInInvalidState`() = runTest {
        val room = createTestRoom()
        Assert.assertEquals(RoomStatus.Initialized, room.status)

        val statusManager = room.StatusManager

        // List of room status other than ATTACHED/ATTACHING
        val invalidStatuses = listOf(
            RoomStatus.Initialized,
            RoomStatus.Detaching,
            RoomStatus.Detached,
            RoomStatus.Suspended,
            RoomStatus.Failed,
            RoomStatus.Releasing,
            RoomStatus.Released,
        )

        for (invalidStatus in invalidStatuses) {
            // Set room status to ATTACHING
            statusManager.setStatus(RoomStatus.Attaching)
            Assert.assertEquals(RoomStatus.Attaching, room.status)

            val ensureAttachJob = async(SupervisorJob()) { room.ensureAttached(logger) }

            // Wait for listener to be registered
            assertWaiter { statusManager.InternalEmitter.Filters.size == 1 }

            // set invalid room status
            statusManager.setStatus(invalidStatus)

            // Check for exception when ensuring room ATTACHED
            val result = kotlin.runCatching { ensureAttachJob.await() }
            Assert.assertTrue(result.isFailure)
            val exception = result.exceptionOrNull() as ChatException
            Assert.assertEquals(ErrorCode.RoomInInvalidState.code, exception.errorInfo.code)
            Assert.assertEquals(HttpStatusCode.InternalServerError, exception.errorInfo.statusCode)
            val errMsg = "Can't perform operation; the room '${room.name}' is in an invalid state: $invalidStatus"
            Assert.assertEquals(errMsg, exception.errorInfo.message)

            Assert.assertEquals(0, statusManager.InternalEmitter.Filters.size) // Emitted event processed
        }
    }
}
