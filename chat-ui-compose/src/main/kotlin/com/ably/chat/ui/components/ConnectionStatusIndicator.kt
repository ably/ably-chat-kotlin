package com.ably.chat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ably.chat.ChatClient
import com.ably.chat.ConnectionStatus
import com.ably.chat.extensions.compose.collectAsStatus
import com.ably.chat.ui.theme.AblyChatColors
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * Display style for the connection status indicator.
 */
public enum class ConnectionStatusStyle {
    /**
     * Shows only a colored dot indicating connection state.
     */
    Dot,

    /**
     * Shows a dot with a short status label.
     */
    Badge,

    /**
     * Shows a full-width banner with status message.
     * Only visible when not connected.
     */
    Banner,
}

/**
 * A composable that displays the current connection status.
 *
 * This component subscribes to connection state changes and displays an appropriate
 * indicator based on the current status (connected, connecting, disconnected, etc.).
 *
 * @param chatClient The chat client to monitor for connection status.
 * @param modifier Modifier to be applied to the indicator.
 * @param style The display style for the indicator. Defaults to [ConnectionStatusStyle.Badge].
 * @param showWhenConnected Whether to show the indicator when connected. Defaults to false for Banner style.
 */
@Composable
public fun ConnectionStatusIndicator(
    chatClient: ChatClient,
    modifier: Modifier = Modifier,
    style: ConnectionStatusStyle = ConnectionStatusStyle.Badge,
    showWhenConnected: Boolean = style != ConnectionStatusStyle.Banner,
) {
    val connectionStatus by chatClient.connection.collectAsStatus()

    ConnectionStatusIndicatorContent(
        status = connectionStatus,
        modifier = modifier,
        style = style,
        showWhenConnected = showWhenConnected,
    )
}

/**
 * A composable that displays the connection status using a pre-collected state.
 *
 * Use this overload when you need to manage the connection state yourself or share it with other components.
 *
 * @param statusState The state containing the current connection status.
 * @param modifier Modifier to be applied to the indicator.
 * @param style The display style for the indicator. Defaults to [ConnectionStatusStyle.Badge].
 * @param showWhenConnected Whether to show the indicator when connected. Defaults to false for Banner style.
 */
@Composable
public fun ConnectionStatusIndicator(
    statusState: State<ConnectionStatus>,
    modifier: Modifier = Modifier,
    style: ConnectionStatusStyle = ConnectionStatusStyle.Badge,
    showWhenConnected: Boolean = style != ConnectionStatusStyle.Banner,
) {
    val status by statusState

    ConnectionStatusIndicatorContent(
        status = status,
        modifier = modifier,
        style = style,
        showWhenConnected = showWhenConnected,
    )
}

@Composable
private fun ConnectionStatusIndicatorContent(
    status: ConnectionStatus,
    modifier: Modifier,
    style: ConnectionStatusStyle,
    showWhenConnected: Boolean,
) {
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography

    // Don't show if connected and showWhenConnected is false
    if (status == ConnectionStatus.Connected && !showWhenConnected) {
        return
    }

    val statusColor by animateColorAsState(
        targetValue = getStatusColor(status, colors),
        animationSpec = tween(durationMillis = 300),
        label = "statusColor",
    )

    val statusText = getStatusText(status)
    val isAnimating = status == ConnectionStatus.Connecting ||
        status == ConnectionStatus.Disconnected ||
        status == ConnectionStatus.Suspended

    when (style) {
        ConnectionStatusStyle.Dot -> {
            StatusDot(
                color = statusColor,
                isAnimating = isAnimating,
                modifier = modifier,
            )
        }

        ConnectionStatusStyle.Badge -> {
            Row(
                modifier = modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusDot(
                    color = statusColor,
                    isAnimating = isAnimating,
                    size = 8.dp,
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = typography.timestamp,
                )
            }
        }

        ConnectionStatusStyle.Banner -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusDot(
                        color = statusColor,
                        isAnimating = isAnimating,
                        size = 8.dp,
                    )
                    Text(
                        text = getBannerText(status),
                        color = statusColor,
                        fontSize = typography.timestamp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(
    color: Color,
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val alpha = if (isAnimating) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        animatedAlpha
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

private fun getStatusColor(status: ConnectionStatus, colors: AblyChatColors): Color {
    return when (status) {
        ConnectionStatus.Connected -> colors.presenceOnline
        ConnectionStatus.Connecting -> colors.connectionConnecting
        ConnectionStatus.Disconnected -> colors.connectionDisconnected
        ConnectionStatus.Suspended -> colors.connectionDisconnected
        ConnectionStatus.Failed -> colors.connectionFailed
        ConnectionStatus.Initialized -> colors.connectionConnecting
        ConnectionStatus.Closing -> colors.connectionDisconnected
        ConnectionStatus.Closed -> colors.presenceOffline
    }
}

private fun getStatusText(status: ConnectionStatus): String {
    return when (status) {
        ConnectionStatus.Connected -> "Connected"
        ConnectionStatus.Connecting -> "Connecting"
        ConnectionStatus.Disconnected -> "Offline"
        ConnectionStatus.Suspended -> "Reconnecting"
        ConnectionStatus.Failed -> "Failed"
        ConnectionStatus.Initialized -> "Starting"
        ConnectionStatus.Closing -> "Closing"
        ConnectionStatus.Closed -> "Closed"
    }
}

private fun getBannerText(status: ConnectionStatus): String {
    return when (status) {
        ConnectionStatus.Connected -> "Connected"
        ConnectionStatus.Connecting -> "Connecting to chat..."
        ConnectionStatus.Disconnected -> "Connection lost. Reconnecting..."
        ConnectionStatus.Suspended -> "Connection suspended. Retrying..."
        ConnectionStatus.Failed -> "Connection failed. Please try again."
        ConnectionStatus.Initialized -> "Initializing..."
        ConnectionStatus.Closing -> "Closing connection..."
        ConnectionStatus.Closed -> "Disconnected"
    }
}
