package dev.stepcounter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.stepcounter.data.StepRepository
import dev.stepcounter.domain.StepSummary
import dev.stepcounter.widgets.updateWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StepUiState(
    val summary: StepSummary = StepSummary(), val loading: Boolean = true,
    val busy: Boolean = false, val permissions: Set<String> = emptySet(),
    val onboarded: Boolean = false, val message: String = "",
)

class StepViewModel(application: Application) : AndroidViewModel(application) {
    val repository = StepRepository(application)
    private val mutable = MutableStateFlow(StepUiState())
    val state = mutable.asStateFlow()
    fun refresh() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val snapshot = repository.snapshot()
                val permissions = repository.health.permissions()
                // Existing Phase 1 users with access have already completed setup.
                if (!repository.setupSeen) {
                    if (repository.health.readPermission in permissions) repository.onboarded = true
                    repository.setupSeen = true
                }
                mutable.update { it.copy(summary = snapshot, permissions = permissions,
                    onboarded = repository.onboarded, loading = false) }
                repository.sync(background = false)
                val refreshed = repository.snapshot()
                mutable.update { it.copy(summary = refreshed, message = "") }
                updateWidgets(getApplication())
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutable.update { it.copy(message = "Could not sync. Check Health Connect and try again.", loading = false) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun goal(value: Int) = edit { repository.setGoal(value) }
    fun initial(value: String) = edit { repository.initial = value }
    private fun edit(action: () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                val snapshot = repository.snapshot()
                mutable.update { it.copy(summary = snapshot) }
                updateWidgets(getApplication())
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.update { it.copy(message = "Could not update widgets. Please refresh.") } }
        }
    }
    fun finishSetup() { repository.onboarded = true; mutable.update { it.copy(onboarded = true) } }
    fun message(value: String) { mutable.update { it.copy(message = value) } }
}
