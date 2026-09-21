package dev.stepcounter.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.stepcounter.data.StepRepository
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.widgets.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StepTheme { Home() } }
    }
    @Composable private fun Home() {
        val repository = remember { StepRepository(applicationContext) }
        var summary by remember { mutableStateOf(StepSummary()) }
        var goal by remember { mutableStateOf("10000") }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }
        var permissions by remember { mutableStateOf(emptySet<String>()) }
        val scope = rememberCoroutineScope()
        suspend fun refresh() {
            if (busy) return
            busy = true
            try {
                summary = repository.snapshot()
                goal = summary.goal.toString()
                permissions = repository.health.permissions()
                repository.sync(background = false)
                summary = repository.snapshot()
                updateWidgets(applicationContext)
                message = summary.status
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                message = "Could not refresh. Check Health Connect access and try again."
            } finally { busy = false }
        }
        val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            scope.launch { refresh() }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { refresh() } }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("STEP / COUNTER", fontSize = 13.sp, letterSpacing = 3.sp)
                Text("Every step counts.", style = MaterialTheme.typography.headlineLarge)
                Text("${summary.today} · Your day, in dots", color = MaterialTheme.colorScheme.secondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(WidgetKind.WALK, WidgetKind.STATS).forEach { kind ->
                        val bitmap = remember(summary, kind) { WidgetArtwork.render(kind, summary).asImageBitmap() }
                        Image(bitmap, "${kind.title}: ${summary.steps ?: "No data"} steps", Modifier.weight(1f).aspectRatio(1f))
                    }
                }
                Text("${summary.steps ?: "—"} steps today · ${summary.percent}% of goal")
                Text(if (summary.updatedAt == 0L) "No synced data yet" else
                    "Last synced ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(summary.updatedAt))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                val available = repository.health.availability
                when (available) {
                    HealthConnectClient.SDK_AVAILABLE -> {
                        Button(enabled = !busy, onClick = {
                            permissionLauncher.launch(setOf(repository.health.readPermission))
                        }) { Text(if (repository.health.readPermission in permissions) "Review step permission" else "Connect Health Connect") }
                        if (repository.health.backgroundSupported() && repository.health.backgroundPermission !in permissions) {
                            OutlinedButton(enabled = !busy, onClick = {
                                permissionLauncher.launch(setOf(repository.health.readPermission, repository.health.backgroundPermission))
                            }) { Text("Allow background refresh") }
                        }
                        OutlinedButton(enabled = !busy, onClick = { scope.launch { refresh() } }) {
                            Text(if (busy) "Syncing…" else "Refresh steps")
                        }
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        Text("Install or update Health Connect to read steps.")
                        Button(onClick = {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) }
                                .onFailure { message = "Open the Play Store and search for Health Connect." }
                        }) { Text("Get Health Connect") }
                    }
                    else -> Text("Health Connect is unavailable on this device. Step access requires Android 9 or newer with a supported provider.")
                }
                Text("Health Connect supplies steps from connected sources. Background refresh is optional; otherwise open this app to update widgets.",
                    style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Daily goal", style = MaterialTheme.typography.titleLarge)
                val validGoal = goal.toIntOrNull()?.takeIf { it in 100..100_000 }
                OutlinedTextField(value = goal, onValueChange = { goal = it.filter(Char::isDigit).take(6) },
                    label = { Text("Steps") }, singleLine = true, isError = validGoal == null,
                    supportingText = { Text("100–100,000 steps · default 10,000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Button(enabled = validGoal != null && !busy, onClick = { scope.launch {
                    repository.setGoal(validGoal!!); summary = repository.snapshot(); updateWidgets(applicationContext)
                    message = "Goal saved"
                } }) { Text("Save goal") }
                HorizontalDivider()
                Text("Make it a home-screen habit.", style = MaterialTheme.typography.titleLarge)
                Text("Pin a widget below, or long-press your home screen → Widgets → Step Counter. Start with a 2 × 2 tile. Tap any widget to open this app.")
                WidgetKind.entries.forEach { kind ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        val cls = when (kind) {
                            WidgetKind.WALK -> WalkProgressReceiver::class.java
                            WidgetKind.STATS -> StatsStackReceiver::class.java
                            WidgetKind.MONTH -> MonthGridReceiver::class.java
                            WidgetKind.CIRCULAR -> CircularMetricReceiver::class.java
                        }
                        val manager = getSystemService(AppWidgetManager::class.java)
                        if (manager.isRequestPinAppWidgetSupported) {
                            manager.requestPinAppWidget(ComponentName(this@MainActivity, cls), null, null)
                            message = "Confirm the widget in your launcher."
                        } else message = "Use your launcher's Widgets menu to add this widget."
                    }) { Text("Pin ${kind.title}") }
                }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { startActivity(Intent(this@MainActivity, PrivacyActivity::class.java)) }) { Text("Privacy & permissions") }
            }
        }
    }
}

class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StepTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Your steps stay here.", style = MaterialTheme.typography.headlineLarge)
                Text("Step Counter reads step totals from Health Connect to show today's progress, a seven-day average and a monthly calendar. Optional background access refreshes your widgets when the app is closed.")
                Text("Daily totals are stored in a local Room database. No account, advertising, analytics, backend or upload is included. Android backup is disabled. No data is written to Health Connect.")
                Text("Revoke permissions in Health Connect at any time. Detected step-permission revocation clears the local cache on the next sync. Clear this app's storage or uninstall to remove all local settings and totals immediately.")
                Text("The seven-day average uses the previous seven completed days. Grey calendar dots show recorded activity, white dots mean the current goal was reached, and red marks today. Tiny dots indicate zero, unavailable or future data.")
                Button(onClick = { finish() }) { Text("Done") }
            }
        } } }
    }
}
