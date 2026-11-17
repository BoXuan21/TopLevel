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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.toplevel.app.data.Achievements
import com.toplevel.app.data.Expense
import com.toplevel.app.data.Growth
import com.toplevel.app.data.SavingsEntry
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

private fun eur(v: Double) = "€" + if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)

/** Old menu id, kept so links and tests that open "vault" still land on the money page. */
@Composable
fun VaultScreen(vm: AppViewModel) = MoneyScreen(vm)

/**
 * Spending, savings, Gold and rewards on one page (they used to be two pages).
 * One entry form with three modes: spent / saved / didn't buy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyScreen(vm: AppViewModel) {
    val gold by vm.gold.collectAsState(initial = 0)
    val pot by vm.pot.collectAsState(initial = 0f)
    val savedMonth by vm.savedThisMonth.collectAsState(initial = 0f)
    val rewards by vm.rewards.collectAsState(initial = emptyList())
    val savings by vm.savings.collectAsState(initial = emptyList())
    val spentList by vm.expensesMonth.collectAsState(initial = emptyList())
    @Suppress("UNUSED_VARIABLE") val bv = vm.budgetsVersion
    var mode by remember { mutableStateOf("spent") }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Growth.categories.first()) }
    var note by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<String?>(null) } // "__total", "__savings" or a category
    var limitsOpen by remember { mutableStateOf(false) }
    val limits = vm.prefs.budgetMap()
    val spent = spentList.sumOf { it.amountEur.toDouble() }
    val budget = vm.prefs.monthBudget
    val goal = vm.prefs.savingsGoal

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {

        // Overview: spent, saved, Gold
        item {
            Panel(title = "This month", color = C.warn) {
                Row {
                    Column(Modifier.weight(1f).tap { edit = "__total" }) {
                        Text("SPENT", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
                        Text(eur(spent), color = if (budget > 0 && spent > budget) C.bad else C.warn, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text(if (budget > 0) "of €$budget budget" else "Tap to set a budget", color = C.muted, fontSize = 11.sp)
                    }
                    Column(Modifier.weight(1f).tap { edit = "__savings" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SAVED", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
                        Text(eur(savedMonth.toDouble()), color = C.good, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text(if (goal > 0) "of €$goal goal" else "Tap to set a goal", color = C.muted, fontSize = 11.sp)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("GOLD", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
                        Text("◈ $gold", color = GOLD, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("Pot €${"%.0f".format(pot)}", color = C.muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (budget > 0) Bar((spent / budget).toFloat(), if (spent > budget) C.bad else C.warn, height = 5)
                if (goal > 0) { Spacer(Modifier.height(4.dp)); Bar(savedMonth / goal, C.good, height = 5) }
                Spacer(Modifier.height(8.dp))
                Text(if (vm.noSpend > 0) "🔥 ${vm.noSpend}-day no-spend streak (+5 Gold per clean day)"
                    else "Days without spending and money you save earn Gold.", color = if (vm.noSpend > 0) C.warn else C.muted,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // One form for everything
        item {
            Panel(title = "Add") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Segment("− Spent", mode == "spent") { mode = "spent" }
                    Segment("+ Saved", mode == "saved") { mode = "saved" }
                    Segment("Didn't buy", mode == "skipped") { mode = "skipped" }
                }
                Spacer(Modifier.height(10.dp))
                if (mode == "saved") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 20, 50).forEach { a -> GhostButton("+€$a") { vm.deposit(a.toFloat(), "") } }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Field(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Amount €", singleLine = true,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (mode == "spent") {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Growth.categories.forEach { c -> Segment(c, cat == c) { cat = c } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Field(note, { note = it }, if (mode == "skipped") "What didn't you buy?" else "Note (optional)", singleLine = true)
                Spacer(Modifier.height(8.dp))
                val a = amount.replace(',', '.').toFloatOrNull()
                val ok = a != null && a > 0 && (mode != "skipped" || note.isNotBlank())
                PrimaryButton(when (mode) { "spent" -> "Log spending"; "saved" -> "Save"; else -> "Into the pot" }, Modifier.fillMaxWidth(), enabled = ok) {
                    when (mode) {
                        "spent" -> vm.addExpense(a!!, cat, note.trim())
                        "saved" -> vm.deposit(a!!, note.trim())
                        else -> vm.skipped(note.trim(), a!!)
                    }
                    amount = ""; note = ""
                }
                Text(when (mode) {
                    "spent" -> "Spending is just tracked: no penalty, but it ends the no-spend streak."
                    "saved" -> "Moved money to savings: +DIS EXP and +½ Gold per €."
                    else -> "Resisted an impulse buy? That money goes to your pot instead."
                }, color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // Rewards
        item {
            Panel(title = "Rewards", color = GOLD, action = {
                IconButton(onClick = { addOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, "Add reward", tint = C.accent)
                }
            }) {
                val open = rewards.filter { it.redeemedAt == null }
                Text(if (open.isEmpty()) "Tap + to add something you want, e.g. \"New desk, €250\". " +
                    "It unlocks with Gold, the money in your pot and a 72h cool-down." else
                    "Needs Gold, the money in your pot, its condition and a 72h cool-down (no impulse buys).",
                    color = C.muted, fontSize = 12.sp)
                open.forEach { r -> RewardRow(vm, r, gold, pot) }
            }
        }

        // Limits per category (collapsed)
        item {
            Panel(title = "Categories", action = {
                TextButton(onClick = { limitsOpen = !limitsOpen }) { Text(if (limitsOpen) "Done" else "Limits", color = C.accent) }
            }) {
                val shown = Growth.categories.filter { c -> limitsOpen || spentList.any { it.category == c } || limits[c] != null }
                if (shown.isEmpty()) Text("Nothing spent this month.", color = C.muted, fontSize = 13.sp)
                shown.forEach { c ->
                    val sp = spentList.filter { it.category == c }.sumOf { it.amountEur.toDouble() }
                    val lim = limits[c]
                    Row(Modifier.fillMaxWidth().tap { edit = c }.padding(top = 6.dp)) {
                        Text(c, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(eur(sp) + (lim?.let { " / €$it" } ?: if (limitsOpen) "  + limit" else ""),
                            color = if (lim != null && sp > lim) C.bad else C.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(3.dp))
                    Bar(if (lim != null && lim > 0) (sp / lim).toFloat() else if (spent > 0) (sp / spent).toFloat() else 0f,
                        if (lim != null && sp > lim) C.bad else C.accent, height = 5)
                }
            }
        }

        // History: spending this month and savings, newest first
        val history = spentList.map { e -> Triple(e.ts, -e.amountEur, e.category + if (e.note.isNotBlank()) " · ${e.note}" else "") to (e as Any) } +
            savings.take(40).map { s -> Triple(s.ts, s.amountEur, s.note.ifBlank { "Saved" }) to (s as Any) }
        if (history.isNotEmpty()) item {
            Panel(title = "History") {
                val fmt = DateTimeFormatter.ofPattern("dd.MM")
                history.sortedByDescending { it.first.first }.take(50).forEach { (row, src) ->
                    val (ts, amt, label) = row
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(fmt), color = C.muted,
                            fontSize = 12.sp, fontFamily = Fonts.tech, modifier = Modifier.width(46.dp))
                        Text(label, color = C.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text((if (amt >= 0) "+" else "−") + eur(kotlin.math.abs(amt).toDouble()),
                            color = if (amt >= 0) C.good else C.warn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val deletable = src is Expense || (src is SavingsEntry && src.rewardId == null)
                        if (deletable) IconButton(onClick = {
                            if (src is Expense) vm.deleteExpense(src) else if (src is SavingsEntry) vm.deleteSavings(src)
                        }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                        } else Spacer(Modifier.width(30.dp))
                    }
                }
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
        item { Spacer(Modifier.height(80.dp)) }
    }

    if (addOpen) AddRewardDialog(vm) { addOpen = false }
    edit?.let { c ->
        val current = when (c) { "__total" -> budget; "__savings" -> goal; else -> limits[c] ?: 0 }
        var v by remember(c) { mutableStateOf(current.takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { edit = null }, containerColor = C.panel,
            title = { Text(when (c) { "__total" -> "Monthly budget"; "__savings" -> "Monthly savings goal"; else -> "$c limit / month" }, color = C.text) },
            text = { Field(v, { v = it.filter(Char::isDigit) }, "€ (empty = none)", singleLine = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(onClick = {
                val n = v.toIntOrNull() ?: 0
                when (c) { "__total" -> vm.setMonthBudget(n); "__savings" -> vm.setSavingsGoal(n); else -> vm.setBudget(c, n) }
                edit = null
            }) { Text("Save", color = C.accent) } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } },
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
fun AddRewardDialog(vm: AppViewModel, initialName: String = "", initialPrice: String = "", onClose: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var price by remember { mutableStateOf(initialPrice) }
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
