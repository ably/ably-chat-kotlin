![Ably Chat Header](/images/ably-chat-github-header.png)

# Ably Chat Kotlin SDK

Ably Chat is a set of purpose-built APIs for a host of chat features enabling you to create 1:1, 1:Many, Many:1 and Many:Many chat rooms for
any scale. It is designed to meet a wide range of chat use cases, such as livestreams, in-game communication, customer support, or social
interactions in SaaS products. Built on [Ably's](https://ably.com/) core service, it abstracts complex details to enable efficient chat
architectures.

---

## Getting started

Everything you need to get started with Ably:

* [SDK setup in Kotlin.](https://ably.com/docs/chat/setup?lang=kotlin)
* Play with the [livestream chat demo](https://ably-livestream-chat-demo.vercel.app/).

---

## Supported Platforms

Ably aims to support a wide range of platforms. If you experience any compatibility issues, open an issue in the repository or contact [Ably support](https://ably.com/support).

This SDK supports the following platforms:

| Platform | Support |
|----------|---------|
|Android | Android 7.0+ (API level 24+) |
| Java | Java 8+ |

> [!NOTE]
> Key functionality such as sending and receiving messages is powered by the [ably-android](https://github.com/ably/ably-java) library.
The `ably-android` library is included as an api dependency within the Chat SDK, so there is no need to manually add it to your project.

---

### Dependency on ably-android

Key functionality such as sending and receiving messages is powered by the [ably-android](https://github.com/ably/ably-java) library.
The `ably-android` library is included as an api dependency within the Chat SDK, so there is no need to manually add it to your project.

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

## Releases

The [CHANGELOG.md](/ably/ably-chat-kotlin/blob/main/CHANGELOG.md) contains details of the latest releases for this SDK. You can also view all Ably releases on [changelog.ably.com](https://changelog.ably.com).

---

## Contribute

Read the [CONTRIBUTING.md](./CONTRIBUTING.md) guidelines to contribute to Ably or [Share feedback or request](https://forms.gle/mBw9M53NYuCBLFpMA) a new feature.

---

## Support and known issues

For help or technical support, visit Ably's [support page](https://ably.com/support). You can also view the [community reported Github issues](https://github.com/ably/ably-chat-js/issues) or raise one yourself.
