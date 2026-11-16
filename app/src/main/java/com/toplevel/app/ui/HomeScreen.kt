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
fun HomeScreen(vm: AppViewModel, openFood: () -> Unit, openCheckIn: () -> Unit, openStatus: () -> Unit, openMore: (String) -> Unit = {}) {
    val quests by vm.quests.collectAsState(initial = emptyList())
    val food by vm.food.collectAsState(initial = emptyList())
    val checkIn by vm.checkInToday.collectAsState(initial = null)
    val health by vm.healthToday.collectAsState(initial = null)
    var addOpen by remember { mutableStateOf(false) }
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
            Column {
                Text("[ SYSTEM ]", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text(
                    when (hour) { in 5..11 -> "GOOD MORNING"; in 12..17 -> "GOOD AFTERNOON"; else -> "GOOD EVENING" },
                    color = C.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                )
                Text(vm.prefs.name.ifBlank { "Player" }.uppercase(), color = Color.White, fontFamily = Fonts.display,
                    fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 2.sp, style = glowStyle(C.accent, 26f))
            }
        }

        item { PlayerCard(vm, onClick = openStatus) }

        item { AdvicePanel(moves, ai = vm.prefs.hasKey && vm.advice.isNotEmpty(), busy = vm.adviceBusy,
            canRefresh = vm.prefs.hasKey) { vm.refreshAdvice() } }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("◈", "LOG MEAL", Modifier.weight(1f), C.stat(Stat.VITALITY), openFood)
                QuickTile("◐", "CHECK-IN", Modifier.weight(1f), C.stat(Stat.SPIRIT), openCheckIn)
                QuickTile("◷", "FOCUS 25", Modifier.weight(1f), C.stat(Stat.DISCIPLINE)) { vm.startFocus(25) }
            }
        }

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
                        IconButton(onClick = { vm.regenerateQuests() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Refresh, "Regenerate", tint = C.accent)
                        }
                    }
                },
            ) {
                if (quests.isNotEmpty()) {
                    Bar(done.toFloat() / quests.size, C.good, height = 4)
                    Spacer(Modifier.height(8.dp))
                }
                if (quests.isEmpty()) Text("No quests yet. Tap ↻ to generate today's quests.", color = C.muted)
                quests.forEach { q -> QuestRow(q, onToggle = { vm.toggle(q) }, onDelete = { vm.deleteQuest(q) }) }
                Text("Tap to complete · hold to delete", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        item { ExtrasPanel(vm, openMore) }

        item { LearnPanel(vm) }

        item {
            Panel(Modifier.tap(openFood), title = "Body today", color = C.stat(Stat.VITALITY)) {
                MiniMacro("STEPS", health?.steps ?: 0, 10_000, "", C.stat(Stat.STRENGTH))
                MiniMacro("CALORIES", kcal, vm.prefs.kcalGoal, "kcal", C.warn)
                MiniMacro("PROTEIN", protein, vm.prefs.proteinGoal, "g", C.stat(Stat.VITALITY))
                MiniMacro("WATER", vm.water, vm.prefs.waterGoal, "glasses", C.accent)
                Text("Open Food to scan, log and get meal advice ›", color = C.accentSoft, fontSize = 13.sp)
            }
        }

        item {
            Panel(title = "Evening report", color = C.stat(Stat.SPIRIT)) {
                val c = checkIn
                if (c == null) {
                    Text("How was your day? Report in to gain Spirit EXP and get your coach's read on it.",
                        color = C.muted, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Start check-in", color = C.stat(Stat.SPIRIT), onClick = openCheckIn)
                } else {
                    Text("Mood ${c.mood}/5 · Energy ${c.energy}/5 · Sleep ${c.sleepHours}h",
                        color = C.accentSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (c.reflection.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(c.reflection, color = C.text, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                    TextButton(onClick = openCheckIn) { Text("Edit check-in", color = C.accent) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (addOpen) AddQuestDialog(onDismiss = { addOpen = false }) { t, s, x ->
        vm.addQuest(t, s, x); addOpen = false
    }
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
            Text("◈ $gold G", color = C.warn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
private fun QuickTile(icon: String, label: String, modifier: Modifier, color: Color, onClick: () -> Unit) {
    Column(
        modifier
            .glow(color.copy(alpha = 0.8f), SmallShape, radius = 8.dp, strength = 0.5f)
            .clip(SmallShape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), C.panel.copy(alpha = 0.9f))))
            .tap(onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, color = color, fontSize = 22.sp, style = glowStyle(color, 14f))
        Spacer(Modifier.height(4.dp))
        Text(label, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun MiniMacro(label: String, value: Int, goal: Int, unit: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            modifier = Modifier.weight(1f))
        Text("$value / $goal $unit".trim(), color = if (value > goal * 1.1 && unit != "glasses" && unit.isNotEmpty()) C.bad else C.muted,
            fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(4.dp))
    Bar(if (goal > 0) value.toFloat() / goal else 0f, color)
    Spacer(Modifier.height(10.dp))
}
