package dev.stepcounter.data

import android.content.Context
import androidx.work.*
import dev.stepcounter.widgets.updateWidgets
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = StepRepository(applicationContext)
        val result = try {
            repository.sync(background = true)
            Result.success()
        } catch (e: CancellationException) { throw e
        } catch (_: SecurityException) {
            repository.status("Allow step access in app")
            Result.success()
        } catch (_: Exception) {
            repository.status("Sync delayed · open app")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        updateWidgets(applicationContext)
        return result
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("steps-sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).build())
        }
    }
}
