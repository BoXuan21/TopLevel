package com.toplevel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Cali
import com.toplevel.app.data.SessionItem
import com.toplevel.app.data.SkillProgress
import com.toplevel.app.data.Stat
import com.toplevel.app.data.WorkoutSet
import com.toplevel.app.data.e1rm

@Composable
fun TrainScreen(vm: AppViewModel) {
    val progress by vm.ladderProgress.collectAsState(initial = emptyList())
    val session by vm.session.collectAsState(initial = emptyList())
    val allSets by vm.allSets.collectAsState(initial = emptyList())
    val sets14 by vm.sets14.collectAsState(initial = emptyList())
    var logTarget by remember { mutableStateOf<String?>(null) }
    var editLadders by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val version = vm.ladderVersion // redraw when stage names change
    val tracked = vm.trackedLadders.split(",").mapNotNull { Cali.byId(it.trim()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item { SystemHeader("TRAIN") }

        // Skill ladders (tracking)
        tracked.forEach { l ->
            item {
                LadderPanel(vm, l, progress.firstOrNull { it.ladder == l.id } ?: SkillProgress(l.id), allSets) { logTarget = it }
            }
        }
        item {
            TextButton(onClick = { editLadders = true }) { Text("Choose skills to track…", color = C.accent) }
        }

        item { ManualLog(vm) }
        item { TodaySets(vm) }
        if (tracked.isNotEmpty()) item { VolumePanel(tracked, sets14, vm.day) }
        item { ProgressPanel(vm) }

        // Optional: coach-built sessions
        item {
            Panel(
                title = "Coach-built session",
                action = {
                    Switch(
                        checked = vm.coachSessions,
                        onCheckedChange = { vm.updateCoachSessions(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.accent),
                    )
                },
            ) {
                if (!vm.coachSessions) {
                    Text("Off: pure tracking. Turn on if you want Claude to program a session from your stages, volume and recovery.",
                        color = C.muted, fontSize = 12.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (session.isEmpty()) "No session yet today." else "${session.count { it.done }}/${session.size} done",
                            color = C.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.buildSession() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Refresh, "Build session", tint = C.accent)
                        }
                    }
                    var lastBlock = ""
                    session.forEach { item ->
                        if (item.block.isNotBlank() && item.block != lastBlock) {
                            lastBlock = item.block
                            Text(item.block.uppercase(), color = C.accent, fontSize = 10.sp, fontFamily = Fonts.tech,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        }
                        SessionRow(item, onToggle = { vm.toggleSessionItem(item) }, onLog = { logTarget = item.exercise })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    logTarget?.let { ex -> QuickLogDialog(vm, ex) { logTarget = null } }

    vm.tip?.let { (ex, text) ->
        AlertDialog(
            onDismissRequest = { vm.tip = null },
            containerColor = C.panel,
            title = { Text("Next session · $ex", color = C.text) },
            text = { Text(text, color = C.text, lineHeight = 20.sp) },
            confirmButton = { TextButton(onClick = { vm.tip = null }) { Text("Got it", color = C.accent) } },
        )
    }

    if (editLadders) {
        var sel by remember { mutableStateOf(tracked.map { it.id }.toSet()) }
        AlertDialog(
            onDismissRequest = { editLadders = false },
            containerColor = C.panel,
            title = { Text("Skills to track", color = C.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Cali.ladders.forEach { l ->
                        Segment(l.name, l.id in sel) { sel = if (l.id in sel) sel - l.id else sel + l.id }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setLadders(Cali.ladders.map { it.id }.filter { it in sel }); editLadders = false }) {
                    Text("Save", color = C.accent)
                }
            },
        )
    }
}

@Composable
private fun SessionRow(item: SessionItem, onToggle: () -> Unit, onLog: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(SmallShape)
            .background(C.bg.copy(alpha = 0.5f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(SmallShape).background(if (item.done) C.good else C.panel2).tap(onToggle),
            contentAlignment = Alignment.Center,
        ) { if (item.done) Text("✓", color = C.bg, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.exercise, color = if (item.done) C.muted else C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (item.done) TextDecoration.LineThrough else null)
            Text(item.target + if (item.rest.isNotBlank()) " · rest ${item.rest}" else "",
                color = C.accentSoft, fontSize = 12.sp, fontFamily = Fonts.tech)
            if (item.note.isNotBlank()) Text(item.note, color = C.muted, fontSize = 12.sp)
        }
        TextButton(onClick = onLog) { Text("Log", color = C.accent, fontSize = 12.sp) }
    }
}

@Composable
private fun LadderPanel(vm: AppViewModel, l: Cali.Ladder, p: SkillProgress, allSets: List<WorkoutSet>, onLog: (String) -> Unit) {
    var confirmAdvance by remember { mutableStateOf(false) }
    var setTo by remember { mutableStateOf<Int?>(null) }
    var editStages by remember { mutableStateOf(false) }
    val stage = p.stage.coerceIn(0, l.stages.lastIndex)
    val maxed = stage >= l.stages.lastIndex
    val best = if (l.hold) p.bestHoldSec else p.bestReps
    val need = if (l.hold) Cali.QUALIFY_HOLD else Cali.QUALIFY_REPS
    val qualified = best >= need
    // all-time PR at the current stage
    val current = Cali.exercise(l, stage)
    val pr = allSets.filter { it.exercise.equals(current, true) }
        .maxOfOrNull { if (l.hold) it.holdSec else it.reps } ?: 0

    Panel(
        title = l.name,
        action = {
            IconButton(onClick = { editStages = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, "Edit stages", tint = C.muted)
            }
        },
    ) {
        // Stage track — tap a dot to set where you are
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            l.stages.forEachIndexed { i, _ ->
                Box(
                    Modifier.size(if (i == stage) 18.dp else 13.dp).clip(CircleShape)
                        .background(when {
                            i < stage -> C.good
                            i == stage -> C.accent
                            else -> C.panel2
                        })
                        .tap { if (i != stage) setTo = i }
                )
                if (i < l.stages.lastIndex) Box(Modifier.width(14.dp).height(2.dp).background(if (i < stage) C.good else C.border))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("STAGE ${stage + 1}/${l.stages.size}", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
        Text(l.stages[stage], color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        if (!maxed) Text("Next: ${l.stages[stage + 1]}", color = C.accentSoft, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row {
            Text(if (l.hold) "PR ${pr}s" else "PR $pr reps", color = C.good, fontSize = 13.sp,
                fontFamily = Fonts.tech, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (l.hold) "next-stage standard ${need}s" else "standard $need reps", color = C.muted, fontSize = 11.sp,
                fontFamily = Fonts.tech)
        }
        Spacer(Modifier.height(4.dp))
        Bar(maxOf(best, pr).toFloat() / need, if (qualified || pr >= need) C.good else C.stat(Stat.STRENGTH))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(if (l.hold) "Log hold" else "Log reps") { onLog(current) }
            if (!maxed) GhostButton("Advance ▲") { confirmAdvance = true }
        }
        Text("Tap a dot to set your current level. Advance ▲ when you hit a new stage = EXP + Gold.",
            color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }

    if (confirmAdvance && !maxed) AlertDialog(
        onDismissRequest = { confirmAdvance = false },
        containerColor = C.panel,
        title = { Text("New stage: ${l.stages[stage + 1]}?", color = C.text) },
        text = { Text("Clean hold, real form? This is a level-up: +EXP and Gold.", color = C.text) },
        confirmButton = {
            TextButton(onClick = { vm.setStage(l.id, stage + 1, reward = true); confirmAdvance = false }) { Text("Level up", color = C.accent) }
        },
        dismissButton = { TextButton(onClick = { confirmAdvance = false }) { Text("Not yet") } },
    )

    setTo?.let { target ->
        AlertDialog(
            onDismissRequest = { setTo = null },
            containerColor = C.panel,
            title = { Text("Set current level", color = C.text) },
            text = { Text("Set ${l.name} to \"${l.stages[target]}\"? (No rewards — this just records where you are.)", color = C.text) },
            confirmButton = {
                TextButton(onClick = { vm.setStage(l.id, target, reward = false); setTo = null }) { Text("Set", color = C.accent) }
            },
            dismissButton = { TextButton(onClick = { setTo = null }) { Text("Cancel") } },
        )
    }

    if (editStages) {
        var text by remember { mutableStateOf(l.stages.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { editStages = false },
            containerColor = C.panel,
            title = { Text("${l.name} stages", color = C.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("One stage per line, easiest first. Use your own progressions (e.g. lean angles, full PPU reps, weighted).",
                        color = C.muted, fontSize = 12.sp)
                    Field(text, { text = it }, "Stages", minLines = 6)
                    TextButton(onClick = { text = Cali.defaultStages(l.id).joinToString("\n") }) {
                        Text("Reset to defaults", color = C.muted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setLadderStages(l.id, text.lines()); editStages = false }) { Text("Save", color = C.accent) }
            },
            dismissButton = { TextButton(onClick = { editStages = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QuickLogDialog(vm: AppViewModel, exercise: String, onClose: () -> Unit) {
    val ladder = Cali.match(exercise)?.first
    var isHold by remember { mutableStateOf(ladder?.hold ?: exercise.contains("hold", true)) }
    var value by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text(exercise, color = C.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Segment("Hold (s)", isHold) { isHold = true }
                    Segment("Reps", !isHold) { isHold = false }
                }
                Field(value, { value = it.filter(Char::isDigit) }, if (isHold) "Seconds held" else "Reps", singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(sets, { sets = it.filter(Char::isDigit) }, "Sets", Modifier.weight(1f), singleLine = true)
                    Field(weight, { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "+kg",
                        Modifier.weight(1f), singleLine = true)
                }
                Field(note, { note = it }, "Note (optional)", singleLine = true, placeholder = "parallettes, RPE 9, band…")
            }
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = {
                val v = value.toIntOrNull() ?: 0
                val w = weight.replace(',', '.').toFloatOrNull() ?: 0f
                repeat((sets.toIntOrNull() ?: 1).coerceIn(1, 10)) {
                    if (isHold) vm.addCaliSet(exercise, v, 0, w, note) else vm.addCaliSet(exercise, 0, v, w, note)
                }
                onClose()
            }) { Text("Log", color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun ManualLog(vm: AppViewModel) {
    val recent by vm.recentExercises.collectAsState(initial = emptyList())
    var ex by rememberSaveable { mutableStateOf("") }
    var isHold by rememberSaveable { mutableStateOf(false) }
    var w by rememberSaveable { mutableStateOf("") }
    var v by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    Panel(title = "Log any exercise") {
        if (recent.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recent.forEach { r -> Segment(r, ex.equals(r, true)) { ex = r } }
            }
            Spacer(Modifier.height(8.dp))
        }
        Field(ex, { ex = it }, "Exercise", singleLine = true, placeholder = "e.g. Full planche push-up")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Segment("Reps", !isHold) { isHold = false }
            Segment("Hold (s)", isHold) { isHold = true }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(v, { v = it.filter(Char::isDigit) }, if (isHold) "seconds" else "reps", Modifier.weight(1f), singleLine = true)
            Field(w, { w = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "+kg (opt.)", Modifier.weight(1f), singleLine = true)
        }
        Spacer(Modifier.height(6.dp))
        Field(note, { note = it }, "Note (optional)", singleLine = true, placeholder = "parallettes, RPE 9…")
        Spacer(Modifier.height(8.dp))
        GhostButton("Add set", enabled = ex.isNotBlank() && v.isNotBlank()) {
            val n = v.toIntOrNull() ?: 0
            val kg = w.replace(',', '.').toFloatOrNull() ?: 0f
            if (isHold) vm.addCaliSet(ex.trim(), n, 0, kg, note) else vm.addCaliSet(ex.trim(), 0, n, kg, note)
        }
        Text("Faster: tell the Coach \"Full planche 3x6s, 5x3 FL pull-ups +5kg\".",
            color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TodaySets(vm: AppViewModel) {
    val sets by vm.setsToday.collectAsState(initial = emptyList())
    val totalHold = sets.sumOf { it.holdSec }
    Panel(title = "Today · ${sets.size} sets" + if (totalHold > 0) " · ${totalHold}s under tension" else "") {
        if (sets.isEmpty()) Text("No sets yet today.", color = C.muted)
        sets.groupBy { it.exercise.lowercase() }.values.forEach { list ->
            val name = list.first().exercise
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = C.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (vm.prefs.hasKey) TextButton(onClick = { vm.askTip(name) }) { Text("Ask coach", color = C.accent, fontSize = 12.sp) }
            }
            list.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", color = C.muted, fontFamily = Fonts.tech, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (s.holdSec > 0) "${s.holdSec}s" else "${s.reps} reps") +
                                (if (s.weightKg > 0f) " · +${fmtKg(s.weightKg)} kg" else ""),
                            color = C.accentSoft, fontFamily = Fonts.tech,
                        )
                        if (s.note.isNotBlank()) Text(s.note, color = C.muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { vm.deleteSet(s) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Weekly volume per skill: hold seconds (or reps), this 7 days vs. previous 7. */
@Composable
private fun VolumePanel(tracked: List<Cali.Ladder>, sets: List<WorkoutSet>, today: Long) {
    Panel(title = "Volume · 7 days vs previous 7") {
        tracked.forEach { l ->
            val mine = sets.filter { s -> Cali.match(s.exercise)?.first?.id == l.id || s.exercise.startsWith(l.name, true) }
            fun vol(list: List<WorkoutSet>) = if (l.hold) list.sumOf { it.holdSec } else list.sumOf { it.reps }
            val now = vol(mine.filter { it.date > today - 7 })
            val before = vol(mine.filter { it.date <= today - 7 })
            val unit = if (l.hold) "s" else " reps"
            val delta = now - before
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.name, color = C.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("$now$unit", color = C.text, fontFamily = Fonts.tech, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text((if (delta >= 0) "▲" else "▼") + "${kotlin.math.abs(delta)}", color = if (delta >= 0) C.good else C.warn,
                    fontFamily = Fonts.tech, fontSize = 12.sp)
            }
            Bar(if (maxOf(now, before) > 0) now.toFloat() / maxOf(now, before) else 0f, C.stat(Stat.STRENGTH), height = 4)
        }
    }
}

@Composable
private fun ProgressPanel(vm: AppViewModel) {
    val recent by vm.recentExercises.collectAsState(initial = emptyList())
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val sel = selected ?: recent.firstOrNull() ?: return
    val flow = remember(sel) { vm.exerciseHistory(sel) }
    val hist by flow.collectAsState(initial = emptyList())
    val holds = hist.any { it.holdSec > 0 }
    Panel(title = "Progress · $sel") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            recent.forEach { r -> Segment(r, r.equals(sel, true)) { selected = r } }
        }
        Spacer(Modifier.height(10.dp))
        val byDay = hist.groupBy { it.date }.toSortedMap().values
        if (holds) {
            Text("Best hold per session", color = C.muted, fontSize = 11.sp)
            LineChart(byDay.map { d -> d.maxOf { it.holdSec }.toFloat() }, C.stat(Stat.STRENGTH), "s")
            Text("Total time under tension per session", color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            LineChart(byDay.map { d -> d.sumOf { it.holdSec }.toFloat() }, C.accent, "s")
            hist.maxByOrNull { it.holdSec }?.let {
                Text("PR: ${it.holdSec}s${if (it.note.isNotBlank()) " (${it.note})" else ""}", color = C.good,
                    fontSize = 12.sp, fontFamily = Fonts.tech)
            }
        } else if (hist.any { it.weightKg > 0f }) {
            Text("Estimated 1-rep max per session (added weight)", color = C.muted, fontSize = 11.sp)
            LineChart(byDay.map { d -> d.maxOf { it.e1rm() } }, C.stat(Stat.STRENGTH), "kg")
        } else {
            Text("Best reps per session", color = C.muted, fontSize = 11.sp)
            LineChart(byDay.map { d -> d.maxOf { it.reps }.toFloat() }, C.stat(Stat.STRENGTH), "reps")
            hist.maxByOrNull { it.reps }?.let {
                Text("PR: ${it.reps} reps", color = C.good, fontSize = 12.sp, fontFamily = Fonts.tech)
            }
        }
    }
}
