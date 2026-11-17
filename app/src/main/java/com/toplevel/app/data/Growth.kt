package com.toplevel.app.data

import com.toplevel.app.ai.Claude
import com.toplevel.app.ai.Claude.Companion.str
import com.toplevel.app.game.Leveling
import com.toplevel.app.widget.Widgets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import kotlin.math.roundToInt

/** Pure rules for the growth features (unit-tested). */
object Growth {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ───────── Stat points ─────────
    const val POINTS_PER_LEVEL = 3
    const val BOOST_PER_POINT = 0.03

    fun pointsEarned(level: Int) = (level - 1).coerceAtLeast(0) * POINTS_PER_LEVEL
    /** EXP after the stat-point boost (capped at ×2). */
    fun boost(amount: Int, points: Int): Int = (amount * minOf(2.0, 1 + points * BOOST_PER_POINT)).roundToInt()

    // ───────── Flashcards (SM-2 lite) ─────────
    /** grade: 0 again, 1 hard, 2 good, 3 easy */
    fun review(c: Flashcard, grade: Int, today: Long): Flashcard {
        val good = when (c.reps) { 0 -> 1; 1 -> 3; else -> (c.interval * c.ease).roundToInt().coerceAtLeast(c.interval + 1) }
        return when (grade) {
            0 -> c.copy(interval = 0, reps = 0, ease = (c.ease - 0.2f).coerceAtLeast(1.3f), due = today)
            1 -> {
                val i = (c.interval * 1.2).roundToInt().coerceAtLeast(1)
                c.copy(interval = i, reps = c.reps + 1, ease = (c.ease - 0.15f).coerceAtLeast(1.3f), due = today + i)
            }
            3 -> {
                val i = (good * 1.3).roundToInt() + 1
                c.copy(interval = i, reps = c.reps + 1, ease = c.ease + 0.15f, due = today + i)
            }
            else -> c.copy(interval = good, reps = c.reps + 1, due = today + good)
        }
    }

    // ───────── Spending ─────────
    val categories = listOf("Groceries", "Eating out", "Transport", "Shopping", "Fun", "Subscriptions", "Health", "Other")

    /** Consecutive days with no spending, ending yesterday, never before the first logged expense. */
    fun noSpendStreak(spendDays: Set<Long>, firstDay: Long?, today: Long): Int {
        if (firstDay == null) return 0
        var n = 0
        var d = today - 1
        while (d >= firstDay && d !in spendDays) { n++; d-- }
        return n
    }

    // ───────── Weekly review ─────────
    fun weekKey(day: Long): Long = LocalDate.ofEpochDay(day).with(java.time.DayOfWeek.MONDAY).toEpochDay()
}

// ───────────────────────── Colour themes ─────────────────────────

/** Unlockable colour themes (one per job class). */
object Themes {
    data class T(val id: String, val name: String)
    val all = listOf(
        T("system", "System Blue"), T("monarch", "Shadow Monarch"), T("crimson", "Crimson"),
        T("gold", "Gold"), T("emerald", "Emerald"),
    )
    fun unlocked(p: Prefs): List<T> {
        val done = p.jobsDone.split(",").filter { it.isNotBlank() }
        val ids = setOf("system") + Jobs.all.filter { it.id in done }.map { it.theme } + if (done.any { it.startsWith("custom") }) setOf("monarch") else emptySet()
        return all.filter { it.id in ids }
    }
}

// ───────────────────────── Job change ─────────────────────────

/**
 * A step of a class-change questline. cond: "" = you tick it yourself; otherwise measured automatically:
 * cali:<ladder>:<stage>, sets:N, streak:N, learned:N, stat:<STAT>:N, level:N, focusmin:N, dungeons:N,
 * fooddays:N, proteindays:N, weighins:N, saved:N, nospend:N, cards:N
 */
@Serializable
data class JobStep(val text: String, val cond: String = "")

