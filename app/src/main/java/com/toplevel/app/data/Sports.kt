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

/** Log a session of any sport. Counts for workout quests like a watch workout does. */
suspend fun Repo.logSession(sport: String, minutes: Int, km: Float?, note: String) {
    addLog("training", Sports.label(sport, minutes, km, note))
    val day = today()
    prefs.addSportMin(day, minutes)
    val watch = fit.healthSince(day).firstOrNull { it.date == day }?.workoutMin?.toInt() ?: 0
    val total = maxOf(watch, prefs.sportMin(day))
    game.questsForNow(day).filter { !it.done && it.autoType == "workout" && it.target in 1..total }.forEach { completeQuest(it.id) }
    // A plain training quest ("Train according to plan", "Run 5 km") is done once a session is logged
    game.questsForNow(day).firstOrNull { !it.done && it.stat == Stat.STRENGTH && it.autoType == "" }?.let { completeQuest(it.id) }
    Widgets.refresh(ctx)
}
