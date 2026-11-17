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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Dungeons
import com.toplevel.app.data.Growth
import com.toplevel.app.data.Jobs
import com.toplevel.app.data.Stat
import com.toplevel.app.data.Themes
import com.toplevel.app.game.Leveling
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun eur(v: Double) = "€" + if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)

/** Current overall level (for stat points). */
@Composable
private fun currentLevel(vm: AppViewModel): Int {
    val totals by vm.statTotals.collectAsState(initial = emptyList())
    return Leveling.levelFor(Leveling.totals(totals).values.sum())
}

// ───────────────────────── Stat points ─────────────────────────

@Composable
fun StatPointsPanel(vm: AppViewModel) {
    val level = currentLevel(vm)
    @Suppress("UNUSED_VARIABLE") val v = vm.pointsVersion // redraw after spending
    val free = vm.unspentPoints(level)
    Panel(title = "Stat points", color = if (free > 0) C.warn else C.accent, action = {
        Text(if (free > 0) "$free FREE" else "0 free", color = if (free > 0) C.warn else C.muted, fontWeight = FontWeight.Bold)
    }) {
        Text("Every level gives ${Growth.POINTS_PER_LEVEL} points. Each point in a stat makes that stat earn " +
            "+${(Growth.BOOST_PER_POINT * 100).roundToInt()}% EXP (up to ×2). Choose how you grow.", color = C.muted, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Stat.entries.forEach { s ->
            val pts = vm.prefs.points(s)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.short, color = C.stat(s), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(44.dp))
                Text("$pts pts", color = C.text, fontSize = 15.sp, modifier = Modifier.width(60.dp))
                Text("×${"%.2f".format(minOf(2.0, 1 + pts * Growth.BOOST_PER_POINT))} EXP", color = C.accentSoft, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GhostButton("+1", enabled = free > 0, color = C.stat(s)) { vm.spendPoint(s, level) }
            }
        }
    }
}

