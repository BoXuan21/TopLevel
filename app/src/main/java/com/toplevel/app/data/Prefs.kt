package com.toplevel.app.data

import android.content.Context
import android.content.SharedPreferences

/** Simple settings stored in the app's private storage. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("toplevel_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply()

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v.trim()).apply()

    var kcalGoal: Int
        get() = sp.getInt("kcal", 2400)
        set(v) = sp.edit().putInt("kcal", v).apply()
    var proteinGoal: Int
        get() = sp.getInt("protein", 150)
        set(v) = sp.edit().putInt("protein", v).apply()
    var carbsGoal: Int
        get() = sp.getInt("carbs", 250)
        set(v) = sp.edit().putInt("carbs", v).apply()
    var fatGoal: Int
        get() = sp.getInt("fat", 75)
        set(v) = sp.edit().putInt("fat", v).apply()

    var morningHour: Int
        get() = sp.getInt("morning_hour", 6)
        set(v) = sp.edit().putInt("morning_hour", v).apply()
    var eveningHour: Int
        get() = sp.getInt("evening_hour", 21)
        set(v) = sp.edit().putInt("evening_hour", v).apply()

    var useCalendar: Boolean
        get() = sp.getBoolean("use_calendar", true)
        set(v) = sp.edit().putBoolean("use_calendar", v).apply()

    /** epochDay when quests/discover were last auto-generated */
    var lastQuestGen: Long
        get() = sp.getLong("last_quest_gen", -1)
        set(v) = sp.edit().putLong("last_quest_gen", v).apply()
    var lastDiscoverGen: Long
        get() = sp.getLong("last_discover_gen", -1)
        set(v) = sp.edit().putLong("last_discover_gen", v).apply()

    /** What kind of quests the user wants (free text, set when regenerating quests). */
    var questWishes: String
        get() = sp.getString("quest_wishes", "") ?: ""
        set(v) = sp.edit().putString("quest_wishes", v.trim().take(500)).apply()

    /** Titles of generated quests the user deleted without doing them (newest last), so the AI stops suggesting them. */
    var skippedQuests: List<String>
        get() = (sp.getString("skipped_quests", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("skipped_quests", v.takeLast(20).joinToString("\n") { it.replace("\n", " ") }).apply()

    var lastSeenLevel: Int
        get() = sp.getInt("last_seen_level", 1)
        set(v) = sp.edit().putInt("last_seen_level", v).apply()

    /** Running focus block: end time (0 = none), start time, length. */
    var focusEnd: Long
        get() = sp.getLong("focus_end", 0)
        set(v) = sp.edit().putLong("focus_end", v).apply()
    var focusStart: Long
        get() = sp.getLong("focus_start", 0)
        set(v) = sp.edit().putLong("focus_start", v).apply()
    var focusLen: Int
        get() = sp.getInt("focus_len", 50)
        set(v) = sp.edit().putInt("focus_len", v).apply()
    var focusTotalMin: Int
        get() = sp.getInt("focus_total", 0)
        set(v) = sp.edit().putInt("focus_total", v).apply()

    /** Title chosen from unlocked achievements ("" = level title). */
    var activeTitle: String
        get() = sp.getString("active_title", "") ?: ""
        set(v) = sp.edit().putString("active_title", v).apply()

    var lastBackup: Long
        get() = sp.getLong("last_backup", 0)
        set(v) = sp.edit().putLong("last_backup", v).apply()

    /** Export everything except the API key. */
    fun exportMap(): Map<String, String> = mapOf(
        "name" to name, "model" to model,
        "kcal" to kcalGoal.toString(), "protein" to proteinGoal.toString(),
        "carbs" to carbsGoal.toString(), "fat" to fatGoal.toString(),
        "morning_hour" to morningHour.toString(), "evening_hour" to eveningHour.toString(),
        "use_calendar" to useCalendar.toString(), "focus_total" to focusTotalMin.toString(),
        "active_title" to activeTitle,
        "ladders" to trackedLadders, "savings_goal" to savingsGoal.toString(),
        "height_cm" to heightCm.toString(), "age" to age.toString(), "sex" to sex,
        "activity" to activity.toString(), "body_goal" to bodyGoal, "water_goal" to waterGoal.toString(),
        "training_level" to trainingLevel.toString(), "equipment" to equipment, "calendar_write" to calendarWrite.toString(),
        "job_json" to jobJson, "jobs_done" to jobsDone, "theme" to theme, "dungeons_cleared" to dungeonsCleared.toString(),
        "budgets" to budgets, "month_budget" to monthBudget.toString(),
        "sports" to sports, "sport_notes" to sportNotes, "quest_wishes" to questWishes, "week_plan" to weekPlanJson,
        "extras" to extrasOn.joinToString(","), "shop_recent" to shopRecent, "eco" to eco.toString(),
    ) + Stat.entries.associate { "pts_${it.name}" to points(it).toString() }

    fun importMap(m: Map<String, String>) {
        m["name"]?.let { name = it }
        m["model"]?.let { model = it }
        m["kcal"]?.toIntOrNull()?.let { kcalGoal = it }
        m["protein"]?.toIntOrNull()?.let { proteinGoal = it }
        m["carbs"]?.toIntOrNull()?.let { carbsGoal = it }
        m["fat"]?.toIntOrNull()?.let { fatGoal = it }
        m["morning_hour"]?.toIntOrNull()?.let { morningHour = it }
        m["evening_hour"]?.toIntOrNull()?.let { eveningHour = it }
        m["use_calendar"]?.toBooleanStrictOrNull()?.let { useCalendar = it }
        m["focus_total"]?.toIntOrNull()?.let { focusTotalMin = it }
        m["active_title"]?.let { activeTitle = it }
        m["ladders"]?.let { trackedLadders = it }
        m["savings_goal"]?.toIntOrNull()?.let { savingsGoal = it }
        m["height_cm"]?.toIntOrNull()?.let { heightCm = it }
        m["age"]?.toIntOrNull()?.let { age = it }
        m["sex"]?.let { sex = it }
        m["activity"]?.toIntOrNull()?.let { activity = it }
        m["body_goal"]?.let { bodyGoal = it }
        m["water_goal"]?.toIntOrNull()?.let { waterGoal = it }
        m["training_level"]?.toIntOrNull()?.let { trainingLevel = it }
        m["equipment"]?.let { equipment = it }
        m["calendar_write"]?.toBooleanStrictOrNull()?.let { calendarWrite = it }
        m["job_json"]?.let { jobJson = it }
        m["jobs_done"]?.let { jobsDone = it }
        m["theme"]?.let { theme = it }
        m["dungeons_cleared"]?.toIntOrNull()?.let { dungeonsCleared = it }
        m["budgets"]?.let { budgets = it }
        m["month_budget"]?.toIntOrNull()?.let { monthBudget = it }
        m["sports"]?.let { sports = it }
        m["sport_notes"]?.let { sportNotes = it }
        m["quest_wishes"]?.let { questWishes = it }
        m["week_plan"]?.let { weekPlanJson = it }
        m["extras"]?.let { extrasOn = it.split(",").filter { s -> s.isNotBlank() }.toSet() }
        m["shop_recent"]?.let { shopRecent = it }
        m["eco"]?.toBooleanStrictOrNull()?.let { eco = it }
        Stat.entries.forEach { s -> m["pts_${s.name}"]?.toIntOrNull()?.let { setPoints(s, it) } }
    }

    /** Eco mode: every AI call on the cheapest model (on by default). */
    var eco: Boolean
        get() = sp.getBoolean("eco", true)
        set(v) = sp.edit().putBoolean("eco", v).apply()

    /** API use this month: tokens and estimated cost (reset when the month changes). */
    data class Usage(val month: String, val calls: Long, val input: Long, val output: Long, val cacheRead: Long, val costUsd: Double)
    @Synchronized fun addUsage(input: Long, output: Long, cacheRead: Long, cacheWrite: Long, costUsd: Double) {
        val u = usage()
        sp.edit().putString("usage_month", u.month).putLong("usage_calls", u.calls + 1)
            .putLong("usage_in", u.input + input + cacheWrite).putLong("usage_out", u.output + output)
            .putLong("usage_cache", u.cacheRead + cacheRead)
            .putLong("usage_cost_micro", ((u.costUsd + costUsd) * 1_000_000).toLong()).apply()
    }
    fun usage(): Usage {
        val month = java.time.YearMonth.now().toString()
        if (sp.getString("usage_month", "") != month) return Usage(month, 0, 0, 0, 0, 0.0)
        return Usage(month, sp.getLong("usage_calls", 0), sp.getLong("usage_in", 0), sp.getLong("usage_out", 0),
            sp.getLong("usage_cache", 0), sp.getLong("usage_cost_micro", 0) / 1_000_000.0)
    }

    /** Last shop searches, newline-separated, newest first. */
    var shopRecent: String
        get() = sp.getString("shop_recent", "") ?: ""
        set(v) = sp.edit().putString("shop_recent", v).apply()

    /** Extra game systems. All off until the user turns them on; their data is kept either way. */
    object Extras {
        data class E(val id: String, val icon: String, val name: String, val desc: String)
        const val BOSS = "boss"; const val DUNGEONS = "dungeons"; const val JOB = "job"; const val POINTS = "points"
        const val VAULT = "vault"; const val MONEY = "money"; const val FOCUS = "focus"
        val all = listOf(
            E(BOSS, "☠", "Weekly boss", "A big goal every Monday, plus a report on last week"),
            E(DUNGEONS, "⛩", "Dungeons", "Timed challenges; quitting early gives a penalty quest"),
            E(JOB, "♛", "Job change", "Multi-week questlines that unlock titles and colour themes"),
            E(POINTS, "▲", "Stat points", "Spend points from level-ups to boost a stat's EXP"),
            E(VAULT, "€", "Money & Gold", "Spending, savings, Gold and real-life rewards on one page"),
            E(FOCUS, "◷", "Focus timer", "25/50/90-minute deep-work blocks"),
        )
    }

    object Levels {
        val trainingLevels = listOf(
            "Beginner" to "New to training or under 1 year",
            "Intermediate" to "1–3 years, solid basics",
            "Advanced" to "3+ years, working on hard skills",
            "Elite" to "Competing / coaching level",
        )
    }

    /** Calisthenics ladders shown in Train, comma-separated ids. */
    var trackedLadders: String
        get() = sp.getString("ladders", "planche,frontlever") ?: "planche,frontlever"
        set(v) = sp.edit().putString("ladders", v).apply()

    /** Monthly savings target in €. */
    var savingsGoal: Int
        get() = sp.getInt("savings_goal", 200)
        set(v) = sp.edit().putInt("savings_goal", v).apply()

    fun ladderStages(id: String): List<String> =
        (sp.getString("stages_$id", "") ?: "").split("|").filter { it.isNotBlank() }

    fun setLadderStages(id: String, stages: List<String>) =
        sp.edit().putString("stages_$id", stages.joinToString("|")).apply()

    /** Show the Claude-built session panel in Train (off = pure tracking). */
    var coachSessions: Boolean
        get() = sp.getBoolean("coach_sessions", false)
        set(v) = sp.edit().putBoolean("coach_sessions", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /** Step-by-step setup wizard finished. */
    var wizardDone: Boolean
        get() = sp.getBoolean("wizard_done", false)
        set(v) = sp.edit().putBoolean("wizard_done", v).apply()

    // Body profile (used for the macro calculator)
    var heightCm: Int
        get() = sp.getInt("height_cm", 0)
        set(v) = sp.edit().putInt("height_cm", v).apply()
    var age: Int
        get() = sp.getInt("age", 0)
        set(v) = sp.edit().putInt("age", v).apply()
    /** "m" or "f" */
    var sex: String
        get() = sp.getString("sex", "m") ?: "m"
        set(v) = sp.edit().putString("sex", v).apply()
    /** 0 = desk, 1 = light, 2 = active, 3 = very active */
    var activity: Int
        get() = sp.getInt("activity", 2)
        set(v) = sp.edit().putInt("activity", v).apply()
    /** "cut", "maintain", "bulk" */
    var bodyGoal: String
        get() = sp.getString("body_goal", "maintain") ?: "maintain"
        set(v) = sp.edit().putString("body_goal", v).apply()

    /** Training experience: -1 unknown, 0 beginner, 1 intermediate, 2 advanced, 3 elite. */
    var trainingLevel: Int
        get() = sp.getInt("training_level", -1)
        set(v) = sp.edit().putInt("training_level", v).apply()
    /** The user's sports (names from [Sports.all] or their own), comma-separated. Older installs: calisthenics. */
    var sports: String
        get() = sp.getString("sports", null) ?: if (wizardDone && trackedLadders.isNotBlank()) "Calisthenics" else ""
        set(v) = sp.edit().putString("sports", v).apply()
    val sportList: List<String> get() = sports.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    /** In their own words: how often, what exactly, goals ("Running 3x/week, half marathon in May"). */
    var sportNotes: String
        get() = sp.getString("sport_notes", "") ?: ""
        set(v) = sp.edit().putString("sport_notes", v.trim()).apply()

    /** Optional features the user switched on (see [Extras]). Off by default to keep the app simple. */
    fun extra(id: String): Boolean = ids(id).any { it in extrasOn }
    fun setExtra(id: String, on: Boolean) {
        extrasOn = if (on) extrasOn + ids(id) else extrasOn - ids(id)
    }
    /** Spending and Vault were two extras up to build 21; now they're one "Money" page. Either id switches both. */
    private fun ids(id: String) = if (id == Extras.VAULT || id == Extras.MONEY) setOf(Extras.VAULT, Extras.MONEY) else setOf(id)
    var extrasOn: Set<String>
        get() = (sp.getString("extras", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
        set(v) = sp.edit().putString("extras", v.joinToString(",")).apply()

    /** Install new builds from GitHub automatically. */
    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit().putBoolean("auto_update", v).apply()
    var updateWaitsForPermission: Boolean
        get() = sp.getBoolean("update_waits_perm", false)
        set(v) = sp.edit().putBoolean("update_waits_perm", v).apply()
    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0)
        set(v) = sp.edit().putLong("last_update_check", v).apply()

    /** The weekly sport plan (WeekPlan as JSON). */
    var weekPlanJson: String
        get() = sp.getString("week_plan", "") ?: ""
        set(v) = sp.edit().putString("week_plan", v).apply()

    /** Quiz widget: the card on screen, whether its answer is showing, cards put back today. */
    var widgetCard: Long
        get() = sp.getLong("widget_card", 0)
        set(v) = sp.edit().putLong("widget_card", v).apply()
    var widgetRevealed: Boolean
        get() = sp.getBoolean("widget_revealed", false)
        set(v) = sp.edit().putBoolean("widget_revealed", v).apply()
    fun widgetSkipped(day: Long): Set<Long> =
        (sp.getString("widget_skip_$day", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.toSet()
    fun addWidgetSkipped(day: Long, id: Long) =
        sp.edit().putString("widget_skip_$day", (widgetSkipped(day) + id).joinToString(",")).apply()

    /** Training minutes logged by hand per day (sessions without a watch). */
    fun sportMin(day: Long): Int = sp.getInt("sportmin_$day", 0)
    fun addSportMin(day: Long, min: Int) = sp.edit().putInt("sportmin_$day", sportMin(day) + min).apply()

    /** Equipment the user has, comma-separated. */
    var equipment: String
        get() = sp.getString("equipment", "") ?: ""
        set(v) = sp.edit().putString("equipment", v).apply()

    /** The coach may add events to the calendar. Off = calendar is read-only, for appointments. */
    var calendarWrite: Boolean
        get() = sp.getBoolean("calendar_write", false)
        set(v) = sp.edit().putBoolean("calendar_write", v).apply()

    // Phone step sensor (used when Health Connect isn't linked)
    var stepDay: Long
        get() = sp.getLong("step_day", -1)
        set(v) = sp.edit().putLong("step_day", v).apply()
    /** Sensor total at the start of [stepDay]. */
    var stepBase: Long
        get() = sp.getLong("step_base", 0)
        set(v) = sp.edit().putLong("step_base", v).apply()
    /** Last sensor total seen. */
    var stepLast: Long
        get() = sp.getLong("step_last", 0)
        set(v) = sp.edit().putLong("step_last", v).apply()

    // Stat points: spent per stat (earned = 3 per level above 1)
    fun points(stat: Stat): Int = sp.getInt("pts_${stat.name}", 0)
    fun setPoints(stat: Stat, n: Int) = sp.edit().putInt("pts_${stat.name}", n.coerceAtLeast(0)).apply()
    fun pointsSpent(): Int = Stat.entries.sumOf { points(it) }

    // Job change (JSON state) and unlocked colour themes
    var jobJson: String
        get() = sp.getString("job_json", "") ?: ""
        set(v) = sp.edit().putString("job_json", v).apply()
    var jobsDone: String
        get() = sp.getString("jobs_done", "") ?: ""
        set(v) = sp.edit().putString("jobs_done", v).apply()
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    // Dungeons
    var dungeonJson: String
        get() = sp.getString("dungeon_json", "") ?: ""
        set(v) = sp.edit().putString("dungeon_json", v).apply()
    var dungeonsCleared: Int
        get() = sp.getInt("dungeons_cleared", 0)
        set(v) = sp.edit().putInt("dungeons_cleared", v).apply()

    // Flashcards
    var cardGenDay: Long
        get() = sp.getLong("card_gen_day", -1)
        set(v) = sp.edit().putLong("card_gen_day", v).apply()
    /** Highest "learned" log id already turned into cards. */
    var cardLastLog: Long
        get() = sp.getLong("card_last_log", 0)
        set(v) = sp.edit().putLong("card_last_log", v).apply()
    fun cardsReviewed(day: Long): Int = if (sp.getLong("cards_day", -1) == day) sp.getInt("cards_count", 0) else 0
    fun setCardsReviewed(day: Long, n: Int) = sp.edit().putLong("cards_day", day).putInt("cards_count", n).apply()

    // Spending limits: "Category=limit;…" (€ per month), plus an overall monthly budget
    var budgets: String
        get() = sp.getString("budgets", "") ?: ""
        set(v) = sp.edit().putString("budgets", v).apply()
    var monthBudget: Int
        get() = sp.getInt("month_budget", 0)
        set(v) = sp.edit().putInt("month_budget", v).apply()
    fun budgetMap(): Map<String, Int> = budgets.split(";").mapNotNull {
        val (k, v) = it.split("=").takeIf { p -> p.size == 2 } ?: return@mapNotNull null
        v.toIntOrNull()?.let { n -> k to n }
    }.toMap()
    fun setBudget(cat: String, limit: Int) {
        val m = budgetMap().toMutableMap()
        if (limit > 0) m[cat] = limit else m.remove(cat)
        budgets = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    // Weekly review (AI part cached per week)
    var reviewWeek: Long
        get() = sp.getLong("review_week", -1)
        set(v) = sp.edit().putLong("review_week", v).apply()
    var reviewJson: String
        get() = sp.getString("review_json", "") ?: ""
        set(v) = sp.edit().putString("review_json", v).apply()

    // Sound
    var sfxOn: Boolean
        get() = sp.getBoolean("sfx_on", true)
        set(v) = sp.edit().putBoolean("sfx_on", v).apply()
    /** "off", "theme" (built-in dark theme) or "custom" (the user's own audio file). */
    var musicMode: String
        get() = sp.getString("music_mode", "theme") ?: "theme"
        set(v) = sp.edit().putString("music_mode", v).apply()
    var musicUri: String
        get() = sp.getString("music_uri", "") ?: ""
        set(v) = sp.edit().putString("music_uri", v).apply()
    var musicName: String
        get() = sp.getString("music_name", "") ?: ""
        set(v) = sp.edit().putString("music_name", v).apply()
    var musicVolume: Float
        get() = sp.getFloat("music_volume", 0.35f)
        set(v) = sp.edit().putFloat("music_volume", v).apply()

    // Water: glasses (250 ml) per day
    var waterGoal: Int
        get() = sp.getInt("water_goal", 10)
        set(v) = sp.edit().putInt("water_goal", v).apply()
    fun water(day: Long): Int = if (sp.getLong("water_day", -1) == day) sp.getInt("water_count", 0) else 0
    fun setWater(day: Long, n: Int) = sp.edit().putLong("water_day", day).putInt("water_count", n.coerceAtLeast(0)).apply()

    // Cached AI advice (JSON) so it isn't re-fetched on every open
    var adviceJson: String
        get() = sp.getString("advice_json", "") ?: ""
        set(v) = sp.edit().putString("advice_json", v).apply()
    var adviceTs: Long
        get() = sp.getLong("advice_ts", 0)
        set(v) = sp.edit().putLong("advice_ts", v).apply()
    var foodAdvice: String
        get() = sp.getString("food_advice", "") ?: ""
        set(v) = sp.edit().putString("food_advice", v).apply()
    var foodAdviceTs: Long
        get() = sp.getLong("food_advice_ts", 0)
        set(v) = sp.edit().putLong("food_advice_ts", v).apply()

    val hasKey get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
    }
}
