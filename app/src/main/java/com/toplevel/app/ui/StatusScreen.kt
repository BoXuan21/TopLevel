package com.toplevel.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.DiscoverItem
import com.toplevel.app.data.Quest
import com.toplevel.app.data.Stat
import com.toplevel.app.game.Leveling
import java.net.URLEncoder

@Composable
fun StatusScreen(vm: AppViewModel) {
    val totalsList by vm.statTotals.collectAsState(initial = emptyList())
    val discover by vm.discover.collectAsState(initial = emptyList())
    val health by vm.healthToday.collectAsState(initial = null)
    val boss by vm.boss.collectAsState(initial = null)
    var achOpen by remember { mutableStateOf(false) }
    val totals = Leveling.totals(totalsList)

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Gap,
    ) {
        item { PlayerCard(vm) { achOpen = true } }

        // Health
        if (vm.healthConnected && health != null) {
            item {
                val h = health!!
                Panel(title = "Vitals · synced") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Vital("STEPS", "${h.steps}", Modifier.weight(1f))
                        Vital("SLEEP", "${h.sleepMin / 60}h${"%02d".format(h.sleepMin % 60)}", Modifier.weight(1f))
                        Vital("WORKOUT", "${h.workoutMin}m", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Bar(h.steps / 10_000f, C.stat(Stat.VITALITY))
                }
            }
        }

        // Focus
        item { FocusPanel(vm) }

        // Weekly boss
        item {
            val b = boss
            Panel(
                title = "Weekly boss",
                action = {
                    if (b == null || !b.defeated) IconButton(onClick = { vm.newBoss() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Refresh, "New boss", tint = C.accent)
                    }
                },
            ) {
                if (b == null) Text("No boss yet. Tap ↻ to summon this week's boss.", color = C.muted, fontSize = 14.sp)
                else {
                    Text(b.name.uppercase(), color = if (b.defeated) C.good else C.bad, fontSize = 18.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text(b.goal, color = C.text, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Chip(if (b.defeated) "DEFEATED" else "REWARD +${b.xp} EXP", if (b.defeated) C.good else C.warn)
                    if (!b.defeated) {
                        Spacer(Modifier.height(8.dp))
                        GhostButton("I defeated it") { vm.defeatBoss() }
                    }
                    if (b.report.isNotBlank()) {
                        var showReport by remember { mutableStateOf(false) }
                        TextButton(onClick = { showReport = !showReport }) {
                            Text(if (showReport) "Hide last week's report" else "Last week's report", color = C.accent)
                        }
                        if (showReport) Text(b.report, color = C.text, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }

        // Stats
        item {
            Panel(title = "Stats") {
                Stat.entries.forEach { s ->
                    val sxp = totals[s] ?: 0
                    val lvl = Leveling.statLevel(sxp)
                    val base = 20 * (lvl - 1) * (lvl - 1)
                    val next = 20 * lvl * lvl
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(s.short, color = C.stat(s), fontFamily = Fonts.tech,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(40.dp))
                        Bar((sxp - base).toFloat() / (next - base), C.stat(s), Modifier.weight(1f))
                        Text("$lvl", color = C.text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.width(36.dp).padding(start = 10.dp))
                    }
                }
            }
        }

        // Calendar
        if (vm.events.isNotEmpty()) {
            item {
                Panel(title = "Today's schedule") {
                    vm.events.forEach { e ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(e.timeLabel(), color = C.accentSoft, fontSize = 12.sp,
                                fontFamily = Fonts.tech, modifier = Modifier.width(96.dp))
                            Text(e.title, color = C.text, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (achOpen) AchievementsDialog(vm) { achOpen = false }
}

@Composable
fun SystemHeader(title: String) {
    Column {
        Text("[ SYSTEM ]", color = C.accent, fontSize = 12.sp, fontFamily = Fonts.tech, fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp)
        Text(title, color = Color.White, fontSize = 24.sp, fontFamily = Fonts.display, fontWeight = FontWeight.Black,
            letterSpacing = 3.sp, style = glowStyle(C.accent, 24f))
    }
}

@Composable
fun SystemNotice(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(SmallShape).background(C.accent.copy(alpha = 0.12f)).padding(10.dp)
    ) {
        Text("! ", color = C.accent, fontWeight = FontWeight.Black, fontFamily = Fonts.tech)
        Text(text, color = C.accentSoft, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuestRow(q: Quest, onToggle: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(SmallShape)
            .background(if (q.source == "penalty" && !q.done) C.bad.copy(alpha = 0.15f) else C.bg.copy(alpha = 0.5f))
            .combinedClickable(onClick = onToggle, onLongClick = { confirm = true })
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(SmallShape)
                .background(if (q.done) C.good else C.panel2),
            contentAlignment = Alignment.Center,
        ) { if (q.done) Text("✓", color = C.bg, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(q.title, color = if (q.done) C.muted else C.text, fontSize = 14.sp,
                textDecoration = if (q.done) TextDecoration.LineThrough else null)
            if (q.detail.isNotBlank()) Text(q.detail, color = C.muted, fontSize = 12.sp)
            if (q.autoType.isNotEmpty() && q.target > 0) Text(
                "⟳ auto · " + when (q.autoType) {
                    "steps" -> "${q.target} steps"; "sleep" -> "${q.target / 60}h${q.target % 60} sleep"; else -> "${q.target} min workout"
                }, color = C.accent, fontSize = 11.sp, fontFamily = Fonts.tech)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Chip(q.stat.short, C.stat(q.stat))
            Text("+${q.xp}", color = C.accentSoft, fontSize = 11.sp, fontFamily = Fonts.tech)
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        confirmButton = { TextButton(onClick = { onDelete(); confirm = false }) { Text("Delete", color = C.bad) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        title = { Text("Delete quest?") },
        text = { Text(q.title) },
        containerColor = C.panel,
    )
}

/** Daily learn & grow feed: videos for work/learning, tools to try, songs. */
@Composable
fun LearnPanel(vm: AppViewModel) {
    val discover by vm.discover.collectAsState(initial = emptyList())
    Panel(
        title = "Learn & grow",
        color = C.stat(Stat.INTELLECT),
        action = {
            if (vm.prefs.hasKey) IconButton(onClick = { vm.regenerateDiscover() }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Refresh, "New picks", tint = C.accent)
            }
        },
    ) {
        if (discover.isEmpty()) {
            Text(
                if (vm.prefs.hasKey) "Tap ↻ for today's videos and tools for your work and learning goals."
                else "Add your API key in Settings to get daily videos and tools for your work and learning.",
                color = C.muted, fontSize = 15.sp,
            )
        }
        listOf(
            Triple("video", "▶ WATCH", C.stat(Stat.INTELLECT)),
            Triple("tool", "⚒ TRY AT WORK", C.stat(Stat.DISCIPLINE)),
            Triple("song", "♪ SOUNDTRACK", C.stat(Stat.SPIRIT)),
        ).forEach { (kind, label, color) ->
            val items = discover.filter { it.kind == kind }
            if (items.isNotEmpty()) {
                Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 6.dp))
                items.forEach { DiscoverRow(it) }
            }
        }
    }
}

@Composable
private fun DiscoverRow(d: DiscoverItem) {
    val ctx = LocalContext.current
    val q = URLEncoder.encode(d.query, "UTF-8").replace("+", "%20")
    val url = when (d.kind) {
        "song" -> "https://music.youtube.com/search?q=$q"
        "tool" -> "https://www.google.com/search?q=$q"
        else -> "https://www.youtube.com/results?search_query=$q"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(SmallShape)
            .background(C.bg.copy(alpha = 0.5f))
            .tap { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(10.dp)
    ) {
        Text(d.title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (d.subtitle.isNotBlank()) Text(d.subtitle, color = C.accentSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (d.why.isNotBlank()) Text(d.why, color = C.muted, fontSize = 14.sp, lineHeight = 18.sp)
    }
}


@Composable
fun AddQuestDialog(onDismiss: () -> Unit, onAdd: (String, Stat, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var stat by remember { mutableStateOf(Stat.DISCIPLINE) }
    var xp by remember { mutableStateOf("25") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panel,
        title = { Text("New quest", color = C.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(title, { title = it }, "Quest")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat.entries.forEach { s ->
                        Box(Modifier.clip(SmallShape)
                            .background(if (s == stat) C.stat(s).copy(alpha = 0.3f) else C.bg)
                            .tap { stat = s }
                            .padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text(s.short, color = C.stat(s), fontSize = 11.sp, fontFamily = Fonts.tech)
                        }
                    }
                }
                Field(xp, { xp = it.filter(Char::isDigit) }, "EXP", singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onAdd(title, stat, xp.toIntOrNull() ?: 25) }) {
                Text("Add", color = C.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Vital(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(SmallShape).background(C.bg.copy(alpha = 0.5f)).padding(10.dp)) {
        Text(label, color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
        Text(value, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FocusPanel(vm: AppViewModel) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val running = vm.focusEnd > now
    androidx.compose.runtime.LaunchedEffect(vm.focusEnd) {
        while (true) {
            now = System.currentTimeMillis()
            if (vm.focusEnd != 0L && now >= vm.focusEnd) { vm.focusFinishedLocally() }
            kotlinx.coroutines.delay(1000)
        }
    }
    Panel(title = "Focus block") {
        if (running) {
            val left = (vm.focusEnd - now) / 1000
            val total = vm.prefs.focusLen * 60f
            Text("%02d:%02d".format(left / 60, left % 60), color = C.text, fontSize = 40.sp,
                fontWeight = FontWeight.Black, fontFamily = Fonts.tech)
            Bar(1f - left / total, C.stat(Stat.DISCIPLINE), height = 8)
            Spacer(Modifier.height(8.dp))
            Text("Phone down. Deep work. +${vm.prefs.focusLen / 2} DIS when done.", color = C.muted, fontSize = 12.sp)
            TextButton(onClick = { vm.cancelFocus() }) { Text("Abort (no EXP)", color = C.bad) }
        } else {
            Text("Start a timed deep-work block. Completing it awards Discipline EXP.", color = C.muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 90).forEach { m -> GhostButton("$m min") { vm.startFocus(m) } }
            }
        }
    }
}

@Composable
fun AchievementsDialog(vm: AppViewModel, onClose: () -> Unit) {
    val unlocked by vm.achievements.collectAsState(initial = emptyList())
    val ids = unlocked.map { it.id }.toSet()
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text("Achievements · ${ids.size}/${com.toplevel.app.data.Achievements.all.size}", color = C.text) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text("Tap an unlocked achievement to wear its title.", color = C.muted, fontSize = 12.sp)
                }
                com.toplevel.app.data.Achievements.all.forEach { a ->
                    item {
                        val got = a.id in ids
                        val wearing = vm.activeTitle == a.title
                        Column(
                            Modifier.fillMaxWidth().clip(SmallShape)
                                .background(if (wearing) C.accent.copy(alpha = 0.2f) else C.bg.copy(alpha = 0.5f))
                                .tap { if (got) vm.setTitle(if (wearing) "" else a.title) }
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (got) "◆ " else "◇ ", color = if (got) C.good else C.muted)
                                Text(a.name, color = if (got) C.text else C.muted, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f))
                                Chip("+${a.xp}", C.stat(a.stat))
                            }
                            Text(a.desc, color = C.muted, fontSize = 12.sp)
                            if (got) Text("Title: ${a.title}${if (wearing) " (equipped)" else ""}", color = C.accentSoft, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = C.accent) } },
    )
}
