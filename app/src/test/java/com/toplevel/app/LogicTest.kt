package com.toplevel.app

import com.toplevel.app.data.Cali
import com.toplevel.app.data.Stat
import com.toplevel.app.data.WorkoutSet
import com.toplevel.app.data.conditionLabel
import com.toplevel.app.data.defaultGoldCost
import com.toplevel.app.data.e1rm
import com.toplevel.app.game.Leveling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test fun levelCurve() {
        assertEquals(1, Leveling.levelFor(0))
        assertEquals(1, Leveling.levelFor(49))
        assertEquals(2, Leveling.levelFor(50))
        assertEquals(5, Leveling.levelFor(800))
        assertEquals(10, Leveling.levelFor(4050))
        assertEquals(1, Leveling.levelFor(-100)) // penalties can't break it
        val p = Leveling.progress(1000)
        assertTrue(p.fraction in 0f..1f)
        assertEquals(Leveling.xpForLevel(p.level) + p.intoLevel, 1000)
    }

    @Test fun ranks() {
        assertEquals("E", Leveling.rank(1))
        assertEquals("D", Leveling.rank(7))
        assertEquals("S", Leveling.rank(60))
    }

    @Test fun statParsing() {
        assertEquals(Stat.STRENGTH, Stat.parse("STR"))
        assertEquals(Stat.VITALITY, Stat.parse("vitality"))
        assertEquals(Stat.DISCIPLINE, Stat.parse("nonsense"))
        assertEquals(Stat.DISCIPLINE, Stat.parse(null))
    }

    @Test fun caliMatching() {
        val m = Cali.match("Planche · Straddle")
        assertNotNull(m)
        assertEquals("planche", m!!.first.id)
        assertEquals(3, m.second)
        assertEquals(4, Cali.match("front lever · half lay")!!.second) // case-insensitive
        assertNull(Cali.match("Bench press"))
    }

    @Test fun customStages() {
        Cali.overrides["planche"] = listOf("A", "B", "C")
        try {
            assertEquals(1, Cali.match("Planche · B")!!.second)
            assertEquals(3, Cali.byId("planche")!!.stages.size)
        } finally {
            Cali.overrides.remove("planche")
        }
        assertEquals(8, Cali.byId("planche")!!.stages.size)
    }

    @Test fun e1rmAndGold() {
        val s = WorkoutSet(date = 0, ts = 0, exercise = "x", weightKg = 100f, reps = 1)
        assertEquals(100f, s.e1rm(), 0.01f)
        assertEquals(50, defaultGoldCost(5f))
        assertEquals(750, defaultGoldCost(250f))
    }

    @Test fun rewardConditionLabels() {
        assertEquals("Reach level 10", conditionLabel("level:10"))
        assertEquals("Planche: reach Half lay", conditionLabel("cali:planche:4"))
        assertEquals("No condition", conditionLabel(""))
    }

    @Test fun macroCalculator() {
        // 80 kg, 180 cm, 30 y male, active: BMR 1780 → TDEE ~2848
        val m = com.toplevel.app.game.Nutrition.targets(true, 30, 180, 80f, 2, "maintain")
        assertEquals(2850, m.kcal)
        assertEquals(160, m.protein)
        val cut = com.toplevel.app.game.Nutrition.targets(true, 30, 180, 80f, 2, "cut")
        val bulk = com.toplevel.app.game.Nutrition.targets(true, 30, 180, 80f, 2, "bulk")
        assertTrue(cut.kcal < m.kcal && m.kcal < bulk.kcal)
        assertTrue(cut.protein > m.protein)
        // macros add up to the calories (within rounding)
        assertTrue(kotlin.math.abs(m.protein * 4 + m.carbs * 4 + m.fat * 9 - m.kcal) < 40)
        val f = com.toplevel.app.game.Nutrition.targets(false, 30, 165, 60f, 1, "maintain")
        assertTrue(f.kcal in 1700..2100)
    }

    @Test fun localAdvice() {
        val evening = com.toplevel.app.data.localMoves(22, listOf("Read 20 pages"), 2000, 2400, 150, 160, 8, 10,
            checkInDone = false, eveningHour = 21)
        assertTrue(evening.first().title.contains("evening report"))
        val lowProtein = com.toplevel.app.data.localMoves(14, emptyList(), 900, 2400, 40, 160, 6, 10, true, 21)
        assertTrue(lowProtein.any { it.kind == "food" && it.title.contains("protein") })
        val allGood = com.toplevel.app.data.localMoves(10, emptyList(), 500, 2400, 40, 50, 10, 10, true, 21)
        assertEquals(1, allGood.size)
        assertTrue(lowProtein.size <= 3)
    }

    @Test fun adviceRoundTrip() {
        val l = listOf(com.toplevel.app.data.Advice("Eat", "Skyr", "short on protein", "food"))
        assertEquals(l, com.toplevel.app.data.AdviceCodec.decode(com.toplevel.app.data.AdviceCodec.encode(l)))
        assertTrue(com.toplevel.app.data.AdviceCodec.decode("garbage").isEmpty())
    }

    @Test fun barcodePortion() {
        val p = com.toplevel.app.data.Product("123", "Skyr", 63.0, 11.0, 4.0, 0.2, 150.0)
        val m = p.macrosFor(150.0)
        assertEquals(95, m.kcal)
        assertEquals(17, m.protein)
        assertEquals("Skyr (150 g)", m.name)
    }

    @Test fun dishSearch() {
        val LF = com.toplevel.app.data.LocalFoods
        assertEquals("Hühnerdöner", LF.search("hühner döner").first().name)
        assertEquals("Hühnerdöner", LF.search("Huhner Doner").first().name)
        assertEquals("Hühnerdöner", LF.search("hühnerdöner").first().name)
        assertTrue(LF.search("schnitzel").first().name.contains("Schnitzel"))
        assertTrue(LF.search("leberkas").any { it.name == "Leberkässemmel" })
        assertTrue(LF.search("xyzzy").isEmpty())
        assertEquals("hunerdoner", LF.norm("Hüner Döner!"))
    }

    @Test fun csvPlan() {
        val t = com.toplevel.app.data.PlanImport.csv("Tag;Übung;Sätze\nMo;Planche \"tuck\";5x8\n\nDi;FL;4x6\n")
        assertEquals("Tag | Übung | Sätze\nMo | Planche tuck | 5x8\nDi | FL | 4x6", t)
        val c = com.toplevel.app.data.PlanImport.csv("Day,Exercise,Notes\nMon,\"Planche, tuck\",\"5\"\"\"")
        assertTrue(c.contains("Planche, tuck"))
    }

    @Test fun stepBaseline() {
        val S = com.toplevel.app.health.StepCounter
        assertEquals(1200, S.stepsToday(51_200, 50_000))
        assertEquals(300, S.stepsToday(300, 50_000)) // sensor reset after reboot
        assertEquals(800, S.stepsToday(300, -500))  // shifted baseline after a reboot today
    }

    @Test fun appointmentsOnly() {
        val C = com.toplevel.app.calendar.CalendarRepo
        assertTrue(C.isNotAppointment("Feiertage in Österreich", "de.austrian#holiday@group.v.calendar.google.com"))
        assertTrue(C.isNotAppointment("Geburtstage", "addressbook#contacts@group.v.calendar.google.com"))
        assertTrue(!C.isNotAppointment("bo@example.com", "bo@example.com"))
    }

    @Test fun statPoints() {
        val G = com.toplevel.app.data.Growth
        assertEquals(0, G.pointsEarned(1))
        assertEquals(27, G.pointsEarned(10))
        assertEquals(100, G.boost(100, 0))
        assertEquals(130, G.boost(100, 10))
        assertEquals(200, G.boost(100, 99)) // capped at ×2
    }

    @Test fun flashcardSchedule() {
        val G = com.toplevel.app.data.Growth
        val c = com.toplevel.app.data.Flashcard(front = "Q", back = "A", due = 100, createdAt = 0)
        val g1 = G.review(c, 2, 100)
        assertEquals(101, g1.due); assertEquals(1, g1.interval)
        val g2 = G.review(g1, 2, 101)
        assertEquals(3, g2.interval)
        val g3 = G.review(g2, 2, 104)
        assertEquals(8, g3.interval) // 3 × 2.5 rounded
        val again = G.review(g3, 0, 112)
        assertEquals(112, again.due); assertEquals(0, again.reps); assertTrue(again.ease < g3.ease)
        assertTrue(G.review(g2, 3, 104).interval > g3.interval)
    }

    @Test fun noSpend() {
        val G = com.toplevel.app.data.Growth
        assertEquals(0, G.noSpendStreak(emptySet(), null, 100))
        assertEquals(3, G.noSpendStreak(setOf(96L), 90, 100))   // 97, 98, 99 clean
        assertEquals(0, G.noSpendStreak(setOf(99L), 90, 100))
        assertEquals(2, G.noSpendStreak(emptySet(), 98, 100))   // never before the first logged day
    }

    @Test fun weekKeys() {
        val G = com.toplevel.app.data.Growth
        val mon = java.time.LocalDate.of(2026, 11, 16).toEpochDay() // a Monday
        assertEquals(mon, G.weekKey(mon))
        assertEquals(mon, G.weekKey(mon + 6))
        assertEquals(mon + 7, G.weekKey(mon + 7))
    }

    @Test fun claudeJsonAnswers() {
        val C = com.toplevel.app.ai.Claude.Companion
        val js = kotlinx.serialization.json.Json
        fun res(s: String) = js.parseToJsonElement(s) as kotlinx.serialization.json.JsonObject
        // forced tool call: object, array, stringified, fields at top level
        assertEquals("1", C.parseAnswer(res("""{"content":[{"type":"tool_use","input":{"data":{"a":1}}}]}"""))
            .toString().filter { it.isDigit() })
        assertTrue(C.parseAnswer(res("""{"content":[{"type":"tool_use","input":{"data":[1,2]}}]}""")) is kotlinx.serialization.json.JsonArray)
        assertNotNull(C.parseAnswer(res("""{"content":[{"type":"tool_use","input":{"data":"{\"a\":1}"}}]}""")))
        assertNotNull(C.parseAnswer(res("""{"content":[{"type":"tool_use","input":{"kcal":300}}]}""")))
        // plain text fallbacks: fences, chatter, trailing commas
        assertNotNull(C.extractJsonText("```json\n{\"a\":1}\n```"))
        assertNotNull(C.extractJsonText("Sure! Here it is: {\"a\":[1,2,],} hope that helps"))
        assertNull(C.extractJsonText("{\"a\":1, \"b\":")) // truncated -> retry
    }
}
