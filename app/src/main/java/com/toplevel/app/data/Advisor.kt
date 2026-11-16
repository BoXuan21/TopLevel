package com.toplevel.app.data

import com.toplevel.app.ai.Claude
import com.toplevel.app.ai.Claude.Companion.int
import com.toplevel.app.ai.Claude.Companion.str
import com.toplevel.app.ai.ClaudeException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** One "do this next" suggestion. kind: food|train|work|learn|rest|sleep|money|mind|quest */
@Serializable
data class Advice(val title: String, val detail: String = "", val why: String = "", val kind: String = "quest")

object AdviceCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(l: List<Advice>) = json.encodeToString(ListSerializer(Advice.serializer()), l)
    fun decode(s: String): List<Advice> = try {
        if (s.isBlank()) emptyList() else json.decodeFromString(ListSerializer(Advice.serializer()), s)
    } catch (_: Exception) { emptyList() }
}

private const val FOOD_JSON =
    "Return {\"name\": short name, \"kcal\": int, \"protein\": grams int, \"carbs\": grams int, \"fat\": grams int, " +
        "\"tip\": one short sentence judging this meal against the user's goals (what's good / what to add next time)}."

/** Estimate a meal from a photo (JPEG, base64) plus an optional note. */
suspend fun Repo.estimateFoodPhoto(jpegBase64: String, note: String): Repo.Macros {
    val msg = buildJsonObject {
        put("role", "user")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "image")
                put("source", buildJsonObject {
                    put("type", "base64"); put("media_type", "image/jpeg"); put("data", jpegBase64)
                })
            })
            add(buildJsonObject {
                put("type", "text")
                put("text", "Estimate the nutrition of this meal." + if (note.isNotBlank()) " Note from me: $note" else "")
            })
        })
    }
    val goals = "User's daily targets: ${prefs.kcalGoal} kcal, ${prefs.proteinGoal} g protein, goal: ${prefs.bodyGoal}."
    val res = claude.send(
        "Identify the food in the photo and estimate the whole visible portion. If the photo shows a nutrition label " +
            "or packaging, read the exact printed values and scale them to the portion in my note (or one serving). " +
            "$goals $FOOD_JSON\nRespond with JSON only.",
        buildJsonArray { add(msg) }, maxTokens = 400,
    )
    val o = claude.extractJson(Claude.textOf(res))?.jsonObject ?: throw ClaudeException("Couldn't read that photo.")
    return macrosOf(o, "Meal (photo)")
}

internal fun macrosOf(o: JsonObject, fallbackName: String) = Repo.Macros(
    str(o, "name") ?: fallbackName, int(o, "kcal"), int(o, "protein"), int(o, "carbs"), int(o, "fat"), str(o, "tip").orEmpty(),
)

internal val foodJsonSpec get() = FOOD_JSON

/** AI: the best thing to do right now, plus two alternatives. */
suspend fun Repo.nextMoves(): List<Advice> {
    val o = claude.askJson(
        system = "You are the SYSTEM inside a level-up life app: terse, sharp, motivating. From the player's data pick the " +
            "single best thing to do RIGHT NOW. Weigh the time of day, calendar, unfinished quests, food eaten vs targets, " +
            "training plan and recovery, sleep, recent check-ins and money goals. Then give 2 good alternatives.\n" +
            "Return {\"moves\": [{\"title\": imperative, max 8 words, \"detail\": concrete how-to, max 25 words, " +
            "\"why\": grounded in their data, max 18 words, \"kind\": one of food|train|work|learn|rest|sleep|money|mind}]} " +
            "with exactly 3 moves, best first. Match the user's language.",
        user = buildContext(), maxTokens = 700,
    )
    val moves = (o as? JsonObject)?.get("moves")?.jsonArray ?: o.jsonArray
    return moves.mapNotNull { it as? JsonObject }.map {
        Advice(str(it, "title").orEmpty(), str(it, "detail").orEmpty(), str(it, "why").orEmpty(), str(it, "kind") ?: "quest")
    }.filter { it.title.isNotBlank() }
}

