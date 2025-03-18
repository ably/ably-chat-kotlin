package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ably.chat.Room
import com.ably.chat.RoomStatus
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.statusAsFlow

/**
 * @return room status
 */
@ExperimentalChatApi
@Composable
public fun Room.collectAsStatus(): RoomStatus {
    var status by remember(this) { mutableStateOf(status) }

    LaunchedEffect(this) {
        statusAsFlow().collect { status = it.current }
    }

    return status
}
