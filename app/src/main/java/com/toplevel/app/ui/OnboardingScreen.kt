package com.toplevel.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.Actions
import com.toplevel.app.data.PlanType
import com.toplevel.app.game.Nutrition

private const val STEPS = 10 // 0 welcome, 1 name, 2 goals, 3 work & learning, 4 level, 5 body, 6 targets, 7 key, 8 rhythm, 9 done

private val equipmentOptions = listOf(
    "Pull-up bar", "Parallettes", "Rings", "Dip bars", "Dumbbells", "Kettlebell",
    "Barbell / gym", "Resistance bands", "Bodyweight only",
)

private val goalOptions = listOf(
    "Build muscle", "Lose fat", "Calisthenics skills", "Run / endurance", "Career growth",
    "Learn new skills", "Save money", "Sleep & recovery", "Mental calm", "Eat healthier",
)

/** First-run setup, one question per page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: AppViewModel, actions: Actions) {
    val p = vm.prefs
    var step by rememberSaveable { mutableIntStateOf(0) }
    var forward by rememberSaveable { mutableStateOf(true) }

    // Answers (pre-filled when re-running setup)
    var name by rememberSaveable { mutableStateOf(p.name) }
    var goals by rememberSaveable { mutableStateOf(listOf<String>()) }
    var goalText by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(p.sex) }
    var age by rememberSaveable { mutableStateOf(if (p.age > 0) p.age.toString() else "") }
    var height by rememberSaveable { mutableStateOf(if (p.heightCm > 0) p.heightCm.toString() else "") }
    var weight by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable { mutableIntStateOf(p.activity) }
    var bodyGoal by rememberSaveable { mutableStateOf(p.bodyGoal) }
    var kcal by rememberSaveable { mutableStateOf(p.kcalGoal.toString()) }
    var prot by rememberSaveable { mutableStateOf(p.proteinGoal.toString()) }
    var carb by rememberSaveable { mutableStateOf(p.carbsGoal.toString()) }
    var fat by rememberSaveable { mutableStateOf(p.fatGoal.toString()) }
    var key by rememberSaveable { mutableStateOf(p.apiKey) }
    var morning by rememberSaveable { mutableIntStateOf(p.morningHour) }
    var evening by rememberSaveable { mutableIntStateOf(p.eveningHour) }
    var job by rememberSaveable { mutableStateOf("") }
    var stack by rememberSaveable { mutableStateOf("") }
    var workNow by rememberSaveable { mutableStateOf("") }
    var learnWhat by rememberSaveable { mutableStateOf("") }
    var learnStyle by rememberSaveable { mutableStateOf(listOf("Videos")) }
    var learnMin by rememberSaveable { mutableIntStateOf(30) }
    var level by rememberSaveable { mutableIntStateOf(p.trainingLevel) }
    var equipment by rememberSaveable { mutableStateOf(p.equipment.split(",").map { it.trim() }.filter { it.isNotEmpty() }) }
    var stageSel by rememberSaveable { mutableStateOf(mapOf<String, Int>()) }
    val progress by vm.ladderProgress.collectAsState(initial = emptyList())
    val plans by vm.plans.collectAsState(initial = emptyList())
    val planPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPlan(uri, PlanType.TRAINING)
    }

    val w = weight.replace(',', '.').toFloatOrNull()
    val bodyOk = (age.toIntOrNull() ?: 0) in 12..99 && (height.toIntOrNull() ?: 0) in 120..230 && (w ?: 0f) in 30f..300f

    fun go(to: Int) { forward = to > step; step = to.coerceIn(0, STEPS - 1) }

    /** Save what this step collected, then move on. */
    fun next() {
        when (step) {
            1 -> p.name = name
            2 -> {
                val txt = buildString {
                    if (goals.isNotEmpty()) append("Main goals: ${goals.joinToString(", ")}.")
                    if (goalText.isNotBlank()) { if (isNotEmpty()) append("\n"); append(goalText.trim()) }
                }
                if (txt.isNotBlank()) vm.savePlan(PlanType.GOALS, txt)
            }
            3 -> {
                val work = buildString {
                    if (job.isNotBlank()) append("Role: ${job.trim()}\n")
                    if (stack.isNotBlank()) append("Tools / tech stack: ${stack.trim()}\n")
                    if (workNow.isNotBlank()) append("Currently working on / struggling with: ${workNow.trim()}\n")
                }.trim()
                if (work.isNotBlank()) vm.savePlan(PlanType.WORK, work)
                val learn = buildString {
                    if (learnWhat.isNotBlank()) append("Wants to learn: ${learnWhat.trim()}\n")
                    if (learnStyle.isNotEmpty()) append("Prefers learning via: ${learnStyle.joinToString(", ")}\n")
                    append("Time for learning: $learnMin min/day")
                }.trim()
                vm.savePlan(PlanType.LEARNING, learn)
            }
            4 -> {
                p.trainingLevel = level
                p.equipment = equipment.joinToString(", ")
                stageSel.forEach { (id, s) -> if (s >= 0) vm.setStage(id, s, reward = false) }
                val tracked = stageSel.filterValues { it >= 0 }.keys.toList()
                if (tracked.isNotEmpty()) vm.setLadders(tracked)
            }
            5 -> if (bodyOk) {
                p.sex = sex; p.age = age.toInt(); p.heightCm = height.toInt(); p.activity = activity; p.bodyGoal = bodyGoal
                vm.logBody(w!!, null)
                val t = Nutrition.targets(sex == "m", age.toInt(), height.toInt(), w, activity, bodyGoal)
                kcal = t.kcal.toString(); prot = t.protein.toString(); carb = t.carbs.toString(); fat = t.fat.toString()
            }
            6 -> {
                kcal.toIntOrNull()?.let { p.kcalGoal = it }
                prot.toIntOrNull()?.let { p.proteinGoal = it }
                carb.toIntOrNull()?.let { p.carbsGoal = it }
                fat.toIntOrNull()?.let { p.fatGoal = it }
            }
            7 -> p.apiKey = key
            8 -> { p.morningHour = morning; p.eveningHour = evening; vm.rescheduleNotifications() }
        }
        if (step == STEPS - 1) vm.finishWizard() else go(step + 1)
    }

    BackHandler(enabled = step > 0) { go(step - 1) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        // Progress
        if (step in 1 until STEPS - 1) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("STEP $step / ${STEPS - 2}", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (i in 1 until STEPS - 1) {
                        val on = i <= step
                        Box(
                            Modifier.weight(1f).height(4.dp)
                                .glow(if (on) C.accent else C.border, androidx.compose.ui.graphics.RectangleShape,
                                    radius = if (on) 6.dp else 0.dp, width = 0.dp)
                                .background(if (on) C.accent else C.panel2)
                        )
                    }
                }
            }
        } else Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val d = if (forward) 1 else -1
                (slideInHorizontally { d * it / 3 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally { -d * it / 4 } + fadeOut(tween(160)))
            },
            modifier = Modifier.weight(1f),
            label = "wizard",
        ) { s ->
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (s) {
                        0 -> Welcome()
                        1 -> Ask("What should the System call you?", "Your name shows on your status window.") {
                            Field(name, { name = it }, "Your name", singleLine = true)
                        }
                        2 -> Ask("What do you want to level up?", "Pick everything that matters. Your daily quests are built from this.") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                goalOptions.forEach { g ->
                                    Pick(g, g in goals) { goals = if (g in goals) goals - g else goals + g }
                                }
                            }
                            Field(goalText, { goalText = it }, "In your own words (optional)", minLines = 3,
                                placeholder = "e.g. Full planche by summer, senior SRE by 2028, calmer mornings", voice = true)
                        }
                        3 -> Ask("Work & learning", "Your coach suggests videos, tools and quests for your job and what you want to learn.") {
                            Field(job, { job = it }, "What do you do?", singleLine = true, placeholder = "e.g. SRE at a bank, student, nurse…")
                            Field(stack, { stack = it }, "Tools / tech you use (optional)", minLines = 2,
                                placeholder = "e.g. Kubernetes, Terraform, AWS, Python, Grafana")
                            Field(workNow, { workNow = it }, "Working on or stuck with right now? (optional)", minLines = 2,
                                placeholder = "e.g. migrating to Argo CD, too many alerts at night", voice = true)
                            Field(learnWhat, { learnWhat = it }, "What do you want to learn?", minLines = 2,
                                placeholder = "e.g. CKA certification, Go, Spanish, investing basics", voice = true)
                            Label("HOW DO YOU LIKE TO LEARN?")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Videos", "Articles", "Courses", "Books", "Podcasts", "Hands-on projects").forEach { s ->
                                    Pick(s, s in learnStyle) { learnStyle = if (s in learnStyle) learnStyle - s else learnStyle + s }
                                }
                            }
                            Label("TIME FOR LEARNING PER DAY")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60, 90).forEach { m -> Pick("$m min", learnMin == m, Modifier.weight(1f)) { learnMin = m } }
                            }
                        }
                        4 -> Ask("Your training level", "So training quests fit you: not too easy, not too hard.") {
                            Label("TRAINING EXPERIENCE")
                            com.toplevel.app.data.Prefs.Levels.trainingLevels.forEachIndexed { i, (name, sub) ->
                                Pick("$name · $sub", level == i, Modifier.fillMaxWidth()) { level = i }
                            }
                            Label("EQUIPMENT YOU HAVE")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                equipmentOptions.forEach { e ->
                                    Pick(e, e in equipment) { equipment = if (e in equipment) equipment - e else equipment + e }
                                }
                            }
                            Label("SKILLS: WHERE ARE YOU NOW?")
                            Text("Tap your current stage. Leave on \"Not yet\" for skills you don't train.", color = C.muted, fontSize = 14.sp)
                            com.toplevel.app.data.Cali.ladders.forEach { l ->
                                val cur = stageSel[l.id] ?: progress.firstOrNull { it.ladder == l.id }?.stage ?: -1
                                Text(l.name.uppercase(), color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pick("Not yet", cur < 0) { stageSel = stageSel + (l.id to -1) }
                                    l.stages.forEachIndexed { i, s -> Pick(s, cur == i) { stageSel = stageSel + (l.id to i) } }
                                }
                            }
                            Label("YOUR TRAINING PLAN")
                            val has = plans.any { it.type == PlanType.TRAINING && it.content.isNotBlank() }
                            Text(if (has) "✓ Training plan loaded. Your quests will follow it."
                                else "Have your plan in Excel? Import the .xlsx (or a .csv) and your quests follow it.",
                                color = if (has) C.good else C.muted, fontSize = 14.sp)
                            GhostButton(if (has) "Replace plan file" else "Import plan (Excel / CSV)", Modifier.fillMaxWidth(),
                                enabled = vm.busy == null) { planPicker.launch(planMimeTypes) }
                        }
                        5 -> Ask("Your body", "Used to calculate your calories and macros. Stays on this phone.") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pick("Male", sex == "m", Modifier.weight(1f)) { sex = "m" }
                                Pick("Female", sex == "f", Modifier.weight(1f)) { sex = "f" }
                            }
                            val num = KeyboardOptions(keyboardType = KeyboardType.Number)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(age, { age = it.filter(Char::isDigit).take(2) }, "Age", Modifier.weight(1f), singleLine = true, keyboard = num)
                                Field(height, { height = it.filter(Char::isDigit).take(3) }, "Height cm", Modifier.weight(1f), singleLine = true, keyboard = num)
                                Field(weight, { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, "Weight kg",
                                    Modifier.weight(1f), singleLine = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                            Label("HOW ACTIVE ARE YOU?")
                            Nutrition.activityLabels.forEachIndexed { i, l -> Pick(l, activity == i, Modifier.fillMaxWidth()) { activity = i } }
                            Label("BODY GOAL")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pick("Lose fat", bodyGoal == "cut", Modifier.weight(1f)) { bodyGoal = "cut" }
                                Pick("Maintain", bodyGoal == "maintain", Modifier.weight(1f)) { bodyGoal = "maintain" }
                                Pick("Build", bodyGoal == "bulk", Modifier.weight(1f)) { bodyGoal = "bulk" }
                            }
                        }
                        6 -> Ask("Your daily targets", if (bodyOk) "Calculated from your body and goal. Change anything you like."
                            else "Set your daily calories and macros (you skipped body stats).") {
                            Panel(color = C.warn) {
                                Text("${kcal.ifBlank { "–" }} KCAL", color = Color.White, fontFamily = Fonts.display,
                                    fontWeight = FontWeight.Bold, fontSize = 28.sp, style = glowStyle(C.warn, 18f))
                                Text("Protein ${prot}g · Carbs ${carb}g · Fat ${fat}g", color = C.accentSoft, fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                            val num = KeyboardOptions(keyboardType = KeyboardType.Number)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(kcal, { kcal = it.filter(Char::isDigit) }, "kcal", Modifier.weight(1f), singleLine = true, keyboard = num)
                                Field(prot, { prot = it.filter(Char::isDigit) }, "Protein g", Modifier.weight(1f), singleLine = true, keyboard = num)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(carb, { carb = it.filter(Char::isDigit) }, "Carbs g", Modifier.weight(1f), singleLine = true, keyboard = num)
                                Field(fat, { fat = it.filter(Char::isDigit) }, "Fat g", Modifier.weight(1f), singleLine = true, keyboard = num)
                            }
                        }
                        7 -> Ask("Connect your AI coach", "Claude builds your quests, scans your meals, tells you what to eat and what to do next.") {
                            Field(key, { key = it }, "Anthropic API key", singleLine = true, password = true, placeholder = "sk-ant-…")
                            Text("Get one at console.anthropic.com → API keys. It's stored only on this phone. " +
                                "You can skip this and add it later in Settings; the app works without it, just less smart.",
                                color = C.muted, fontSize = 14.sp)
                            if (key.isNotBlank()) GhostButton("Test key", enabled = vm.busy == null) { p.apiKey = key; vm.testKey() }
                            if (vm.busy != null) Text("▍ ${vm.busy}", color = C.accent, fontWeight = FontWeight.Bold)
                        }
                        8 -> Ask("Daily rhythm & access", "When should the System brief you and ask for your report?") {
                            Stepper("Morning quests at", morning) { morning = (it + 24) % 24 }
                            Stepper("Evening report at", evening) { evening = (it + 24) % 24 }
                            Label("PERMISSIONS (OPTIONAL)")
                            GhostButton("Allow notifications & calendar", Modifier.fillMaxWidth(), onClick = actions.requestPerms)
                            GhostButton(if (vm.healthConnected) "Health Connect linked ✓" else "Connect Health (steps, sleep)",
                                Modifier.fillMaxWidth(), color = C.good, onClick = actions.connectHealth)
                        }
                        else -> Done(name)
                    }
                }
            }
        }

        // Navigation
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step in 1 until STEPS - 1) GhostButton("Back", color = C.muted) { go(step - 1) }
            val canNext = when (step) {
                1 -> name.isNotBlank()
                else -> true
            }
            val label = when (step) {
                0 -> "Accept"
                5 -> if (bodyOk) "Calculate" else "Skip"
                7 -> if (key.isBlank()) "Skip for now" else "Next"
                STEPS - 1 -> "Begin"
                else -> "Next"
            }
            PrimaryButton(label, Modifier.weight(1f), enabled = canNext) { next() }
        }
    }
}

