package dev.stepcounter.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.room.withTransaction
import dev.stepcounter.data.db.*
import dev.stepcounter.data.health.HealthSource
import dev.stepcounter.domain.StepSummary
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

class StepRepository(private val context: Context) {
    private val auth = dev.stepcounter.data.auth.AuthRepository(context)
    private val db = StepDatabase.get(context)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val health = HealthSource(context)
    fun setGoal(value: Int) { require(value in 100..100_000); prefs.edit().putInt("goal", value).apply() }
    var initial: String
        get() = prefs.getString("initial", "S") ?: "S"
        set(value) { prefs.edit().putString("initial", value.take(1).uppercase(java.util.Locale.ROOT)).apply() }
    var setupSeen: Boolean
        get() = prefs.getBoolean("setup_seen", false)
        set(value) { prefs.edit().putBoolean("setup_seen", value).apply() }
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) { prefs.edit().putBoolean("onboarded", value).apply() }
    suspend fun snapshot(): StepSummary {
        val zone = ZoneId.systemDefault().id
        val rows = if (prefs.getString("zone", zone) == zone) db.steps().all() else emptyList()
        return StepSummary(days = rows.associate { LocalDate.parse(it.date) to it.steps },
            initial = auth.profile?.initial ?: initial, goal = prefs.getInt("goal", 10_000), updatedAt = rows.maxOfOrNull { it.syncedAt } ?: 0,
            status = prefs.getString("status", "Connect Health Connect")!!)
    }
    suspend fun sync(background: Boolean): Boolean = lock.withLock {
        if (health.availability != HealthConnectClient.SDK_AVAILABLE) {
            status("Health Connect unavailable"); return@withLock false
        }
        val permissions = health.permissions()
        if (health.readPermission !in permissions) {
            db.steps().clear(); status("Allow step access"); return@withLock false
        }
        if (background && (!health.backgroundSupported() || health.backgroundPermission !in permissions)) {
            status("Open app to refresh"); return@withLock false
        }
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        // A rolling 30-day window stays inside Health Connect's default history allowance.
        val rows = (0L..29L).map { ago ->
            val date = today.minusDays(ago)
            DailySteps(date.toString(), health.steps(date, zone), now)
        }
        db.withTransaction {
            if (prefs.getString("zone", zone.id) != zone.id) db.steps().clear()
            db.steps().upsert(rows)
            db.steps().prune(today.minusDays(62).toString())
        }
        prefs.edit().putString("zone", zone.id).apply()
        status(if (health.backgroundPermission in permissions) "Synced · Health Connect" else "Synced · refresh in app")
        try {
            val social = dev.stepcounter.data.social.SocialRepository(context)
            social.sync(snapshot())
            if (social.target.isNotBlank()) social.compare()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Cloud failure must not fail local Health Connect sync. */ }
        true
    }
    fun status(value: String) { prefs.edit().putString("status", value).apply() }
    companion object { private val lock = Mutex() }
}
