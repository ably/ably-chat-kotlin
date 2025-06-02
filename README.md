![Ably Chat Header](/images/KotlinChatSDK-github.png)
[![Version](https://img.shields.io/badge/version-0.3.0-2ea44f)](https://github.com/3scale/saas-operator/releases/tag/v0.3.0)
[![License](https://badgen.net/github/license/3scale/saas-operator)](https://github.com/3scale/saas-operator/blob/main/LICENSE)


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

---

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
