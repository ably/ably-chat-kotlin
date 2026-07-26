package com.ably.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ably.chat.ui.R
import com.ably.chat.ui.theme.AblyChatTheme
import kotlin.math.absoluteValue

/**
 * Represents information about a chat room for display in the room list.
 *
 * @property id The unique identifier of the room.
 * @property displayName Optional display name for the room. If null, [id] is shown.
 * @property lastMessage Optional preview of the last message in the room.
 * @property lastMessageTime Optional timestamp string for the last message.
 * @property unreadCount Number of unread messages in the room.
 * @property isActive Whether this room is currently active/selected.
 */
@Stable
public data class RoomInfo(
    val id: String,
    val displayName: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0,
    val isActive: Boolean = false,
)

/**
 * A composable that displays a list of chat rooms with selection and management options.
 *
 * This component provides:
 * - A scrollable list of rooms with unread badges
 * - Room selection with visual feedback
 * - Optional "Add Room" button with dialog
 * - Optional "Leave Room" action via swipe or long-press
 *
 * @param rooms The list of rooms to display.
 * @param onRoomSelected Callback invoked when a room is selected.
 * @param modifier Modifier to be applied to the list.
 * @param showAddRoom Whether to show the "Add Room" button. Defaults to true.
 * @param onAddRoom Callback invoked when the user wants to add a new room.
 * @param showLeaveRoom Whether to show the "Leave Room" option. Defaults to true.
 * @param onLeaveRoom Callback invoked when the user wants to leave a room.
 * @param header Optional custom header content.
 */
@Composable
public fun RoomList(
    rooms: List<RoomInfo>,
    onRoomSelected: (RoomInfo) -> Unit,
    modifier: Modifier = Modifier,
    showAddRoom: Boolean = true,
    onAddRoom: ((String) -> Unit)? = null,
    showLeaveRoom: Boolean = true,
    onLeaveRoom: ((RoomInfo) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = AblyChatTheme.colors
    var showAddRoomDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Header
        if (header != null) {
            header()
        } else {
            RoomListHeader(
                roomCount = rooms.size,
                showAddButton = showAddRoom && onAddRoom != null,
                onAddClick = { showAddRoomDialog = true },
            )
        }

        HorizontalDivider(color = colors.dateSeparatorBackground)

        // Room list
        LazyColumn {
            items(
                items = rooms,
                key = { it.id },
            ) { room ->
                RoomListItem(
                    room = room,
                    onClick = { onRoomSelected(room) },
                    showLeaveOption = showLeaveRoom && onLeaveRoom != null,
                    onLeave = { onLeaveRoom?.invoke(room) },
                )
            }
        }
    }

    // Add Room Dialog
    if (showAddRoomDialog && onAddRoom != null) {
        AddRoomDialog(
            onDismiss = { showAddRoomDialog = false },
            onConfirm = { roomName ->
                onAddRoom(roomName)
                showAddRoomDialog = false
            },
        )
    }
}

@Composable
private fun RoomListHeader(
    roomCount: Int,
    showAddButton: Boolean,
    onAddClick: () -> Unit,
) {
    val colors = AblyChatTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (roomCount == 1) "1 Room" else "$roomCount Rooms",
            color = colors.menuContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (showAddButton) {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_room),
                    contentDescription = "Add room",
                    tint = colors.sendButton,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun RoomListItem(
    room: RoomInfo,
    onClick: () -> Unit,
    showLeaveOption: Boolean,
    onLeave: () -> Unit,
) {
    val colors = AblyChatTheme.colors
    var showLeaveDialog by remember { mutableStateOf(false) }

    val backgroundColor = if (room.isActive) {
        colors.reactionBackgroundSelected
    } else {
        colors.dialogBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Room avatar/icon
        RoomAvatar(roomId = room.id)

        Spacer(modifier = Modifier.width(12.dp))

        // Room info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = room.displayName ?: room.id,
                    color = colors.menuContent,
                    fontSize = 15.sp,
                    fontWeight = if (room.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                room.lastMessageTime?.let { time ->
                    Text(
                        text = time,
                        color = colors.timestamp,
                        fontSize = 12.sp,
                    )
                }
            }

            if (room.lastMessage != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = room.lastMessage,
                    color = colors.timestamp,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Unread badge
        if (room.unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            UnreadBadge(count = room.unreadCount)
        }

        // Leave button
        if (showLeaveOption) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { showLeaveDialog = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_leave_room),
                    contentDescription = "Leave room",
                    tint = colors.timestamp,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    // Leave confirmation dialog
    if (showLeaveDialog) {
        ConfirmDialog(
            title = "Leave Room",
            message = "Are you sure you want to leave \"${room.displayName ?: room.id}\"?",
            confirmText = "Leave",
            onConfirm = {
                onLeave()
                showLeaveDialog = false
            },
            onDismiss = { showLeaveDialog = false },
        )
    }
}

@Composable
private fun RoomAvatar(roomId: String) {
    val colors = AblyChatTheme.colors

    // Generate color from room ID
    val avatarColors = listOf(
        0xFF3B82F6, // Blue
        0xFF10B981, // Emerald
        0xFFF59E0B, // Amber
        0xFFEF4444, // Red
        0xFF8B5CF6, // Violet
        0xFF06B6D4, // Cyan
        0xFFF97316, // Orange
        0xFFEC4899, // Pink
    )
    val hash = roomId.hashCode().absoluteValue
    val bgColor = androidx.compose.ui.graphics.Color(avatarColors[hash % avatarColors.size])

    // Get initials (first letter or #)
    val initial = roomId.firstOrNull()?.uppercaseChar()?.toString() ?: "#"

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = colors.avatarText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    val colors = AblyChatTheme.colors
    val displayCount = if (count > 99) "99+" else count.toString()

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.sendButton)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayCount,
            color = colors.scrollFabContent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AddRoomDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = AblyChatTheme.colors
    var roomName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.dialogBackground,
        title = {
            Text(
                text = "Add Room",
                color = colors.menuContent,
            )
        },
        text = {
            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it },
                placeholder = {
                    Text(
                        text = "Room name",
                        color = colors.inputPlaceholder,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (roomName.isNotBlank()) {
                            onConfirm(roomName.trim())
                        }
                    },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.inputBackground,
                    unfocusedContainerColor = colors.inputBackground,
                    focusedTextColor = colors.inputContent,
                    unfocusedTextColor = colors.inputContent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (roomName.isNotBlank()) {
                        onConfirm(roomName.trim())
                    }
                },
                enabled = roomName.isNotBlank(),
            ) {
                Text(
                    text = "Add",
                    color = if (roomName.isNotBlank()) colors.sendButton else colors.sendButtonDisabled,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = colors.menuContent,
                )
            }
        },
    )
}
