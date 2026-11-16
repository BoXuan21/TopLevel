package com.toplevel.app.data

import com.toplevel.app.ai.Claude.Companion.int
import com.toplevel.app.ai.Claude.Companion.str
import com.toplevel.app.game.Leveling
import com.toplevel.app.widget.Widgets
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.jsonObject
import java.time.DayOfWeek
import java.time.LocalDate

/** A "[SYSTEM]" pop-up window. */
data class Popup(
    val kind: Kind,
    val title: String,
    val body: String = "",
    val lines: List<String> = emptyList(),
    val reward: String = "",
    /** Closes by itself after a few seconds (quests, logged meals). */
    val auto: Boolean = kind == Kind.QUEST,
) {
    enum class Kind { NOTICE, QUEST, CLEAR, LEVEL_UP, ACHIEVEMENT, WARNING, BOSS, ARRIVED, JOB }
}

/** One-off "[SYSTEM]" pop-ups for the UI (quests, achievements, penalties, boss…). */
object SystemEvents {
    private val _flow = MutableSharedFlow<Popup>(extraBufferCapacity = 32)
    val flow: SharedFlow<Popup> = _flow

    fun show(p: Popup) { _flow.tryEmit(p) }

    /** Plain message: the window style is picked from its wording. */
    fun emit(msg: String) {
        val m = msg.lowercase()
        show(when {
            m.startsWith("achievement") -> Popup(Popup.Kind.ACHIEVEMENT, "ACHIEVEMENT UNLOCKED", msg.substringAfter(": "))
            "penalty" in m || "failed" in m -> Popup(Popup.Kind.WARNING, "WARNING", msg)
            "boss" in m -> Popup(Popup.Kind.BOSS, if ("defeated" in m) "BOSS DEFEATED" else "BOSS APPEARED", msg)
            "advanced" in m || "complete" in m -> Popup(Popup.Kind.CLEAR, "LEVEL OF MASTERY RISES", msg)
            else -> Popup(Popup.Kind.NOTICE, "NOTIFICATION", msg)
        })
    }
}

// ───────────────────────── Streaks ─────────────────────────

/** A day counts as cleared when ≥ 80% of its quests are done. */
private fun cleared(qs: List<Quest>) = qs.isNotEmpty() && qs.count { it.done } >= kotlin.math.ceil(qs.size * 0.8)

suspend fun Repo.streak(): Int {
    val today = today()
    val byDay = game.questsSince(today - 120).groupBy { it.date }
    var n = if (cleared(byDay[today].orEmpty())) 1 else 0
    var d = today - 1
    while (cleared(byDay[d].orEmpty())) { n++; d-- }
    return n
}

/** +5% quest EXP per streak day, capped at +50%. */
suspend fun Repo.streakMultiplier(): Float = 1f + minOf(streak(), 10) * 0.05f

// ───────────────────────── Penalties ─────────────────────────

private val penalties = listOf(
    "50 push-ups + 50 squats, any sets",
    "20 min brisk walk, no phone",
    "Cold shower + 10 min tidy-up",
    "100 burpees… or 40 if you're honest about yesterday",
    "No social media until all quests are done",
    "Plan tomorrow in detail before 20:00",
)

/**
 * Run each morning before quests are generated:
 * - an ignored penalty quest from yesterday costs 50 DIS EXP
 * - finishing < 50% of yesterday's quests spawns a penalty quest today
 */
suspend fun Repo.applyPenalties() {
    val today = today()
    val y = today - 1
    val yq = game.questsForNow(y)
    if (yq.any { it.source == "penalty" && !it.done }) {
        award(Stat.DISCIPLINE, -50, "penaltymiss:$y")
        SystemEvents.emit("Penalty quest ignored. −50 DIS EXP.")
    }
    val normal = yq.filter { it.source != "penalty" }
    val alreadyPenalized = game.questsForNow(today).any { it.source == "penalty" }
    if (normal.isNotEmpty() && normal.count { it.done } * 2 < normal.size && !alreadyPenalized) {
        val p = penalties[(today % penalties.size).toInt()]
        game.insertQuest(Quest(date = today, title = "⚠ PENALTY: $p", detail = "Yesterday's quests were not cleared. Ignore this and lose 50 EXP.",
            stat = Stat.DISCIPLINE, xp = 20, source = "penalty"))
        SystemEvents.emit("Yesterday's quests failed. A penalty quest has been issued.")
    }
}

