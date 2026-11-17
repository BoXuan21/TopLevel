package com.toplevel.app.data

import com.toplevel.app.ai.Claude
import com.toplevel.app.ai.ClaudeException
import com.toplevel.app.ai.Claude.Companion.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/** One shop's price for the product. */
data class ShopOffer(
    val shop: String,
    val title: String,
    val price: Double,
    val shipping: Double?,
    val url: String,
    val condition: String,
    val inStock: Boolean?,
    val note: String,
) {
    val total: Double get() = price + (shipping ?: 0.0)
}

data class ShopAlternative(val name: String, val why: String, val approx: Double?)

data class ShopResult(
    val query: String,
    val product: String,
    val summary: String,
    val offers: List<ShopOffer>,
    val alternatives: List<ShopAlternative>,
    val at: Long = System.currentTimeMillis(),
) {
    val cheapest: ShopOffer? get() = offers.minByOrNull { it.total }
}

object Shop {
    /** Newest first, no duplicates, max 8. */
    fun remember(recent: String, query: String): String =
        (listOf(query.trim()) + recent.lines()).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.take(8).joinToString("\n")

    fun parse(query: String, o: JsonObject): ShopResult {
        fun num(x: JsonObject, k: String): Double? {
            var t = str(x, k)?.replace(Regex("[^0-9.,]"), "") ?: return null
            if (',' in t && '.' in t) t = t.replace(".", "") // "1.299,00"
            return t.replace(",", ".").toDoubleOrNull()
        }
        val offers = (o["offers"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { x ->
            val price = num(x, "price_eur") ?: return@mapNotNull null
            val url = str(x, "url").orEmpty()
            if (price <= 0.0 || !url.startsWith("http")) return@mapNotNull null
            ShopOffer(
                shop = str(x, "shop") ?: url.substringAfter("://").substringBefore('/').removePrefix("www."),
                title = str(x, "title").orEmpty(),
                price = price,
                shipping = num(x, "shipping_eur"),
                url = url,
                condition = str(x, "condition")?.lowercase() ?: "new",
                inStock = str(x, "in_stock")?.toBooleanStrictOrNull(),
                note = str(x, "note").orEmpty(),
            )
        }.sortedBy { it.total }
        val alts = (o["alternatives"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { a ->
            ShopAlternative(str(a, "name") ?: return@mapNotNull null, str(a, "why").orEmpty(), num(a, "approx_eur"))
        }
        return ShopResult(query, str(o, "product") ?: query, str(o, "summary").orEmpty(), offers, alts)
    }

    /** Web search with dynamic filtering on current models; the basic version for older ones. */
    fun webSearchTool(basic: Boolean, country: String?) = buildJsonObject {
        put("type", if (basic) "web_search_20250305" else "web_search_20260209")
        put("name", "web_search")
        put("max_uses", 8)
        if (country != null) put("user_location", buildJsonObject { put("type", "approximate"); put("country", country) })
    }
}

/**
 * Finds where a product is cheapest right now: Claude searches the web (price comparison sites and shops)
 * and returns the offers it found, cheapest first.
 */
suspend fun Repo.searchShop(query: String, maxEur: Int?, usedOk: Boolean): ShopResult {
    val country = Locale.getDefault().country.takeIf { it.length == 2 }
    val system = """You find the cheapest place to buy a product online, for a user in ${country ?: "Europe"} paying in EUR.
Use web search. Check price comparison sites for that country (e.g. Geizhals, idealo, billiger.de in DE/AT; PCPartPicker, Google Shopping)
and the shops themselves (Amazon, MediaMarkt, Alternate, Mindfactory, Caseking, Cyberport, notebooksbilliger, Galaxus, eBay…).
Compare at least 4 different shops if they exist. Only list offers you actually saw in search results, with the real URL of the
offer page (or the comparison-site page for it). Prices in EUR incl. VAT; add shipping if known.
${if (usedOk) "Used and refurbished offers are welcome; mark them." else "New items only."}
${maxEur?.let { "The user's budget is €$it. If the exact product is above it, say so and suggest cheaper alternatives." } ?: ""}
If the query is vague (e.g. "good GPU for 1440p"), pick the best-value concrete product and list alternatives.
Write the summary and notes in the user's language (the language of the query).
Return {"product":"exact product name","summary":"1-2 sentences: where it's cheapest, total price, any catch",
"offers":[{"shop":"...","title":"product as listed","price_eur":0.0,"shipping_eur":0.0,"url":"https://...","condition":"new|used|refurbished","in_stock":true,"note":"short, optional"}],
"alternatives":[{"name":"...","why":"short","approx_eur":0.0}]}
Always answer by calling the `answer` tool exactly once, with the complete JSON in `data`, after your searches."""

    suspend fun attempt(basic: Boolean): ShopResult {
        val tools = JsonArray(listOf(Shop.webSearchTool(basic, country), Claude.ANSWER_TOOL))
        val messages = mutableListOf<JsonObject>(Claude.userMsg("Find the cheapest offers for: $query"))
        repeat(5) {
            val res = claude.send(system, JsonArray(messages), tools, maxTokens = 8000,
                toolChoice = buildJsonObject { put("type", "auto") })
            val stop = str(res, "stop_reason")
            val answer = (res["content"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .any { str(it, "type") == "tool_use" && str(it, "name") == "answer" }
            if (answer || stop == "end_turn") Claude.parseAnswer(res)?.let { el ->
                (el as? JsonObject)?.let { return Shop.parse(query, it) }
            }
            if (stop == "refusal") throw ClaudeException("Claude declined this search.")
            // Long searches pause; hand the turn back so it continues. Otherwise ask for the answer.
            messages += buildJsonObject { put("role", "assistant"); put("content", res["content"] ?: buildJsonArray { }) }
            if (stop != "pause_turn") {
                if (answer) {
                    // An answer we couldn't read: close the tool call and ask again
                    val id = (res["content"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                        .last { str(it, "type") == "tool_use" }.let { str(it, "id").orEmpty() }
                    messages += buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "tool_result"); put("tool_use_id", id); put("is_error", true)
                                put("content", "Invalid. `data` must be the JSON object described above.")
                            })
                        })
                    }
                } else messages += Claude.userMsg("Now call the `answer` tool with the offers you found.")
            }
        }
        throw ClaudeException("The price search didn't finish. Try again or make the query more specific.")
    }

    val result = try {
        attempt(basic = false)
    } catch (e: ClaudeException) {
        // Older models only know the basic web search tool
        if (e.message.orEmpty().contains("web_search", true) && e.message.orEmpty().contains("400")) attempt(basic = true)
        else throw e
    }
    prefs.shopRecent = Shop.remember(prefs.shopRecent, query)
    return result
}
