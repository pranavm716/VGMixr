package io.github.pranavm716.vgmixr

import app.cash.turbine.test
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolchainSmokeTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `runTest skips delays with virtual time`(): Unit = runTest {
        delay(1.hours)

        assertEquals(1.hours.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `turbine can read from StateFlow`(): Unit = runTest {
        val stateFlow = MutableStateFlow(0)
        stateFlow.test {
            assertEquals(0, awaitItem())
            stateFlow.value = 1
            assertEquals(1, awaitItem())
        }
    }
}
