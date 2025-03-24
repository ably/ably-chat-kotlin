package com.ably.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

class LaunchAsyncTest {

    private val typingScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    suspend fun launchMethod()  {
        typingScope.launch {
            throw Exception("Error")
        }.join()
    }

    suspend fun asyncMethod()  {
        typingScope.async {
            throw Exception("Error")
        }.await()
    }

    @Test
    fun `test coroutine launch`() = runTest {
        val exception = Assert.assertThrows(Exception::class.java) {
            runBlocking {
                launchMethod()
            }
        }
        Assert.assertEquals("Error", exception.message)
    }

    @Test
    fun `test coroutine async`() = runTest {
        val exception = Assert.assertThrows(Exception::class.java) {
            runBlocking {
                asyncMethod()
            }
        }
        Assert.assertEquals("Error", exception.message)
    }
}
