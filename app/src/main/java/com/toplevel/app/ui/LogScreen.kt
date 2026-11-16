package com.toplevel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Stat
import com.toplevel.app.data.e1rm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LogScreen(vm: AppViewModel) {
    var section by rememberSaveable { mutableStateOf("food") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item { SystemHeader("LOG") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("food" to "Food", "body" to "Body", "journal" to "Journal", "skills" to "Skills").forEach { (k, l) ->
                    Segment(l, section == k) { section = k }
                }
            }
        }
        when (section) {
            "food" -> foodSection(vm)
            "body" -> bodySection(vm)
            "skills" -> item { SkillsPanels(vm) }
            else -> journalSection(vm)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    vm.macroSuggestion?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.macroSuggestion = null },
            containerColor = C.panel,
            title = { Text("Suggested targets", color = C.text) },
            text = {
                Column {
                    Text("${m.kcal} kcal · P${m.protein} C${m.carbs} F${m.fat}", color = C.accentSoft,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(m.why, color = C.text, lineHeight = 20.sp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.applyMacros(m) }) { Text("Apply", color = C.accent) } },
            dismissButton = { TextButton(onClick = { vm.macroSuggestion = null }) { Text("Keep current") } },
        )
    }
}

@Composable
fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label,
        color = if (selected) C.bg else C.accent,
        fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) C.accent else C.panel)
            .tap(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp))
}

// ───────────────────────── Food ─────────────────────────

private fun LazyListScope.foodSection(vm: AppViewModel) {
    item { FoodPanels(vm) }
}

