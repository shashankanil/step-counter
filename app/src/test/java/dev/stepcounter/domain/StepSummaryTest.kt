package dev.stepcounter.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StepSummaryTest {
    private val today = LocalDate.of(2026, 9, 21)
    @Test fun missingDaysAreNotZero() {
        assertNull(StepSummary(today = today).steps)
        assertNull(StepSummary(today = today, days = mapOf(today.minusDays(1) to 0L)).average)
    }
    @Test fun averageUsesSevenCompletedDays() {
        val days = (1L..7L).associate { today.minusDays(it) to it * 1000 } + (today to 99000L)
        assertEquals(4000L, StepSummary(today = today, days = days).average)
    }
    @Test fun goalOverflowRetainsPercentageButClampsArt() {
        val summary = StepSummary(today = today, days = mapOf(today to 12500L))
        assertEquals(125L, summary.percent)
        assertEquals(1f, summary.progress, 0f)
    }
    @Test fun calendarIsMondayFirstAndSupportsSixRows() {
        val cells = monthCells(LocalDate.of(2026, 8, 15))
        assertEquals(42, cells.size)
        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 8, 1), cells[5])
        assertEquals(LocalDate.of(2026, 8, 31), cells[35])
        assertEquals(31, cells.filterNotNull().size)
    }
    @Test fun leapFebruaryHasTwentyNineDays() {
        assertEquals(29, monthCells(LocalDate.of(2024, 2, 2)).filterNotNull().size)
    }
    @Test fun localDayBoundariesFollowDaylightSaving() {
        val date = LocalDate.of(2026, 3, 8)
        val zone = ZoneId.of("America/New_York")
        val seconds = date.plusDays(1).atStartOfDay(zone).toEpochSecond() - date.atStartOfDay(zone).toEpochSecond()
        assertEquals(23 * 3600L, seconds)
    }
    @Test fun streakKeepsYesterdayUntilTodayReachesGoal() {
        val days = mapOf(today to 100L, today.minusDays(1) to 10000L, today.minusDays(2) to 12000L)
        assertEquals(2, StepSummary(today = today, days = days).recordedStreak())
        assertEquals(3, StepSummary(today = today, days = days + (today to 10000L)).recordedStreak())
    }
    @Test fun streakStopsAtMissingOrBelowGoalDays() {
        assertEquals(0, StepSummary(today = today).recordedStreak())
        val days = mapOf(today to 10000L, today.minusDays(2) to 10000L)
        assertEquals(1, StepSummary(today = today, days = days).recordedStreak())
        assertEquals(0, StepSummary(today = today, days = days, goal = 20000).recordedStreak())
    }
}
