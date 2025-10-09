package com.ably.chat

import com.ably.annotations.InternalAPI
import com.ably.chat.json.JsonObject
import com.ably.chat.json.jsonObject
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.types.MessageExtras
import kotlinx.coroutines.flow.Flow
import io.ably.lib.realtime.Channel as AblyRealtimeChannel

/**
 * This interface is used to interact with room-level reactions in a chat room: subscribing to reactions and sending them.
 *
 * Get an instance via [Room.reactions].
 */
public interface RoomReactions {

    /**
     * Sends a reaction to the specified room along with optional metadata.
     *
     * This method allows you to send a reaction at the room level.
     * It accepts parameters for defining the type of reaction, metadata, and additional headers.
     *
     * @param name The name of the reaction. See [SendRoomReactionParams.name].
     * @param metadata Optional metadata to include with the reaction. Defaults to `null`. See [SendRoomReactionParams.metadata]
     * @param headers Additional headers to include with the reaction. Defaults to an empty map. See [SendRoomReactionParams.headers]
     *
     * @return Unit when the reaction has been successfully sent. Note that it is
     * possible to receive your own reaction via the reactions listener before
     * this method completes.
     */
    public suspend fun send(name: String, metadata: JsonObject? = null, headers: Map<String, String>? = null)

    /**
     * Subscribe to receive room-level reactions.
     *
     * @param listener The listener function to be called when a reaction is received.
     * @returns A response object that allows you to control the subscription.
     */
    public fun subscribe(listener: RoomReactionListener): Subscription
}

/**
 * The listener function type for room-level reactions.
 */
public typealias RoomReactionListener = (RoomReactionEvent) -> Unit

public interface RoomReactionEvent {
    public val type: RoomReactionEventType
    public val reaction: RoomReaction
}

internal data class DefaultRoomReactionEvent(
    override val reaction: RoomReaction,
    override val type: RoomReactionEventType = RoomReactionEventType.Reaction,
) : RoomReactionEvent

/**
 * @return [RoomReaction] events as a [Flow]
 */
public fun RoomReactions.asFlow(): Flow<RoomReactionEvent> = transformCallbackAsFlow {
    subscribe(it)
}

/**
 * Params for sending a room-level reactions. Only `type` is mandatory.
 */
internal data class SendRoomReactionParams(
    /**
     * The name of the reaction, for example an emoji or a short string such as
     * "like".
     *
     * It is the only mandatory parameter to send a room-level reaction.
     */
    val name: String,

    /**
     * Optional metadata of the reaction.
     *
     * The metadata is a map of extra information that can be attached to the
     * room reaction. It is not used by Ably and is sent as part of the realtime
     * message payload. Example use cases are custom animations or other effects.
     *
     * Do not use metadata for authoritative information. There is no server-side
     * validation. When reading the metadata treat it like user input.
     *
     * The key `ably-chat` is reserved and cannot be used. Ably may populate this
     * with different values in the future.
     */
    val metadata: JsonObject? = null,

    /**
     * Optional headers of the room reaction.
     *
     * The headers are a flat key-value map and are sent as part of the realtime
     * message's `extras` inside the `headers` property. They can serve similar
     * purposes as the metadata but they are read by Ably and can be used for
     * features such as
     * [subscription filters](https://faqs.ably.com/subscription-filters).
     *
     * Do not use the headers for authoritative information. There is no
     * server-side validation. When reading the headers treat them like user
     * input.
     *
     * The key prefix `ably-chat` is reserved and cannot be used. Ably may add
     * headers prefixed with `ably-chat` in the future.
     */
    val headers: Map<String, String>? = null,
)

internal class DefaultRoomReactions(
    private val room: DefaultRoom,
) : RoomReactions, RoomFeature {

    override val featureName = "reactions"

    val channelWrapper: RealtimeChannel = room.channel

    @OptIn(InternalAPI::class)
    internal val channel: AblyRealtimeChannel = channelWrapper.javaChannel // CHA-RC3

    private val logger = room.logger.withContext(tag = "Reactions")

    // (CHA-ER3) Ephemeral room reactions are sent to Ably via the Realtime connection via a send method.
    override suspend fun send(name: String, metadata: JsonObject?, headers: Map<String, String>?) {
        val pubSubMessage = PubSubMessage().apply {
            this.name = RoomReactionEventType.Reaction.eventName
            data = jsonObject {
                put("name", name)
                metadata?.let { put("metadata", it) }
            }.toGson()
            headers?.let {
                extras = MessageExtras(
                    jsonObject {
                        put("headers", it.toJson())
                    }.toGson().asJsonObject,
                )
            }
        }
        room.ensureConnected(logger) // CHA-ER3f
        channelWrapper.publishCoroutine(pubSubMessage.asEphemeralMessage()) // CHA-ER3d
    }

    override fun subscribe(listener: RoomReactionListener): Subscription {
        val messageListener = PubSubMessageListener {
            val pubSubMessage = it ?: run {
                logger.warn("Got empty pubsub channel message")
                return@PubSubMessageListener
            }
            val data = pubSubMessage.data.tryAsJsonValue() ?: run {
                logger.warn("Received message with invalid data", context = mapOf("message" to pubSubMessage))
                return@PubSubMessageListener
            }
            val name = data.getOrNull("name")?.stringOrNull() ?: run {
                logger.warn("Received message with invalid data", context = mapOf("message" to pubSubMessage))
                return@PubSubMessageListener
            }
            val reaction = DefaultRoomReaction(
                name = name,
                createdAt = pubSubMessage.timestamp,
                clientId = pubSubMessage.clientId,
                metadata = data.jsonObjectOrNull()?.get("metadata")?.jsonObjectOrNull() ?: jsonObject {},
                headers = pubSubMessage.extras?.asJsonObject()?.get("headers")?.tryAsJsonValue()?.toMap() ?: mapOf(),
                isSelf = pubSubMessage.clientId == room.clientId,
            )
            listener.invoke(DefaultRoomReactionEvent(reaction))
        }
        return channelWrapper.subscribe(RoomReactionEventType.Reaction.eventName, messageListener).asChatSubscription()
    }

    override fun dispose() {
        // No need to do anything
    }
}
