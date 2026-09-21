package dev.stepcounter.domain

import java.time.LocalDate
import java.time.YearMonth

data class StepSummary(
    val today: LocalDate = LocalDate.now(),
    val days: Map<LocalDate, Long> = emptyMap(),
    val goal: Int = 10_000,
    val updatedAt: Long = 0,
    val status: String = "Connect Health Connect",
) {
    val steps: Long? get() = days[today]
    val progress: Float get() = ((steps ?: 0).toDouble() / goal.coerceAtLeast(1)).coerceIn(0.0, 1.0).toFloat()
    val percent: Long get() = ((steps ?: 0) * 100 / goal.coerceAtLeast(1))
    // Seven completed local days; never label missing data as zero.
    val average: Long? get() {
        val values = (1L..7L).map { days[today.minusDays(it)] ?: return null }
        return values.sum() / 7
    }
}

fun monthCells(today: LocalDate): List<LocalDate?> {
    val month = YearMonth.from(today)
    val offset = month.atDay(1).dayOfWeek.value - 1
    return List(42) { index ->
        val day = index - offset + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}
