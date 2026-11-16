package com.toplevel.app.data

import com.toplevel.app.ai.Claude.Companion.str
import com.toplevel.app.game.Leveling
import com.toplevel.app.widget.Widgets
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.time.LocalDate

// ───────────────────────── Calisthenics ladders ─────────────────────────

object Cali {
    class Ladder(val id: String, val name: String, val stages: List<String>, val hold: Boolean = true)

    private val defaults = listOf(
        Ladder("planche", "Planche", listOf("Planche lean", "Tuck", "Advanced tuck", "Straddle", "Half lay", "Full",
            "Full planche push-up", "Maltese")),
        Ladder("frontlever", "Front lever", listOf("Tuck", "Advanced tuck", "One leg", "Straddle", "Half lay", "Full",
            "Full FL pull-up", "One-arm FL")),
        Ladder("backlever", "Back lever", listOf("Skin the cat", "Tuck", "Advanced tuck", "Straddle", "Half lay", "Full",
            "Full BL pull-out", "Iron cross prep")),
        Ladder("handstand", "Handstand", listOf("Wall hold", "Chest-to-wall", "Freestanding", "Press to handstand",
            "Straddle press", "Figa", "One-arm (supported)", "One-arm handstand")),
        Ladder("muscleup", "Muscle-up", listOf("Chest-to-bar", "Strict", "Strict ×5", "Weighted +10kg",
            "Weighted +20kg", "Slow strict", "One-arm prep"), hold = false),
        Ladder("hspu", "Handstand push-up", listOf("Pike push-up", "Wall HSPU", "Deficit wall HSPU", "Freestanding partial",
            "Freestanding", "Deficit freestanding", "90° push-up"), hold = false),
    )

    /** Your own stage names per ladder (Setup in Train → "Edit stages"). */
    val overrides = mutableMapOf<String, List<String>>()

    val ladders: List<Ladder>
        get() = defaults.map { d -> overrides[d.id]?.takeIf { it.size >= 2 }?.let { Ladder(d.id, d.name, it, d.hold) } ?: d }

    fun defaultStages(id: String) = defaults.firstOrNull { it.id == id }?.stages.orEmpty()

    /** Hold this long (or this many reps) on the current stage, cleanly, to qualify for the next. */
    const val QUALIFY_HOLD = 12
    const val QUALIFY_REPS = 5

    fun byId(id: String) = ladders.firstOrNull { it.id == id }
    fun exercise(l: Ladder, stage: Int) = "${l.name} · ${l.stages[stage.coerceIn(0, l.stages.lastIndex)]}"

    /** Finds the ladder + stage an exercise name refers to, e.g. "Planche · Tuck". */
    fun match(exercise: String): Pair<Ladder, Int>? {
        ladders.forEach { l ->
            l.stages.forEachIndexed { i, st ->
                if (exercise.equals(exercise(l, i), true)) return l to i
            }
        }
        return null
    }
}

fun Repo.setLadderStages(id: String, stages: List<String>) {
    val clean = stages.map { it.trim() }.filter { it.isNotEmpty() }
    prefs.setLadderStages(id, clean)
    if (clean.size >= 2) Cali.overrides[id] = clean else Cali.overrides.remove(id)
}

fun Repo.trackedLadders(): List<Cali.Ladder> =
    prefs.trackedLadders.split(",").mapNotNull { Cali.byId(it.trim()) }

/** Log a calisthenics hold or rep set and update the ladder's best. */
suspend fun Repo.addCaliSet(exercise: String, holdSec: Int, reps: Int, weightKg: Float = 0f, note: String = "") {
    val day = today()
    val before = fit.setCountFor(day)
    val id = fit.insertSet(WorkoutSet(date = day, ts = System.currentTimeMillis(), exercise = exercise.trim(),
        weightKg = weightKg, reps = reps, holdSec = holdSec, note = note.trim()))
    if (before < 12) award(Stat.STRENGTH, 3, "set:$id")
    Cali.match(exercise)?.let { (l, stage) ->
        val v = db.vault()
        val p = v.progressOf(l.id) ?: SkillProgress(l.id)
        if (stage == p.stage) {
            val np = p.copy(bestHoldSec = maxOf(p.bestHoldSec, holdSec), bestReps = maxOf(p.bestReps, reps),
                updatedAt = System.currentTimeMillis())
            v.upsertProgress(np)
            val qualified = if (l.hold) np.bestHoldSec >= Cali.QUALIFY_HOLD else np.bestReps >= Cali.QUALIFY_REPS
            val was = if (l.hold) p.bestHoldSec >= Cali.QUALIFY_HOLD else p.bestReps >= Cali.QUALIFY_REPS
            if (qualified && !was && stage < l.stages.lastIndex)
                SystemEvents.emit("${l.name}: you qualify for ${l.stages[stage + 1]}. Advance when you're ready.")
        }
    }
    if (before + 1 == 6) {
        game.questsForNow(day).firstOrNull { !it.done && it.stat == Stat.STRENGTH && it.autoType == "" }
            ?.let { toggleQuest(it.id) }
    }
    checkAchievements()
    Widgets.refresh(ctx)
}

