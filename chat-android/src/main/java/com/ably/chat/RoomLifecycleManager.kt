package com.ably.chat

import com.ably.annotations.InternalAPI
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An interface for features that contribute to the room.
 */
internal interface ContributesToRoomLifecycle {
    /**
     * Name of the feature
     */
    val featureName: String

    /**
     * Implements code to free up underlying resources.
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
 * An implementation of RoomLifecycleManager.
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
     * Retry duration in milliseconds, used by internal retryUntilChannelDetachedOrFailed method
     */
    private val retryDurationInMs: Long = 250

    private val roomChannel = room.channel

    @OptIn(InternalAPI::class)
    val channel: Channel = roomChannel.javaChannel // CHA-RC2f

    private var attachedOnce: Boolean = false

    private var explicitlyDetached: Boolean = false

    private val operationInProcess: Boolean
        get() = !atomicCoroutineScope.finishedProcessing

    private val channelStateToRoomStatusMap = mapOf(
        ChannelState.initialized to RoomStatus.Initialized,
        ChannelState.attaching to RoomStatus.Attaching,
        ChannelState.attached to RoomStatus.Attached,
        ChannelState.detaching to RoomStatus.Detaching,
        ChannelState.detached to RoomStatus.Detached,
        ChannelState.failed to RoomStatus.Failed,
        ChannelState.suspended to RoomStatus.Suspended
    )

    /**
     * Maps a channel state to a room status.
     */
    private fun mapChannelStateToRoomStatus(channelState: ChannelState): RoomStatus {
        return channelStateToRoomStatusMap[channelState] ?: error("Unknown ChannelState: $channelState")
    }


    init {
        // CHA-RL11, CHA-RL12 - Start monitoring channel state changes
        startMonitoringChannelState()
    }

    /**
     * Sets up monitoring of channel state changes to keep room status in sync.
     * If an operation is in progress (attach/detach/release), state changes are ignored.
     * @private
     */
    private fun startMonitoringChannelState() {
        logger.trace("startMonitoringChannelState();")
        // CHA-RL11a
        channel.on {
            roomScope.launch { // sequentially launches events since limitedParallelism is set to 1
                logger.debug("startMonitoringChannelState(); RoomLifecycleManager.channel state changed", context = mapOf("change" to it.toString()))
                // CHA-RL11b
                if (operationInProcess) {
                    logger.debug(
                        "startMonitoringChannelState(); ignoring channel state change - operation in progress",
                        context = mapOf("status" to room.status.stateName)
                    );
                    return@launch
                }

                // CHA-RL11c
                val newStatus = mapChannelStateToRoomStatus(it.current)
                statusLifecycle.setStatus(newStatus, it.reason)
            }
        }
    }

    /**
     * Attaches to the channel and updates room status accordingly.
     * If the room is already released, this operation fails.
     * If already attached, this is a no-op.
     * Spec: CHA-RL1
     */
    internal suspend fun attach() {
        logger.trace("attach();")
        val deferredAttach = atomicCoroutineScope.async(LifecycleOperationPrecedence.AttachOrDetach.priority) { // CHA-RL1d

            if (statusLifecycle.status == RoomStatus.Attached) { // CHA-RL1a
                logger.debug("attach(); room is already attached")
                return@async
            }

            if (statusLifecycle.status == RoomStatus.Released) { // CHA-RL1c
                logger.error("attach(); attach failed, room is in released state")
                throw lifeCycleException("attach(); unable to attach room; room is released", ErrorCode.RoomIsReleased)
            }

            logger.debug("attach(); attaching room", context = mapOf("state" to room.status.stateName));

            try {
                // CHA-RL1e
                statusLifecycle.setStatus(RoomStatus.Attaching)
                // CHA-RL1k
                roomChannel.attachCoroutine()
                statusLifecycle.setStatus(RoomStatus.Attached)
                attachedOnce = true;
                logger.debug("attach(): room attached successfully")
            } catch (attachException: AblyException) {
                val errorMessage = "failed to attach room: ${attachException.message}"
                logger.error(errorMessage)
                attachException.errorInfo?.let {
                    it.message = errorMessage
                }
                val newStatus = mapChannelStateToRoomStatus(roomChannel.state)
                statusLifecycle.setStatus(newStatus, attachException.errorInfo)
                throw attachException
            }
        }
        deferredAttach.await()
    }

