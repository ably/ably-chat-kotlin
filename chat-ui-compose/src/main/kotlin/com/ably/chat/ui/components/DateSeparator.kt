package com.ably.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ably.chat.ui.theme.AblyChatTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A composable that displays a date separator in the message list.
 *
 * Shows "Today", "Yesterday", or a formatted date string depending on when the message was sent.
 *
 * @param timestamp The timestamp of the message in milliseconds.
 * @param modifier Modifier to be applied to the separator.
 * @param dateFormat The format pattern for dates older than yesterday. Defaults to "MMMM d, yyyy".
 */
@Composable
public fun DateSeparator(
    timestamp: Long,
    modifier: Modifier = Modifier,
    dateFormat: String = "MMMM d, yyyy",
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography
    val displayText = formatDateForSeparator(timestamp, dateFormat)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            color = colors.dateSeparatorText,
            fontSize = typography.dateSeparator,
            modifier = Modifier
                .background(
                    color = colors.dateSeparatorBackground,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * Determines whether a date separator should be shown between two messages.
 *
 * Returns true if the messages are from different days.
 *
 * @param currentTimestamp The timestamp of the current message.
 * @param previousTimestamp The timestamp of the previous message, or null if this is the first message.
 * @return True if a date separator should be shown before the current message.
 */
public fun shouldShowDateSeparator(currentTimestamp: Long, previousTimestamp: Long?): Boolean {
    if (previousTimestamp == null) return true

    val currentCal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }
    val previousCal = Calendar.getInstance().apply { timeInMillis = previousTimestamp }

    return currentCal.get(Calendar.YEAR) != previousCal.get(Calendar.YEAR) ||
        currentCal.get(Calendar.DAY_OF_YEAR) != previousCal.get(Calendar.DAY_OF_YEAR)
}

private fun formatDateForSeparator(timestamp: Long, dateFormat: String): String {
    val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = Calendar.getInstance()

    val isToday = messageCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
        messageCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

    if (isToday) return "Today"

    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = messageCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
        messageCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) return "Yesterday"

    val formatter = SimpleDateFormat(dateFormat, Locale.getDefault())
    return formatter.format(Date(timestamp))
}
