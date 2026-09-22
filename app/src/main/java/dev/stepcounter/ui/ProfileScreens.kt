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
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.widgets.WidgetKind

@Composable fun WidgetsScreen(summary: StepSummary, pin: (WidgetKind) -> Unit) {
    Page {
        Heading("Home screen", "Movement, at a glance.")
        Text("Four quiet pages: Walk, Stats, Month and Compare. Tap the top edge for the previous page or bottom edge for the next page, with a vertical slide. Tap the centre artwork to open the app.", color = Color(Design.Grey))
        val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 4 })
        androidx.compose.foundation.pager.VerticalPager(state = pager, modifier = Modifier.fillMaxWidth().height(300.dp)) { index ->
            WidgetPreview(WidgetKind.entries[index], summary, Modifier.fillMaxHeight())
        }
        Text("${pager.currentPage + 1} / 4 · ${WidgetKind.entries[pager.currentPage].title}", color = Color(Design.Grey))
        Action("Add Step Counter widget") { pin(WidgetKind.WALK) }
        HorizontalDivider()
        Heading("Circle", "Just your next step.")
        WidgetPreview(WidgetKind.CIRCLE, summary, Modifier.fillMaxWidth(.75f))
        Action("Add Circle widget") { pin(WidgetKind.CIRCLE) }
        Tile {
            Eyebrow("Keep movement in sight")
            Text("The square widget is fixed at 2 × 2. Touch and hold Circle in your launcher to resize from 2 × 2 to around 4 × 4. Circle artwork keeps its proportions.")
            Text("No pin support? Long-press your home screen → Widgets → Step Counter. Start at 2 × 2. Select a friend or group in Together for the Comparison page. Updates follow your latest sync.", color = Color(Design.Grey), fontSize = 13.sp)
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
    val health = model.repository.health
    Page {
        Heading("You", "You set the pace.")
        Eyebrow("Connection")
        AccountTile(state, model)
        Tile {
            Eyebrow("Daily intention")
            Matrix(state.summary.goal.toLong())
            Text("Steps a day. Choose a goal that fits your routine.", color = Color(Design.Grey))
            Action("Adjust goal", onClick = goal)
        }
        Eyebrow("Your step data")
        Tile {
            Eyebrow("Health Connect")
            Text(if (health.readPermission in state.permissions) "Connected to your movement." else "Bring your steps together.", fontSize = 22.sp)
            Text("Read-only step totals from your connected sources. This app does not count steps on its own.", color = Color(Design.Grey), fontSize = 13.sp)
            Action(if (health.readPermission in state.permissions) "Review access ↗" else "Connect / install ↗", !state.busy) { connect(false) }
            HorizontalDivider(color = Color(Design.Grey).copy(alpha = .25f))
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
        Tile {
            Eyebrow("Privacy")
            Text("Sharing is your choice.", fontSize = 23.sp)
            Text("Review what stays on your device and the access you control.", color = Color(Design.Grey))
            OutlinedButton(privacy) { Text("Privacy & permissions") }
        }
        Eyebrow("Step / Counter   ·   0.2.0")
        Text("Original dot artwork. Built for a quieter relationship with movement. No ads or analytics. Walk together in Together when you choose to share.", color = Color(Design.Grey), fontSize = 12.sp)
    }

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
                Tile {
                    Eyebrow("Step data")
                    Action(if (model.repository.health.readPermission in state.permissions) "Review Health Connect access" else "Connect Health Connect", !state.busy) { connect(false) }
                    Text("No account needed. You can connect with friends later in Together.", color = Color(Design.Grey))
                }
            }
            1 -> { Tile { Eyebrow("Daily goal"); Matrix(state.summary.goal.toLong()); Action("Set your goal", onClick = goal) }; Text("Start with something achievable. Change it any time in You.", color = Color(Design.Grey)) }
            else -> { WidgetPreview(WidgetKind.STATS, state.summary, Modifier.fillMaxWidth(.8f)); Action("Add Step Counter widget") { pin(WidgetKind.STATS) }; Text("Your launcher will ask you to confirm. The square widget is fixed at 2 × 2. Tap its top or bottom edge to turn between four pages; tap the centre to open the app.", color = Color(Design.Grey)) }
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
        Heading("Privacy & permissions", "Your steps.\nYour choice.")
        Tile { Eyebrow("Read only"); Text("Step Counter reads step totals from Health Connect to show today's progress, a seven-day average and a monthly calendar. Optional background access refreshes your widgets when the app is closed.") }
        Tile { Eyebrow("On this device"); Text("Daily totals stay in a local Room database until you explicitly opt into sync. Enable step sharing in Together to upload daily totals for comparisons. Accepted friends and fellow group members can view shared totals. Turning sharing off deletes cloud totals when connected; offline deletion remains pending until you reconnect. An account is optional. Google sign-in shares your identity with Google and, when connected, our account service; it never uploads steps. Sign-in tokens are encrypted on this device. Comparisons are cached locally with their fetch time. No advertising or analytics. Android backup is disabled. No data is written to Health Connect.") }
        Tile { Eyebrow("Always in your control"); Text("Revoke permissions in Health Connect at any time. Detected step-permission revocation clears the local cache on the next sync. Clear this app's storage or uninstall to remove all local settings and totals immediately.") }
        Tile { Eyebrow("Reading the dots"); Text("The seven-day average uses the previous seven completed days. Grey calendar dots show recorded activity, white dots mean the current goal was reached, and red marks today. Tiny dots indicate zero, unavailable or future data.") }
        Action("Done", onClick = done)
    }
}

@Composable fun AccountTile(state: StepUiState, model: StepViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Tile {
        Eyebrow("Your optional account")
        if (state.authMessage.isNotBlank()) Text(state.authMessage, color = Color(Design.Grey), fontSize = 13.sp)
        val account = state.account
        if (account == null) {
            Text("A little more connected.", fontSize = 23.sp)
            Text("Sign in to connect with friends and groups. Your steps stay local until you opt into sync. You can always continue without an account.", color = Color(Design.Grey), fontSize = 13.sp)
            OutlinedButton({ model.signIn(context) }, enabled = !state.authBusy,
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Text(if (state.authBusy) "Connecting…" else "Sign in with Google")
            }
        } else {
            Text(account.name, fontSize = 23.sp)
            Text(account.email, color = Color(Design.Grey))
            Text(if (account.connected) "Account connected · manage step sharing in Together" else "Google profile saved locally · cloud sync needs a reachable backend", color = Color(Design.Grey), fontSize = 13.sp)
            if (!account.connected && dev.stepcounter.BuildConfig.API_CONFIGURED)
                Action("Connect account", !state.authBusy) { model.signIn(context) }
            TextButton({ model.signOut() }, enabled = !state.authBusy) { Text(if (state.authBusy) "Please wait…" else "Sign out") }
        }
    }
}
