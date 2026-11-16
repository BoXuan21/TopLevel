package com.toplevel.app.game

import kotlin.math.roundToInt

/** Offline calorie/macro calculator (Mifflin-St Jeor + activity + goal). */
object Nutrition {
    data class Targets(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int)

    val activityLabels = listOf(
        "Desk job, little movement",
        "Light: walking, 1–2 workouts/week",
        "Active: 3–5 workouts/week",
        "Very active: hard training most days",
    )
    private val activityFactor = listOf(1.3, 1.45, 1.6, 1.75)

    fun bmr(male: Boolean, age: Int, heightCm: Int, weightKg: Float): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (male) 5 else -161

    fun targets(male: Boolean, age: Int, heightCm: Int, weightKg: Float, activity: Int, goal: String): Targets {
        val tdee = bmr(male, age, heightCm, weightKg) * activityFactor[activity.coerceIn(0, 3)]
        val kcal = when (goal) {
            "cut" -> tdee - minOf(500.0, tdee * 0.2)
            "bulk" -> tdee + 250
            else -> tdee
        }
        val protein = weightKg * if (goal == "cut") 2.2 else 2.0
        val fat = maxOf(kcal * 0.25 / 9, weightKg * 0.8)
        val carbs = maxOf(0.0, (kcal - protein * 4 - fat * 9) / 4)
        return Targets(round(kcal, 10), round(protein, 5), round(carbs, 5), round(fat, 5))
    }

    private fun round(v: Double, step: Int) = ((v / step).roundToInt() * step)
}
