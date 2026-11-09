package com.krlbryn.sophia.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AiProviderId(val label: String, val defaultModel: String) {
    NONE("Off", ""),
    OPENAI("OpenAI", "gpt-5-mini"),
    ANTHROPIC("Anthropic Claude", "claude-opus-5-5"),
    COMPATIBLE("OpenAI-compatible service", ""),
    DEMO("Demo (offline, no AI)", "demo"),
}

class ProviderException(message: String, val code: Code) : Exception(message) {
    enum class Code { AUTH, NETWORK, REFUSED, INVALID_RESPONSE, CANCELLED, CONFIG, UNKNOWN }
}

data class AiRequest(
    val apiKey: String,
    val model: String,
    val userMessage: String,
    /** Only for [AiProviderId.COMPATIBLE] (and tests): an https base URL such as https://api.example.com/v1 */
    val baseUrl: String? = null,
)

/** Every provider returns the raw text of a JSON answer; parsing and validation happen once, in the caller. */
interface AiProvider {
    val id: AiProviderId
    suspend fun ask(req: AiRequest): String
    suspend fun test(apiKey: String, model: String, baseUrl: String? = null)
}

private const val REQUEST_TIMEOUT_S = 90L
private val MODEL_RE = Regex("^[\\w.:/-]{1,100}$")

object AiConfigValidation {
    fun isValidModel(model: String): Boolean = MODEL_RE.matches(model.trim())

    /** Accepts only https base URLs (or loopback http for local servers) without credentials. */
    fun normalizeBaseUrl(raw: String): String? {
        val url = raw.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null
        if (url.scheme != "https" && !UrlPolicy.isLoopbackHost(url.host)) return null
        return url.toString().trimEnd('/')
    }
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

/**
 * OpenAI Chat Completions. Also serves "OpenAI-compatible" services when given a base URL; those get
 * JSON mode instead of strict schemas, because many compatible servers do not support strict mode.
 */
class OpenAiProvider(
    private val http: OkHttpClient,
    override val id: AiProviderId = AiProviderId.OPENAI,
) : AiProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = http.newBuilder().callTimeout(REQUEST_TIMEOUT_S, TimeUnit.SECONDS).build()

    private fun base(baseUrl: String?): String {
        if (id == AiProviderId.OPENAI && baseUrl == null) return "https://api.openai.com/v1"
        return baseUrl?.let { AiConfigValidation.normalizeBaseUrl(it) }
            ?: throw ProviderException("Enter a valid https base URL for the AI service in Settings.", ProviderException.Code.CONFIG)
    }

    override suspend fun ask(req: AiRequest): String {
        val strict = id == AiProviderId.OPENAI
        val body = buildJsonObject {
            put("model", req.model)
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", systemPrompt(strict)) })
                    add(buildJsonObject { put("role", "user"); put("content", req.userMessage) })
                },
            )
            put(
                "response_format",
                if (strict) {
                    buildJsonObject {
                        put("type", "json_schema")
                        put(
                            "json_schema",
                            buildJsonObject {
                                put("name", "study_answer")
                                put("strict", true)
                                put("schema", StudyAssistant.ANSWER_JSON_SCHEMA)
                            },
                        )
                    }
                } else {
                    buildJsonObject { put("type", "json_object") }
                },
            )
        }
        val text = execute(req.apiKey, "${base(req.baseUrl)}/chat/completions", body.toString())
        val data = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw ProviderException("The AI provider returned an unreadable response.", ProviderException.Code.INVALID_RESPONSE)
        val choice = data["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject
        val refusal = (message?.get("refusal") as? JsonPrimitive)?.contentOrNull
        if (!refusal.isNullOrBlank()) {
            throw ProviderException("The AI provider declined to answer this request.", ProviderException.Code.REFUSED)
        }
        if ((choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull == "length") {
            throw ProviderException("The AI response was cut off before it finished.", ProviderException.Code.INVALID_RESPONSE)
        }
        val content = (message?.get("content") as? JsonPrimitive)?.contentOrNull
        if (content.isNullOrBlank()) {
            throw ProviderException("The AI provider returned an empty response.", ProviderException.Code.INVALID_RESPONSE)
        }
        return content
    }

    private fun systemPrompt(strict: Boolean): String =
        if (strict) {
            StudyAssistant.SYSTEM_PROMPT
        } else {
            StudyAssistant.SYSTEM_PROMPT + "\n\nThe JSON must match this JSON Schema:\n" + StudyAssistant.ANSWER_JSON_SCHEMA
        }

    override suspend fun test(apiKey: String, model: String, baseUrl: String?) {
        val path = if (id == AiProviderId.OPENAI) "/models/${java.net.URLEncoder.encode(model, "UTF-8")}" else "/models"
        execute(apiKey, base(baseUrl) + path, null)
    }

    private suspend fun execute(apiKey: String, url: String, body: String?): String {
        val builder = Request.Builder().url(url).header("Authorization", "Bearer $apiKey")
        if (body != null) builder.post(body.toRequestBody("application/json".toMediaType()))
        val response = try {
            client.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw ProviderException("Could not reach ${label()}. Check your internet connection.", ProviderException.Code.NETWORK)
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (res.isSuccessful) return text
            throw when (res.code) {
                401, 403 -> ProviderException("The ${label()} API key was rejected. Check it in Settings.", ProviderException.Code.AUTH)
                404 -> ProviderException("The selected ${label()} model was not found. Check the model name in Settings.", ProviderException.Code.AUTH)
                429 -> ProviderException("${label()} rate limit or billing limit reached. Try again later.", ProviderException.Code.NETWORK)
                else -> ProviderException("${label()} is unavailable right now (HTTP ${res.code}).", ProviderException.Code.NETWORK)
            }
        }
    }

    private fun label() = if (id == AiProviderId.OPENAI) "OpenAI" else "the AI service"
}

/**
 * Offline provider for trying the assistant and for automated tests. Its answers are canned and
 * clearly labelled as a demo; it never contacts the network.
 */
class DemoProvider : AiProvider {
    override val id = AiProviderId.DEMO

    override suspend fun ask(req: AiRequest): String {
        delay(300)
        val material = Regex("<student_material>\\n([\\s\\S]*)\\n</student_material>").find(req.userMessage)?.groupValues?.get(1).orEmpty()
        val firstLine = material.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120) ?: "your material"
        val answer = StudyAnswer(
            title = firstLine,
            explanation = "This is the offline demo provider. It shows how Sophia presents an answer, but it does not analyse your material. Choose OpenAI, Anthropic or a compatible service in Settings and add your own API key to get real explanations.",
            keyPoints = listOf("No analysis was performed in demo mode."),
            flashcards = listOf(Flashcard("What does demo mode do?", "It shows the answer layout without contacting any AI service.")),
            practiceQuestions = emptyList(),
            missingInformation = listOf("A configured AI provider"),
            confidence = Confidence.LOW,
            warning = "Demo answer only. Do not rely on it.",
        )
        return Json.encodeToString(StudyAnswer.serializer(), answer)
    }

    override suspend fun test(apiKey: String, model: String, baseUrl: String?) = Unit
}
