package io.github.romanvht.byedpi.data

data class TestState(
    val runId: Long = 0,
    val isLoaded: Boolean = false,
    val isRunning: Boolean = false,
    val isStopping: Boolean = false,
    val currentStrategy: Int = 0,
    val strategies: List<StrategyResult> = emptyList(),
    val carriedStrategies: List<StrategyResult> = emptyList(),
    val failed: Boolean = false,
    val wasInterrupted: Boolean = false,
)
