package dev.stepcounter.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import dev.stepcounter.widgets.WidgetKind

@Composable fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp), content = content)
}
@Composable fun Tile(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Color(Design.Surface), RoundedCornerShape(32.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
@Composable fun Heading(kicker: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow(kicker)
        Text(title, fontSize = 34.sp, lineHeight = 39.sp, color = Color(Design.White))
    }
}
@Composable fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = CircleShape, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) { Text(label) }
}

@Composable fun StepApp(state: StepUiState, model: StepViewModel, connect: (Boolean) -> Unit, pin: (WidgetKind) -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "Today"
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) { snack.showSnackbar(state.message); model.message("") }
    }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Scaffold(containerColor = Color.Black, snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (!state.loading && state.onboarded && route != "Privacy") {
                    Row(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)
                        .fillMaxWidth().background(Color(Design.Surface), CircleShape).padding(5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Today", "History", "Widgets", "Social", "You").forEach { tab ->
                            TextButton(onClick = {
                                nav.navigate(tab) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
                            }, modifier = Modifier.weight(1f).semantics { selected = route == tab },
                                colors = ButtonDefaults.textButtonColors(contentColor = if (route == tab) Color.White else Color(Design.Grey))) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(4.dp).background(if (route == tab) Color(Design.Red) else Color.Transparent, CircleShape))
                                    Spacer(Modifier.height(6.dp))
                                    Text(tab, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Eyebrow("Step / Counter") }
                    !state.onboarded -> Onboarding(state, model, connect, pin, { goalOpen = true })
                    else -> NavHost(nav, startDestination = "Today", enterTransition = { fadeIn(tween(250)) }, exitTransition = { fadeOut(tween(150)) }) {
                        composable("Today") { TodayScreen(state, { model.refresh() }, { goalOpen = true }, { nav.navigate("You") }) }
                        composable("History") { HistoryScreen(state.summary) }
                        composable("Widgets") { WidgetsScreen(state.summary, pin) }
                        composable("Social") { SocialScreen(androidx.lifecycle.viewmodel.compose.viewModel(key = state.account?.email ?: "local")) }
                        composable("You") { YouScreen(state, model, connect, { goalOpen = true }, { nav.navigate("Privacy") }) }
                        composable("Privacy") { PrivacyContent { nav.popBackStack() } }
                    }
                }
            }
        }
    }
    if (goalOpen) GoalDialog(state.summary.goal, { goalOpen = false }) { model.goal(it); goalOpen = false }
}
