package com.toplevel.app

import android.app.Application
import com.toplevel.app.data.Repo
import com.toplevel.app.work.Notifier
import com.toplevel.app.work.Scheduler

class TopLevelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        val repo = Repo.get(this)
        Scheduler.schedule(this, repo.prefs.morningHour, repo.prefs.eveningHour)
        com.toplevel.app.update.Updater.schedule(this, repo.prefs.autoUpdate)
    }
}
