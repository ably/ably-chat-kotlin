package com.ably.chat

/**
 * Manages the lifecycle of chat rooms.
 */
interface Rooms {
    /**
     * Get the client options used to create the Chat instance.
     */
    val clientOptions: ClientOptions

    /**
     * Gets a room reference by ID. The Rooms class ensures that only one reference
     * exists for each room. A new reference object is created if it doesn't already
     * exist, or if the one used previously was released using release(roomId).
     *
     * Always call `release(roomId)` after the Room object is no longer needed.
     *
     * @param roomId The ID of the room.
     * @param options The options for the room.
     * @throws {@link ErrorInfo} if a room with the same ID but different options already exists.
     * @returns Room A new or existing Room object.
     */
    fun get(roomId: String, options: RoomOptions): Room

    /**
     * Release the Room object if it exists. This method only releases the reference
     * to the Room object from the Rooms instance and detaches the room from Ably. It does not unsubscribe to any
     * events.
     *
     * After calling this function, the room object is no-longer usable. If you wish to get the room object again,
     * you must call {@link Rooms.get}.
     *
     * @param roomId The ID of the room.
     */
    suspend fun release(roomId: String)
}