package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ably.chat.Room
import com.ably.chat.asFlow

/**
 * @return currently typing clients
 */
@Composable
public fun Room.collectAsCurrentlyTyping(): State<Set<String>> {
    val currentlyTyping = remember(this) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(this) {
        typing.asFlow().collect {
            currentlyTyping.value = it.currentlyTyping
        }
    }

    return currentlyTyping
}