/** AI: what to eat next, from targets, what's eaten, the plan and (optionally) what's at home. */
suspend fun Repo.whatToEat(atHome: String = ""): String = claude.ask(
    system = "You are a sports nutritionist inside the user's level-up app. Using their targets, what they've eaten today, " +
        "the time now, today's training and their eating plan, tell them what to eat next." +
        (if (atHome.isNotBlank()) " Build the options ONLY from what they have at home (listed below), plus basics like salt, oil, spices." else "") +
        "\nFormat: one line on how today is going. Then 2–3 concrete options as lines starting with '• ' " +
        "(name, rough portion, ~kcal and protein). Then one line starting with 'Tip: '. " +
        "Under 110 words, plain text, no markdown headers or bold. Match the user's language.",
    user = buildContext() + if (atHome.isNotBlank()) "\n\n## Food I have at home right now\n$atHome" else "",
    maxTokens = 500,
)

/** AI: confirm an imported plan in a few short lines (what it understood). */
suspend fun Repo.summarizePlan(type: String, text: String): List<String> {
    val o = claude.askJson(
        system = "The user imported their ${PlanType.label(type).lowercase()} from a spreadsheet (rows are ' | ' separated). " +
            "Summarise what you understood in 3–5 very short lines (max 9 words each), e.g. one line per training day " +
            "or block, so they can check it was read correctly. Return {\"lines\": [..]}. Match the user's language.",
        user = text, maxTokens = 400,
    )
    return ((o as? JsonObject)?.get("lines")?.jsonArray ?: return emptyList())
        .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.take(5)
}

/** Rule-based advice when there's no API key (or as an instant placeholder). */
fun localMoves(
    hour: Int,
    openQuests: List<String>,
    kcal: Int, kcalGoal: Int,
    protein: Int, proteinGoal: Int,
    water: Int, waterGoal: Int,
    checkInDone: Boolean,
    eveningHour: Int,
    steps: Int = -1,
): List<Advice> {
    val out = mutableListOf<Advice>()
    if (steps in 0 until 5000 && hour in 14..20)
        out += Advice("Take a 20-minute walk", "Around the block, no phone needed.", "Only $steps steps so far today.", "rest")
    if (hour >= eveningHour && !checkInDone)
        out += Advice("File your evening report", "Two minutes: mood, energy, what went well.", "It closes the day and feeds tomorrow's quests.", "mind")
    if (hour >= 23 || hour < 5)
        out += Advice("Go to sleep", "Screens off, lights down.", "Sleep is the cheapest stat boost there is.", "sleep")
    val pLeft = proteinGoal - protein
    if (pLeft > 30 && hour >= 11)
        out += Advice("Eat ${minOf(pLeft, 50)} g of protein", "Skyr, eggs, chicken, tuna or a shake.", "You're $pLeft g short of today's target.", "food")
    if (kcal == 0 && hour in 6..11)
        out += Advice("Log your breakfast", "Snap a photo or type it in the Food tab.", "Nothing logged yet today.", "food")
    if (water < waterGoal / 2 && hour >= 13)
        out += Advice("Drink two glasses of water", "Then tap +1 twice in Food.", "Only $water of $waterGoal glasses so far.", "rest")
    openQuests.firstOrNull()?.let { out += Advice("Clear: $it", "Knock it out now while you have momentum.", "${openQuests.size} quest(s) still open today.", "quest") }
    if (kcal > kcalGoal * 1.1)
        out += Advice("Keep the rest of today light", "Veggies and lean protein only.", "You're over today's calories.", "food")
    if (out.isEmpty())
        out += Advice("Start a 50-minute focus block", "Pick one hard task. Phone face down.", "All clear — bank some Discipline EXP.", "work")
    return out.take(3)
}
