package dev.stepcounter.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

/** Non-waking expiry fallback, not a sub-second animation clock. */
class WalkTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WALK_TICK) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { updateWidgets(context) } finally { pending.finish() }
        }
    }

    companion object {
        private const val ACTION_WALK_TICK = "dev.stepcounter.ACTION_WALK_TICK"
        private fun operation(context: Context) = PendingIntent.getBroadcast(context, 0,
            Intent(context, WalkTickReceiver::class.java).setAction(ACTION_WALK_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun schedule(context: Context, at: Long) {
            context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at, operation(context))
        }
        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(operation(context))
        }
    }
}
