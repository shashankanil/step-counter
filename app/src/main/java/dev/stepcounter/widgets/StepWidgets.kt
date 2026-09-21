package dev.stepcounter.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import dev.stepcounter.data.StepRepository
import dev.stepcounter.ui.MainActivity

abstract class StepWidget(private val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summary = StepRepository(context).snapshot()
        val bitmap = WidgetArtwork.render(kind, summary)
        provideContent {
            Box(GlanceModifier.fillMaxSize().padding(2.dp).clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(bitmap),
                    contentDescription = "${kind.title}: ${summary.steps ?: "No data"} steps, ${summary.percent}% of ${summary.goal}. " +
                        "Seven-day average ${summary.average ?: "unavailable"}. ${summary.status}. Tap to open.",
                    modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}
class WalkProgressWidget : StepWidget(WidgetKind.WALK)
class StatsStackWidget : StepWidget(WidgetKind.STATS)
class MonthGridWidget : StepWidget(WidgetKind.MONTH)
class CircularMetricWidget : StepWidget(WidgetKind.CIRCULAR)
class WalkProgressReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = WalkProgressWidget() }
class StatsStackReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = StatsStackWidget() }
class MonthGridReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = MonthGridWidget() }
class CircularMetricReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = CircularMetricWidget() }
suspend fun updateWidgets(context: Context) {
    WalkProgressWidget().updateAll(context)
    StatsStackWidget().updateAll(context)
    MonthGridWidget().updateAll(context)
    CircularMetricWidget().updateAll(context)
}
