package com.toplevel.app.data

import com.toplevel.app.widget.Widgets

/** Any sport, not just calisthenics. The Train tab only shows what fits the user's sports. */
object Sports {
    data class Sport(val name: String, val icon: String, val sets: Boolean = false, val distance: Boolean = false)

    val all = listOf(
        Sport("Gym / weights", "🏋", sets = true),
        Sport("Calisthenics", "🤸", sets = true),
        Sport("Home workouts", "🏠", sets = true),
        Sport("Running", "🏃", distance = true),
        Sport("Cycling", "🚴", distance = true),
        Sport("Swimming", "🏊", distance = true),
        Sport("Hiking", "🥾", distance = true),
        Sport("Football", "⚽"),
        Sport("Basketball", "🏀"),
        Sport("Tennis / padel", "🎾"),
        Sport("Martial arts", "🥋"),
        Sport("Climbing", "🧗"),
        Sport("Yoga / mobility", "🧘"),
        Sport("Dance", "💃"),
    )

    fun of(name: String): Sport = all.firstOrNull { it.name.equals(name, true) } ?: Sport(name, "✦")

    /** Lift / bodyweight sets get their own logger (sets × reps, PRs). */
    fun usesSets(sports: List<String>) = sports.any { of(it).sets }
    fun usesLadders(sports: List<String>) = sports.any { it.equals("Calisthenics", true) }

    /** "Running · 45 min · 8 km · easy pace" (stored as a training journal entry). */
    fun label(sport: String, minutes: Int, km: Float?, note: String) = buildString {
        append(sport).append(" · ").append(minutes).append(" min")
        if (km != null && km > 0) append(" · ").append(if (km % 1f == 0f) km.toInt().toString() else "%.1f".format(km)).append(" km")
        if (note.isNotBlank()) append(" · ").append(note.trim())
    }

    /** Sport name of a stored session, or null for free-text training notes. */
    fun sportOf(entry: LogEntry): String? =
        entry.text.substringBefore(" · ", "").takeIf { entry.category == "training" && entry.text.contains(" min") && it.isNotBlank() }
}

// ───────────────────────── Weekly plan ─────────────────────────

@kotlinx.serialization.Serializable
data class PlanSession(val sport: String, val title: String, val minutes: Int = 0, val detail: String = "",
                       /** Warm-up for this session, from their plan (empty = none given). */
                       val warmup: String = "") {
    fun label() = buildString {
        append(title.ifBlank { sport })
        if (minutes > 0) append(" · ").append(minutes).append(" min")
    }
}

/** Monday..Sunday; an empty day is a rest day. */
@kotlinx.serialization.Serializable
data class WeekPlan(val days: List<List<PlanSession>> = List(7) { emptyList() }) {
    fun day(i: Int) = days.getOrNull(i).orEmpty()
    fun withDay(i: Int, s: List<PlanSession>) = WeekPlan(List(7) { if (it == i) s else day(it) })
    fun isEmpty() = days.all { it.isEmpty() }

    companion object {
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        fun todayIndex(): Int = java.time.LocalDate.now().dayOfWeek.value - 1

        /** One session per line: "Gym: Push day 60 min" (sport before ':' optional, minutes optional). */
        fun parseLines(text: String, sports: List<String>): List<PlanSession> = text.lines().map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("rest", true) && !it.equals("ruhetag", true) }.map { line ->
                val min = Regex("(\\d+)\\s*(min|m\\b)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("(\\d+)\\s*h\\b", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.toIntOrNull()?.times(60) ?: 0
                val (sport, rest) = if (':' in line) line.substringBefore(':').trim() to line.substringAfter(':').trim()
                    else (sports.firstOrNull { line.contains(it.substringBefore(" /"), true) } ?: sports.firstOrNull() ?: "Training") to line
                PlanSession(sport, rest.ifBlank { sport }, min)
            }

        fun toLines(s: List<PlanSession>) = s.joinToString("\n") {
            "${it.sport}: ${it.title}" + if (it.minutes > 0 && !Regex("\\d+\\s*min").containsMatchIn(it.title)) " ${it.minutes} min" else ""
        }
    }
}

fun Repo.weekPlan(): WeekPlan? = try {
    prefs.weekPlanJson.takeIf { it.isNotBlank() }?.let { Growth.json.decodeFromString(WeekPlan.serializer(), it) }
        ?.takeIf { it.days.size == 7 }
} catch (_: Exception) { null }

fun Repo.saveWeekPlan(p: WeekPlan?) {
    prefs.weekPlanJson = p?.let { Growth.json.encodeToString(WeekPlan.serializer(), it) } ?: ""
}

/** The plan as text, for the AI context and quests. */
fun WeekPlan.describe(): String = WeekPlan.names.mapIndexed { i, n ->
    "$n: " + day(i).joinToString("; ") { s -> "${s.sport} – ${s.label()}" + (if (s.detail.isNotBlank()) " (${s.detail})" else "") +
        if (s.warmup.isNotBlank()) " [warm-up: ${s.warmup}]" else "" }
        .ifBlank { "rest" }
}.joinToString("\n")

