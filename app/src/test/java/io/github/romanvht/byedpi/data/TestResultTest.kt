package io.github.romanvht.byedpi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestResultTest {

    private fun strategy(command: String, successCount: Int, vararg siteTimes: Long?) = StrategyResult(
        command = command,
        successCount = successCount,
        totalRequests = 10,
        isCompleted = true,
        siteResults = siteTimes.mapIndexed { index, time ->
            SiteResult("site$index", if (time == null) 0 else 1, 1, time)
        }.toMutableList()
    )

    @Test
    fun medianOfEmptyListIsNull() {
        assertNull(emptyList<Long>().median())
    }

    @Test
    fun medianOfOddCountIsMiddleValue() {
        assertEquals(200L, listOf(900L, 100L, 200L).median())
    }

    @Test
    fun medianOfEvenCountIsMeanOfMiddleValues() {
        assertEquals(250L, listOf(400L, 100L, 300L, 200L).median())
    }

    @Test
    fun responseTimeIgnoresFailedSites() {
        assertEquals(300L, strategy("a", 2, 100L, null, 500L).responseTimeMs)
        assertNull(strategy("a", 0, null, null).responseTimeMs)
    }

    @Test
    fun responseTimeOfOldResultsIsNull() {
        val result = StrategyResult("a", siteResults = mutableListOf(SiteResult("site", 1, 1)))
        assertNull(result.responseTimeMs)
    }

    @Test
    fun successCountGoesBeforeResponseTime() {
        val slow = strategy("slow", 5, 2000L)
        val fast = strategy("fast", 4, 100L)
        assertEquals(listOf("slow", "fast"), listOf(fast, slow).sortedWith(StrategyResult.ORDER).map { it.command })
    }

    @Test
    fun fasterStrategyGoesFirstOnEqualSuccessCount() {
        val slow = strategy("slow", 5, 900L)
        val fast = strategy("fast", 5, 300L)
        val unknown = strategy("unknown", 5)
        assertEquals(
            listOf("fast", "slow", "unknown"),
            listOf(unknown, slow, fast).sortedWith(StrategyResult.ORDER).map { it.command }
        )
    }

    @Test
    fun completedStrategiesGoFirst() {
        val running = strategy("running", 9, 100L).apply { isCompleted = false }
        val done = strategy("done", 1, 900L)
        assertEquals(listOf("done", "running"), listOf(running, done).sortedWith(StrategyResult.ORDER).map { it.command })
    }
}
