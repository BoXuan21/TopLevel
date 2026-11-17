package com.toplevel.app.data

import android.content.Context
import com.toplevel.app.ai.Claude
import com.toplevel.app.ai.Claude.Companion.int
import com.toplevel.app.ai.Claude.Companion.str
import com.toplevel.app.ai.ClaudeException
import com.toplevel.app.calendar.CalendarRepo
import com.toplevel.app.game.Leveling
import com.toplevel.app.game.Rewards
import com.toplevel.app.widget.Widgets
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The single place where game logic, storage and Claude meet. */
class Repo private constructor(internal val ctx: Context) {

    val db = AppDb.get(ctx)
    val game = db.game()
    val life = db.life()
    val fit = db.fit()
    val prefs = Prefs(ctx)
    val claude = Claude(prefs)

    init {
        // load custom calisthenics stage names
        listOf("planche", "frontlever", "backlever", "handstand", "muscleup", "hspu").forEach { id ->
            prefs.ladderStages(id).takeIf { it.size >= 2 }?.let { Cali.overrides[id] = it }
        }
    }

    fun today(): Long = LocalDate.now().toEpochDay()
    private fun now() = System.currentTimeMillis()

    // ───────────────────────── XP & quests ─────────────────────────

    internal suspend fun award(stat: Stat, amount: Int, reason: String) {
        game.deleteXpByReason(reason)
        // Stat points: +3% EXP per point put into this stat (gains only, never penalties)
        val boosted = if (amount > 0) Growth.boost(amount, prefs.points(stat)) else amount
        if (boosted != 0) game.insertXp(XpEvent(ts = now(), stat = stat, amount = boosted, reason = reason))
    }

    internal suspend fun revoke(reason: String) = game.deleteXpByReason(reason)

    /** Complete (never un-complete) — safe for automatic paths like health sync. */
    suspend fun completeQuest(id: Long) {
        val q = game.quest(id) ?: return
        if (!q.done) toggleQuest(id, expectDone = false)
    }

    suspend fun toggleQuest(id: Long, expectDone: Boolean? = null) {
        db.withTransaction { toggleQuestTx(id, expectDone) }
        Widgets.refresh(ctx)
    }

    private suspend fun toggleQuestTx(id: Long, expectDone: Boolean?) {
        val q = game.quest(id) ?: return
        if (expectDone != null && q.done != expectDone) return
        val nq = q.copy(done = !q.done)
        game.updateQuest(nq)
        if (nq.done) {
            val xp = (nq.xp * streakMultiplier()).toInt()
            val gold = maxOf(1, nq.xp / 4)
            award(nq.stat, xp, "quest:${nq.id}")
            gainGold(gold, "quest:${nq.id}")
            val all = game.questsForNow(q.date)
            val goldText = if (prefs.extra(Prefs.Extras.VAULT)) " · +$gold Gold" else ""
            if (all.isNotEmpty() && all.all { it.done }) {
                SystemEvents.show(Popup(Popup.Kind.CLEAR, "DAILY QUESTS CLEARED",
                    "Every quest for today is complete.", reward = "+${Rewards.ALL_QUESTS_BONUS} DIS bonus · +$xp ${nq.stat.short}$goldText"))
            } else {
                SystemEvents.show(Popup(Popup.Kind.QUEST, "QUEST COMPLETE", nq.title,
                    reward = "+$xp ${nq.stat.short} EXP$goldText"))
            }
        } else {
            revoke("quest:${nq.id}")
            loseGold("quest:${nq.id}")
        }
        // Bonus for clearing every quest of the day
        val all = game.questsForNow(q.date)
        val bonusKey = "allquests:${q.date}"
        if (all.isNotEmpty() && all.all { it.done }) award(Stat.DISCIPLINE, Rewards.ALL_QUESTS_BONUS, bonusKey)
        else revoke(bonusKey)
        checkAchievements()
    }

    suspend fun addQuest(title: String, stat: Stat, xp: Int, day: Long = today(), detail: String = "", source: String = "manual",
                         autoType: String = "", target: Int = 0) {
        game.insertQuest(Quest(date = day, title = title, detail = detail, stat = stat, xp = xp.coerceIn(5, 100), source = source,
            autoType = autoType, target = target))
        Widgets.refresh(ctx)
    }

    suspend fun deleteQuest(q: Quest, learn: Boolean = true) {
        // Deleting a generated quest without doing it = "not for me": the AI avoids it next time
        if (learn && !q.done && q.source in setOf("ai", "system", "checkin")) prefs.skippedQuests = prefs.skippedQuests + q.title
        db.withTransaction {
            revoke("quest:${q.id}")
            loseGold("quest:${q.id}")
            game.deleteQuest(q)
            val all = game.questsForNow(q.date)
            val bonusKey = "allquests:${q.date}"
            if (all.isNotEmpty() && all.all { it.done }) award(Stat.DISCIPLINE, Rewards.ALL_QUESTS_BONUS, bonusKey)
            else revoke(bonusKey)
        }
        Widgets.refresh(ctx)
    }

    /** Without an API key: a default set, still shaped by what the user told the app (steps, learning topic). */
    private suspend fun defaultQuests(day: Long): List<Quest> {
        val topic = life.skillsNow().firstOrNull { it.status == "learning" }?.title
        val skipped = prefs.skippedQuests.map { it.lowercase() }.toSet()
        val planned = weekPlan()?.day(WeekPlan.todayIndex())
        val training = when {
            planned == null -> Quest(date = day, title = prefs.sportList.firstOrNull()?.let { "Training: $it session (log it in Train)" }
                ?: "Train according to plan", stat = Stat.STRENGTH, xp = 40, source = "system")
            planned.isEmpty() -> Quest(date = day, title = "Rest day: 20 min walk or mobility", stat = Stat.VITALITY, xp = 15, source = "system")
            else -> Quest(date = day, title = "Today: " + planned.joinToString(" + ") { it.title.ifBlank { it.sport } },
                detail = planned.mapNotNull { it.detail.ifBlank { null } }.joinToString("\n"), stat = Stat.STRENGTH, xp = 40,
                source = "system", autoType = "workout", target = planned.sumOf { it.minutes }.coerceAtLeast(0))
                .let { if (it.target == 0) it.copy(autoType = "") else it }
        }
        val warmups = planned.orEmpty().filter { it.warmup.isNotBlank() }
        val warmup = if (warmups.isEmpty()) null else Quest(date = day, title = "Warm-up before training",
            detail = warmups.joinToString(" · ") { it.warmup }, stat = Stat.VITALITY, xp = 10, source = "system")
        return listOfNotNull(
            warmup,
            training,
            Quest(date = day, title = "Walk 8,000 steps", stat = Stat.VITALITY, xp = 20, source = "system", autoType = "steps", target = 8000),
            Quest(date = day, title = "Hit your protein goal (${prefs.proteinGoal} g)", stat = Stat.VITALITY, xp = 20, source = "system"),
            if (topic != null) Quest(date = day, title = "Quiz: answer 10 questions on $topic", stat = Stat.INTELLECT, xp = 30,
                source = "system", autoType = "cards", target = 10)
            else Quest(date = day, title = "30 min focused learning", stat = Stat.INTELLECT, xp = 30, source = "system"),
            Quest(date = day, title = "90 min deep-work block", stat = Stat.DISCIPLINE, xp = 30, source = "system"),
            Quest(date = day, title = "Evening check-in", stat = Stat.SPIRIT, xp = 15, source = "system"),
        ).filter { it.title.lowercase() !in skipped }
    }

