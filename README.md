# Ably Chat SDK for Android

<p style="text-align: left">
    <img src="https://badgen.net/github/license/3scale/saas-operator" alt="License" />
    <img src="https://img.shields.io/badge/version-0.3.0-2ea44f" alt="version: 0.3.0" />
    <a href="https://github.com/ably/ably-chat-kotlin/actions/workflows/coverage.yml"><img src="https://img.shields.io/static/v1?label=coverage&message=80%2B%25&color=2ea44f" alt="coverage - 80+%"></a>
</p>

Ably Chat is a set of purpose-built APIs for a host of chat features enabling you to create 1:1, 1:Many, Many:1 and Many:Many chat rooms for
any scale. It is designed to meet a wide range of chat use cases, such as livestreams, in-game communication, customer support, or social
interactions in SaaS products. Built on [Ably's](https://ably.com/) core service, it abstracts complex details to enable efficient chat
architectures.

Get started using the [📚 documentation](https://ably.com/docs/products/chat).

![Ably Chat Header](/images/ably-chat-github-header.png)

## Supported Platforms

This SDK works on Android 7.0+ (API level 24+) and Java 8+.

## Supported chat features

This project is under development so we will be incrementally adding new features. At this stage, you'll find APIs for the following chat
features:

- Chat rooms for 1:1, 1:many, many:1 and many:many participation.
- Sending and receiving chat messages.
- Online status aka presence of chat participants.
- Chat room occupancy, i.e total number of connections and presence members.
- Typing indicators
- Room-level reactions (ephemeral at this stage)

If there are other features you'd like us to prioritize, please [let us know](https://forms.gle/mBw9M53NYuCBLFpMA).

## Usage

You will need the following prerequisites:

- An Ably account
  - You can [sign up](https://ably.com/signup) to the generous free tier.
- An Ably API key
  - Use the default or create a new API key in an app within
    your [Ably account dashboard](https://ably.com/dashboard).
  - Make sure your API key has the
    following [capabilities](https://ably.com/docs/auth/capabilities): `publish`, `subscribe`, `presence`, `history` and
    `channel-metadata`.

## Installation

The Ably Chat SDK is available on the Maven Central Repository. To include the dependency in your project, add the following to your `build.gradle` file:

For Groovy:

```groovy
implementation 'com.ably.chat:chat-android:0.3.0'
```

For Kotlin Script (`build.gradle.kts`):

```kotlin
implementation("com.ably.chat:chat-android:0.3.0")
```

### Dependency on ably-android

Key functionality such as sending and receiving messages is powered by the [ably-android](https://github.com/ably/ably-java) library.
The `ably-android` library is included as an api dependency within the Chat SDK, so there is no need to manually add it to your project.

## Versioning

The Ably client library follows [Semantic Versioning](http://semver.org/). See https://github.com/ably/ably-chat-kotlin/tags for a list of
tagged releases.

## Instantiation and authentication

To instantiate the Chat SDK, create an [Ably client](https://ably.com/docs/getting-started/setup) and pass it into the
Chat constructor:

```kotlin
import com.ably.chat.ChatClient
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.ClientOptions

val realtimeClient = AblyRealtime(
    ClientOptions().apply {
        key = "<api-key>"
        clientId = "<client-id>"
    },
)

val chatClient = ChatClient(realtimeClient)
```

You can use [basic authentication](https://ably.com/docs/auth/basic) i.e. the API Key directly for testing purposes,
however it is strongly recommended that you use [token authentication](https://ably.com/docs/auth/token) in production
environments.

To use Chat you must also set a [`clientId`](https://ably.com/docs/auth/identified-clients) so that clients are
identifiable. If you are prototyping, you can use `java.util.UUID` to generate an ID.

In most cases, `clientId` can be set to `userId`, the user’s application-specific identifier, provided that `userId`
is unique within the context of your application.

## Getting Started

At the end of this tutorial, you will have initialized the Ably Chat client and sent your first message.

Start by creating a new Android project and installing the Chat SDK using the instructions described above. Then add the code in the following snippet
to your app and call it in your code. This simple script initializes the Chat client, creates a chat room and sends a message, printing it to logcat when it is received over the websocket connection.

```kotlin
import kotlinx.coroutines.delay
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.ClientOptions
import com.ably.chat.ChatClient
import com.ably.chat.ConnectionStatusChange
import com.ably.chat.RoomOptions
import com.ably.chat.RoomStatusChange

suspend fun getStartedWithChat() {
    // Create an Ably Realtime client that will be passed to the chat client
    val realtimeClient = AblyRealtime(
        ClientOptions().apply {
            key = "<API_KEY>"
            clientId = "ably-chat"
        },
    )

    // Create the chat client
    // The two clients can be re-used for the duration of your application.
    val chatClient = ChatClient(realtimeClient)

    // Subscribe to connection state changes
    val connectionSubscription = chatClient.connection.onStatusChange { statusChange: ConnectionStatusChange ->
        println("Connection status changed: ${statusChange.current}")
    }

    // Get our chat room, by default all room options are enabled except for occupancy events
    val room = chatClient.rooms.get("readme-getting-started")
    // Subscribe to room status changes
    val roomSubscription = room.onStatusChange {statusChange: RoomStatusChange ->
        println("Room status changed: ${statusChange.current}")
    }

    // Subscribe to room discontinuity, represents possible loss of messages after reconnection
    val discontinuitySubscription = room.onDiscontinuity { error ->
        println("Room discontinuity: ${error.message}")
    }

    // Subscribe to incoming messages
    val messageSubscription = room.messages.subscribe { msgEvent ->
        println("Message received: ${msgEvent.message.text}")
    }

    // Attach the room - meaning we'll start receiving events from the server
    room.attach()

    // Send a message
    room.messages.send(text = "Hello, World! This is my first message with Ably Chat!")

    // Wait for 5 seconds to make sure the message has time to be received.
    delay(5000)

    // Now release the room and close the connection
    chatClient.rooms.release("readme-getting-started")
    realtimeClient.close()
}
```

All being well, you should see lines in logcat resembling the following:

```
Connection status changed: Connected
Room status changed: Attaching
Room status changed: Attached
Message received: Hello, World! This is my first message with Ably Chat!
Room status changed: Releasing
Room status changed: Released
```

Congratulations! You have sent your first message using the Ably Chat SDK!

## Documentation

Click [📚 here](https://ably.com/docs/products/chat) to view the complete documentation for the Ably Chat SDKs.

## Contributing

For guidance on how to contribute to this project, see the [contributing guidelines](CONTRIBUTING.md).

## Support, feedback and troubleshooting

Please visit http://support.ably.com/ for access to our knowledge base and to ask for any assistance. You can also view
the [community reported Github issues](https://github.com/ably/ably-chat-kotlin/issues) or raise one yourself.

To see what has changed in recent versions, see the [changelog](CHANGELOG.md).

## Further reading

- See a [simple chat example](/example/) in this repo.
- [Share feedback or request](https://forms.gle/mBw9M53NYuCBLFpMA) a new feature.