@Serializable
data class JobDef(
    val id: String, val name: String, val title: String, val stat: Stat, val theme: String,
    val desc: String, val steps: List<JobStep>,
)

@Serializable
data class JobState(val def: JobDef, val manualDone: List<Int> = emptyList(), val startedAt: Long = 0)

object Jobs {
    val all = listOf(
        JobDef("monarch", "Calisthenics Monarch", "Monarch of Levers", Stat.STRENGTH, "monarch",
            "Master the hardest static holds.", listOf(
                JobStep("Reach tuck planche", "cali:planche:1"),
                JobStep("Reach advanced tuck front lever", "cali:frontlever:1"),
                JobStep("Log 150 calisthenics sets", "sets:150"),
                JobStep("Reach straddle planche", "cali:planche:3"),
                JobStep("Hold a 14-day quest streak", "streak:14"),
            )),
        JobDef("architect", "Architect", "Cluster Architect", Stat.INTELLECT, "system",
            "Become a certified Kubernetes architect (CKA).", listOf(
                JobStep("Log 20 things you learned", "learned:20"),
                JobStep("Finish a CKA course"),
                JobStep("Review 100 flashcards", "cards:100"),
                JobStep("Pass 3 mock exams"),
                JobStep("Pass the CKA exam"),
            )),
        JobDef("ironwill", "Iron Will", "Iron Will", Stat.DISCIPLINE, "crimson",
            "Unbreakable focus and consistency.", listOf(
                JobStep("Bank 1,000 minutes of focus", "focusmin:1000"),
                JobStep("Clear 3 dungeons", "dungeons:3"),
                JobStep("Hold a 14-day quest streak", "streak:14"),
                JobStep("Hold a 30-day quest streak", "streak:30"),
            )),
        JobDef("sage", "Vital Sage", "Vital Sage", Stat.VITALITY, "emerald",
            "Fuel and recovery on autopilot.", listOf(
                JobStep("Log food on 21 days", "fooddays:21"),
                JobStep("Hit your protein goal on 14 days", "proteindays:14"),
                JobStep("Log your weight 20 times", "weighins:20"),
                JobStep("Reach Vitality level 10", "stat:VITALITY:10"),
            )),
        JobDef("treasurer", "Shadow Treasurer", "Treasurer", Stat.DISCIPLINE, "gold",
            "Money works for you, not the other way round.", listOf(
                JobStep("Save €500", "saved:500"),
                JobStep("7-day no-spend streak", "nospend:7"),
                JobStep("Save €1,000", "saved:1000"),
            )),
    )
}

data class StepProgress(val step: JobStep, val current: Int, val target: Int, val done: Boolean)

suspend fun Repo.jobState(): JobState? = try {
    prefs.jobJson.takeIf { it.isNotBlank() }?.let { Growth.json.decodeFromString(JobState.serializer(), it) }
} catch (_: Exception) { null }

private fun Repo.saveJob(s: JobState?) {
    prefs.jobJson = s?.let { Growth.json.encodeToString(JobState.serializer(), it) } ?: ""
}

fun Repo.startJob(def: JobDef) {
    saveJob(JobState(def, startedAt = System.currentTimeMillis()))
    SystemEvents.show(Popup(Popup.Kind.ARRIVED, "JOB CHANGE QUEST", "Class: ${def.name}. ${def.desc}",
        lines = def.steps.map { it.text }, reward = "Reward: title \"${def.title}\" + new colour theme"))
}

fun Repo.abandonJob() = saveJob(null)

suspend fun Repo.toggleJobStep(index: Int) {
    val s = jobState() ?: return
    val d = if (index in s.manualDone) s.manualDone - index else s.manualDone + index
    saveJob(s.copy(manualDone = d))
    checkJobComplete()
}

