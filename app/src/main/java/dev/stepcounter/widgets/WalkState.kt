package dev.stepcounter.widgets

/** A date-scoped delta detector: initial history and corrections are not live steps. */
internal data class WalkState(val day: String = "", val steps: Long? = null, val increasedAt: Long = 0) {
    fun active(now: Long) = increasedAt > 0 && now - increasedAt in 0 until 45_000L

    fun note(day: String, steps: Long?, now: Long): WalkState {
        if (this.day != day || steps == null || this.steps == null || steps < this.steps) {
            return WalkState(day, steps)
        }
        return WalkState(day, steps, if (steps > this.steps) now else increasedAt)
    }
}
