package com.toplevel.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.toplevel.app.data.HealthDay
import com.toplevel.app.data.Repo
import com.toplevel.app.data.checkAchievements
import com.toplevel.app.data.logBody
import com.toplevel.app.widget.Widgets
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pulls steps, sleep, workouts and weight from Health Connect
 * (Samsung Health syncs into it — enable that in Samsung Health → Settings → Health Connect).
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

    /** Syncs today's data and auto-completes measurable quests. Returns an error message or null. */
    suspend fun sync(repo: Repo): String? {
        val ctx = repo.ctx
        val ok = grantedSet(ctx)
        if (STEPS !in ok) return "Health Connect not connected"
        return try {
            val client = HealthConnectClient.getOrCreate(ctx)
            val z = ZoneId.systemDefault()
            val today = LocalDate.now()
            val dayStart = today.atStartOfDay(z).toInstant()
            val now = java.time.Instant.now()

            val hcSteps = client.aggregate(
                AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(dayStart, now))
            )[StepsRecord.COUNT_TOTAL] ?: 0L

            // Sleep that ended between yesterday 18:00 and now = "last night"
            val sleepFrom = today.minusDays(1).atTime(18, 0).atZone(z).toInstant()
            val sleepMin = if (SLEEP !in ok) 0L else client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(sleepFrom, now))
            ).records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }

            val sessions = if (EXERCISE !in ok) emptyList() else client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(dayStart, now))
            ).records
            val workoutMin = sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            val workoutNames = sessions.joinToString(", ") { it.title ?: "workout" }

            val weight = if (WEIGHT !in ok) null else client.readRecords(
                ReadRecordsRequest(WeightRecord::class,
                    TimeRangeFilter.between(today.minusDays(1).atStartOfDay(z).toInstant(), now))
            ).records.maxByOrNull { it.time }?.weight?.inKilograms

            val day = today.toEpochDay()
            // Never go below what the phone's own step sensor already counted today
            val cur = repo.fit.healthSince(day).firstOrNull { it.date == day }
            val steps = maxOf(hcSteps, (cur?.steps ?: 0).toLong())
            repo.fit.upsertHealth(HealthDay(day, steps.toInt(), sleepMin.toInt(), workoutMin.toInt(),
                workoutNames, System.currentTimeMillis()))
            if (weight != null) repo.logBody(weight.toFloat(), source = "health")

            autoComplete(repo, day, steps, sleepMin, workoutMin)
            repo.checkAchievements()
            Widgets.refresh(ctx)
            null
        } catch (e: Exception) {
            e.message ?: "Health sync failed"
        }
    }

    /** Completes measurable quests ("8,000 steps", "7 h sleep", "45 min workout") once reached. */
    suspend fun autoComplete(repo: Repo, day: Long, steps: Long, sleepMin: Long, workoutMin: Long) {
        repo.game.questsForNow(day).filter { !it.done && it.autoType.isNotEmpty() }.forEach { q ->
            val reached = when (q.autoType) {
                "steps" -> steps >= q.target
                "sleep" -> sleepMin >= q.target
                "workout" -> workoutMin >= q.target
                else -> false
            }
            if (reached && q.target > 0) repo.completeQuest(q.id)
        }
    }
}
