# Change Log

## [0.10.0](https://github.com/ably/ably-chat-kotlin/tree/v0.10.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.9.0...v0.10.0)

## What's Changed

Type Renaming: some type renamed for clarity and consistency with other SDKs:

- `MessageOptions` → `MessagesOptions`
- `MessagesReactions` → `MessageReactions`

For detailed migration instructions, please refer to the [Upgrading Guide](UPGRADING.md).

## [0.9.0](https://github.com/ably/ably-chat-kotlin/tree/v0.9.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.8.0...v0.9.0)

## What's Changed

The following features have been added in this release:

- Added `get(serial: String)` in `Messages` to fetch messages by serial.
- Added `clientReactions` in `MessageReactions` for filtering reaction summaries by client.
- **chat-compose-extension promoted to stable** The Jetpack Compose extension module has been promoted from experimental to stable status.

### Breaking changes

This release includes several breaking changes:

- `MessageAction` enum no longer includes internal `Meta` and `Summary` values that were not part of the public API.
- Interface-based listeners have been replaced with Kotlin function types for a more idiomatic API.
- The Chat SDK now throws `ChatException` instead of `AblyException` for better error handling specificity.
- Status properties have been changed from `current()` function to `current` property for cleaner syntax.
- Import path changes: `ErrorInfo`, `SummaryClientIdCounts`, `SummaryClientIdList`, and `MessageAction` now import from `com.ably.chat` instead of other packages.

For detailed migration instructions, please refer to the [Upgrading Guide](UPGRADING.md).

## [0.8.0](https://github.com/ably/ably-chat-kotlin/tree/v0.8.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.7.0...v0.8.0)

## What's Changed

The following features have been added in this release:

- **Protocol v4 support** Added support for Chat protocol v4, enabling enhanced message handling. [#161](https://github.com/ably/ably-chat-kotlin/pull/161)
- **Destructuring syntax** Added destructuring syntax for subscription handling, providing more convenient API usage. [#159](https://github.com/ably/ably-chat-kotlin/pull/159)
- **Custom JSON implementation** Replaced Gson with custom JSON implementation to reduce dependencies [#160](https://github.com/ably/ably-chat-kotlin/pull/160)

### Breaking changes

This release includes several breaking changes:

- `Message` structure has changed.
- Replaced `Gson` with a custom JSON interface.
- Presence Data must be a JsonObject.

For detailed migration instructions, please refer to the [Upgrading Guide](UPGRADING.md).

## [0.7.0](https://github.com/ably/ably-chat-kotlin/tree/v0.7.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.6.0...v0.7.0)

## What's Changed

- Enabling the SDK to run on JVM environments in addition to Android.
This expands the SDK's compatibility to server-side applications, desktop applications,
and other JVM-based platforms while maintaining the same chat functionality and API.

## [0.6.0](https://github.com/ably/ably-chat-kotlin/tree/v0.6.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.5.0...v0.6.0)

## What's Changed

Breaking Changes:

- Removes opinionated presence structure [150](https://github.com/ably/ably-chat-kotlin/pull/150)

## [0.5.0](https://github.com/ably/ably-chat-kotlin/tree/v0.5.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.4.0...v0.5.0)

## What's Changed

Breaking Changes:

- Renames `Reaction` to `RoomReaction` [144](https://github.com/ably/ably-chat-kotlin/pull/144)
- Renames `RoomReaction.type` to `RoomReaction.name` [144](https://github.com/ably/ably-chat-kotlin/pull/144)

## [0.4.0](https://github.com/ably/ably-chat-kotlin/tree/v0.4.0)

[Full Changelog](https://github.com/ably/ably-chat-kotlin/compare/v0.3.0...v0.4.0)

## What's Changed

### New features

The following features have been added in this release:

- **Message reactions** Added __experimental__ support for message reactions. [#139](https://github.com/ably/ably-chat-kotlin/pull/139)

### Breaking changes

This release also includes several breaking changes:

- `roomId` has been renamed to `name` or `roomName` throughout the SDK to align terminology with other Ably SDKs [#141](https://github.com/ably/ably-chat-kotlin/pull/141)
- Event restructuring in Occupancy, Room Reactions, and Presence to match the style used by messages and typing indicators [#140](https://github.com/ably/ably-chat-kotlin/pull/140)
- Sending typing indicators and room reactions now requires the connection status to be `Connected` [#140](https://github.com/ably/ably-chat-kotlin/pull/140)

For detailed migration instructions, please refer to the [Upgrading Guide](UPGRADING.md).

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