@Composable
private fun FoodPanels(vm: AppViewModel) {
    val food by vm.food.collectAsState(initial = emptyList())
    var foodText by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var kcal by remember { mutableStateOf("") }
    var prot by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val tk = food.sumOf { it.kcal }
    val tp = food.sumOf { it.protein }

    Column(verticalArrangement = Gap) {
        Panel(title = "Nutrition · today") {
            Macro("Calories", tk, vm.prefs.kcalGoal, "kcal", C.warn)
            Macro("Protein", tp, vm.prefs.proteinGoal, "g", C.stat(Stat.VITALITY))
            Macro("Carbs", food.sumOf { it.carbs }, vm.prefs.carbsGoal, "g", C.accent)
            Macro("Fat", food.sumOf { it.fat }, vm.prefs.fatGoal, "g", C.stat(Stat.SPIRIT))
            val left = vm.prefs.proteinGoal - tp
            if (left > 0) Text("$left g protein to go", color = C.muted, fontSize = 12.sp)
            else SystemNotice("Protein goal reached. +20 VIT")
            if (vm.prefs.hasKey) {
                Spacer(Modifier.height(8.dp))
                GhostButton("Adjust my targets (AI)", enabled = vm.busy == null) { vm.suggestMacros() }
            }
        }
        Panel(title = "Add food") {
            Field(foodText, { foodText = it }, "What did you eat?", placeholder = "e.g. 200g chicken, rice, broccoli", voice = true)
            Spacer(Modifier.height(8.dp))
            if (manual) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(kcal, { kcal = it.filter(Char::isDigit) }, "kcal", Modifier.weight(1f), singleLine = true)
                    Field(prot, { prot = it.filter(Char::isDigit) }, "P", Modifier.weight(1f), singleLine = true)
                    Field(carb, { carb = it.filter(Char::isDigit) }, "C", Modifier.weight(1f), singleLine = true)
                    Field(fat, { fat = it.filter(Char::isDigit) }, "F", Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!manual && vm.prefs.hasKey) {
                    GhostButton("Estimate & log", enabled = foodText.isNotBlank() && vm.busy == null) {
                        vm.estimateAndAddFood(foodText.trim()); foodText = ""
                    }
                }
                if (manual) {
                    GhostButton("Log", enabled = foodText.isNotBlank()) {
                        vm.addFood(foodText.trim(), kcal.toIntOrNull() ?: 0, prot.toIntOrNull() ?: 0,
                            carb.toIntOrNull() ?: 0, fat.toIntOrNull() ?: 0)
                        foodText = ""; kcal = ""; prot = ""; carb = ""; fat = ""
                    }
                }
                GhostButton(if (manual) "Use AI" else "Enter manually", enabled = !manual || vm.prefs.hasKey) { manual = !manual }
            }
            if (food.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                food.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = C.text, fontSize = 14.sp)
                            Text("${f.kcal} kcal · P${f.protein} C${f.carbs} F${f.fat}", color = C.muted,
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        IconButton(onClick = { vm.deleteFood(f) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Macro(label: String, value: Int, goal: Int, unit: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = C.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("$value / $goal $unit", color = if (value > goal * 1.1) C.bad else C.muted,
            fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
    Spacer(Modifier.height(3.dp))
    Bar(if (goal > 0) value.toFloat() / goal else 0f, color)
    Spacer(Modifier.height(8.dp))
}

fun fmtKg(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

// ───────────────────────── Body ─────────────────────────

private fun LazyListScope.bodySection(vm: AppViewModel) {
    item { BodyPanels(vm) }
}

@Composable
private fun BodyPanels(vm: AppViewModel) {
    val body by vm.body.collectAsState(initial = emptyList())
    val health by vm.healthToday.collectAsState(initial = null)
    var w by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    val fmt = DateTimeFormatter.ofPattern("dd.MM")

    Column(verticalArrangement = Gap) {
        Panel(title = "Log weight") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(w, { w = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Weight kg", Modifier.weight(1f), singleLine = true)
                Field(waist, { waist = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Waist cm (opt.)", Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("Save", enabled = w.isNotBlank()) {
                w.replace(',', '.').toFloatOrNull()?.let { vm.logBody(it, waist.replace(',', '.').toFloatOrNull()) }
                w = ""; waist = ""
            }
            if (vm.healthConnected) Text("Weight from your scale/Samsung Health is imported automatically.",
                color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Panel(title = "Weight trend") {
            val recent = body.takeLast(60)
            LineChart(recent.map { it.weightKg }, C.stat(Stat.VITALITY), "kg",
                labels = if (recent.size >= 2) LocalDate.ofEpochDay(recent.first().date).format(fmt) to
                    LocalDate.ofEpochDay(recent.last().date).format(fmt) else null)
            if (recent.size >= 2) {
                val weekAgo = recent.lastOrNull { it.date <= vm.day - 7 }
                if (weekAgo != null) {
                    val d = recent.last().weightKg - weekAgo.weightKg
                    Text("7-day change: ${if (d >= 0) "+" else ""}${"%.1f".format(d)} kg",
                        color = C.accentSoft, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (vm.prefs.hasKey) {
                Spacer(Modifier.height(8.dp))
                GhostButton("Adjust my macros to this trend", enabled = vm.busy == null) { vm.suggestMacros() }
            }
        }
        health?.let { h ->
            Panel(title = "Today from Health Connect") {
                Text("${h.steps} steps · sleep ${h.sleepMin / 60}h ${h.sleepMin % 60}m · ${h.workoutMin} min workouts",
                    color = C.text, fontSize = 13.sp)
                if (h.workouts.isNotBlank()) Text(h.workouts, color = C.muted, fontSize = 12.sp)
            }
        }
        if (body.isNotEmpty()) {
            Panel(title = "History") {
                body.takeLast(14).reversed().forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalDate.ofEpochDay(b.date).format(fmt), color = C.muted, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(60.dp))
                        Text("${b.weightKg} kg${b.waistCm?.let { " · waist $it cm" } ?: ""}", color = C.text, modifier = Modifier.weight(1f))
                        if (b.source == "health") Chip("SYNC", C.muted)
                        IconButton(onClick = { vm.deleteBody(b) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Journal ─────────────────────────

private fun LazyListScope.journalSection(vm: AppViewModel) {
    item { JournalPanels(vm) }
}

@Composable
private fun JournalPanels(vm: AppViewModel) {
    val logs by vm.recentLogs.collectAsState(initial = emptyList())
    var logCat by remember { mutableStateOf("work") }
    var logText by remember { mutableStateOf("") }
    Column(verticalArrangement = Gap) {
        Panel(title = "Journal") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("work" to "Work", "learned" to "Learned", "training" to "Training", "note" to "Note").forEach { (k, l) ->
                    Segment(l, logCat == k) { logCat = k }
                }
            }
            Spacer(Modifier.height(8.dp))
            Field(logText, { logText = it }, when (logCat) {
                "work" -> "What did you get done at work?"
                "learned" -> "What did you learn?"
                "training" -> "What training did you do?"
                else -> "Note"
            }, minLines = 2, voice = true)
            Spacer(Modifier.height(8.dp))
            GhostButton("Save entry", enabled = logText.isNotBlank()) {
                vm.addLog(logCat, logText.trim()); logText = ""
            }
        }
        if (logs.isNotEmpty()) {
            Panel(title = "Recent entries") {
                val fmt = DateTimeFormatter.ofPattern("EEE dd.MM HH:mm")
                logs.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Row {
                                Chip(l.category.uppercase(), catColor(l.category))
                                Spacer(Modifier.width(6.dp))
                                Text(Instant.ofEpochMilli(l.ts).atZone(ZoneId.systemDefault()).format(fmt),
                                    color = C.muted, fontSize = 11.sp)
                            }
                            Text(l.text, color = C.text, fontSize = 14.sp)
                        }
                        IconButton(onClick = { vm.deleteLog(l) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                        }
                    }
                }
            }
        }
    }
}

private fun catColor(c: String): Color = when (c) {
    "work" -> C.stat(Stat.DISCIPLINE)
    "learned" -> C.stat(Stat.INTELLECT)
    "training" -> C.stat(Stat.STRENGTH)
    else -> C.stat(Stat.SPIRIT)
}
