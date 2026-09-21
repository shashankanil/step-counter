package dev.stepcounter.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.stepcounter.widgets.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        setContent { StepTheme {
            val model: StepViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { model.refresh() }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
            StepApp(state, model, connect = { background ->
                val health = model.repository.health
                when (health.availability) {
                    HealthConnectClient.SDK_AVAILABLE -> {
                        if (!background && health.readPermission in state.permissions) {
                            runCatching { startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                                .onFailure { model.message("Open Health Connect settings to review access.") }
                        } else launcher.launch(if (background && health.backgroundSupported())
                            setOf(health.readPermission, health.backgroundPermission) else setOf(health.readPermission))
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                    }.onFailure { model.message("Install or update Health Connect from the Play Store.") }
                    else -> model.message("Health Connect requires Android 9 or newer and a supported provider.")
                }
            }, pin = { kind ->
                val cls = StepWidgetReceiver::class.java
                val manager = getSystemService(AppWidgetManager::class.java)
                if (manager.isRequestPinAppWidgetSupported) {
                    runCatching { manager.requestPinAppWidget(ComponentName(this, cls), null, null) }
                        .onSuccess { model.message(if (it) "Confirm the widget in your launcher." else "Use your launcher's Widgets menu.") }
                        .onFailure { model.message("Use your launcher's Widgets menu to add this tile.") }
                } else model.message("Long-press your home screen → Widgets → Step Counter.")
            })
        } }
    }
}

class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        setContent { StepTheme { PrivacyScreen { finish() } } }
    }
}