    /** How the user actually deals with quests: the AI uses this to tailor difficulty and kind. */
    private suspend fun questProfile(day: Long): String {
        val past = game.questsSince(day - 14).filter { it.date < day }
        val sb = StringBuilder("## How they handle quests (last 14 days)\n")
        if (past.isEmpty()) sb.append("New user: no history yet. Start easy and varied.\n")
        else {
            val rate = past.count { it.done } * 100 / past.size
            sb.append("Completion rate: $rate% (${past.count { it.done }}/${past.size}).\n")
            sb.append("By stat: " + past.groupBy { it.stat }.entries.joinToString { (s, l) -> "${s.short} ${l.count { it.done }}/${l.size}" } + "\n")
            val done = past.filter { it.done }.map { it.title }.distinct().takeLast(12)
            if (done.isNotEmpty()) sb.append("Quests they DID (they like these):\n" + done.joinToString("\n") { "- $it" } + "\n")
            val missed = past.filter { !it.done }.map { it.title }.distinct().takeLast(10)
            if (missed.isNotEmpty()) sb.append("Quests they did NOT do:\n" + missed.joinToString("\n") { "- $it" } + "\n")
        }
        // What they actually use in the app: quests should lean on habits they have, not push features they ignore
        val logs = life.logsSince(day - 13)
        val sessions = logs.mapNotNull { Sports.sportOf(it) }
        val foodDays = life.foodSince(day - 13).map { it.date }.toSet().size
        val setDays = fit.setsSince(day - 13).map { it.date }.toSet().size
        val checkIns = life.recentCheckIns(14).count { it.date >= day - 13 }
        val quizDays = (0..13).count { prefs.cardsReviewed(day - it) > 0 }
        sb.append("\n## What they actually use (last 14 days)\n")
        sb.append("Food logged on $foodDays days · training sessions logged: ${sessions.size}" +
            (if (sessions.isNotEmpty()) " (" + sessions.groupingBy { it }.eachCount().entries.joinToString { "${it.key} ${it.value}" } + ")" else "") +
            " · days with sets logged: $setDays · check-ins: $checkIns · quiz days: $quizDays · journal entries: ${logs.size}\n")
        sb.append("Use this: build on what they do regularly. For things they never use (e.g. food logging), at most one gentle quest " +
            "a few times a week, not daily.\n")
        prefs.skippedQuests.takeIf { it.isNotEmpty() }?.let {
            sb.append("Quests they deleted (don't suggest these or close variants):\n" + it.takeLast(12).joinToString("\n") { t -> "- $t" } + "\n")
        }
        val topics = life.skillsNow().filter { it.status == "learning" }
        if (topics.isNotEmpty()) {
            sb.append("\n## What they're learning right now (Learn tab quiz)\n")
            topics.forEach { t ->
                val cards = db.grow().cardsForTopic(t.title)
                sb.append("- ${t.title}${if (t.notes.isNotBlank()) " (${t.notes})" else ""}: ${cards.size} quiz questions, " +
                    "${cards.count { it.due <= day }} due today\n")
            }
            sb.append("Quiz questions answered today: ${prefs.cardsReviewed(day)}\n")
        }
        if (prefs.questWishes.isNotBlank()) sb.append("\n## The user's own wishes for their quests (follow these)\n${prefs.questWishes}\n")
        return sb.toString()
    }

    /**
     * Builds today's quests. With an API key, Claude designs them from your plans,
     * calendar, recent history and how you've handled past quests. Without one, a default set is used.
     */
    suspend fun generateQuests(force: Boolean = false, instructions: String = ""): String? {
        val day = today()
        val existing = game.questsForNow(day)
        if (!force && prefs.lastQuestGen == day) return null
        if (!prefs.hasKey) {
            if (existing.none { it.source == "system" || it.source == "ai" }) defaultQuests(day).forEach { game.insertQuest(it) }
            prefs.lastQuestGen = day
            Widgets.refresh(ctx)
            return null
        }
        return try {
            val ctxText = buildContext() + "\n" + questProfile(day) +
                (if (instructions.isNotBlank()) "\n## The coach's instructions for today's quests (follow these)\n$instructions\n" else "")
            val res = claude.askJson(
                system = """You design the user's daily quests in a gamified self-improvement app. The quests must fit THIS user, not a generic person.
Stats: STRENGTH (training), VITALITY (nutrition, sleep, health), INTELLECT (learning), DISCIPLINE (work, focus, habits), SPIRIT (mood, reflection, relationships, rest).
Personalise:
- Follow "The user's own wishes" if present: they override the defaults below.
- Use their goals, plans, job, learning topics and memories: name real things from their data (their exercises, their topic, their project), never generic filler.
- Difficulty follows their completion rate: under 50% → 4 easier, shorter quests; 50–85% → keep the level; over 85% → make 1–2 a bit harder.
- More of the kinds of quests they complete; fewer of those they skip; never the ones they deleted.
- Write titles and details in the user's language (see "Language").
Rules:
- 4 to 7 quests for TODAY, concrete and checkable ("Push day: bench 4x8, OHP 3x10…", not "work out").
- If there is a "Weekly sport plan": today's training quest IS today's planned session(s), aligned 1:1 with their plan:
  title = the session name, detail = the COMPLETE workout for today (every exercise with sets × reps × load/stage, or the run's
  distance/pace/intervals). Take it from the weekly plan and, where that is shorter, from their stored Training plan for this day —
  never a shortened or invented version. auto "workout", target = its minutes. Planned rest day → a light recovery quest, no hard training.
- On a training day also add a warm-up quest listed BEFORE the training quest: title "Warm-up: <session>" (in their language,
  but always starting with "Warm-up" or "Aufwärmen"), detail = the warm-up from their plan (weekly plan "warm-up", or their
  stored training plan); if the plan has none, a short 5–10 min warm-up that fits today's session. stat VITALITY, xp 10, auto "".
- Training quests are in THEIR sports (## Training profile → Sports, "In their words"), never a sport they don't do.
  Follow today's training plan / their weekly rhythm; spread their sports over the week; on rest days give a recovery quest.
  If they log sessions by hand, phrase it so one logged session completes it (or use auto "workout" with minutes).
- Include the nutrition target as a quest (e.g. hit protein goal).
- One learning quest: if they have learning topics, make it a quiz round on one of them ("Quiz: 10 questions on <topic>",
  auto "cards", target 8–15, pick the topic with the most due questions). Otherwise tie it to their learning goals.
- One work/focus quest.
- Calendar entries are the user's appointments: fixed, never schedule over them; use the free windows between them and mention times when useful.
- Adapt to what's already done today (steps so far, sets logged, food eaten): don't ask for what's already achieved.
- Use yesterday's check-in to make one quest address what they wanted to improve.
- Match their training level and equipment (## Training profile). On training days make the strength quest follow THEIR plan
  for the day (it may come from an imported spreadsheet) with concrete targets at their current skill stages
  (e.g. "Planche: 6×5s at current stage + 4×3 PPU"). Advanced/elite: don't over-coach basics. Beginners: simple, safe, achievable.
${if (prefs.extra(Prefs.Extras.VAULT) || prefs.extra(Prefs.Extras.MONEY))
    "- If they are saving or tracking spending (## Money), include at most one money quest (no-spend day, move €X to savings, cook instead of ordering) — ideally on 2-3 days per week."
  else "- No money or saving quests (they don't use that part of the app)."}
- xp between 10 and 60 depending on effort.
- Health data syncs automatically. When a quest is measurable, set "auto" so it completes itself:
  "steps" (target = step count), "sleep" (target = minutes of sleep last night), "workout" (target = workout minutes),
  "cards" (target = quiz questions answered today). Otherwise "auto": "".
Return: [{"title": "...", "detail": "short how/when", "stat": "STRENGTH", "xp": 40, "auto": "steps", "target": 8000}, ...]""",
                user = ctxText,
            )
            val arr = res as? JsonArray ?: (res as? JsonObject)?.get("quests")?.jsonArray
                ?: throw ClaudeException("Unexpected quest format")
            if (force) game.clearOpenAiQuests(day)
            // remove untouched default quests when AI takes over
            existing.filter { it.source == "system" && !it.done }.forEach { game.deleteQuest(it) }
            arr.mapNotNull { it as? JsonObject }.forEach { o ->
                val title = str(o, "title") ?: return@forEach
                val auto = str(o, "auto")?.lowercase()?.takeIf { it in setOf("steps", "sleep", "workout", "cards") }.orEmpty()
                game.insertQuest(
                    Quest(date = day, title = title, detail = str(o, "detail").orEmpty(),
                        stat = Stat.parse(str(o, "stat")), xp = int(o, "xp", 20).coerceIn(5, 100), source = "ai",
                        autoType = auto, target = if (auto.isEmpty()) 0 else int(o, "target"))
                )
            }
            prefs.lastQuestGen = day
            Widgets.refresh(ctx)
            null
        } catch (e: Exception) {
            if (game.questsForNow(day).isEmpty()) defaultQuests(day).forEach { game.insertQuest(it) }
            Widgets.refresh(ctx)
            e.message ?: "Quest generation failed"
        }
    }

