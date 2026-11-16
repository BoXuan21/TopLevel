package com.toplevel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.PlanType

@Composable
fun SetupScreen(
    vm: AppViewModel,
    requestCalendar: () -> Unit,
    connectHealth: () -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
) {
    val plans by vm.plans.collectAsState(initial = emptyList())
    val memories by vm.memories.collectAsState(initial = emptyList())
    val p = vm.prefs

    var name by remember { mutableStateOf(p.name) }
    var key by remember { mutableStateOf(p.apiKey) }
    var model by remember { mutableStateOf(p.model) }
    var kcal by remember { mutableStateOf(p.kcalGoal.toString()) }
    var prot by remember { mutableStateOf(p.proteinGoal.toString()) }
    var carb by remember { mutableStateOf(p.carbsGoal.toString()) }
    var fat by remember { mutableStateOf(p.fatGoal.toString()) }
    var morning by remember { mutableStateOf(p.morningHour.toString()) }
    var evening by remember { mutableStateOf(p.eveningHour.toString()) }
    var useCal by remember { mutableStateOf(p.useCalendar) }
    var calWrite by remember { mutableStateOf(p.calendarWrite) }
    var importType by remember { mutableStateOf(PlanType.TRAINING) }
    val planPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importPlan(uri, importType) }
    var memText by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }

    if (confirmRestore) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmRestore = false },
        containerColor = C.panel,
        title = { Text("Restore backup?") },
        text = { Text("This replaces ALL current data in the app with the backup file.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmRestore = false; restore() }) { Text("Restore", color = C.bad) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
    )

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {

        item {
            Panel(title = "Player") {
                Field(name, { name = it }, "Your name", singleLine = true)
                Spacer(Modifier.height(8.dp))
                GhostButton("Save") { p.name = name; vm.message = "Saved" }
            }
        }

        item {
            Panel(title = "Claude (AI coach)") {
                Field(key, { key = it }, "Anthropic API key", singleLine = true, password = true,
                    placeholder = "sk-ant-…")
                Spacer(Modifier.height(6.dp))
                Field(model, { model = it }, "Model", singleLine = true)
                Spacer(Modifier.height(6.dp))
                Text("Get a key at console.anthropic.com. It's stored only on this phone.",
                    color = C.muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Save") { p.apiKey = key; p.model = model; vm.message = "Saved" }
                    GhostButton("Test", enabled = vm.busy == null) { p.apiKey = key; p.model = model; vm.testKey() }
                }
            }
        }

        item {
            Panel(title = "Nutrition goals (daily)") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(kcal, { kcal = it.filter(Char::isDigit) }, "kcal", Modifier.weight(1f), singleLine = true)
                    Field(prot, { prot = it.filter(Char::isDigit) }, "Protein g", Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(carb, { carb = it.filter(Char::isDigit) }, "Carbs g", Modifier.weight(1f), singleLine = true)
                    Field(fat, { fat = it.filter(Char::isDigit) }, "Fat g", Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("Save goals") {
                    p.kcalGoal = kcal.toIntOrNull() ?: p.kcalGoal
                    p.proteinGoal = prot.toIntOrNull() ?: p.proteinGoal
                    p.carbsGoal = carb.toIntOrNull() ?: p.carbsGoal
                    p.fatGoal = fat.toIntOrNull() ?: p.fatGoal
                    vm.message = "Goals saved"
                }
                Text("Not sure? Ask your coach: \"Calculate my macros — I'm 180cm, 80kg, want to lean bulk.\"",
                    color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item {
            Panel(title = "Plans & context") {
                Text("Paste anything here — your coach and quest generator read all of it. You can also paste plans straight into the Coach chat.",
                    color = C.muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                PlanType.all.forEach { t ->
                    PlanEditor(t, plans.firstOrNull { it.type == t }?.content.orEmpty()) { vm.savePlan(t, it) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Plan in a spreadsheet? Import an Excel (.xlsx) or CSV file.", color = C.muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Import training plan", enabled = vm.busy == null) {
                        importType = PlanType.TRAINING; planPicker.launch(planMimeTypes)
                    }
                    GhostButton("Import eating plan", enabled = vm.busy == null) {
                        importType = PlanType.NUTRITION; planPicker.launch(planMimeTypes)
                    }
                }
            }
        }

        item {
            Panel(title = "Google Calendar") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Read my appointments", color = C.text, modifier = Modifier.weight(1f))
                    Switch(
                        checked = useCal,
                        onCheckedChange = { useCal = it; p.useCalendar = it; vm.refreshCalendar() },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.accent),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Let the coach add events", color = C.text, modifier = Modifier.weight(1f))
                    Switch(
                        checked = calWrite,
                        onCheckedChange = { calWrite = it; p.calendarWrite = it; if (it) requestCalendar() },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.accent),
                    )
                }
                Text("Your calendar is used for appointments only: quests and advice plan around them. " +
                    "Holiday and birthday calendars are ignored. The coach only writes to it if you switch that on.",
                    color = C.muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                GhostButton("Grant calendar access", onClick = requestCalendar)
            }
        }

        item {
            Panel(title = "Health Connect (Samsung Health)") {
                Text(if (vm.healthConnected) "Connected ✓ — steps, sleep, workouts and weight sync hourly, and measurable quests complete themselves."
                    else "Sync steps, sleep, workouts and weight from your phone/watch. In Samsung Health: Settings → Health Connect → allow sharing.",
                    color = if (vm.healthConnected) C.good else C.muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(if (vm.healthConnected) "Permissions" else "Connect", onClick = connectHealth)
                    if (vm.healthConnected) GhostButton("Sync now", enabled = vm.busy == null) { vm.syncHealth() }
                }
            }
        }

        item {
            Panel(title = "Backup") {
                val last = vm.prefs.lastBackup
                Text(
                    "Save everything (except your API key) to a file. Pick Google Drive in the file picker for a cloud backup. " +
                        if (last > 0) "Last backup: " + java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) else "No backup yet.",
                    color = C.muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Back up", enabled = vm.busy == null, onClick = backup)
                    GhostButton("Restore", enabled = vm.busy == null) { confirmRestore = true }
                }
            }
        }

        item {
            Panel(title = "Daily rhythm") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(morning, { morning = it.filter(Char::isDigit).take(2) }, "Quests at (h)", Modifier.weight(1f), singleLine = true)
                    Field(evening, { evening = it.filter(Char::isDigit).take(2) }, "Check-in at (h)", Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("Save schedule") {
                    p.morningHour = (morning.toIntOrNull() ?: 6).coerceIn(0, 23)
                    p.eveningHour = (evening.toIntOrNull() ?: 21).coerceIn(0, 23)
                    vm.rescheduleNotifications()
                    vm.message = "Notifications rescheduled"
                }
            }
        }

        item {
            Panel(title = "Memory · what your coach knows") {
                if (memories.isEmpty()) Text("Nothing yet. Your coach saves facts here as you chat.", color = C.muted, fontSize = 13.sp)
                memories.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${m.text}", color = C.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.deleteMemory(m) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Field(memText, { memText = it }, "Add a fact", placeholder = "e.g. Bad left knee — no jumping")
                Spacer(Modifier.height(6.dp))
                GhostButton("Add", enabled = memText.isNotBlank()) { vm.addMemory(memText.trim()); memText = "" }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun PlanEditor(type: String, current: String, onSave: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(current) }
    LaunchedEffect(current) { if (!open) text = current }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().tap { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(PlanType.label(type), color = C.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Chip(if (current.isBlank()) "EMPTY" else "SAVED", if (current.isBlank()) C.muted else C.good)
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Field(text, { text = it }, PlanType.label(type), minLines = 5, placeholder = PlanType.hint(type))
            Spacer(Modifier.height(6.dp))
            GhostButton("Save") { onSave(text.trim()); open = false }
        }
    }
}