@Composable
private fun Welcome() {
    val inf = rememberInfiniteTransition(label = "w")
    val pulse by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    var shown by rememberSaveable { mutableIntStateOf(0) }
    val lines = listOf(
        "A new Player has been detected.",
        "From now on, your life is the game: daily quests, EXP, levels, ranks and rewards.",
        "An AI coach watches your food, training and goals, and tells you the best next move.",
        "Do you accept?",
    )
    LaunchedEffect(Unit) { for (i in 1..lines.size) { kotlinx.coroutines.delay(550); shown = i } }
    Spacer(Modifier.height(40.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(72.dp).glow(C.accent, WindowShape, radius = 22.dp, strength = pulse, width = 1.5.dp)
                .clip(WindowShape).background(C.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Text("!", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, style = glowStyle(C.accent, 24f)) }
    }
    Text("NOTIFICATION", color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Bold, fontSize = 24.sp,
        letterSpacing = 3.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = glowStyle(C.accent, 22f))
    Panel {
        lines.forEachIndexed { i, l ->
            Text(l, color = if (i == lines.lastIndex) C.accentSoft else C.text, fontSize = if (i == 0) 20.sp else 17.sp,
                fontWeight = if (i == 0 || i == lines.lastIndex) FontWeight.Bold else FontWeight.Medium, lineHeight = 24.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (i < shown) 1f else 0f))
        }
    }
}

