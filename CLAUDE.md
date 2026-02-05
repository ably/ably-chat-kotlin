# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ably Chat SDK for Kotlin - a purpose-built chat library supporting JVM and Android platforms. Built on Ably's core real-time messaging service, it provides APIs for chat rooms, messages, reactions, presence, typing indicators, and occupancy tracking.

## Build Commands

```bash
# Build the library AAR
./gradlew chat:assemble

# Run all checks (lint + tests)
./gradlew check

# Run Detekt static analysis with auto-fix
./gradlew detekt

# Run all tests
./gradlew test

# Run specific platform tests
./gradlew :chat:jvmTest
./gradlew :chat:testDebugUnitTest
./gradlew :chat:testReleaseUnitTest

# Publish to local Maven for testing in other projects
./gradlew publishToMavenLocal

# Generate API documentation
./gradlew dokkaHtml
```

## Module Structure

- **chat/** - Core SDK library (Kotlin Multiplatform)
  - `src/commonMain/` - Shared code for both platforms
  - `src/androidMain/` - Android-specific implementations
  - `src/jvmMain/` - JVM-specific implementations
  - `src/commonTest/`, `src/androidUnitTest/`, `src/jvmTest/` - Tests
- **chat-extensions-compose/** - Jetpack Compose extensions
- **example/** - Reference Android app with Compose UI

## Architecture

The SDK uses Kotlin Multiplatform with shared `commonMain` code and platform-specific implementations. Key abstractions in `com.ably.chat`:

- **ChatClient** - Entry point, manages room lifecycle
- **Room** - Chat room with messages, reactions, presence, occupancy, typing
- **Messages** - Send/receive messages, subscribe to updates
- **Presence** - User presence tracking
- **Typing** - Typing indicator management
- **Connection** - Ably connection state management
- **RoomLifecycleManager** - Handles room attach/detach lifecycle

All async operations use Kotlin coroutines with suspend functions. Subscriptions return `Flow<>` for reactive streams.

## Testing

- **JUnit 4** for test framework
- **MockK** for mocking
- **Turbine** for Flow testing
- **Robolectric** for Android unit tests

## Testing Requirements

When adding or modifying code:

- **Test file location**: Mirror source structure - `commonTest/` for shared code, `androidUnitTest/` for Android, `jvmTest/` for JVM
- **Naming**: Use `*Test.kt` suffix (e.g., `MessagesTest.kt` for `Messages.kt`)
- **Test utilities**: Use helpers from `TestUtils.kt` - `createMockRealtimeClient()`, `createMockRealtimeChannel()`, `createTestRoom()`
- **Flow testing**: Use Turbine's `.test { }` block for Flow assertions
- **Async testing**: Use `runTest` from kotlinx-coroutines-test
- **Mocking**: Use MockK - `mockk()`, `every { } returns`, `slot<T>()` for capturing callbacks
- **Integration tests**: Located in `commonTest/kotlin/com/ably/chat/integration/`; use `Sandbox` class to create real test clients against Ably sandbox environment
- **Integration test coverage**: Cover main flows (happy path + key error cases), not every code branch - unit tests handle exhaustive branch coverage

## API Design Patterns

When adding public APIs:

- **Interfaces over classes**: Public API uses interfaces (`Messages`, `Room`), implementations are `internal class` (`DefaultMessages`, `DefaultRoom`)
- **Extension functions**: Add convenience methods as extensions, especially `asFlow()` adapters for callback-based subscriptions
- **Stability annotations**: Use `@ExperimentalChatApi` for unstable APIs, `@InternalChatApi` for internal-only APIs
- **KDoc comments**: Document all public APIs with `@param`, `@return`, `@throws`; include "Not suitable for inheritance" warning on interfaces
- **Options pattern**: Use `MutableXxxOptions` classes with `@ChatDsl` annotation for builder DSL

## Error Handling

- **Exception type**: Throw `ChatException` (wraps `ErrorInfo` with code, message, statusCode)
- **Error codes**: Use `ErrorCode` enum from `ErrorCodes.kt` (e.g., `ErrorCode.RoomInInvalidState`, `ErrorCode.FeatureNotEnabledInRoom`)
- **Factory functions**: Use `chatException()`, `clientError()`, `serverError()`, `lifeCycleException()` from `Exceptions.kt`
- **Logging**: Use the `Logger` interface with `trace()`, `debug()`, `info()`, `warn()`, `error()` extensions
- **Context in logs**: Pass `context = mapOf("key" to value)` for structured logging
- **Wrapping PubSub errors**: Use `ChatException(reason)` constructor or `reason.toErrorInfo()` extension

## Code Standards

Detekt enforces:
- Max line length: 140 characters
- Max cyclomatic complexity: 15
- Auto-correct enabled for formatting issues

## Development Workflow

When making changes to this codebase:

1. **After every code change**, run the linter/formatter:
   ```bash
   ./gradlew detekt
   ```
   This auto-fixes formatting issues. Review and commit any changes it makes.

2. **Before completing work**, run the full test suite:
   ```bash
   ./gradlew test
   ```
   All tests must pass. If you modified platform-specific code, ensure tests pass on all platforms.

3. **For quick iteration**, run only the relevant platform tests:
   - JVM changes: `./gradlew :chat:jvmTest`
   - Android changes: `./gradlew :chat:testDebugUnitTest`

4. **Before submitting a PR**, run the full check:
   ```bash
   ./gradlew check
   ```
   This runs both Detekt and all tests together.
