package com.toplevel.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** The five stats that level up. */
enum class Stat(val label: String, val short: String) {
    STRENGTH("Strength", "STR"),
    VITALITY("Vitality", "VIT"),
    INTELLECT("Intellect", "INT"),
    DISCIPLINE("Discipline", "DIS"),
    SPIRIT("Spirit", "SPI");

    companion object {
        fun parse(s: String?): Stat =
            entries.firstOrNull { it.name.equals(s?.trim(), true) || it.short.equals(s?.trim(), true) }
                ?: DISCIPLINE
    }
}

/** A daily quest. date = LocalDate.toEpochDay(). */
@Serializable
@Entity(tableName = "quests")
data class Quest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val title: String,
    val detail: String = "",
    val stat: Stat,
    val xp: Int,
    val done: Boolean = false,
    val source: String = "manual", // manual | ai | system | penalty
    /** Auto-complete from Health Connect: "steps" | "sleep" | "workout" | "" */
    val autoType: String = "",
    /** steps count, sleep minutes, or workout minutes */
    val target: Int = 0,
)

/** Every XP gain is an event. Stats and level are sums over these. */
@Serializable
@Entity(tableName = "xp_events")
data class XpEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val stat: Stat,
    val amount: Int,
    val reason: String,
)

/** Plans you paste in: training, nutrition, work, learning, goals, music taste… */
@Serializable
@Entity(tableName = "plans")
data class Plan(
    @PrimaryKey val type: String,
    val content: String,
    val updatedAt: Long,
)

object PlanType {
    const val TRAINING = "training"
    const val NUTRITION = "nutrition"
    const val WORK = "work"
    const val LEARNING = "learning"
    const val GOALS = "goals"
    const val MUSIC = "music"
    val all = listOf(GOALS, TRAINING, NUTRITION, WORK, LEARNING, MUSIC)
    fun label(t: String) = when (t) {
        TRAINING -> "Training plan"
        NUTRITION -> "Eating plan & nutrition goals"
        WORK -> "Work (role, projects, on-call…)"
        LEARNING -> "Learning goals"
        GOALS -> "Life goals / who I want to become"
        MUSIC -> "Music taste (artists, genres, songs)"
        else -> t
    }
    fun hint(t: String) = when (t) {
        TRAINING -> "Paste your full plan, e.g. Mon: Push (bench 4x8…), Tue: Run 5k…"
        NUTRITION -> "e.g. 2400 kcal, 160g protein, no pork, meal prep Sundays…"
        WORK -> "e.g. SRE at X, k8s + Terraform, on-call every 4th week…"
        LEARNING -> "e.g. CKA cert, Go, distributed systems, Spanish…"
        GOALS -> "e.g. Be lean and strong, senior SRE by 2028, calmer mornings…"
        MUSIC -> "e.g. Fred again.., Bicep, Tame Impala, lo-fi for focus…"
        else -> ""
    }
}

@Serializable
@Entity(tableName = "checkins")
data class CheckIn(
    @PrimaryKey val date: Long,
    val mood: Int,
    val energy: Int,
    val sleepHours: Float,
    val dayText: String,
    val wentWell: String,
    val improve: String,
    val reflection: String = "",
)

@Serializable
@Entity(tableName = "food")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val ts: Long,
    val name: String,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
)

/** Free logs: what I did at work, what I learned today, workouts done, notes. */
@Serializable
@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val ts: Long,
    val category: String, // work | learned | training | note
    val text: String,
)

/** Skill tree: things to learn / learning / learned. */
@Serializable
@Entity(tableName = "skills")
data class SkillItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val area: String = "",
    val status: String = "todo", // todo | learning | learned
    val notes: String = "",
    val createdAt: Long,
    val learnedAt: Long? = null,
)

@Serializable
@Entity(tableName = "chat")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val role: String, // user | assistant
    val content: String,
)

/** Long-term facts Claude saves about you (or you add yourself). */
@Serializable
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val text: String,
)

/** Daily discovery: songs + educational videos. */
@Serializable
@Entity(tableName = "discover")
data class DiscoverItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val kind: String, // song | video
    val title: String,
    val subtitle: String,
    val why: String,
    val query: String,
)

