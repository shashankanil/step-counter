package dev.stepcounter

import android.app.Application
import dev.stepcounter.data.SyncWorker

class StepApplication : Application() {
    override fun onCreate() { super.onCreate(); SyncWorker.schedule(this) }
}
