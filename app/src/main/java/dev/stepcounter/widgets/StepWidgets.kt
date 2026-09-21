package dev.stepcounter.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.stepcounter.R
import dev.stepcounter.data.StepRepository
import dev.stepcounter.data.social.SocialRepository
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.ui.MainActivity
import kotlinx.coroutines.*
import org.json.JSONObject

private val pages = listOf(WidgetKind.WALK, WidgetKind.STATS, WidgetKind.MONTH, WidgetKind.COMPARISON)

private fun page(context: Context, position: Int, summary: StepSummary, comparison: JSONObject?, target: String, size: Int) =
    RemoteViews(context.packageName, R.layout.step_page).apply {
        val kind = pages[position]
        // Render to the current launcher allocation, with a collection memory cap.
        setImageViewBitmap(R.id.step_page, WidgetArtwork.render(kind, summary, size, comparison, target))
        // Square rows ≈ launcher allocation so one page fills the tile while scrolling.
        if (Build.VERSION.SDK_INT >= 31) {
            setViewLayoutWidth(R.id.step_page, size.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            setViewLayoutHeight(R.id.step_page, size.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        }
        setContentDescription(R.id.step_page, "${kind.title}. ${summary.steps ?: "No recorded"} steps. " +
            (if (kind == WidgetKind.COMPARISON) "Comparison with ${target.ifBlank { "no selection" }}. Open Together for totals and freshness. " else "") +
            "Swipe up or down for another page. Tap to open.")
        setOnClickFillInIntent(R.id.step_page, Intent())
    }

private fun artworkSize(context: Context, id: Int): Int {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
    val side = minOf(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180),
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180))
    return (side * context.resources.displayMetrics.density).toInt().coerceIn(180, 420)
}
open class StepWidgetReceiver : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { publish(context, ids, this@StepWidgetReceiver is CircleWidgetReceiver) }
            finally { pending.finish() }
        }
    }
}

class CircleWidgetReceiver : StepWidgetReceiver()

private suspend fun publish(context: Context, ids: IntArray, circle: Boolean = false) = withContext(Dispatchers.IO) {
    if (ids.isEmpty()) return@withContext
    val manager = AppWidgetManager.getInstance(context)
    val summary = StepRepository(context).snapshot()
    val social = SocialRepository(context)
    val comparison = social.cachedComparison
    ids.forEach { id ->
        val size = artworkSize(context, id)
        if (circle) {
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.circle_widget).apply {
                setImageViewBitmap(R.id.step_page, WidgetArtwork.render(WidgetKind.CIRCLE, summary, size))
                setContentDescription(R.id.step_page, "Walk progress. ${summary.percent}% of goal. Tap to open.")
                setOnClickPendingIntent(R.id.step_page, PendingIntent.getActivity(context, id,
                    Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            })
            return@forEach
        }
        val views = RemoteViews(context.packageName, R.layout.step_widget).apply {
            if (Build.VERSION.SDK_INT >= 31) {
                // Stable rows let the launcher retain its vertical scroll position on refresh.
                val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
                pages.indices.forEach { items.addItem(it.toLong(), page(context, it, summary, comparison, social.targetName, size)) }
                setRemoteAdapter(R.id.step_pages, items.build())
            } else {
                setRemoteAdapter(R.id.step_pages, Intent(context, StepPageService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    data = Uri.parse("stepcounter://widget/$id")
                })
            }
            setPendingIntentTemplate(R.id.step_pages, PendingIntent.getActivity(context, id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)))
        }
        manager.updateAppWidget(id, views)
        if (Build.VERSION.SDK_INT < 31) manager.notifyAppWidgetViewDataChanged(id, R.id.step_pages)
    }
}

class StepPageService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Pages(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    private class Pages(val context: Context, val id: Int) : RemoteViewsFactory {
        private var summary = StepSummary()
        private var comparison: JSONObject? = null
        private var target = ""
        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            summary = runBlocking { StepRepository(context).snapshot() }
            val social = SocialRepository(context)
            comparison = social.cachedComparison; target = social.targetName
        }
        override fun onDestroy() = Unit
        override fun getCount() = pages.size
        override fun getViewAt(position: Int) = page(context, position, summary, comparison, target, artworkSize(context, id))
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = true
    }
}
suspend fun updateWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    publish(context, manager.getAppWidgetIds(ComponentName(context, StepWidgetReceiver::class.java)))
    publish(context, manager.getAppWidgetIds(ComponentName(context, CircleWidgetReceiver::class.java)), true)
}
