package com.ably.chat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ably.chat.ui.R
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A composable that displays a message input field with an emoji picker and send button.
 *
 * @param onSend Callback invoked when the user sends a message. The callback receives
 *               the message text and the input field is cleared after sending.
 * @param modifier Modifier to be applied to the input row.
 * @param placeholder The placeholder text to display when the input is empty.
 * @param sendButtonText The text to display on the send button.
 * @param enabled Whether the input and send button are enabled.
 * @param showEmojiPicker Whether to show the emoji picker button. Defaults to true.
 * @param emojis List of emojis to display in the picker. Defaults to [DefaultReactionEmojis].
 * @param onTextChanged Optional callback invoked when the text changes. Useful for typing indicators.
 */
@Composable
public fun MessageInput(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type a message...",
    sendButtonText: String = "Send",
    enabled: Boolean = true,
    showEmojiPicker: Boolean = true,
    emojis: List<String> = DefaultReactionEmojis,
    onTextChanged: ((String) -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var showEmojiDropdown by rememberSaveable { mutableStateOf(false) }
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography
    val canSend = text.isNotBlank() && enabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Emoji picker button
        if (showEmojiPicker) {
            Box {
                IconButton(
                    onClick = { showEmojiDropdown = true },
                    enabled = enabled,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_emoji),
                        contentDescription = "Emoji picker",
                        tint = if (enabled) colors.sendButton else colors.sendButtonDisabled,
                        modifier = Modifier.size(24.dp),
                    )
                }

                EmojiPickerDropdown(
                    expanded = showEmojiDropdown,
                    onDismiss = { showEmojiDropdown = false },
                    onEmojiSelected = { emoji ->
                        text += emoji
                        onTextChanged?.invoke(text)
                    },
                    emojis = emojis,
                )
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                onTextChanged?.invoke(newText)
            },
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = placeholder,
                    color = colors.inputPlaceholder,
                )
            },
            enabled = enabled,
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            textStyle = TextStyle(fontSize = typography.inputText),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.inputBackground,
                unfocusedContainerColor = colors.inputBackground,
                disabledContainerColor = colors.inputBackground,
                focusedTextColor = colors.inputContent,
                unfocusedTextColor = colors.inputContent,
            ),
        )

        Button(
            onClick = {
                if (canSend) {
                    onSend(text.trim())
                    text = ""
                    onTextChanged?.invoke("")
                }
            },
            enabled = canSend,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.sendButton,
                disabledContainerColor = colors.sendButtonDisabled,
            ),
        ) {
            Text(sendButtonText)
        }
    }
}
