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

    /** Raw call. Returns the full response object. */
    suspend fun send(
        system: String,
        messages: JsonArray,
        tools: JsonArray? = null,
        maxTokens: Int = 2048,
        small: Boolean = false,
        cache: Boolean = false,
    ): JsonObject = withContext(Dispatchers.IO) {
        if (!prefs.hasKey) throw ClaudeException("Add your Anthropic API key in Setup first.")
        val body = buildJsonObject {
            // Small, well-defined jobs (macros, summaries) run on the cheaper fast model
            put("model", if (small) SMALL_MODEL else prefs.model)
            put("max_tokens", maxTokens)
            if (cache) {
                // Cache the (large) system prompt so repeated chat/tool rounds re-use it cheaply
                put("system", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text"); put("text", system)
                        put("cache_control", buildJsonObject { put("type", "ephemeral") })
                    })
                })
            } else put("system", system)
            put("messages", messages)
            if (tools != null) put("tools", tools)
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
            json.parseToJsonElement(text).jsonObject
        }
    }

    /** One-shot text answer. */
    suspend fun ask(system: String, user: String, maxTokens: Int = 1500, small: Boolean = false): String {
        val res = send(system, buildJsonArray { add(userMsg(user)) }, maxTokens = maxTokens, small = small)
        return textOf(res)
    }

    /** One-shot answer parsed as JSON (tolerates code fences / chatter). */
    suspend fun askJson(system: String, user: String, maxTokens: Int = 2000, small: Boolean = false): JsonElement {
        val txt = ask("$system\n\nRespond with JSON only. No prose, no code fences.", user, maxTokens, small)
        return extractJson(txt) ?: throw ClaudeException("Claude didn't return valid JSON.")
    }

    fun extractJson(txt: String): JsonElement? {
        val t = txt.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        try { return json.parseToJsonElement(t) } catch (_: Exception) { }
        val starts = listOf(t.indexOf('{'), t.indexOf('[')).filter { it >= 0 }
        if (starts.isEmpty()) return null
        val s = starts.min()
        val e = maxOf(t.lastIndexOf('}'), t.lastIndexOf(']'))
        if (e <= s) return null
        return try { json.parseToJsonElement(t.substring(s, e + 1)) } catch (_: Exception) { null }
    }

    companion object {
        /** Fast, cheap model for small structured jobs. */
        const val SMALL_MODEL = "claude-haiku-5-5"

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
