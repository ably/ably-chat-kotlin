package com.ably.chat

import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * An interface for features that contribute to the room status.
 */
internal interface ContributesToRoomLifecycle {
    /**
     * Name of the feature
     */
    val featureName: String

    /**
     * Underlying Realtime feature channel is removed from the core SDK to prevent leakage.
     * Spec: CHA-RL3h
     */
    fun release()
}

/**
 * The order of precedence for lifecycle operations, passed to PriorityQueueExecutor which
 * allows us to ensure that certain operations are completed before others.
 */
internal enum class LifecycleOperationPrecedence(val priority: Int) {
    Release(1),
    AttachOrDetach(2),
}

/**
 * An implementation of the `Status` interface.
 * @internal
 */
internal class RoomLifecycleManager(
    private val room: DefaultRoom,
    private val roomScope: CoroutineScope,
    private val statusLifecycle: DefaultRoomLifecycle,
    private val contributors: List<ContributesToRoomLifecycle>,
    roomLogger: Logger,
) {
    private val logger = roomLogger.withContext(
        "RoomLifecycleManager",
        dynamicContext = mapOf("scope" to { Thread.currentThread().name }),
    )

    /**
     * AtomicCoroutineScope makes sure all operations are atomic and run with given priority.
     * See [Kotlin Dispatchers](https://kt.academy/article/cc-dispatchers) for more information.
     * Spec: CHA-RL7
     */
    private val atomicCoroutineScope = AtomicCoroutineScope(roomScope)

    /**
     * Retry duration in milliseconds, used by internal doRetry and runDownChannelsOnFailedAttach methods
     */
    private val retryDurationInMs: Long = 250

    private var attachedOnce: Boolean = false

    private var explicitlyDetached: Boolean = false

    private val channelStateToRoomStatusMap = mapOf(
        ChannelState.initialized to RoomStatus.Initialized,
        ChannelState.attaching to RoomStatus.Attaching,
        ChannelState.attached to RoomStatus.Attached,
        ChannelState.detaching to RoomStatus.Detaching,
        ChannelState.detached to RoomStatus.Detached,
        ChannelState.failed to RoomStatus.Failed,
        ChannelState.suspended to RoomStatus.Suspended
    )

    init {
        // TODO - [CHA-RL4] set up room monitoring here
    }

    /**
     * Maps a channel state to a room status.
     */
    private fun mapChannelStateToRoomStatus(channelState: ChannelState): RoomStatus {
        return channelStateToRoomStatusMap[channelState] ?: error("Unknown ChannelState: $channelState")
    }

    /**
     * Try to attach all the channels in a room.
     *
     * If the operation succeeds, the room enters the attached state.
     * If a channel enters the suspended state, then we throw exception, but we will retry after a short delay as is the case
     * in the core SDK.
     * If a channel enters the failed state, we throw an exception and then begin to wind down the other channels.
     * Spec: CHA-RL1
     */
    @Suppress("ThrowsCount")
    internal suspend fun attach() {
        logger.trace("attach();")
        val deferredAttach = atomicCoroutineScope.async(LifecycleOperationPrecedence.AttachOrDetach.priority) { // CHA-RL1d

            if (statusLifecycle.status == RoomStatus.Attached) { // CHA-RL1a
                logger.debug("attach(); room is already attached")
                return@async
            }

            if (statusLifecycle.status == RoomStatus.Released) { // CHA-RL1c
                logger.error("attach(); attach failed, room is in released state")
                throw lifeCycleException("unable to attach room; room is released", ErrorCode.RoomIsReleased)
            }

            logger.debug("attach(); attaching room", context = mapOf("state" to room.status.stateName));

            try {
                // CHA-RL1e
                statusLifecycle.setStatus(RoomStatus.Attaching);
                // CHA-RL1k
                room.channel.attachCoroutine()
                statusLifecycle.setStatus(RoomStatus.Attached);
                attachedOnce = true;
                logger.debug("attach(): room attached successfully")
            } catch (attachException: AblyException) {
                val errorMessage = "failed to attach room: ${attachException.message}"
                logger.error(errorMessage)
                attachException.errorInfo?.let {
                    it.message = errorMessage
                }
                val newStatus = mapChannelStateToRoomStatus(room.channel.state);
                statusLifecycle.setStatus(newStatus, attachException.errorInfo);
                throw attachException;
            }
        }

        deferredAttach.await()
    }

    /**
     * Detaches the room. If the room is already detached, this is a no-op.
     * If one of the channels fails to detach, the room status will be set to failed.
     * If the room is in the process of detaching, this will wait for the detachment to complete.
     * Spec: CHA-RL2
     */
    @Suppress("ThrowsCount")
    internal suspend fun detach() {
        logger.trace("detach();")
        val deferredDetach = atomicCoroutineScope.async(LifecycleOperationPrecedence.AttachOrDetach.priority) { // CHA-RL2i

            // CHA-RL2d
            if (statusLifecycle.status == RoomStatus.Failed) {
                throw lifeCycleException("cannot detach room, room is in failed state", ErrorCode.RoomInFailedState);
            }

            // CHA-RL2c
            if (statusLifecycle.status == RoomStatus.Released) {
                logger.error("attach(); attach failed, room is in released state")
                throw lifeCycleException("unable to attach room; room is released", ErrorCode.RoomIsReleased)
            }

            // CHA-RL2a
            if (statusLifecycle.status == RoomStatus.Detached) {
                logger.debug("attach(); room is already detached")
                return@async
            }

            logger.debug("detach(); detaching room", context = mapOf("state" to room.status.stateName))

            try {
                // CHA-RL2j
                statusLifecycle.setStatus(RoomStatus.Detaching)
                // CHA-RL2k
                room.channel.detachCoroutine()
                explicitlyDetached = true;
                statusLifecycle.setStatus(RoomStatus.Detached)
                logger.debug("detach(): room detached successfully")
            } catch (detachException: AblyException) {
                val errorMessage = "failed to attach room: ${detachException.message}"
                logger.error(errorMessage)
                detachException.errorInfo?.let {
                    it.message = errorMessage
                }
                val newStatus = mapChannelStateToRoomStatus(room.channel.state);
                statusLifecycle.setStatus(newStatus, detachException.errorInfo);
                throw detachException;
            }
        }
        return deferredDetach.await()
    }

    /**
     * Releases the room. If the room is already released, this is a no-op.
     * Any channel that detaches into the failed state is ok. But any channel that fails to detach
     * will cause the room status to be set to failed.
     *
     * @returns Returns when the room is released. If a channel detaches into a non-terminated
     * state (e.g. attached), release will throw exception.
     * Spec: CHA-RL3
     */
    internal suspend fun release() {
        logger.trace("release();")
        val deferredRelease = atomicCoroutineScope.async(LifecycleOperationPrecedence.Release.priority) { // CHA-RL3k
            // CHA-RL3a
            if (statusLifecycle.status == RoomStatus.Released) {
                logger.debug("release(); room is already released, no-op")
                return@async
            }

            // CHA-RL3b, CHA-RL3j
            if (statusLifecycle.status == RoomStatus.Initialized || statusLifecycle.status == RoomStatus.Detached) {
                logger.debug("release(); room is initialized or detached, releasing immediately")
                doRelease()
                return@async
            }
            // CHA-RL3m
            statusLifecycle.setStatus(RoomStatus.Releasing);
            // CHA-RL3n
            logger.debug("release(); attempting channel detach before release", context = mapOf("state" to room.status.stateName))
            retryUntilChannelDetached()
            logger.debug("release(); success, channel successfully detached")
            // CHA-RL3o, CHA-RL3h
            doRelease()
        }
        deferredRelease.await()
    }

    /**
     *  Releases the room by detaching all channels. If the release operation fails, we wait
     *  a short period and then try again.
     *  Spec: CHA-RL3f, CHA-RL3d
     */
    private suspend fun retryUntilChannelDetached() {
        logger.trace("retryUntilChannelDetached();")
        var channelDetached = kotlin.runCatching { room.channel.detachCoroutine() }
        while (channelDetached.isFailure) {
            if (room.channel.state == ChannelState.failed) {
                logger.debug("retryUntilChannelDetached(); channel state is failed, skipping detach")
                return
            }
            // Wait a short period and then try again
            delay(retryDurationInMs)
            channelDetached = kotlin.runCatching { room.channel.detachCoroutine() }
        }
    }

    /**
     * Performs the release operation. This will detach all channels in the room that aren't
     * already detached or in the failed state.
     * Spec: CHA-RL3d, CHA-RL3g
     */
    @Suppress("RethrowCaughtException")
    private suspend fun doRelease() = coroutineScope {
        logger.trace("doRelease();")
        room.realtimeClient.channels.release(room.channel.name)
        // CHA-RL3h - underlying Realtime Channels are released from the core SDK prevent leakage
        logger.debug("doRelease(); releasing underlying channels from core SDK to prevent leakage")
        contributors.forEach {
            it.release()
        }
        logger.debug("doRelease(); underlying channels released from core SDK")
        statusLifecycle.setStatus(RoomStatus.Released) // CHA-RL3g
        logger.debug("doRelease(); transitioned room to RELEASED state")
    }
}
