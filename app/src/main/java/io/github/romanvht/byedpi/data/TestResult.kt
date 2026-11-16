package io.github.romanvht.byedpi.data

data class StrategyResult(
    val command: String,
    var successCount: Int = 0,
    var totalRequests: Int = 0,
    var currentProgress: Int = 0,
    var isCompleted: Boolean = false,
    val siteResults: MutableList<SiteResult> = mutableListOf(),
    var isExpanded: Boolean = false
) {
    val successPercentage: Int
        get() = if (totalRequests > 0) (successCount * 100) / totalRequests else 0
}

data class SiteResult(
    val site: String,
    val successCount: Int,
    val totalCount: Int
)

fun bestSuccessfulStrategy(strategies: List<StrategyResult>): StrategyResult? =
    strategies.asSequence()
        .filter { it.isCompleted && it.totalRequests > 0 && it.successCount > 0 }
        .maxWithOrNull(
            compareBy<StrategyResult> { it.successCount.toDouble() / it.totalRequests }
                .thenBy { it.successCount }
        )
