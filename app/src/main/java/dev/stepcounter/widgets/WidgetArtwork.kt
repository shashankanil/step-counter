package dev.stepcounter.widgets

import android.graphics.*
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.domain.monthCells
import dev.stepcounter.ui.Design
import java.text.NumberFormat
import kotlin.math.*

enum class WidgetKind(val title: String) {
    WALK("Walk progress"), STATS("Stats stack"), MONTH("Month grid"), COMPARISON("Comparison")
}

/** Original geometric artwork. No bundled typeface or third-party brand assets. */
object WidgetArtwork {
    val glyphs = mapOf(
        '0' to "01110/10001/10011/10101/11001/10001/01110",
        '1' to "00100/01100/00100/00100/00100/00100/01110",
        '2' to "01110/10001/00001/00010/00100/01000/11111",
        '3' to "11110/00001/00001/01110/00001/00001/11110",
        '4' to "00010/00110/01010/10010/11111/00010/00010",
        '5' to "11111/10000/10000/11110/00001/00001/11110",
        '6' to "01110/10000/10000/11110/10001/10001/01110",
        '7' to "11111/00001/00010/00100/01000/01000/01000",
        '8' to "01110/10001/10001/01110/10001/10001/01110",
        '9' to "01110/10001/10001/01111/00001/00001/01110",
        ',' to "0/0/0/0/0/1/1", '-' to "000/000/000/111/000/000/000",
        'S' to "0111/1000/1000/0110/0001/0001/1110"
    )
    fun render(kind: WidgetKind, summary: StepSummary, size: Int = 480, comparison: org.json.JSONObject? = null, targetName: String = ""): Bitmap {
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
        fun matrix(s: String, x: Float, y: Float, maxWidth: Float, pitch: Float = 3.5f) {
            val patterns = s.map { glyphs[it] ?: glyphs.getValue('-') }
            val columns = patterns.sumOf { it.substringBefore('/').length + 1 } - 1
            val step = min(pitch, maxWidth / columns.coerceAtLeast(1))
            var left = x
            patterns.forEach { pattern ->
                val rows = pattern.split('/')
                rows.forEachIndexed { row, line -> line.forEachIndexed { col, char ->
                    if (char == '1') dot(left + col * step, y + row * step, step * .33f)
                } }
                left += (rows[0].length + 1) * step
            }
        }
        fun figure(x: Float, y: Float, pitch: Float) {
            listOf(2 to 0, 3 to 0, 2 to 1, 3 to 1, 1 to 2, 2 to 2, 3 to 2,
                0 to 3, 2 to 3, 4 to 3, 2 to 4, 2 to 5, 1 to 6, 3 to 6,
                1 to 7, 4 to 7, 0 to 8, 4 to 8, 5 to 8).forEach { (dx, dy) ->
                dot(x + dx * pitch, y + dy * pitch, pitch * .39f)
            }
        }
        fun pages(selected: Int) { repeat(4) { dot(191f, 82f + it * 12, 2.3f, if (it == selected) Design.White else Design.Grey) } }
        fun number(value: Long?) = value?.let { NumberFormat.getIntegerInstance(java.util.Locale.US).format(it) } ?: "--"
        p.color = Design.Surface.toInt()
        c.drawRoundRect(0f, 0f, 200f, 200f, 30f, 30f, p)
        when (kind) {
            WidgetKind.WALK -> {
                val x = 15f + summary.progress * 142f
                repeat(21) { dot(12f + it * 8.3f, 100f, if (it < summary.progress * 20) 2f else 1f, if (it < summary.progress * 20) Design.White else Design.Grey) }
                figure(x, 83f, 4.3f)
                pages(0)
                label("${summary.percent}%", 18f, 176f, 10f, Design.Grey)
            }
            WidgetKind.STATS -> {
                matrix(number(summary.steps), 22f, 23f, 146f, 4f)
                label("TOTAL TODAY", 18f, 68f, 10f)
                label("${summary.percent}%", 154f, 68f, 10f)
                matrix(number(summary.average), 22f, 132f, 106f, 3.6f)
                label("7-DAY AVERAGE", 18f, 179f, 9f)
                pages(1)
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
                pages(2)
                "MTWTFSS".forEachIndexed { i, ch -> label(ch.toString(), 18f + i * 25.5f, 188f, 11f) }

            }
            WidgetKind.COMPARISON -> {
                label("TODAY / COMPARE", 18f, 27f, 9f, Design.Grey)
                label(targetName.take(24).ifBlank { "A little company." }, 18f, 47f, 12f)
                if (comparison == null) {
                    label("Choose a friend or group", 18f, 96f, 10f)
                    label("in Social, then refresh.", 18f, 112f, 10f, Design.Grey)
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
                    label(if (others.size > 2) "+${others.size - 2} more in Social" else "— means unavailable", 18f, 187f, 8f, Design.Grey)
                }
                pages(3)
            }
        }
        if (summary.steps == null && kind != WidgetKind.MONTH && kind != WidgetKind.COMPARISON) label("OPEN TO CONNECT", 46f, 193f, 7f, Design.Grey)
        return bitmap
    }
}
