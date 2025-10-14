package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ably.chat.Room
import com.ably.chat.RoomStatus
import com.ably.chat.statusAsFlow

/**
 * @return room status
 */
@Composable
public fun Room.collectAsStatus(): State<RoomStatus> {
    val statusState = remember(this) { mutableStateOf(status) }

    LaunchedEffect(this) {
        statusState.value = status
        statusAsFlow().collect { statusState.value = it.current }
    }

    return statusState
}
