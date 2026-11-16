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
}
