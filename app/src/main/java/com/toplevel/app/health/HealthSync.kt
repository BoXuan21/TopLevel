package com.toplevel.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.toplevel.app.data.HealthDay
import com.toplevel.app.data.Repo
import com.toplevel.app.data.checkAchievements
import com.toplevel.app.data.logBody
import com.toplevel.app.widget.Widgets
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Pulls steps, sleep, workouts and weight from Health Connect
 * (Samsung Health syncs into it — enable that in Samsung Health → Settings → Health Connect).
 *
 * Samsung Health pushes to Health Connect in batches, often hours late, so every sync re-reads the last
 * [BACKFILL_DAYS] days: steps or a night's sleep that arrive late still land on the right day.
 */
object HealthSync {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    /** Lets the hourly worker read while the app is closed (ignored on older Health Connect). */
    const val BACKGROUND_READ = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    val requestPermissions = permissions + BACKGROUND_READ

    /** Today plus this many days back are re-read on every sync. */
    private const val BACKFILL_DAYS = 6L

    fun available(ctx: Context): Boolean =
        HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    private val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    private val SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)

    private suspend fun grantedSet(ctx: Context): Set<String> {
        if (!available(ctx)) return emptySet()
        return try {
            HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
        } catch (_: Exception) { emptySet() }
    }

    /** Connected = at least steps are readable. Sleep, workouts and weight are optional extras. */
    suspend fun granted(ctx: Context): Boolean = STEPS in grantedSet(ctx)

    /** What the last sync found, in plain words (shown after "Sync now"). */
    @Volatile var lastSummary: String = ""
        private set

    /**
     * Syncs the last week and auto-completes measurable quests (today's, and yesterday's when its data came in late).
     * Each kind of data is read on its own, so a failing read (e.g. sleep) never blocks steps.
     * Returns an error message or null.
     */
    suspend fun sync(repo: Repo): String? {
        val ctx = repo.ctx
        val ok = grantedSet(ctx)
        if (STEPS !in ok) return "Health Connect not connected"
        val client = try { HealthConnectClient.getOrCreate(ctx) } catch (e: Exception) {
            return e.message ?: "Health Connect unavailable"
        }
        val z = ZoneId.systemDefault()
        val today = LocalDate.now()
        val first = today.minusDays(BACKFILL_DAYS)
        val now = Instant.now()
        val errors = mutableListOf<String>()

        // Steps per day in one call. Health Connect de-duplicates phone + watch + other apps by its app priority list.
        val steps = mutableMapOf<Long, Long>()
        var stepSources = emptySet<String>()
        try {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(first.atStartOfDay(), today.plusDays(1).atStartOfDay()), Period.ofDays(1))
            ).forEach { g ->
                g.result[StepsRecord.COUNT_TOTAL]?.let { steps[g.startTime.toLocalDate().toEpochDay()] = it }
                if (g.startTime.toLocalDate() == today) stepSources = g.result.dataOrigins.map { it.packageName }.toSet()
            }
        } catch (e: Exception) { errors += "steps: ${e.message}" }

        // Sleep: a night counts for the day it ends on (sessions ending 18:00 the evening before → 18:00 that day).
        val sleep = mutableMapOf<Long, Long>()
        if (SLEEP in ok) try {
            val sessions = readAll(client, SleepSessionRecord::class,
                TimeRangeFilter.between(first.minusDays(1).atTime(18, 0).atZone(z).toInstant(), now))
            for (d in 0..BACKFILL_DAYS) {
                val day = first.plusDays(d)
                val from = day.minusDays(1).atTime(18, 0).atZone(z).toInstant()
                val to = day.atTime(18, 0).atZone(z).toInstant()
                val nights = sessions.filter { it.endTime > from && it.endTime <= to }
                if (nights.isNotEmpty()) sleep[day.toEpochDay()] = asleepMinutes(nights)
            }
        } catch (e: Exception) { errors += "sleep: ${e.message}" }

        // Workouts: by the day they started. Overlapping copies (watch + phone app) count once.
        val workouts = mutableMapOf<Long, Pair<Long, String>>()
        if (EXERCISE in ok) try {
            readAll(client, ExerciseSessionRecord::class, TimeRangeFilter.between(first.atStartOfDay(z).toInstant(), now))
                .groupBy { it.startTime.atZone(z).toLocalDate().toEpochDay() }
                .forEach { (day, list) ->
                    val min = merged(list.map { it.startTime to it.endTime }).sumOf { minutes(it.first, it.second) }
                    val names = list.map { it.title?.takeIf { t -> t.isNotBlank() } ?: typeName(it.exerciseType) }.distinct()
                    workouts[day] = min to names.joinToString(", ")
                }
        } catch (e: Exception) { errors += "workouts: ${e.message}" }

        // Weight: only a weigh-in from today counts as today's weight.
        var weight: Double? = null
        if (WEIGHT in ok) try {
            weight = readAll(client, WeightRecord::class, TimeRangeFilter.between(today.atStartOfDay(z).toInstant(), now))
                .maxByOrNull { it.time }?.weight?.inKilograms
        } catch (e: Exception) { errors += "weight: ${e.message}" }

        if (steps.isEmpty() && sleep.isEmpty() && workouts.isEmpty() && errors.isNotEmpty())
            return "Health sync failed (${errors.joinToString("; ")})"

        // Write each day; never lower what we already had (the phone's own step sensor, or an earlier read)
        val stored = repo.fit.healthSince(first.toEpochDay()).associateBy { it.date }
        val todayId = today.toEpochDay()
        val changed = mutableSetOf<Long>()
        for (d in 0..BACKFILL_DAYS) {
            val day = first.plusDays(d).toEpochDay()
            val cur = stored[day]
            if (cur == null && day !in steps && day !in sleep && day !in workouts && day != todayId) continue
            val base = cur ?: HealthDay(day)
            val w = workouts[day]
            val next = base.copy(
                steps = maxOf(steps[day] ?: 0L, base.steps.toLong()).toInt(),
                sleepMin = sleep[day]?.toInt() ?: base.sleepMin,
                workoutMin = w?.first?.toInt() ?: base.workoutMin,
                workouts = w?.second ?: base.workouts,
            )
            if (next != base || cur == null || day == todayId) {
                repo.fit.upsertHealth(next.copy(updatedAt = System.currentTimeMillis()))
                if (next != base) changed += day
            }
        }
        if (weight != null) repo.logBody(weight.toFloat(), source = "health")

        // Today's quests, and yesterday's if late data just arrived for it
        val after = repo.fit.healthSince(todayId - 1).associateBy { it.date }
        listOf(todayId, todayId - 1).filter { it == todayId || it in changed }.forEach { day ->
            after[day]?.let { autoComplete(repo, day, it.steps.toLong(), it.sleepMin.toLong(), it.workoutMin.toLong()) }
        }
        repo.checkAchievements()
        Widgets.refresh(ctx)

        lastSummary = summary(after[todayId], ok, stepSources, errors)
        return null
    }

    private fun summary(t: HealthDay?, ok: Set<String>, stepSources: Set<String>, errors: List<String>) = buildString {
        append("Synced ✓ ")
        append("%,d steps".format(t?.steps ?: 0))
        if (SLEEP in ok) append(" · sleep ${(t?.sleepMin ?: 0) / 60}h${"%02d".format((t?.sleepMin ?: 0) % 60)}")
        if (EXERCISE in ok) append(" · ${t?.workoutMin ?: 0} min workouts")
        val missing = listOfNotNull(
            "sleep".takeIf { SLEEP !in ok }, "workouts".takeIf { EXERCISE !in ok }, "weight".takeIf { WEIGHT !in ok })
        if (missing.isNotEmpty()) append("\nNot allowed yet: ${missing.joinToString()} (tap Permissions).")
        if (stepSources.none { it.startsWith(SAMSUNG_HEALTH) })
            append("\nNo steps from Samsung Health today. In Samsung Health: Settings → Health Connect → allow it to share steps, sleep and exercise.")
        if (errors.isNotEmpty()) append("\nSkipped: ${errors.joinToString("; ")}")
    }

    private const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"

    /** Reads every page (Health Connect returns at most 1000 records per call). */
    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val r = client.readRecords(ReadRecordsRequest(type, filter, pageToken = token))
            out += r.records
            token = r.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    /**
     * Time asleep: the union of all sessions (so Samsung Health + another app writing the same night count once),
     * minus the stages marked awake / out of bed.
     */
    internal fun asleepMinutes(sessions: List<SleepSessionRecord>): Long {
        val inBed = merged(sessions.map { it.startTime to it.endTime })
        val awakeTypes = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)
        val awake = merged(sessions.flatMap { s -> s.stages.filter { it.stage in awakeTypes }.map { it.startTime to it.endTime } })
        return (inBed.sumOf { minutes(it.first, it.second) } - awake.sumOf { minutes(it.first, it.second) }).coerceAtLeast(0)
    }

    /** Union of time intervals. */
    internal fun merged(spans: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> {
        val out = mutableListOf<Pair<Instant, Instant>>()
        spans.filter { it.second > it.first }.sortedBy { it.first }.forEach { s ->
            val last = out.lastOrNull()
            if (last != null && s.first <= last.second) out[out.size - 1] = last.first to maxOf(last.second, s.second)
            else out += s
        }
        return out
    }

    private fun minutes(a: Instant, b: Instant) = java.time.Duration.between(a, b).toMinutes()

    /** Samsung Health usually leaves the title empty; name the workout by its type instead. */
    private fun typeName(t: Int): String = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Strength training"
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Yoga / pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Football"
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Basketball"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tennis"
        ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Martial arts"
        ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "Climbing"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dance"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE, ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "Rowing"
        else -> "Workout"
    }

    /** Completes measurable quests ("8,000 steps", "7 h sleep", "45 min workout") once reached. */
    suspend fun autoComplete(repo: Repo, day: Long, steps: Long, sleepMin: Long, workoutMin: Long) {
        repo.game.questsForNow(day).filter { !it.done && it.autoType.isNotEmpty() }.forEach { q ->
            val reached = when (q.autoType) {
                "steps" -> steps >= q.target
                "sleep" -> sleepMin >= q.target
                "workout" -> maxOf(workoutMin, repo.prefs.sportMin(day).toLong()) >= q.target // watch or logged by hand
                else -> false
            }
            if (reached && q.target > 0) repo.completeQuest(q.id)
        }
    }
}