    // ───────────────────────── Food ─────────────────────────

    suspend fun addFood(name: String, kcal: Int, protein: Int, carbs: Int, fat: Int) {
        val day = today()
        val before = life.foodSince(day).count { it.date == day }
        val entry = FoodEntry(date = day, ts = now(), name = name, kcal = kcal, protein = protein, carbs = carbs, fat = fat)
        val id = life.insertFood(entry)
        if (before < Rewards.FOOD_LOG_DAILY_CAP) award(Stat.VITALITY, Rewards.FOOD_LOG, "food:$id")
        recheckProtein(day)
        Widgets.refresh(ctx)
    }

    suspend fun deleteFood(f: FoodEntry) {
        life.deleteFood(f)
        revoke("food:${f.id}")
        recheckProtein(f.date)
        Widgets.refresh(ctx)
    }

    private suspend fun recheckProtein(day: Long) {
        val p = life.foodSince(day).filter { it.date == day }.sumOf { it.protein }
        val key = "protein:$day"
        if (p >= prefs.proteinGoal) award(Stat.VITALITY, Rewards.PROTEIN_HIT, key) else revoke(key)
    }

    data class Macros(val name: String, val kcal: Int, val protein: Int, val carbs: Int, val fat: Int, val tip: String = "")

    suspend fun estimateFood(description: String): Macros {
        val o = claude.askJson(
            system = "Estimate nutrition for the described food/meal. Use typical portion sizes if unspecified. " +
                "User's daily targets: ${prefs.kcalGoal} kcal, ${prefs.proteinGoal} g protein, goal: ${prefs.bodyGoal}. " +
                foodJsonSpec,
            user = description, maxTokens = 300, small = true,
        ).jsonObject
        return macrosOf(o, description)
    }

    // ───────────────────────── Logs & skills ─────────────────────────

    suspend fun addLog(category: String, text: String) {
        val ts = now()
        life.insertLog(LogEntry(date = today(), ts = ts, category = category, text = text))
        val (stat, xp) = when (category) {
            "work" -> Stat.DISCIPLINE to Rewards.WORK_LOG
            "learned" -> Stat.INTELLECT to Rewards.LEARNED_LOG
            "training" -> Stat.STRENGTH to Rewards.TRAINING_LOG
            else -> Stat.SPIRIT to 5
        }
        game.insertXp(XpEvent(ts = ts, stat = stat, amount = xp, reason = "log:$ts"))
        Widgets.refresh(ctx)
    }

    suspend fun deleteLog(l: LogEntry) {
        life.deleteLog(l)
        revoke("log:${l.ts}")
        Widgets.refresh(ctx)
    }

    suspend fun addSkill(title: String, area: String = "", status: String = "todo") {
        val id = life.insertSkill(SkillItem(title = title, area = area, status = status, createdAt = now(),
            learnedAt = if (status == "learned") now() else null))
        if (status == "learned") award(Stat.INTELLECT, Rewards.SKILL_LEARNED, "skill:$id")
        Widgets.refresh(ctx)
    }

    suspend fun setSkillStatus(s: SkillItem, status: String) {
        life.updateSkill(s.copy(status = status, learnedAt = if (status == "learned") now() else null))
        if (status == "learned") award(Stat.INTELLECT, Rewards.SKILL_LEARNED, "skill:${s.id}")
        else revoke("skill:${s.id}")
        Widgets.refresh(ctx)
    }

    suspend fun updateSkillNotes(s: SkillItem, notes: String) = life.updateSkill(s.copy(notes = notes))

    suspend fun deleteSkill(s: SkillItem) {
        revoke("skill:${s.id}")
        life.deleteSkill(s)
        Widgets.refresh(ctx)
    }

    /** Ask Claude for a learning roadmap and add it to the skill tree. */
    suspend fun suggestSkills(): String? = try {
        val arr = claude.askJson(
            system = "Suggest the next 5 concrete things the user should learn, ordered, based on their work, " +
                "learning goals, and what they already know. Avoid duplicates of existing skill items. " +
                "Return [{\"title\": \"...\", \"area\": \"e.g. Kubernetes\", \"why\": \"one line\"}].",
            user = buildContext(lite = true) + "\n## Already in the skill tree\n" +
                life.skillsNow().joinToString("\n") { "- [${it.status}] ${it.title}" },
        ) as? JsonArray ?: JsonArray(emptyList())
        arr.mapNotNull { it as? JsonObject }.forEach { o ->
            val t = str(o, "title") ?: return@forEach
            life.insertSkill(SkillItem(title = t, area = str(o, "area").orEmpty(),
                notes = str(o, "why").orEmpty(), createdAt = now()))
        }
        null
    } catch (e: Exception) { e.message }

    // ───────────────────────── Check-in ─────────────────────────

    suspend fun saveCheckIn(c: CheckIn): String? {
        life.upsertCheckIn(c)
        award(Stat.SPIRIT, Rewards.CHECK_IN, "checkin:${c.date}")
        // Auto-complete a check-in quest if there is one
        game.questsForNow(c.date).firstOrNull { !it.done && it.title.contains("check-in", true) }
            ?.let { completeQuest(it.id) }
        Widgets.refresh(ctx)
        if (!prefs.hasKey) return null
        return try {
            val o = claude.askJson(
                system = """You are the user's personal coach. They just did their evening check-in.
Write a short, honest, warm reflection (3-5 sentences): name a pattern you see across recent days if any,
acknowledge what went well, and give ONE specific, small improvement for tomorrow.
Also create one quest for tomorrow that implements that improvement.
Return {"reflection": "...", "tomorrow_quest": {"title": "...", "detail": "...", "stat": "SPIRIT", "xp": 25}}""",
                user = buildContext(lite = true) + "\n\n## Tonight's check-in\nMood ${c.mood}/5, energy ${c.energy}/5, " +
                    "sleep ${c.sleepHours}h\nDay: ${c.dayText}\nWent well: ${c.wentWell}\nWould improve: ${c.improve}",
            ).jsonObject
            life.upsertCheckIn(c.copy(reflection = str(o, "reflection").orEmpty()))
            (o["tomorrow_quest"] as? JsonObject)?.let { q ->
                val t = str(q, "title")
                if (t != null) {
                    game.clearOpenQuestsBySource(c.date + 1, "checkin")
                    addQuest(t, Stat.parse(str(q, "stat")), int(q, "xp", 25),
                        day = c.date + 1, detail = str(q, "detail").orEmpty(), source = "checkin")
                }
            }
            null
        } catch (e: Exception) { e.message }
    }

    // ───────────────────────── Discover (music + videos) ─────────────────────────

