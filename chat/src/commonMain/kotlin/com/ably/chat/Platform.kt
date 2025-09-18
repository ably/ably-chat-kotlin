package com.ably.chat

internal enum class Platform { Jvm, Android }

internal expect val CurrentPlatform: Platform

internal val PlatformSpecificAgent get() = when (CurrentPlatform) {
    Platform.Jvm -> "chat-kotlin-jvm"
    Platform.Android -> "chat-kotlin-android"
}
