package com.ably.chat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Represents resolved avatar data for a client.
 *
 * @property imageUrl The URL of the avatar image, or null to use initials fallback.
 * @property displayName Optional display name to use for initials instead of clientId.
 */
public data class AvatarData(
    val imageUrl: String? = null,
    val displayName: String? = null,
)

/**
 * A function type that resolves avatar data for a given clientId.
 *
 * This can be a suspend function that fetches data from a remote source,
 * or a synchronous function that looks up cached data.
 */
public typealias AvatarResolver = suspend (clientId: String) -> AvatarData?

/**
 * Configuration options for the avatar provider.
 *
 * @property resolver A suspend function that resolves avatar data for a clientId.
 * @property cacheEnabled Whether to cache resolved avatars. Defaults to true.
 */
@Stable
public class AvatarProviderOptions(
    public val resolver: AvatarResolver,
    public val cacheEnabled: Boolean = true,
)

/**
 * Internal state holder for the avatar provider.
 */
@Stable
internal class AvatarProviderState(
    private val options: AvatarProviderOptions,
    private val scope: CoroutineScope,
) {
    private val cache = mutableStateMapOf<String, AvatarData?>()
    private val pending = mutableSetOf<String>()

    /**
     * Gets the cached avatar data for a clientId, or triggers resolution if not cached.
     */
    fun getAvatarData(clientId: String): AvatarData? {
        // Return cached value if available
        if (options.cacheEnabled && cache.containsKey(clientId)) {
            return cache[clientId]
        }

        // Start resolution if not already pending
        if (clientId !in pending) {
            pending.add(clientId)
            scope.launch {
                try {
                    val data = options.resolver(clientId)
                    if (options.cacheEnabled) {
                        cache[clientId] = data
                    }
                } finally {
                    pending.remove(clientId)
                }
            }
        }

        return null
    }

    /**
     * Clears the avatar cache.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Removes a specific clientId from the cache.
     */
    fun invalidate(clientId: String) {
        cache.remove(clientId)
    }
}

/**
 * CompositionLocal for providing avatar resolution throughout the component tree.
 */
internal val LocalAvatarProvider = compositionLocalOf<AvatarProviderState?> { null }

/**
 * Provides custom avatar resolution for all [Avatar] components within its content.
 *
 * The resolver function is called for each unique clientId to fetch avatar data.
 * Results are cached by default to avoid repeated network requests.
 *
 * Example usage:
 * ```kotlin
 * AvatarProvider(
 *     options = AvatarProviderOptions(
 *         resolver = { clientId ->
 *             // Fetch from your user service
 *             val user = userService.getUser(clientId)
 *             AvatarData(
 *                 imageUrl = user?.avatarUrl,
 *                 displayName = user?.displayName
 *             )
 *         }
 *     )
 * ) {
 *     // All Avatar components here will use the custom resolver
 *     MessageList(room = room)
 * }
 * ```
 *
 * @param options Configuration including the resolver function and caching options.
 * @param content The composable content that will have access to custom avatar resolution.
 */
@Composable
public fun AvatarProvider(
    options: AvatarProviderOptions,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember(options) {
        AvatarProviderState(options, scope)
    }

    CompositionLocalProvider(
        LocalAvatarProvider provides state,
        content = content,
    )
}

/**
 * Creates and remembers [AvatarProviderOptions] with the given resolver.
 *
 * @param cacheEnabled Whether to cache resolved avatars. Defaults to true.
 * @param resolver A suspend function that resolves avatar data for a clientId.
 */
@Composable
public fun rememberAvatarProviderOptions(
    cacheEnabled: Boolean = true,
    resolver: AvatarResolver,
): AvatarProviderOptions {
    return remember(cacheEnabled, resolver) {
        AvatarProviderOptions(
            resolver = resolver,
            cacheEnabled = cacheEnabled,
        )
    }
}
