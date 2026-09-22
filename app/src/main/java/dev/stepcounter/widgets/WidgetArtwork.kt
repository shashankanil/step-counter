package dev.stepcounter.widgets

import android.graphics.*
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.domain.monthCells
import dev.stepcounter.ui.Design
import java.text.NumberFormat
import kotlin.math.*

enum class WidgetKind(val title: String) {
    WALK("Walk progress"), STATS("Stats"), MONTH("Month grid"), COMPARISON("Compare"), CIRCLE("Circle")
}

/** Original geometric artwork. No bundled typeface or third-party brand assets. */
object WidgetArtwork {
    fun render(kind: WidgetKind, summary: StepSummary, size: Int = 480, comparison: org.json.JSONObject? = null, targetName: String = "", walkFrame: Int = 0): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(size / 200f, size / 200f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun dot(x: Float, y: Float, r: Float, color: Long = Design.White) {
            p.color = color.toInt(); c.drawCircle(x, y, r, p)
        }
        fun label(s: String, x: Float, y: Float, textSize: Float = 10f, color: Long = Design.White) {
            p.color = color.toInt(); p.textSize = textSize; p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            c.drawText(s, x, y, p)
        }
        fun figure(x: Float, y: Float, pitch: Float) {
            val body = listOf(2 to 0, 3 to 0, 2 to 1, 3 to 1, 1 to 2, 2 to 2, 3 to 2,
                2 to 3, 2 to 4, 2 to 5)
            val limbs = when (Math.floorMod(walkFrame, 4)) {
                1 -> listOf(1 to 3, 3 to 3, 1 to 4, 3 to 4, 2 to 6, 3 to 6, 2 to 7, 3 to 7, 1 to 8, 3 to 8)
                2 -> listOf(0 to 3, 4 to 3, 3 to 6, 1 to 6, 3 to 7, 0 to 7, 4 to 8, 0 to 8, -1 to 8)
                3 -> listOf(1 to 3, 3 to 3, 0 to 4, 4 to 4, 1 to 6, 2 to 6, 1 to 7, 2 to 7, 1 to 8, 3 to 8)
                else -> listOf(0 to 3, 4 to 3, 1 to 6, 3 to 6, 1 to 7, 4 to 7, 0 to 8, 4 to 8, 5 to 8)
            }
            (body + limbs).forEach { (dx, dy) ->
                dot(x + dx * pitch, y + dy * pitch, pitch * .39f)
            }
        }
        fun number(value: Long?) = value?.let { NumberFormat.getIntegerInstance(java.util.Locale.UK).format(it) } ?: "--"
        p.color = Design.Surface.toInt()
        if (kind == WidgetKind.CIRCLE) c.drawCircle(100f, 100f, 100f, p)
        else c.drawRoundRect(0f, 0f, 200f, 200f, 30f, 30f, p)
        when (kind) {
            WidgetKind.CIRCLE -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f
                p.color = 0xFF383838.toInt(); c.drawCircle(100f, 100f, 78f, p)
                p.color = Design.White.toInt(); p.strokeCap = Paint.Cap.ROUND
                c.drawArc(22f, 22f, 178f, 178f, -90f, summary.progress * 360f, false, p)
                p.style = Paint.Style.FILL
                figure(88f, 68f, 5f)
                label("${summary.percent}%", 87f, 140f, 12f)
            }
            WidgetKind.WALK -> {
                val x = 15f + summary.progress * 142f
                repeat(21) { dot(12f + it * 8.3f, 100f, if (it < summary.progress * 20) 2f else 1f, if (it < summary.progress * 20) Design.White else Design.Grey) }
                figure(x, 83f, 4.3f)
                label("${summary.percent}%", 18f, 176f, 10f, Design.Grey)
            }
            WidgetKind.STATS -> {
                label(number(summary.steps), 18f, 48f, 29f)
                label("TOTAL TODAY", 18f, 68f, 10f)
                label("${summary.percent}%", 154f, 68f, 10f)
                label(number(summary.average), 18f, 155f, 25f)
                label("7-DAY AVERAGE", 18f, 179f, 9f)
            }
            WidgetKind.MONTH -> {
                monthCells(summary.today).forEachIndexed { index, date ->
                    if (date != null) {
                        val count = summary.days[date]
                        val color = when {
                            date == summary.today -> Design.Red
                            count != null && count >= summary.goal -> Design.White
                            else -> Design.Grey
                        }
                        val radius = if (date == summary.today || (count != null && count > 0)) 3.3f else 1.1f
                        dot(22f + index % 7 * 25.5f, 26f + index / 7 * 20f, radius, color)
                    }
                }
                "MTWTFSS".forEachIndexed { i, ch -> label(ch.toString(), 18f + i * 25.5f, 188f, 11f) }

            }
            WidgetKind.COMPARISON -> {
                label("TODAY / COMPARE", 18f, 27f, 9f, Design.Grey)
                label(targetName.take(24).ifBlank { "A little company." }, 18f, 47f, 12f)
                if (comparison == null) {
                    label("Choose a friend or group", 18f, 96f, 10f)
                    label("in Together to compare.", 18f, 112f, 10f, Design.Grey)
                } else {
                    val array = comparison.optJSONArray("members") ?: org.json.JSONArray()
                    val others = (0 until array.length()).map { array.getJSONObject(it) }
                        .filter { it.optString("id") != comparison.optString("selfId") }
                    val rows = listOf("You" to summary.steps) + others.take(2).map {
                        it.optString("name").take(17) to if (it.isNull("today")) null else it.optLong("today")
                    }
                    val maxValue = rows.mapNotNull { it.second }.maxOrNull()?.coerceAtLeast(1) ?: 1
                    rows.forEachIndexed { i, (name, count) ->
                        val y = 73f + i * 33f
                        label(name, 18f, y, 9f, Design.Grey)
                        label(number(count), 130f, y, 10f)
                        p.color = 0xFF383838.toInt()
                        c.drawRoundRect(18f, y + 8f, 174f, y + 12f, 2f, 2f, p)
                        if (count != null && count > 0) {
                            p.color = (if (i == 0) Design.White else Design.Grey).toInt()
                            c.drawRoundRect(18f, y + 8f, 18f + 156f * count.toFloat() / maxValue, y + 12f, 2f, 2f, p)
                        }
                    }
                    val fetched = comparison.optLong("fetchedAt")
                    val stamp = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.UK).format(java.util.Date(fetched))
                    label("Fetched " + stamp, 18f, 173f, 8f, Design.Grey)
                    label(if (others.size > 2) "+${others.size - 2} more in Together" else "-- means unavailable", 18f, 187f, 8f, Design.Grey)
                }
            }
        }
        if (summary.steps == null && kind != WidgetKind.MONTH && kind != WidgetKind.COMPARISON) label("OPEN TO CONNECT", 46f, 193f, 7f, Design.Grey)
        return bitmap
    }
}
