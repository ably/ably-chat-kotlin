package com.ably.chat.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ably.chat.ChatClient
import com.ably.chat.Message
import com.ably.chat.MessageEventType
import com.ably.chat.MessageMetadata
import com.ably.chat.Room
import com.ably.chat.RoomOptions
import com.ably.chat.Typing
import com.ably.chat.example.ui.PresencePopup
import com.ably.chat.example.ui.theme.AblyChatExampleTheme
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.ClientOptions
import io.ably.lib.types.MessageAction
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val randomClientId = UUID.randomUUID().toString()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val realtimeClient = AblyRealtime(
            ClientOptions().apply {
                key = BuildConfig.ABLY_KEY
                clientId = randomClientId
                logLevel = 2
            },
        )

        val chatClient = ChatClient(realtimeClient)

        enableEdgeToEdge()
        setContent {
            AblyChatExampleTheme {
                App(chatClient)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(chatClient: ChatClient) {
    var showPopup by remember { mutableStateOf(false) }
    val room = remember {
        runBlocking {
            chatClient.rooms.get(
                Settings.ROOM_ID,
                RoomOptions.default,
            )
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val currentlyTyping by typingUsers(room.typing)

    DisposableEffect(Unit) {
        coroutineScope.launch {
            room.attach()
        }
        onDispose {
            coroutineScope.launch {
                room.detach()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(Settings.ROOM_ID) },
                actions = {
                    IconButton(onClick = { showPopup = true }) {
                        Icon(Icons.Default.Person, contentDescription = "Show members")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (currentlyTyping.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(start = 16.dp),
                    text = "Currently typing: ${currentlyTyping.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.Gray,
                    ),
                )
            }
            Chat(
                room,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (showPopup) {
            PresencePopup(chatClient, onDismiss = { showPopup = false })
        }
    }
}

@SuppressWarnings("LongMethod", "CognitiveComplexMethod")
@Composable
fun Chat(room: Room, modifier: Modifier = Modifier) {
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var sending by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var receivedReactions by remember { mutableStateOf<List<String>>(listOf()) }
    var edited: Message? by remember { mutableStateOf(null) }
    val updating = edited != null

    val handleSend = {
        coroutineScope.launch {
            room.messages.send(
                text = messageText.text,
            )
            messageText = TextFieldValue("")
            sending = false
        }
    }

    val handleEdit = handleEdit@{
        val editedMessage = edited ?: return@handleEdit
        coroutineScope.launch {
            room.messages.update(
                editedMessage.copy(text = messageText.text),
            )
            messageText = TextFieldValue("")
            edited = null
            sending = false
        }
    }

    DisposableEffect(Unit) {
        val subscription = room.messages.subscribe { event ->
            when (event.type) {
                MessageEventType.Created -> {
                    messages += event.message
                    coroutineScope.launch {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                MessageEventType.Updated -> messages = messages.map {
                    if (it.serial != event.message.serial) it else event.message
                }

                MessageEventType.Deleted -> messages = messages.filter {
                    it.serial != event.message.serial
                }
            }
        }

        coroutineScope.launch {
            messages = subscription.getPreviousMessages().items.reversed()
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }

        onDispose {
            subscription.unsubscribe()
        }
    }

    DisposableEffect(Unit) {
        val subscription = room.reactions.subscribe {
            receivedReactions += it.type
        }

        onDispose {
            subscription.unsubscribe()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            userScrollEnabled = true,
            state = listState,
        ) {
            items(messages.size) { index ->
                MessageBubble(
                    message = messages[index],
                    onEdit = {
                        edited = messages[index]
                        messageText = TextFieldValue(messages[index].text)
                    },
                    onDelete = {
                        coroutineScope.launch {
                            room.messages.delete(messages[index])
                        }
                    },
                )
            }
        }

        ChatInputField(
            updating = updating,
            sending = sending,
            messageInput = messageText,
            onMessageChange = {
                messageText = it
                coroutineScope.launch {
                    room.typing.start()
                }
            },
            onSendClick = {
                sending = true
                when {
                    updating -> handleEdit()
                    else -> handleSend()
                }
            },
            onReactionClick = {
                coroutineScope.launch {
                    room.reactions.send(type = "\uD83D\uDC4D")
                }
            },
        )
        if (receivedReactions.isNotEmpty()) {
            Text("Received reactions: ${receivedReactions.joinToString()}", modifier = Modifier.padding(16.dp))
        }
    }
}

@Suppress("LongMethod")
@Composable
fun MessageBubble(message: Message, onEdit: () -> Unit = {}, onDelete: () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    var confirmationDialogShown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = if (message.clientId == randomClientId) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (message.clientId != randomClientId) Color.Blue else Color.Gray,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text = message.text,
                color = Color.White,
            )
        }
        if (message.clientId == randomClientId) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            expanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            expanded = false
                            confirmationDialogShown = true
                        },
                    )
                }
            }
        }
        // Delete Confirmation Dialog
        if (confirmationDialogShown) {
            AlertDialog(
                onDismissRequest = { confirmationDialogShown = false },
                title = { Text("Delete Message") },
                text = { Text("Are you sure you want to delete this message?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDelete()
                            confirmationDialogShown = false
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(onClick = { confirmationDialogShown = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
fun ChatInputField(
    sending: Boolean = false,
    updating: Boolean = false,
    messageInput: TextFieldValue,
    onMessageChange: (TextFieldValue) -> Unit,
    onSendClick: () -> Unit,
    onReactionClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = messageInput,
            onValueChange = onMessageChange,
            modifier = Modifier
                .weight(1f)
                .background(Color.White),
            placeholder = { Text("Type a message...") },
        )
        when {
            updating -> Button(enabled = !sending, onClick = onSendClick) {
                Text("Update")
            }

            messageInput.text.isEmpty() -> Button(onClick = onReactionClick) {
                Text("\uD83D\uDC4D")
            }

            else -> Button(enabled = !sending, onClick = onSendClick) {
                Text("Send")
            }
        }
    }
}

@Composable
fun typingUsers(typing: Typing): State<Set<String>> {
    val currentlyTyping = remember { mutableStateOf(emptySet<String>()) }

    DisposableEffect(typing) {
        val subscription = typing.subscribe { typingEvent ->
            currentlyTyping.value = typingEvent.currentlyTyping - randomClientId
        }

        onDispose {
            subscription.unsubscribe()
        }
    }

    return currentlyTyping
}

@Preview
@Composable
fun MessageBubblePreview() {
    AblyChatExampleTheme {
        MessageBubble(
            message = Message(
                text = "Hello World!",
                serial = "fake",
                roomId = "roomId",
                clientId = "clientId",
                createdAt = System.currentTimeMillis(),
                metadata = MessageMetadata(),
                headers = mapOf(),
                action = MessageAction.MESSAGE_CREATE,
                version = "fake",
                timestamp = System.currentTimeMillis(),
            ),
        )
    }
}

@Preview
@Composable
fun ChatInputPreview() {
    AblyChatExampleTheme {
        ChatInputField(
            sending = false,
            messageInput = TextFieldValue(""),
            onMessageChange = {},
            onSendClick = {},
            onReactionClick = {},
        )
    }
}