// ───────────────────────── Theme picker ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePanel(vm: AppViewModel) {
    val unlocked = Themes.unlocked(vm.prefs).map { it.id }.toSet()
    Panel(title = "Colour theme") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Themes.all.forEach { t ->
                val pal = C.palettes.getValue(t.id)
                val on = vm.prefs.theme == t.id
                val open = t.id in unlocked
                Row(
                    Modifier.glow(if (on) pal.accent else C.border, SmallShape, radius = if (on) 8.dp else 0.dp)
                        .clip(SmallShape).background(pal.accent.copy(alpha = if (open) 0.12f else 0.03f))
                        .then(if (open) Modifier.tap { vm.setTheme(t.id) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(SmallShape).background(if (open) pal.accent else C.muted))
                    Spacer(Modifier.width(8.dp))
                    Text(if (open) t.name else "🔒 ${t.name}", color = if (open) C.text else C.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("New themes unlock when you complete a job change.", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

// ───────────────────────── Job change ─────────────────────────

@Composable
fun JobPage(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.refreshJob() }
    var goal by remember { mutableStateOf("") }
    var confirmQuit by remember { mutableStateOf(false) }
    val job = vm.job
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        if (job != null) {
            item {
                val prog = vm.jobProgress
                val done = prog.count { it.done }
                Panel(title = "Class change in progress", color = C.shadow) {
                    Text(job.def.name.uppercase(), color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, style = glowStyle(C.shadow, 20f))
                    Text(job.def.desc, color = C.text, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Bar(if (prog.isEmpty()) 0f else done.toFloat() / prog.size, C.shadow, height = 7)
                    Text("$done / ${prog.size} steps", color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    prog.forEachIndexed { i, sp ->
                        val manual = sp.step.cond.isBlank()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(SmallShape)
                                .background(if (sp.done) C.good.copy(alpha = 0.1f) else C.bg.copy(alpha = 0.5f))
                                .then(if (manual) Modifier.tap { vm.toggleJobStep(i) } else Modifier)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (sp.done) "◆" else "◇", color = if (sp.done) C.good else C.shadow, fontSize = 16.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sp.step.text, color = if (sp.done) C.muted else C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (manual) (if (sp.done) "done · tap to undo" else "tap when done")
                                    else "auto · ${sp.current} / ${sp.target}", color = C.muted, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Reward: title \"${job.def.title}\", the ${Themes.all.firstOrNull { it.id == job.def.theme }?.name ?: ""} theme, " +
                        "+500 ${job.def.stat.short} EXP and 300 Gold.", color = C.warn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { confirmQuit = true }) { Text("Abandon this class change", color = C.bad) }
                }
            }
        } else {
            item {
                Text("Pick a class to work towards. It's a multi-week questline: steps tick off automatically when the app can measure them.",
                    color = C.muted, fontSize = 15.sp)
            }
            Jobs.all.forEach { d ->
                item {
                    val pal = C.palettes.getValue(d.theme)
                    Panel(title = d.name, color = pal.accent) {
                        Text(d.desc, color = C.text, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        d.steps.forEach { Text("◇ ${it.text}", color = C.muted, fontSize = 14.sp) }
                        Spacer(Modifier.height(8.dp))
                        Text("Unlocks: \"${d.title}\" + ${Themes.all.first { it.id == d.theme }.name} theme", color = pal.soft,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Begin class change", color = pal.accent) { vm.startJob(d) }
                    }
                }
            }
            item {
                Panel(title = "Design your own", color = C.shadow) {
                    Text("Describe a big goal and the System turns it into a class-change questline.", color = C.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Field(goal, { goal = it }, "Your goal", placeholder = "e.g. Run a half marathon, become a team lead, speak Spanish", voice = true)
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Design my class", enabled = goal.isNotBlank() && vm.prefs.hasKey && vm.busy == null, color = C.shadow) {
                        vm.customJob(goal.trim()); goal = ""
                    }
                    if (!vm.prefs.hasKey) Text("Needs your API key.", color = C.muted, fontSize = 12.sp)
                }
            }
        }
        item { ThemePanel(vm) }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (confirmQuit) AlertDialog(
        onDismissRequest = { confirmQuit = false }, containerColor = C.panel,
        title = { Text("Abandon class change?") },
        text = { Text("Your progress on this questline is lost. Stats and EXP stay.") },
        confirmButton = { TextButton(onClick = { vm.abandonJob(); confirmQuit = false }) { Text("Abandon", color = C.bad) } },
        dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep going") } },
    )
}

// ───────────────────────── Dungeons ─────────────────────────

@Composable
private fun countdown(end: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    return (end - now).coerceAtLeast(0)
}

fun fmtLeft(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
fun DungeonPage(vm: AppViewModel) {
    var confirmQuit by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }
    var customMin by remember { mutableIntStateOf(60) }
    var customStat by remember { mutableStateOf(Stat.DISCIPLINE) }
    val inf = rememberInfiniteTransition(label = "gate")
    val pulse by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        val a = vm.dungeon
        if (a != null) {
            item {
                val left = countdown(a.end)
                Panel(title = "Inside the gate", color = C.bad) {
                    Text(a.d.name.uppercase(), color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, style = glowStyle(C.bad, 18f))
                    Text(a.d.desc, color = C.text, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(if (left > 0) fmtLeft(left) else "TIME'S UP", color = Color.White, fontFamily = Fonts.display, fontSize = 36.sp,
                        fontWeight = FontWeight.Bold, style = glowStyle(if (left > 0) C.bad else C.good, 24f),
                        modifier = Modifier.fillMaxWidth().alpha(if (left > 0) 1f else 0.6f + 0.4f * pulse), textAlign = TextAlign.Center)
                    Bar(1f - left.toFloat() / (a.end - a.start), C.bad, height = 6)
                    Spacer(Modifier.height(12.dp))
                    if (left == 0L) PrimaryButton("Claim clear", Modifier.fillMaxWidth()) { vm.clearDungeon() }
                    else Text("Stay until the timer ends, then claim your clear. Leaving early spawns a penalty quest: ${a.d.penalty}",
                        color = C.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { confirmQuit = true }) { Text(if (left == 0L) "I didn't make it" else "Leave the gate (fail)", color = C.bad) }
                }
            }
        } else {
            item {
                Text("Start a timed challenge. Clear it for big EXP and Gold; quit early and the System gives you a penalty quest.",
                    color = C.muted, fontSize = 15.sp)
                Text("Cleared so far: ${vm.prefs.dungeonsCleared}", color = C.accentSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Dungeons.all.forEach { d ->
                item {
                    Panel(title = d.name, color = C.stat(d.stat)) {
                        Text(d.desc, color = C.text, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(if (d.minutes >= 120) "${d.minutes / 60} H" else "${d.minutes} MIN", C.accent)
                            Chip("+${d.xp} ${d.stat.short}", C.stat(d.stat))
                            Chip("+${d.gold} G", C.warn)
                        }
                        Text("Fail: ${d.penalty.removePrefix("Penalty: ")}", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Enter gate", color = C.stat(d.stat)) { vm.enterDungeon(d) }
                    }
                }
            }
            item {
                Panel(title = "Custom gate", color = C.shadow) {
                    Field(custom, { custom = it }, "Your challenge", singleLine = true, placeholder = "e.g. Clean the whole flat")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 120, 240).forEach { m -> Segment(if (m < 60) "${m}m" else "${m / 60}h", customMin == m) { customMin = m } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Stat.entries.forEach { s -> Segment(s.short, customStat == s) { customStat = s } }
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Enter gate", enabled = custom.isNotBlank(), color = C.shadow) {
                        vm.enterDungeon(Dungeons.custom(custom.trim(), customMin, customStat)); custom = ""
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (confirmQuit) AlertDialog(
        onDismissRequest = { confirmQuit = false }, containerColor = C.panel,
        title = { Text("Fail the dungeon?") },
        text = { Text("You'll get a penalty quest: ${vm.dungeon?.d?.penalty ?: ""}") },
        confirmButton = { TextButton(onClick = { vm.quitDungeon(); confirmQuit = false }) { Text("Leave", color = C.bad) } },
        dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Stay") } },
    )
}

// ───────────────────────── Flashcards ─────────────────────────

@Composable
fun CardsPage(vm: AppViewModel) {
    val due by vm.dueCards.collectAsState(initial = emptyList())
    val total by vm.cardCount.collectAsState(initial = 0)
    var flipped by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(setOf<Long>()) } // "again" cards go to the back of today's queue
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    val queue = due.sortedBy { it.id in skipped }
    val card = queue.firstOrNull()
    val today = vm.prefs.cardsReviewed(vm.day)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${due.size} due · $total cards · $today reviewed today", color = C.accentSoft, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }
        item {
            if (card == null) Panel(title = "All clear", color = C.good) {
                Text(if (total == 0) "No cards yet. Log what you learn in the Journal (category \"Learned\") and the System turns it into cards. " +
                    "For an AI quiz on a whole topic, use the Learn tab."
                    else "Nothing due. Due cards also show up in the Learn tab quiz.", color = C.text, fontSize = 15.sp)
            } else Panel(Modifier.tap { flipped = !flipped }, title = if (flipped) "Answer" else "Question", color = C.stat(Stat.INTELLECT)) {
                Text(card.front, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                Spacer(Modifier.height(12.dp))
                if (flipped) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
                    Spacer(Modifier.height(12.dp))
                    Text(card.back, color = C.accentSoft, fontSize = 17.sp, lineHeight = 23.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Triple("Again", 0, C.bad), Triple("Hard", 1, C.warn), Triple("Good", 2, C.good), Triple("Easy", 3, C.accent))
                            .forEach { (l, g, c) ->
                                GhostButton(l, Modifier.weight(1f), color = c) {
                                    if (g == 0) skipped = skipped + card.id
                                    vm.reviewCard(card, g); flipped = false
                                }
                            }
                    }
                } else Text("Tap to reveal", color = C.muted, fontSize = 14.sp)
            }
        }
        item {
            Panel(title = "Make cards") {
                GhostButton("Make cards from my learnings (AI)", enabled = vm.prefs.hasKey && vm.busy == null) { vm.makeCards() }
                Text("Runs automatically once a day when there's something new.", color = C.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                Field(front, { front = it }, "Question", singleLine = true)
                Spacer(Modifier.height(6.dp))
                Field(back, { back = it }, "Answer", minLines = 2)
                Spacer(Modifier.height(8.dp))
                GhostButton("Add card", enabled = front.isNotBlank() && back.isNotBlank()) {
                    vm.addCard(front.trim(), back.trim()); front = ""; back = ""
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ───────────────────────── Money ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyPage(vm: AppViewModel) {
    val month by vm.expensesMonth.collectAsState(initial = emptyList())
    @Suppress("UNUSED_VARIABLE") val bv = vm.budgetsVersion
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Growth.categories.first()) }
    var note by remember { mutableStateOf("") }
    var editBudget by remember { mutableStateOf<String?>(null) }
    val limits = vm.prefs.budgetMap()
    val total = month.sumOf { it.amountEur.toDouble() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item {
            Panel(title = "This month", color = C.warn, action = {
                TextButton(onClick = { editBudget = "__total" }) { Text("Budget", color = C.accent) }
            }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(eur(total), color = Color.White, fontFamily = Fonts.display, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        style = glowStyle(C.warn, 16f))
                    if (vm.prefs.monthBudget > 0) Text("  / €${vm.prefs.monthBudget}", color = C.muted, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (vm.prefs.monthBudget > 0) Bar((total / vm.prefs.monthBudget).toFloat(),
                    if (total > vm.prefs.monthBudget) C.bad else C.warn, height = 6)
                Spacer(Modifier.height(8.dp))
                Text(if (vm.noSpend > 0) "🔥 ${vm.noSpend}-day no-spend streak (+5 Gold per clean day)"
                    else "Days with no spending earn Discipline EXP and Gold.", color = if (vm.noSpend > 0) C.warn else C.muted,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Panel(title = "Log spending") {
                Field(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Amount €", singleLine = true,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Growth.categories.forEach { c -> Segment(c, cat == c) { cat = c } }
                }
                Spacer(Modifier.height(8.dp))
                Field(note, { note = it }, "Note (optional)", singleLine = true)
                Spacer(Modifier.height(8.dp))
                val a = amount.replace(',', '.').toFloatOrNull()
                PrimaryButton("Log", Modifier.fillMaxWidth(), enabled = a != null && a > 0) {
                    vm.addExpense(a!!, cat, note.trim()); amount = ""; note = ""
                }
                Text("Skipped a purchase instead? Log it in the Vault: it goes to your savings.", color = C.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Panel(title = "Categories") {
                Growth.categories.forEach { c ->
                    val spent = month.filter { it.category == c }.sumOf { it.amountEur.toDouble() }
                    val lim = limits[c]
                    if (spent > 0 || lim != null) {
                        Row(Modifier.fillMaxWidth().tap { editBudget = c }.padding(top = 6.dp)) {
                            Text(c, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(eur(spent) + (lim?.let { " / €$it" } ?: ""), color = if (lim != null && spent > lim) C.bad else C.muted,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(3.dp))
                        Bar(if (lim != null && lim > 0) (spent / lim).toFloat() else if (total > 0) (spent / total).toFloat() else 0f,
                            if (lim != null && spent > lim) C.bad else C.accent, height = 5)
                    }
                }
                Text("Tap a category to set a monthly limit.", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)) {
                    Growth.categories.filter { c -> month.none { it.category == c } && limits[c] == null }.forEach { c ->
                        Text("+ $c limit", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.tap { editBudget = c }.padding(4.dp))
                    }
                }
            }
        }
        if (month.isNotEmpty()) item {
            Panel(title = "Spending this month") {
                val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM")
                month.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(java.time.LocalDate.ofEpochDay(e.date).format(fmt), color = C.muted, fontSize = 13.sp, modifier = Modifier.width(46.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.category + if (e.note.isNotBlank()) " · ${e.note}" else "", color = C.text, fontSize = 15.sp)
                        }
                        Text(eur(e.amountEur.toDouble()), color = C.warn, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        IconButton(onClick = { vm.deleteExpense(e) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    editBudget?.let { c ->
        var v by remember(c) { mutableStateOf((if (c == "__total") vm.prefs.monthBudget else limits[c] ?: 0).takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editBudget = null }, containerColor = C.panel,
            title = { Text(if (c == "__total") "Monthly budget" else "$c limit / month") },
            text = { Field(v, { v = it.filter(Char::isDigit) }, "€ (empty = no limit)", singleLine = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(onClick = {
                val n = v.toIntOrNull() ?: 0
                if (c == "__total") vm.setMonthBudget(n) else vm.setBudget(c, n); editBudget = null
            }) { Text("Save", color = C.accent) } },
            dismissButton = { TextButton(onClick = { editBudget = null }) { Text("Cancel") } },
        )
    }
}

// ───────────────────────── Weekly review ─────────────────────────

@Composable
fun ReviewPage(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadReview() }
    val w = vm.weekNow
    val l = vm.weekLast
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        if (w == null || l == null) { item { Text("▍ Crunching your week…", color = C.accent, fontWeight = FontWeight.Bold) }; return@LazyColumn }
        item {
            Panel(title = "Quests · last 7 days") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${w.questsDone}/${w.questsTotal}", color = Color.White, fontFamily = Fonts.display, fontSize = 28.sp,
                        fontWeight = FontWeight.Bold, style = glowStyle(C.accent, 14f))
                    Spacer(Modifier.width(10.dp))
                    Delta(pct(w.questsDone, w.questsTotal), pct(l.questsDone, l.questsTotal), "%")
                }
                Spacer(Modifier.height(10.dp))
                // 7 bars: completion per day
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    val days = (6 downTo 0).map { java.time.LocalDate.now().minusDays(it.toLong()) }
                    w.questPctByDay.forEachIndexed { i, f ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(f.coerceAtLeast(0.03f) * 0.8f)
                                .glow(if (f >= 0.8f) C.good else C.accent, androidx.compose.ui.graphics.RectangleShape, radius = 4.dp, width = 0.dp)
                                .background(Brush.verticalGradient(listOf(if (f >= 0.8f) C.good else C.accent, C.glow.copy(alpha = 0.4f)))))
                            Text(days[i].dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                                color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Panel(title = "EXP gained") {
                val max = (w.xpByStat.values.maxOrNull() ?: 1).coerceAtLeast(1)
                Stat.entries.forEach { s ->
                    val v = w.xpByStat[s] ?: 0
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.short, color = C.stat(s), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(44.dp))
                        Bar(v.toFloat() / max, C.stat(s), Modifier.weight(1f))
                        Text("$v", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp).padding(start = 8.dp))
                        Delta(v, l.xpByStat[s] ?: 0, "")
                    }
                }
            }
        }
        item {
            Panel(title = "Body · food · money") {
                Metric("Food logged", "${w.foodDays}/7 days", w.foodDays, l.foodDays)
                Metric("Avg calories", "${w.avgKcal} kcal", w.avgKcal, l.avgKcal, neutral = true)
                Metric("Protein goal hit", "${w.proteinDays} days", w.proteinDays, l.proteinDays)
                Metric("Avg steps", "${w.avgSteps}", w.avgSteps, l.avgSteps)
                Metric("Avg sleep", "${w.avgSleepMin / 60}h${"%02d".format(w.avgSleepMin % 60)}", w.avgSleepMin, l.avgSleepMin)
                Metric("Training sets", "${w.sets}", w.sets, l.sets)
                Metric("Mood", if (w.moodAvg > 0) "%.1f / 5".format(w.moodAvg) else "–", (w.moodAvg * 10).toInt(), (l.moodAvg * 10).toInt())
                Metric("Spent", "€${w.spent}", w.spent, l.spent, lowerIsBetter = true)
                Metric("Saved", "€${w.saved}", w.saved, l.saved)
                w.weightChange?.let { Metric("Weight", "%+.1f kg".format(it), 0, 0, neutral = true) }
            }
        }
        item {
            Panel(title = "Top 3 changes for next week", color = C.shadow) {
                if (vm.reviewChanges.isEmpty()) {
                    Text("Your coach compares this week with last week and picks the 3 changes that would help most. One small AI call per week.",
                        color = C.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Get my top 3", Modifier.fillMaxWidth(), enabled = vm.prefs.hasKey && vm.busy == null) { vm.askReview() }
                    if (!vm.prefs.hasKey) Text("Needs your API key.", color = C.muted, fontSize = 12.sp)
                } else vm.reviewChanges.forEachIndexed { i, c ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text("${i + 1}", color = C.shadow, fontFamily = Fonts.display, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            style = glowStyle(C.shadow, 12f), modifier = Modifier.width(30.dp))
                        Column {
                            Text(c.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            if (c.why.isNotBlank()) Text(c.why, color = C.muted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun pct(a: Int, b: Int) = if (b == 0) 0 else a * 100 / b

@Composable
private fun Delta(now: Int, before: Int, unit: String, lowerIsBetter: Boolean = false, neutral: Boolean = false) {
    val d = now - before
    if (d == 0) { Text("±0$unit", color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold); return }
    val good = if (lowerIsBetter) d < 0 else d > 0
    Text((if (d > 0) "▲" else "▼") + " ${kotlin.math.abs(d)}$unit", fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = if (neutral) C.accentSoft else if (good) C.good else C.bad)
}

@Composable
private fun Metric(label: String, value: String, now: Int, before: Int, lowerIsBetter: Boolean = false, neutral: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
        if (now != 0 || before != 0) Delta(now, before, "", lowerIsBetter, neutral)
    }
}

// ───────────────────────── Home: today's extras ─────────────────────────

/** Small Home panel: active dungeon, due cards, free stat points, job progress. Hidden when there's nothing. */
@Composable
fun ExtrasPanel(vm: AppViewModel, open: (String) -> Unit) {
    val due by vm.dueCards.collectAsState(initial = emptyList())
    val level = currentLevel(vm)
    @Suppress("UNUSED_VARIABLE") val v = vm.pointsVersion
    // Only extras the user switched on show up here
    val free = if (vm.extra("points")) vm.unspentPoints(level) else 0
    val a = vm.dungeon?.takeIf { vm.extra("dungeons") }
    val job = vm.job?.takeIf { vm.extra("job") }
    if (a == null && due.isEmpty() && free <= 0 && job == null) return
    Panel(title = "System", color = C.shadow) {
        if (a != null) {
            val left = countdown(a.end)
            ExtraRow("⛩", a.d.name, if (left > 0) "${fmtLeft(left)} left" else "Time's up: claim your clear", C.bad) { open("dungeons") }
        }
        if (due.isNotEmpty()) ExtraRow("✦", "${due.size} quiz questions due", "Learn tab · a few minutes", C.stat(Stat.INTELLECT)) { open("quiz") }
        if (free > 0) ExtraRow("▲", "$free stat points to spend", "Choose how you grow", C.warn) { open("status") }
        if (job != null) {
            val p = vm.jobProgress
            ExtraRow("♛", job.def.name, "${p.count { it.done }}/${p.size} steps to class change", C.shadow) { open("job") }
        }
    }
}

@Composable
private fun ExtraRow(icon: String, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().tap(onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = color, fontSize = 20.sp, style = glowStyle(color, 12f), modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = C.muted, fontSize = 13.sp)
        }
        Text("›", color = color, fontSize = 22.sp)
    }
}
