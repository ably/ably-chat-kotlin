package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ably.chat.Room
import com.ably.chat.RoomStatus.Attached
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.asFlow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

public data class CurrentOccupancy(
    val connections: Int = 0,
    val presenceMembers: Int = 0,
)

/**
 * @return current occupancy
 */
@ExperimentalChatApi
@Composable
public fun Room.collectAsOccupancy(): CurrentOccupancy {
    var currentOccupancy by remember(this) { mutableStateOf(CurrentOccupancy()) }
    val roomStatus = collectAsStatus()

    LaunchedEffect(this, roomStatus) {
        if (roomStatus != Attached) return@LaunchedEffect

        val initialOccupancyGet = launch {
            runCatching {
                val occupancyEvent = occupancy.get()
                currentOccupancy = CurrentOccupancy(
                    connections = occupancyEvent.connections,
                    presenceMembers = occupancyEvent.presenceMembers,
                )
            }
        }

        occupancy.asFlow().collect {
            if (initialOccupancyGet.isActive) initialOccupancyGet.cancelAndJoin()
            currentOccupancy = CurrentOccupancy(
                connections = it.connections,
                presenceMembers = it.presenceMembers,
            )
        }
    }

    return currentOccupancy
}