/** Current/target for an automatic condition. */
suspend fun Repo.condProgress(cond: String): Pair<Int, Int> {
    val p = cond.split(":")
    val target = p.last().toIntOrNull() ?: 1
    val b = db.backup()
    val cur = when (p[0]) {
        "cali" -> db.vault().progressOf(p.getOrNull(1) ?: "")?.stage ?: 0
        "sets" -> b.sets().size
        "streak" -> streak()
        "learned" -> b.logs().count { it.category == "learned" }
        "stat" -> Leveling.statLevel(Leveling.totals(game.statTotalsNow())[Stat.parse(p.getOrNull(1))] ?: 0)
        "level" -> Leveling.levelFor(Leveling.totals(game.statTotalsNow()).values.sum())
        "focusmin" -> prefs.focusTotalMin
        "dungeons" -> prefs.dungeonsCleared
        "fooddays" -> b.food().map { it.date }.toSet().size
        "proteindays" -> b.food().groupBy { it.date }.count { (_, l) -> l.sumOf { it.protein } >= prefs.proteinGoal }
        "weighins" -> b.body().size
        "saved" -> b.savings().filter { it.amountEur > 0 }.sumOf { it.amountEur.toDouble() }.toInt()
        "nospend" -> noSpendStreak()
        "cards" -> b.xp().filter { it.reason.startsWith("cards:") }.sumOf { it.amount } / 2
        else -> 0
    }
    return cur to target
}

suspend fun Repo.jobProgress(): List<StepProgress> {
    val s = jobState() ?: return emptyList()
    return s.def.steps.mapIndexed { i, st ->
        if (st.cond.isBlank()) StepProgress(st, if (i in s.manualDone) 1 else 0, 1, i in s.manualDone)
        else condProgress(st.cond).let { (c, t) -> StepProgress(st, minOf(c, t), t, c >= t) }
    }
}

/** Finishes the job when every step is done: title, theme, big rewards, pop-up. */
suspend fun Repo.checkJobComplete(): Boolean {
    val s = jobState() ?: return false
    val prog = jobProgress()
    if (prog.isEmpty() || !prog.all { it.done }) return false
    val d = s.def
    award(d.stat, 500, "job:${d.id}:${s.startedAt}")
    gainGold(300, "job:${d.id}:${s.startedAt}")
    prefs.jobsDone = (prefs.jobsDone.split(",").filter { it.isNotBlank() } + d.id).distinct().joinToString(",")
    prefs.activeTitle = d.title
    prefs.theme = d.theme
    saveJob(null)
    SystemEvents.show(Popup(Popup.Kind.JOB, "JOB CHANGE COMPLETE", "You are now: ${d.name}",
        lines = listOf("Title unlocked: ${d.title}", "Theme unlocked: ${Themes.all.firstOrNull { it.id == d.theme }?.name ?: d.theme}"),
        reward = "+500 ${d.stat.short} EXP · +300 Gold"))
    Widgets.refresh(ctx)
    return true
}