/** One logged set. */
@Serializable
@Entity(tableName = "sets")
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val ts: Long,
    val exercise: String,
    val weightKg: Float,
    val reps: Int,
    /** Static holds (planche, front lever…): seconds held. 0 for rep sets. */
    val holdSec: Int = 0,
    /** Free note: parallettes, band, RPE 9, lean angle… */
    val note: String = "",
)

/** Epley estimated 1-rep max. */
fun WorkoutSet.e1rm(): Float = if (reps <= 1) weightKg else weightKg * (1 + reps / 30f)

val WorkoutSet.isHold: Boolean get() = holdSec > 0

/** Daily snapshot pulled from Health Connect (Samsung Health, watch, etc.). */
@Serializable
@Entity(tableName = "health")
data class HealthDay(
    @PrimaryKey val date: Long,
    val steps: Int = 0,
    val sleepMin: Int = 0,
    val workoutMin: Int = 0,
    val workouts: String = "",
    val updatedAt: Long = 0,
)

@Serializable
@Entity(tableName = "body")
data class BodyEntry(
    @PrimaryKey val date: Long,
    val weightKg: Float,
    val waistCm: Float? = null,
    val source: String = "manual", // manual | health
)

/** The weekly boss: a big goal for the week + last week's report. */
@Serializable
@Entity(tableName = "bosses")
data class WeeklyBoss(
    @PrimaryKey val weekStart: Long, // epochDay of Monday
    val name: String,
    val goal: String,
    val report: String,
    val xp: Int = 200,
    val defeated: Boolean = false,
)

@Serializable
@Entity(tableName = "achievements")
data class AchievementUnlock(
    @PrimaryKey val id: String,
    val ts: Long,
)

/** Current stage on a calisthenics skill ladder (see Cali). */
@Serializable
@Entity(tableName = "skill_progress")
data class SkillProgress(
    @PrimaryKey val ladder: String,
    val stage: Int = 0,
    val bestHoldSec: Int = 0,
    val bestReps: Int = 0,
    val updatedAt: Long = 0,
)

/** One exercise in today's coached training session. */
@Serializable
@Entity(tableName = "session_items")
data class SessionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val pos: Int,
    val block: String = "", // warm-up | skill | strength | core | cool-down
    val exercise: String,
    val target: String,
    val rest: String = "",
    val note: String = "",
    val kind: String = "reps", // reps | hold
    val done: Boolean = false,
)

/** Gold: the currency you spend on real-life rewards. */
@Serializable
@Entity(tableName = "gold")
data class GoldEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val amount: Int,
    val reason: String,
)

/**
 * A real-life treat. To claim it you need: enough Gold, enough money saved,
 * the unlock condition met, and a 72h cool-down since you added it (no impulse buys).
 * condition: "" | "level:10" | "ach:streak_7" | "cali:planche:3"
 */
@Serializable
@Entity(tableName = "rewards")
data class Reward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priceEur: Float,
    val goldCost: Int,
    val condition: String = "",
    val createdAt: Long,
    val redeemedAt: Long? = null,
    val note: String = "",
)

/** Money moved into savings (positive) or spent on a reward (negative). */
@Serializable
@Entity(tableName = "savings")
data class SavingsEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val date: Long,
    val amountEur: Float,
    val note: String = "",
    val rewardId: Long? = null,
)

/** Spaced-repetition card, usually made from a "learned" journal entry. */
@Serializable
@Entity(tableName = "flashcards")
data class Flashcard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val front: String,
    val back: String,
    /** epochDay when the card is next due */
    val due: Long,
    /** days until next review after a "good" answer */
    val interval: Int = 0,
    val ease: Float = 2.5f,
    val reps: Int = 0,
    val createdAt: Long,
    /** LogEntry id it was made from (0 = manual) */
    val sourceLog: Long = 0,
    /** What you're learning that this card quizzes (a learning topic), "" = from the journal / manual */
    @androidx.room.ColumnInfo(defaultValue = "") val topic: String = "",
)

/** Money spent (positive €). */
@Serializable
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val date: Long,
    val amountEur: Float,
    val category: String,
    val note: String = "",
)
