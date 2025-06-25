# Upgrade Guide

This guide provides detailed instructions on how to upgrade between versions of the Chat SDK.

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