/** AI (one small call): turn a goal into a 4–5 step class-change questline. */
suspend fun Repo.customJob(goal: String): JobDef {
    val o = claude.askJson(
        system = "Design a 'job change' questline (a multi-week class change in a level-up life app) for this goal. " +
            "4–5 concrete, checkable milestones in order, from first step to the goal itself. " +
            "Return {\"name\": epic class name (2-3 words), \"title\": title they earn, \"stat\": one of STRENGTH|VITALITY|INTELLECT|DISCIPLINE|SPIRIT, " +
            "\"desc\": one short line, \"steps\": [\"...\"]}. Match the user's language.",
        user = goal, maxTokens = 400, small = true,
    ) as? JsonObject ?: throw IllegalStateException("Couldn't design that job")
    val steps = (o["steps"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.take(6)
    if (steps.isEmpty()) throw IllegalStateException("Couldn't design that job")
    return JobDef("custom${System.currentTimeMillis()}", str(o, "name") ?: "Custom class", str(o, "title") ?: goal.take(30),
        Stat.parse(str(o, "stat")), "monarch", str(o, "desc").orEmpty(), steps.map { JobStep(it) })
}

// ───────────────────────── Dungeons ─────────────────────────

@Serializable
data class Dungeon(
    val id: String, val name: String, val desc: String, val stat: Stat, val xp: Int, val gold: Int,
    val minutes: Int, val penalty: String,
)

@Serializable
data class ActiveDungeon(val d: Dungeon, val start: Long, val end: Long)

object Dungeons {
    val all = listOf(
        Dungeon("deepwork", "Deep-work dungeon", "90 minutes on one hard task. Phone face down, no tabs.", Stat.DISCIPLINE,
            120, 40, 90, "Penalty: 30 min focused work before bed"),
        Dungeon("reading", "Library dungeon", "Read 50 pages (or one long chapter) in 2 hours.", Stat.INTELLECT,
            120, 40, 120, "Penalty: read 20 pages tomorrow morning"),
        Dungeon("gauntlet", "Training gauntlet", "One brutal session: beat your usual volume by 20%.", Stat.STRENGTH,
            150, 50, 75, "Penalty: 100 push-ups"),
        Dungeon("phonefree", "Phone-free evening", "3 hours without social media or scrolling.", Stat.SPIRIT,
            100, 30, 180, "Penalty: 1 hour offline tomorrow"),
        Dungeon("nosugar", "No-sugar weekend", "48 hours with no sweets, soda or desserts.", Stat.VITALITY,
            220, 70, 48 * 60, "Penalty: no sweets for 3 days"),
        Dungeon("nospend", "No-spend gate", "72 hours without buying anything that isn't essential.", Stat.DISCIPLINE,
            200, 80, 72 * 60, "Penalty: move €20 to savings"),
    )
    fun custom(name: String, minutes: Int, stat: Stat) = Dungeon("custom", name, "Your own challenge.", stat,
        (minutes / 2 + 40).coerceIn(40, 250), (minutes / 6 + 15).coerceIn(15, 80), minutes, "Penalty: redo it tomorrow")
}

fun Repo.activeDungeon(): ActiveDungeon? = try {
    prefs.dungeonJson.takeIf { it.isNotBlank() }?.let { Growth.json.decodeFromString(ActiveDungeon.serializer(), it) }
} catch (_: Exception) { null }

fun Repo.enterDungeon(d: Dungeon): ActiveDungeon {
    val now = System.currentTimeMillis()
    val a = ActiveDungeon(d, now, now + d.minutes * 60_000L)
    prefs.dungeonJson = Growth.json.encodeToString(ActiveDungeon.serializer(), a)
    SystemEvents.show(Popup(Popup.Kind.BOSS, "A GATE HAS OPENED", d.name, lines = listOf(d.desc),
        reward = "Clear: +${d.xp} ${d.stat.short} EXP · +${d.gold} Gold"))
    return a
}

/** Only after the timer has run out. */
suspend fun Repo.clearDungeon(): Boolean {
    val a = activeDungeon() ?: return false
    if (System.currentTimeMillis() < a.end) return false
    award(a.d.stat, a.d.xp, "dungeon:${a.start}")
    gainGold(a.d.gold, "dungeon:${a.start}")
    prefs.dungeonsCleared += 1
    prefs.dungeonJson = ""
    SystemEvents.show(Popup(Popup.Kind.CLEAR, "DUNGEON CLEARED", a.d.name,
        reward = "+${a.d.xp} ${a.d.stat.short} EXP · +${a.d.gold} Gold"))
    checkJobComplete()
    Widgets.refresh(ctx)
    return true
}

/** Leaving early spawns a penalty quest. */
suspend fun Repo.quitDungeon() {
    val a = activeDungeon() ?: return
    prefs.dungeonJson = ""
    addQuest(a.d.penalty, a.d.stat, 20, source = "penalty")
    SystemEvents.show(Popup(Popup.Kind.WARNING, "DUNGEON FAILED", "You left ${a.d.name} early.",
        lines = listOf(a.d.penalty), reward = "Penalty quest added"))
}

// ───────────────────────── Flashcards ─────────────────────────

/** AI (one small call per batch): make cards from new "learned" journal entries. Returns how many were made. */
suspend fun Repo.generateCards(force: Boolean = false): Int {
    val day = today()
    if (!prefs.hasKey || (!force && prefs.cardGenDay == day)) return 0
    val g = db.grow()
    val since = maxOf(prefs.cardLastLog, g.lastCardSource())
    val entries = db.backup().logs().filter { it.category == "learned" && it.id > since }.sortedBy { it.id }.take(12)
    prefs.cardGenDay = day
    if (entries.isEmpty()) return 0
    prefs.cardLastLog = entries.last().id // processed, even if the AI skips one
    val o = claude.askJson(
        system = "Make spaced-repetition flashcards from what the user learned. 1–2 cards per entry, testing the key idea, " +
            "not trivia. Front: a short question (max 90 chars). Back: the answer (max 180 chars). Match the entry's language. " +
            "Return {\"cards\": [{\"log\": entry id, \"front\": \"...\", \"back\": \"...\"}]}.",
        user = entries.joinToString("\n") { "[${it.id}] ${it.text.take(400)}" }, maxTokens = 1200, small = true,
    ) as? JsonObject ?: return 0
    val now = System.currentTimeMillis()
    val cards = (o["cards"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { c ->
        val f = str(c, "front") ?: return@mapNotNull null
        val b = str(c, "back") ?: return@mapNotNull null
        Flashcard(front = f, back = b, due = day, createdAt = now, sourceLog = str(c, "log")?.toLongOrNull() ?: entries.last().id)
    }
    if (cards.isNotEmpty()) g.insertCards(cards)
    return cards.size
}

suspend fun Repo.addCard(front: String, back: String) =
    db.grow().insertCards(listOf(Flashcard(front = front, back = back, due = today(), createdAt = System.currentTimeMillis())))

// ───────────────────────── Learn: topics + AI quiz ─────────────────────────

/** What the user is learning right now = skill-tree items with status "learning". */
suspend fun Repo.learnTopics(): List<SkillItem> = life.skillsNow().filter { it.status == "learning" }

/** Add something you're learning. [level] is free text: your level, goal or exam date. */
suspend fun Repo.addLearnTopic(title: String, level: String) {
    val t = title.trim()
    val existing = life.skillsNow().firstOrNull { it.title.equals(t, true) }
    if (existing != null) life.updateSkill(existing.copy(status = "learning", notes = level.ifBlank { existing.notes }, learnedAt = null))
    else life.insertSkill(SkillItem(title = t, status = "learning", notes = level.trim(), createdAt = System.currentTimeMillis()))
}

/** Stop learning a topic (its cards stay, so you keep reviewing what you know). */
suspend fun Repo.removeLearnTopic(s: SkillItem) = life.updateSkill(s.copy(status = "todo"))

/** Quiz stats per topic, used to adapt the next questions to how the user is doing. */
data class TopicStats(val cards: Int, val due: Int, val weak: List<String>, val strong: Int)

suspend fun Repo.topicStats(topic: String): TopicStats {
    val cards = db.grow().cardsForTopic(topic)
    val day = today()
    return TopicStats(
        cards = cards.size,
        due = cards.count { it.due <= day },
        // reviewed but failed recently / hard for them
        weak = cards.filter { it.reps == 0 && it.ease < 2.5f || it.ease < 2.2f }.map { it.front },
        strong = cards.count { it.reps >= 2 && it.ease >= 2.5f },
    )
}

/**
 * AI: write [n] new quiz questions for a topic, adapted to the user: their level/goal, their learning plan,
 * what they already got asked (no repeats), and where they struggle (go deeper there).
 */
suspend fun Repo.generateQuiz(topic: SkillItem, n: Int = 8): Int {
    val existing = db.grow().cardsForTopic(topic.title)
    val st = topicStats(topic.title)
    val plan = life.plansNow().firstOrNull { it.type == PlanType.LEARNING }?.content?.take(1200).orEmpty()
    val o = claude.askJson(
        system = """You are a tutor quizzing the user on what they are learning right now. Write $n new quiz questions on the topic.
- Fit their level and goal. Test understanding and use (why, how, what happens if, which command/word/formula), not trivia.
- Mix: mostly short-answer questions, a few small "apply it" scenarios. One idea per question.
- If they struggle with some questions, ask about those ideas again from a different angle and fill the gaps behind them.
- If they know most answers, make it harder; if they're new (few or no cards), start with the foundations.
- Never repeat or rephrase a question they already have.
- Question max 140 chars, answer max 240 chars (the key point first, then a short why). Write in the language of the topic/level text
  (if unclear, use the language of their learning plan).
Return {"cards": [{"q": "...", "a": "..."}]}""",
        user = buildString {
            append("Topic: ${topic.title}\n")
            if (topic.notes.isNotBlank()) append("Their level / goal: ${topic.notes}\n")
            if (plan.isNotBlank()) append("Their learning plan:\n$plan\n")
            append("\nProgress: ${st.cards} questions so far, ${st.strong} well known.\n")
            if (st.weak.isNotEmpty()) append("They struggle with:\n" + st.weak.takeLast(10).joinToString("\n") { "- $it" } + "\n")
            if (existing.isNotEmpty()) append("Already asked (don't repeat):\n" + existing.takeLast(60).joinToString("\n") { "- ${it.front}" })
        },
        maxTokens = 2000, small = true,
    ) as? JsonObject ?: throw IllegalStateException("Couldn't make questions. Try again.")
    val now = System.currentTimeMillis()
    val day = today()
    val seen = existing.map { it.front.lowercase().trim() }.toMutableSet()
    val cards = (o["cards"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { c ->
        val q = (str(c, "q") ?: str(c, "front"))?.trim() ?: return@mapNotNull null
        val a = (str(c, "a") ?: str(c, "back"))?.trim() ?: return@mapNotNull null
        if (!seen.add(q.lowercase())) return@mapNotNull null
        Flashcard(front = q, back = a, due = day, createdAt = now, topic = topic.title)
    }
    if (cards.isNotEmpty()) db.grow().insertCards(cards)
    Widgets.refresh(ctx)
    return cards.size
}

data class QuizResult(val grade: Int, val feedback: String)

/**
 * AI checks the user's own answer against the reference answer (small, cheap call),
 * then schedules the card like a normal review.
 */
suspend fun Repo.checkAnswer(c: Flashcard, answer: String): QuizResult {
    val o = claude.askJson(
        system = "You check a learner's answer to a quiz question. Be fair: meaning counts, not wording; typos don't matter. " +
            "grade: 0 = wrong or missing the key idea, 1 = partly right, 2 = right, 3 = right and complete/precise. " +
            "feedback: max 2 short sentences, in the language of the question: say what was right, then fix or add what was missing. " +
            "Return {\"grade\": 0-3, \"feedback\": \"...\"}",
        user = "Question: ${c.front}\nReference answer: ${c.back}\nTheir answer: ${answer.take(600)}",
        maxTokens = 400, small = true,
    ) as? JsonObject ?: throw IllegalStateException("Couldn't check that answer")
    val grade = (str(o, "grade")?.toDoubleOrNull()?.toInt() ?: 0).coerceIn(0, 3)
    reviewCard(c, grade)
    return QuizResult(grade, str(o, "feedback").orEmpty())
}

/** Grade a card; +2 INT per review, max 20 per day. */
suspend fun Repo.reviewCard(c: Flashcard, grade: Int) {
    val day = today()
    db.grow().updateCard(Growth.review(c, grade, day))
    val n = prefs.cardsReviewed(day) + 1
    prefs.setCardsReviewed(day, n)
    award(Stat.INTELLECT, minOf(20, n * 2), "cards:$day")
    // Quiz quests ("answer 10 quiz questions") complete themselves
    game.questsForNow(day).filter { !it.done && it.autoType == "cards" && it.target in 1..n }.forEach { completeQuest(it.id) }
    Widgets.refresh(ctx)
}

// ───────────────────────── Spending ─────────────────────────

suspend fun Repo.noSpendStreak(): Int {
    val g = db.grow()
    val first = g.firstExpenseDay() ?: return 0
    val days = g.expensesSinceNow(first).map { it.date }.toSet()
    return Growth.noSpendStreak(days, first, today())
}

/** Log spending; warns when a category or the month goes over its limit. */
suspend fun Repo.addExpense(amount: Float, category: String, note: String) {
    val day = today()
    db.grow().insertExpense(Expense(ts = System.currentTimeMillis(), date = day, amountEur = amount, category = category, note = note))
    val month = db.grow().expensesSinceNow(monthStart())
    val cat = month.filter { it.category == category }.sumOf { it.amountEur.toDouble() }
    val total = month.sumOf { it.amountEur.toDouble() }
    val limit = prefs.budgetMap()[category]
    when {
        limit != null && cat > limit && cat - amount <= limit -> SystemEvents.show(Popup(Popup.Kind.WARNING, "BUDGET EXCEEDED",
            "$category: €${cat.roundToInt()} of €$limit this month.", reward = "Tighten up for the rest of the month"))
        prefs.monthBudget > 0 && total > prefs.monthBudget && total - amount <= prefs.monthBudget -> SystemEvents.show(Popup(
            Popup.Kind.WARNING, "MONTHLY BUDGET EXCEEDED", "€${total.roundToInt()} of €${prefs.monthBudget} spent.",
            reward = "No-spend days help"))
    }
}

/** Once a day: reward yesterday if nothing was spent (only once you track spending). */
suspend fun Repo.rewardNoSpend() {
    val g = db.grow()
    val first = g.firstExpenseDay() ?: return
    val y = today() - 1
    if (y < first) return
    if (g.expensesSinceNow(y).none { it.date == y }) {
        award(Stat.DISCIPLINE, 10, "nospend:$y")
        gainGold(5, "nospend:$y")
    }
}

// ───────────────────────── Weekly review ─────────────────────────

data class WeekNumbers(
    val questsDone: Int, val questsTotal: Int,
    val xpByStat: Map<Stat, Int>,
    val questPctByDay: List<Float>,
    val avgKcal: Int, val proteinDays: Int, val foodDays: Int,
    val avgSteps: Int, val avgSleepMin: Int,
    val sets: Int, val spent: Int, val saved: Int,
    val moodAvg: Float, val weightChange: Float?,
)

suspend fun Repo.weekNumbers(endDay: Long): WeekNumbers {
    val from = endDay - 6
    val b = db.backup()
    val quests = game.questsSince(from).filter { it.date <= endDay }
    val z = java.time.ZoneId.systemDefault()
    fun dayOf(ts: Long) = java.time.Instant.ofEpochMilli(ts).atZone(z).toLocalDate().toEpochDay()
    val xp = b.xp().filter { dayOf(it.ts) in from..endDay && it.amount > 0 }.groupBy { it.stat }.mapValues { it.value.sumOf { e -> e.amount } }
    val food = b.food().filter { it.date in from..endDay }.groupBy { it.date }
    val health = b.health().filter { it.date in from..endDay }
    val body = b.body().filter { it.date in (from - 7)..endDay }.sortedBy { it.date }
    val checkins = b.checkins().filter { it.date in from..endDay }
    return WeekNumbers(
        questsDone = quests.count { it.done }, questsTotal = quests.size,
        xpByStat = xp,
        questPctByDay = (from..endDay).map { d -> quests.filter { it.date == d }.let { l -> if (l.isEmpty()) 0f else l.count { it.done }.toFloat() / l.size } },
        avgKcal = if (food.isEmpty()) 0 else food.values.sumOf { l -> l.sumOf { it.kcal } } / food.size,
        proteinDays = food.count { (_, l) -> l.sumOf { it.protein } >= prefs.proteinGoal },
        foodDays = food.size,
        avgSteps = if (health.isEmpty()) 0 else health.sumOf { it.steps } / health.size,
        avgSleepMin = health.filter { it.sleepMin > 0 }.let { if (it.isEmpty()) 0 else it.sumOf { h -> h.sleepMin } / it.size },
        sets = b.sets().count { it.date in from..endDay },
        spent = db.grow().expensesSinceNow(from).filter { it.date <= endDay }.sumOf { it.amountEur.toDouble() }.roundToInt(),
        saved = b.savings().filter { it.date in from..endDay && it.amountEur > 0 }.sumOf { it.amountEur.toDouble() }.roundToInt(),
        moodAvg = if (checkins.isEmpty()) 0f else checkins.map { it.mood }.average().toFloat(),
        weightChange = if (body.size >= 2) body.last().weightKg - body.first().weightKg else null,
    )
}

@Serializable
data class ReviewChange(val title: String, val why: String = "")

fun Repo.cachedReview(): List<ReviewChange> =
    if (prefs.reviewWeek != Growth.weekKey(today())) emptyList() else try {
        Growth.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ReviewChange.serializer()), prefs.reviewJson)
    } catch (_: Exception) { emptyList() }

/** AI, once per week: the top 3 changes for next week, from this week's numbers (cheap: no full history). */
suspend fun Repo.weeklyChanges(now: WeekNumbers, last: WeekNumbers): List<ReviewChange> {
    fun fmt(w: WeekNumbers) = "quests ${w.questsDone}/${w.questsTotal}, EXP ${w.xpByStat.entries.joinToString { "${it.key.short} ${it.value}" }}, " +
        "food logged ${w.foodDays}/7 days (avg ${w.avgKcal} kcal, protein goal hit ${w.proteinDays} days), avg steps ${w.avgSteps}, " +
        "avg sleep ${w.avgSleepMin / 60}h${w.avgSleepMin % 60}, sets ${w.sets}, spent €${w.spent}, saved €${w.saved}, " +
        "mood ${"%.1f".format(w.moodAvg)}/5, weight change ${w.weightChange?.let { "%.1f kg".format(it) } ?: "n/a"}"
    val goals = life.plansNow().firstOrNull { it.type == PlanType.GOALS }?.content?.take(500).orEmpty()
    val o = claude.askJson(
        system = "You review the user's week in a level-up life app. Compare this week with last week and pick the 3 changes " +
            "that would help most next week. Concrete and small (doable in 7 days). Return {\"changes\": [{\"title\": max 8 words, " +
            "\"why\": max 20 words, grounded in the numbers}]}. Match the user's language.",
        user = "Goals: $goals\nThis week: ${fmt(now)}\nLast week: ${fmt(last)}\nTarget: ${prefs.kcalGoal} kcal, ${prefs.proteinGoal} g protein.",
        maxTokens = 400, small = true,
    ) as? JsonObject ?: return emptyList()
    val list = (o["changes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        .map { ReviewChange(str(it, "title").orEmpty(), str(it, "why").orEmpty()) }.filter { it.title.isNotBlank() }.take(3)
    prefs.reviewWeek = Growth.weekKey(today())
    prefs.reviewJson = Growth.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ReviewChange.serializer()), list)
    return list
}