/** reward = false when you're just setting your current level (no EXP farming). */
suspend fun Repo.setStage(ladderId: String, stage: Int, reward: Boolean = true) {
    val l = Cali.byId(ladderId) ?: return
    val v = db.vault()
    val p = v.progressOf(ladderId) ?: SkillProgress(ladderId)
    val s = stage.coerceIn(0, l.stages.lastIndex)
    v.upsertProgress(p.copy(stage = s, bestHoldSec = 0, bestReps = 0, updatedAt = System.currentTimeMillis()))
    if (reward && s > p.stage) {
        // Reward each new stage reached once
        award(Stat.STRENGTH, 80 + s * 20, "stage:$ladderId:$s")
        gainGold(50 + s * 25, "stage:$ladderId:$s")
        SystemEvents.emit("${l.name} advanced to ${l.stages[s]}! +${80 + s * 20} STR, +${50 + s * 25} Gold")
    }
    checkAchievements()
    Widgets.refresh(ctx)
}

suspend fun Repo.stageOf(ladderId: String): Int = db.vault().progressOf(ladderId)?.stage ?: 0

/** Claude builds today's calisthenics session from your ladders, history, recovery and plan. */
suspend fun Repo.generateSession(): String? {
    if (!prefs.hasKey) return "Add your API key to get coached sessions."
    return try {
        val v = db.vault()
        val withProgress = trackedLadders().map { l -> l to (v.progressOf(l.id) ?: SkillProgress(l.id)) }
        val ladderInfo = withProgress.joinToString("\n") { (l, p) ->
            "- ${l.name}: current stage ${l.stages[p.stage]} (stage ${p.stage + 1}/${l.stages.size}), " +
                "best ${if (l.hold) "${p.bestHoldSec}s hold" else "${p.bestReps} reps"}; next: ${l.stages.getOrNull(p.stage + 1) ?: "mastered"}"
        }
        val arr = claude.askJson(
            system = """You are an elite calisthenics coach (planche/front lever specialist). Build TODAY's session.
Principles: skill work first while fresh; static holds in sets of short max-effort holds (e.g. 5-8 x 6-10s) at the current stage
plus one easier stage for volume; support strength (pseudo planche push-ups, planche leans, tuck/adv-tuck front lever rows,
ice cream makers, hollow body, scap pulls, dips, pull-ups); straight-arm conditioning; wrist & elbow prep warm-up.
Respect recovery: if sleep was poor or they trained the same pattern yesterday, reduce volume or make it a mobility/technique day.
Follow the user's own training plan/split for this weekday if they have one.
For skill holds use EXACT exercise names in the form "<Skill> · <Stage>" (e.g. "Planche · Tuck", "Front lever · Advanced tuck") so logging links to progress.
Return [{"block": "warm-up|skill|strength|core|cool-down", "exercise": "...", "target": "e.g. 6 × 8s or 4 × 8 reps",
"rest": "e.g. 2 min", "kind": "hold|reps", "note": "one short cue"}] with 6-12 items.""",
            user = buildContext() + "\n\n## Calisthenics ladders\n" + ladderInfo,
            maxTokens = 2500,
        ) as? JsonArray ?: JsonArray(emptyList())
        val day = today()
        val items = arr.mapNotNull { it as? JsonObject }.mapIndexedNotNull { i, o ->
            val ex = str(o, "exercise") ?: return@mapIndexedNotNull null
            SessionItem(date = day, pos = i, block = str(o, "block").orEmpty(), exercise = ex,
                target = str(o, "target").orEmpty(), rest = str(o, "rest").orEmpty(), note = str(o, "note").orEmpty(),
                kind = if (str(o, "kind") == "hold") "hold" else "reps")
        }
        if (items.isEmpty()) return "Claude returned an empty session."
        v.clearSession(day)
        v.insertSession(items)
        null
    } catch (e: Exception) { e.message }
}

suspend fun Repo.toggleSessionItem(item: SessionItem) {
    val v = db.vault()
    v.updateSessionItem(item.copy(done = !item.done))
    val all = v.sessionNow(item.date)
    if (all.isNotEmpty() && all.all { it.done }) {
        award(Stat.STRENGTH, 40, "session:${item.date}")
        gainGold(20, "session:${item.date}")
        game.questsForNow(item.date).filter { !it.done && it.stat == Stat.STRENGTH && it.autoType == "" }
            .forEach { toggleQuest(it.id) }
        SystemEvents.emit("Training session complete. +40 STR, +20 Gold")
    } else {
        revoke("session:${item.date}")
        loseGold("session:${item.date}")
    }
    Widgets.refresh(ctx)
}

// ───────────────────────── Gold ─────────────────────────

suspend fun Repo.gainGold(amount: Int, reason: String) {
    val v = db.vault()
    v.deleteGoldByReason(reason)
    if (amount != 0) v.insertGold(GoldEvent(ts = System.currentTimeMillis(), amount = amount, reason = reason))
}

suspend fun Repo.loseGold(reason: String) = db.vault().deleteGoldByReason(reason)

// ───────────────────────── Savings ─────────────────────────

