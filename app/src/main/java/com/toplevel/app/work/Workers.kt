package com.toplevel.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.toplevel.app.MainActivity
import com.toplevel.app.R
import com.toplevel.app.data.Repo
import com.toplevel.app.data.applyPenalties
import com.toplevel.app.data.completeFocus
import com.toplevel.app.data.generateBoss
import com.toplevel.app.health.HealthSync
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.toplevel.app.widget.Widgets
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Notifier {
    const val CHANNEL = "daily"

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily quests & check-ins", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun show(ctx: Context, id: Int, title: String, text: String, open: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open", open)
        }
        val pi = PendingIntent.getActivity(ctx, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }
}

/** Morning: build quests + discovery feed, then notify. */
class MorningWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = Repo.get(applicationContext)
        val day = repo.today()
        if (repo.prefs.lastQuestGen != day) {
            repo.applyPenalties()
            repo.generateQuests()
        }
        repo.generateDiscover()
        repo.generateBoss()
        HealthSync.sync(repo)
        val quests = repo.game.questsForNow(day)
        Notifier.show(applicationContext, 1, "⚔ Daily quests are ready",
            quests.take(4).joinToString("\n") { "• ${it.title}" }.ifBlank { "Open TopLevel to start your day." },
            "status")
        Widgets.refresh(applicationContext)
        return Result.success()
    }
}

/** Evening: ask how the day was if not checked in yet. */
class EveningWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = Repo.get(applicationContext)
        val day = repo.today()
        HealthSync.sync(repo)
        if (repo.life.checkInNow(day) == null) {
            val open = repo.game.questsForNow(day).count { !it.done }
            Notifier.show(applicationContext, 2, "How was your day?",
                "Take 2 minutes for your check-in (+30 Spirit XP)." +
                    if (open > 0) " $open quest(s) still open." else " All quests cleared!",
                "checkin")
        }
        return Result.success()
    }
}

/** Hourly: pull health data and auto-complete quests. */
class HealthWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        HealthSync.sync(Repo.get(applicationContext))
        Widgets.refresh(applicationContext) // also rolls widgets over to the new day
        return Result.success()
    }
}

/** Fires when a focus block ends. */
class FocusWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = Repo.get(applicationContext)
        val start = inputData.getLong("start", 0)
        val minutes = inputData.getInt("minutes", 0)
        if (repo.prefs.focusStart == start && repo.prefs.focusEnd != 0L) {
            repo.completeFocus(start, minutes)
            Notifier.show(applicationContext, 3, "Focus block complete",
                "$minutes min of deep work. +${minutes / 2} DIS EXP.", "status")
        }
        return Result.success()
    }
}

object Focus {
    fun start(ctx: Context, minutes: Int) {
        val repo = Repo.get(ctx)
        val now = System.currentTimeMillis()
        repo.prefs.focusStart = now
        repo.prefs.focusLen = minutes
        repo.prefs.focusEnd = now + minutes * 60_000L
        WorkManager.getInstance(ctx).enqueueUniqueWork("focus", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FocusWorker>()
                .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf("start" to now, "minutes" to minutes))
                .build())
    }

    fun cancel(ctx: Context) {
        Repo.get(ctx).prefs.focusEnd = 0
        WorkManager.getInstance(ctx).cancelUniqueWork("focus")
    }
}

object Scheduler {
    private fun delayUntil(hour: Int): Long {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMinutes()
    }

    fun schedule(ctx: Context, morningHour: Int, eveningHour: Int, replace: Boolean = false) {
        val wm = WorkManager.getInstance(ctx)
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        wm.enqueueUniquePeriodicWork("morning", policy,
            PeriodicWorkRequestBuilder<MorningWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(morningHour), TimeUnit.MINUTES).build())
        wm.enqueueUniquePeriodicWork("evening", policy,
            PeriodicWorkRequestBuilder<EveningWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(eveningHour), TimeUnit.MINUTES).build())
        wm.enqueueUniquePeriodicWork("health", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HealthWorker>(1, TimeUnit.HOURS).build())
    }
}
