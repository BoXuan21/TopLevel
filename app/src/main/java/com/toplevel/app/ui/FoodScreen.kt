package com.toplevel.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.toplevel.app.data.Product
import com.toplevel.app.data.Stat
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun FoodScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val food by vm.food.collectAsState(initial = emptyList())
    var text by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    var kcal by remember { mutableStateOf("") }
    var prot by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    // Camera: remember where the photo goes and what it's for
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf("meal") }
    var atHome by rememberSaveable { mutableStateOf("") }
    val hasKey = vm.prefs.hasKey

    fun newPhotoUri(): Uri {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(dir, "scan_${System.currentTimeMillis()}.jpg"))
    }
    fun handle(uri: Uri) {
        if (mode == "barcode") vm.scanBarcode(uri) else { vm.logPhoto(uri, text.trim()); text = "" }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending?.let { handle(Uri.parse(it)) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) handle(uri)
    }
    fun shoot(m: String) {
        mode = m
        val u = newPhotoUri(); pending = u.toString()
        try { camera.launch(u) } catch (_: Exception) { vm.message = "No camera app found" }
    }

    val tk = food.sumOf { it.kcal }
    val tp = food.sumOf { it.protein }
    val tc = food.sumOf { it.carbs }
    val tf = food.sumOf { it.fat }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item { SystemHeader("FUEL") }

        // Today
        item {
            Panel(title = "Today", color = C.stat(Stat.VITALITY)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KcalRing(tk, vm.prefs.kcalGoal)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        MacroLine("PROTEIN", tp, vm.prefs.proteinGoal, C.stat(Stat.VITALITY))
                        MacroLine("CARBS", tc, vm.prefs.carbsGoal, C.accent)
                        MacroLine("FAT", tf, vm.prefs.fatGoal, C.stat(Stat.SPIRIT))
                    }
                }
                val left = vm.prefs.proteinGoal - tp
                Spacer(Modifier.height(4.dp))
                if (left > 0) Text("$left g protein to go", color = C.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                else SystemNotice("Protein goal reached. +20 VIT")
            }
        }

        // Scan / log
        item {
            Panel(title = "Log a meal") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScanTile("◉", "SNAP MEAL", if (hasKey) "AI reads the plate" else "Needs API key",
                        Modifier.weight(1f), C.stat(Stat.VITALITY), enabled = hasKey && vm.busy == null) { shoot("meal") }
                    ScanTile("▥", "BARCODE", "Exact label values", Modifier.weight(1f), C.warn,
                        enabled = vm.busy == null) { shoot("barcode") }
                    ScanTile("▣", "GALLERY", "Photo or label", Modifier.weight(1f), C.accent,
                        enabled = hasKey && vm.busy == null) {
                        mode = "meal"
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Field(text, { text = it; vm.searchFood(it) }, "Search or describe it",
                    placeholder = "e.g. nöm pro 35, hühnerdöner, 200 g chicken + rice", voice = true)
                val hits = vm.foodHits
                if (hits.query.isNotEmpty() && hits.query == text.trim()) {
                    Spacer(Modifier.height(8.dp))
                    SearchResults(vm, hits, hasKey) { text = "" }
                } else Text(
                    if (hasKey) "Type a product or dish to find it, or describe a whole meal and let the AI estimate it."
                    else "Type a product or dish to find it. Add your API key in Settings for AI estimates.",
                    color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                if (manual || !hasKey) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val num = KeyboardOptions(keyboardType = KeyboardType.Number)
                        Field(kcal, { kcal = it.filter(Char::isDigit) }, "kcal", Modifier.weight(1f), singleLine = true, keyboard = num)
                        Field(prot, { prot = it.filter(Char::isDigit) }, "P", Modifier.weight(1f), singleLine = true, keyboard = num)
                        Field(carb, { carb = it.filter(Char::isDigit) }, "C", Modifier.weight(1f), singleLine = true, keyboard = num)
                        Field(fat, { fat = it.filter(Char::isDigit) }, "F", Modifier.weight(1f), singleLine = true, keyboard = num)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (manual || !hasKey) {
                        PrimaryButton("Log", Modifier.weight(1f), enabled = text.isNotBlank()) {
                            vm.addFood(text.trim(), kcal.toIntOrNull() ?: 0, prot.toIntOrNull() ?: 0,
                                carb.toIntOrNull() ?: 0, fat.toIntOrNull() ?: 0)
                            text = ""; kcal = ""; prot = ""; carb = ""; fat = ""
                        }
                    } else {
                        PrimaryButton("Estimate & log", Modifier.weight(1f), enabled = text.isNotBlank() && vm.busy == null) {
                            vm.estimateAndAddFood(text.trim()); text = ""
                        }
                    }
                    if (hasKey) GhostButton(if (manual) "AI" else "Manual") { manual = !manual }
                }
            }
        }

        // One-tap re-log
        if (vm.recentFoods.isNotEmpty()) {
            item {
                Panel(title = "Eat it again") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.recentFoods, key = { it.id }) { f ->
                            Column(
                                Modifier.glow(C.border, SmallShape, radius = 0.dp).clip(SmallShape)
                                    .background(C.bg.copy(alpha = 0.6f)).tap { vm.relog(f) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(f.name.take(26), color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${f.kcal} kcal · P${f.protein}", color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("Tap to log the same thing again.", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // Water
        item {
            Panel(title = "Water", color = C.accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(vm.prefs.waterGoal) { i ->
                            val on = i < vm.water
                            Box(
                                Modifier.weight(1f).height(26.dp)
                                    .glow(if (on) C.accent else C.border, SmallShape, radius = if (on) 6.dp else 0.dp, strength = 0.6f)
                                    .clip(SmallShape)
                                    .background(if (on) Brush.verticalGradient(listOf(C.accentSoft, C.glow)) else Brush.verticalGradient(listOf(C.bg, C.bg)))
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${vm.water}/${vm.prefs.waterGoal}", color = C.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("+1 glass", Modifier.weight(1f)) { vm.addWater(1) }
                    GhostButton("−1", color = C.muted) { vm.addWater(-1) }
                }
            }
        }

        // What to eat next
        item {
            val inf = rememberInfiniteTransition(label = "fa")
            val pulse by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
            Panel(
                title = "What should I eat next?",
                color = C.stat(Stat.VITALITY),
                action = {
                    if (hasKey && vm.foodAdvice.isNotBlank()) IconButton(onClick = { vm.askWhatToEat() }, enabled = !vm.foodAdviceBusy,
                        modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Refresh, "Ask again", tint = C.accent) }
                },
            ) {
                when {
                    !hasKey -> Text("Add your API key in Settings and your coach will plan your next meal from what you've eaten.",
                        color = C.muted, fontSize = 15.sp)
                    vm.foodAdviceBusy -> Text("▍ Checking your macros and plan…", color = C.accent, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.alpha(pulse))
                    else -> {
                        if (vm.foodAdvice.isBlank()) Text(
                            "Your coach looks at your targets, what you've eaten, the time and today's training, then suggests what to eat.",
                            color = C.muted, fontSize = 15.sp)
                        else AdviceText(vm.foodAdvice)
                        Spacer(Modifier.height(10.dp))
                        Field(atHome, { atHome = it }, "What do you have at home? (optional)",
                            placeholder = "e.g. eggs, rice, skyr, frozen veg, tuna", voice = true)
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(if (atHome.isBlank()) "Ask the system" else "Plan from what I have", Modifier.fillMaxWidth()) {
                            vm.askWhatToEat(atHome.trim())
                        }
                    }
                }
            }
        }

        // Meals list
        item {
            Panel(title = "Meals today · ${food.size}") {
                if (food.isEmpty()) Text("Nothing logged yet.", color = C.muted, fontSize = 15.sp)
                val fmt = DateTimeFormatter.ofPattern("HH:mm")
                food.reversed().forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(Instant.ofEpochMilli(f.ts).atZone(ZoneId.systemDefault()).format(fmt), color = C.muted,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${f.kcal} kcal · P${f.protein} C${f.carbs} F${f.fat}", color = C.muted,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { vm.deleteFood(f) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "Delete", tint = C.muted)
                        }
                    }
                }
                if (hasKey) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Adjust my targets (AI)", enabled = vm.busy == null) { vm.suggestMacros() }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    vm.scanned?.let { p -> PortionDialog(p, onClose = { vm.scanned = null }) { g -> vm.logProduct(p, g) } }

    vm.macroSuggestion?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.macroSuggestion = null },
            containerColor = C.panel,
            title = { Text("Suggested targets", color = C.text) },
            text = {
                Column {
                    Text("${m.kcal} kcal · P${m.protein} C${m.carbs} F${m.fat}", color = C.accentSoft, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(m.why, color = C.text, lineHeight = 20.sp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.applyMacros(m) }) { Text("Apply", color = C.accent) } },
            dismissButton = { TextButton(onClick = { vm.macroSuggestion = null }) { Text("Keep current") } },
        )
    }
}

/** Live results while typing: recent foods, built-in dishes, Open Food Facts products, AI estimate. */
@Composable
private fun SearchResults(vm: AppViewModel, hits: AppViewModel.FoodHits, hasKey: Boolean, clear: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glow(C.border, SmallShape, radius = 0.dp).clip(SmallShape).background(C.bg.copy(alpha = 0.75f))
    ) {
        hits.recent.forEach { f ->
            Hit("↺", f.name, "you had this · ${f.kcal} kcal · P${f.protein}", C.accentSoft) { vm.relog(f); vm.clearFoodSearch(); clear() }
        }
        hits.dishes.forEach { d ->
            Hit("◆", d.name, "${d.portion} · ${d.kcal} kcal · P${d.protein} C${d.carbs} F${d.fat}", C.stat(Stat.VITALITY)) {
                vm.logDish(d); clear()
            }
        }
        hits.products.forEach { p ->
            val per = p.servingG?.let { "${it.roundToInt()} g: ${(p.kcal100 * it / 100).roundToInt()} kcal · P${(p.protein100 * it / 100).roundToInt()}" }
                ?: "100 g: ${p.kcal100.roundToInt()} kcal · P${p.protein100.roundToInt()}"
            Hit("▥", p.name, per, C.warn) { vm.pickProduct(p); clear() }
        }
        if (hits.searching) Text("▍ Searching products…", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp))
        else if (hits.recent.isEmpty() && hits.dishes.isEmpty() && hits.products.isEmpty())
            Text("Nothing found in the food database.", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        if (hasKey) Hit("✦", "Estimate \"${hits.query}\" with AI", "for whole meals, homemade food, restaurants", C.accent) {
            vm.estimateAndAddFood(hits.query); vm.clearFoodSearch(); clear()
        }
    }
}

@Composable
private fun Hit(icon: String, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().tap(onClick).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = color, fontSize = 16.sp, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(sub, color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text("+", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Renders the coach's plain-text meal advice, with bullets styled. */
@Composable
private fun AdviceText(t: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        t.lines().filter { it.isNotBlank() }.forEach { line ->
            val l = line.trim()
            when {
                l.startsWith("•") || l.startsWith("-") -> Row {
                    Box(Modifier.padding(top = 7.dp)) { Diamond(C.stat(Stat.VITALITY), 7.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text(l.trimStart('•', '-', ' '), color = C.text, fontSize = 15.sp, lineHeight = 20.sp)
                }
                l.startsWith("Tip:", ignoreCase = true) -> Text(l, color = C.warn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                else -> Text(l, color = C.accentSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun KcalRing(value: Int, goal: Int) {
    val f = if (goal > 0) value.toFloat() / goal else 0f
    val over = f > 1.1f
    val col = if (over) C.bad else C.warn
    Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 9.dp.toPx()
            val inset = sw / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(C.bg, 135f, 270f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Butt))
            drawArc(col.copy(alpha = 0.25f), 135f, 270f * f.coerceIn(0f, 1f), false, tl, arcSize, style = Stroke(sw * 2.2f, cap = StrokeCap.Butt))
            drawArc(Brush.sweepGradient(listOf(col.copy(alpha = 0.6f), col, col.copy(alpha = 0.6f))), 135f, 270f * f.coerceIn(0f, 1f),
                false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Butt))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                style = glowStyle(col, 14f))
            Text("/ $goal kcal", color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val left = goal - value
            Text(if (left >= 0) "$left left" else "${-left} over", color = if (left >= 0) C.good else C.bad,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MacroLine(label: String, value: Int, goal: Int, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
        Text("$value/$goal g", color = if (value > goal * 1.1) C.bad else C.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(3.dp))
    Bar(if (goal > 0) value.toFloat() / goal else 0f, color, height = 5)
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun ScanTile(icon: String, label: String, sub: String, modifier: Modifier, color: Color, enabled: Boolean, onClick: () -> Unit) {
    val c = if (enabled) color else C.muted
    Column(
        modifier
            .glow(c.copy(alpha = if (enabled) 0.9f else 0.4f), SmallShape, radius = if (enabled) 8.dp else 0.dp, strength = 0.55f)
            .clip(SmallShape)
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.18f), C.bg.copy(alpha = 0.7f))))
            .then(if (enabled) Modifier.tap(onClick) else Modifier)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, color = c, fontSize = 26.sp, style = glowStyle(c, 16f))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (enabled) C.text else C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(sub, color = C.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** After a barcode scan: how much did you eat? */
@Composable
private fun PortionDialog(p: Product, onClose: () -> Unit, onLog: (Double) -> Unit) {
    var grams by remember { mutableStateOf((p.servingG ?: 100.0).roundToInt().toString()) }
    val g = grams.replace(',', '.').toDoubleOrNull() ?: 0.0
    val m = p.macrosFor(g)
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text("PRODUCT FOUND", color = C.accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Per 100 g: ${p.kcal100.roundToInt()} kcal · P${p.protein100.roundToInt()} C${p.carbs100.roundToInt()} F${p.fat100.roundToInt()}",
                    color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Field(grams, { grams = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "How many grams?", singleLine = true,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOfNotNull(p.servingG?.roundToInt(), 50, 100, 200).distinct().take(4).forEach { v ->
                        Text("${v} g", color = C.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.glow(C.accent, SmallShape, radius = 0.dp).clip(SmallShape)
                                .tap { grams = v.toString() }.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                Text("= ${m.kcal} kcal · P${m.protein} C${m.carbs} F${m.fat}", color = C.warn, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Values from Open Food Facts.", color = C.muted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(enabled = g > 0, onClick = { onLog(g) }) { Text("LOG", color = C.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
