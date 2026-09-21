package dev.stepcounter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.widgets.WidgetKind

@Composable fun WidgetsScreen(summary: StepSummary, pin: (WidgetKind) -> Unit) {
    Page {
        Heading("At a glance", "A little more\nyou. Everywhere.")
        Text("Your live totals, in four original faces. Choose a tile to make movement part of your home screen.", color = Color(Design.Grey))
        WidgetKind.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { kind ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetPreview(kind, summary, Modifier.fillMaxWidth())
                        Text(kind.title, fontSize = 14.sp)
                        OutlinedButton({ pin(kind) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Pin ${kind.title} widget" }) { Text("Pin +") }
                    }
                }
            }
        }
        Tile {
            Eyebrow("Make room for a habit")
            Text("01   Choose a face and tap Pin.\n02   Confirm in your launcher.\n03   Touch and hold to resize.", lineHeight = 28.sp)
            Text("No pin support? Long-press your home screen → Widgets → Step Counter. Start at 2 × 2. Tap any tile to open the app. Updates follow your latest sync.", color = Color(Design.Grey), fontSize = 13.sp)
        }
    }
}

@Composable fun GoalDialog(goal: Int, dismiss: () -> Unit, save: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(goal.toString()) }
    val parsed = value.toIntOrNull()?.takeIf { it in 100..100_000 }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Your pace. Your goal.") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("A daily intention, one step at a time.")
            OutlinedTextField(value, { value = it.filter(Char::isDigit).take(6) }, label = { Text("Daily steps") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = parsed == null,
                supportingText = { Text("100–100,000 steps") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5000, 8000, 10000).forEach { preset -> TextButton({ value = preset.toString() }) { Text("${preset / 1000}k") } }
            }
        }
    }, confirmButton = { TextButton({ parsed?.let(save) }, enabled = parsed != null) { Text("Save goal") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@Composable fun YouScreen(state: StepUiState, model: StepViewModel, connect: (Boolean) -> Unit, goal: () -> Unit, privacy: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var initial by rememberSaveable(state.summary.initial) { mutableStateOf(state.summary.initial) }
    val health = model.repository.health
    Page {
        Heading("Locally yours", "You set the pace.")
        Tile {
            Eyebrow("Your signature")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.summary.initial, fontSize = 48.sp)
                TextButton({ editing = true }) { Text("Edit initial ↗") }
            }
            Text("A single letter, shared with your widgets. Stored only on this device.", color = Color(Design.Grey), fontSize = 13.sp)
        }
        Tile { Eyebrow("Daily intention"); Matrix(state.summary.goal.toLong()); Action("Adjust goal", onClick = goal) }
        Tile {
            Eyebrow("Health Connect")
            Text(if (health.readPermission in state.permissions) "Connected to your movement." else "Bring your steps together.", fontSize = 22.sp)
            Text("Read-only step totals from your connected sources. This app does not count steps on its own.", color = Color(Design.Grey), fontSize = 13.sp)
            Action(if (health.readPermission in state.permissions) "Review access ↗" else "Connect / install ↗", !state.busy) { connect(false) }
            HorizontalDivider(color = Color(0xFF383838))
            Eyebrow("Background refresh")
            Text(when {
                health.backgroundPermission in state.permissions -> "Allowed · Android schedules updates roughly every 30 minutes, sometimes later."
                health.backgroundSupported() -> "Optional. Keep widgets fresh when the app is closed."
                else -> "Not supported by this provider. Open the app to refresh your widgets."
            }, fontSize = 13.sp, color = Color(Design.Grey))
            if (health.backgroundSupported()) TextButton({ connect(health.backgroundPermission !in state.permissions) }, enabled = !state.busy) {
                Text(if (health.backgroundPermission in state.permissions) "Manage in Health Connect ↗" else "Allow background access ↗")
            }
        }
        Tile { Eyebrow("Private by design"); Text("No account. No uploads.\nJust your steps.", fontSize = 23.sp); TextButton(privacy) { Text("Privacy & permissions ↗") } }
        Eyebrow("Step / Counter   ·   0.2.0")
        Text("Original dot artwork. Built for a quieter relationship with movement. No ads, analytics, backend or social features.", color = Color(Design.Grey), fontSize = 12.sp)
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Make your mark.") },
        text = { OutlinedTextField(initial, { initial = it.filter { c -> c in 'a'..'z' || c in 'A'..'Z' }.take(1).uppercase() },
            label = { Text("One letter · A–Z") }, singleLine = true) },
        confirmButton = { TextButton({ model.initial(initial); editing = false }, enabled = initial.length == 1) { Text("Save") } },
        dismissButton = { TextButton({ editing = false }) { Text("Cancel") } })
}

@Composable fun Onboarding(state: StepUiState, model: StepViewModel, connect: (Boolean) -> Unit, pin: (WidgetKind) -> Unit, goal: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Page {
        Eyebrow("Step / Counter     ${page + 1} / 3")
        Heading("A little movement, every day", when(page) { 0 -> "Your day,\nin dots."; 1 -> "Find a goal\nthat feels like you."; else -> "Keep your next\nstep in sight." })
        when(page) {
            0 -> {
                GoalOrbit(state.summary, Modifier.fillMaxWidth())
                Text("Connect Health Connect to see your steps. Your totals stay on this device. You control access.", color = Color(Design.Grey))
                Action(if (model.repository.health.readPermission in state.permissions) "Connected" else "Connect Health Connect", !state.busy) { connect(false) }
            }
            1 -> { Tile { Eyebrow("Daily goal"); Matrix(state.summary.goal.toLong()); Action("Set your goal", onClick = goal) }; Text("Start with something achievable. Change it any time in You.", color = Color(Design.Grey)) }
            else -> { WidgetPreview(WidgetKind.STATS, state.summary, Modifier.fillMaxWidth(.8f)); Action("Pin stats widget +") { pin(WidgetKind.STATS) }; Text("Your launcher will ask you to confirm. You can add all four faces later from Widgets.", color = Color(Design.Grey)) }
        }
        Action(if (page == 2) "Let's walk →" else if (page == 0) "Continue →" else "Keep this goal →") { if (page < 2) page++ else model.finishSetup() }
        TextButton({ model.finishSetup() }) { Text("Set up later") }
    }
}

@Composable fun PrivacyScreen(done: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color.Black) { Box(Modifier.safeDrawingPadding()) { PrivacyContent(done) } }
}
@Composable fun PrivacyContent(done: () -> Unit) {
    Page {
        Heading("Privacy & permissions", "Your steps\nstay here.")
        Tile { Eyebrow("Read only"); Text("Step Counter reads step totals from Health Connect to show today's progress, a seven-day average and a monthly calendar. Optional background access refreshes your widgets when the app is closed.") }
        Tile { Eyebrow("On this device"); Text("Daily totals are stored in a local Room database. No account, advertising, analytics, backend or upload is included. Android backup is disabled. No data is written to Health Connect.") }
        Tile { Eyebrow("Always in your control"); Text("Revoke permissions in Health Connect at any time. Detected step-permission revocation clears the local cache on the next sync. Clear this app's storage or uninstall to remove all local settings and totals immediately.") }
        Tile { Eyebrow("Reading the dots"); Text("The seven-day average uses the previous seven completed days. Grey calendar dots show recorded activity, white dots mean the current goal was reached, and red marks today. Tiny dots indicate zero, unavailable or future data.") }
        Action("Done", onClick = done)
    }
}
