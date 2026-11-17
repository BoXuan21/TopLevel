package com.toplevel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.toplevel.app.data.SkillItem

@Composable
fun SkillsPanels(vm: AppViewModel) {
    val skills by vm.skills.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Gap) {
        val learned = skills.count { it.status == "learned" }
        Panel(title = "Skill tree · $learned acquired", action = {
            if (vm.prefs.hasKey) Text("✦", color = if (vm.busy == null) C.accent else C.muted, fontSize = 18.sp,
                modifier = Modifier.semantics { contentDescription = "Suggest next skills" }
                    .tap { if (vm.busy == null) vm.suggestSkills() }.padding(horizontal = 10.dp, vertical = 4.dp))
            IconButton(onClick = { adding = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, "Add skill", tint = C.accent)
            }
        }) {
            Text(if (skills.isEmpty()) "Tap + to add something you want to learn" + (if (vm.prefs.hasKey) ", or ✦ for suggestions." else ".")
                else "Tap ◇ to advance: locked → in training → acquired (+60 INT).",
                color = C.muted, fontSize = 12.sp)
        }
        listOf("learning" to "In training", "todo" to "Locked", "learned" to "Acquired").forEach { (st, label) ->
            val list = skills.filter { it.status == st }
            if (list.isNotEmpty()) {
                Panel(title = "$label · ${list.size}") {
                    list.forEach { s -> androidx.compose.runtime.key(s.id) { SkillRow(vm, s) } }
                }
            }
        }
    }
    if (adding) AddSkillDialog(vm) { adding = false }
}

@Composable
private fun SkillRow(vm: AppViewModel, s: SkillItem) {
    var open by remember { mutableStateOf(false) }
    var notes by remember(s.id) { mutableStateOf(s.notes) }
    val color = when (s.status) { "learned" -> C.good; "learning" -> C.accent; else -> C.muted }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(SmallShape)
            .background(C.bg.copy(alpha = 0.5f)).padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (s.status) { "learned" -> "◆"; "learning" -> "◈"; else -> "◇" },
                color = color, fontSize = 18.sp,
                modifier = Modifier.tap {
                    vm.setSkillStatus(s, when (s.status) { "todo" -> "learning"; "learning" -> "learned"; else -> "todo" })
                },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).tap { open = !open }) {
                Text(s.title, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (s.area.isNotBlank()) Text(s.area.uppercase(), color = C.accentSoft, fontSize = 10.sp,
                    fontFamily = Fonts.tech)
                if (!open && s.notes.isNotBlank()) Text(s.notes, color = C.muted, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = { vm.deleteSkill(s) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
            }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Field(notes, { notes = it }, "Notes / resources / what I learned", minLines = 3)
            Spacer(Modifier.height(6.dp))
            GhostButton("Save notes") { vm.saveSkillNotes(s, notes); open = false }
        }
    }
}

@Composable
private fun AddSkillDialog(vm: AppViewModel, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text("New skill", color = C.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(title, { title = it }, "Skill or topic", placeholder = "e.g. eBPF, SLO design, Go concurrency", singleLine = true)
                Field(area, { area = it }, "Area (optional)", singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { vm.addSkill(title.trim(), area.trim()); onClose() }) {
                Text("Add", color = C.accent)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
