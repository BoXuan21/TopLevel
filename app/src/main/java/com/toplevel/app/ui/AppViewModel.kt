package com.toplevel.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toplevel.app.calendar.CalEvent
import com.toplevel.app.calendar.CalendarRepo
import com.toplevel.app.data.CheckIn
import com.toplevel.app.data.FoodEntry
import com.toplevel.app.data.LogEntry
import com.toplevel.app.data.MacroSuggestion
import com.toplevel.app.data.Memory
import com.toplevel.app.data.Plan
import com.toplevel.app.data.Quest
import com.toplevel.app.data.Repo
import com.toplevel.app.data.SkillItem
import com.toplevel.app.data.Stat
import com.toplevel.app.data.WorkoutSet
import com.toplevel.app.data.addSet
import com.toplevel.app.data.Reward
import com.toplevel.app.data.SavingsEntry
import com.toplevel.app.data.SessionItem
import com.toplevel.app.data.addCaliSet
import com.toplevel.app.data.addReward
import com.toplevel.app.data.deleteSavings
import com.toplevel.app.data.deposit
import com.toplevel.app.data.generateSession
import com.toplevel.app.data.monthStart
import com.toplevel.app.data.redeem
import com.toplevel.app.data.setStage
import com.toplevel.app.data.setLadderStages
import com.toplevel.app.data.skippedPurchase
import com.toplevel.app.data.toggleSessionItem
import com.toplevel.app.data.applyPenalties
import com.toplevel.app.data.checkAchievements
import com.toplevel.app.data.defeatBoss
import com.toplevel.app.data.deleteSet
import com.toplevel.app.data.exportBackup
import com.toplevel.app.data.generateBoss
import com.toplevel.app.data.importBackup
import com.toplevel.app.data.logBody
import com.toplevel.app.data.progressionTip
import com.toplevel.app.data.streak
import com.toplevel.app.data.streakMultiplier
import com.toplevel.app.data.suggestMacros
import com.toplevel.app.data.weekStart
import com.toplevel.app.health.HealthSync
import com.toplevel.app.work.Focus
import com.toplevel.app.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo.get(app)
    val prefs = repo.prefs
    val day: Long get() = repo.today()

    // Streams (screens collect these)
    val quests = repo.game.questsFor(day)
    val statTotals = repo.game.statTotals()
    val plans = repo.life.plans()
    val food = repo.life.foodFor(day)
    val recentLogs = repo.life.recentLogs(50)
    val skills = repo.life.skills()
    val chat = repo.life.chat()
    val memories = repo.life.memories()
    val discover = repo.life.discoverFor(day)
    val checkInToday = repo.life.checkIn(day)
    val setsToday = repo.fit.setsFor(day)
    val recentExercises = repo.fit.recentExercises()
    val body = repo.fit.body()
    val healthToday = repo.fit.healthFor(day)
    val boss = repo.fit.boss(repo.weekStart())
    val achievements = repo.fit.achievements()
    fun exerciseHistory(ex: String) = repo.fit.setsForExercise(ex)
    private val vault = repo.db.vault()
    val gold = vault.goldBalance()
    val pot = vault.pot()
    val savings = vault.savings()
    val savedThisMonth = vault.depositedSince(repo.monthStart())
    val rewards = vault.rewards()
    val ladderProgress = vault.progress()
    val session = vault.session(day)

    var busy by mutableStateOf<String?>(null)   // label of running AI task
        private set
    var message by mutableStateOf<String?>(null) // snackbar text
    var events by mutableStateOf<List<CalEvent>>(emptyList())
        private set
    var streak by mutableIntStateOf(0)
        private set
    var multiplier by mutableStateOf(1f)
        private set
    var healthConnected by mutableStateOf(false)
        private set
    var focusEnd by mutableLongStateOf(prefs.focusEnd)
        private set
    var tip by mutableStateOf<Pair<String, String>?>(null)  // exercise to advice
    var macroSuggestion by mutableStateOf<MacroSuggestion?>(null)
    var activeTitle by mutableStateOf(prefs.activeTitle)
        private set

    private fun run(label: String, block: suspend () -> String?) {
        if (busy != null) return
        busy = label
        viewModelScope.launch {
            val err = try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { e.message }
            busy = null
            if (err != null) message = err
            refreshStreak()
        }
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: Exception) { message = e.message }
            refreshStreak()
        }
    }

    private suspend fun refreshStreak() {
        val s = withContext(Dispatchers.IO) { repo.streak() to repo.streakMultiplier() }
        streak = s.first; multiplier = s.second
    }

    fun onStart() {
        refreshCalendar()
        focusEnd = prefs.focusEnd
        viewModelScope.launch(Dispatchers.IO) {
            healthConnected = HealthSync.granted(getApplication())
            if (healthConnected) HealthSync.sync(repo)
            repo.checkAchievements()
            refreshStreak()
        }
        // first open of the day: penalties, quests, boss, discoveries
        if (prefs.lastQuestGen != day) run("Designing today's quests…") {
            repo.applyPenalties()
            val e1 = repo.generateQuests()
            val e2 = repo.generateBoss()
            val e3 = repo.generateDiscover()
            e1 ?: e2 ?: e3
        } else if (prefs.hasKey && prefs.lastDiscoverGen != day) run("Finding today's discoveries…") {
            repo.generateDiscover()
        }
    }

    fun refreshCalendar() {
        if (!prefs.useCalendar) { events = emptyList(); return }
        viewModelScope.launch {
            events = withContext(Dispatchers.IO) { CalendarRepo.eventsFor(getApplication(), LocalDate.now(), 1) }
        }
    }

    // Quests
    fun toggle(q: Quest) = io { repo.toggleQuest(q.id) }
    fun deleteQuest(q: Quest) = io { repo.deleteQuest(q) }
    fun addQuest(title: String, stat: Stat, xp: Int) = io { repo.addQuest(title, stat, xp) }
    fun regenerateQuests() = run("Redesigning today's quests…") { repo.generateQuests(force = true) }

    // Discover
    fun regenerateDiscover() = run("Finding new songs & videos…") { repo.generateDiscover(force = true) }

    // Food
    fun addFood(name: String, kcal: Int, p: Int, c: Int, f: Int) = io { repo.addFood(name, kcal, p, c, f) }
    fun deleteFood(f: FoodEntry) = io { repo.deleteFood(f) }
    fun estimateAndAddFood(text: String) = run("Estimating macros…") {
        val m = repo.estimateFood(text)
        repo.addFood(m.name, m.kcal, m.protein, m.carbs, m.fat)
        null
    }
    fun suggestMacros() = run("Analysing your progress…") { macroSuggestion = repo.suggestMacros(); null }
    fun applyMacros(m: MacroSuggestion) {
        prefs.kcalGoal = m.kcal; prefs.proteinGoal = m.protein; prefs.carbsGoal = m.carbs; prefs.fatGoal = m.fat
        macroSuggestion = null
        message = "New targets: ${m.kcal} kcal · P${m.protein} C${m.carbs} F${m.fat}"
    }

    // Training
    fun addSet(ex: String, w: Float, reps: Int) = io { repo.addSet(ex, w, reps) }
    fun deleteSet(s: WorkoutSet) = io { repo.deleteSet(s) }
    fun askTip(ex: String) = run("Planning your next $ex session…") { tip = ex to repo.progressionTip(ex); null }

    // Calisthenics
    var trackedLadders by mutableStateOf(prefs.trackedLadders)
        private set
    fun setLadders(ids: List<String>) { prefs.trackedLadders = ids.joinToString(","); trackedLadders = prefs.trackedLadders }
    fun addCaliSet(ex: String, hold: Int, reps: Int, w: Float = 0f, note: String = "") =
        io { repo.addCaliSet(ex, hold, reps, w, note) }
    fun setStage(ladder: String, stage: Int, reward: Boolean = true) = io { repo.setStage(ladder, stage, reward) }
    fun setLadderStages(id: String, stages: List<String>) {
        repo.setLadderStages(id, stages)
        ladderVersion++
    }
    /** Bumped when stage names change so the Train screen redraws. */
    var ladderVersion by mutableIntStateOf(0)
        private set
    var coachSessions by mutableStateOf(prefs.coachSessions)
        private set
    fun updateCoachSessions(on: Boolean) { prefs.coachSessions = on; coachSessions = on }
    val sets14 = repo.fit.setsSinceFlow(repo.today() - 13)
    val allSets = repo.fit.allSets()
    fun buildSession() = run("Your coach is building today's session…") { repo.generateSession() }
    fun toggleSessionItem(i: SessionItem) = io { repo.toggleSessionItem(i) }

    // Vault
    var claimed by mutableStateOf<Reward?>(null)
    fun deposit(eur: Float, note: String) = io { repo.deposit(eur, note) }
    fun deleteSavings(s: SavingsEntry) = io { repo.deleteSavings(s) }
    fun skipped(what: String, eur: Float) = io { repo.skippedPurchase(what, eur) }
    fun addReward(name: String, eur: Float, goldCost: Int?, condition: String) = io { repo.addReward(name, eur, goldCost, condition) }
    fun deleteReward(r: Reward) = io { vault.deleteReward(r) }
    fun redeem(r: Reward) = io {
        val err = repo.redeem(r)
        if (err != null) {
            message = err
        } else {
            claimed = r
        }
    }
    fun setSavingsGoal(eur: Int) { prefs.savingsGoal = eur }

    // Body
    fun logBody(w: Float, waist: Float?) = io { repo.logBody(w, waist) }
    fun deleteBody(b: com.toplevel.app.data.BodyEntry) = io { repo.fit.deleteBody(b) }

    // Health
    fun onHealthPermissionResult() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = HealthSync.granted(getApplication())
            healthConnected = ok
            if (ok) {
                HealthSync.sync(repo)
                message = "Health Connect linked ✓"
            } else {
                message = "Health permissions not granted"
            }
        }
    }
    fun syncHealth() = run("Syncing health data…") { HealthSync.sync(repo) }

    // Logs
    fun addLog(cat: String, text: String) = io { repo.addLog(cat, text) }
    fun deleteLog(l: LogEntry) = io { repo.deleteLog(l) }

    // Skills
    fun addSkill(title: String, area: String) = io { repo.addSkill(title, area) }
    fun setSkillStatus(s: SkillItem, status: String) = io { repo.setSkillStatus(s, status); repo.checkAchievements() }
    fun saveSkillNotes(s: SkillItem, notes: String) = io { repo.updateSkillNotes(s, notes) }
    fun deleteSkill(s: SkillItem) = io { repo.deleteSkill(s) }
    fun suggestSkills() = run("Building your learning path…") { repo.suggestSkills() }

    // Check-in
    fun saveCheckIn(c: CheckIn) = run("Reflecting on your day…") {
        val e = repo.saveCheckIn(c)
        repo.checkAchievements()
        e
    }

    // Boss
    fun newBoss() = run("Summoning this week's boss…") { repo.generateBoss(force = true) }
    fun defeatBoss() = io { repo.defeatBoss() }

    // Focus
    fun startFocus(min: Int) {
        Focus.start(getApplication(), min)
        focusEnd = prefs.focusEnd
    }
    fun cancelFocus() {
        Focus.cancel(getApplication())
        focusEnd = 0
    }
    fun focusFinishedLocally() { focusEnd = prefs.focusEnd }

    // Chat
    fun send(text: String) = run("Thinking…") { repo.chat(text) }
    fun clearChat() = io { repo.life.clearChat() }

    // Plans / memories / settings
    fun savePlan(type: String, content: String) = io {
        repo.life.upsertPlan(Plan(type, content, System.currentTimeMillis()))
    }
    fun deleteMemory(m: Memory) = io { repo.life.deleteMemory(m) }
    fun addMemory(text: String) = io { repo.life.insertMemory(Memory(ts = System.currentTimeMillis(), text = text)) }
    fun setTitle(t: String) { prefs.activeTitle = t; activeTitle = t }

    fun rescheduleNotifications() =
        Scheduler.schedule(getApplication(), prefs.morningHour, prefs.eveningHour, replace = true)

    fun testKey() = run("Testing API key…") {
        val r = repo.claude.ask("Reply with exactly: OK", "ping", 10)
        message = "API key works ✓ ($r)"
        null
    }

    // Backup
    fun backupTo(uri: Uri) = run("Backing up…") { repo.exportBackup(uri); message = "Backup saved ✓"; null }
    fun restoreFrom(uri: Uri) = run("Restoring…") {
        repo.importBackup(uri)
        activeTitle = prefs.activeTitle
        message = "Backup restored ✓"
        null
    }
}
