package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ably.chat.PresenceMember
import com.ably.chat.Room
import com.ably.chat.RoomStatus.Attached
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.asFlow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * @return currently present members
 */
@ExperimentalChatApi
@Composable
public fun Room.collectAsPresenceMembers(): List<PresenceMember> {
    var presenceMembers by remember(this) { mutableStateOf(emptyList<PresenceMember>()) }
    val roomStatus = collectAsStatus()

    LaunchedEffect(this, roomStatus) {
        if (roomStatus != Attached) return@LaunchedEffect

        val initialPresenceGet = launch {
            runCatching {
                presenceMembers = presence.get()
            }
        }
        presence.asFlow().collect {
            if (initialPresenceGet.isActive) initialPresenceGet.cancelAndJoin()
            runCatching {
                presenceMembers = presence.get()
            }
        }
    }

    return presenceMembers
}
