package com.ably.chat.example.uikit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ably.chat.ChatClient
import com.ably.chat.LogLevel
import com.ably.chat.Room
import com.ably.chat.MutableRoomOptions
import com.ably.chat.annotations.InternalChatApi
import com.ably.chat.occupancy
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.ui.components.Avatar
import com.ably.chat.ui.components.AvatarData
import com.ably.chat.ui.components.AvatarProvider
import com.ably.chat.ui.components.AvatarProviderOptions
import com.ably.chat.ui.components.AvatarSize
import com.ably.chat.ui.components.ChatWindow
import com.ably.chat.ui.components.ConnectionStatusIndicator
import com.ably.chat.ui.components.ConnectionStatusStyle
import com.ably.chat.ui.components.MessageInput
import com.ably.chat.ui.components.MessageList
import com.ably.chat.ui.components.MessageListSkeleton
import com.ably.chat.ui.components.OccupancyBadge
import com.ably.chat.ui.components.ParticipantList
import com.ably.chat.ui.components.PresenceIndicator
import com.ably.chat.ui.components.RoomInfo
import com.ably.chat.ui.components.RoomList
import com.ably.chat.ui.components.ThemeToggleButton
import com.ably.chat.ui.components.TypingIndicator
import com.ably.chat.ui.theme.AblyChatColors
import com.ably.chat.ui.theme.AblyChatTheme
import com.ably.chat.ui.theme.rememberAblyChatThemeState
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.ClientOptions
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Example app demonstrating the Ably Chat UI Kit components.
 *
 * This app shows how to build a complete chat interface using the
 * components from the chat-ui-compose module including:
 * - ChatWindow (unified chat component)
 * - Avatar with PresenceIndicator
 * - OccupancyBadge
 * - ParticipantList
 * - Message reactions, editing, and deletion
 */
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalChatApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clientId = UUID.randomUUID().toString()

        val realtimeClient = AblyRealtime(
            ClientOptions().apply {
                key = BuildConfig.ABLY_KEY
                this.clientId = clientId
            },
        )

        val chatClient = ChatClient(realtimeClient) {
            logLevel = LogLevel.Debug
        }

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ChatApp(chatClient)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalChatApi::class, InternalChatApi::class)
@Composable
fun ChatApp(chatClient: ChatClient) {
    var currentRoom by remember { mutableStateOf<Room?>(null) }
    var currentRoomId by remember { mutableStateOf("ui-kit-demo") }
    var showParticipants by remember { mutableStateOf(false) }
    var showRoomList by remember { mutableStateOf(false) }
    var useWildTheme by remember { mutableStateOf(false) }
    val participantsSheetState = rememberModalBottomSheetState()
    val roomListSheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()

    // Room list state
    val rooms = remember {
        mutableStateListOf(
            RoomInfo(id = "ui-kit-demo", displayName = "UI Kit Demo", isActive = true),
            RoomInfo(id = "general", displayName = "General Chat"),
            RoomInfo(id = "random", displayName = "Random", lastMessage = "Last message preview..."),
        )
    }

    // Theme state with persistence
    val themeState = rememberAblyChatThemeState()

    // Random names to assign to users
    val randomNames = remember {
        listOf(
            "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry",
            "Ivy", "Jack", "Kate", "Leo", "Mia", "Noah", "Olivia", "Peter",
            "Quinn", "Ruby", "Sam", "Tina", "Uma", "Victor", "Wendy", "Xavier",
            "Yara", "Zach", "Emma", "Liam", "Sophia", "Mason", "Ava", "Ethan",
        )
    }

    // Map to store assigned names for each clientId
    val userNames = remember { mutableStateMapOf<String, String>() }

    // Custom avatar provider - assigns random names to users
    val avatarProviderOptions = remember(userNames) {
        AvatarProviderOptions(
            resolver = { clientId ->
                // Get or assign a random name for this client
                val displayName = userNames.getOrPut(clientId) {
                    randomNames.random()
                }
                AvatarData(displayName = displayName)
            },
            cacheEnabled = true,
        )
    }

    // Switch room when selected
    LaunchedEffect(currentRoomId) {
        // Release previous room if exists
        currentRoom?.let {
            try {
                it.presence.leave()
                chatClient.rooms.release(it.name)
            } catch (_: Exception) {
                // Ignore release errors
            }
        }

        // Get new room with occupancy events enabled
        val roomOptions = MutableRoomOptions().apply {
            occupancy { enableEvents = true }
        }
        val chatRoom = chatClient.rooms.get(currentRoomId, roomOptions)
        chatRoom.attach()
        // Enter presence so we show up in the participant list
        chatRoom.presence.enter()
        currentRoom = chatRoom

        // Update room list to show active room
        val index = rooms.indexOfFirst { it.id == currentRoomId }
        if (index >= 0) {
            rooms.forEachIndexed { i, room ->
                rooms[i] = room.copy(isActive = i == index)
            }
        }
    }

    // Wild color schemes - neon/cyberpunk vibes!
    val wildLightColors = AblyChatColors(
        ownMessageBackground = Color(0xFFFF6B6B),      // Coral red
        ownMessageContent = Color.White,
        otherMessageBackground = Color(0xFF4ECDC4),    // Teal
        otherMessageContent = Color(0xFF1A1A2E),
        timestamp = Color(0xFFFF9F1C),                  // Orange
        clientId = Color(0xFFE056FD),                   // Purple
        inputBackground = Color(0xFFFFF3E0),            // Light peach
        inputContent = Color(0xFF1A1A2E),
        inputPlaceholder = Color(0xFFFF9F1C),
        sendButton = Color(0xFFFF6B6B),                 // Coral
        sendButtonDisabled = Color(0xFFCCCCCC),
        dateSeparatorBackground = Color(0xFFDFF9FB),    // Light cyan
        dateSeparatorText = Color(0xFF00CEC9),          // Cyan
        scrollFabBackground = Color(0xFFE056FD),        // Purple
        scrollFabContent = Color.White,
        menuBackground = Color(0xFFFFF9C4),             // Light yellow
        menuContent = Color(0xFF1A1A2E),
        avatarBackground = Color(0xFFFF9F1C),           // Orange
        avatarText = Color.White,
        presenceOnline = Color(0xFF00FF7F),             // Neon green
        presenceOffline = Color(0xFFFF6B6B),            // Coral
        reactionBackground = Color(0xFFE1BEE7),         // Light purple
        reactionBackgroundSelected = Color(0xFFCE93D8), // Medium purple
        reactionText = Color(0xFF4A148C),               // Dark purple
        dialogBackground = Color(0xFFFFF9C4),           // Light yellow
        dialogDestructive = Color(0xFFFF1744),          // Neon red
        connectionConnecting = Color(0xFFFFEB3B),       // Neon yellow
        connectionDisconnected = Color(0xFFFF9F1C),     // Orange
        connectionFailed = Color(0xFFFF1744),           // Neon red
        shimmerBase = Color(0xFFE1BEE7),                // Light purple
        shimmerHighlight = Color(0xFFFFF9C4),           // Light yellow
    )

    val wildDarkColors = AblyChatColors(
        ownMessageBackground = Color(0xFFFF0080),       // Hot pink
        ownMessageContent = Color.White,
        otherMessageBackground = Color(0xFF00F5D4),     // Neon cyan
        otherMessageContent = Color(0xFF0D0D0D),
        timestamp = Color(0xFFFFE66D),                  // Neon yellow
        clientId = Color(0xFFB388FF),                   // Light purple
        inputBackground = Color(0xFF1A1A2E),            // Dark purple-blue
        inputContent = Color(0xFFE0E0E0),
        inputPlaceholder = Color(0xFF7B68EE),           // Medium purple
        sendButton = Color(0xFFFF0080),                 // Hot pink
        sendButtonDisabled = Color(0xFF4A4A4A),
        dateSeparatorBackground = Color(0xFF2D2D44),    // Dark blue
        dateSeparatorText = Color(0xFF00F5D4),          // Neon cyan
        scrollFabBackground = Color(0xFFB388FF),        // Light purple
        scrollFabContent = Color(0xFF0D0D0D),
        menuBackground = Color(0xFF1A1A2E),             // Dark purple-blue
        menuContent = Color(0xFFE0E0E0),
        avatarBackground = Color(0xFFFF6B6B),           // Coral
        avatarText = Color.White,
        presenceOnline = Color(0xFF39FF14),             // Neon green
        presenceOffline = Color(0xFFFF0080),            // Hot pink
        reactionBackground = Color(0xFF2D2D44),         // Dark blue
        reactionBackgroundSelected = Color(0xFF4A4A6A), // Medium dark blue
        reactionText = Color(0xFF00F5D4),               // Neon cyan
        dialogBackground = Color(0xFF1A1A2E),           // Dark purple-blue
        dialogDestructive = Color(0xFFFF1744),          // Neon red
        connectionConnecting = Color(0xFFFFE66D),       // Neon yellow
        connectionDisconnected = Color(0xFFFF9F1C),     // Orange
        connectionFailed = Color(0xFFFF1744),           // Neon red
        shimmerBase = Color(0xFF2D2D44),                // Dark blue
        shimmerHighlight = Color(0xFF4A4A6A),           // Medium dark blue
    )

    // Wrap the entire app in the theme with state management
    AblyChatTheme(
        themeState = themeState,
        lightColors = if (useWildTheme) wildLightColors else AblyChatColors.light(),
        darkColors = if (useWildTheme) wildDarkColors else AblyChatColors.dark(),
    ) {
        // Wrap in AvatarProvider to demo custom avatar resolution
        AvatarProvider(options = avatarProviderOptions) {
            val colors = AblyChatTheme.colors

            Scaffold(
                containerColor = colors.dialogBackground,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rooms.find { it.isActive }?.displayName ?: "Chat")
                                currentRoom?.let { chatRoom ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OccupancyBadge(room = chatRoom)
                                }
                            }
                        },
                        navigationIcon = {
                            // Room list button
                            IconButton(onClick = { showRoomList = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Room list",
                                    tint = Color.White,
                                )
                            }
                        },
                        actions = {
                            // Wild theme toggle button
                            IconButton(onClick = { useWildTheme = !useWildTheme }) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = if (useWildTheme) "Use default theme" else "Use wild theme",
                                    tint = if (useWildTheme) Color(0xFFFF0080) else Color.White,
                                )
                            }
                            // Theme toggle button (light/dark)
                            ThemeToggleButton(
                                themeState = themeState,
                            )
                            // Participant list button
                            IconButton(onClick = { showParticipants = true }) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Participants",
                                    tint = Color.White,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.sendButton,
                            titleContentColor = Color.White,
                        ),
                    )
                },
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    // Connection status banner (auto-hides when connected)
                    ConnectionStatusIndicator(
                        chatClient = chatClient,
                        style = ConnectionStatusStyle.Banner,
                    )

                    currentRoom?.let { chatRoom ->
                        // Feature-rich chat screen with ChatWindow
                        FeatureRichChatScreen(
                            room = chatRoom,
                            modifier = Modifier.weight(1f),
                        )
                    } ?: run {
                        // Show skeleton loading while room is being initialized
                        MessageListSkeleton(
                            showAvatars = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Room list bottom sheet
            if (showRoomList) {
                ModalBottomSheet(
                    onDismissRequest = { showRoomList = false },
                    sheetState = roomListSheetState,
                    containerColor = colors.dialogBackground,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                    ) {
                        RoomList(
                            rooms = rooms,
                            onRoomSelected = { selectedRoom ->
                                currentRoomId = selectedRoom.id
                                coroutineScope.launch {
                                    roomListSheetState.hide()
                                    showRoomList = false
                                }
                            },
                            onAddRoom = { roomName ->
                                rooms.add(RoomInfo(id = roomName, displayName = roomName))
                            },
                            onLeaveRoom = { roomToLeave ->
                                if (roomToLeave.id != currentRoomId) {
                                    rooms.removeIf { it.id == roomToLeave.id }
                                }
                            },
                            modifier = Modifier.height(400.dp),
                        )
                    }
                }
            }

            // Participant list bottom sheet
            if (showParticipants) {
                ModalBottomSheet(
                    onDismissRequest = { showParticipants = false },
                    sheetState = participantsSheetState,
                    containerColor = colors.dialogBackground,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Participants",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.menuContent,
                            )
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    participantsSheetState.hide()
                                    showParticipants = false
                                }
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = colors.menuContent,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        currentRoom?.let { chatRoom ->
                            ParticipantList(
                                room = chatRoom,
                                modifier = Modifier.height(300.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feature-rich chat screen using ChatWindow with all features enabled.
 *
 * Demonstrates:
 * - ChatWindow unified component
 * - Message reactions
 * - Message editing and deletion
 * - Typing indicators
 * - Custom header with Avatar
 * - Light/dark theme support
 */
@OptIn(ExperimentalChatApi::class, InternalChatApi::class)
@Composable
fun FeatureRichChatScreen(
    room: Room,
    modifier: Modifier = Modifier,
) {
    // Theme is already provided by parent, so just use ChatWindow directly
    ChatWindow(
        room = room,
        modifier = modifier.fillMaxSize(),
        showTypingIndicator = true,
        showDateSeparators = true,
        showScrollToBottom = true,
        enableReactions = true,
        enableEditing = true,
        enableDeletion = true,
        headerContent = {
            // Custom header showing current user info
            ChatHeader(clientId = room.clientId)
        },
    )
}

/**
 * Custom header component showing the current user's avatar and info.
 */
@Composable
private fun ChatHeader(clientId: String) {
    val colors = AblyChatTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(
                clientId = clientId,
                size = AvatarSize.Small,
            )
            PresenceIndicator(
                isOnline = true,
                modifier = Modifier.align(Alignment.BottomEnd),
                size = 10.dp,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = "Logged in as",
                fontSize = 10.sp,
                color = colors.timestamp,
            )
            Text(
                text = clientId.take(8) + "...",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.menuContent,
            )
        }
    }
}

/**
 * Simple chat screen using UI Kit with default styling.
 *
 * This demonstrates the minimal code needed to create a fully functional chat.
 */
@Composable
fun SimpleChatScreen(room: Room, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()

    AblyChatTheme {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // MessageList handles:
            // - Subscribing to new messages
            // - Loading history with pagination
            // - Auto-loading more when scrolling up
            MessageList(
                room = room,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )

            // TypingIndicator shows who is currently typing
            // - Automatically subscribes to typing events
            // - Shows "You" when the current user is typing
            // - Animated dots indicator
            TypingIndicator(
                room = room,
                includeCurrentUser = true,
            )

            // MessageInput handles:
            // - Text input with placeholder
            // - Send button (enabled only when text is entered)
            MessageInput(
                onSend = { text ->
                    coroutineScope.launch {
                        room.messages.send(text = text)
                    }
                },
                onTextChanged = { text ->
                    // Trigger typing indicators when text changes
                    coroutineScope.launch {
                        if (text.isEmpty()) {
                            room.typing.stop()
                        } else {
                            room.typing.keystroke()
                        }
                    }
                },
            )
        }
    }
}

/**
 * Custom themed chat screen demonstrating theme customization.
 *
 * Shows how to customize colors to match your app's branding.
 */
@Composable
fun CustomThemedChatScreen(room: Room, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()

    // Custom color scheme - purple/pink theme
    val customColors = AblyChatColors(
        ownMessageBackground = Color(0xFF7C3AED),
        ownMessageContent = Color.White,
        otherMessageBackground = Color(0xFFEC4899),
        otherMessageContent = Color.White,
        timestamp = Color(0xFF9CA3AF),
        clientId = Color(0xFF6B7280),
        inputBackground = Color(0xFFF3F4F6),
        inputContent = Color(0xFF1F2937),
        inputPlaceholder = Color(0xFF9CA3AF),
        sendButton = Color(0xFF7C3AED),
        sendButtonDisabled = Color(0xFFD1D5DB),
        dateSeparatorBackground = Color(0xFFF3E8FF),
        dateSeparatorText = Color(0xFF7C3AED),
        scrollFabBackground = Color(0xFF7C3AED),
        scrollFabContent = Color.White,
        menuBackground = Color.White,
        menuContent = Color(0xFF1F2937),
        avatarBackground = Color(0xFF9CA3AF),
        avatarText = Color.White,
        presenceOnline = Color(0xFF22C55E),
        presenceOffline = Color(0xFF9CA3AF),
        reactionBackground = Color(0xFFF3E8FF),
        reactionBackgroundSelected = Color(0xFFDDD6FE),
        reactionText = Color(0xFF374151),
        dialogBackground = Color.White,
        dialogDestructive = Color(0xFFDC2626),
        connectionConnecting = Color(0xFFF59E0B),
        connectionDisconnected = Color(0xFFF97316),
        connectionFailed = Color(0xFFDC2626),
        shimmerBase = Color(0xFFF3E8FF),
        shimmerHighlight = Color(0xFFFFFFFF),
    )

    AblyChatTheme(colors = customColors) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            MessageList(
                room = room,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )

            TypingIndicator(
                room = room,
                includeCurrentUser = true,
            )

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
                placeholder = "Say something...",
                sendButtonText = "Go",
            )
        }
    }
}
