package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ably.chat.PresenceMember
import com.ably.chat.Room
import com.ably.chat.RoomStatus.Attached
import com.ably.chat.asFlow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * @return currently present members
 */
@Composable
public fun Room.collectAsPresenceMembers(): State<List<PresenceMember>> {
    val presenceMembers = remember(this) { mutableStateOf(emptyList<PresenceMember>()) }
    val roomStatus by collectAsStatus()

    LaunchedEffect(this, roomStatus) {
        if (roomStatus != Attached) return@LaunchedEffect

        val initialPresenceGet = launch {
            runCatching {
                presenceMembers.value = presence.get()
            }
        }
        presence.asFlow().collect {
            if (initialPresenceGet.isActive) initialPresenceGet.cancelAndJoin()
            runCatching {
                presenceMembers.value = presence.get()
            }
        }
    }

    return presenceMembers
}
