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
        message = ""
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
        // The ViewModel is keyed by account; retain loaded rows during an offline retry.
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

@Composable fun SocialScreen(state: StepUiState, appModel: StepViewModel, model: SocialViewModel = viewModel()) {
    var page by rememberSaveable { mutableStateOf("Overview") }
    var handle by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    androidx.activity.compose.BackHandler(enabled = page != "Overview") { page = "Overview" }
    val connected = state.account?.connected == true
    LaunchedEffect(connected) { if (connected) model.refresh() }
    if (!connected) {
        Page {
            Heading("Together", "A little company.")
            Text("Walk at your own pace, with people you know.")
            AccountTile(state, appModel)
            Tile {
                Eyebrow("Always opt-in")
                Text("Signing in does not share your steps. When connected, choose who to walk with and whether to share daily totals.")
                if (state.account != null) Text("Friends and groups need a cloud connection. Your local steps are still available in Home.")
            }
        }
        return
    }
    Page {
        Heading("Together", if (page == "Compare") model.selected else "A little company.")
        if (page != "Overview") TextButton({ page = "Overview" }) { Text("← People & groups") }
        if (page == "Overview") {
            Column {
                Action("Add friend", !model.busy) { page = "Friends" }
                TextButton({ page = "Groups" }) { Text("Create or join group") }
            }
            if (model.selected.isNotBlank()) TextButton({ page = "Compare" }) { Text("Pinned compare · ${model.selected} →") }
        }
        if (model.message.isNotBlank()) Text(model.message)
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (page) {
            "Overview" -> {
                Eyebrow("People")
                if (model.friends.isEmpty()) Text("No people loaded. Add a friend by their Google account email.")
                model.friends.forEach { friend -> Tile {
                    Text(friend.getString("name"), fontSize = 22.sp)
                    val accepted = friend.getString("status") == "accepted"
                    Text(if (accepted) "Accepted friend" else if (friend.getBoolean("outgoing")) "Pending · request sent" else "Pending · wants to walk with you")
                    if (!accepted && !friend.getBoolean("outgoing")) Action("Accept", !model.busy) {
                        model.mutate("/friends/${friend.getString("id")}/accept", JSONObject())
                    }
                    if (accepted) TextButton({ model.select("friend=${friend.getString("userId")}", friend.getString("name")); page = "Compare" }, enabled = !model.busy) { Text("Compare →") }
                } }
                Eyebrow("Groups")
                if (model.groups.isEmpty()) Text("No groups loaded. Start one or join with an invite code.")
                model.groups.forEach { group -> Tile {
                    Text(group.getString("name"), fontSize = 22.sp)
                    Text("Joined")
                    androidx.compose.foundation.text.selection.SelectionContainer { Text("Invite code: ${group.getString("code")}") }
                    TextButton({ model.select("group=${group.getString("id")}", group.getString("name")); page = "Compare" }, enabled = !model.busy) { Text("Compare →") }
                } }
            }
            "Friends" -> {
                Tile {
                    Eyebrow("Invite a friend")
                    OutlinedTextField(handle, { handle = it }, label = { Text("Google account email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Action("Send request", !model.busy && handle.isNotBlank()) {
                        model.mutate("/friends", JSONObject().put("handle", handle))
                    }
                }
                TextButton({ page = "Overview" }) { Text("View people & requests →") }
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
                TextButton({ page = "Overview" }) { Text("View your groups →") }
            }
            else -> {
                Text(model.selected.ifBlank { "Choose a friend or group to compare." }, fontSize = 22.sp)
                Text("Pinned to your widget. Seven days includes today. Missing totals are shown as unavailable.")
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
        Action("Refresh", !model.busy) { model.refresh() }
    }
}
