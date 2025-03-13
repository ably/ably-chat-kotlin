package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ably.chat.Room
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.asFlow

/**
 * @return currently typing clients
 */
@ExperimentalChatApi
@Composable
public fun Room.collectAsCurrentlyTyping(): Set<String> {
    var currentlyTyping by remember(this) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(this) {
        typing.asFlow().collect {
            currentlyTyping = it.currentlyTyping
        }
    }

    return currentlyTyping
}
