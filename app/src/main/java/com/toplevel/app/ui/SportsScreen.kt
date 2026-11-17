package com.toplevel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Sports
import com.toplevel.app.data.Stat

/** "What sport do you do?" — any sport, plus your own words. Used in Train and in setup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportsPanel(vm: AppViewModel, first: Boolean, done: () -> Unit) {
    var sel by rememberSaveable { mutableStateOf(vm.sports) }
    var notes by rememberSaveable { mutableStateOf(vm.prefs.sportNotes) }
    var own by rememberSaveable { mutableStateOf("") }
    val content: @Composable () -> Unit = {
        if (first) {
            Text("Pick what you do. Train, your quests and your coach adapt to it.", color = C.muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
        }
        SportPicker(sel) { sel = it }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(own, { own = it }, "Something else?", Modifier.weight(1f), singleLine = true, placeholder = "e.g. Rowing, Ski touring")
            Spacer(Modifier.width(8.dp))
            GhostButton("Add", enabled = own.isNotBlank()) { sel = (sel + own.trim()).distinct(); own = "" }
        }
        Spacer(Modifier.height(8.dp))
        Field(notes, { notes = it }, "How often, what exactly, your goal (optional)", minLines = 2, voice = true,
            placeholder = "e.g. Gym 3x a week (push/pull/legs), running Sundays, 10 km under 50 min")
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Save", Modifier.fillMaxWidth(), enabled = sel.isNotEmpty()) { vm.setSports(sel, notes); done() }
    }
    if (first) Panel(title = "What sport do you do?", color = C.stat(Stat.STRENGTH)) { content() } else Column { content() }
}

/** Tap to pick sports (also lists the user's own ones). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportPicker(sel: List<String>, onChange: (List<String>) -> Unit) {
    val options = Sports.all.map { it.name } + sel.filter { s -> Sports.all.none { it.name.equals(s, true) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { name ->
            val on = sel.any { it.equals(name, true) }
            Segment("${Sports.of(name).icon} $name", on) {
                onChange(if (on) sel.filterNot { it.equals(name, true) } else sel + name)
            }
        }
    }
}

/** One quick form for any sport: which, how long, (distance), note. */
@Composable
fun SessionLog(vm: AppViewModel, sports: List<String>) {
    var picked by rememberSaveable { mutableStateOf(sports.first()) }
    val sport = picked.takeIf { it in sports } ?: sports.first()
    var minutes by rememberSaveable { mutableIntStateOf(45) }
    var custom by rememberSaveable { mutableStateOf("") }
    var km by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val s = Sports.of(sport)
    Panel(title = "Log a session", color = C.stat(Stat.STRENGTH)) {
        if (sports.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sports.forEach { n -> Segment("${Sports.of(n).icon} $n", n == sport) { picked = n } }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                Segment("$m min", custom.isBlank() && minutes == m) { minutes = m; custom = "" }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(custom, { custom = it.filter(Char::isDigit).take(3) }, "Other min", Modifier.weight(1f), singleLine = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (s.distance) Field(km, { km = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, "km (opt.)",
                Modifier.weight(1f), singleLine = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Spacer(Modifier.height(6.dp))
        Field(note, { note = it }, "How was it? (optional)", singleLine = true, voice = true, placeholder = "e.g. easy pace, new PR, knee ok")
        Spacer(Modifier.height(10.dp))
        val min = custom.toIntOrNull() ?: minutes
        PrimaryButton("Log $min min ${sport.lowercase()}", Modifier.fillMaxWidth(), enabled = min > 0) {
            vm.logSession(sport, min, km.replace(',', '.').toFloatOrNull(), note)
            custom = ""; km = ""; note = ""
        }
        if (vm.healthConnected) Text("Workouts from your watch / Health Connect count automatically.", color = C.muted,
            fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Mon–Sun plan: today highlighted, tap a day to edit, ✓ when a session was logged that day. */
@Composable
fun WeekPlanPanel(vm: AppViewModel) {
    val logs by vm.recentLogs.collectAsState(initial = emptyList())
    var pasteOpen by rememberSaveable { mutableStateOf(false) }
    var editDay by rememberSaveable { mutableStateOf(-1) }
    val plan = vm.weekPlan
    val today = com.toplevel.app.data.WeekPlan.todayIndex()
    val monday = vm.day - today
    val color = C.stat(Stat.STRENGTH)

    Panel(title = "Your week", color = color) {
        if (plan == null) {
            Text("Get a plan for the week: the AI builds one from your sports, or paste the plan you already have " +
                "(from a coach, an app, a note). Your daily quests follow it.", color = C.muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Build my week", Modifier.weight(1f), enabled = vm.prefs.hasKey && vm.busy == null) { vm.makeWeekPlan() }
                GhostButton("Paste plan", enabled = vm.busy == null) { pasteOpen = true }
            }
            androidx.compose.material3.TextButton(onClick = { editDay = today }) { Text("Or write it yourself", color = C.accent) }
        } else {
            com.toplevel.app.data.WeekPlan.names.forEachIndexed { i, name ->
                val s = plan.day(i)
                val isToday = i == today
                val done = logs.any { it.date == monday + i && Sports.sportOf(it) != null }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .then(if (isToday) Modifier.background(color.copy(alpha = 0.12f), SmallShape) else Modifier)
                        .tap { editDay = i }.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name.uppercase(), color = if (isToday) color else C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(46.dp))
                    Column(Modifier.weight(1f)) {
                        if (s.isEmpty()) Text("Rest", color = C.muted, fontSize = 15.sp)
                        s.forEach { p ->
                            Text("${Sports.of(p.sport).icon} ${p.label()}", color = if (isToday) C.text else C.text.copy(alpha = 0.85f),
                                fontSize = 15.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                            if (isToday && p.detail.isNotBlank()) Text(p.detail, color = C.muted, fontSize = 13.sp)
                        }
                    }
                    if (done) Text("✓", color = C.good, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
            // Today: one tap to log what was planned
            val todays = plan.day(today)
            val doneToday = logs.any { it.date == vm.day && Sports.sportOf(it) != null }
            if (todays.isNotEmpty() && !doneToday) {
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Done: " + todays.joinToString(" + ") { it.title.ifBlank { it.sport } }, Modifier.fillMaxWidth()) {
                    todays.forEach { vm.logSession(it.sport, it.minutes.coerceAtLeast(1), null, it.title) }
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tap a day to change it.", color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { pasteOpen = true }, enabled = vm.busy == null) { Text("Paste", color = C.accent) }
                androidx.compose.material3.TextButton(onClick = { vm.makeWeekPlan() }, enabled = vm.prefs.hasKey && vm.busy == null) {
                    Text("New (AI)", color = C.accent)
                }
            }
        }
    }

    if (pasteOpen) {
        var text by rememberSaveable { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pasteOpen = false }, containerColor = C.panel,
            title = { Text("Paste your plan", color = C.text) },
            text = {
                Column {
                    Text("Any format: a table, a message from your coach, your notes. " +
                        (if (vm.prefs.hasKey) "The AI turns it into your week." else "Without an API key: one line per day, Monday first."),
                        color = C.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Field(text, { text = it }, "Your plan", minLines = 6,
                        placeholder = "Mon: Gym push 60 min\nTue: Easy run 6 km\nWed: rest\n…")
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = text.isNotBlank(), onClick = {
                    if (vm.prefs.hasKey) vm.makeWeekPlan(text.trim())
                    else text.lines().filter { it.isNotBlank() }.take(7).forEachIndexed { i, l ->
                        // drop a leading weekday ("Mon:", "Di -", "Monday") and keep the rest
                        vm.setPlanDay(i, l.trim().replace(Regex("^(monday|tuesday|wednesday|thursday|friday|saturday|sunday|montag|dienstag|mittwoch|donnerstag|freitag|" +
                            "samstag|sonntag|mon|tue|wed|thu|fri|sat|sun|mo|di|mi|do|fr|sa|so)\\b\\.?\\s*[:\\-]?\\s*",
                            RegexOption.IGNORE_CASE), ""))
                    }
                    pasteOpen = false
                }) { Text("Use this plan", color = C.accent) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pasteOpen = false }) { Text("Cancel") } },
        )
    }

    if (editDay >= 0) {
        val i = editDay
        var text by rememberSaveable(i) { mutableStateOf(com.toplevel.app.data.WeekPlan.toLines(plan?.day(i).orEmpty())) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editDay = -1 }, containerColor = C.panel,
            title = { Text(listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i], color = C.text) },
            text = {
                Column {
                    Text("One session per line, e.g. \"Running: Easy run 45 min\". Empty = rest day.", color = C.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Field(text, { text = it }, "Sessions", minLines = 3)
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.setPlanDay(i, text); editDay = -1 }) {
                Text("Save", color = C.accent) } },
            dismissButton = {
                Row {
                    if (plan != null) androidx.compose.material3.TextButton(onClick = { vm.clearWeekPlan(); editDay = -1 }) {
                        Text("Delete plan", color = C.bad) }
                    androidx.compose.material3.TextButton(onClick = { editDay = -1 }) { Text("Cancel") }
                }
            },
        )
    }
}

/** This week's sessions, any sport. */
@Composable
fun WeekSessions(vm: AppViewModel) {
    val logs by vm.recentLogs.collectAsState(initial = emptyList())
    val week = logs.filter { it.date > vm.day - 7 && Sports.sportOf(it) != null }
    if (week.isEmpty()) return
    val total = week.sumOf { Regex("· (\\d+) min").find(it.text)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
    Panel(title = "This week · ${week.size} sessions · ${total} min") {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE")
        week.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(java.time.LocalDate.ofEpochDay(l.date).format(fmt), color = C.muted, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                Text(Sports.of(Sports.sportOf(l)!!).icon + "  " + l.text, color = C.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.deleteLog(l) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                }
            }
        }
    }
}
