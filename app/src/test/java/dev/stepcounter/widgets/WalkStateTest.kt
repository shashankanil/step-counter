package dev.stepcounter.widgets

import org.junit.Assert.*
import org.junit.Test

class WalkStateTest {
    @Test fun firstObservationIsOnlyABaseline() {
        val state = WalkState().note("today/zone/boot", 500, 100_000)
        assertEquals(500L, state.steps)
        assertFalse(state.active(100_000))
    }

    @Test fun increaseStartsWalkingButEqualSnapshotsDoNotExtendIt() {
        val walking = WalkState("day", 500).note("day", 501, 100_000)
        assertTrue(walking.active(100_000))
        val unchanged = walking.note("day", 501, 130_000)
        assertTrue(unchanged.active(144_999))
        assertFalse(unchanged.active(145_000))
        val renewed = unchanged.note("day", 502, 140_000)
        assertTrue(renewed.active(145_000))
    }

    @Test fun missingDecreasedAndDifferentDayTotalsResetActivity() {
        val walking = WalkState("day", 500, 100_000)
        for (state in listOf(walking.note("day", null, 110_000),
            walking.note("day", 499, 110_000),
            walking.note("new day or zone or boot", 600, 110_000))) {
            assertEquals(0L, state.increasedAt)
        }
        val missing = walking.note("day", null, 110_000)
        assertEquals(0L, missing.note("day", 600, 120_000).increasedAt)
    }

    @Test fun futureOrUnsetTimestampNeverAnimates() {
        assertFalse(WalkState().active(1_000))
        assertFalse(WalkState(increasedAt = 100_000).active(99_999))
    }
}