// ───────────────────────── Workouts & body ─────────────────────────

suspend fun Repo.addSet(exercise: String, weightKg: Float, reps: Int) = addCaliSet(exercise, 0, reps, weightKg)

suspend fun Repo.deleteSet(s: WorkoutSet) {
    fit.deleteSet(s)
    revoke("set:${s.id}")
    Widgets.refresh(ctx)
}

suspend fun Repo.logBody(weightKg: Float, waistCm: Float? = null, source: String = "manual") {
    val day = today()
    if (source == "health" && fit.bodyOn(day)?.source == "manual") return
    fit.upsertBody(BodyEntry(day, weightKg, waistCm, source))
    if (source == "manual") award(Stat.VITALITY, 5, "body:$day")
}

data class MacroSuggestion(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int, val why: String)

suspend fun Repo.suggestMacros(): MacroSuggestion {
    val o = claude.askJson(
        system = """You are a sports nutritionist. Based on the user's goals, body-weight trend, training, food log
and current targets, propose updated daily macro targets. If weight is stalling against their goal, adjust by ~100-200 kcal.
Protein ~1.6-2.2 g/kg. Return {"kcal": int, "protein": int, "carbs": int, "fat": int, "why": "2-3 sentences"}""",
        user = buildContext(),
    ).jsonObject
    return MacroSuggestion(int(o, "kcal", prefs.kcalGoal), int(o, "protein", prefs.proteinGoal),
        int(o, "carbs", prefs.carbsGoal), int(o, "fat", prefs.fatGoal), str(o, "why").orEmpty())
}

/** Claude's progression advice for one exercise. */
suspend fun Repo.progressionTip(exercise: String): String {
    val hist = fit.setsSince(today() - 90).filter { it.exercise.equals(exercise, true) }
    val lines = hist.groupBy { it.date }.toSortedMap().entries.joinToString("\n") { (d, l) ->
        "${LocalDate.ofEpochDay(d)}: " + l.joinToString(", ") { "${it.weightKg}kg x${it.reps}" }
    }
    return claude.ask(
        "You are a strength coach. Using progressive overload, give the exact target for the user's NEXT session of this exercise " +
            "(weight × reps × sets) and one technique/recovery cue. Max 4 short lines.",
        "Exercise: $exercise\nHistory:\n${lines.ifBlank { "(none yet)" }}\n\nUser context:\n${buildContext().take(3000)}",
        400,
    )
}

// ───────────────────────── Focus ─────────────────────────

suspend fun Repo.completeFocus(startTs: Long, minutes: Int) {
    award(Stat.DISCIPLINE, minutes / 2, "focus:$startTs")
    prefs.focusTotalMin = prefs.focusTotalMin + minutes
    prefs.focusEnd = 0
    game.questsForNow(today()).firstOrNull { !it.done && (it.title.contains("deep", true) || it.title.contains("focus", true)) }
        ?.let { completeQuest(it.id) }
    checkAchievements()
    Widgets.refresh(ctx)
}

// ───────────────────────── Weekly boss ─────────────────────────

fun Repo.weekStart(day: Long = today()): Long =
    LocalDate.ofEpochDay(day).with(DayOfWeek.MONDAY).toEpochDay()

