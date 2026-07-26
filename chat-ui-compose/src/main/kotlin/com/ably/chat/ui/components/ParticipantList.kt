package com.ably.chat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ably.chat.PresenceMember
import com.ably.chat.Room
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.annotations.InternalChatApi
import com.ably.chat.extensions.compose.collectAsCurrentlyTyping
import com.ably.chat.extensions.compose.collectAsPresenceMembers
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A composable that displays a list of participants in a chat room.
 *
 * Automatically subscribes to presence and typing updates from the provided [Room].
 * Current user is sorted first, followed by other participants sorted alphabetically.
 *
 * @param room The chat room to display participants for.
 * @param modifier Modifier to be applied to the list.
 * @param header Optional custom header content.
 */
@OptIn(ExperimentalChatApi::class, InternalChatApi::class)
@Composable
public fun ParticipantList(
    room: Room,
    modifier: Modifier = Modifier,
    header: (@Composable (Int) -> Unit)? = null,
) {
    val presenceMembers by room.collectAsPresenceMembers()
    val typingClientIds by room.collectAsCurrentlyTyping()

    ParticipantListContent(
        members = presenceMembers,
        typingClientIds = typingClientIds,
        currentClientId = room.clientId,
        modifier = modifier,
        header = header,
    )
}

/**
 * A composable that displays a list of participants using pre-collected state.
 *
 * Use this overload when you need to manage the presence state yourself
 * or share it with other components.
 *
 * @param members State containing the list of presence members.
 * @param typingClientIds State containing the set of client IDs currently typing.
 * @param currentClientId The clientId of the current user.
 * @param modifier Modifier to be applied to the list.
 * @param header Optional custom header content.
 */
@Composable
public fun ParticipantList(
    members: State<List<PresenceMember>>,
    typingClientIds: State<Set<String>>,
    currentClientId: String,
    modifier: Modifier = Modifier,
    header: (@Composable (Int) -> Unit)? = null,
) {
    val membersList by members
    val typingSet by typingClientIds

    ParticipantListContent(
        members = membersList,
        typingClientIds = typingSet,
        currentClientId = currentClientId,
        modifier = modifier,
        header = header,
    )
}

/**
 * A composable that displays a list of participants from pre-built lists.
 *
 * @param members List of presence members.
 * @param typingClientIds Set of client IDs currently typing.
 * @param currentClientId The clientId of the current user.
 * @param modifier Modifier to be applied to the list.
 * @param header Optional custom header content.
 */
@Composable
public fun ParticipantList(
    members: List<PresenceMember>,
    typingClientIds: Set<String>,
    currentClientId: String,
    modifier: Modifier = Modifier,
    header: (@Composable (Int) -> Unit)? = null,
) {
    ParticipantListContent(
        members = members,
        typingClientIds = typingClientIds,
        currentClientId = currentClientId,
        modifier = modifier,
        header = header,
    )
}

@Composable
private fun ParticipantListContent(
    members: List<PresenceMember>,
    typingClientIds: Set<String>,
    currentClientId: String,
    modifier: Modifier = Modifier,
    header: (@Composable (Int) -> Unit)? = null,
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    // Sort: current user first, then alphabetically by clientId
    val sortedMembers by remember(members, currentClientId) {
        derivedStateOf {
            members.sortedWith(
                compareBy(
                    { it.clientId != currentClientId }, // Current user first
                    { it.clientId.lowercase() }, // Then alphabetically
                ),
            )
        }
    }

    Column(modifier = modifier) {
        // Header
        if (header != null) {
            header(members.size)
        } else {
            ParticipantListHeader(count = members.size)
        }

        HorizontalDivider(color = colors.dateSeparatorBackground)

        // Participant list
        LazyColumn {
            items(
                items = sortedMembers,
                key = { it.clientId },
            ) { member ->
                Participant(
                    member = member,
                    isCurrentUser = member.clientId == currentClientId,
                    isTyping = typingClientIds.contains(member.clientId),
                )
            }
        }
    }
}

@Composable
private fun ParticipantListHeader(count: Int) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    val headerText = when (count) {
        0 -> "No one in room"
        1 -> "1 person in room"
        else -> "$count people in room"
    }

    Text(
        text = headerText,
        color = colors.menuContent,
        fontSize = typography.participantName,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
