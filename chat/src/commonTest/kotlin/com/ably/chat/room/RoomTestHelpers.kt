package com.ably.chat.room

import com.ably.annotations.InternalAPI
import com.ably.chat.AtomicCoroutineScope
import com.ably.chat.ChatApi
import com.ably.chat.ClientIdResolver
import com.ably.chat.DefaultRoom
import com.ably.chat.DefaultRoomStatusManager
import com.ably.chat.Logger
import com.ably.chat.MutableRoomOptions
import com.ably.chat.Room
import com.ably.chat.RoomFeature
import com.ably.chat.RoomLifecycleManager
import com.ably.chat.RoomOptions
import com.ably.chat.RoomStatus
import com.ably.chat.RoomStatusEventEmitter
import com.ably.chat.Rooms
import com.ably.chat.Typing
import com.ably.chat.TypingClientState
import com.ably.chat.TypingEventType
import com.ably.chat.buildRoomOptions
import com.ably.chat.getPrivateField
import com.ably.chat.invokePrivateMethod
import com.ably.chat.occupancy
import com.ably.chat.presence
import com.ably.chat.reactions
import com.ably.chat.setPrivateField
import com.ably.chat.typing
import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimeClient
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.realtime.ChannelStateListener.ChannelStateChange
import io.ably.lib.realtime.buildRealtimeChannel
import io.ably.lib.types.ErrorInfo
import io.ably.lib.util.EventEmitter
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlinx.coroutines.CompletableDeferred
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
            every { release(any<String>()) } returns Unit
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
): ChatApi {
    val clientIdResolver = mockk<ClientIdResolver>()
    every { clientIdResolver.get() } returns clientId
    return spyk(ChatApi(realtimeClient, logger), recordPrivateCalls = true)
}

internal fun createMockLogger(): Logger = mockk<Logger>(relaxed = true)

internal fun createTestRoom(
    roomName: String = DEFAULT_ROOM_ID,
    clientId: String = DEFAULT_CLIENT_ID,
    realtimeClient: RealtimeClient = createMockRealtimeClient(),
    chatApi: ChatApi = mockk<ChatApi>(relaxed = true),
    logger: Logger = createMockLogger(),
    roomOptions: (MutableRoomOptions.() -> Unit)? = null,
): DefaultRoom {
    val clientIdResolver = mockk<ClientIdResolver>()
    every { clientIdResolver.get() } returns clientId
    return DefaultRoom(roomName, buildRoomOptions(roomOptions), realtimeClient, chatApi, clientIdResolver, logger)
}

internal val RoomOptionsWithAllFeatures: RoomOptions
    get() = buildRoomOptions {
        typing()
        presence()
        reactions()
        occupancy {
            enableEvents = true
        }
    }

// Rooms mocks
val Rooms.RoomNameToRoom get() = getPrivateField<MutableMap<String, Room>>("roomNameToRoom")
val Rooms.RoomGetDeferredMap get() = getPrivateField<MutableMap<String, CompletableDeferred<Unit>>>("roomGetDeferredMap")
val Rooms.RoomReleaseDeferredMap get() = getPrivateField<MutableMap<String, CompletableDeferred<Unit>>>("roomReleaseDeferredMap")

// Room mocks
internal var Room.StatusManager
    get() = getPrivateField<DefaultRoomStatusManager>("statusManager")
    set(value) = setPrivateField("statusManager", value)
internal val Room.LifecycleManager get() = getPrivateField<RoomLifecycleManager>("lifecycleManager")

// DefaultRoomLifecycle mocks
internal val DefaultRoomStatusManager.InternalEmitter get() = getPrivateField<RoomStatusEventEmitter>("internalEmitter")

// EventEmitter mocks
internal val EventEmitter<*, *>.Listeners get() = getPrivateField<List<Any>>("listeners")
internal val EventEmitter<*, *>.Filters get() = getPrivateField<Map<Any, Any>>("filters")

// RoomLifeCycleManager Mocks
internal fun RoomLifecycleManager.atomicCoroutineScope(): AtomicCoroutineScope = getPrivateField("atomicCoroutineScope")
internal val RoomLifecycleManager.hasAttachedOnce get() = getPrivateField<Boolean>("hasAttachedOnce")
internal val RoomLifecycleManager.isExplicitlyDetached get() = getPrivateField<Boolean>("isExplicitlyDetached")
internal var RoomLifecycleManager.RoomFeatures
    get() = getPrivateField<List<RoomFeature>>("roomFeatures")
    set(value) = setPrivateField("roomFeatures", value)

internal val RoomLifecycleManager.EventCompletionDeferred
    get() = getPrivateField<AtomicReference<CompletableDeferred<Unit>>>("eventCompletionDeferred")

internal fun RoomLifecycleManager.channelStateToRoomStatus(channelState: ChannelState) =
    invokePrivateMethod<RoomStatus>("mapChannelStateToRoomStatus", channelState)

internal var Typing.TypingHeartbeatStarted: ValueTimeMark?
    get() = getPrivateField("typingHeartbeatStarted")
    set(value) = setPrivateField("typingHeartbeatStarted", value)

internal val Typing.TypingStartEventPrunerJobs get() = getPrivateField<Map<String, TypingClientState>>("typingStartEventPrunerJobs")

internal fun Typing.processEvent(eventType: TypingEventType, clientId: String, userClaim: String? = null) =
    invokePrivateMethod<Unit>("processReceivedTypingEvents", eventType, clientId, userClaim)

internal fun createRoomFeatureMocks(
    roomName: String = DEFAULT_ROOM_ID,
    clientId: String = DEFAULT_CLIENT_ID,
): List<RoomFeature> {
    val realtimeClient = createMockRealtimeClient()
    val chatApi = createMockChatApi()
    val logger = createMockLogger()
    val room = createTestRoom(roomName, clientId, realtimeClient, chatApi, logger)

    val messages = spyk(room.messages, recordPrivateCalls = true) as RoomFeature
    val presence = spyk(room.presence, recordPrivateCalls = true) as RoomFeature
    val occupancy = spyk(room.occupancy, recordPrivateCalls = true) as RoomFeature
    val typing = spyk(room.typing, recordPrivateCalls = true) as RoomFeature
    val reactions = spyk(room.reactions, recordPrivateCalls = true) as RoomFeature

    return listOf(messages, presence, typing, occupancy, reactions)
}

fun AblyRealtimeChannel.setState(newState: ChannelState, errorInfo: ErrorInfo? = null) {
    val previousState = this.state
    this.state = newState
    this.reason = errorInfo
    emit(newState, constructChannelStateChangeEvent(newState, previousState, errorInfo))
}

fun constructChannelStateChangeEvent(newState: ChannelState, prevState: ChannelState, errorInfo: ErrorInfo? = null): ChannelStateChange {
    val constructor = ChannelStateListener.ChannelStateChange::class.java
        .getDeclaredConstructor(ChannelState::class.java, ChannelState::class.java, ErrorInfo::class.java, Boolean::class.javaPrimitiveType)
    constructor.isAccessible = true
    return constructor.newInstance(newState, prevState, errorInfo, false)
}
