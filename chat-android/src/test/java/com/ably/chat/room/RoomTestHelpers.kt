package com.ably.chat.room

import com.ably.annotations.InternalAPI
import com.ably.chat.AndroidLogger
import com.ably.chat.AtomicCoroutineScope
import com.ably.chat.ChatApi
import com.ably.chat.ContributesToRoomLifecycle
import com.ably.chat.DefaultRoom
import com.ably.chat.DefaultRoomLifecycle
import com.ably.chat.LifecycleOperationPrecedence
import com.ably.chat.Logger
import com.ably.chat.Room
import com.ably.chat.RoomLifecycleManager
import com.ably.chat.RoomOptions
import com.ably.chat.RoomStatusEventEmitter
import com.ably.chat.Rooms
import com.ably.chat.Typing
import com.ably.chat.TypingEventType
import com.ably.chat.getPrivateField
import com.ably.chat.invokePrivateMethod
import com.ably.chat.invokePrivateSuspendMethod
import com.ably.chat.setPrivateField
import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimeClient
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.buildRealtimeChannel
import io.ably.lib.types.ErrorInfo
import io.ably.lib.util.EventEmitter
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import io.ably.lib.realtime.Channel as AblyRealtimeChannel

const val DEFAULT_ROOM_ID = "1234"
const val DEFAULT_CLIENT_ID = "clientId"
const val DEFAULT_CHANNEL_NAME = "channel"

fun createMockRealtimeClient(): RealtimeClient {
    val realtimeClient = mockk<RealtimeClient> {
        every {
            channels
        } returns mockk {
            every { get(any(), any()) } answers {
                createMockRealtimeChannel(firstArg<String>())
            }
            every { release(any()) } returns Unit
        }
    }
    return realtimeClient
}

@OptIn(InternalAPI::class)
fun createMockRealtimeChannel(channelName: String = "channel"): RealtimeChannel = mockk {
    every { name } returns channelName
    every { javaChannel } returns spyk(buildRealtimeChannel(channelName))
    every { state } returns ChannelState.initialized
    every { reason } returns null
    every { presence } returns mockk {
        every { subscribe(any()) } returns mockk(relaxUnitFun = true)
    }
    every { subscribe(any<String>(), any()) } returns mockk(relaxUnitFun = true)
    every { subscribe(any<List<String>>(), any()) } returns mockk(relaxUnitFun = true)
    every { subscribe(any()) } returns mockk(relaxUnitFun = true)
}

@OptIn(InternalAPI::class)
fun RealtimeClient.createMockChannel(channelName: String = DEFAULT_CHANNEL_NAME): Channel =
    spyk(this.javaClient.channels.get(channelName), recordPrivateCalls = true)

internal fun createMockChatApi(
    realtimeClient: RealtimeClient = createMockRealtimeClient(),
    clientId: String = DEFAULT_CLIENT_ID,
    logger: Logger = createMockLogger(),
) = spyk(ChatApi(realtimeClient, clientId, logger), recordPrivateCalls = true)

internal fun createMockLogger(): Logger = mockk<AndroidLogger>(relaxed = true)

internal fun createMockRoom(
    roomId: String = DEFAULT_ROOM_ID,
    clientId: String = DEFAULT_CLIENT_ID,
    realtimeClient: RealtimeClient = createMockRealtimeClient(),
    chatApi: ChatApi = mockk<ChatApi>(relaxed = true),
    logger: Logger = createMockLogger(),
): DefaultRoom =
    DefaultRoom(roomId, RoomOptions.AllFeaturesEnabled, realtimeClient, chatApi, clientId, logger)

// Rooms mocks
val Rooms.RoomIdToRoom get() = getPrivateField<MutableMap<String, Room>>("roomIdToRoom")
val Rooms.RoomGetDeferredMap get() = getPrivateField<MutableMap<String, CompletableDeferred<Unit>>>("roomGetDeferredMap")
val Rooms.RoomReleaseDeferredMap get() = getPrivateField<MutableMap<String, CompletableDeferred<Unit>>>("roomReleaseDeferredMap")

// Room mocks
internal val Room.StatusLifecycle get() = getPrivateField<DefaultRoomLifecycle>("statusLifecycle")
internal val Room.LifecycleManager get() = getPrivateField<RoomLifecycleManager>("lifecycleManager")

// DefaultRoomLifecycle mocks
internal val DefaultRoomLifecycle.InternalEmitter get() = getPrivateField<RoomStatusEventEmitter>("internalEmitter")

// EventEmitter mocks
internal val EventEmitter<*, *>.Listeners get() = getPrivateField<List<Any>>("listeners")
internal val EventEmitter<*, *>.Filters get() = getPrivateField<Map<Any, Any>>("filters")

// RoomLifeCycleManager Mocks
internal fun RoomLifecycleManager.atomicCoroutineScope(): AtomicCoroutineScope = getPrivateField("atomicCoroutineScope")

internal suspend fun RoomLifecycleManager.retry(exceptContributor: ContributesToRoomLifecycle) =
    invokePrivateSuspendMethod<Unit>("doRetry", exceptContributor)

internal var Typing.TypingHeartbeatStarted: ValueTimeMark?
    get() = getPrivateField("typingHeartbeatStarted")
    set(value) = setPrivateField("typingHeartbeatStarted", value)

internal val Typing.TypingStartEventPrunerJobs get() = getPrivateField<Map<String, Job>>("typingStartEventPrunerJobs")

internal fun Typing.processEvent(eventType: TypingEventType, clientId: String) =
    invokePrivateMethod<Unit>("processReceivedTypingEvents", eventType, clientId)

internal suspend fun RoomLifecycleManager.atomicRetry(exceptContributor: ContributesToRoomLifecycle) {
    atomicCoroutineScope().async(LifecycleOperationPrecedence.Internal.priority) {
        retry(exceptContributor)
    }.await()
}

internal fun createRoomFeatureMocks(
    roomId: String = DEFAULT_ROOM_ID,
    clientId: String = DEFAULT_CLIENT_ID,
): List<ContributesToRoomLifecycle> {
    val realtimeClient = createMockRealtimeClient()
    val chatApi = createMockChatApi()
    val logger = createMockLogger()
    val room = createMockRoom(roomId, clientId, realtimeClient, chatApi, logger)

    val messagesContributor = spyk(room.messages, recordPrivateCalls = true) as ContributesToRoomLifecycle
    val presenceContributor = spyk(room.presence, recordPrivateCalls = true) as ContributesToRoomLifecycle
    val occupancyContributor = spyk(room.occupancy, recordPrivateCalls = true) as ContributesToRoomLifecycle
    val typingContributor = spyk(room.typing, recordPrivateCalls = true) as ContributesToRoomLifecycle
    val reactionsContributor = spyk(room.reactions, recordPrivateCalls = true) as ContributesToRoomLifecycle

    // CHA-RC2e - Add contributors/features as per the order of precedence
    return listOf(messagesContributor, presenceContributor, typingContributor, reactionsContributor, occupancyContributor)
}

fun AblyRealtimeChannel.setState(state: ChannelState, errorInfo: ErrorInfo? = null) {
    this.state = state
    this.reason = errorInfo
}
