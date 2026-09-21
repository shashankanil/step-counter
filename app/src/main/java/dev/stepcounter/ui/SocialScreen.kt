package dev.stepcounter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import dev.stepcounter.data.social.SocialRepository
import dev.stepcounter.data.StepRepository
import dev.stepcounter.widgets.updateWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

class SocialViewModel(app: Application) : AndroidViewModel(app) {
    val repository = SocialRepository(app)
    var friends by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var groups by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var comparison by mutableStateOf<JSONObject?>(repository.cachedComparison); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var enabled by mutableStateOf(repository.enabled); private set
    var pendingDisable by mutableStateOf(repository.pendingDisable); private set
    var selected by mutableStateOf(repository.targetName); private set
    private fun launch(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Unable to reach the cloud. Try again." }
            finally { enabled = repository.enabled; pendingDisable = repository.pendingDisable; busy = false }
        }
    }
    private fun JSONObject.items(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private suspend fun load() {
        friends = repository.request("/friends").items("friends")
        groups = repository.request("/groups").items("groups")
    }
    fun refresh() = launch {
        // Clear any previous identity's UI before loading.
        friends = emptyList(); groups = emptyList()
        comparison = repository.cachedComparison; selected = repository.targetName
        repository.sync(StepRepository(getApplication()).snapshot())
        load()
        if (repository.target.isNotBlank()) comparison = repository.compare()
        message = "Up to date"
        updateWidgets(getApplication())
    }
    fun mutate(path: String, body: JSONObject) = launch {
        repository.request(path, "POST", body)
        load(); message = "Saved"
    }
    fun sharing(value: Boolean) = launch {
        repository.setSharing(value)
        if (value) repository.sync(StepRepository(getApplication()).snapshot())
        if (!value) comparison = null
        message = if (value) "Step sharing enabled for friends and group members." else "Sharing off. Uploaded totals deleted."
        updateWidgets(getApplication())
    }
    fun select(value: String, name: String) = launch {
        repository.selectTarget(value, name); selected = name; comparison = null
        updateWidgets(getApplication())
        comparison = repository.compare()
        message = "Comparison selected for your widget"
        updateWidgets(getApplication())
    }
}

@Composable fun SocialScreen(model: SocialViewModel = viewModel()) {
    var page by rememberSaveable { mutableStateOf("Friends") }
    var handle by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { model.refresh() }
    Page {
        Heading("Together, at your pace", "A little company.")
        Tile {
            Eyebrow("Step sharing")
            Text("Opt in to upload daily totals. Accepted friends and members of your groups can see today and the last seven days. Turning off deletes uploaded totals.")
            Row {
                Switch(model.enabled, { model.sharing(it) }, enabled = !model.busy)
                Text(if (model.enabled) "Sharing enabled" else "Steps stay on this device")
            }
            if (model.pendingDisable) {
                Text("Uploads stopped on this device. Cloud deletion is pending; reconnect and retry.")
                Action("Retry turning off", !model.busy) { model.sharing(false) }
            }
        }
        Row { listOf("Friends", "Groups", "Compare").forEach { tab ->
            TextButton({ page = tab }) { Text(if (page == tab) "• $tab" else tab) }
        } }
        if (model.message.isNotBlank()) Text(model.message)
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (page) {
            "Friends" -> {
                Tile {
                    Eyebrow("Invite a friend")
                    OutlinedTextField(handle, { handle = it }, label = { Text("Google account email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Action("Send request", !model.busy && handle.isNotBlank()) {
                        model.mutate("/friends", JSONObject().put("handle", handle))
                    }
                }
                if (model.friends.isEmpty()) Text("No friends to show. Connect to the cloud and invite someone.")
                model.friends.forEach { friend -> Tile {
                    Text(friend.getString("name"), fontSize = 22.sp)
                    Text(friend.getString("email"))
                    val accepted = friend.getString("status") == "accepted"
                    Text(if (accepted) "Friends" else if (friend.getBoolean("outgoing")) "Request sent" else "Wants to walk with you")
                    if (!accepted && !friend.getBoolean("outgoing"))
                        Action("Accept", !model.busy) { model.mutate("/friends/${friend.getString("id")}/accept", JSONObject()) }
                    if (accepted) Action("Compare", !model.busy) {
                        model.select("friend=${friend.getString("userId")}", friend.getString("name")); page = "Compare"
                    }
                } }
            }
            "Groups" -> {
                Tile {
                    Eyebrow("Start a group")
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Group name") }, modifier = Modifier.fillMaxWidth())
                    Action("Create", !model.busy && name.isNotBlank()) { model.mutate("/groups", JSONObject().put("name", name)) }
                }
                Tile {
                    Eyebrow("Have an invite?")
                    OutlinedTextField(code, { code = it }, label = { Text("Group code") }, modifier = Modifier.fillMaxWidth())
                    Action("Join", !model.busy && code.isNotBlank()) { model.mutate("/groups/join", JSONObject().put("code", code)) }
                }
                if (model.groups.isEmpty()) Text("No groups to show. Create one or ask someone for their invite code.")
                model.groups.forEach { group -> Tile {
                    Text(group.getString("name"), fontSize = 22.sp)
                    androidx.compose.foundation.text.selection.SelectionContainer { Text("Invite code: ${group.getString("code")}") }
                    Action("Compare group", !model.busy) {
                        model.select("group=${group.getString("id")}", group.getString("name")); page = "Compare"
                    }
                } }
            }
            else -> {
                Text(model.selected.ifBlank { "Choose a friend or group to compare." }, fontSize = 22.sp)
                Text("Your selection also appears on the widget. Seven days includes today; incomplete totals show as unavailable.")
                val result = model.comparison
                if (result == null) Text("No comparison available. Select a target and refresh when connected.")
                else {
                    Text("Last fetched ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(result.optLong("fetchedAt")))}")
                    val members = result.getJSONArray("members")
                    val max = (0 until members.length()).maxOfOrNull { members.getJSONObject(it).optDouble("today", 0.0) }?.coerceAtLeast(1.0) ?: 1.0
                    repeat(members.length()) { index ->
                        val member = members.getJSONObject(index)
                        Tile {
                            Text(if (member.getString("id") == result.getString("selfId")) "You" else member.getString("name"), fontSize = 22.sp)
                            Text(if (!member.getBoolean("sharing")) "Not sharing steps" else
                                "Today: ${if (member.isNull("today")) "Unavailable" else member.getLong("today")} · 7 days: ${if (member.isNull("sevenDay")) "Unavailable" else member.getLong("sevenDay")}")
                            if (!member.isNull("today")) LinearProgressIndicator(progress = { (member.getDouble("today") / max).toFloat() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        Action("Refresh", !model.busy) { model.refresh() }
    }
}