    /**
     * Detaches from the channel and updates room status accordingly.
     * If the room is already released, this operation fails.
     * If already detached, this is a no-op.
     * Spec: CHA-RL2
     */
    internal suspend fun detach() {
        logger.trace("detach();")
        val deferredDetach = atomicCoroutineScope.async(LifecycleOperationPrecedence.AttachOrDetach.priority) { // CHA-RL2i

            // CHA-RL2d
            if (statusLifecycle.status == RoomStatus.Failed) {
                throw lifeCycleException("detach(); cannot detach room, room is in failed state", ErrorCode.RoomInFailedState)
            }

            // CHA-RL2c
            if (statusLifecycle.status == RoomStatus.Released) {
                logger.error("detach(); detach failed, room is in released state")
                throw lifeCycleException("detach(); unable to detach room; room is released", ErrorCode.RoomIsReleased)
            }

            // CHA-RL2a
            if (statusLifecycle.status == RoomStatus.Detached) {
                logger.debug("detach(); room is already detached")
                return@async
            }

            logger.debug("detach(); detaching room", context = mapOf("state" to room.status.stateName))

            try {
                // CHA-RL2j
                statusLifecycle.setStatus(RoomStatus.Detaching)
                // CHA-RL2k
                roomChannel.detachCoroutine()
                explicitlyDetached = true;
                statusLifecycle.setStatus(RoomStatus.Detached)
                logger.debug("detach(): room detached successfully")
            } catch (detachException: AblyException) {
                val errorMessage = "failed to attach room: ${detachException.message}"
                logger.error(errorMessage)
                detachException.errorInfo?.let {
                    it.message = errorMessage
                }
                val newStatus = mapChannelStateToRoomStatus(roomChannel.state)
                statusLifecycle.setStatus(newStatus, detachException.errorInfo)
                throw detachException
            }
        }
        deferredDetach.await()
    }

    /**
     * Releases the room by detaching the channel and releasing it from the channel manager.
     * If the channel is in a failed state, skips the detach operation.
     * Will retry detach until successful unless in failed state.
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
            statusLifecycle.setStatus(RoomStatus.Releasing)
            // CHA-RL3n
            logger.debug("release(); attempting channel detach before release", context = mapOf("state" to room.status.stateName))
            retryUntilChannelDetachedOrFailed()
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
    private suspend fun retryUntilChannelDetachedOrFailed() {
        logger.trace("retryUntilChannelDetachedOrFailed();")
        var channelDetached = kotlin.runCatching { roomChannel.detachCoroutine() }
        while (channelDetached.isFailure) {
            if (roomChannel.state == ChannelState.failed) {
                logger.debug("retryUntilChannelDetachedOrFailed(); channel state is failed, skipping detach")
                return
            }
            // Wait a short period and then try again
            delay(retryDurationInMs)
            channelDetached = kotlin.runCatching { roomChannel.detachCoroutine() }
        }
    }

    /**
     * Performs the release operation on the room channel.
     * Underlying resources are released each room feature.
     * Spec: CHA-RL3d, CHA-RL3g
     */
    private fun doRelease() {
        logger.trace("doRelease();")
        room.realtimeClient.channels.release(roomChannel.name)
        // CHA-RL3h
        logger.debug("doRelease(); releasing underlying resources from each room feature")
        contributors.forEach {
            it.release()
            logger.debug("doRelease(); resource cleanup for feature: ${it.featureName}")
        }
        logger.debug("doRelease(); underlying resources released each room feature")
        statusLifecycle.setStatus(RoomStatus.Released) // CHA-RL3g
        logger.debug("doRelease(); transitioned room to RELEASED state")
    }
}
