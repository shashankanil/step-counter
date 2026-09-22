package dev.stepcounter.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import androidx.work.*
import dev.stepcounter.data.StepRepository
import dev.stepcounter.domain.StepSummary
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One bounded job owns the frame clock; no sub-second wake-up alarms or receiver lifetime leaks. */
internal object WalkAnimation {
    private const val WORK = "widget-walk-animation"
    private fun prefs(context: Context) = context.getSharedPreferences("widget_walk", Context.MODE_PRIVATE)
    private fun state(context: Context): WalkState = prefs(context).let {
        WalkState(it.getString("walk_day", "") ?: "",
            if (it.contains("walk_last_steps")) it.getLong("walk_last_steps", 0) else null,
            it.getLong("walk_last_increase_at", 0))
    }

    @Synchronized fun noteSteps(context: Context, summary: StepSummary): Boolean {
        val state = state(context).note("${summary.today}/${java.time.ZoneId.systemDefault().id}", summary.steps, System.currentTimeMillis())
        prefs(context).edit().apply {
            putString("walk_day", state.day)
            if (state.steps == null) remove("walk_last_steps") else putLong("walk_last_steps", state.steps)
            putLong("walk_last_increase_at", state.increasedAt)
            if (!state.active(System.currentTimeMillis())) putInt("walk_frame", 0)
        }.apply()
        return state.active(System.currentTimeMillis())
    }

    @Synchronized fun active(context: Context) = state(context).active(System.currentTimeMillis())
    @Synchronized fun frame(context: Context) = if (active(context)) prefs(context).getInt("walk_frame", 0) else 0
    @Synchronized fun advance(context: Context): Int {
        val next = if (active(context)) (frame(context) + 1) % 4 else 0
        prefs(context).edit().putInt("walk_frame", next).apply()
        return next
    }
    @Synchronized fun rest(context: Context) {
        prefs(context).edit().putInt("walk_frame", 0).apply()
    }
    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(StepWidgetReceiver::class.java, CircleWidgetReceiver::class.java).any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }
    fun screenOn(context: Context) = context.getSystemService(PowerManager::class.java).isInteractive

    fun ensureRunning(context: Context) {
        val work = WorkManager.getInstance(context)
        if (active(context) && hasWidgets(context) && screenOn(context)) {
            WalkTickReceiver.schedule(context, state(context).increasedAt + 45_000)
            work.enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WalkAnimationWorker>().apply {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }.build())
        } else {
            work.cancelUniqueWork(WORK)
            WalkTickReceiver.cancel(context)
        }
    }
}

class WalkAnimationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // Required by expedited WorkManager on Android versions before API 31. Use ordinary
    // work there to avoid a notification/foreground service for decorative animation.
    override suspend fun doWork(): Result {
        val context = applicationContext
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            while (WalkAnimation.active(context) && WalkAnimation.hasWidgets(context) &&
                WalkAnimation.screenOn(context) && android.os.SystemClock.elapsedRealtime() - started < 60_000) {
                val frameStarted = android.os.SystemClock.elapsedRealtime()
                val summary = StepRepository(context).snapshot()
                WalkAnimation.noteSteps(context, summary)
                renderWidgets(context, summary, WalkAnimation.advance(context))
                delay((250 - (android.os.SystemClock.elapsedRealtime() - frameStarted)).coerceAtLeast(1))
            }
        } finally {
            withContext(NonCancellable) {
                WalkAnimation.rest(context)
                renderWidgets(context, StepRepository(context).snapshot(), 0)
            }
        }
        return Result.success()
    }
}
