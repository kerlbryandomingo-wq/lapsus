package com.krlbryn.sophia.core

import com.anthropic.client.AnthropicClientAsync
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.util.concurrent.CompletionException

/** Anthropic Messages API through the official Anthropic Java SDK, with structured JSON output. */
class AnthropicProvider(
    /** Overrides the API host; used by tests against a local mock server. */
    private val baseUrlOverride: String? = null,
) : AiProvider {
    override val id = AiProviderId.ANTHROPIC

    private fun client(apiKey: String): AnthropicClientAsync {
        // Only the explicit key is used; nothing is read from the environment.
        val builder = AnthropicOkHttpClientAsync.builder()
            .apiKey(apiKey)
            .maxRetries(1)
            .timeout(Duration.ofSeconds(120))
        baseUrlOverride?.let { builder.baseUrl(it) }
        return builder.build()
    }

    override suspend fun ask(req: AiRequest): String {
        val client = client(req.apiKey)
        try {
            val schema = JsonOutputFormat.Schema.builder().apply {
                StudyAssistant.ANSWER_JSON_SCHEMA.forEach { (k, v) -> putAdditionalProperty(k, v.toJsonValue()) }
            }.build()
            val params = MessageCreateParams.builder()
                .model(req.model)
                .maxTokens(16000L)
                .system(StudyAssistant.SYSTEM_PROMPT)
                .addUserMessage(req.userMessage)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(schema).build())
                        .build(),
                )
                .apply {
                    if (FALLBACK_MODELS.matches(req.model)) {
                        // Server-side refusal fallback: the API retries a refused request on a suitable model.
                        putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                        putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    }
                }
                .build()
            val message = client.messages().create(params).await()
            when (message.stopReason().orElse(null)) {
                StopReason.REFUSAL -> throw ProviderException("The AI provider declined to answer this request.", ProviderException.Code.REFUSED)
                StopReason.MAX_TOKENS -> throw ProviderException("The AI response was cut off before it finished.", ProviderException.Code.INVALID_RESPONSE)
                else -> Unit
            }
            val text = message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")
            if (text.isBlank()) throw ProviderException("The AI provider returned an empty response.", ProviderException.Code.INVALID_RESPONSE)
            return text
        } catch (e: Throwable) {
            throw mapError(e)
        } finally {
            client.close()
        }
    }

    override suspend fun test(apiKey: String, model: String, baseUrl: String?) {
        val client = client(apiKey)
        try {
            client.models().retrieve(model).await()
        } catch (e: Throwable) {
            throw mapError(e)
        } finally {
            client.close()
        }
    }

    private fun mapError(raw: Throwable): Throwable {
        val e = if (raw is CompletionException && raw.cause != null) raw.cause!! else raw
        return when (e) {
            is ProviderException, is CancellationException -> e
            is UnauthorizedException, is PermissionDeniedException ->
                ProviderException("The Anthropic API key was rejected. Check it in Settings.", ProviderException.Code.AUTH)
            is NotFoundException ->
                ProviderException("The selected Claude model was not found. Check the model name in Settings.", ProviderException.Code.AUTH)
            is RateLimitException ->
                ProviderException("Anthropic rate limit or spending limit reached. Try again later.", ProviderException.Code.NETWORK)
            is BadRequestException ->
                ProviderException("Anthropic rejected the request. The model may not support this feature.", ProviderException.Code.UNKNOWN)
            is AnthropicIoException ->
                ProviderException("Could not reach Anthropic. Check your internet connection.", ProviderException.Code.NETWORK)
            is AnthropicServiceException ->
                ProviderException("Anthropic is unavailable right now (HTTP ${e.statusCode()}).", ProviderException.Code.NETWORK)
            else -> ProviderException("The AI request failed.", ProviderException.Code.UNKNOWN)
        }
    }

    companion object {
        /** Models that accept the server-side refusal fallback. */
        private val FALLBACK_MODELS = Regex("^claude-(?:opus-5|opus-5-5|sonnet-5-5|fable-5-1)$")
    }
}

internal fun JsonElement.toJsonValue(): JsonValue = when (this) {
    is JsonNull -> JsonValue.from(null)
    is JsonObject -> JsonValue.from(mapValues { it.value.toJsonValue() })
    is JsonArray -> JsonValue.from(map { it.toJsonValue() })
    is JsonPrimitive -> when {
        isString -> JsonValue.from(content)
        booleanOrNull != null -> JsonValue.from(booleanOrNull)
        longOrNull != null -> JsonValue.from(longOrNull)
        else -> JsonValue.from(content.toDouble())
    }
}
