package dev.stepcounter.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import dev.stepcounter.R
import dev.stepcounter.data.StepRepository
import dev.stepcounter.data.social.SocialRepository
import dev.stepcounter.domain.StepSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private fun artworkSize(context: Context, id: Int): Int {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
    val side = minOf(
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180),
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
    )
    return (side * context.resources.displayMetrics.density).toInt().coerceIn(180, 420)
}

open class StepWidgetReceiver : AppWidgetProvider() {
    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetPages.clear(context, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class CircleWidgetReceiver : StepWidgetReceiver()

private fun swipeIntent(context: Context, id: Int): PendingIntent {
    val intent = Intent(context, WidgetSwipeActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        data = Uri.parse("stepcounter://widget-swipe/$id")
    }
    return PendingIntent.getActivity(
        context,
        id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun squareViews(
    context: Context,
    id: Int,
    summary: StepSummary,
    walkFrame: Int,
    comparison: org.json.JSONObject?,
    target: String
): RemoteViews {
    val kind = WidgetPages.kind(context, id)
    val size = artworkSize(context, id)
    return RemoteViews(context.packageName, R.layout.step_widget).apply {
        setImageViewBitmap(R.id.step_page, WidgetArtwork.render(kind, summary, size, comparison, target, walkFrame))
        setContentDescription(
            R.id.step_page,
            "${kind.title}. ${summary.steps ?: "No recorded"} steps. " +
                (if (kind == WidgetKind.COMPARISON) {
                    "Comparison with ${target.ifBlank { "no selection" }}. Open Together for totals and freshness. "
                } else "") +
                "Flick up or down through Walk, Stats, Month and Compare. Tap to open."
        )
        setOnClickPendingIntent(R.id.step_page, swipeIntent(context, id))
    }
}

private suspend fun publish(
    context: Context,
    ids: IntArray,
    summary: StepSummary,
    walkFrame: Int,
    circle: Boolean = false
) = withContext(Dispatchers.IO) {
    if (ids.isEmpty()) return@withContext
    val manager = AppWidgetManager.getInstance(context)
    val social = SocialRepository(context)
    val comparison = social.cachedComparison
    ids.forEach { id ->
        val size = artworkSize(context, id)
        if (circle) {
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.circle_widget).apply {
                setImageViewBitmap(
                    R.id.step_page,
                    WidgetArtwork.render(WidgetKind.CIRCLE, summary, size, walkFrame = walkFrame)
                )
                setContentDescription(R.id.step_page, "Walk progress. ${summary.percent}% of goal. Tap to open.")
                setOnClickPendingIntent(
                    R.id.step_page,
                    PendingIntent.getActivity(
                        context,
                        id,
                        Intent(context, dev.stepcounter.ui.MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            })
            return@forEach
        }
        manager.updateAppWidget(id, squareViews(context, id, summary, walkFrame, comparison, social.targetName))
    }
}

suspend fun updateWidgets(context: Context) {
    val summary = StepRepository(context).snapshot()
    WalkAnimation.noteSteps(context, summary)
    renderWidgets(context, summary, WalkAnimation.frame(context))
    WalkAnimation.ensureRunning(context)
}

private val renderLock = Mutex()

internal suspend fun renderWidgets(context: Context, summary: StepSummary, walkFrame: Int) = renderLock.withLock {
    val manager = AppWidgetManager.getInstance(context)
    publish(context, manager.getAppWidgetIds(ComponentName(context, StepWidgetReceiver::class.java)), summary, walkFrame)
    publish(context, manager.getAppWidgetIds(ComponentName(context, CircleWidgetReceiver::class.java)), summary, walkFrame, true)
}
