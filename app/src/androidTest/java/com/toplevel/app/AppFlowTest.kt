package com.toplevel.app

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.toplevel.app.data.AchievementUnlock
import com.toplevel.app.data.BodyEntry
import com.toplevel.app.data.ChatMessage
import com.toplevel.app.data.DiscoverItem
import com.toplevel.app.data.FoodEntry
import com.toplevel.app.data.GoldEvent
import com.toplevel.app.data.HealthDay
import com.toplevel.app.data.LogEntry
import com.toplevel.app.data.Memory
import com.toplevel.app.data.Plan
import com.toplevel.app.data.PlanType
import com.toplevel.app.data.Quest
import com.toplevel.app.data.Repo
import com.toplevel.app.data.Reward
import com.toplevel.app.data.SavingsEntry
import com.toplevel.app.data.SkillItem
import com.toplevel.app.data.SkillProgress
import com.toplevel.app.data.Stat
import com.toplevel.app.data.WeeklyBoss
import com.toplevel.app.data.WorkoutSet
import com.toplevel.app.data.weekStart
import com.toplevel.app.game.Leveling
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Seeds realistic data, walks through every screen, exercises the core flows
 * and saves full-screen screenshots to the app's files/shots folder (pulled by CI).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = Repo.get(ctx)
    private val day = repo.today()
    private val now = System.currentTimeMillis()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val shotDir = File(ctx.filesDir, "shots").apply { mkdirs() }
    private var shotNo = 0

    @Before fun seed() = runBlocking {
        repo.db.clearAllTables()
        val p = repo.prefs
        p.onboarded = true
        p.name = "BoXuan"
        p.apiKey = "sk-ant-demo-not-real"
        p.lastQuestGen = day
        p.lastDiscoverGen = day
        p.useCalendar = false
        p.kcalGoal = 2600; p.proteinGoal = 160; p.carbsGoal = 300; p.fatGoal = 80
        p.trackedLadders = "planche,frontlever"
        p.activeTitle = "Straddle Sovereign"
        p.savingsGoal = 200
        p.coachSessions = false

        val g = repo.game
        // XP history → ~level 13
        listOf(Stat.STRENGTH to 2600, Stat.VITALITY to 1300, Stat.INTELLECT to 1500,
            Stat.DISCIPLINE to 1900, Stat.SPIRIT to 700).forEach { (s, a) ->
            g.insertXp(com.toplevel.app.data.XpEvent(ts = now - 86_400_000L * 20, stat = s, amount = a, reason = "seed:$s"))
        }
        p.lastSeenLevel = Leveling.levelFor(8000)

        // Past 6 days: mostly cleared → streak
        for (d in 1..6) {
            g.insertQuest(Quest(date = day - d, title = "Past quest A", stat = Stat.STRENGTH, xp = 30, done = true))
            g.insertQuest(Quest(date = day - d, title = "Past quest B", stat = Stat.VITALITY, xp = 20, done = true))
        }
        // Today
        g.insertQuest(Quest(date = day, title = "Planche: 6×5s straddle + 4×3 full PPU", detail = "Before lunch, parallettes",
            stat = Stat.STRENGTH, xp = 50, done = true, source = "ai"))
        g.insertQuest(Quest(date = day, title = "Walk 8,000 steps", stat = Stat.VITALITY, xp = 20, source = "ai", autoType = "steps", target = 8000))
        g.insertQuest(Quest(date = day, title = "Hit 160 g protein", detail = "4 meals, shake after training", stat = Stat.VITALITY, xp = 25, source = "ai"))
        g.insertQuest(Quest(date = day, title = "Read: SRE Workbook ch. 2 (SLOs)", detail = "30 min after work", stat = Stat.INTELLECT, xp = 30, done = true, source = "ai"))
        g.insertQuest(Quest(date = day, title = "90 min deep work: Terraform refactor", detail = "09:00–10:30 is free", stat = Stat.DISCIPLINE, xp = 35, done = true, source = "ai"))
        g.insertQuest(Quest(date = day, title = "No-spend day", detail = "Cook instead of ordering", stat = Stat.DISCIPLINE, xp = 20, source = "ai"))
        g.insertQuest(Quest(date = day, title = "Evening check-in", stat = Stat.SPIRIT, xp = 15, source = "system"))

        val v = repo.db.vault()
        v.insertGold(GoldEvent(ts = now, amount = 1240, reason = "seed"))
        v.insertSavings(SavingsEntry(ts = now - 86_400_000L * 12, date = day - 12, amountEur = 200f, note = "Monthly transfer"))
        v.insertSavings(SavingsEntry(ts = now - 86_400_000L * 5, date = day - 5, amountEur = 20f, note = "Skipped: takeaway"))
        v.insertSavings(SavingsEntry(ts = now - 86_400_000L * 2, date = day - 2, amountEur = 120f, note = ""))
        v.insertReward(Reward(name = "Wooden gymnastic rings", priceEur = 45f, goldCost = 150, createdAt = now - 86_400_000L * 4))
        v.insertReward(Reward(name = "New desk (oak, standing)", priceEur = 420f, goldCost = 1260, condition = "cali:planche:4",
            createdAt = now - 86_400_000L * 4))
        v.upsertProgress(SkillProgress("planche", stage = 3, bestHoldSec = 9, updatedAt = now))
        v.upsertProgress(SkillProgress("frontlever", stage = 4, bestHoldSec = 6, updatedAt = now))

        val f = repo.fit
        // 14 days of hold history
        for (d in 13 downTo 1 step 2) {
            val base = 5 + (13 - d) / 3
            repeat(4) { i -> f.insertSet(WorkoutSet(date = day - d, ts = now - d * 86_400_000L + i, exercise = "Planche · Straddle", weightKg = 0f, reps = 0, holdSec = base + (i % 2))) }
            repeat(5) { i -> f.insertSet(WorkoutSet(date = day - d, ts = now - d * 86_400_000L + 10 + i, exercise = "Front lever · Half lay", weightKg = 0f, reps = 0, holdSec = 4 + (13 - d) / 5)) }
        }
        listOf(8 to "parallettes", 9 to "", 7 to "", 7 to "RPE 9").forEachIndexed { i, (s, n) ->
            f.insertSet(WorkoutSet(date = day, ts = now - 3_600_000 + i, exercise = "Planche · Straddle", weightKg = 0f, reps = 0, holdSec = s, note = n))
        }
        repeat(3) { i -> f.insertSet(WorkoutSet(date = day, ts = now - 3_000_000 + i, exercise = "Full planche push-up", weightKg = 0f, reps = 3)) }
        for (d in 30 downTo 0 step 3) f.upsertBody(BodyEntry(day - d, 74.8f - (30 - d) * 0.03f))
        f.upsertHealth(HealthDay(day, 7842, 432, 64, "Calisthenics", now))
        f.upsertBoss(WeeklyBoss(repo.weekStart(), "The Half-Lay Hydra", "5 front lever sessions this week and a clean 3 s half lay.",
            "Last week: 31/36 quests (86%). 4 sessions, planche volume +18%. Sleep averaged 6h50 — Thursday's on-call shift cost you a session.\nProtein hit on 5/7 days. Saved €140.\nOne thing holding you back: FL volume dropped while planche climbed.", 250))
        listOf("first_quest", "planche_tuck", "fl_adv", "planche_straddle", "fl_straddle", "saver_100", "level_10").forEach {
            f.unlock(AchievementUnlock(it, now))
        }

        val l = repo.life
        l.insertFood(FoodEntry(date = day, ts = now - 30_000_000, name = "Skyr bowl w/ oats & berries", kcal = 520, protein = 38, carbs = 64, fat = 11))
        l.insertFood(FoodEntry(date = day, ts = now - 20_000_000, name = "Chicken rice bowl", kcal = 780, protein = 52, carbs = 96, fat = 18))
        l.insertFood(FoodEntry(date = day, ts = now - 10_000_000, name = "Protein shake + banana", kcal = 310, protein = 28, carbs = 30, fat = 6))
        l.insertFood(FoodEntry(date = day, ts = now - 5_000_000, name = "Handful of almonds", kcal = 230, protein = 8, carbs = 6, fat = 20))
        l.upsertPlan(Plan(PlanType.TRAINING, "Mon planche · Tue FL · Thu planche · Fri FL + pulling · Sat handstand", now))
        l.upsertPlan(Plan(PlanType.MUSIC, "Fred again.., Bicep, Four Tet, Kaytranada", now))
        l.insertMemory(Memory(ts = now, text = "On-call every 4th week — keep sessions short then"))
        l.insertSkill(SkillItem(title = "SLO design & error budgets", area = "SRE", status = "learning", createdAt = now))
        l.insertSkill(SkillItem(title = "eBPF for observability", area = "Linux", status = "todo", createdAt = now))
        l.insertSkill(SkillItem(title = "Terraform modules", area = "IaC", status = "learned", createdAt = now, learnedAt = now))
        l.insertLog(LogEntry(date = day, ts = now - 7_000_000, category = "work", text = "Cut alert noise on the payments cluster by 40%"))
        l.insertLog(LogEntry(date = day, ts = now - 6_000_000, category = "learned", text = "Multi-window burn-rate alerts"))
        l.insertDiscover(listOf(
            DiscoverItem(date = day, kind = "song", title = "Glue", subtitle = "Bicep", why = "Warm, driving build — good for long holds.", query = "Glue Bicep"),
            DiscoverItem(date = day, kind = "song", title = "Baby", subtitle = "Four Tet", why = "Same euphoric chops as Fred again..", query = "Baby Four Tet"),
            DiscoverItem(date = day, kind = "video", title = "SLIs, SLOs, SLAs, oh my!", subtitle = "Google Cloud Tech", why = "Matches today's SRE Workbook chapter.", query = "SLIs SLOs SLAs oh my"),
        ))
        l.insertChat(ChatMessage(ts = now - 90_000, role = "user", content = "Straddle planche 4 holds 8,9,7,7s on parallettes. 3x3 full PPU"))
        l.insertChat(ChatMessage(ts = now - 80_000, role = "assistant", content = "Solid — 9 s straddle is a new PR, 3 s off the standard for half lay.\n\n✓ Logged 4× Planche · Straddle\n✓ Logged 3× Full planche push-up 3 reps"))
        l.insertChat(ChatMessage(ts = now - 70_000, role = "user", content = "Ich will mir einen neuen Tisch gönnen, so 420€"))
        l.insertChat(ChatMessage(ts = now - 60_000, role = "assistant", content = "Gute Belohnung. Ich hab ihn an die Half-Lay-Planche gekoppelt. Mit €20/Woche hast du das Geld in ~4 Wochen.\n\n✓ Reward added: New desk (€420, 1260 Gold)"))

        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle(2500)
    }

    @After fun tearDown() { if (::scenario.isInitialized) scenario.close() }

    // ───────────────────────── helpers ─────────────────────────

    private fun settle(ms: Long = 900) { compose.waitForIdle(); SystemClock.sleep(ms); compose.waitForIdle() }

    private fun shot(name: String) {
        settle(700)
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        shotNo++
        File(shotDir, "%02d_%s.png".format(shotNo, name)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tab(label: String) { compose.onAllNodesWithText(label).onFirst().performClick(); settle() }

    private fun scroll(times: Int = 1) {
        repeat(times) { compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() } }
        settle(600)
    }

    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        settle(500)
    }

    private fun waitFor(what: String, cond: suspend () -> Boolean) {
        try {
            compose.waitUntil(8_000) { runBlocking { cond() } }
        } catch (e: Throwable) {
            throw AssertionError("Timed out waiting for: $what", e)
        }
    }

    private suspend fun totalXp() = repo.game.statTotalsNow().sumOf { it.total }

    // ───────────────────────── the walkthrough ─────────────────────────

    @Test fun fullWalkthrough() {
        // STATUS
        compose.onNodeWithText("BoXuan").assertExists()
        shot("status")
        scroll(); shot("status_quests")
        scroll(2); shot("status_focus_boss")
        scroll(2); shot("status_discovery")

        // Completing a quest awards EXP + Gold
        val xpBefore = runBlocking { totalXp() }
        val goldBefore = runBlocking { repo.db.vault().goldBalanceNow() }
        scrollTo("Hit 160 g protein")
        compose.onNodeWithText("Hit 160 g protein").performClick()
        waitFor("quest EXP") { totalXp() > xpBefore }
        waitFor("quest Gold") { repo.db.vault().goldBalanceNow() > goldBefore }
        shot("status_quest_completed")

        // TRAIN
        tab("Train")
        shot("train")
        // Log a new PR hold on the current planche stage
        compose.onAllNodesWithText("Log hold").onFirst().performClick(); settle()
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).onFirst().performTextInput("11")
        shot("train_log_hold_dialog")
        compose.onNode(hasText("Log") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitFor("planche PR 11s") { (repo.db.vault().progressOf("planche")?.bestHoldSec ?: 0) >= 11 }
        settle(); shot("train_after_pr")
        scroll(2); shot("train_volume")
        scroll(2); shot("train_progress_chart")

        // LOG
        tab("Log"); shot("log_food")
        compose.onNodeWithText("Body").performClick(); settle(); shot("log_body")
        compose.onNodeWithText("Journal").performClick(); settle(); shot("log_journal")
        compose.onNodeWithText("Skills").performClick(); settle(); shot("log_skills")

        // VAULT: deposit +€10 → pot 350
        tab("Vault"); shot("vault")
        compose.onNodeWithText("+€10").performClick()
        waitFor("pot €350") { repo.db.vault().potNow() >= 349.9f }
        settle(); scroll(); shot("vault_rewards")

        // COACH
        tab("Coach"); shot("coach")

        // SETUP
        tab("Status")
        compose.onNode(hasContentDescription("Setup")).performClick(); settle()
        shot("setup")
        scroll(2); shot("setup_plans")

        // CHECK-IN dialog
        tab("Status")
        scrollTo("Start check-in")
        compose.onAllNodesWithText("Start check-in").onFirst().performClick(); settle()
        shot("checkin")
        compose.onNodeWithText("Cancel").performScrollTo().performClick(); settle()

        // LEVEL UP: push XP over the next level
        runBlocking {
            val xp = totalXp()
            val next = Leveling.xpForLevel(Leveling.levelFor(xp) + 1)
            repo.award(Stat.SPIRIT, next - xp + 5, "test:levelup")
        }
        settle(1500)
        shot("level_up")
        assertTrue(runBlocking { Leveling.levelFor(totalXp()) } > Leveling.levelFor(8000))
    }

    @Test fun rewardRedemptionAndSavings() {
        tab("Vault")
        // Rings: enough gold (1240 ≥ 150), pot 340 ≥ 45, cool-down passed → claimable
        scrollTo("Claim reward ✦")
        compose.onAllNodesWithText("Claim reward ✦").onFirst().performClick()
        waitFor("rings redeemed") { repo.db.vault().rewardsNow().any { it.name.startsWith("Wooden") && it.redeemedAt != null } }
        settle(); shot("vault_reward_claimed")
        runBlocking {
            assertEquals(295f, repo.db.vault().potNow(), 0.01f) // 340 − 45
            // −150 for the rings, +25 from the "Well Earned" achievement it unlocks
            assertEquals(1240 - 150 + 25, repo.db.vault().goldBalanceNow())
        }
    }
}
