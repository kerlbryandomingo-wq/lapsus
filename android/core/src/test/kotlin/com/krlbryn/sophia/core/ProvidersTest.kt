package com.krlbryn.sophia.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProvidersTest {
    private lateinit var server: MockWebServer
    private val answer = """{"title":"T","explanation":"E","keyPoints":[],"flashcards":[],"practiceQuestions":[],"missingInformation":[],"confidence":"high","warning":null}"""

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun base() = server.url("/v1").toString().trimEnd('/')

    @Test fun compatibleProviderSendsJsonModeAndBearerKey() = runTest {
        val content = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(answer))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":$content},"finish_reason":"stop"}]}"""))
        val p = OpenAiProvider(OkHttpClient(), AiProviderId.COMPATIBLE)
        val text = p.ask(AiRequest("key-123", "llama", "hello", baseUrl = base()))
        assertTrue(StudyAssistant.parseAnswer(text) is ParseResult.Ok)
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer key-123", req.getHeader("Authorization"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun maps401ToAuthError() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            OpenAiProvider(OkHttpClient(), AiProviderId.COMPATIBLE).ask(AiRequest("bad", "m", "x", base()))
            fail()
        } catch (e: ProviderException) {
            assertEquals(ProviderException.Code.AUTH, e.code)
        }
    }

    @Test fun refusalIsReported() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":null,"refusal":"no"},"finish_reason":"stop"}]}"""))
        try {
            OpenAiProvider(OkHttpClient(), AiProviderId.COMPATIBLE).ask(AiRequest("k", "m", "x", base()))
            fail()
        } catch (e: ProviderException) {
            assertEquals(ProviderException.Code.REFUSED, e.code)
        }
    }

    @Test fun compatibleRequiresHttpsBaseUrl() {
        assertNull(AiConfigValidation.normalizeBaseUrl("http://api.example.com/v1"))
        assertEquals("https://api.example.com/v1", AiConfigValidation.normalizeBaseUrl("https://api.example.com/v1/"))
        assertNull(AiConfigValidation.normalizeBaseUrl("https://u:p@api.example.com/v1"))
    }

    @Test fun anthropicUsesStructuredOutputAndFallback() = runTest {
        val text = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(answer))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                "content":[{"type":"text","text":$text}],"stop_reason":"end_turn","stop_sequence":null,
                "usage":{"input_tokens":10,"output_tokens":20}}""",
            ),
        )
        val p = AnthropicProvider(baseUrlOverride = server.url("/").toString().trimEnd('/'))
        val out = p.ask(AiRequest("sk-ant-test", "claude-opus-5-5", "hello"))
        assertTrue(StudyAssistant.parseAnswer(out) is ParseResult.Ok)
        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        val auth = req.getHeader("x-api-key") ?: req.getHeader("Authorization").orEmpty()
        assertTrue(auth.endsWith("sk-ant-test"))
        assertTrue(req.getHeader("anthropic-beta").orEmpty().contains("server-side-fallback-2026-07-01"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        val format = body["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test fun anthropicRefusalIsReported() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],
                "stop_reason":"refusal","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":0}}""",
            ),
        )
        try {
            AnthropicProvider(server.url("/").toString().trimEnd('/')).ask(AiRequest("k", "claude-opus-5-5", "x"))
            fail()
        } catch (e: ProviderException) {
            assertEquals(ProviderException.Code.REFUSED, e.code)
        }
    }

    @Test fun anthropic401IsAuth() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
            .setBody("""{"type":"error","error":{"type":"authentication_error","message":"bad"}}"""))
        try {
            AnthropicProvider(server.url("/").toString().trimEnd('/')).ask(AiRequest("k", "claude-haiku-5-5", "x"))
            fail()
        } catch (e: ProviderException) {
            assertEquals(ProviderException.Code.AUTH, e.code)
        }
    }

    @Test fun demoNeverUsesNetwork() = runTest {
        val out = DemoProvider().ask(AiRequest("", "demo", StudyAssistant.buildUserMessage(StudyMode.EXPLAIN, "What is osmosis?", "")))
        val r = StudyAssistant.parseAnswer(out) as ParseResult.Ok
        assertEquals("What is osmosis?", r.answer.title)
        assertEquals(0, server.requestCount)
    }
}