suspend fun Repo.generateBoss(force: Boolean = false): String? {
    val week = weekStart()
    val existing = fit.bossNow(week)
    if (existing != null && !force) return null
    if (existing?.defeated == true) return "This week's boss is already defeated."
    if (!prefs.hasKey) {
        fit.upsertBoss(WeeklyBoss(week, "The Iron Week", "Clear your daily quests on at least 5 of 7 days.",
            "Add an API key for a personal weekly report."))
        return null
    }
    return try {
        val last = game.questsSince(week - 7).filter { it.date < week }
        val o = claude.askJson(
            system = """It's the start of the user's week in a gamified self-improvement app.
1) Write last week's report (5-8 short lines): quest completion, training, nutrition, sleep/steps, mood trends, wins, the one thing holding them back. Be specific with numbers from the data.
2) Design this week's BOSS: one ambitious-but-achievable weekly goal that attacks their biggest weakness or pushes their main goal. Give it a dramatic name.
Return {"report": "...", "boss_name": "...", "boss_goal": "concrete measurable goal for the week", "xp": 150-300}""",
            user = buildContext() + "\n\n## Last week quests: ${last.count { it.done }}/${last.size} done",
            maxTokens = 1500,
        ).jsonObject
        fit.upsertBoss(WeeklyBoss(week, str(o, "boss_name") ?: "Weekly Boss", str(o, "boss_goal").orEmpty(),
            str(o, "report").orEmpty(), int(o, "xp", 200).coerceIn(100, 400)))
        SystemEvents.emit("A new weekly boss has appeared.")
        null
    } catch (e: Exception) { e.message }
}

suspend fun Repo.defeatBoss() {
    val b = fit.bossNow(weekStart()) ?: return
    if (b.defeated) return
    fit.upsertBoss(b.copy(defeated = true))
    award(Stat.DISCIPLINE, b.xp / 2, "boss:${b.weekStart}:dis")
    gainGold(b.xp / 2, "boss:${b.weekStart}")
    award(Stat.SPIRIT, b.xp - b.xp / 2, "boss:${b.weekStart}:spi")
    SystemEvents.emit("Boss defeated: ${b.name}. +${b.xp} EXP")
    checkAchievements()
    Widgets.refresh(ctx)
}

// ───────────────────────── Achievements ─────────────────────────

class Achievement(
    val id: String,
    val name: String,
    val desc: String,
    val title: String,
    val stat: Stat,
    val xp: Int,
    val check: suspend (Repo) -> Boolean,
)

