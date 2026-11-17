package com.toplevel.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Advice
import com.toplevel.app.data.Stat
import com.toplevel.app.data.localMoves
import com.toplevel.app.game.Leveling
import java.time.LocalTime

@Composable
fun HomeScreen(vm: AppViewModel, openFood: () -> Unit, openCheckIn: () -> Unit, openMore: (String) -> Unit, connectHealth: () -> Unit) {
    val quests by vm.quests.collectAsState(initial = emptyList())
    val food by vm.food.collectAsState(initial = emptyList())
    val checkIn by vm.checkInToday.collectAsState(initial = null)
    val health by vm.healthToday.collectAsState(initial = null)
    var addOpen by remember { mutableStateOf(false) }
    var wishesOpen by remember { mutableStateOf(false) }
    val hour = LocalTime.now().hour

    // Keep the advice fresh (at most every 3 h), once the morning AI work is done
    LaunchedEffect(vm.busy) { if (vm.busy == null && vm.adviceStale) vm.refreshAdvice() }

    val kcal = food.sumOf { it.kcal }
    val protein = food.sumOf { it.protein }
    val local = localMoves(
        hour, quests.filter { !it.done }.map { it.title }, kcal, vm.prefs.kcalGoal, protein, vm.prefs.proteinGoal,
        vm.water, vm.prefs.waterGoal, checkIn != null, vm.prefs.eveningHour, health?.steps ?: -1,
    )
    val moves = if (vm.prefs.hasKey && vm.advice.isNotEmpty()) vm.advice else local

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (hour) { in 5..11 -> "GOOD MORNING"; in 12..17 -> "GOOD AFTERNOON"; else -> "GOOD EVENING" },
                        color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    )
                    Text(vm.prefs.name.ifBlank { "Player" }.uppercase(), color = Color.White, fontFamily = Fonts.display,
                        fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp, style = glowStyle(C.accent, 24f))
                }
                // Everything that isn't a tab lives here
                Column(
                    Modifier.clip(SmallShape).background(C.panel.copy(alpha = 0.9f)).tap { openMore("menu") }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("☰", color = C.accent, fontSize = 20.sp)
                    Text("MENU", color = C.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
            }
        }

        item { PlayerCard(vm, onClick = { openMore("status") }) }

        // Today at a glance: steps (from Samsung Health / Health Connect, or the phone), food, water
        item {
            val steps = health?.steps ?: 0
            val noSource = !vm.healthConnected && steps == 0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayTile("STEPS", if (noSource) "—" else "%,d".format(steps).replace(',', '.'),
                    if (vm.healthConnected) "Health" else if (noSource) "tap to link" else "phone",
                    steps / 10_000f, C.stat(Stat.STRENGTH), Modifier.weight(1f)) {
                    if (!vm.healthConnected) connectHealth() else vm.syncHealth()
                }
                TodayTile("KCAL", "$kcal", "/ ${vm.prefs.kcalGoal}", kcal.toFloat() / vm.prefs.kcalGoal.coerceAtLeast(1),
                    C.warn, Modifier.weight(1f), openFood)
                TodayTile("PROTEIN", "${protein}g", "/ ${vm.prefs.proteinGoal}g", protein.toFloat() / vm.prefs.proteinGoal.coerceAtLeast(1),
                    C.stat(Stat.VITALITY), Modifier.weight(1f), openFood)
            }
        }

        // Daily check-in: always on Home. In the evening, until it's filed, it moves above the quests.
        val evening = hour >= vm.prefs.eveningHour - 2
        val checkInFirst = checkIn == null && evening
        if (checkInFirst) item { CheckInPanel(checkIn, evening, openCheckIn) }

        item {
            val done = quests.count { it.done }
            Panel(
                title = "Daily quests",
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$done/${quests.size}", color = if (quests.isNotEmpty() && done == quests.size) C.good else C.accentSoft,
                            fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        IconButton(onClick = { addOpen = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Add, "Add quest", tint = C.accent)
                        }
                        IconButton(onClick = { if (vm.prefs.hasKey) wishesOpen = true else vm.regenerateQuests() },
                            modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Refresh, "New quests", tint = C.accent)
                        }
                    }
                },
            ) {
                if (quests.isNotEmpty()) {
                    Bar(done.toFloat() / quests.size, C.good, height = 4)
                    Spacer(Modifier.height(8.dp))
                }
                if (quests.isEmpty()) Text("No quests yet. Tap ↻ to get today's quests.", color = C.muted)
                quests.forEach { q -> QuestRow(q, onToggle = { vm.toggle(q) }, onDelete = { vm.deleteQuest(q) }) }
                Text("Tap to complete · hold to delete (the AI won't suggest it again)", color = C.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (!checkInFirst) item { CheckInPanel(checkIn, evening, openCheckIn) }

        item { ExtrasPanel(vm, openMore) }

        item { AdvicePanel(moves.take(1), ai = vm.prefs.hasKey && vm.advice.isNotEmpty(), busy = vm.adviceBusy,
            canRefresh = vm.prefs.hasKey) { vm.refreshAdvice(manual = true) } }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (addOpen) AddQuestDialog(onDismiss = { addOpen = false }) { t, s, x ->
        vm.addQuest(t, s, x); addOpen = false
    }
    if (wishesOpen) QuestWishesDialog(vm.prefs.questWishes, onDismiss = { wishesOpen = false }) { w ->
        vm.regenerateQuests(w); wishesOpen = false
    }
}

@Composable
private fun CheckInPanel(c: com.toplevel.app.data.CheckIn?, evening: Boolean, openCheckIn: () -> Unit) {
    Panel(title = "Daily check-in", color = C.stat(Stat.SPIRIT)) {
        if (c == null) {
            Text(if (evening) "How was your day? Report in to gain Spirit EXP and get your coach's read on it."
                else "Check in any time today: mood, energy, sleep and how it's going. Your coach uses it for tomorrow's quests.",
                color = if (evening) C.text else C.muted, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            GhostButton("Start check-in", color = C.stat(Stat.SPIRIT), onClick = openCheckIn)
        } else {
            Text("✓ Done · Mood ${c.mood}/5 · Energy ${c.energy}/5 · Sleep ${c.sleepHours}h",
                color = C.accentSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (c.reflection.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(c.reflection, color = C.text, fontSize = 15.sp, lineHeight = 21.sp)
            }
            TextButton(onClick = openCheckIn) { Text("Edit check-in", color = C.accent) }
        }
    }
}

/** Before new quests: what does the user want? Kept for the following days too. */
@Composable
private fun QuestWishesDialog(current: String, onDismiss: () -> Unit, onGo: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.panel,
        title = { Text("New quests", color = C.text) },
        text = {
            Column {
                Text("Tell the AI what you want. It also learns from which quests you do, skip or delete.",
                    color = C.muted, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Field(text, { text = it }, "Your wishes (optional)", minLines = 2, voice = true,
                    placeholder = "e.g. little time today, more learning, no running, only 4 quests")
            }
        },
        confirmButton = { TextButton(onClick = { onGo(text.trim()) }) { Text("Make quests", color = C.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Name, title, level, rank, EXP. Tap: achievements / status. */
@Composable
fun PlayerCard(vm: AppViewModel, onClick: () -> Unit) {
    val totalsList by vm.statTotals.collectAsState(initial = emptyList())
    val gold by vm.gold.collectAsState(initial = 0)
    val xp = Leveling.totals(totalsList).values.sum()
    val p = Leveling.progress(xp)
    val rank = Leveling.rank(p.level)
    val rc = C.rank(rank)
    Panel(Modifier.tap(onClick), color = C.accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Rank emblem
            Box(
                Modifier.size(64.dp).glow(rc, WindowShape, radius = 14.dp, strength = 0.9f, width = 1.5.dp)
                    .clip(WindowShape).background(Brush.radialGradient(listOf(rc.copy(alpha = 0.35f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) {
                Text(rank, color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Black, fontSize = 30.sp,
                    style = glowStyle(rc, 22f))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("TITLE", color = C.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(vm.activeTitle.ifBlank { Leveling.title(p.level) }, color = C.accentSoft, fontSize = 17.sp,
                    fontWeight = FontWeight.Bold)
                Text("$rank-RANK HUNTER", color = rc, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("LEVEL", color = C.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("${p.level}", color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Black,
                    fontSize = 40.sp, style = glowStyle(C.accent, 22f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Bar(p.fraction, C.accent, height = 7)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("EXP ${p.intoLevel} / ${p.levelSpan}", color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            if (vm.extra("vault")) Text("◈ $gold G", color = C.warn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (vm.streak > 0) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥 ${vm.streak}-day streak", color = C.warn, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Chip("EXP ×${"%.2f".format(vm.multiplier)}", C.warn)
            }
        }
    }
}

@Composable
private fun AdvicePanel(moves: List<Advice>, ai: Boolean, busy: Boolean, canRefresh: Boolean, refresh: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "adv")
    val pulse by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Panel(
        title = "Best next move",
        color = C.accent,
        action = {
            if (canRefresh) IconButton(onClick = refresh, enabled = !busy, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Refresh, "New advice", tint = if (busy) C.muted else C.accent)
            }
        },
    ) {
        if (busy) {
            Text("▍ Analysing your status…", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(pulse))
            Spacer(Modifier.height(8.dp))
        }
        moves.firstOrNull()?.let { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(m.kind.uppercase(), kindColor(m.kind))
                Spacer(Modifier.width(8.dp))
                Text(if (ai) "CHOSEN BY YOUR COACH" else "SYSTEM RULES", color = C.muted, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(m.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp,
                style = glowStyle(C.accent, 10f))
            if (m.detail.isNotBlank()) Text(m.detail, color = C.text, fontSize = 15.sp, lineHeight = 20.sp)
            if (m.why.isNotBlank()) Text("↳ ${m.why}", color = C.accentSoft, fontSize = 14.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
        if (moves.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text("OR", color = C.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            moves.drop(1).forEach { m ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp)) { Diamond(kindColor(m.kind), 7.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(m.title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (m.detail.isNotBlank()) Text(m.detail, color = C.muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

fun kindColor(kind: String): Color = when (kind) {
    "food" -> C.stat(Stat.VITALITY)
    "train" -> C.stat(Stat.STRENGTH)
    "work" -> C.stat(Stat.DISCIPLINE)
    "learn" -> C.stat(Stat.INTELLECT)
    "mind", "rest", "sleep" -> C.stat(Stat.SPIRIT)
    "money" -> C.warn
    else -> C.accent
}

@Composable
private fun TodayTile(label: String, value: String, sub: String, fraction: Float, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .glow(color.copy(alpha = 0.6f), SmallShape, radius = 6.dp, strength = 0.35f)
            .clip(SmallShape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.14f), C.panel.copy(alpha = 0.9f))))
            .tap(onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sub, color = C.muted, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Bar(fraction, color, height = 3)
    }
}
