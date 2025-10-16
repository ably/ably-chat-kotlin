package com.ably.chat.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.ably.chat.Room
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.extensions.compose.collectAsPresenceMembers
import com.ably.chat.json.jsonObject
import kotlinx.coroutines.launch

@Suppress("LongMethod")
@OptIn(ExperimentalChatApi::class)
@Composable
fun PresencePopup(room: Room, onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val members by room.collectAsPresenceMembers()
    val presence = room.presence

    Popup(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentWidth(),
            ) {
                Text("Chat Members", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                members.forEach { member ->
                    BasicText("${member.clientId} - (${member.data?.get("status")})")
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    coroutineScope.launch {
                        presence.enter(
                            jsonObject {
                                put("status", "online")
                            },
                        )
                    }
                }) {
                    Text("Join")
                }
                Button(onClick = {
                    coroutineScope.launch {
                        presence.enter(
                            jsonObject {
                                put("status", "away")
                            },
                        )
                    }
                }) {
                    Text("Appear away")
                }
                Button(onClick = {
                    coroutineScope.launch {
                        presence.leave()
                    }
                }) {
                    Text("Leave")
                }
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
