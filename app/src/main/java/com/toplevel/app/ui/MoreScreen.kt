package com.toplevel.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.Actions
import com.toplevel.app.data.Stat

private data class Entry(val id: String, val icon: String, val title: String, val sub: String, val color: Color)

/** The menu, in a few small groups so it's easy to find things. */
private val groups = listOf(
    "YOU" to listOf(
        Entry("status", "◆", "Status", "Stats, stat points, title", C.accent),
        Entry("body", "◍", "Body", "Weight and trend", C.stat(Stat.VITALITY)),
        Entry("review", "▦", "Weekly review", "Your week + top 3 changes", C.stat(Stat.INTELLECT)),
    ),
    "CHALLENGES" to listOf(
        Entry("job", "♛", "Job change", "Questlines for a new class", C.shadow),
        Entry("dungeons", "⛩", "Dungeons", "Timed challenges", C.bad),
    ),
    "MONEY" to listOf(
        Entry("money", "€", "Spending", "Limits and no-spend streak", C.warn),
        Entry("vault", "◈", "Vault", "Gold, savings, rewards", C.warn),
    ),
    "APP" to listOf(
        Entry("settings", "⚙", "Settings", "API key, goals, plans, health, sound", C.muted),
    ),
)

private val titles = groups.flatMap { it.second }.associate { it.id to it.title } + ("menu" to "Menu")

@Composable
fun MoreScreen(vm: AppViewModel, actions: Actions, page: String, open: (String) -> Unit, back: () -> Unit) {
    BackHandler { back() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.accent)
            }
            SystemHeader((titles[page] ?: page).uppercase())
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                "menu" -> Menu(vm, open)
                "status" -> StatusScreen(vm)
                "body" -> BodyPage(vm)
                "vault" -> VaultScreen(vm)
                "review" -> ReviewPage(vm)
                "job" -> JobPage(vm)
                "dungeons" -> DungeonPage(vm)
                "money" -> MoneyPage(vm)
                else -> SetupScreen(vm, actions.requestPerms, actions.connectHealth, actions.backup, actions.restore)
            }
        }
    }
}

@Composable
private fun Menu(vm: AppViewModel, open: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        groups.forEach { (label, list) ->
            item {
                Text(label, color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
            list.forEach { e -> item { MenuRow(e) { open(e.id) } } }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Run setup again", Modifier.weight(1f), color = C.muted) { vm.restartWizard() }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

@Composable
private fun MenuRow(e: Entry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .background(Brush.horizontalGradient(listOf(e.color.copy(alpha = 0.12f), C.panel.copy(alpha = 0.9f))))
            .tap(onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.icon, color = e.color, fontSize = 20.sp, style = glowStyle(e.color, 10f), modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(e.sub, color = C.muted, fontSize = 13.sp)
        }
        Text("›", color = e.color, fontSize = 22.sp)
    }
}