/** Put money aside. Saving pays Discipline EXP and Gold, which makes rewards reachable sooner. */
suspend fun Repo.deposit(amountEur: Float, note: String = "") {
    if (amountEur <= 0f) return
    val ts = System.currentTimeMillis()
    val id = db.vault().insertSavings(SavingsEntry(ts = ts, date = today(), amountEur = amountEur, note = note))
    award(Stat.DISCIPLINE, (amountEur / 2).toInt().coerceIn(1, 50), "save:$id")
    gainGold((amountEur / 2).toInt().coerceIn(1, 50), "save:$id")
    game.questsForNow(today()).firstOrNull {
        !it.done && (it.title.contains("sav", true) || it.title.contains("spar", true) || it.title.contains("€"))
    }?.let { toggleQuest(it.id) }
    checkAchievements()
    Widgets.refresh(ctx)
}

suspend fun Repo.deleteSavings(s: SavingsEntry) {
    db.vault().deleteSavings(s)
    revoke("save:${s.id}")
    loseGold("save:${s.id}")
}

// ───────────────────────── Rewards ─────────────────────────

const val REWARD_COOLDOWN_MS = 72L * 60 * 60 * 1000

data class RewardStatus(
    val goldOk: Boolean,
    val moneyOk: Boolean,
    val conditionOk: Boolean,
    val cooldownOk: Boolean,
    val conditionLabel: String,
    val cooldownLeftH: Long,
) { val ready get() = goldOk && moneyOk && conditionOk && cooldownOk }

fun conditionLabel(c: String): String {
    val parts = c.split(":")
    return when (parts.getOrNull(0)) {
        "level" -> "Reach level ${parts.getOrNull(1)}"
        "ach" -> "Unlock \"${Achievements.byId(parts.getOrNull(1).orEmpty())?.name ?: parts.getOrNull(1)}\""
        "cali" -> {
            val l = Cali.byId(parts.getOrNull(1).orEmpty())
            val st = parts.getOrNull(2)?.toIntOrNull() ?: 0
            if (l != null) "${l.name}: reach ${l.stages[st.coerceIn(0, l.stages.lastIndex)]}" else c
        }
        else -> "No condition"
    }
}

suspend fun Repo.rewardStatus(r: Reward, gold: Int, pot: Float): RewardStatus {
    val parts = r.condition.split(":")
    val condOk = when (parts.getOrNull(0)) {
        "level" -> Leveling.levelFor(game.statTotalsNow().sumOf { it.total }) >= (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        "ach" -> fit.achievementsNow().any { it.id == parts.getOrNull(1) }
        "cali" -> stageOf(parts.getOrNull(1).orEmpty()) >= (parts.getOrNull(2)?.toIntOrNull() ?: 0)
        else -> true
    }
    val left = r.createdAt + REWARD_COOLDOWN_MS - System.currentTimeMillis()
    return RewardStatus(gold >= r.goldCost, pot >= r.priceEur, condOk, left <= 0,
        conditionLabel(r.condition), if (left > 0) (left / 3_600_000L) + 1 else 0)
}

suspend fun Repo.addReward(name: String, priceEur: Float, goldCost: Int? = null, condition: String = "", note: String = "") {
    db.vault().insertReward(Reward(name = name.trim(), priceEur = priceEur,
        goldCost = goldCost ?: defaultGoldCost(priceEur), condition = condition, createdAt = System.currentTimeMillis(), note = note))
}

fun defaultGoldCost(priceEur: Float): Int = (priceEur * 3).toInt().coerceAtLeast(50)

/** Claim it: spends the Gold and takes the money out of savings. Returns error or null. */
suspend fun Repo.redeem(r: Reward): String? {
    val v = db.vault()
    val st = rewardStatus(r, v.goldBalanceNow(), v.potNow())
    if (!st.ready) return "Not yet: " + listOfNotNull(
        if (!st.goldOk) "need ${r.goldCost} Gold" else null,
        if (!st.moneyOk) "save €${"%.0f".format(r.priceEur)}" else null,
        if (!st.conditionOk) st.conditionLabel else null,
        if (!st.cooldownOk) "cool-down ${st.cooldownLeftH}h" else null,
    ).joinToString(", ")
    gainGold(-r.goldCost, "reward:${r.id}")
    v.insertSavings(SavingsEntry(ts = System.currentTimeMillis(), date = today(), amountEur = -r.priceEur,
        note = "Reward: ${r.name}", rewardId = r.id))
    v.updateReward(r.copy(redeemedAt = System.currentTimeMillis()))
    award(Stat.SPIRIT, 30, "reward:${r.id}")
    checkAchievements()
    Widgets.refresh(ctx)
    return null
}

/** "I didn't buy it": the money goes to savings instead. */
suspend fun Repo.skippedPurchase(what: String, amountEur: Float) {
    deposit(amountEur, "Skipped: $what")
    SystemEvents.emit("Impulse resisted: €${"%.0f".format(amountEur)} saved instead of spent.")
}

/** Monday of the month start, used for "saved this month". */
fun Repo.monthStart(): Long = LocalDate.now().withDayOfMonth(1).toEpochDay()
