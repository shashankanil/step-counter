package dev.stepcounter.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.stepcounter.R
import dev.stepcounter.data.StepRepository
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.ui.MainActivity
import kotlinx.coroutines.runBlocking

class StepWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val adapter = Intent(context, StepPageService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse("stepcounter://widget/$id")
            }
            val views = RemoteViews(context.packageName, R.layout.step_widget).apply {
                setRemoteAdapter(R.id.step_stack, adapter)
                setPendingIntentTemplate(R.id.step_stack, PendingIntent.getActivity(context, id,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
            }
            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.step_stack)
        }
    }
}

class StepPageService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Pages(applicationContext)
    private class Pages(val context: Context) : RemoteViewsFactory {
        private var summary = StepSummary()
        private val pages = listOf(WidgetKind.WALK, WidgetKind.STATS, WidgetKind.MONTH)
        override fun onCreate() = Unit
        override fun onDataSetChanged() { summary = runBlocking { StepRepository(context).snapshot() } }
        override fun onDestroy() = Unit
        override fun getCount() = pages.size
        override fun getViewAt(position: Int): RemoteViews = RemoteViews(context.packageName, R.layout.step_page).apply {
            val kind = pages[position]
            setImageViewBitmap(R.id.step_page, WidgetArtwork.render(kind, summary, 360))
            setContentDescription(R.id.step_page, "${kind.title}. ${summary.steps ?: "No recorded"} steps. Swipe up or down for another page. Tap to open.")
            setOnClickFillInIntent(R.id.step_page, Intent())
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = true
    }
}
suspend fun updateWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    manager.notifyAppWidgetViewDataChanged(
        manager.getAppWidgetIds(ComponentName(context, StepWidgetReceiver::class.java)), R.id.step_stack)
}
