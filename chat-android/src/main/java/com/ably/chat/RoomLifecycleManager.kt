package com.ably.chat

import com.ably.annotations.InternalAPI
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener.ChannelStateChange
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An interface for features that contribute to the room.
 */
internal interface RoomFeature {
    /**
     * Name of the feature
     */
    val featureName: String

    /**
     * Implements code to free up underlying resources.
     * Spec: CHA-RL3h
     */
    fun dispose()
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
    private val statusManager: DefaultRoomStatusManager,
    private val roomFeatures: List<RoomFeature>,
    roomLogger: Logger,
) : DiscontinuityImpl(logger = roomLogger) {

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

    val roomChannel = room.channel

    @OptIn(InternalAPI::class)
    val channel: Channel = roomChannel.javaChannel // CHA-RC2f

    private var hasAttachedOnce: Boolean = false

    private var isExplicitlyDetached: Boolean = false

    private val operationInProcess: Boolean
        get() = !atomicCoroutineScope.finishedProcessing

    private val channelEventBus = AwaitableChannel<ChannelStateChange>(logger)

    private var roomMonitoringJob: Job

    private val channelStateToRoomStatusMap = mapOf(
        ChannelState.initialized to RoomStatus.Initialized,
        ChannelState.attaching to RoomStatus.Attaching,
        ChannelState.attached to RoomStatus.Attached,
        ChannelState.detaching to RoomStatus.Detaching,
        ChannelState.detached to RoomStatus.Detached,
        ChannelState.failed to RoomStatus.Failed,
        ChannelState.suspended to RoomStatus.Suspended,
    )

    init {
        // CHA-RL11, CHA-RL12 - Start monitoring channel state changes
        channel.on { channelEventBus.trySend(it) }
        roomMonitoringJob = handleChannelStateChanges()
    }

    /**
     * Maps a channel state to a room status.
     */
    private fun mapChannelStateToRoomStatus(channelState: ChannelState): RoomStatus =
        channelStateToRoomStatusMap.getValue(channelState)

    /**
     * Sets up monitoring of channel state changes to keep room status in sync.
     * If an operation is in progress (attach/detach/release), state changes are ignored.
     * @private
     */
    private fun handleChannelStateChanges(): Job {
        logger.trace("handleChannelStateChanges();")
        return roomScope.launch {
            channelEventBus.collect { channelStateChangeEvent ->
                // CHA-RL11a
                logger.debug(
                    "handleChannelStateChanges(); RoomLifecycleManager.channel state changed",
                    context = mapOf("change" to channelStateChangeEvent.toString()),
                )
                // CHA-RL11b, CHA-RL11c
                if (!operationInProcess) {
                    val newStatus = mapChannelStateToRoomStatus(channelStateChangeEvent.current)
                    statusManager.setStatus(newStatus, channelStateChangeEvent.reason)
                }
                val attached = channelStateChangeEvent.current == ChannelState.attached
                val notResumed = !channelStateChangeEvent.resumed
                // CHA-RL12a, CHA-RL12b
                if (attached && notResumed && hasAttachedOnce && !isExplicitlyDetached) {
                    val updatedErrorInfo = channelStateChangeEvent.reason?.let { originalErrorInfo ->
                        ErrorInfo().apply {
                            message = "Discontinuity detected, ${originalErrorInfo.message}"
                            code = ErrorCode.RoomDiscontinuity.code
                            statusCode = originalErrorInfo.statusCode
                            href = originalErrorInfo.href
                        }
                    }
                    val errorContext = updatedErrorInfo?.toString() ?: "no error info"
                    logger.warn("handleChannelStateChanges(); discontinuity detected", context = mapOf("error" to errorContext))
                    discontinuityDetected(updatedErrorInfo)
                }
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

            if (statusManager.status == RoomStatus.Attached) { // CHA-RL1a
                logger.debug("attach(); room is already attached")
                return@async
            }

            if (statusManager.status == RoomStatus.Released) { // CHA-RL1c
                logger.error("attach(); attach failed, room is in released state")
                throw lifeCycleException("unable to attach room; room is released", ErrorCode.RoomIsReleased)
            }

            logger.debug("attach(); attaching room", context = mapOf("state" to room.status.stateName))

            try {
                // CHA-RL1e
                statusManager.setStatus(RoomStatus.Attaching)
                // CHA-RL1k
                roomChannel.attachCoroutine()
                // await on internal channel state changes to be processed
                channelEventBus.await()
                hasAttachedOnce = true
                isExplicitlyDetached = false
                // CHA-RL1f
                statusManager.setStatus(RoomStatus.Attached)
                logger.debug("attach(): room attached successfully")
            } catch (attachException: AblyException) {
                channelEventBus.await() // await on internal channel state changes to be processed
                val errorMessage = "failed to attach room: ${attachException.errorInfo.message}"
                logger.error(errorMessage)
                attachException.errorInfo?.let {
                    it.message = errorMessage
                }
                val newStatus = mapChannelStateToRoomStatus(roomChannel.state)
                statusManager.setStatus(newStatus, attachException.errorInfo)
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
    @Suppress("ThrowsCount")
    internal suspend fun detach() {
        logger.trace("detach();")
        val deferredDetach = atomicCoroutineScope.async(LifecycleOperationPrecedence.AttachOrDetach.priority) { // CHA-RL2i

            // CHA-RL2d
            if (statusManager.status == RoomStatus.Failed) {
                throw lifeCycleException("cannot detach room, room is in failed state", ErrorCode.RoomInFailedState)
            }

            // CHA-RL2c
            if (statusManager.status == RoomStatus.Released) {
                logger.error("detach(); detach failed, room is in released state")
                throw lifeCycleException("unable to detach room; room is released", ErrorCode.RoomIsReleased)
            }

            // CHA-RL2a
            if (statusManager.status == RoomStatus.Detached) {
                logger.debug("detach(); room is already detached")
                return@async
            }

            logger.debug("detach(); detaching room", context = mapOf("state" to room.status.stateName))

            try {
                // CHA-RL2j
                statusManager.setStatus(RoomStatus.Detaching)
                // CHA-RL2k
                roomChannel.detachCoroutine()
                // await on internal channel state changes to be processed
                channelEventBus.await()
                isExplicitlyDetached = true
                statusManager.setStatus(RoomStatus.Detached)
                logger.debug("detach(): room detached successfully")
            } catch (detachException: AblyException) {
                channelEventBus.await() // await on internal channel state changes to be processed
                val errorMessage = "failed to detach room: ${detachException.errorInfo.message}"
                logger.error(errorMessage)
                detachException.errorInfo?.let {
                    it.message = errorMessage
                }
                val newStatus = mapChannelStateToRoomStatus(roomChannel.state)
                statusManager.setStatus(newStatus, detachException.errorInfo)
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
            if (statusManager.status == RoomStatus.Released) {
                logger.debug("release(); room is already released, no-op")
                return@async
            }

            // CHA-RL3b, CHA-RL3j
            if (statusManager.status == RoomStatus.Initialized || statusManager.status == RoomStatus.Detached) {
                logger.debug("release(); room is initialized or detached, releasing immediately")
                doRelease()
                return@async
            }
            // CHA-RL3m
            statusManager.setStatus(RoomStatus.Releasing)
            // CHA-RL3n
            logger.debug("release(); attempting channel detach before release", context = mapOf("state" to room.status.stateName))
            retryUntilChannelDetachedOrFailed()
            // await on internal channel state changes to be processed
            channelEventBus.await()
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
     * Underlying resources are released for each room feature.
     * Spec: CHA-RL3d, CHA-RL3g
     */
    private fun doRelease() {
        logger.trace("doRelease();")
        room.realtimeClient.channels.release(roomChannel.name)
        // CHA-RL3h
        logger.debug("doRelease(); releasing underlying resources from each room feature")
        roomFeatures.forEach {
            it.dispose()
            logger.debug("doRelease(); resource cleanup for feature: ${it.featureName}")
        }
        channelEventBus.dispose()
        roomMonitoringJob.cancel()
        offAllDiscontinuity()
        logger.debug("doRelease(); underlying resources released each room feature")
        statusManager.setStatus(RoomStatus.Released) // CHA-RL3g
        logger.debug("doRelease(); transitioned room to RELEASED state")
    }
}
