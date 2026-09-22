package dev.stepcounter.widgets

import android.content.Context

/** Persists the square widget page index per appWidgetId. */
internal object WidgetPages {
    val kinds = listOf(WidgetKind.WALK, WidgetKind.STATS, WidgetKind.MONTH, WidgetKind.COMPARISON)
    private const val PREFS = "widget_pages"
    private fun key(id: Int) = "page_$id"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun index(context: Context, appWidgetId: Int): Int {
        val raw = prefs(context).getInt(key(appWidgetId), 0)
        return raw.floorMod(kinds.size)
    }

    fun kind(context: Context, appWidgetId: Int): WidgetKind = kinds[index(context, appWidgetId)]

    fun advance(context: Context, appWidgetId: Int, delta: Int): Int {
        val next = (index(context, appWidgetId) + delta).floorMod(kinds.size)
        prefs(context).edit().putInt(key(appWidgetId), next).apply()
        return next
    }

    fun clear(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        prefs(context).edit().apply {
            appWidgetIds.forEach { remove(key(it)) }
        }.apply()
    }

    private fun Int.floorMod(m: Int): Int = ((this % m) + m) % m
}
