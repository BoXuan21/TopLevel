package com.toplevel.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

private val entries = listOf(
    Entry("status", "◆", "Status", "Stats, weekly boss, focus", C.accent),
    Entry("review", "▦", "Weekly review", "Your week in charts + top 3 changes", C.stat(Stat.INTELLECT)),
    Entry("job", "♛", "Job change", "Class-change questlines, titles, themes", C.shadow),
    Entry("dungeons", "⛩", "Dungeons", "Timed challenges: clear or take a penalty", C.bad),
    Entry("cards", "✎", "Flashcards", "Review what you learned", C.stat(Stat.INTELLECT)),
    Entry("money", "€", "Money", "Spending, limits, no-spend streak", C.warn),
    Entry("body", "◍", "Body", "Weight, waist and trend", C.stat(Stat.VITALITY)),
    Entry("journal", "▤", "Journal", "Work, learning and notes", C.stat(Stat.DISCIPLINE)),
    Entry("skills", "✦", "Skill tree", "What you're learning", C.stat(Stat.INTELLECT)),
    Entry("vault", "◈", "Vault", "Gold, savings and rewards", C.warn),
    Entry("settings", "⚙", "Settings", "API key, goals, plans, backup", C.muted),
)

@Composable
fun MoreScreen(vm: AppViewModel, actions: Actions, page: String?, setPage: (String?) -> Unit) {
    BackHandler(enabled = page != null) { setPage(null) }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState != null) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            else (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
        },
        label = "more",
    ) { p ->
        if (p == null) Menu(vm, setPage)
        else Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { setPage(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.accent)
                }
                SystemHeader(entries.firstOrNull { it.id == p }?.title?.uppercase() ?: "")
            }
            Box(Modifier.weight(1f)) {
                when (p) {
                    "status" -> StatusScreen(vm)
                    "body" -> BodyPage(vm)
                    "journal" -> JournalPage(vm)
                    "skills" -> SkillsPage(vm)
                    "vault" -> VaultScreen(vm)
                    "review" -> ReviewPage(vm)
                    "job" -> JobPage(vm)
                    "dungeons" -> DungeonPage(vm)
                    "cards" -> CardsPage(vm)
                    "money" -> MoneyPage(vm)
                    else -> SetupScreen(vm, actions.requestPerms, actions.connectHealth, actions.backup, actions.restore)
                }
            }
        }
    }
}

@Composable
private fun Menu(vm: AppViewModel, open: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item { SystemHeader("MENU") }
        item { PlayerCard(vm) { open("status") } }
        entries.forEach { e ->
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glow(e.color.copy(alpha = 0.55f), SmallShape, radius = 6.dp, strength = 0.35f)
                        .clip(SmallShape)
                        .background(Brush.horizontalGradient(listOf(e.color.copy(alpha = 0.12f), C.panel.copy(alpha = 0.9f))))
                        .tap { open(e.id) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e.icon, color = e.color, fontSize = 22.sp, style = glowStyle(e.color, 12f),
                        modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title.uppercase(), color = C.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(e.sub, color = C.muted, fontSize = 14.sp)
                    }
                    Text("›", color = e.color, fontSize = 24.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Run setup again", Modifier.weight(1f), color = C.muted) { vm.restartWizard() }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}
