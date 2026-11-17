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
import com.toplevel.app.data.AdviceCodec
import com.toplevel.app.data.Dungeon
import com.toplevel.app.data.Expense
import com.toplevel.app.data.Flashcard
import com.toplevel.app.data.Growth
import com.toplevel.app.data.JobDef
import com.toplevel.app.data.JobState
import com.toplevel.app.data.StepProgress
import com.toplevel.app.data.WeekNumbers
import com.toplevel.app.data.abandonJob
import com.toplevel.app.data.activeDungeon
import com.toplevel.app.data.addCard
import com.toplevel.app.data.logSession
import com.toplevel.app.data.makeWeekPlan
import com.toplevel.app.data.saveWeekPlan
import com.toplevel.app.data.weekPlan
import com.toplevel.app.data.QuizResult
import com.toplevel.app.data.addLearnTopic
import com.toplevel.app.data.checkAnswer
import com.toplevel.app.data.generateQuiz
import com.toplevel.app.data.learnTopics
import com.toplevel.app.data.removeLearnTopic
import com.toplevel.app.data.addExpense
import com.toplevel.app.data.cachedReview
import com.toplevel.app.data.checkJobComplete
import com.toplevel.app.data.clearDungeon
import com.toplevel.app.data.customJob
import com.toplevel.app.data.enterDungeon
import com.toplevel.app.data.generateCards
import com.toplevel.app.data.jobProgress
import com.toplevel.app.data.jobState
import com.toplevel.app.data.noSpendStreak
import com.toplevel.app.data.quitDungeon
import com.toplevel.app.data.reviewCard
import com.toplevel.app.data.rewardNoSpend
import com.toplevel.app.data.startJob
import com.toplevel.app.data.toggleJobStep
import com.toplevel.app.data.weekNumbers
import com.toplevel.app.data.weeklyChanges
import com.toplevel.app.data.FoodScan
import com.toplevel.app.data.Popup
import com.toplevel.app.data.Product
import com.toplevel.app.data.SystemEvents
import com.toplevel.app.data.estimateFoodPhoto
import com.toplevel.app.data.PlanType
import com.toplevel.app.data.compactPlan
import com.toplevel.app.health.StepCounter
import com.toplevel.app.data.nextMoves
import com.toplevel.app.data.whatToEat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo.get(app)
    val prefs = repo.prefs
    val day: Long get() = repo.today()

    /** Current day as a stream so every "today" screen rolls over at midnight. */
    private val today = MutableStateFlow(repo.today())
    private fun <T> daily(f: (Long) -> Flow<T>): Flow<T> = today.flatMapLatest { f(it) }

    // Streams (screens collect these)
    val quests = daily { repo.game.questsFor(it) }
    val statTotals = repo.game.statTotals()
    val plans = repo.life.plans()
    val food = daily { repo.life.foodFor(it) }
    val recentLogs = repo.life.recentLogs(50)
    val skills = repo.life.skills()
    val chat = repo.life.chat()
    val memories = repo.life.memories()
    val discover = daily { repo.life.discoverFor(it) }
    val checkInToday = daily { repo.life.checkIn(it) }
    val setsToday = daily { repo.fit.setsFor(it) }
    val recentExercises = repo.fit.recentExercises()
    val body = repo.fit.body()
    val healthToday = daily { repo.fit.healthFor(it) }
    val boss = daily { repo.fit.boss(repo.weekStart(it)) }
    val achievements = repo.fit.achievements()
    fun exerciseHistory(ex: String) = repo.fit.setsForExercise(ex)
    private val vault = repo.db.vault()
    val gold = vault.goldBalance()
    val pot = vault.pot()
    val savings = vault.savings()
    val savedThisMonth = daily { d -> vault.depositedSince(java.time.LocalDate.ofEpochDay(d).withDayOfMonth(1).toEpochDay()) }
    val rewards = vault.rewards()
    val ladderProgress = vault.progress()
    val session = daily { vault.session(it) }

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
        today.value = repo.today()
        refreshCalendar()
        loadRecentFoods()
        water = prefs.water(day)
        growthOnStart()
        focusEnd = prefs.focusEnd
        // While the app is open, Android can show the install prompt right away: check on every open (max every 15 min)
        val pm = getApplication<android.app.Application>().packageManager
        val permissionJustGiven = prefs.updateWaitsForPermission && pm.canRequestPackageInstalls()
        if (permissionJustGiven) prefs.updateWaitsForPermission = false
        if (prefs.autoUpdate && (permissionJustGiven || System.currentTimeMillis() - prefs.lastUpdateCheck > 15 * 60_000L))
            checkUpdate(quiet = true)
        viewModelScope.launch(Dispatchers.IO) {
            healthConnected = HealthSync.granted(getApplication())
            if (healthConnected) HealthSync.sync(repo)
            StepCounter.sync(repo) // only ever raises today's count, so it's a safe fallback either way
            repo.checkAchievements()
            refreshStreak()
        }
        if (!wizardDone) return
        // first open of the day: penalties, quests, boss, discoveries
        if (prefs.lastQuestGen != day) run("Designing today's quests…") {
            // Late Samsung Health data can still clear yesterday's measurable quests before they're judged
            if (HealthSync.granted(getApplication())) HealthSync.sync(repo)
            repo.applyPenalties()
            val e1 = repo.generateQuests()
            val qs = repo.game.questsForNow(repo.today())
            if (qs.isNotEmpty()) SystemEvents.show(Popup(Popup.Kind.ARRIVED, "DAILY QUESTS ARRIVED",
                "Your orders for today are in.", lines = qs.take(6).map { it.title },
                reward = "Clear them all: +${com.toplevel.app.game.Rewards.ALL_QUESTS_BONUS} DIS bonus"))
            val e2 = if (prefs.extra("boss")) repo.generateBoss() else null
            // Discoveries are made when you tap ↻ on them, not every morning (saves tokens)
            e1 ?: e2
        }
    }

    /** Pull-down on a screen: sync health + calendar, re-check the day, streak and advice. */
    var refreshing by mutableStateOf(false)
        private set
    /** When the last pull-to-refresh finished. */
    var lastRefreshAt by mutableLongStateOf(0L)
        private set
    fun pullRefresh() {
        if (refreshing) return
        refreshing = true
        today.value = repo.today()
        refreshCalendar()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    healthConnected = HealthSync.granted(getApplication())
                    if (healthConnected) HealthSync.sync(repo)
                    StepCounter.sync(repo)
                    repo.checkAchievements()
                    noSpend = repo.noSpendStreak()
                    com.toplevel.app.widget.Widgets.refresh(getApplication())
                }
                refreshStreak()
                water = prefs.water(day)
            } catch (e: Exception) {
                message = e.message
            } finally {
                refreshing = false
                lastRefreshAt = System.currentTimeMillis()
            }
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
    /** Regenerate with the user's wishes ("little time today", "more learning", …); they're kept for next days too. */
    fun regenerateQuests(wishes: String) {
        prefs.questWishes = wishes
        regenerateQuests()
    }

    // Discover
    fun regenerateDiscover() = run("Finding new songs & videos…") { repo.generateDiscover(force = true) }

    // Food
    fun addFood(name: String, kcal: Int, p: Int, c: Int, f: Int) = io { repo.addFood(name, kcal, p, c, f) }
    fun deleteFood(f: FoodEntry) = io { repo.deleteFood(f) }
    fun estimateAndAddFood(text: String) = run("Estimating macros…") {
        logMacros(repo.estimateFood(text)); null
    }

    private suspend fun logMacros(m: Repo.Macros) {
        repo.addFood(m.name, m.kcal, m.protein, m.carbs, m.fat)
        SystemEvents.show(Popup(Popup.Kind.NOTICE, "MEAL LOGGED", m.name,
            lines = listOfNotNull(m.tip.takeIf { it.isNotBlank() }),
            reward = "${m.kcal} kcal · P${m.protein} C${m.carbs} F${m.fat}", auto = true))
        loadRecentFoods()
    }

    /** Photo of a meal or nutrition label → Claude vision estimate. */
    fun logPhoto(uri: Uri, note: String) = run("Scanning your meal…") {
        val bmp = FoodScan.loadBitmap(getApplication(), uri)
        logMacros(repo.estimateFoodPhoto(FoodScan.jpegBase64(bmp), note)); null
    }

    /** Barcode photo → on-device decode → Open Food Facts. Opens the portion dialog. */
    var scanned by mutableStateOf<Product?>(null)
    fun scanBarcode(uri: Uri) = run("Reading barcode…") {
        val bmp = FoodScan.loadBitmap(getApplication(), uri, maxSide = 2000)
        val code = FoodScan.readBarcode(bmp) ?: return@run "No barcode found. Hold the camera closer and keep it sharp."
        val p = try {
            FoodScan.lookup(getApplication(), code)
        } catch (e: FoodScan.Unreachable) {
            return@run "Open Food Facts isn't answering right now (barcode $code). Try again in a minute, or SNAP MEAL on the nutrition label."
        } ?: return@run "Product $code isn't in Open Food Facts. Try a photo of the label instead."
        scanned = p
        null
    }
    fun logProduct(p: Product, grams: Double) {
        scanned = null
        io { logMacros(p.macrosFor(grams)) }
    }

    /** Recently eaten foods for one-tap re-logging. */
    var recentFoods by mutableStateOf<List<FoodEntry>>(emptyList())
        private set
    fun loadRecentFoods() {
        viewModelScope.launch(Dispatchers.IO) {
            recentFoods = repo.life.foodSince(day - 30).reversed()
                .distinctBy { it.name.lowercase().trim() }.take(10)
        }
    }
    fun relog(f: FoodEntry) = io {
        repo.addFood(f.name, f.kcal, f.protein, f.carbs, f.fat)
        SystemEvents.show(Popup(Popup.Kind.NOTICE, "MEAL LOGGED", f.name,
            reward = "${f.kcal} kcal · P${f.protein} C${f.carbs} F${f.fat}", auto = true))
    }

    // Water
    var water by mutableIntStateOf(prefs.water(repo.today()))
        private set
    fun addWater(delta: Int) {
        val n = (prefs.water(day) + delta).coerceAtLeast(0)
        prefs.setWater(day, n); water = n
        if (delta > 0 && n == prefs.waterGoal) SystemEvents.show(Popup(Popup.Kind.CLEAR, "HYDRATION COMPLETE",
            "${n} glasses today. Your body thanks you.", auto = true))
    }

    // Type-to-find food: your recent foods, built-in dishes, then Open Food Facts
    data class FoodHits(
        val query: String = "",
        val recent: List<FoodEntry> = emptyList(),
        val dishes: List<com.toplevel.app.data.Dish> = emptyList(),
        val products: List<Product> = emptyList(),
        val searching: Boolean = false,
    )
    var foodHits by mutableStateOf(FoodHits())
        private set
    private var searchJob: kotlinx.coroutines.Job? = null
    fun searchFood(q: String) {
        searchJob?.cancel()
        val query = q.trim()
        if (query.length < 2) { foodHits = FoodHits(); return }
        val nq = com.toplevel.app.data.LocalFoods.norm(query)
        val recent = recentFoods.filter { com.toplevel.app.data.LocalFoods.norm(it.name).contains(nq) }.take(3)
        val dishes = com.toplevel.app.data.LocalFoods.search(query)
        foodHits = FoodHits(query, recent, dishes, emptyList(), searching = query.length >= 3)
        if (query.length < 3) return
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(650) // wait until typing pauses
            val products = try { FoodScan.search(query) } catch (_: Exception) { emptyList() }
            if (foodHits.query == query) foodHits = foodHits.copy(products = products, searching = false)
        }
    }
    fun clearFoodSearch() { searchJob?.cancel(); foodHits = FoodHits() }
    fun logDish(d: com.toplevel.app.data.Dish) { clearFoodSearch(); io { logMacros(d.macros()) } }
    fun pickProduct(p: Product) { clearFoodSearch(); scanned = p; FoodScan.remember(getApplication(), p) }

    // Plans from files (PDF / Excel / CSV)
    fun importPlan(uri: Uri, type: String = PlanType.TRAINING) = run("Reading your plan…") {
        val file = com.toplevel.app.data.PlanImport.load(getApplication(), uri)
        val pdf = file.pdfBase64 != null
        if (pdf && !prefs.hasKey) return@run "Reading a PDF needs your Anthropic API key (Settings). Excel/CSV work without it."
        val sum = if (prefs.hasKey) try { repo.compactPlan(type, file) } catch (e: Exception) {
            if (pdf) return@run "Couldn't read that PDF: ${e.message}"
            com.toplevel.app.data.PlanSummary(file.text, emptyList(), null)
        } else com.toplevel.app.data.PlanSummary(file.text, emptyList(), null)
        repo.life.upsertPlan(Plan(type, sum.plan, System.currentTimeMillis()))
        // Only track the skills the plan really trains (no more handstand questions if you don't do handstands)
        val skillLine = sum.skills?.let { ids ->
            withContext(Dispatchers.Main) { setLadders(ids) }
            if (ids.isEmpty()) "No skill ladders in this plan" else
                "Tracking: " + ids.mapNotNull { com.toplevel.app.data.Cali.byId(it)?.name }.joinToString(", ")
        }
        SystemEvents.show(Popup(Popup.Kind.CLEAR, "PLAN LOADED",
            "${PlanType.label(type)} imported" + (if (pdf) " from PDF" else " (${file.text.lines().size} rows)") +
                ". Your quests now follow it.",
            lines = sum.lines + listOfNotNull(skillLine)))
        if (prefs.hasKey && wizardDone) {
            repo.generateQuests(force = true)
        }
        null
    }

    // Steps from the phone sensor (when Health Connect isn't linked)
    fun syncSteps() = viewModelScope.launch(Dispatchers.IO) { StepCounter.sync(repo) }

    // What to eat next (AI)
    var foodAdvice by mutableStateOf(if (prefs.foodAdviceTs > 0 && sameDay(prefs.foodAdviceTs)) prefs.foodAdvice else "")
        private set
    var foodAdviceBusy by mutableStateOf(false)
        private set
    fun askWhatToEat(atHome: String = "") {
        if (foodAdviceBusy) return
        foodAdviceBusy = true
        viewModelScope.launch {
            try {
                val a = withContext(Dispatchers.IO) { repo.whatToEat(atHome) }
                prefs.foodAdvice = a; prefs.foodAdviceTs = System.currentTimeMillis(); foodAdvice = a
            } catch (e: Exception) { message = e.message }
            foodAdviceBusy = false
        }
    }

    // Best next move (AI, with an offline fallback)
    var advice by mutableStateOf(AdviceCodec.decode(prefs.adviceJson))
        private set
    var adviceTs by mutableLongStateOf(prefs.adviceTs)
        private set
    var adviceBusy by mutableStateOf(false)
        private set
    /** Auto-refresh at most once a day (saves tokens); the ↻ button refreshes on demand. */
    val adviceStale get() = advice.isEmpty() || !sameDay(adviceTs)
    /** [manual] = the user tapped ↻; automatic refreshes fail quietly (no snackbar over the screen). */
    fun refreshAdvice(manual: Boolean = false) {
        if (adviceBusy || !prefs.hasKey) return
        adviceBusy = true
        viewModelScope.launch {
            try {
                val a = withContext(Dispatchers.IO) { repo.nextMoves() }
                if (a.isNotEmpty()) {
                    advice = a; adviceTs = System.currentTimeMillis()
                    prefs.adviceJson = AdviceCodec.encode(a); prefs.adviceTs = adviceTs
                }
            } catch (e: Exception) { if (manual) message = e.message }
            adviceBusy = false
        }
    }
    private fun sameDay(ts: Long) =
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() == day

    // Setup wizard
    var wizardDone by mutableStateOf(prefs.wizardDone)
        private set
    fun finishWizard() {
        prefs.wizardDone = true; prefs.onboarded = true; wizardDone = true
        SystemEvents.show(Popup(Popup.Kind.ARRIVED, "PLAYER REGISTERED",
            "Welcome, ${prefs.name.ifBlank { "Player" }}. From today, every rep, meal and page you read counts.",
            reward = "Level 1 · Rank E"))
        onStart()
    }
    fun restartWizard() { prefs.wizardDone = false; wizardDone = false }

    // ───────── Growth features ─────────

    /** Which section of the Journal tab is open. */
    var journalTab by mutableStateOf("quiz")

    /** Opened from the food widget: start the barcode camera. */
    var pendingScan by mutableStateOf(false)

    // Stat points (bumped to redraw after spending)
    var pointsVersion by mutableIntStateOf(0)
        private set
    fun unspentPoints(level: Int) = Growth.pointsEarned(level) - prefs.pointsSpent()
    fun spendPoint(stat: Stat, level: Int) {
        if (unspentPoints(level) <= 0) return
        prefs.setPoints(stat, prefs.points(stat) + 1); pointsVersion++
    }

    // Themes
    fun setTheme(id: String) { prefs.theme = id; C.applyTheme(id) }

    // Job change
    var job by mutableStateOf<JobState?>(null)
        private set
    var jobProgress by mutableStateOf<List<StepProgress>>(emptyList())
        private set
    fun refreshJob() = viewModelScope.launch(Dispatchers.IO) {
        if (repo.checkJobComplete()) C.applyTheme(prefs.theme)
        job = repo.jobState(); jobProgress = repo.jobProgress()
        activeTitle = prefs.activeTitle
    }
    fun startJob(d: JobDef) { repo.startJob(d); refreshJob() }
    fun abandonJob() { repo.abandonJob(); refreshJob() }
    fun toggleJobStep(i: Int) = io { repo.toggleJobStep(i); refreshJob() }
    fun customJob(goal: String) = run("Designing your class…") { repo.startJob(repo.customJob(goal)); refreshJob(); null }

    // Dungeons
    var dungeon by mutableStateOf(repo.activeDungeon())
        private set
    fun enterDungeon(d: Dungeon) {
        val a = repo.enterDungeon(d)
        com.toplevel.app.work.DungeonTimer.start(getApplication(), a.start, d.minutes)
        dungeon = a
    }
    fun clearDungeon() = io { if (repo.clearDungeon()) dungeon = null; refreshJob() }
    fun quitDungeon() = io {
        repo.quitDungeon(); dungeon = null
        com.toplevel.app.work.DungeonTimer.cancel(getApplication())
    }

    // Flashcards
    val dueCards = repo.db.grow().dueCards(day)
    val cardCount = repo.db.grow().cardCount()
    fun makeCards(force: Boolean = true) = run("Making flashcards from what you learned…") {
        val n = repo.generateCards(force)
        message = if (n > 0) "$n new flashcards" else "No new learnings to turn into cards yet"
        null
    }
    fun reviewCard(c: Flashcard, grade: Int) = io { repo.reviewCard(c, grade) }
    fun addCard(front: String, back: String) = io { repo.addCard(front, back) }

    // Learn: topics (= skills with status "learning") + AI quiz
    fun addLearnTopic(title: String, level: String) = run("Writing your first quiz questions…") {
        repo.addLearnTopic(title, level)
        if (prefs.hasKey) {
            val t = repo.learnTopics().first { it.title.equals(title.trim(), true) }
            val n = repo.generateQuiz(t)
            message = "$n questions on ${t.title} ready"
        }
        null
    }
    fun removeLearnTopic(s: SkillItem) = io { repo.removeLearnTopic(s) }
    fun moreQuestions(s: SkillItem) = run("Writing new questions on ${s.title}…") {
        val n = repo.generateQuiz(s)
        message = if (n > 0) "$n new questions on ${s.title}" else "No new questions this time. Try again."
        null
    }
    /** Result of the last AI-checked answer, shown under the question until the next one. */
    var quizResult by mutableStateOf<Pair<Long, QuizResult>?>(null)
    var checking by mutableStateOf(false)
        private set
    fun checkAnswer(c: Flashcard, answer: String) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                quizResult = c.id to withContext(Dispatchers.IO) { repo.checkAnswer(c, answer) }
            } catch (e: Exception) { message = e.message }
            checking = false
            refreshStreak()
        }
    }

    // Spending
    val expensesMonth = repo.db.grow().expensesSince(repo.monthStart())
    var noSpend by mutableIntStateOf(0)
        private set
    var budgetsVersion by mutableIntStateOf(0)
        private set
    fun addExpense(amount: Float, cat: String, note: String) = io { repo.addExpense(amount, cat, note); noSpend = repo.noSpendStreak() }
    fun deleteExpense(e: Expense) = io { repo.db.grow().deleteExpense(e); noSpend = repo.noSpendStreak() }
    fun setBudget(cat: String, limit: Int) { prefs.setBudget(cat, limit); budgetsVersion++ }
    fun setMonthBudget(v: Int) { prefs.monthBudget = v; budgetsVersion++ }

    // Weekly review
    var weekNow by mutableStateOf<WeekNumbers?>(null)
        private set
    var weekLast by mutableStateOf<WeekNumbers?>(null)
        private set
    var reviewChanges by mutableStateOf(repo.cachedReview())
        private set
    fun loadReview() = viewModelScope.launch(Dispatchers.IO) {
        weekNow = repo.weekNumbers(day); weekLast = repo.weekNumbers(day - 7)
        reviewChanges = repo.cachedReview()
    }
    fun askReview() = run("Reviewing your week…") {
        val n = weekNow ?: repo.weekNumbers(day); val l = weekLast ?: repo.weekNumbers(day - 7)
        reviewChanges = repo.weeklyChanges(n, l); null
    }

    /** Daily housekeeping for the growth features (cheap; AI only when there's something new). */
    private fun growthOnStart() = viewModelScope.launch(Dispatchers.IO) {
        try {
            repo.rewardNoSpend()
            noSpend = repo.noSpendStreak()
            if (prefs.hasKey && prefs.cardGenDay != day) repo.generateCards()
        } catch (_: Exception) { }
        dungeon = repo.activeDungeon()
        refreshJob()
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
    val sets14 = daily { repo.fit.setsSinceFlow(it - 13) }
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
    fun syncHealth() = run("Syncing health data…") {
        HealthSync.sync(repo) ?: null.also { message = HealthSync.lastSummary }
    }

    // Logs
    fun addLog(cat: String, text: String) = io { repo.addLog(cat, text) }

    // Sports (any sport; the Train tab shows only what fits them)
    var sports by mutableStateOf(prefs.sportList)
        private set
    fun setSports(list: List<String>, notes: String) {
        prefs.sports = list.joinToString(",")
        prefs.sportNotes = notes
        sports = prefs.sportList
        // Calisthenics skill tracking only for people who do calisthenics
        if (!com.toplevel.app.data.Sports.usesLadders(sports)) setLadders(emptyList())
        else if (trackedLadders.isBlank()) setLadders(listOf("planche", "frontlever"))
    }
    // Self-update from GitHub releases
    var autoUpdate by mutableStateOf(prefs.autoUpdate)
        private set
    var updateStatus by mutableStateOf<String?>(null)
        private set
    var updateChecking by mutableStateOf(false)
        private set
    val versionName: String = try {
        getApplication<android.app.Application>().let { it.packageManager.getPackageInfo(it.packageName, 0).versionName } ?: "?"
    } catch (_: Exception) { "?" }
    fun updateAutoUpdate(on: Boolean) {
        prefs.autoUpdate = on; autoUpdate = on
        com.toplevel.app.update.Updater.schedule(getApplication(), on)
    }
    fun checkUpdate(quiet: Boolean = false) {
        if (updateChecking) return
        updateChecking = true
        viewModelScope.launch {
            val s = try { withContext(Dispatchers.IO) { com.toplevel.app.update.Updater.checkAndInstall(getApplication(), manual = !quiet) } }
                catch (e: Exception) { "Update failed: ${e.message}" }
            prefs.lastUpdateCheck = System.currentTimeMillis()
            updateChecking = false
            // A quiet check (on app start) only speaks up when something happens
            if (!quiet || s.startsWith("Installing") || s.startsWith("Update failed")) updateStatus = s
        }
    }

    // Extras (optional game systems), bumped to redraw after a toggle
    var extrasVersion by mutableIntStateOf(0)
        private set
    fun extra(id: String): Boolean { @Suppress("UNUSED_VARIABLE") val v = extrasVersion; return prefs.extra(id) }
    fun setExtra(id: String, on: Boolean) { prefs.setExtra(id, on); extrasVersion++ }

    // Weekly plan
    var weekPlan by mutableStateOf(repo.weekPlan())
        private set
    fun makeWeekPlan(pasted: String = "") = run(if (pasted.isBlank()) "Building your week…" else "Reading your plan…") {
        weekPlan = repo.makeWeekPlan(pasted)
        message = "✓ Weekly plan saved. Your quests follow it from tomorrow (↻ for today)."
        null
    }
    fun setPlanDay(i: Int, text: String) {
        val old = weekPlan?.day(i).orEmpty()
        // Editing the text keeps each session's details and warm-up when the session itself is still there
        val parsed = com.toplevel.app.data.WeekPlan.parseLines(text, sports).map { s ->
            old.firstOrNull { it.title.equals(s.title, true) && it.sport.equals(s.sport, true) }
                ?.let { o -> s.copy(detail = o.detail, warmup = o.warmup) } ?: s
        }
        val p = (weekPlan ?: com.toplevel.app.data.WeekPlan()).withDay(i, parsed)
        repo.saveWeekPlan(p); weekPlan = p
    }
    fun clearWeekPlan() { repo.saveWeekPlan(null); weekPlan = null }

    fun logSession(sport: String, minutes: Int, km: Float?, note: String) = io {
        repo.logSession(sport, minutes, km, note)
        message = "✓ $sport · $minutes min logged"
    }
    fun deleteLog(l: LogEntry) = io { repo.deleteLog(l) }

    // Skills
    fun addSkill(title: String, area: String) = io { repo.addSkill(title, area) }
    fun setSkillStatus(s: SkillItem, status: String) = io { repo.setSkillStatus(s, status); repo.checkAchievements() }
    fun saveSkillNotes(s: SkillItem, notes: String) = io { repo.updateSkillNotes(s, notes) }
    fun deleteSkill(s: SkillItem) = io { repo.deleteSkill(s) }
    fun suggestSkills() = run("Building your learning path…") { repo.suggestSkills() }

    // Check-in
    fun saveCheckIn(c: CheckIn) {
        // Never drop a report: if another AI task is running, save in the background anyway.
        if (busy == null) run("Reflecting on your day…") {
            val e = repo.saveCheckIn(c)
            repo.checkAchievements()
            e
        } else io { repo.saveCheckIn(c); repo.checkAchievements() }
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
    fun send(text: String) = run("Thinking…") {
        val err = repo.chat(text)
        // The coach may have changed the week plan or training profile: redraw from storage
        weekPlan = repo.weekPlan()
        sports = prefs.sportList
        trackedLadders = prefs.trackedLadders
        err
    }
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
        val r = repo.claude.ask("Reply with exactly: OK", "ping", 300).take(40)
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
