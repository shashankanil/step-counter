package dev.stepcounter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.widgets.WidgetArtwork
import dev.stepcounter.widgets.WidgetKind
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

fun number(value: Long?) = value?.let { NumberFormat.getIntegerInstance(Locale.US).format(it) } ?: "--"
@Composable fun Eyebrow(text: String) {
    Text(text.uppercase(Locale.ROOT), color = Color(0xFFB4B4B4), fontSize = 11.sp, letterSpacing = 2.sp)
}
@Composable fun Matrix(value: Long?, modifier: Modifier = Modifier, label: String = "steps", centered: Boolean = false) {
    val text = number(value)
    Canvas(modifier.fillMaxWidth().height(58.dp).semantics {
        contentDescription = "${value ?: "Unavailable"} $label"
    }) {
        val patterns = text.map { WidgetArtwork.glyphs.getValue(it) }.map { it.split('/') }
        val columns = patterns.sumOf { it[0].length + 1 } - 1
        val pitch = min(size.width / (columns + 1), size.height / 8)
        var x = if (centered) (size.width - (columns - 1) * pitch) / 2 else pitch / 2
        patterns.forEach { rows ->
            rows.forEachIndexed { row, line -> line.forEachIndexed { col, c ->
                if (c == '1') drawCircle(Color(Design.White), pitch * .32f, Offset(x + col * pitch, pitch + row * pitch))
            } }
            x += (rows[0].length + 1) * pitch
        }
    }
}
@Composable fun WidgetPreview(kind: WidgetKind, summary: StepSummary, modifier: Modifier = Modifier) {
    val bitmap = remember(kind, summary) { WidgetArtwork.render(kind, summary).asImageBitmap() }
    Image(bitmap, "${kind.title}. ${summary.steps ?: "Unavailable"} steps. ${summary.percent}% of goal.", modifier.aspectRatio(1f))
}
@Composable fun GoalOrbit(summary: StepSummary, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(summary.progress) { progress.animateTo(summary.progress, tween(1100)) }
    Box(modifier.aspectRatio(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Goal ring: ${summary.percent} percent complete" }) {
            val radius = size.minDimension * .45f
            repeat(72) { i ->
                val angle = (i * 5 - 90) * PI / 180
                drawCircle(if (i < progress.value * 72) Color(Design.White) else Color(0xFF393939),
                    size.minDimension * .0065f, center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius))
            }
            // The same dot-walker geometry as the launcher face.
            val pitch = size.minDimension * .018f
            listOf(2 to 0, 3 to 0, 2 to 1, 3 to 1, 1 to 2, 2 to 2, 3 to 2,
                0 to 3, 2 to 3, 4 to 3, 2 to 4, 2 to 5, 1 to 6, 3 to 6, 1 to 7, 4 to 7).forEach { (x,y) ->
                drawCircle(Color(Design.Grey), pitch * .35f,
                    Offset(center.x + (x - 2) * pitch, size.height * .19f + y * pitch))
            }
            drawCircle(Color(Design.Red), 4.dp.toPx(), Offset(center.x, size.height * .79f))
        }
        Column(Modifier.fillMaxWidth(.68f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Matrix(summary.steps, centered = true)
            Spacer(Modifier.height(8.dp))
            Eyebrow("Steps today")
        }
    }
}
