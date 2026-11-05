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

    // Median over sites, so one slow site doesn't outweigh the rest
    val responseTimeMs: Long?
        get() = siteResults.mapNotNull { it.responseTimeMs }.median()

    companion object {
        val ORDER: Comparator<StrategyResult> = compareByDescending<StrategyResult> { it.isCompleted }
            .thenByDescending { it.successPercentage }
            .thenByDescending { it.successCount }
            .thenBy(nullsLast()) { it.responseTimeMs }
    }
}

data class SiteResult(
    val site: String,
    val successCount: Int,
    val totalCount: Int,
    // Median time of successful requests, null if none succeeded.
    // Nullable because results saved by older versions don't have it.
    val responseTimeMs: Long? = null
)

fun List<Long>.median(): Long? {
    if (isEmpty()) return null
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}
