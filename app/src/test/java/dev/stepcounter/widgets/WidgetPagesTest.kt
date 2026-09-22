package dev.stepcounter.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPagesTest {
    @Test fun kindsOrder() {
        assertEquals(
            listOf(WidgetKind.WALK, WidgetKind.STATS, WidgetKind.MONTH, WidgetKind.COMPARISON),
            WidgetPages.kinds
        )
    }

    @Test fun floorWrap() {
        fun wrap(i: Int) = ((i % 4) + 4) % 4
        assertEquals(0, wrap(0))
        assertEquals(3, wrap(-1))
        assertEquals(0, wrap(4))
        assertEquals(1, wrap(5))
    }
}
