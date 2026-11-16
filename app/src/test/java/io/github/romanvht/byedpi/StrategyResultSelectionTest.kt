package io.github.romanvht.byedpi

import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.data.bestSuccessfulStrategy
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StrategyResultSelectionTest {
    @Test
    fun selectsTheHighestSuccessRatio() {
        val lower = StrategyResult("-s 1", successCount = 90, totalRequests = 100, isCompleted = true)
        val best = StrategyResult("-s 2", successCount = 10, totalRequests = 10, isCompleted = true)

        assertSame(best, bestSuccessfulStrategy(listOf(lower, best)))
    }

    @Test
    fun ignoresIncompleteAndUnsuccessfulStrategies() {
        val incomplete = StrategyResult("-s 1", successCount = 10, totalRequests = 10)
        val unsuccessful = StrategyResult("-s 2", successCount = 0, totalRequests = 10, isCompleted = true)

        assertNull(bestSuccessfulStrategy(listOf(incomplete, unsuccessful)))
    }
}