    suspend fun generateDiscover(force: Boolean = false): String? {
        val day = today()
        if (!force && prefs.lastDiscoverGen == day) return null
        if (!prefs.hasKey) return "Add your API key to get daily discoveries."
        return try {
            val past = life.discoverSince(day - 21).joinToString("\n") { "- ${it.title} — ${it.subtitle}" }
            val o = claude.askJson(
                system = """You curate a short daily "learn & grow" feed for the user's whole life, not just sport.
1) 3 videos/talks for their WORK and LEARNING goals (see Work and Learning plans): well-known conference talks, channels,
   courses or lectures that really exist, matched to their level and preferred learning style. Prefer what's useful this week
   (e.g. tied to today's quests or a skill they're learning). At most one may be about training/health.
2) 2 tools, techniques or resources they could use at work or in life right now (a CLI tool, library, framework, app, method,
   book or free course that really exists). Each with a concrete first step they can try today in "why".
3) ${if (life.plansNow().any { it.type == PlanType.MUSIC && it.content.isNotBlank() }) "3 songs (they listen on YouTube Music) they probably don't know yet but will like, based on their music taste. Real songs only." else "No songs (no music taste stored): return an empty songs list."}
Never repeat anything from the 'already suggested' list. Match the user's language for the "why" lines.
Return {"videos":[{"title":"...","channel":"...","why":"one short line","query":"youtube search query"}],
        "tools":[{"name":"...","category":"e.g. CLI tool / book / method","why":"what it helps with + first step","query":"web search query"}],
        "songs":[{"title":"...","artist":"...","why":"one short line"}]}""",
                user = buildContext(lite = true) + "\n\n## Already suggested (don't repeat)\n" + past.ifBlank { "(none)" }, maxTokens = 1200,
            ).jsonObject
            val items = mutableListOf<DiscoverItem>()
            o["songs"]?.jsonArray?.mapNotNull { it as? JsonObject }?.forEach { s ->
                val t = str(s, "title") ?: return@forEach
                val a = str(s, "artist").orEmpty()
                items += DiscoverItem(date = day, kind = "song", title = t, subtitle = a,
                    why = str(s, "why").orEmpty(), query = "$t $a")
            }
            o["videos"]?.jsonArray?.mapNotNull { it as? JsonObject }?.forEach { v ->
                val t = str(v, "title") ?: return@forEach
                val ch = str(v, "channel").orEmpty()
                items += DiscoverItem(date = day, kind = "video", title = t, subtitle = ch,
                    why = str(v, "why").orEmpty(), query = str(v, "query") ?: "$t $ch")
            }
            o["tools"]?.jsonArray?.mapNotNull { it as? JsonObject }?.forEach { v ->
                val t = str(v, "name") ?: return@forEach
                items += DiscoverItem(date = day, kind = "tool", title = t, subtitle = str(v, "category").orEmpty(),
                    why = str(v, "why").orEmpty(), query = str(v, "query") ?: t)
            }
            if (items.isNotEmpty()) {
                life.clearDiscover(day)
                life.insertDiscover(items)
            }
            prefs.lastDiscoverGen = day
            null
        } catch (e: Exception) { e.message }
    }

    // ───────────────────────── Coach chat with tools ─────────────────────────

    private val allChatTools: JsonArray by lazy {
        val stats = "One of STRENGTH, VITALITY, INTELLECT, DISCIPLINE, SPIRIT"
        JsonArray(listOf(
            Claude.tool("update_plan",
                "Save or replace one of the user's stored plans. Use whenever they paste or describe a plan, goals or music taste. Store the full cleaned-up content, merging with the existing plan if they only change part of it.",
                mapOf("type" to ("string" to "One of: goals, training, nutrition, work, learning, music"),
                    "content" to ("string" to "Full plan text to store")),
                listOf("type", "content")),
            Claude.tool("add_quest", "Add a quest to the user's quest list.",
                mapOf("title" to ("string" to "Short, concrete quest"),
                    "detail" to ("string" to "Optional how/when"),
                    "stat" to ("string" to stats),
                    "xp" to ("integer" to "10-60"),
                    "date" to ("string" to "today, tomorrow, or YYYY-MM-DD")),
                listOf("title", "stat", "xp")),
            Claude.tool("log_food", "Log food the user says they ate today. Estimate macros if not given.",
                mapOf("name" to ("string" to "Food"), "kcal" to ("integer" to "kcal"),
                    "protein" to ("integer" to "g"), "carbs" to ("integer" to "g"), "fat" to ("integer" to "g")),
                listOf("name", "kcal", "protein", "carbs", "fat")),
            Claude.tool("add_log", "Log something the user did or learned today.",
                mapOf("category" to ("string" to "One of: work, learned, training, note"),
                    "text" to ("string" to "What happened")),
                listOf("category", "text")),
            Claude.tool("add_skill", "Add an item to the user's skill tree (things to learn / learning / learned).",
                mapOf("title" to ("string" to "Skill/topic"), "area" to ("string" to "Area, e.g. Kubernetes"),
                    "status" to ("string" to "todo, learning, or learned")),
                listOf("title")),
            Claude.tool("save_memory", "Remember a lasting fact about the user (preferences, constraints, injuries, schedule, values).",
                mapOf("text" to ("string" to "The fact, one sentence")),
                listOf("text")),
            Claude.tool("log_session", "Log a training session of any sport (run, ride, swim, football, gym session, yoga…).",
                mapOf("sport" to ("string" to "The sport, ideally one of the user's sports"),
                    "minutes" to ("integer" to "Duration in minutes"),
                    "km" to ("number" to "Optional distance in km"),
                    "note" to ("string" to "Optional short note (pace, how it felt)")),
                listOf("sport", "minutes")),
            Claude.tool("log_set", "Log a strength training set (one call per set).",
                mapOf("exercise" to ("string" to "Exercise name, e.g. Bench Press"),
                    "weight_kg" to ("number" to "Weight in kg (0 for bodyweight)"),
                    "reps" to ("integer" to "Reps"),
                    "sets" to ("integer" to "How many identical sets (default 1)")),
                listOf("exercise", "weight_kg", "reps")),
            Claude.tool("log_hold", "Log a calisthenics static hold (planche, front lever…). Use names like 'Planche · Tuck' or 'Front lever · Advanced tuck' so progress is tracked.",
                mapOf("exercise" to ("string" to "e.g. Planche · Advanced tuck"),
                    "seconds" to ("integer" to "Seconds held"),
                    "sets" to ("integer" to "How many holds of that length (default 1)")),
                listOf("exercise", "seconds")),
            Claude.tool("add_reward", "Add a real-life reward the user wants to earn (e.g. a new desk). It unlocks with Gold + saved money.",
                mapOf("name" to ("string" to "Reward"), "price_eur" to ("number" to "Price in €"),
                    "condition" to ("string" to "Optional: level:N, ach:<id>, or cali:<planche|frontlever|backlever|handstand|muscleup|hspu>:<stage index 0-5>")),
                listOf("name", "price_eur")),
            Claude.tool("log_expense", "Record money the user spent (not savings).",
                mapOf("amount_eur" to ("number" to "Amount in €"),
                    "category" to ("string" to "One of: ${Growth.categories.joinToString()}"),
                    "note" to ("string" to "What it was")),
                listOf("amount_eur", "category")),
            Claude.tool("log_savings", "Record money the user moved to savings, or a purchase they skipped.",
                mapOf("amount_eur" to ("number" to "Amount in €"), "note" to ("string" to "What for / what they skipped")),
                listOf("amount_eur")),
            Claude.tool("log_weight", "Log the user's body weight for today.",
                mapOf("weight_kg" to ("number" to "Body weight in kg"),
                    "waist_cm" to ("number" to "Optional waist in cm")),
                listOf("weight_kg")),
            Claude.tool("set_nutrition_goals", "Update the user's daily nutrition targets.",
                mapOf("kcal" to ("integer" to "kcal"), "protein" to ("integer" to "g"),
                    "carbs" to ("integer" to "g"), "fat" to ("integer" to "g")),
                listOf("kcal", "protein", "carbs", "fat")),
            Claude.tool("edit_quest", "Change one of the user's quests (use its #id from the quest list). Only the fields you send change.",
                mapOf("id" to ("integer" to "Quest id, the number after #"),
                    "title" to ("string" to "New title"),
                    "detail" to ("string" to "New how/when"),
                    "stat" to ("string" to stats),
                    "xp" to ("integer" to "10-60"),
                    "auto" to ("string" to "Auto-complete: steps, sleep, workout, cards, or none"),
                    "target" to ("integer" to "Target for auto: step count, sleep minutes, workout minutes or quiz questions")),
                listOf("id")),
            Claude.tool("remove_quest", "Remove one of the user's quests (use its #id). Use when they ask, or when a quest no longer fits (e.g. training moved to another day).",
                mapOf("id" to ("integer" to "Quest id, the number after #")),
                listOf("id")),
            Claude.tool("regenerate_quests", "Throw away today's open generated quests and design a fresh set. Completed and hand-made quests stay.",
                mapOf("instructions" to ("string" to "Optional: what the new set should take into account (e.g. 'sick today, keep it light')")),
                emptyList()),
            Claude.tool("set_quest_wishes", "Save the user's lasting wishes for their daily quests (e.g. 'no food quests', 'always one reading quest'). Replaces the stored wishes, so include the existing ones they still want.",
                mapOf("text" to ("string" to "All their quest wishes")),
                listOf("text")),
            weekPlanTool,
            Claude.tool("update_training_profile", "Change the user's training profile. Only the fields you send change.",
                mapOf("sports" to ("string" to "Comma-separated list of ALL their sports. Known names: ${Sports.all.joinToString { it.name }}"),
                    "notes" to ("string" to "Their training in their own words (goals, rhythm, constraints)"),
                    "equipment" to ("string" to "Equipment they have")),
                emptyList()),
            Claude.tool("set_stat_points",
                "Fix the user's stat points (from level-ups). Send the stats to change with their NEW point count; the total can't exceed the points earned (see ## Stat points). Use to undo a mis-tap or redistribute when they ask.",
                mapOf("STR" to ("integer" to "Points in Strength"), "VIT" to ("integer" to "Points in Vitality"),
                    "INT" to ("integer" to "Points in Intellect"), "DIS" to ("integer" to "Points in Discipline"),
                    "SPI" to ("integer" to "Points in Spirit"), "reason" to ("string" to "Short reason")),
                listOf("reason")),
            Claude.tool("adjust_stat_exp",
                "Correct a stat's EXP when something went wrong (e.g. EXP missing for a finished quest, or counted twice). Positive adds, negative removes. Only for real errors the user confirms, not as a reward.",
                mapOf("stat" to ("string" to stats), "amount" to ("integer" to "EXP to add (or remove if negative), -1000..1000"),
                    "reason" to ("string" to "What is being fixed")),
                listOf("stat", "amount", "reason")),
            Claude.tool("add_calendar_event", "Create an event in the user's Google Calendar (e.g. to schedule a workout or study block).",
                mapOf("title" to ("string" to "Event title"),
                    "start" to ("string" to "Local start time, format YYYY-MM-DDTHH:MM"),
                    "minutes" to ("integer" to "Duration in minutes"),
                    "description" to ("string" to "Optional details")),
                listOf("title", "start", "minutes")),
        ))
    }

