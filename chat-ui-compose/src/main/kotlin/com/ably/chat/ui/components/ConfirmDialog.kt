package com.ably.chat.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * A composable that displays a confirmation dialog.
 *
 * Used for confirming destructive actions like message deletion.
 *
 * @param title The title text for the dialog.
 * @param message The message/body text for the dialog.
 * @param onConfirm Callback invoked when the confirm button is clicked.
 * @param onDismiss Callback invoked when the dialog is dismissed or cancel is clicked.
 * @param modifier Modifier to be applied to the dialog.
 * @param confirmText The text for the confirm button. Defaults to "Delete".
 * @param dismissText The text for the dismiss/cancel button. Defaults to "Cancel".
 * @param isDestructive Whether the confirm action is destructive (shows in red). Defaults to true.
 */
@Composable
public fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Delete",
    dismissText: String = "Cancel",
    isDestructive: Boolean = true,
) {
    val colors = AblyChatTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.dialogBackground,
        title = {
            Text(
                text = title,
                color = colors.menuContent,
            )
        },
        text = {
            Text(
                text = message,
                color = colors.timestamp,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(
                    text = confirmText,
                    color = if (isDestructive) colors.dialogDestructive else colors.sendButton,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissText,
                    color = colors.menuContent,
                )
            }
        },
    )
}

/**
 * A composable that displays a delete confirmation dialog.
 *
 * Convenience wrapper around [ConfirmDialog] with default delete messaging.
 *
 * @param onConfirm Callback invoked when delete is confirmed.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 * @param modifier Modifier to be applied to the dialog.
 * @param title The title text for the dialog. Defaults to "Delete message".
 * @param message The message/body text. Defaults to "Are you sure you want to delete this message?".
 */
@Composable
public fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Delete message",
    message: String = "Are you sure you want to delete this message? This action cannot be undone.",
) {
    ConfirmDialog(
        title = title,
        message = message,
        confirmText = "Delete",
        dismissText = "Cancel",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        isDestructive = true,
    )
}
