package com.ably.chat

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val DefaultStateDispatcher: CoroutineDispatcher = Dispatchers.Main
