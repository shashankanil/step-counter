package dev.stepcounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stepcounter.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable fun HistoryScreen(s: StepSummary) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(s.today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(s.today.toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    Page {
        Heading("Your rhythm", "Small steps.\nLasting patterns.")
        Tile {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ monthText = month.minusMonths(1).toString() }, enabled = month > YearMonth.from(s.today.minusDays(62))) {
                    Text("‹", modifier = Modifier.semantics { contentDescription = "Previous month" })
                }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontSize = 18.sp)
                TextButton({ monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(s.today)) {
                    Text("›", modifier = Modifier.semantics { contentDescription = "Next month" })
                }
            }
            Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color(Design.Grey), fontSize = 11.sp) } }
            monthCells(month.atDay(1)).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val count = s.days[date]
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).then(if (date == null) Modifier else Modifier
                            .selectable(selected = date == selected, enabled = date <= s.today, role = Role.Button, onClick = { selectedText = date.toString() })
                            .semantics { contentDescription = "$date, ${count?.let { "$it steps" } ?: "No data"}${if (date == s.today) ", today" else ""}" }),
                            contentAlignment = Alignment.Center) {
                            if (date != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(if (count != null && count > 0 || date == s.today) 7.dp else 3.dp)
                                    .background(when { date == s.today -> Color(Design.Red); count != null && count >= s.goal -> Color.White; else -> Color(Design.Grey) }, CircleShape))
                                Spacer(Modifier.height(5.dp))
                                Text(date.dayOfMonth.toString(), fontSize = 10.sp, color = if (date == selected) Color.White else Color(Design.Grey))
                                Box(Modifier.size(3.dp).background(if (date == selected) Color.White else Color.Transparent, CircleShape))
                            }
                        }
                    }
                }
            }
            Eyebrow(selected.format(DateTimeFormatter.ofPattern("dd MMM / EEEE")))
            Matrix(s.days[selected])
            Text("Red · today   White · goal reached\nGrey · activity   Tiny · zero or no data", fontSize = 11.sp, color = Color(Design.Grey))
        }
        Tile {
            Eyebrow("Last seven days / includes today")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dates = (6L downTo 0L).map { s.today.minusDays(it) }
                val max = dates.mapNotNull { s.days[it] }.maxOrNull()?.coerceAtLeast(1) ?: 1
                dates.forEach { date ->
                    val value = s.days[date]
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$date: ${value?.let { "$it steps" } ?: "No data"}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                            val filled = if (value == null) 0 else kotlin.math.ceil(value.toDouble() / max * 10).toInt()
                            repeat(10) { row ->
                                drawCircle(if (row < filled) { if (date == s.today) Color(Design.Red) else Color.White } else Color(0xFF373737),
                                    3.dp.toPx(), Offset(size.width / 2, size.height - 5.dp.toPx() - row * 9.dp.toPx()))
                            }
                        }
                        Text(if (value == null) "—" else date.dayOfWeek.name.take(1), fontSize = 11.sp, color = Color(Design.Grey))
                    }
                }
            }
        }
        Tile { Eyebrow("Recorded streak"); Matrix(s.recordedStreak().toLong(), label = "days"); Text("Consecutive days at your current goal. An unfinished today keeps yesterday's streak. Missing history ends the count.", fontSize = 13.sp, color = Color(Design.Grey)) }
        Text("Up to 63 days stay on this device. The latest 30 are refreshed; older totals are cached. Missing days are never treated as zero.", color = Color(Design.Grey), fontSize = 12.sp)
    }
}
