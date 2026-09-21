package dev.stepcounter.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

private fun page(context: Context, position: Int, summary: StepSummary, comparison: JSONObject?, target: String) =
    RemoteViews(context.packageName, R.layout.step_page).apply {
        val kind = pages[position]
        // Four 280px pages keep the collection bitmap allocation below 1.3 MB.
        setImageViewBitmap(R.id.step_page, WidgetArtwork.render(kind, summary, 280, comparison, target))
        setContentDescription(R.id.step_page, "${kind.title}. ${summary.steps ?: "No recorded"} steps. " +
            (if (kind == WidgetKind.COMPARISON) "Comparison with ${target.ifBlank { "no selection" }}. Open Social for totals and freshness. " else "") +
            "Swipe up or down for another page. Tap to open.")
        setOnClickFillInIntent(R.id.step_page, Intent())
    }

class StepWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { publish(context, ids) }
            finally { pending.finish() }
        }
    }
}

private suspend fun publish(context: Context, ids: IntArray) = withContext(Dispatchers.IO) {
    if (ids.isEmpty()) return@withContext
    val manager = AppWidgetManager.getInstance(context)
    val summary = StepRepository(context).snapshot()
    val social = SocialRepository(context)
    val comparison = social.cachedComparison
    ids.forEach { id ->
        val views = RemoteViews(context.packageName, R.layout.step_widget).apply {
            if (Build.VERSION.SDK_INT >= 31) {
                // Modern Android uses the same StackView gestures without legacy service conversion.
                val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
                pages.indices.forEach { items.addItem(it.toLong(), page(context, it, summary, comparison, social.targetName)) }
                setRemoteAdapter(R.id.step_stack, items.build())
            } else {
                setRemoteAdapter(R.id.step_stack, Intent(context, StepPageService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    data = Uri.parse("stepcounter://widget/$id")
                })
            }
            setPendingIntentTemplate(R.id.step_stack, PendingIntent.getActivity(context, id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)))
        }
        manager.updateAppWidget(id, views)
        if (Build.VERSION.SDK_INT < 31) manager.notifyAppWidgetViewDataChanged(id, R.id.step_stack)
    }
}

class StepPageService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Pages(applicationContext)
    private class Pages(val context: Context) : RemoteViewsFactory {
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
        override fun getViewAt(position: Int) = page(context, position, summary, comparison, target)
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = true
    }
}
suspend fun updateWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    publish(context, manager.getAppWidgetIds(ComponentName(context, StepWidgetReceiver::class.java)))
}
