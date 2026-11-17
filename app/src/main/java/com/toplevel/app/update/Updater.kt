package com.toplevel.app.update

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.toplevel.app.R
import com.toplevel.app.data.Repo
import com.toplevel.app.work.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Self-update from this repo's GitHub releases (every push to main publishes "build-N" with TopLevel.apk).
 * Android checks that the new APK has the same signature before it replaces the app.
 * The first update asks you to confirm (and to allow TopLevel to install apps); after that TopLevel is the
 * app's installer and later updates go through without a prompt (Android 12+).
 */
object Updater {
    private const val REPO = "BoXuan21/TopLevel"
    private const val ASSET = "TopLevel.apk"
    private const val NOTIFY_ID = 7

    data class Release(val build: Int, val apkUrl: String, val size: Long)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    fun currentBuild(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    /** "build-16" → 16 */
    fun buildOf(tag: String): Int? = tag.removePrefix("build-").toIntOrNull()

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "TopLevel-app").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val o = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            val build = buildOf(o["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()) ?: return@withContext null
            val asset = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == ASSET } ?: return@withContext null
            Release(build, asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@withContext null,
                asset["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0)
        }
    }

    /**
     * Checks and, if there's a newer build, downloads and installs it.
     * Returns a short status for the UI.
     */
    suspend fun checkAndInstall(ctx: Context, manual: Boolean = false): String {
        // Local/debug builds (Android Studio, emulator tests) never replace themselves on their own
        val debuggable = ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && !manual) return "Debug build: automatic updates are off"
        val rel = latest() ?: return "Couldn't reach GitHub. Try again later."
        val cur = currentBuild(ctx)
        if (rel.build <= cur) return "Up to date (build $cur)"
        val file = download(ctx, rel)
        verify(ctx, file, rel.build)
        install(ctx, file)
        return "Installing build ${rel.build}…"
    }

    private suspend fun download(ctx: Context, rel: Release): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != "TopLevel-${rel.build}.apk" }?.forEach { it.delete() }
        val out = File(dir, "TopLevel-${rel.build}.apk")
        if (out.exists() && rel.size > 0 && out.length() == rel.size) return@withContext out
        val tmp = File(dir, "download.part")
        val req = Request.Builder().url(rel.apkUrl).header("User-Agent", "TopLevel-app").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Download failed (${r.code})")
            tmp.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
        }
        if (!tmp.renameTo(out)) throw IllegalStateException("Couldn't save the update")
        out
    }

    /** Make sure it's really TopLevel and really newer before handing it to Android. */
    private fun verify(ctx: Context, file: File, build: Int) {
        val info = ctx.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: run { file.delete(); throw IllegalStateException("The downloaded update is damaged") }
        if (info.packageName != ctx.packageName || PackageInfoCompat.getLongVersionCode(info) < build) {
            file.delete(); throw IllegalStateException("The downloaded file isn't a TopLevel update")
        }
    }

    private fun install(ctx: Context, file: File) {
        // Android aborts this install if it first has to send you to "allow installs"; remember that, so the
        // app retries as soon as you're back with the permission (see AppViewModel.onStart)
        Repo.get(ctx).prefs.updateWaitsForPermission = !ctx.packageManager.canRequestPackageInstalls()
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("TopLevel.apk", 0, file.length()).use { o ->
                file.inputStream().use { it.copyTo(o) }
                s.fsync(o)
            }
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            s.commit(pi.intentSender)
        }
    }

    fun schedule(ctx: Context, on: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!on) { wm.cancelUniqueWork("update"); return }
        // UPDATE (not KEEP) so phones that scheduled the old 6-hour Wi-Fi-only check switch to this one
        wm.enqueueUniquePeriodicWork("update", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build())
    }

    internal fun notify(ctx: Context, title: String, text: String, tap: PendingIntent?) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(ctx, Notifier.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .apply { if (tap != null) setContentIntent(tap) }
            .setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIFY_ID, n) } catch (_: SecurityException) { }
    }
}

/** Hourly: install a newer build if there is one. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!Repo.get(applicationContext).prefs.autoUpdate) return Result.success()
        return try { Updater.checkAndInstall(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
    }
}

/** Result of an install session. Asks the user to confirm when Android needs it. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Works while the app is open; from the background Android shows our notification instead
                try { ctx.startActivity(confirm) } catch (_: Exception) { }
                Updater.notify(ctx, "TopLevel update ready", "Tap to install the new version.",
                    PendingIntent.getActivity(ctx, 71, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app restarts as the new version
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // user tapped cancel; we try again next check
            else -> Updater.notify(ctx, "TopLevel update failed",
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Unknown error", null)
        }
    }
}
