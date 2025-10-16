package com.ably.chat.extensions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ably.chat.EmptyOccupancyData
import com.ably.chat.OccupancyData
import com.ably.chat.Room
import com.ably.chat.RoomStatus.Attached
import com.ably.chat.annotations.InternalChatApi
import com.ably.chat.asFlow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * @return current occupancy
 */
@Composable
public fun Room.collectAsOccupancy(): State<OccupancyData> {
    @OptIn(InternalChatApi::class)
    val currentOccupancy = remember(this) { mutableStateOf(EmptyOccupancyData) }
    val roomStatus by collectAsStatus()

    LaunchedEffect(this, roomStatus) {
        if (roomStatus != Attached) return@LaunchedEffect

        val initialOccupancyGet = launch {
            runCatching {
                currentOccupancy.value = occupancy.get()
            }
        }

        occupancy.asFlow().collect {
            if (initialOccupancyGet.isActive) initialOccupancyGet.cancelAndJoin()
            currentOccupancy.value = it.occupancy
        }
    }

    return currentOccupancy
}