@Composable
private fun Done(name: String) {
    Spacer(Modifier.height(40.dp))
    Text("SETUP COMPLETE", color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Bold, fontSize = 24.sp,
        letterSpacing = 3.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = glowStyle(C.accent, 22f))
    Panel {
        Text("Player ${name.ifBlank { "Player" }} registered.", color = C.text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        listOf(
            "HOME: best next move, quests, videos & tools to learn",
            "FOOD: snap a meal or scan a barcode",
            "TRAIN: log sets and skill progress",
            "COACH: talk to your AI coach about anything",
        ).forEach {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Diamond(C.accent, 7.dp); Spacer(Modifier.width(10.dp))
                Text(it, color = C.text, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Ask(title: String, sub: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp,
            style = glowStyle(C.accent, 14f))
        Text(sub, color = C.muted, fontSize = 16.sp, lineHeight = 21.sp)
    }
    Spacer(Modifier.height(4.dp))
    content()
}

@Composable
private fun Label(t: String) =
    Text(t, color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun Pick(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .glow(if (on) C.accent else C.border, SmallShape, radius = if (on) 8.dp else 0.dp, strength = 0.6f)
            .clip(SmallShape)
            .background(if (on) C.accent.copy(alpha = 0.18f) else C.panel.copy(alpha = 0.8f))
            .tap(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text((if (on) "◆ " else "◇ ") + label, color = if (on) Color.White else C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stepper(label: String, hour: Int, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        GhostButton("−") { set(hour - 1) }
        Text("%02d:00".format(hour), color = Color.White, fontFamily = Fonts.display, fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp))
        GhostButton("+") { set(hour + 1) }
    }
}
