package com.toplevel.app.ui

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
