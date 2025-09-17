![Ably Chat Header](images/Android-JVM-SDK-github.png)
[![Version](https://img.shields.io/maven-central/v/com.ably.chat/chat-android?color=2ea44f&label=version)](https://central.sonatype.com/artifact/com.ably.chat/chat-android)
[![License](https://badgen.net/github/license/ably/ably-chat-kotlin)](https://github.com/ably/ably-chat-kotlin/blob/main/LICENSE)

# Ably Chat SDK

Ably Chat is a set of purpose-built APIs for a host of chat features enabling you to create 1:1, 1:Many, Many:1 and Many:Many chat rooms for
any scale. It is designed to meet a wide range of chat use cases, such as livestreams, in-game communication, customer support, or social
interactions in SaaS products. Built on [Ably's](https://ably.com/) core service, it abstracts complex details to enable efficient chat
architectures.

---

## Getting started

Everything you need to get started with Ably Chat for JVM and Android:

* [Getting started: Chat with Kotlin.](https://ably.com/docs/chat/getting-started/kotlin)
* [SDK and usage docs in Kotlin.](https://ably.com/docs/chat/setup?lang=kotlin)
* Play with the [livestream chat demo.](https://ably-livestream-chat-demo.vercel.app/)

---

## Supported platforms

Ably aims to support a wide range of platforms. If you experience any compatibility issues, open an issue in the repository or contact [Ably support](https://ably.com/support).

This SDK supports the following platforms:

| Platform | Support |
|----------|---------|
|Android | Android 7.0+ (API level 24+) |
| Java | Java 8+ |

> [!NOTE]
> Key functionality such as sending and receiving messages is powered by the [ably-java](https://github.com/ably/ably-java) library.
The `ably-java` library is included as an api dependency within the Chat SDK, so there is no need to manually add it to your project.

---

## Installation

The Ably Chat SDK is available on the Maven Central Repository. To include the dependency in your project, add the following to your `build.gradle` file:

For Groovy:

```groovy
implementation 'com.ably.chat:chat:0.6.0'
```

For Kotlin Script (`build.gradle.kts`):

```kotlin
implementation("com.ably.chat:chat:0.6.0")
```

## Releases

The [CHANGELOG.md](/ably/ably-chat-kotlin/blob/main/CHANGELOG.md) contains details of the latest releases for this SDK. You can also view all Ably releases on [changelog.ably.com](https://changelog.ably.com).

---

## Contribute

Read the [CONTRIBUTING.md](./CONTRIBUTING.md) guidelines to contribute to Ably or [Share feedback or request](https://forms.gle/mBw9M53NYuCBLFpMA) a new feature.

---

## Support, Feedback, and Troubleshooting

For help or technical support, visit Ably's [support page](https://ably.com/support). You can also view the [community reported Github issues](https://github.com/ably/ably-chat-kotlin/issues) or raise one yourself.
