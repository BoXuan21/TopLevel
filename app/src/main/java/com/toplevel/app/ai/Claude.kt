package com.toplevel.app.ai

import com.toplevel.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClaudeException(msg: String) : Exception(msg)

/** Minimal client for the Anthropic Messages API, with tool use. */
class Claude(private val prefs: Prefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Raw call. Returns the full response object.
     *
     * Token saving: in eco mode (default) every call runs on the cheap model, and all calls ask for
     * low effort (thinking is billed as output). [system] is the stable part (cached when [cache]);
     * [dynamic] is appended after it, so the stable part keeps hitting the cache across messages.
     */
    suspend fun send(
        system: String,
        messages: JsonArray,
        tools: JsonArray? = null,
        maxTokens: Int = 2048,
        small: Boolean = false,
        cache: Boolean = false,
        toolChoice: JsonObject? = null,
        dynamic: String? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        if (!prefs.hasKey) throw ClaudeException("Add your Anthropic API key in Setup first.")
        val model = modelFor(small)
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            if (cache || dynamic != null) {
                put("system", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text"); put("text", system)
                        if (cache) put("cache_control", buildJsonObject { put("type", "ephemeral") })
                    })
                    if (dynamic != null) add(buildJsonObject {
                        put("type", "text"); put("text", dynamic)
                        // Second breakpoint: tool rounds of the same message re-read it from cache
                        if (cache) put("cache_control", buildJsonObject { put("type", "ephemeral") })
                    })
                })
            } else put("system", system)
            put("messages", messages)
            if (tools != null) put("tools", tools)
            if (toolChoice != null) put("tool_choice", toolChoice)
            if (supportsEffort(model)) put("output_config", buildJsonObject { put("effort", "low") })
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", prefs.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = try {
                    json.parseToJsonElement(text).jsonObject["error"]?.jsonObject
                        ?.get("message")?.jsonPrimitive?.contentOrNull
                } catch (_: Exception) { null }
                throw ClaudeException("Claude API ${resp.code}: ${msg ?: text.take(200)}")
            }
            json.parseToJsonElement(text).jsonObject.also { trackUsage(model, it) }
        }
    }

    /** Eco mode: everything on the cheap model. Otherwise the chosen model, and the cheap one for small jobs. */
    fun modelFor(small: Boolean): String = if (prefs.eco || small) SMALL_MODEL else prefs.model

    /** Adds this call's tokens and estimated cost to the monthly counters shown in Settings. */
    private fun trackUsage(model: String, res: JsonObject) {
        val u = res["usage"] as? JsonObject ?: return
        fun n(k: String) = str(u, k)?.toLongOrNull() ?: 0L
        prefs.addUsage(n("input_tokens"), n("output_tokens"), n("cache_read_input_tokens"),
            n("cache_creation_input_tokens"), costUsd(model, n("input_tokens"), n("output_tokens"),
                n("cache_read_input_tokens"), n("cache_creation_input_tokens")))
    }

    /**
     * One-shot text answer. Current models think before answering and that counts against max_tokens,
     * so small budgets could end with no text at all: use a floor, and retry bigger if the answer got cut off.
     */
    suspend fun ask(system: String, user: String, maxTokens: Int = 1500, small: Boolean = false): String {
        var budget = maxOf(maxTokens, MIN_TEXT_TOKENS)
        repeat(2) {
            val res = send(system, buildJsonArray { add(userMsg(user)) }, maxTokens = budget, small = small)
            val text = textOf(res)
            if (text.isNotBlank() && str(res, "stop_reason") != "max_tokens") return text
            if (text.isNotBlank() && it == 1) return text // cut off twice: better a long answer than none
            budget = (budget * 2).coerceAtMost(8000)
        }
        throw ClaudeException("Claude didn't send an answer. Try again.")
    }

    /** One-shot answer parsed as JSON. */
    suspend fun askJson(system: String, user: String, maxTokens: Int = 2000, small: Boolean = false): JsonElement =
        sendJson(system, buildJsonArray { add(userMsg(user)) }, maxTokens, small)

    /**
     * Structured answer. Claude is asked to call the `answer` tool, so the API hands back
     * parsed JSON instead of free text (with a JSON-in-text fallback). If the answer was cut off
     * (token limit) or unreadable, retry with a bigger budget before giving up.
     *
     * The tool is NOT forced: current models (Sonnet 5.5, Opus 5.5, …) reject
     * tool_choice "tool"/"any" with HTTP 400, so we use "auto" and steer from the prompt.
     */
    suspend fun sendJson(system: String, messages: JsonArray, maxTokens: Int = 2000, small: Boolean = false): JsonElement {
        var budget = maxOf(maxTokens, MIN_JSON_TOKENS)
        repeat(3) {
            val res = send(
                "$system\n\nAlways answer by calling the `answer` tool exactly once, with the complete JSON in `data`. " +
                    "Don't write anything else.",
                messages, JsonArray(listOf(ANSWER_TOOL)), budget, small, toolChoice = ANSWER_CHOICE,
            )
            parseAnswer(res)?.let { return it }
            if (str(res, "stop_reason") == "max_tokens") budget = (budget * 2).coerceAtMost(8000)
        }
        throw ClaudeException("Claude didn't return valid JSON. Try again.")
    }

    fun extractJson(txt: String): JsonElement? = extractJsonText(txt)

    companion object {
        /** Fast, cheap model for small structured jobs (and everything in eco mode). */
        const val SMALL_MODEL = "claude-haiku-5-5"

        /** Effort ("low") is accepted by the Claude 5 family and Opus/Sonnet 4.6+; older models reject it. */
        fun supportsEffort(model: String): Boolean =
            Regex("claude-(opus|sonnet|haiku|fable|mythos)-5|claude-opus-4-[678]|claude-sonnet-4-6").containsMatchIn(model)

        /** $ per million tokens (input, output); cache reads 0.1×, cache writes 1.25× input. */
        fun pricePerM(model: String): Pair<Double, Double> = when {
            "haiku-5" in model -> 0.10 to 0.50
            "haiku" in model -> 1.0 to 5.0
            "sonnet" in model -> 2.0 to 10.0
            "opus-5-5" in model -> 4.0 to 20.0
            "opus" in model -> 5.0 to 25.0
            "fable-5-2" in model || "mythos-5-2" in model -> 8.0 to 40.0
            else -> 10.0 to 50.0
        }

        fun costUsd(model: String, input: Long, output: Long, cacheRead: Long, cacheWrite: Long): Double {
            val (i, o) = pricePerM(model)
            return (input * i + output * o + cacheRead * i * 0.1 + cacheWrite * i * 1.25) / 1_000_000.0
        }

        /** Tight budgets were the main cause of cut-off (= invalid) JSON. Thinking also uses this budget. */
        const val MIN_JSON_TOKENS = 1500
        const val MIN_TEXT_TOKENS = 1500

        private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

        val ANSWER_TOOL = buildJsonObject {
            put("name", "answer")
            put("description", "Return the answer. `data` holds the complete JSON answer (object or array) in exactly the shape the instructions ask for.")
            put("input_schema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("data", buildJsonObject { put("description", "The JSON answer (object or array).") })
                })
                put("required", JsonArray(listOf(JsonPrimitive("data"))))
            })
        }

        /** "auto" works on every model; forced choices ("tool"/"any") are a 400 on current models. */
        val ANSWER_CHOICE = buildJsonObject { put("type", "auto"); put("disable_parallel_tool_use", true) }

        /** Pull the JSON answer out of a response: the `answer` tool input first, then any JSON in the text. */
        fun parseAnswer(res: JsonObject): JsonElement? {
            val blocks = res["content"] as? JsonArray ?: return null
            val input = blocks.mapNotNull { it as? JsonObject }
                .firstOrNull { str(it, "type") == "tool_use" }?.get("input") as? JsonObject
            if (input != null) {
                val data = input["data"]
                when {
                    data is JsonObject || data is JsonArray -> return data
                    data is JsonPrimitive && data.isString -> extractJsonText(data.content)?.let { return it }
                    data == null && input.isNotEmpty() -> return input // model put the fields at top level
                }
            }
            return extractJsonText(textOf(res))
        }

        /** Tolerates code fences and chatter around the JSON. */
        fun extractJsonText(txt: String): JsonElement? {
            val t = txt.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            if (t.isEmpty()) return null
            try { return lenient.parseToJsonElement(t) } catch (_: Exception) { }
            val starts = listOf(t.indexOf('{'), t.indexOf('[')).filter { it >= 0 }
            if (starts.isEmpty()) return null
            val s = starts.min()
            val e = maxOf(t.lastIndexOf('}'), t.lastIndexOf(']'))
            if (e <= s) return null
            val cut = t.substring(s, e + 1)
            try { return lenient.parseToJsonElement(cut) } catch (_: Exception) { }
            // Common slip: trailing commas before } or ]
            return try { lenient.parseToJsonElement(cut.replace(Regex(",\\s*([}\\]])"), "$1")) } catch (_: Exception) { null }
        }

        fun userMsg(text: String) = buildJsonObject {
            put("role", "user")
            put("content", text)
        }

        fun assistantMsg(text: String) = buildJsonObject {
            put("role", "assistant")
            put("content", text)
        }

        fun textOf(res: JsonObject): String =
            res["content"]?.jsonArray.orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                .joinToString("\n") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                .trim()

        fun str(o: JsonObject?, key: String): String? =
            (o?.get(key) as? JsonPrimitive)?.contentOrNull

        fun int(o: JsonObject?, key: String, def: Int = 0): Int =
            str(o, key)?.toDoubleOrNull()?.toInt() ?: def

        /** Helper to declare a tool with string/integer properties. */
        fun tool(name: String, description: String, props: Map<String, Pair<String, String>>, required: List<String>) =
            buildJsonObject {
                put("name", name)
                put("description", description)
                put("input_schema", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        props.forEach { (k, v) ->
                            put(k, buildJsonObject {
                                put("type", v.first)
                                put("description", v.second)
                            })
                        }
                    })
                    put("required", JsonArray(required.map { JsonPrimitive(it) }))
                })
            }
    }
}
