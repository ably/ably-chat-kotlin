package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ably.chat.Connection
import com.ably.chat.ConnectionStatus
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.statusAsFlow

/**
 * @return active connection status
 */
@ExperimentalChatApi
@Composable
public fun Connection.collectAsStatus(): ConnectionStatus {
    var status by remember(this) { mutableStateOf(status) }

    LaunchedEffect(this) {
        statusAsFlow().collect { status = it.current }
    }

    return status
}