/**
 * AI: build the user's week. With [pasted] text it converts THEIR plan faithfully (any format: a table, a coach's message,
 * notes); without, it designs a sensible week from their sports, words, level and stored training plan.
 */
suspend fun Repo.makeWeekPlan(pasted: String): WeekPlan {
    val stored = life.plansNow().firstOrNull { it.type == PlanType.TRAINING }?.content?.take(PlanImport.MAX_CHARS).orEmpty()
    val o = claude.askJson(
        system = """You build the user's weekly sport plan for a simple training app.
${if (pasted.isNotBlank()) "They pasted their own plan: convert it FAITHFULLY to the format (keep their sessions, days, exercises and times; " +
            "don't invent extra sessions; if days aren't given, spread it sensibly over the week)."
        else "Design a realistic week from their sports, their own words, level and any stored plan. Include rest days; never more than they can do."}
- Only their sports (unless the pasted plan says otherwise).
- title: short, e.g. "Push day", "Easy run 6 km", "Intervals 6×400 m". minutes: duration.
- detail: the session's actual content, max 300 chars: every exercise with sets × reps (× weight/stage), or pace/distance/intervals.
  From a pasted plan, copy it faithfully; this is what their daily quest shows.
- warmup: the warm-up for that session, max 150 chars. Use the plan's own warm-up when it has one; otherwise a short 5–10 min
  warm-up that fits the session (e.g. "5 min easy jog, leg swings, 2 build-up sprints"). Empty for rest/very light sessions.
- Write in the user's language (language of their notes / pasted plan).
Return {"days":[{"day":"Mon","sessions":[{"sport":"Running","title":"...","minutes":45,"detail":"...","warmup":"..."}]}, … all 7 days Mon–Sun, empty sessions = rest]}""",
        user = buildString {
            append("Sports: ${prefs.sportList.joinToString().ifBlank { "not set" }}\n")
            if (prefs.sportNotes.isNotBlank()) append("In their words: ${prefs.sportNotes}\n")
            append("Level: ${Prefs.Levels.trainingLevels.getOrNull(prefs.trainingLevel)?.first ?: "unknown"}\n")
            if (prefs.equipment.isNotBlank()) append("Equipment: ${prefs.equipment}\n")
            if (pasted.isNotBlank()) append("\n## Their pasted plan\n${pasted.take(PlanImport.MAX_CHARS)}\n")
            else if (stored.isNotBlank()) append("\n## Their stored training plan\n$stored\n")
        },
        maxTokens = 5000,
    ) as? kotlinx.serialization.json.JsonObject ?: throw IllegalStateException("Couldn't build a plan. Try again.")
    val days = (o["days"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? kotlinx.serialization.json.JsonObject }
    val plan = List(7) { i ->
        val d = days.firstOrNull { (com.toplevel.app.ai.Claude.str(it, "day") ?: "").take(3).equals(WeekPlan.names[i], true) } ?: days.getOrNull(i)
        (d?.get("sessions") as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? kotlinx.serialization.json.JsonObject }.map { s ->
            PlanSession(
                sport = com.toplevel.app.ai.Claude.str(s, "sport") ?: prefs.sportList.firstOrNull() ?: "Training",
                title = com.toplevel.app.ai.Claude.str(s, "title").orEmpty(),
                minutes = com.toplevel.app.ai.Claude.int(s, "minutes"),
                detail = com.toplevel.app.ai.Claude.str(s, "detail").orEmpty(),
                warmup = com.toplevel.app.ai.Claude.str(s, "warmup").orEmpty(),
            )
        }
    }
    if (plan.all { it.isEmpty() }) throw IllegalStateException("Couldn't read a plan from that. Try again or write it yourself.")
    if (pasted.isNotBlank()) life.upsertPlan(Plan(PlanType.TRAINING, pasted.trim(), System.currentTimeMillis()))
    return WeekPlan(plan).also { saveWeekPlan(it) }
}

/** A warm-up quest is ticked by hand: logging the session doesn't complete it. */
fun isWarmup(q: Quest) = q.title.startsWith("Warm", true) || q.title.startsWith("Aufwärm", true)

/** Log a session of any sport. Counts for workout quests like a watch workout does. */
suspend fun Repo.logSession(sport: String, minutes: Int, km: Float?, note: String) {
    addLog("training", Sports.label(sport, minutes, km, note))
    val day = today()
    prefs.addSportMin(day, minutes)
    val watch = fit.healthSince(day).firstOrNull { it.date == day }?.workoutMin?.toInt() ?: 0
    val total = maxOf(watch, prefs.sportMin(day))
    game.questsForNow(day).filter { !it.done && it.autoType == "workout" && it.target in 1..total }.forEach { completeQuest(it.id) }
    // A plain training quest ("Train according to plan", "Run 5 km") is done once a session is logged
    game.questsForNow(day).firstOrNull { !it.done && it.stat == Stat.STRENGTH && it.autoType == "" && !isWarmup(it) }?.let { completeQuest(it.id) }
    Widgets.refresh(ctx)
}
