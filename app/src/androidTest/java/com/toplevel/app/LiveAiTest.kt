package com.toplevel.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.toplevel.app.data.CheckIn
import com.toplevel.app.data.Plan
import com.toplevel.app.data.PlanType
import com.toplevel.app.data.Repo
import com.toplevel.app.data.addLearnTopic
import com.toplevel.app.data.checkAnswer
import com.toplevel.app.data.generateQuiz
import com.toplevel.app.data.learnTopics
import com.toplevel.app.data.makeWeekPlan
import com.toplevel.app.data.nextMoves
import com.toplevel.app.data.progressionTip
import com.toplevel.app.data.whatToEat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Calls the real Claude API through the app's own code. Skipped unless a key is passed:
 *   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.toplevel.app.LiveAiTest \
 *       -Pandroid.testInstrumentationRunnerArguments.apiKey=sk-ant-…
 * The key is only kept in memory for the run and wiped from the app's settings afterwards.
 */
@RunWith(AndroidJUnit4::class)
class LiveAiTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = Repo.get(ctx)
    private val results = mutableListOf<String>()
    private var failures = 0

    private fun step(name: String, block: suspend () -> String) {
        val t0 = System.currentTimeMillis()
        val line = try {
            val r = runBlocking { block() }
            if (r.isBlank()) throw AssertionError("empty answer")
            "✓ $name: $r"
        }
            catch (e: Throwable) { failures++; "✗ $name: ${e.javaClass.simpleName}: ${e.message}" }
        val out = "$line  [${(System.currentTimeMillis() - t0) / 1000}s]"
        results += out
        Log.i("LIVEAI", out)
    }

    @Test fun everyAiFeature() {
        val key = InstrumentationRegistry.getArguments().getString("apiKey")
        assumeTrue("no apiKey argument: live test skipped", !key.isNullOrBlank())
        val p = repo.prefs
        runBlocking {
            repo.db.clearAllTables()
            p.apiKey = key!!
            p.model = "claude-sonnet-5-5"
            p.name = "Bo"; p.wizardDone = true
            p.sports = "Gym / weights,Running"; p.sportNotes = "Gym 3x pro Woche (Push/Pull/Legs), sonntags laufen. Ziel: 10 km unter 50 min."
            p.trainingLevel = 1; p.equipment = "Barbell / gym"
            p.questWishes = ""; p.skippedQuests = emptyList(); p.lastQuestGen = -1
            p.extrasOn = emptySet()
            repo.life.upsertPlan(Plan(PlanType.GOALS, "Fitter werden, CKA-Zertifizierung bestehen, gesünder essen.", System.currentTimeMillis()))
            repo.life.upsertPlan(Plan(PlanType.LEARNING, "Lernt gerade: Kubernetes (CKA), 30 min pro Tag, lernt am liebsten mit Videos.", System.currentTimeMillis()))
        }
        try {
            step("API key test (Settings button)") { repo.claude.ask("Reply with exactly: OK", "ping", 300).take(40) }
            step("Progression tip") { repo.progressionTip("Bench Press").replace("\n", " ").take(160) }
            step("Food estimate") { repo.estimateFood("200g Hühnerbrust mit Reis").let { "${it.name}: ${it.kcal} kcal, ${it.protein} g P" } }
            step("Learn topic + quiz") {
                repo.addLearnTopic("Kubernetes CKA", "Grundlagen sitzen, Prüfung im März")
                val t = repo.learnTopics().first()
                val n = repo.generateQuiz(t, 5)
                assertTrue("no questions", n > 0)
                val c = repo.db.grow().cardsForTopic(t.title).first()
                "$n questions, e.g. \"${c.front}\""
            }
            step("Check an answer") {
                val c = repo.db.grow().cardsForTopic("Kubernetes CKA").first()
                val r = repo.checkAnswer(c, "Weiß ich nicht genau")
                "grade ${r.grade}: ${r.feedback}"
            }
            step("Week plan (AI)") {
                val wp = repo.makeWeekPlan("")
                "${wp.days.count { it.isNotEmpty() }} training days: " + wp.days.mapIndexed { i, d -> "${listOf("Mo","Di","Mi","Do","Fr","Sa","So")[i]} ${d.joinToString { it.title }.ifBlank { "–" }}" }.joinToString(" | ")
            }
            step("Week plan (pasted)") {
                val wp = repo.makeWeekPlan("Montag: Push 60 min\nMittwoch: Pull 60 min\nFreitag: Beine 70 min\nSonntag: Lauf 8 km locker")
                wp.days.mapIndexed { i, d -> "${i}:${d.joinToString { "${it.sport}/${it.title}/${it.minutes}" }}" }.joinToString(" ")
            }
            step("Daily quests") {
                val err = repo.generateQuests(force = true)
                if (err != null) throw AssertionError(err)
                repo.game.questsForNow(repo.today()).joinToString(" · ") { it.title + if (it.autoType.isNotEmpty()) " [auto ${it.autoType} ${it.target}]" else "" }
            }
            step("Quests with a wish") {
                p.questWishes = "Heute wenig Zeit, nur 4 Quests"
                val err = repo.generateQuests(force = true)
                if (err != null) throw AssertionError(err)
                p.questWishes = ""
                "${repo.game.questsForNow(repo.today()).count { it.source == "ai" }} AI quests"
            }
            step("Coach chat with tools") {
                val err = repo.chat("Ich bin heute 5 km in 28 Minuten gelaufen, lief super.")
                if (err != null) throw AssertionError(err)
                val last = repo.life.recentChat(1).lastOrNull()?.content.orEmpty()
                assertTrue("session not logged: $last", repo.life.logsSince(repo.today()).any { it.text.startsWith("Running") || it.text.contains("km") })
                last.replace("\n", " ").take(200)
            }
            step("Evening check-in") {
                val err = repo.saveCheckIn(CheckIn(repo.today(), 4, 3, 7f, "Viel gearbeitet", "Training durchgezogen", "Früher schlafen"))
                if (err != null) throw AssertionError(err)
                repo.life.recentCheckIns(1).first().reflection.take(160)
            }
            step("Next move") { repo.nextMoves().joinToString(" | ") { it.title } }
            step("What to eat") { repo.whatToEat("Eier, Reis, Skyr").replace("\n", " ").take(160) }
            step("Discover feed") {
                val err = repo.generateDiscover(force = true)
                if (err != null) throw AssertionError(err)
                "${repo.life.discoverSince(repo.today()).size} items"
            }
        } finally {
            p.apiKey = "" // never leave the key on the device
            Log.i("LIVEAI", "SUMMARY\n" + results.joinToString("\n"))
        }
        assertTrue("$failures AI feature(s) failed:\n" + results.joinToString("\n"), failures == 0)
    }
}
