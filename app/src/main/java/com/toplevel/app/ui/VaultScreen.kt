package com.toplevel.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.toplevel.app.data.Achievements
import com.toplevel.app.data.Cali
import com.toplevel.app.data.Reward
import com.toplevel.app.data.RewardStatus
import com.toplevel.app.data.Stat
import com.toplevel.app.data.defaultGoldCost
import com.toplevel.app.data.rewardStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val GOLD = androidx.compose.ui.graphics.Color(0xFFFFD166)

@Composable
fun VaultScreen(vm: AppViewModel) {
    val gold by vm.gold.collectAsState(initial = 0)
    val pot by vm.pot.collectAsState(initial = 0f)
    val month by vm.savedThisMonth.collectAsState(initial = 0f)
    val rewards by vm.rewards.collectAsState(initial = emptyList())
    val savings by vm.savings.collectAsState(initial = emptyList())
    var addOpen by remember { mutableStateOf(false) }
    var skipOpen by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }
    var goalEdit by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {

        item {
            Panel {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("GOLD", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
                        Text("◈ $gold", color = GOLD, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("Earned from quests, training, saving", color = C.muted, fontSize = 11.sp)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("SAVINGS POT", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
                        Text("€${"%.0f".format(pot)}", color = C.good, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("Real money set aside", color = C.muted, fontSize = 11.sp)
                    }
                }
            }
        }

        // Savings
        item {
            Panel(title = "Save money") {
                val goal = vm.prefs.savingsGoal
                Row {
                    Text("This month", color = C.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("€${"%.0f".format(month)} / €$goal", color = C.muted, fontSize = 12.sp, fontFamily = Fonts.tech,
                        modifier = Modifier.tap { goalEdit = true })
                }
                Spacer(Modifier.height(4.dp))
                Bar(if (goal > 0) month / goal else 0f, C.good, height = 8)
                Spacer(Modifier.height(10.dp))
                Text("Moved money to savings? Log it: +DIS EXP and +Gold (½ per €).", color = C.muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 20, 50).forEach { a -> GhostButton("+€$a") { vm.deposit(a.toFloat(), "") } }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(custom, { custom = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "€ amount", Modifier.weight(1f), singleLine = true)
                    GhostButton("Save", enabled = custom.isNotBlank()) {
                        custom.replace(',', '.').toFloatOrNull()?.let { vm.deposit(it, "") }; custom = ""
                    }
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("🛑 I almost bought something…") { skipOpen = true }
                Text("Didn't buy it? The money goes to your pot instead.", color = C.muted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Rewards
        item {
            Panel(
                title = "Rewards",
                action = {
                    IconButton(onClick = { addOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Add, "Add reward", tint = C.accent)
                    }
                },
            ) {
                Text("Treat yourself — once you've earned it. Each reward needs Gold, the money in your pot, " +
                    "its unlock condition, and a 72h cool-down after adding it (no impulse buys).",
                    color = C.muted, fontSize = 12.sp)
                val open = rewards.filter { it.redeemedAt == null }
                if (open.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("No rewards yet. Tap + to add one — e.g. \"New desk, €250, unlock at straddle planche\".",
                        color = C.text, fontSize = 13.sp)
                }
                open.forEach { r -> RewardRow(vm, r, gold, pot) }
            }
        }

        val claimed = rewards.filter { it.redeemedAt != null }
        if (claimed.isNotEmpty()) item {
            Panel(title = "Claimed") {
                val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                claimed.forEach { r ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("◆ ${r.name}", color = C.good, modifier = Modifier.weight(1f))
                        Text(Instant.ofEpochMilli(r.redeemedAt ?: 0).atZone(ZoneId.systemDefault()).format(fmt),
                            color = C.muted, fontSize = 12.sp)
                    }
                }
            }
        }

        if (savings.isNotEmpty()) item {
            Panel(title = "Pot history") {
                val fmt = DateTimeFormatter.ofPattern("dd.MM HH:mm")
                savings.take(30).forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Instant.ofEpochMilli(s.ts).atZone(ZoneId.systemDefault()).format(fmt), color = C.muted,
                            fontSize = 11.sp, fontFamily = Fonts.tech, modifier = Modifier.width(80.dp))
                        Text(s.note.ifBlank { "Saved" }, color = C.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text((if (s.amountEur >= 0) "+" else "") + "€${"%.2f".format(s.amountEur)}",
                            color = if (s.amountEur >= 0) C.good else C.bad, fontSize = 13.sp, fontFamily = Fonts.tech)
                        if (s.rewardId == null) IconButton(onClick = { vm.deleteSavings(s) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    if (addOpen) AddRewardDialog(vm) { addOpen = false }
    if (skipOpen) SkipDialog(vm) { skipOpen = false }
    if (goalEdit) {
        var g by remember { mutableStateOf(vm.prefs.savingsGoal.toString()) }
        AlertDialog(
            onDismissRequest = { goalEdit = false },
            containerColor = C.panel,
            title = { Text("Monthly savings goal", color = C.text) },
            text = { Field(g, { g = it.filter(Char::isDigit) }, "€ per month", singleLine = true) },
            confirmButton = {
                TextButton(onClick = { g.toIntOrNull()?.let { vm.setSavingsGoal(it) }; goalEdit = false }) { Text("Save", color = C.accent) }
            },
        )
    }
    vm.claimed?.let { r -> ClaimedOverlay(r) { vm.claimed = null } }
}

@Composable
private fun RewardRow(vm: AppViewModel, r: Reward, gold: Int, pot: Float) {
    val st by produceState<RewardStatus?>(null, r, gold, pot) {
        value = withContext(Dispatchers.IO) { vm.repo.rewardStatus(r, gold, pot) }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(SmallShape)
            .background(C.bg.copy(alpha = 0.5f)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("◈ Gold  $gold / ${r.goldCost}", color = GOLD, fontSize = 12.sp, fontFamily = Fonts.tech)
        Bar(gold.toFloat() / r.goldCost.coerceAtLeast(1), GOLD)
        Spacer(Modifier.height(4.dp))
        Text("€ Saved  ${"%.0f".format(pot)} / ${"%.0f".format(r.priceEur)}", color = C.good, fontSize = 12.sp, fontFamily = Fonts.tech)
        Bar(if (r.priceEur > 0) pot / r.priceEur else 1f, C.good)
        val s = st
        if (s != null) {
            Spacer(Modifier.height(6.dp))
            if (r.condition.isNotBlank()) Check(s.conditionOk, s.conditionLabel)
            Check(s.cooldownOk, if (s.cooldownOk) "Cool-down passed" else "Cool-down: ${s.cooldownLeftH}h left — still want it?")
            Spacer(Modifier.height(8.dp))
            GhostButton(if (s.ready) "Claim reward ✦" else "Locked", enabled = s.ready) { vm.redeem(r) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = C.panel,
        title = { Text("Remove ${r.name}?") },
        confirmButton = { TextButton(onClick = { vm.deleteReward(r); confirmDelete = false }) { Text("Remove", color = C.bad) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Check(ok: Boolean, label: String) {
    Text((if (ok) "✓ " else "✗ ") + label, color = if (ok) C.good else C.muted, fontSize = 12.sp)
}

@Composable
private fun AddRewardDialog(vm: AppViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var goldCost by remember { mutableStateOf("") }
    var condType by remember { mutableStateOf("none") }
    var level by remember { mutableStateOf("10") }
    var ladder by remember { mutableStateOf("planche") }
    var stage by remember { mutableStateOf(3) }
    var ach by remember { mutableStateOf(Achievements.all.first().id) }
    val p = price.replace(',', '.').toFloatOrNull() ?: 0f

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text("New reward", color = C.text) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Field(name, { name = it }, "What do you want?", placeholder = "e.g. New desk", singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Field(price, { price = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Price €", Modifier.weight(1f), singleLine = true)
                        Field(goldCost, { goldCost = it.filter(Char::isDigit) }, "Gold (${defaultGoldCost(p)})", Modifier.weight(1f), singleLine = true)
                    }
                }
                item { Text("Unlock condition", color = C.muted, fontSize = 12.sp) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Segment("None", condType == "none") { condType = "none" }
                        Segment("Skill", condType == "cali") { condType = "cali" }
                        Segment("Level", condType == "level") { condType = "level" }
                        Segment("Badge", condType == "ach") { condType = "ach" }
                    }
                }
                when (condType) {
                    "level" -> item { Field(level, { level = it.filter(Char::isDigit) }, "Reach level", singleLine = true) }
                    "cali" -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Cali.ladders.take(3).forEach { l -> Segment(l.name, ladder == l.id) { ladder = l.id } }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Cali.ladders.drop(3).forEach { l -> Segment(l.name, ladder == l.id) { ladder = l.id } }
                            }
                        }
                        item {
                            val l = Cali.byId(ladder)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                l?.stages?.forEachIndexed { i, st -> Segment("${i + 1}. $st", stage == i) { stage = i } }
                            }
                        }
                    }
                    "ach" -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Achievements.all.forEach { a -> Segment(a.name, ach == a.id) { ach = a.id } }
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && p > 0f, onClick = {
                val cond = when (condType) {
                    "level" -> "level:${level.toIntOrNull() ?: 10}"
                    "cali" -> "cali:$ladder:$stage"
                    "ach" -> "ach:$ach"
                    else -> ""
                }
                vm.addReward(name, p, goldCost.toIntOrNull(), cond)
                onClose()
            }) { Text("Add", color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun SkipDialog(vm: AppViewModel, onClose: () -> Unit) {
    var what by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text("Impulse resisted", color = C.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What didn't you buy? That money goes to your pot instead.", color = C.muted, fontSize = 13.sp)
                Field(what, { what = it }, "What", placeholder = "e.g. Takeaway, sneakers…", singleLine = true)
                Field(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "€", singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = what.isNotBlank() && amount.isNotBlank(), onClick = {
                amount.replace(',', '.').toFloatOrNull()?.let { vm.skipped(what.trim(), it) }
                onClose()
            }) { Text("Save it", color = C.good) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun ClaimedOverlay(r: Reward, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.clip(WindowShape).background(C.panel).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("[ SYSTEM ]", color = C.accent, fontFamily = Fonts.tech, letterSpacing = 3.sp, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("REWARD CLAIMED", color = GOLD, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(r.name, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("You earned this. Enjoy it, no guilt.", color = C.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            GhostButton("Continue", onClick = onClose)
        }
    }
}

@Suppress("unused")
private val unusedStat = Stat.SPIRIT
