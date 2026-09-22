package dev.stepcounter.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import dev.stepcounter.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Translucent trampoline over the home screen. RemoteViews cannot deliver fling
 * MotionEvents, so a widget tap starts this nearly-invisible activity. A vertical
 * fling advances or retreats the page; a short tap (or timeout with no further
 * gesture) opens [MainActivity]. Theme is translucent with no animations.
 */
class WidgetSwipeActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var downX = 0f
    private var downY = 0f
    private var tracker: VelocityTracker? = null
    private var handled = false
    private val handler = Handler(Looper.getMainLooper())
    private val tapFallback = Runnable { finishAsTap() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        // Empty transparent content so touches land on the activity window.
        setContentView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
        })
        readWidgetId(intent)
        handler.postDelayed(tapFallback, TAP_FALLBACK_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readWidgetId(intent)
        handled = false
        handler.removeCallbacks(tapFallback)
        handler.postDelayed(tapFallback, TAP_FALLBACK_MS)
    }

    private fun readWidgetId(intent: Intent?) {
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                finishAsTap()
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(tapFallback)
                downX = event.x
                downY = event.y
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                val vy = tracker?.yVelocity ?: 0f
                val vx = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                tracker = null
                val dy = event.y - downY
                val dx = event.x - downX
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                val minFling = ViewConfiguration.get(this).scaledMinimumFlingVelocity.toFloat()
                val mostlyVertical = abs(dy) >= abs(dx)
                val farEnough = abs(dy) > slop * 2 || abs(vy) >= minFling
                if (mostlyVertical && farEnough) {
                    val delta = if (dy < 0 || vy < -minFling) 1 else -1
                    finishAsFling(delta)
                } else {
                    finishAsTap()
                }
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun finishAsFling(delta: Int) {
        if (handled) return
        handled = true
        handler.removeCallbacks(tapFallback)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetPages.advance(this, appWidgetId, delta)
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { updateWidgets(this@WidgetSwipeActivity) }
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun finishAsTap() {
        if (handled) return
        handled = true
        handler.removeCallbacks(tapFallback)
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tapFallback)
        tracker?.recycle()
        tracker = null
        super.onDestroy()
    }

    companion object {
        /** Brief window to catch a follow-up vertical fling after the widget click. */
        const val TAP_FALLBACK_MS = 320L
    }
}