object Achievements {
    val all = listOf(
        Achievement("first_quest", "Awakening", "Complete your first quest", "The Awakened", Stat.SPIRIT, 20) { r ->
            r.game.questsSince(r.today() - 365).any { it.done } },
        Achievement("streak_7", "Unbroken", "7-day quest streak", "Unbroken", Stat.DISCIPLINE, 100) { r -> r.streak() >= 7 },
        Achievement("streak_30", "Iron Will", "30-day quest streak", "Iron Will", Stat.DISCIPLINE, 400) { r -> r.streak() >= 30 },
        Achievement("protein_7", "Fuelled", "Hit your protein goal 7 days in a row", "The Fuelled", Stat.VITALITY, 100) { r ->
            val t = r.today()
            val byDay = r.life.foodSince(t - 6).groupBy { it.date }
            (0..6).all { i -> (byDay[t - i]?.sumOf { it.protein } ?: 0) >= r.prefs.proteinGoal } },
        Achievement("checkin_7", "Self-Aware", "7 evening reports in a row", "Self-Aware", Stat.SPIRIT, 100) { r ->
            val t = r.today()
            val dates = r.life.recentCheckIns(8).map { it.date }.toSet()
            (0..6).all { (t - it) in dates } || (1..7).all { (t - it) in dates } },
        Achievement("skills_5", "Apprentice", "Acquire 5 skills", "Apprentice", Stat.INTELLECT, 100) { r ->
            r.life.skillsNow().count { it.status == "learned" } >= 5 },
        Achievement("skills_20", "Sage", "Acquire 20 skills", "Sage", Stat.INTELLECT, 300) { r ->
            r.life.skillsNow().count { it.status == "learned" } >= 20 },
        Achievement("sets_100", "Iron Initiate", "Log 100 sets", "Iron Initiate", Stat.STRENGTH, 100) { r -> r.fit.setCount() >= 100 },
        Achievement("sets_1000", "Iron Lord", "Log 1,000 sets", "Iron Lord", Stat.STRENGTH, 400) { r -> r.fit.setCount() >= 1000 },
        Achievement("steps_10k", "Wanderer", "Walk 10,000 steps in a day", "Wanderer", Stat.VITALITY, 50) { r ->
            r.fit.healthSince(r.today() - 365).any { it.steps >= 10_000 } },
        Achievement("focus_10h", "Deep Diver", "10 hours of focus blocks", "Deep Diver", Stat.DISCIPLINE, 150) { r ->
            r.prefs.focusTotalMin >= 600 },
        Achievement("boss_1", "Boss Slayer", "Defeat a weekly boss", "Boss Slayer", Stat.DISCIPLINE, 100) { r -> r.fit.bossesDefeated() >= 1 },
        Achievement("boss_5", "Raid Leader", "Defeat 5 weekly bosses", "Raid Leader", Stat.DISCIPLINE, 300) { r -> r.fit.bossesDefeated() >= 5 },
        Achievement("planche_tuck", "Lift-off", "Reach tuck planche", "Lift-off", Stat.STRENGTH, 100) { r -> r.stageOf("planche") >= 1 },
        Achievement("planche_straddle", "Straddle Sovereign", "Reach straddle planche", "Straddle Sovereign", Stat.STRENGTH, 400) { r -> r.stageOf("planche") >= 3 },
        Achievement("planche_full", "Gravity Defier", "Full planche", "Gravity Defier", Stat.STRENGTH, 1000) { r -> r.stageOf("planche") >= 5 },
        Achievement("fl_adv", "Lever Initiate", "Reach advanced tuck front lever", "Lever Initiate", Stat.STRENGTH, 150) { r -> r.stageOf("frontlever") >= 1 },
        Achievement("fl_straddle", "Horizon Walker", "Reach straddle front lever", "Horizon Walker", Stat.STRENGTH, 400) { r -> r.stageOf("frontlever") >= 3 },
        Achievement("fl_full", "Lord of the Bar", "Full front lever", "Lord of the Bar", Stat.STRENGTH, 1000) { r -> r.stageOf("frontlever") >= 5 },
        Achievement("saver_100", "Treasurer", "Save €100 in total", "Treasurer", Stat.DISCIPLINE, 100) { r -> r.db.vault().totalDepositedNow() >= 100f },
        Achievement("saver_1000", "Dragon's Hoard", "Save €1,000 in total", "Dragon's Hoard", Stat.DISCIPLINE, 400) { r -> r.db.vault().totalDepositedNow() >= 1000f },
        Achievement("first_reward", "Well Earned", "Claim your first real-life reward", "Well Earned", Stat.SPIRIT, 50) { r ->
            r.db.vault().rewardsNow().any { it.redeemedAt != null } },
        Achievement("level_10", "Climber", "Reach level 10", "Climber", Stat.SPIRIT, 100) { r ->
            Leveling.levelFor(r.game.statTotalsNow().sumOf { it.total }) >= 10 },
        Achievement("level_25", "Ascendant", "Reach level 25", "Ascendant", Stat.SPIRIT, 300) { r ->
            Leveling.levelFor(r.game.statTotalsNow().sumOf { it.total }) >= 25 },
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

suspend fun Repo.checkAchievements() {
    val have = fit.achievementsNow().map { it.id }.toSet()
    Achievements.all.filter { it.id !in have }.forEach { a ->
        val ok = try { a.check(this) } catch (_: Exception) { false }
        if (ok && fit.unlock(AchievementUnlock(a.id, System.currentTimeMillis())) != -1L) {
            award(a.stat, a.xp, "ach:${a.id}")
            gainGold(a.xp / 2, "ach:${a.id}")
            SystemEvents.emit("Achievement unlocked: ${a.name} (+${a.xp} EXP). Title \"${a.title}\" available.")
        }
    }
}
