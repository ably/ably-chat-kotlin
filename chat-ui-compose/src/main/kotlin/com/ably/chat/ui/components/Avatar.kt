package com.ably.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ably.chat.ui.theme.AblyChatTheme
import kotlin.math.absoluteValue

/**
 * Size options for the Avatar component.
 *
 * @property dp The size of the avatar in density-independent pixels.
 */
public enum class AvatarSize(public val dp: Dp) {
    Small(32.dp),
    Medium(40.dp),
    Large(48.dp),
    ExtraLarge(64.dp),
}

/**
 * A composable that displays a user avatar with an optional image or initials fallback.
 *
 * If [imageUrl] is provided and loads successfully, the image is displayed.
 * Otherwise, initials extracted from [clientId] are displayed with a background color
 * that is deterministically generated from the clientId hash.
 *
 * @param clientId The unique identifier of the user, used to generate initials and background color.
 * @param modifier Modifier to be applied to the avatar.
 * @param imageUrl Optional URL of the avatar image.
 * @param size The size of the avatar. Defaults to [AvatarSize.Medium].
 */
@Composable
public fun Avatar(
    clientId: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: AvatarSize = AvatarSize.Medium,
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    val initials = remember(clientId) { extractInitials(clientId) }
    val backgroundColor = remember(clientId) { generateColorFromClientId(clientId) }
    val context = LocalContext.current

    val fontSize = when (size) {
        AvatarSize.Small -> (typography.avatarInitials.value * 0.8f).sp
        AvatarSize.Medium -> typography.avatarInitials
        AvatarSize.Large -> (typography.avatarInitials.value * 1.2f).sp
        AvatarSize.ExtraLarge -> (typography.avatarInitials.value * 1.5f).sp
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar for $clientId",
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials,
                color = colors.avatarText,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Extracts initials from a clientId string.
 *
 * - If the clientId contains spaces, returns the first letter of the first two words
 * - Otherwise, returns the first two characters (or first character if single-char)
 */
private fun extractInitials(clientId: String): String {
    val trimmed = clientId.trim()
    if (trimmed.isEmpty()) return "?"

    val words = trimmed.split(" ", "_", "-", ".").filter { it.isNotEmpty() }

    return when {
        words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        trimmed.length >= 2 -> trimmed.take(2).uppercase()
        else -> trimmed.first().uppercaseChar().toString()
    }
}

/**
 * Generates a deterministic color from a clientId hash.
 * Uses a predefined palette of visually pleasing colors.
 */
private fun generateColorFromClientId(clientId: String): Color {
    val avatarColors = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFFEF4444), // Red
        Color(0xFF8B5CF6), // Violet
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF97316), // Orange
        Color(0xFFEC4899), // Pink
        Color(0xFF6366F1), // Indigo
        Color(0xFF14B8A6), // Teal
    )

    val hash = clientId.hashCode().absoluteValue
    return avatarColors[hash % avatarColors.size]
}
