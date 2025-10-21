# Upgrade Guide

This guide provides detailed instructions on how to upgrade between versions of the Chat SDK.

## 0.9.x to 0.10.x

### `MessageOptions` renamed to `MessagesOptions`

**Expected Impact: Low**

The `MessageOptions` class has been renamed to `MessagesOptions` for consistency with other SDKs.

**Before**

```kotlin
val options: MessageOptions = room.options.messages
```

**After**

```kotlin
val options: MessagesOptions = room.options.messages
```

### `MessagesReactions` renamed to `MessageReactions`

**Expected Impact: Low**

The `MessagesReactions` class has been renamed to `MessageReactions` for consistency with other SDKs.

**Before**

```kotlin
val reactions: MessagesReactions = room.messages.reactions
```

**After**

```kotlin
val reactions: MessageReactions = room.messages.reactions
```

## 0.8.x to 0.9.x

### MessageAction Enum

**Expected Impact: Low**

The `MessageAction` enum no longer includes internal `Meta` and `Summary` values. These values were not part of the public API and should not have been used in application code. The enum now only contains the three public action types: `MessageCreate`, `MessageUpdate`, and `MessageDelete`.

No code changes are required unless you were incorrectly using these internal values.

### Listener Functions

**Expected Impact: Low**

Interface-based listeners have been replaced with Kotlin function types for a more idiomatic API.
This affects binary-compatibility mostly for all subscription methods throughout the SDK.

This change applies to all subscription methods including:
- `Messages.subscribe()`
- `Presence.subscribe()`
- `RoomReactions.subscribe()`
- `Occupancy.subscribe()`
- `Typing.subscribe()`
- `Room.onStatusChange()`
- `Connection.onStatusChange()`

### Exception Handling

**Expected Impact: Medium**

The Chat SDK now throws `ChatException` instead of `AblyException`. This provides better error handling specificity for chat-related operations.

#### Code Changes Required

**Before**

```kotlin
import io.ably.lib.types.AblyException

try {
    room.attach()
} catch (e: AblyException) {
    // Handle error
}
```

**After**

```kotlin
import com.ably.chat.*

try {
    room.attach()
} catch (e: ChatException) {
    // Handle error
}
```

### Status Properties

**Expected Impact: Medium**

Status properties have been changed from `current()` function to `current` property for cleaner, more idiomatic Kotlin syntax.

#### Code Changes Required

**Before**

```kotlin
val occupancyData = occupancy.current()
val typingClients = typing.current()
```

**After**

```kotlin
val occupancyData = occupancy.current
val typingClients = typing.current
```

This change applies to:
- `Occupancy.current`
- `Typing.current`

### Import Path Changes

**Expected Impact: High**

Several types now import from `com.ably.chat` instead of their previous locations. This ensures consistency and clarity about which types are part of the Chat SDK public API.

#### Code Changes Required

**Before**

```kotlin
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.MessageAction
```

**After**

```kotlin
import com.ably.chat.*
```

### Compose Extension

**Expected Impact: Low**

The `chat-compose-extension` module has been promoted from experimental to stable.
Made extensions return `State<*>` instead of plain objects to avoid unnecessary recompositions:

**Before**

```kotlin
val roomStatus = room.collectAsStatus()
val connectionStatus = connection.collectAsStatus()
val currentlyTyping = room.collectAsCurrentlyTyping()
val presentMembers = room.collectAsPresenceMembers()
val occupancy = room.collectAsOccupancy()
```

**After**

```kotlin
val roomStatus by room.collectAsStatus()
val connectionStatus by connection.collectAsStatus()
val currentlyTyping by room.collectAsCurrentlyTyping()
val presentMembers by room.collectAsPresenceMembers()
val occupancy by room.collectAsOccupancy()
```

## 0.7.x to 0.8.x

### Protocol v4 changes

**Expected Impact: Medium**

The Chat SDK now supports protocol v4, which introduces changes to message structure and handling.

#### Message Structure Changes

**Before**

```kotlin
val message: Message = getMessage()
val versionSerial: String = message.serial
val createdAt: Long = message.createdAt
val updatedAt: Long = message.timestamp
```

**After**

```kotlin
val message: Message = getMessage()
val versionSerial: String = message.version.serial
val createdAt: Long = message.timestamp
val updatedAt: Long = message.version.timestamp
```

### Custom JSON Implementation

**Expected Impact: Low**

The SDK has replaced Gson with a custom JSON interface to reduce external dependencies.

Affected fields: `Message.metadata`, `RoomReaction.metadata`, `Presence.data`

To build a JSON object to send, use the `jsonObject` builder from `com.ably.chat.json`:

```kotlin
presence.enter(jsonObject {
    put("status", "active")
    putObject("profile") {
        put("username", "John Doe")
        put("img", "https://example.com/img")
    }
})
```

### Presence Data JsonObject

**Expected Impact: Low**

Presence data must be a `JsonObject` rather than an arbitrary JSON value.
The `enter()`, `leave()`, and `update()` methods now accept only a `JsonObject`.

## 0.4.x to 0.5.x

### Room Reaction Wire Protocol

**Expected Impact: Medium**

The room reactions wire protocol has been updated to reflect the change below. If you are using multiple SDKs (e.g. Mobile, Web), please ensure you update them at the same time
to avoid compatibility issues.

### Room Reaction Interface Rename

**Expected Impact: Medium**

The `Reaction` interface and related types have been renamed to `RoomReaction` to disambiguate against message reactions. The property `type` has been renamed to `name`.

#### Affected Types

The following types have been renamed:

- `Reaction` → `RoomReaction`

#### Code Changes Required

**Before**

```kotlin
room.reactions.send(type = "like")
```

**After**

```kotlin
room.reactions.send(name = "like")
```

## 0.3.x to 0.4.x

### Room ID Rename

**Expected Impact: High**

`roomId` has been renamed to `name` or `roomName` throughout the SDK.

This is to align terminology more closely with other Ably SDKs.

### Event Restructuring

**Expected Impact: Medium**

In Occupancy, Room Reactions and Presence, the event received by the listeners you subscribe has changed to match the style used by messages
and typing indicators. The main change is that
the entity (e.g. presence member) is now nested in the event.

All of the data that you originally had accessible by the old event versions is still present, just in different places.

#### Presence

**Before**

```kotlin
room.presence.subscribe { event ->
    // Log the presence member
    println(event)

    // Log the presence event type
    println(event.action)
}
```

**After**

```kotlin
room.presence.subscribe { event ->
    // Log the presence member
    println(event.member)

    // Log the presence event type
    println(event.type)
}
```

#### Occupancy

**Before**

```kotlin
room.occupancy.subscribe { event ->
    // Log the number of connections
    println(event.connections)
}
```

**After**

```kotlin
room.occupancy.subscribe { event ->
    // Log the number of connections
    println(event.occupancy.connections)
}
```

#### Room Reactions

**Before**

```kotlin
room.reactions.subscribe { event ->
    // Log the reaction type
    println(event.type)
}
```

**After**

```kotlin
room.reactions.subscribe { event ->
    // Log the reaction type
    println(event.reaction.type)
}
```

### Operation Predicates

**Expected Impact: Low**

- Sending typing indicators now requires the connection status to be `Connected`.
- Sending room reactions now requires the connection status to be `Connected`.

This is to avoid messages being queued, which is in contrast to their ephemeral instantaneous use-case.