    /** Weekly schedule tool: nested sessions, so it's declared by hand. */
    private val weekPlanTool: JsonObject by lazy {
        fun prop(type: String, desc: String) = buildJsonObject { put("type", type); put("description", desc) }
        fun strs(vararg s: String) = JsonArray(s.map { JsonPrimitive(it) })
        buildJsonObject {
            put("name", "update_week_plan")
            put("description", "Change the user's weekly training schedule (the plan in the Train tab; daily training quests follow it). " +
                "Send only the days that change, each with its COMPLETE new list of sessions; an empty list makes it a rest day. " +
                "To move a session, send both days. Set replace_all to rebuild the whole week (days not sent become rest days).")
            put("input_schema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("days", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("day", buildJsonObject {
                                    put("type", "string"); put("enum", strs(*WeekPlan.names.toTypedArray()))
                                })
                                put("sessions", buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject {
                                        put("type", "object")
                                        put("properties", buildJsonObject {
                                            put("sport", prop("string", "One of their sports"))
                                            put("title", prop("string", "Short, e.g. 'Push day', 'Easy run 6 km'"))
                                            put("minutes", prop("integer", "Duration"))
                                            put("detail", prop("string", "The full session content, max 300 chars (exercises x sets x reps, pace...)"))
                                            put("warmup", prop("string", "Warm-up for this session, max 150 chars"))
                                        })
                                        put("required", strs("sport", "title"))
                                    })
                                })
                            })
                            put("required", strs("day", "sessions"))
                        })
                    })
                    put("replace_all", prop("boolean", "true = the days sent are the whole week"))
                })
                put("required", strs("days"))
            })
        }
    }

    /**
     * The calendar is read-only (appointments) unless the user allowed the coach to add events.
     * Reward / savings / spending tools only when those extras are on.
     */
    private fun chatTools(): JsonArray {
        val off = buildSet {
            if (!prefs.calendarWrite) add("add_calendar_event")
            if (!prefs.extra(Prefs.Extras.VAULT)) { add("add_reward"); add("log_savings") }
            if (!prefs.extra(Prefs.Extras.MONEY)) add("log_expense")
            if (!prefs.extra(Prefs.Extras.POINTS)) add("set_stat_points")
        }
        return JsonArray(allChatTools.filter { str(it as? JsonObject, "name") !in off })
    }

    internal suspend fun runTool(name: String, input: JsonObject): String = try {
        when (name) {
            "update_plan" -> {
                val type = str(input, "type")?.lowercase() ?: "goals"
                life.upsertPlan(Plan(type, str(input, "content").orEmpty(), now()))
                "✓ Saved ${PlanType.label(type).lowercase()}"
            }
            "add_quest" -> {
                val d = when (val s = str(input, "date")?.lowercase()) {
                    null, "", "today" -> today()
                    "tomorrow" -> today() + 1
                    else -> runCatching { LocalDate.parse(s).toEpochDay() }.getOrDefault(today())
                }
                addQuest(str(input, "title") ?: "Quest", Stat.parse(str(input, "stat")), int(input, "xp", 20),
                    day = d, detail = str(input, "detail").orEmpty(), source = "ai")
                "✓ Quest added: ${str(input, "title")}"
            }
            "edit_quest" -> {
                val q = game.quest(int(input, "id", -1).toLong())
                when {
                    q == null -> "✗ No quest #${int(input, "id")}"
                    q.done -> "✗ \"${q.title}\" is already done; it can't be changed"
                    else -> {
                        val auto = str(input, "auto")?.lowercase()?.let { if (it in setOf("steps", "sleep", "workout", "cards")) it else "" }
                        val nq = q.copy(
                            title = str(input, "title")?.takeIf { it.isNotBlank() } ?: q.title,
                            detail = str(input, "detail") ?: q.detail,
                            stat = str(input, "stat")?.let { Stat.parse(it) } ?: q.stat,
                            xp = if (input.containsKey("xp")) int(input, "xp", q.xp).coerceIn(5, 100) else q.xp,
                            autoType = auto ?: q.autoType,
                            target = if (input.containsKey("target")) int(input, "target") else q.target,
                        ).let { if (it.autoType.isEmpty()) it.copy(target = 0) else it }
                        game.updateQuest(nq)
                        Widgets.refresh(ctx)
                        "✓ Quest changed: ${nq.title}"
                    }
                }
            }
            "remove_quest" -> {
                val q = game.quest(int(input, "id", -1).toLong())
                if (q == null) "✗ No quest #${int(input, "id")}" else {
                    deleteQuest(q, learn = false)
                    "✓ Quest removed: ${q.title}"
                }
            }
            "regenerate_quests" -> {
                generateQuests(force = true, instructions = str(input, "instructions").orEmpty())
                    ?.let { "✗ Couldn't make new quests: $it" }
                    ?: ("✓ New quests for today:\n" + game.questsForNow(today()).filter { !it.done }
                        .joinToString("\n") { "#${it.id} ${it.title}" })
            }
            "set_quest_wishes" -> {
                prefs.questWishes = str(input, "text").orEmpty().trim()
                "✓ Quest wishes saved (used from the next quest set)"
            }
            "update_week_plan" -> {
                val replace = str(input, "replace_all")?.toBoolean() == true
                var plan = if (replace) WeekPlan() else weekPlan() ?: WeekPlan()
                val changed = mutableListOf<String>()
                (input["days"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEach { d ->
                    val i = WeekPlan.names.indexOfFirst { n -> (str(d, "day") ?: "").take(3).equals(n, true) }
                    if (i < 0) return@forEach
                    val sessions = (d["sessions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { s ->
                        PlanSession(sport = str(s, "sport") ?: prefs.sportList.firstOrNull() ?: "Training",
                            title = str(s, "title").orEmpty(), minutes = int(s, "minutes"), detail = str(s, "detail").orEmpty(),
                            warmup = str(s, "warmup").orEmpty())
                    }
                    plan = plan.withDay(i, sessions)
                    changed += WeekPlan.names[i] + ": " + sessions.joinToString("; ") { it.label() }.ifBlank { "rest" }
                }
                if (changed.isEmpty()) "✗ No valid days sent" else {
                    saveWeekPlan(plan)
                    "✓ Week plan updated\n" + changed.joinToString("\n")
                }
            }
            "update_training_profile" -> {
                str(input, "sports")?.let { s ->
                    prefs.sports = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        .map { n -> Sports.all.firstOrNull { it.name.equals(n, true) }?.name ?: n }.distinct().joinToString(",")
                    // Calisthenics skill tracking only for people who do calisthenics
                    if (!Sports.usesLadders(prefs.sportList)) prefs.trackedLadders = ""
                    else if (prefs.trackedLadders.isBlank()) prefs.trackedLadders = "planche,frontlever"
                }
                str(input, "notes")?.let { prefs.sportNotes = it.trim() }
                str(input, "equipment")?.let { prefs.equipment = it.trim() }
                "✓ Training profile: ${prefs.sportList.joinToString()}"
            }
            "log_food" -> {
                addFood(str(input, "name") ?: "Food", int(input, "kcal"), int(input, "protein"),
                    int(input, "carbs"), int(input, "fat"))
                "✓ Logged ${str(input, "name")} (${int(input, "kcal")} kcal, ${int(input, "protein")}g protein)"
            }
            "add_log" -> {
                addLog(str(input, "category") ?: "note", str(input, "text").orEmpty())
                "✓ Logged ${str(input, "category")}"
            }
            "add_skill" -> {
                addSkill(str(input, "title") ?: "Skill", str(input, "area").orEmpty(), str(input, "status") ?: "todo")
                "✓ Skill tree: ${str(input, "title")}"
            }
            "save_memory" -> {
                life.insertMemory(Memory(ts = now(), text = str(input, "text").orEmpty()))
                "✓ Remembered"
            }
            "log_session" -> {
                val sport = str(input, "sport") ?: "Training"
                val min = int(input, "minutes").coerceIn(1, 600)
                logSession(sport, min, str(input, "km")?.toFloatOrNull(), str(input, "note").orEmpty())
                "✓ Logged ${Sports.label(sport, min, str(input, "km")?.toFloatOrNull(), "")}"
            }
            "log_set" -> {
                val n = int(input, "sets", 1).coerceIn(1, 10)
                val w = str(input, "weight_kg")?.toFloatOrNull() ?: 0f
                repeat(n) { addSet(str(input, "exercise") ?: "Exercise", w, int(input, "reps")) }
                "✓ Logged $n× ${str(input, "exercise")} ${w}kg × ${int(input, "reps")}"
            }
            "log_hold" -> {
                val n = int(input, "sets", 1).coerceIn(1, 10)
                val sec = int(input, "seconds")
                repeat(n) { addCaliSet(str(input, "exercise") ?: "Hold", sec, 0) }
                "✓ Logged $n× ${str(input, "exercise")} ${sec}s"
            }
            "add_reward" -> {
                val price = str(input, "price_eur")?.toFloatOrNull() ?: 0f
                addReward(str(input, "name") ?: "Reward", price, condition = str(input, "condition").orEmpty())
                "✓ Reward added: ${str(input, "name")} (€${"%.0f".format(price)}, ${defaultGoldCost(price)} Gold)"
            }
            "log_expense" -> {
                val a = str(input, "amount_eur")?.toFloatOrNull() ?: throw IllegalArgumentException("amount")
                val cat = str(input, "category")?.let { c -> Growth.categories.firstOrNull { it.equals(c, true) } } ?: "Other"
                addExpense(a, cat, str(input, "note").orEmpty())
                "✓ Spent €${"%.2f".format(a)} ($cat)"
            }
            "log_savings" -> {
                val a = str(input, "amount_eur")?.toFloatOrNull() ?: 0f
                deposit(a, str(input, "note").orEmpty())
                "✓ Saved €${"%.2f".format(a)}"
            }
            "set_stat_points" -> {
                val level = Leveling.levelFor(Leveling.totals(game.statTotalsNow()).values.sum())
                val earned = Growth.pointsEarned(level)
                val next = Stat.entries.associateWith { st ->
                    if (input.containsKey(st.short)) int(input, st.short, prefs.points(st)).coerceAtLeast(0) else prefs.points(st)
                }
                if (next.values.sum() > earned) "✗ That's ${next.values.sum()} points, but only $earned are earned at level $level"
                else {
                    next.forEach { (st, n) -> prefs.setPoints(st, n) }
                    "✓ Stat points: " + next.entries.joinToString(", ") { "${it.key.short} ${it.value}" } +
                        " (${earned - next.values.sum()} free)"
                }
            }
            "adjust_stat_exp" -> {
                val st = Stat.parse(str(input, "stat"))
                val amt = int(input, "amount").coerceIn(-1000, 1000)
                if (amt == 0) "✗ Amount is 0" else {
                    // Raw correction: no stat-point boost, its own reason so it never replaces other EXP
                    game.insertXp(XpEvent(ts = now(), stat = st, amount = amt, reason = "fix:${now()}:${str(input, "reason").orEmpty().take(60)}"))
                    Widgets.refresh(ctx)
                    "✓ ${st.short} ${if (amt > 0) "+" else ""}$amt EXP (${str(input, "reason").orEmpty()})"
                }
            }
            "log_weight" -> {
                val w = str(input, "weight_kg")?.toFloatOrNull() ?: throw IllegalArgumentException("weight")
                logBody(w, str(input, "waist_cm")?.toFloatOrNull())
                "✓ Weight logged: $w kg"
            }
            "set_nutrition_goals" -> {
                prefs.kcalGoal = int(input, "kcal", prefs.kcalGoal)
                prefs.proteinGoal = int(input, "protein", prefs.proteinGoal)
                prefs.carbsGoal = int(input, "carbs", prefs.carbsGoal)
                prefs.fatGoal = int(input, "fat", prefs.fatGoal)
                "✓ Nutrition goals: ${prefs.kcalGoal} kcal, P${prefs.proteinGoal} C${prefs.carbsGoal} F${prefs.fatGoal}"
            }
            "add_calendar_event" -> {
                val start = LocalDateTime.parse(str(input, "start") ?: throw IllegalArgumentException("missing start"))
                val ok = CalendarRepo.addEvent(ctx, str(input, "title") ?: "TopLevel", start,
                    int(input, "minutes", 60), str(input, "description").orEmpty())
                if (ok) "✓ Added to calendar: ${str(input, "title")}" else
                    "✗ Couldn't add to calendar (permission missing or no writable calendar)"
            }
            else -> "Unknown tool $name"
        }
    } catch (e: Exception) { "✗ $name failed: ${e.message}" }

    /** Sends a chat message, runs tools, stores the conversation. */
    suspend fun chat(userText: String): String? {
        life.insertChat(ChatMessage(ts = now(), role = "user", content = userText))
        return try {
            val system = """You are the user's personal coach and daily supporter inside TopLevel, their gamified life app.
You help with their whole life: work and career (see their Work plan), learning, training, nutrition, money, mood and becoming a better person.
For work and learning questions give concrete, technical, current answers: tools, commands, resources, videos worth watching.
Style: direct, warm, practical, no fluff. Short answers on a phone screen unless they ask for depth. Use their data below.
When the user shares information worth keeping (a plan, a goal, food they ate, something learned, a preference), STORE it with your tools without asking — then mention briefly what you saved.
If they paste a training or eating plan, save it with update_plan (for training, also update_week_plan so the schedule matches).
You can change the app for them, not just advise. When they ask for a change, or agree to one you proposed, MAKE it with your tools instead of explaining how:
- Quests: edit_quest / remove_quest / add_quest by #id, or regenerate_quests for a fresh set (sick, travelling, busy day…). Lasting wishes → set_quest_wishes.
- Training schedule: update_week_plan (move, swap, add or drop sessions, deload week, new plan). If today's sessions change, fix today's training quest too.
- Sports / equipment: update_training_profile. Nutrition targets: set_nutrition_goals.
Small, clearly wanted changes: just do them. Big changes they didn't ask for (rewriting the whole week, replacing all quests): propose first, act after they agree.
Their calendar holds their appointments: treat them as fixed and plan around them.${if (!prefs.calendarWrite) " You can't add calendar events; suggest times instead." else ""}
Match their training level and equipment (see Training profile); adapt to what they've already done today (steps, sets, food logged)."""
            // Changes every message (time, food, quests): kept out of the cached part above
            val dynamic = buildContext() + if (prefs.questWishes.isNotBlank()) "\n## Their stored quest wishes\n${prefs.questWishes}\n" else ""
            val history = life.recentChat(12)
            val msgs = mutableListOf<JsonObject>()
            // API requires alternating roles starting with user; merge consecutive same-role messages.
            history.forEach { m ->
                val role = if (m.role == "assistant") "assistant" else "user"
                val last = msgs.lastOrNull()
                if (last != null && str(last, "role") == role) {
                    msgs[msgs.size - 1] = buildJsonObject {
                        put("role", role)
                        put("content", str(last, "content") + "\n\n" + m.content)
                    }
                } else msgs += buildJsonObject { put("role", role); put("content", m.content) }
            }
            while (msgs.isNotEmpty() && str(msgs.first(), "role") != "user") msgs.removeAt(0)

            val convo = ArrayList<JsonElement>(msgs)
            val actions = mutableListOf<String>()
            var finalText = ""
            for (round in 0 until 8) {
                val res = claude.send(system, JsonArray(convo), chatTools(), maxTokens = 4000, cache = true, dynamic = dynamic)
                val content = res["content"]?.jsonArray ?: JsonArray(emptyList())
                val text = Claude.textOf(res)
                if (text.isNotBlank()) finalText = text
                val toolUses = content.mapNotNull { it as? JsonObject }
                    .filter { str(it, "type") == "tool_use" }
                if (toolUses.isEmpty() || str(res, "stop_reason") != "tool_use") break
                convo += buildJsonObject { put("role", "assistant"); put("content", content) }
                val results = buildJsonArray {
                    toolUses.forEach { tu ->
                        val out = runTool(str(tu, "name").orEmpty(), tu["input"]?.jsonObject ?: JsonObject(emptyMap()))
                        actions += out
                        add(buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", str(tu, "id").orEmpty())
                            put("content", out)
                        })
                    }
                }
                convo += buildJsonObject { put("role", "user"); put("content", results) }
            }
            val stored = buildString {
                append(finalText.ifBlank { "Done." })
                if (actions.isNotEmpty()) append("\n\n").append(actions.joinToString("\n"))
            }
            life.insertChat(ChatMessage(ts = now(), role = "assistant", content = stored))
            null
        } catch (e: Exception) {
            e.message ?: "Something went wrong"
        }
    }

    // ───────────────────────── Context for Claude ─────────────────────────

    suspend fun buildContext(lite: Boolean = false): String {
        val today = LocalDate.now()
        val day = today.toEpochDay()
        val sb = StringBuilder()
        val dow = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        sb.append("## Now\n$dow, ${today.format(DateTimeFormatter.ISO_DATE)}, ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))).append("\n")
        sb.append("Language: ${Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)} (phone setting; if they write to you in another language, use theirs)\n")
        if (prefs.name.isNotBlank()) sb.append("Name: ${prefs.name}\n")

        val totals = Leveling.totals(game.statTotalsNow())
        val total = totals.values.sum()
        val p = Leveling.progress(total)
        sb.append("\n## Level\nLevel ${p.level} (rank ${Leveling.rank(p.level)}), ${total} XP. Stats: ")
            .append(totals.entries.joinToString(", ") { "${it.key.short} ${Leveling.statLevel(it.value)}" }).append("\n")

        if (prefs.extra(Prefs.Extras.POINTS)) {
            val earned = Growth.pointsEarned(p.level)
            sb.append("\n## Stat points\nEarned $earned (${Growth.POINTS_PER_LEVEL} per level), spent: ")
                .append(Stat.entries.joinToString(", ") { "${it.short} ${prefs.points(it)}" })
                .append(". Free: ${earned - prefs.pointsSpent()}. Each point = +${(Growth.BOOST_PER_POINT * 100).toInt()}% EXP for that stat (max ×2).\n")
        }
        sb.append("Stat EXP totals: ").append(totals.entries.joinToString(", ") { "${it.key.short} ${it.value}" }).append("\n")

        sb.append("\n## Nutrition goals\n${prefs.kcalGoal} kcal, ${prefs.proteinGoal}g protein, " +
            "${prefs.carbsGoal}g carbs, ${prefs.fatGoal}g fat per day (body goal: ${prefs.bodyGoal})\n")

        sb.append("\n## Training profile\n")
        sb.append("Sports: ${prefs.sportList.joinToString().ifBlank { "not set yet" }}\n")
        if (prefs.sportNotes.isNotBlank()) sb.append("In their words: ${prefs.sportNotes}\n")
        weekPlan()?.let { wp ->
            sb.append("\n## Weekly sport plan (today is ${WeekPlan.names[WeekPlan.todayIndex()]})\n${wp.describe()}\n")
        }
        sb.append("Level: ${Prefs.Levels.trainingLevels.getOrNull(prefs.trainingLevel)?.let { "${it.first} (${it.second})" } ?: "unknown"}\n")
        if (prefs.equipment.isNotBlank()) sb.append("Equipment available: ${prefs.equipment}\n")
        if (prefs.heightCm > 0) sb.append("Body: ${prefs.heightCm} cm, ${prefs.age} y, ${if (prefs.sex == "f") "female" else "male"}\n")

        val plans = life.plansNow().filter { it.content.isNotBlank() }
        if (plans.isNotEmpty()) {
            sb.append("\n## Stored plans\n")
            // Trimmed: the training schedule is already in "Weekly sport plan"; long plans were the biggest token cost
            plans.forEach { sb.append("### ${PlanType.label(it.type)}\n${it.content.take(if (lite) 700 else if (it.type == PlanType.TRAINING) 2500 else 1500)}\n\n") }
        }

        val mems = life.memoriesNow()
        if (mems.isNotEmpty()) {
            sb.append("\n## Things to remember about the user\n")
            mems.take(if (lite) 15 else 30).forEach { sb.append("- ${it.text}\n") }
        }

        if (prefs.useCalendar) {
            val evs = CalendarRepo.eventsFor(ctx, today, 2)
            if (evs.isNotEmpty()) {
                sb.append("\n## Appointments from calendar (today + tomorrow; fixed, plan around them)\n")
                val df = DateTimeFormatter.ofPattern("EEE")
                evs.take(25).forEach {
                    val d = java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    sb.append("- ${d.format(df)} ${it.timeLabel()}: ${it.title}\n")
                }
            }
        }

        fun questLine(q: Quest) = "- #${q.id} [${if (q.done) "x" else " "}] ${q.title}" +
            (if (q.detail.isNotBlank()) " — ${q.detail.take(120)}" else "") +
            " (${q.stat.short} +${q.xp}${if (q.autoType.isNotEmpty()) ", auto ${q.autoType} ${q.target}" else ""})\n"
        val quests = game.questsForNow(day)
        if (quests.isNotEmpty()) {
            sb.append("\n## Today's quests\n")
            quests.forEach { sb.append(questLine(it)) }
        }
        val tomorrowQuests = if (lite) emptyList() else game.questsForNow(day + 1)
        if (tomorrowQuests.isNotEmpty()) {
            sb.append("\n## Tomorrow's quests so far\n")
            tomorrowQuests.forEach { sb.append(questLine(it)) }
        }

        val food = life.foodSince(day - 6)
        val todayFood = food.filter { it.date == day }
        sb.append("\n## Food today\n")
        if (todayFood.isEmpty()) sb.append("(nothing logged yet)\n")
        todayFood.forEach { sb.append("- ${it.name}: ${it.kcal} kcal, P${it.protein} C${it.carbs} F${it.fat}\n") }
        sb.append("Total: ${todayFood.sumOf { it.kcal }} kcal, ${todayFood.sumOf { it.protein }}g protein\n")
        val byDay = if (lite) emptyMap() else food.filter { it.date != day }.groupBy { it.date }
        if (byDay.isNotEmpty()) {
            sb.append("Previous days: ")
            sb.append(byDay.entries.sortedBy { it.key }.joinToString("; ") { (d, l) ->
                "${LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${l.sumOf { it.kcal }}kcal/${l.sumOf { it.protein }}gP"
            }).append("\n")
        }

        val checkIns = life.recentCheckIns(if (lite) 1 else 3)
        if (checkIns.isNotEmpty()) {
            sb.append("\n## Recent check-ins\n")
            checkIns.forEach {
                sb.append("- ${LocalDate.ofEpochDay(it.date)}: mood ${it.mood}/5, energy ${it.energy}/5, sleep ${it.sleepHours}h. " +
                    "${it.dayText.take(200)} | well: ${it.wentWell.take(100)} | improve: ${it.improve.take(100)}\n")
            }
        }

        val logs = if (lite) emptyList() else life.logsSince(day - 7)
        if (logs.isNotEmpty()) {
            sb.append("\n## Logs (last 7 days)\n")
            logs.takeLast(15).forEach { sb.append("- ${LocalDate.ofEpochDay(it.date)} [${it.category}] ${it.text.take(150)}\n") }
        }

        val pastQuests = if (lite) emptyList() else game.questsSince(day - 7).filter { it.date < day }
        if (pastQuests.isNotEmpty()) {
            val done = pastQuests.count { it.done }
            sb.append("\n## Quest completion last 7 days: $done/${pastQuests.size}\n")
            pastQuests.filter { !it.done }.take(10).forEach { sb.append("- missed: ${it.title}\n") }
        }

        sb.append("\n## Streak\n${streak()} day(s) (quest EXP x${"%.2f".format(streakMultiplier())})\n")

        val health = fit.healthSince(if (lite) day else day - 6)
        if (health.isNotEmpty()) {
            sb.append("\n## Health data (from phone/watch)\n")
            health.forEach {
                sb.append("- ${LocalDate.ofEpochDay(it.date)}: ${it.steps} steps, sleep ${it.sleepMin / 60}h${it.sleepMin % 60}m, " +
                    "workouts ${it.workoutMin} min ${it.workouts}\n")
            }
        }

        val sets = fit.setsSince(if (lite) day else day - 7)
        if (sets.isNotEmpty()) {
            sb.append("\n## Strength log (last 7 days)\n")
            sets.groupBy { it.date }.toSortedMap().forEach { (d, l) ->
                sb.append("- ${LocalDate.ofEpochDay(d)}: ")
                sb.append(l.groupBy { it.exercise.lowercase() }.values.joinToString("; ") { ex ->
                    ex.first().exercise + " " + ex.joinToString(", ") { "${it.weightKg}x${it.reps}" }
                }).append("\n")
            }
        }

        val body = fit.bodySince(day - if (lite) 14 else 60)
        if (body.isNotEmpty()) {
            sb.append("\n## Body weight\n")
            body.takeLast(8).forEach { sb.append("- ${LocalDate.ofEpochDay(it.date)}: ${it.weightKg} kg${it.waistCm?.let { w -> ", waist $w cm" } ?: ""}\n") }
        }

        val v = db.vault()
        val ladders = if (Sports.usesLadders(prefs.sportList)) trackedLadders() else emptyList()
        if (ladders.isNotEmpty()) {
            sb.append("\n## Calisthenics skill ladders\n")
            ladders.forEach { l ->
                val p = v.progressOf(l.id) ?: SkillProgress(l.id)
                val st = p.stage.coerceIn(0, l.stages.lastIndex)
                sb.append("- ${l.name}: at ${l.stages[st]} (best ${if (l.hold) "${p.bestHoldSec}s" else "${p.bestReps} reps"}), next ${l.stages.getOrNull(st + 1) ?: "mastered"}\n")
            }
        }
        // Money only when the user turned on the Vault or Spending extras
        val vault = prefs.extra(Prefs.Extras.VAULT)
        val money = prefs.extra(Prefs.Extras.MONEY)
        if (vault) sb.append("\n## Money\nGold: ${v.goldBalanceNow()}. Savings pot: €${"%.0f".format(v.potNow())}. " +
            "Saved this month: €${"%.0f".format(v.depositedSinceNow(monthStart()))} of €${prefs.savingsGoal} goal.\n")
        else if (money) sb.append("\n## Money\n")
        if (money) db.grow().expensesSinceNow(monthStart()).takeIf { it.isNotEmpty() }?.let { ex ->
            sb.append("Spent this month: €${"%.0f".format(ex.sumOf { it.amountEur.toDouble() })} (" +
                ex.groupBy { it.category }.entries.joinToString { "${it.key} €${"%.0f".format(it.value.sumOf { e -> e.amountEur.toDouble() })}" } +
                (if (prefs.monthBudget > 0) "; budget €${prefs.monthBudget}" else "") + ")\n")
        }
        val rewards = if (vault) v.rewardsNow().filter { it.redeemedAt == null } else emptyList()
        if (rewards.isNotEmpty()) {
            sb.append("Rewards being earned: ")
            sb.append(rewards.joinToString("; ") { "${it.name} (€${"%.0f".format(it.priceEur)}, ${it.goldCost} Gold${if (it.condition.isNotBlank()) ", " + conditionLabel(it.condition) else ""})" })
            sb.append("\n")
        }

        if (prefs.extra(Prefs.Extras.BOSS)) fit.bossNow(weekStart())?.let {
            sb.append("\n## This week's boss\n${it.name}: ${it.goal} (${if (it.defeated) "defeated" else "active"})\n")
        }

        val skills = if (lite) emptyList() else life.skillsNow()
        if (skills.isNotEmpty()) {
            sb.append("\n## Skill tree\n")
            skills.filter { it.status != "learned" }.take(20).forEach { sb.append("- [${it.status}] ${it.title}${if (it.area.isNotBlank()) " (${it.area})" else ""}\n") }
        }
        return sb.toString()
    }

    companion object {
        @Volatile private var inst: Repo? = null
        fun get(ctx: Context): Repo = inst ?: synchronized(this) {
            inst ?: Repo(ctx.applicationContext).also { inst = it }
        }
    }
}
