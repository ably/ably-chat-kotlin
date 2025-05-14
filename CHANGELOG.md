# Change Log

## [0.3.0](https://github.com/ably/ably-chat-kotlin/tree/v0.3.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.2.1...v0.3.0)

## What's Changed

- All Chat features now use a single underlying channel. This greatly simplifies the SDK whilst improving performance.

The following features have also been added in this release:

- Ephemeral typing indicators

**Closed issues:**

- Update to new Occupancy and Presence RoomOptions [\#129](https://github.com/ably/ably-chat-kotlin/issues/129)
- Implement/migrate to single channel [\#121](https://github.com/ably/ably-chat-kotlin/issues/121)

**Merged pull requests:**

- Added type to TypingEvent [\#131](https://github.com/ably/ably-chat-kotlin/pull/131) ([sacOO7](https://github.com/sacOO7))
- Feature/single channel integration [\#130](https://github.com/ably/ably-chat-kotlin/pull/130) ([sacOO7](https://github.com/sacOO7))
- \[ECO-5242\]\[CHADR-093\] Implement ephemeral typing [\#122](https://github.com/ably/ably-chat-kotlin/pull/122) ([sacOO7](https://github.com/sacOO7))
- \[ECO-5256\] refactor: getting rid of data classes from public API [\#119](https://github.com/ably/ably-chat-kotlin/pull/119) ([ttypic](https://github.com/ttypic))
- chore: turn on `explicitApi` for kotlin [\#114](https://github.com/ably/ably-chat-kotlin/pull/114) ([ttypic](https://github.com/ttypic))
- \[ECO-5231\] Renamed ClientOptions to ChatClientOptions [\#113](https://github.com/ably/ably-chat-kotlin/pull/113) ([sacOO7](https://github.com/sacOO7))

## [0.2.1](https://github.com/ably/ably-chat-kotlin/tree/v0.2.1)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.2.0...v0.2.1)

Fixed Room Typing Bug - https://github.com/ably/ably-chat-kotlin/pull/126

## [0.2.0](https://github.com/ably/ably-chat-kotlin/tree/v0.2.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.1.1...v0.2.0)

## What's Changed

The following features have been added in this release:

- Updating messages in a chat room
- Deleting messages in a chat room

The included example app has been updated to demonstrate the new features.

## [0.1.1](https://github.com/ably/ably-chat-kotlin/tree/v0.1.1)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.1.0...v0.1.1)

Fixed SDK version in `BuildConfig`

## [0.1.0](https://github.com/ably/ably-chat-kotlin/tree/v0.1.0) (2024-12-16)

Initial release of the Ably Chat SDK for Android. It includes following chat
features:

- Chat rooms for 1:1, 1:many, many:1 and many:many participation.
- Sending and receiving chat messages.
- Online status aka presence of chat participants.
- Chat room occupancy, i.e., total number of connections and presence members.
- Typing indicators
- Room-level reactions (ephemeral at this stage - reactions are sent and received in real-time without persistence)
