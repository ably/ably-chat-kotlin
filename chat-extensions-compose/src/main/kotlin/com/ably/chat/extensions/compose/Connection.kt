package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ably.chat.Connection
import com.ably.chat.ConnectionStatus
import com.ably.chat.statusAsFlow

/**
 * @return active connection status
 */
@Composable
public fun Connection.collectAsStatus(): State<ConnectionStatus> {
    val statusState = remember(this) { mutableStateOf(status) }

    LaunchedEffect(this) {
        statusState.value = status
        statusAsFlow().collect { statusState.value = it.current }
    }

    return statusState
}
