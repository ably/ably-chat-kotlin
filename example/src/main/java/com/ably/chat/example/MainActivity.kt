package com.ably.chat.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ably.chat.ChatClient
import com.ably.chat.LogLevel
import com.ably.chat.Room
import com.ably.chat.RoomReaction
import com.ably.chat.asFlow
import com.ably.chat.example.ui.PresencePopup
import com.ably.chat.example.ui.theme.AblyChatExampleTheme
import com.ably.chat.extensions.compose.collectAsCurrentlyTyping
import com.ably.chat.ui.components.MessageInput
import com.ably.chat.ui.components.ChatMessageList
import com.ably.chat.ui.theme.AblyChatTheme
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.ClientOptions
import java.util.UUID
import kotlinx.coroutines.launch

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

        val chatClient = ChatClient(realtimeClient) { logLevel = LogLevel.Trace }

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
    var room by remember { mutableStateOf<Room?>(null) }
    val currentlyTyping = room?.collectAsCurrentlyTyping()?.value ?: setOf()

    LaunchedEffect(Unit) {
        val chatRoom = chatClient.rooms.get(
            Settings.ROOM_ID,
        )
        chatRoom.attach()
        room = chatRoom
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
            room?.let {
                Chat(it, modifier = Modifier.padding(16.dp))
            }
        }

        room?.let {
            if (showPopup) {
                PresencePopup(it, onDismiss = { showPopup = false })
            }
        }
    }
}

/**
 * Chat screen demonstrating the use of the Ably Chat UI Kit components.
 *
 * This example shows how to use [ChatMessageList] and [MessageInput] from chat-ui-compose
 * to build a chat interface with minimal code.
 */
@Composable
fun Chat(room: Room, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val receivedReactions = remember { mutableStateListOf<RoomReaction>() }

    LaunchedEffect(Unit) {
        room.reactions.asFlow().collect {
            receivedReactions.add(it.reaction)
        }
    }

    AblyChatTheme {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ChatMessageList from UI Kit - handles pagination and message display
            ChatMessageList(
                room = room,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                scrollThreshold = 1,
                fetchSize = 15,
            )

            // MessageInput from UI Kit - handles text input and send button
            MessageInput(
                onSend = { text ->
                    coroutineScope.launch {
                        room.messages.send(text = text)
                    }
                },
                onTextChanged = { text ->
                    coroutineScope.launch {
                        if (text.isEmpty()) {
                            room.typing.stop()
                        } else {
                            room.typing.keystroke()
                        }
                    }
                },
            )

            if (receivedReactions.isNotEmpty()) {
                Text(
                    "Received reactions: ${receivedReactions.joinToString()}",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
