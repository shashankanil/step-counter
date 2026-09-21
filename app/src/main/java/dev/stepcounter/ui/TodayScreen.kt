package dev.stepcounter.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.text.DateFormat
import java.util.Date

@Composable fun TodayScreen(state: StepUiState, refresh: () -> Unit, goal: () -> Unit, profile: () -> Unit, history: () -> Unit, widgets: () -> Unit) {
    val s = state.summary
    Page {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Heading(s.today.format(DateTimeFormatter.ofPattern("EEE, dd MMM")), "Find your stride.", Modifier.weight(1f))
            TextButton(profile) { Text("You ↗") }
        }
        Tile {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Eyebrow("Daily movement"); Eyebrow("${s.percent}%") }
            GoalOrbit(s, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Goal / ${number(s.goal.toLong())}")
                TextButton(goal) { Text("Adjust ↗") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f)) { Eyebrow("7-day average"); Matrix(s.average); Text("Completed days", color = Color(Design.Grey), fontSize = 12.sp) }
            Tile(Modifier.weight(1f)) { Eyebrow("To your goal"); Matrix(s.steps?.let { (s.goal - it).coerceAtLeast(0) }); Text("One step at a time", color = Color(Design.Grey), fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val pulse = rememberInfiniteTransition(label = "Sync pulse")
            val alpha by pulse.animateFloat(.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "Sync opacity")
            Box(Modifier.size(7.dp).background(Color(Design.Red).copy(alpha = if (state.busy) alpha else 1f), CircleShape))
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(if (state.busy) "Syncing your steps…" else s.status, fontSize = 13.sp)
                Text(if (s.updatedAt == 0L) "No synced data yet" else "Updated ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.updatedAt))}",
                    fontSize = 11.sp, color = Color(Design.Grey))
            }
            TextButton(refresh, enabled = !state.busy) { Text("Refresh") }
        }
        Tile {
            Eyebrow("Your rhythm")
            Text("See how your days add up.", color = Color(Design.Grey))
            Action("View history", onClick = history)
            Action("Home screen widgets", onClick = widgets)
        }
        if (s.steps == null) Tile {
            Eyebrow("Ready when you are")
            Text("Connect your steps", fontSize = 22.sp)
            Text("Choose Health Connect in You to bring your daily movement here.", color = Color(Design.Grey))
            Action("Set up in You", onClick = profile)
        }
    }
}
