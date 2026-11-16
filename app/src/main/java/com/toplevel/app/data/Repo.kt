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
        if (amount != 0) game.insertXp(XpEvent(ts = now(), stat = stat, amount = amount, reason = reason))
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
            award(nq.stat, (nq.xp * streakMultiplier()).toInt(), "quest:${nq.id}")
            gainGold(maxOf(1, nq.xp / 4), "quest:${nq.id}")
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

    suspend fun deleteQuest(q: Quest) {
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

    private fun defaultQuests(day: Long) = listOf(
        Quest(date = day, title = "Train according to plan", stat = Stat.STRENGTH, xp = 40, source = "system"),
        Quest(date = day, title = "Walk 8,000 steps", stat = Stat.VITALITY, xp = 20, source = "system", autoType = "steps", target = 8000),
        Quest(date = day, title = "Log every meal", stat = Stat.VITALITY, xp = 20, source = "system"),
        Quest(date = day, title = "30 min focused learning", stat = Stat.INTELLECT, xp = 30, source = "system"),
        Quest(date = day, title = "90 min deep-work block", stat = Stat.DISCIPLINE, xp = 30, source = "system"),
        Quest(date = day, title = "Evening check-in", stat = Stat.SPIRIT, xp = 15, source = "system"),
    )

    /**
     * Builds today's quests. With an API key, Claude designs them from your plans,
     * calendar and recent history. Without one, a sensible default set is used.
     */
    suspend fun generateQuests(force: Boolean = false): String? {
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
            val ctxText = buildContext()
            val res = claude.askJson(
                system = """You design the user's daily quests in a gamified self-improvement app.
Stats: STRENGTH (training), VITALITY (nutrition, sleep, health), INTELLECT (learning), DISCIPLINE (work, focus, habits), SPIRIT (mood, reflection, relationships, rest).
Rules:
- 4 to 7 quests for TODAY, concrete and checkable ("Push day: bench 4x8, OHP 3x10…", not "work out").
- Follow today's training plan for this weekday exactly; on rest days give a recovery quest.
- Include the nutrition target as a quest (e.g. hit protein goal).
- Include one learning quest tied to the user's learning goals and one work/focus quest.
- Respect calendar events: don't schedule things that clash; mention time windows when useful.
- Use yesterday's check-in to make one quest address what they wanted to improve.
- They are an advanced calisthenics athlete (planche / front lever focus) and use the app for tracking. On training days make the strength quest
  follow THEIR plan for the day with concrete targets at their current ladder stages (e.g. "Planche: 6×5s at current stage + 4×3 PPU"); don't over-coach basics.
- If they are saving towards rewards, include at most one money quest (no-spend day, move €X to savings, cook instead of ordering) — ideally on 2-3 days per week.
- xp between 10 and 60 depending on effort.
- Health data syncs automatically. When a quest is measurable, set "auto" so it completes itself:
  "steps" (target = step count), "sleep" (target = minutes of sleep last night), "workout" (target = workout minutes). Otherwise "auto": "".
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
                val auto = str(o, "auto")?.lowercase()?.takeIf { it in setOf("steps", "sleep", "workout") }.orEmpty()
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

    data class Macros(val name: String, val kcal: Int, val protein: Int, val carbs: Int, val fat: Int)

    suspend fun estimateFood(description: String): Macros {
        val o = claude.askJson(
            system = "Estimate nutrition for the described food/meal. Use typical portion sizes if unspecified. " +
                "Return {\"name\": short name, \"kcal\": int, \"protein\": grams int, \"carbs\": grams int, \"fat\": grams int}.",
            user = description, maxTokens = 300,
        ).jsonObject
        return Macros(str(o, "name") ?: description, int(o, "kcal"), int(o, "protein"), int(o, "carbs"), int(o, "fat"))
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
            user = buildContext(),
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
                user = buildContext() + "\n\n## Tonight's check-in\nMood ${c.mood}/5, energy ${c.energy}/5, " +
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
                system = """You curate a short daily discovery feed for the user.
1) 3 songs (they listen on YouTube Music) they probably don't know yet but will like, based on their music taste (similar artists/sound, mix of familiar-adjacent and adventurous). Real songs only.
2) 2 educational videos/talks for their work and learning goals (well-known conference talks, channels or lectures that really exist). Prefer things directly useful this week.
Never repeat anything from the 'already suggested' list.
Return {"songs":[{"title":"...","artist":"...","why":"one short line"}],
        "videos":[{"title":"...","channel":"...","why":"one short line","query":"youtube search query"}]}""",
                user = buildContext() + "\n\n## Already suggested (don't repeat)\n" + past.ifBlank { "(none)" },
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
            if (items.isNotEmpty()) {
                life.clearDiscover(day)
                life.insertDiscover(items)
            }
            prefs.lastDiscoverGen = day
            null
        } catch (e: Exception) { e.message }
    }

    // ───────────────────────── Coach chat with tools ─────────────────────────

    private val chatTools: JsonArray by lazy {
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
            Claude.tool("add_calendar_event", "Create an event in the user's Google Calendar (e.g. to schedule a workout or study block).",
                mapOf("title" to ("string" to "Event title"),
                    "start" to ("string" to "Local start time, format YYYY-MM-DDTHH:MM"),
                    "minutes" to ("integer" to "Duration in minutes"),
                    "description" to ("string" to "Optional details")),
                listOf("title", "start", "minutes")),
        ))
    }

    private suspend fun runTool(name: String, input: JsonObject): String = try {
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
            "log_savings" -> {
                val a = str(input, "amount_eur")?.toFloatOrNull() ?: 0f
                deposit(a, str(input, "note").orEmpty())
                "✓ Saved €${"%.2f".format(a)}"
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
You help with training, nutrition, work (they are an SRE), learning, mood and becoming a better person.
Style: direct, warm, practical, no fluff. Short answers on a phone screen unless they ask for depth. Use their data below.
When the user shares information worth keeping (a plan, a goal, food they ate, something learned, a preference), STORE it with your tools without asking — then mention briefly what you saved.
If they paste a training or eating plan, save it with update_plan.

${buildContext()}"""
            val history = life.recentChat(30)
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
            for (round in 0 until 6) {
                val res = claude.send(system, JsonArray(convo), chatTools, maxTokens = 2048)
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

    suspend fun buildContext(): String {
        val today = LocalDate.now()
        val day = today.toEpochDay()
        val sb = StringBuilder()
        val dow = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        sb.append("## Now\n$dow, ${today.format(DateTimeFormatter.ISO_DATE)}, ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))).append("\n")
        if (prefs.name.isNotBlank()) sb.append("Name: ${prefs.name}\n")

        val totals = Leveling.totals(game.statTotalsNow())
        val total = totals.values.sum()
        val p = Leveling.progress(total)
        sb.append("\n## Level\nLevel ${p.level} (rank ${Leveling.rank(p.level)}), ${total} XP. Stats: ")
            .append(totals.entries.joinToString(", ") { "${it.key.short} ${Leveling.statLevel(it.value)}" }).append("\n")

        sb.append("\n## Nutrition goals\n${prefs.kcalGoal} kcal, ${prefs.proteinGoal}g protein, " +
            "${prefs.carbsGoal}g carbs, ${prefs.fatGoal}g fat per day\n")

        val plans = life.plansNow().filter { it.content.isNotBlank() }
        if (plans.isNotEmpty()) {
            sb.append("\n## Stored plans\n")
            plans.forEach { sb.append("### ${PlanType.label(it.type)}\n${it.content.take(4000)}\n\n") }
        }

        val mems = life.memoriesNow()
        if (mems.isNotEmpty()) {
            sb.append("\n## Things to remember about the user\n")
            mems.take(40).forEach { sb.append("- ${it.text}\n") }
        }

        if (prefs.useCalendar) {
            val evs = CalendarRepo.eventsFor(ctx, today, 2)
            if (evs.isNotEmpty()) {
                sb.append("\n## Calendar (today + tomorrow)\n")
                val df = DateTimeFormatter.ofPattern("EEE")
                evs.take(25).forEach {
                    val d = java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    sb.append("- ${d.format(df)} ${it.timeLabel()}: ${it.title}\n")
                }
            }
        }

        val quests = game.questsForNow(day)
        if (quests.isNotEmpty()) {
            sb.append("\n## Today's quests\n")
            quests.forEach { sb.append("- [${if (it.done) "x" else " "}] ${it.title} (${it.stat.short} +${it.xp})\n") }
        }

        val food = life.foodSince(day - 6)
        val todayFood = food.filter { it.date == day }
        sb.append("\n## Food today\n")
        if (todayFood.isEmpty()) sb.append("(nothing logged yet)\n")
        todayFood.forEach { sb.append("- ${it.name}: ${it.kcal} kcal, P${it.protein} C${it.carbs} F${it.fat}\n") }
        sb.append("Total: ${todayFood.sumOf { it.kcal }} kcal, ${todayFood.sumOf { it.protein }}g protein\n")
        val byDay = food.filter { it.date != day }.groupBy { it.date }
        if (byDay.isNotEmpty()) {
            sb.append("Previous days: ")
            sb.append(byDay.entries.sortedBy { it.key }.joinToString("; ") { (d, l) ->
                "${LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${l.sumOf { it.kcal }}kcal/${l.sumOf { it.protein }}gP"
            }).append("\n")
        }

        val checkIns = life.recentCheckIns(7)
        if (checkIns.isNotEmpty()) {
            sb.append("\n## Recent check-ins\n")
            checkIns.forEach {
                sb.append("- ${LocalDate.ofEpochDay(it.date)}: mood ${it.mood}/5, energy ${it.energy}/5, sleep ${it.sleepHours}h. " +
                    "${it.dayText.take(300)} | well: ${it.wentWell.take(150)} | improve: ${it.improve.take(150)}\n")
            }
        }

        val logs = life.logsSince(day - 7)
        if (logs.isNotEmpty()) {
            sb.append("\n## Logs (last 7 days)\n")
            logs.takeLast(40).forEach { sb.append("- ${LocalDate.ofEpochDay(it.date)} [${it.category}] ${it.text.take(200)}\n") }
        }

        val pastQuests = game.questsSince(day - 7).filter { it.date < day }
        if (pastQuests.isNotEmpty()) {
            val done = pastQuests.count { it.done }
            sb.append("\n## Quest completion last 7 days: $done/${pastQuests.size}\n")
            pastQuests.filter { !it.done }.take(10).forEach { sb.append("- missed: ${it.title}\n") }
        }

        sb.append("\n## Streak\n${streak()} day(s) (quest EXP x${"%.2f".format(streakMultiplier())})\n")

        val health = fit.healthSince(day - 6)
        if (health.isNotEmpty()) {
            sb.append("\n## Health data (from phone/watch)\n")
            health.forEach {
                sb.append("- ${LocalDate.ofEpochDay(it.date)}: ${it.steps} steps, sleep ${it.sleepMin / 60}h${it.sleepMin % 60}m, " +
                    "workouts ${it.workoutMin} min ${it.workouts}\n")
            }
        }

        val sets = fit.setsSince(day - 14)
        if (sets.isNotEmpty()) {
            sb.append("\n## Strength log (last 14 days)\n")
            sets.groupBy { it.date }.toSortedMap().forEach { (d, l) ->
                sb.append("- ${LocalDate.ofEpochDay(d)}: ")
                sb.append(l.groupBy { it.exercise.lowercase() }.values.joinToString("; ") { ex ->
                    ex.first().exercise + " " + ex.joinToString(", ") { "${it.weightKg}x${it.reps}" }
                }).append("\n")
            }
        }

        val body = fit.bodySince(day - 60)
        if (body.isNotEmpty()) {
            sb.append("\n## Body weight\n")
            body.takeLast(15).forEach { sb.append("- ${LocalDate.ofEpochDay(it.date)}: ${it.weightKg} kg${it.waistCm?.let { w -> ", waist $w cm" } ?: ""}\n") }
        }

        val v = db.vault()
        val ladders = trackedLadders()
        if (ladders.isNotEmpty()) {
            sb.append("\n## Calisthenics skill ladders\n")
            ladders.forEach { l ->
                val p = v.progressOf(l.id) ?: SkillProgress(l.id)
                val st = p.stage.coerceIn(0, l.stages.lastIndex)
                sb.append("- ${l.name}: at ${l.stages[st]} (best ${if (l.hold) "${p.bestHoldSec}s" else "${p.bestReps} reps"}), next ${l.stages.getOrNull(st + 1) ?: "mastered"}\n")
            }
        }
        sb.append("\n## Money\nGold: ${v.goldBalanceNow()}. Savings pot: €${"%.0f".format(v.potNow())}. " +
            "Saved this month: €${"%.0f".format(v.depositedSinceNow(monthStart()))} of €${prefs.savingsGoal} goal.\n")
        val rewards = v.rewardsNow().filter { it.redeemedAt == null }
        if (rewards.isNotEmpty()) {
            sb.append("Rewards being earned: ")
            sb.append(rewards.joinToString("; ") { "${it.name} (€${"%.0f".format(it.priceEur)}, ${it.goldCost} Gold${if (it.condition.isNotBlank()) ", " + conditionLabel(it.condition) else ""})" })
            sb.append("\n")
        }

        fit.bossNow(weekStart())?.let {
            sb.append("\n## This week's boss\n${it.name}: ${it.goal} (${if (it.defeated) "defeated" else "active"})\n")
        }

        val skills = life.skillsNow()
        if (skills.isNotEmpty()) {
            sb.append("\n## Skill tree\n")
            skills.take(40).forEach { sb.append("- [${it.status}] ${it.title}${if (it.area.isNotBlank()) " (${it.area})" else ""}\n") }
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
