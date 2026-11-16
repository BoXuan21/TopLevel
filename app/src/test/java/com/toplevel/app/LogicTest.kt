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
}
