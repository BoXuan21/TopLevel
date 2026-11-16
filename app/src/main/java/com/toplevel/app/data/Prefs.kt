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
    )

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
