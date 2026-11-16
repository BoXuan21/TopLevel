package com.toplevel.app.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.toplevel.app.data.HealthDay
import com.toplevel.app.data.Repo
import com.toplevel.app.widget.Widgets
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Counts steps with the phone's own step sensor, for when Health Connect isn't linked.
 * The sensor reports steps since boot, so we keep a per-day baseline.
 */
object StepCounter {

    fun available(ctx: Context): Boolean =
        (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun permitted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private suspend fun readTotal(ctx: Context): Long? {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(4000) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        }
    }

    /** Steps today from a sensor total, given the stored baseline. Pure, for tests. */
    fun stepsToday(total: Long, base: Long): Long = if (total >= base) total - base else total // reboot resets the sensor

    /** Reads the sensor, updates today's steps and auto-completes step quests. */
    suspend fun sync(repo: Repo) {
        val ctx = repo.ctx
        if (!permitted(ctx) || !available(ctx)) return
        val total = readTotal(ctx) ?: return
        val p = repo.prefs
        val day = repo.today()
        if (p.stepDay != day) {
            // New day: steps after yesterday's last reading count for today (best we can do)
            p.stepBase = if (p.stepDay >= 0 && total >= p.stepLast) p.stepLast else total
            p.stepDay = day
        } else if (total < p.stepLast) {
            // Phone rebooted today: keep what we had by shifting the baseline
            p.stepBase = -(p.stepLast - p.stepBase)
        }
        p.stepLast = total
        val steps = stepsToday(total, p.stepBase).toInt()
        val cur = repo.fit.healthSince(day).firstOrNull { it.date == day } ?: HealthDay(day)
        if (steps > cur.steps) {
            repo.fit.upsertHealth(cur.copy(steps = steps, updatedAt = System.currentTimeMillis()))
            HealthSync.autoComplete(repo, day, steps.toLong(), cur.sleepMin.toLong(), cur.workoutMin.toLong())
            Widgets.refresh(ctx)
        }
    }
}
