package com.toplevel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toplevel.app.data.CheckIn

@Composable
fun CheckInDialog(vm: AppViewModel, onClose: () -> Unit) {
    val existing by vm.checkInToday.collectAsState(initial = null)
    var mood by remember { mutableIntStateOf(3) }
    var energy by remember { mutableIntStateOf(3) }
    var sleep by remember { mutableFloatStateOf(7f) }
    var day by remember { mutableStateOf("") }
    var well by remember { mutableStateOf("") }
    var improve by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        val e = existing
        if (e != null && !loaded) {
            mood = e.mood; energy = e.energy; sleep = e.sleepHours
            day = e.dayText; well = e.wentWell; improve = e.improve
            loaded = true
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(C.bg).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Gap,
        ) {
            SystemHeader("EVENING REPORT")
            SystemNotice("Report on today. Honest answers make your coach sharper. +30 SPI")

            Panel(title = "Condition") {
                Scale("Mood", mood) { mood = it }
                Scale("Energy", energy) { energy = it }
                Text("Sleep last night: ${"%.1f".format(sleep)} h", color = C.text, fontSize = 14.sp)
                Slider(
                    value = sleep, onValueChange = { sleep = (it * 2).toInt() / 2f },
                    valueRange = 3f..11f,
                    colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent),
                )
            }
            Panel(title = "Log") {
                Field(day, { day = it }, "How was your day?", minLines = 3, voice = true)
                Spacer(Modifier.height(8.dp))
                Field(well, { well = it }, "What went well?", minLines = 2, voice = true)
                Spacer(Modifier.height(8.dp))
                Field(improve, { improve = it }, "What would you do better?", minLines = 2, voice = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Cancel", color = C.muted) }
                Spacer(Modifier.weight(1f))
                GhostButton("Submit report", enabled = day.isNotBlank()) {
                    vm.saveCheckIn(CheckIn(vm.day, mood, energy, sleep, day.trim(), well.trim(), improve.trim()))
                    onClose()
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Scale(label: String, value: Int, onChange: (Int) -> Unit) {
    Text(label, color = C.text, fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { i ->
            Box(
                Modifier.size(44.dp).clip(SmallShape)
                    .background(if (i <= value) C.accent.copy(alpha = 0.25f + i * 0.12f) else C.bg)
                    .tap { onChange(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text("$i", color = if (i <= value) C.text else C.muted,
                    fontWeight = FontWeight.Bold, fontFamily = Fonts.tech)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
