package io.github.romanvht.byedpi.utility

import android.content.Context
import io.github.romanvht.byedpi.R

fun Context.formatTestResult(successCount: Int, totalCount: Int, responseTimeMs: Long?): String =
    if (responseTimeMs == null) "$successCount/$totalCount"
    else getString(R.string.test_result_time, successCount, totalCount, responseTimeMs)
