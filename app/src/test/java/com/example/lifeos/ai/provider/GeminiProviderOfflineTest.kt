package com.example.lifeos.ai.provider

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Prompt section 39 ("AI failures must NEVER cause the core application to
 * fail... show a clear error, do not crash") and section 42 (offline-first)
 * both hinge on [GeminiProvider.sendTurn] correctly turning every kind of
 * network/response failure into a catchable [GeminiProvider.AIProviderException]
 * rather than letting a raw exception (or worse, a silent wrong answer)
 * escape. This had no test coverage before — these tests hit a real local
 * HTTP server via MockWebServer, so they exercise the actual OkHttp call
 * path (timeouts, connection refusal, malformed JSON, HTTP error codes),
 * not a mocked stand-in for it.
 */
class GeminiProviderOfflineTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: GeminiProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = GeminiProvider()
        provider.baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- Missing API key: must fail fast, without ever touching the network ---

    @Test
    fun sendTurn_blankApiKey_throwsWithoutMakingNetworkCall() = runTest {
        try {
            provider.sendTurn(
                apiKey = "",
                systemInstruction = "test",
                history = listOf("user" to "hi"),
                functionDeclarations = JSONArray()
            )
            fail("expected AIProviderException")
        } catch (e: GeminiProvider.AIProviderException) {
            assertTrue(e.message?.contains("کلید") == true)
        }
        // No request should have reached the (fake) server at all.
        assertEquals(0, server.requestCount)
    }

    // --- True "offline" simulation: connection never completes / is refused ---

    @Test
    fun sendTurn_connectionFailure_throwsAIProviderExceptionNotRawException() = runTest {
        // Simulate the device having no network path to the server at all
        // by shutting the fake server down before the call is made.
        server.shutdown()

        try {
            provider.sendTurn(
                apiKey = "fake-key",
                systemInstruction = "test",
                history = listOf("user" to "hi"),
                functionDeclarations = JSONArray()
            )
            fail("expected AIProviderException")
        } catch (e: GeminiProvider.AIProviderException) {
            // This is the exact exception type AIChatViewModel catches to
            // show a friendly message and keep the rest of the app usable
            // (prompt section 39) -- if this were some other exception type,
            // it would propagate uncaught and could crash the screen.
            assertTrue(e.message?.contains("اینترنت") == true || e.message?.contains("اتصال") == true)
        }
    }

    @Test
    fun sendTurn_socketTimeout_throwsAIProviderException() = runTest {
        // No response is ever enqueued and the connection is held open,
        // forcing OkHttp's read timeout to fire -- this is what a slow/dead
        // mobile network looks like from the app's perspective, distinct
        // from an outright connection refusal. Use a short client timeout
        // here so the test doesn't have to wait out the real 30s production
        // read timeout to observe the same failure path.
        provider.client = provider.client.newBuilder()
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build()
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        try {
            provider.sendTurn(
                apiKey = "fake-key",
                systemInstruction = "test",
                history = listOf("user" to "hi"),
                functionDeclarations = JSONArray()
            )
            fail("expected AIProviderException")
        } catch (e: GeminiProvider.AIProviderException) {
            assertTrue(e.message?.isNotBlank() == true)
        }
    }

    // --- Server-side errors (rate limit, invalid key, quota) ---

    @Test
    fun sendTurn_httpErrorWithStructuredMessage_surfacesServerReason() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":{"message":"Resource has been exhausted (e.g. check quota)."}}""")
        )

        try {
            provider.sendTurn(
                apiKey = "fake-key",
                systemInstruction = "test",
                history = listOf("user" to "hi"),
                functionDeclarations = JSONArray()
            )
            fail("expected AIProviderException")
        } catch (e: GeminiProvider.AIProviderException) {
            assertTrue(e.message?.contains("quota") == true || e.message?.contains("exhausted") == true)
        }
    }

    @Test
    fun sendTurn_httpErrorWithoutStructuredBody_fallsBackToStatusCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("not json at all"))

        try {
            provider.sendTurn(
                apiKey = "fake-key",
                systemInstruction = "test",
                history = listOf("user" to "hi"),
                functionDeclarations = JSONArray()
            )
            fail("expected AIProviderException")
        } catch (e: GeminiProvider.AIProviderException) {
            // Even with an unparseable body, the failure must still surface
            // as a clean AIProviderException with *some* explanation
            // (the numeric status code), never a raw JSONException escaping.
            assertTrue(e.message?.contains("500") == true)
        }
    }

    @Test
    fun sendTurn_malformedSuccessBody_throwsAIProviderExceptionInsteadOfCrashing() = runTest {
        // HTTP 200 but a body that isn't valid JSON -- a class of bug where
        // the network "succeeded" but the response can't be trusted.
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>not json</html>"))

        try {
            provider.sendTurn(
                apiKey = "fake-key",
                systemInstruction = "test",
                history = listOf("user" to "hi"),
                functionDeclarations = JSONArray()
            )
            fail("expected AIProviderException")
        } catch (e: GeminiProvider.AIProviderException) {
            assertTrue(e.message?.isNotBlank() == true)
        }
    }

    // --- Safety-filter block: a "successful" response with no candidates ---

    @Test
    fun parseTurnResult_blockedBySafetyFilter_returnsExplanatoryTextNotCrash() {
        val body = """{"promptFeedback":{"blockReason":"SAFETY"}}"""

        val result = provider.parseTurnResult(body)

        assertTrue(result.text.isNotBlank())
        assertTrue(result.functionCalls.isEmpty())
    }

    @Test
    fun parseTurnResult_emptyCandidatesNoBlockReason_returnsGenericNoResponseText() {
        val body = """{"candidates":[]}"""

        val result = provider.parseTurnResult(body)

        assertTrue(result.text.isNotBlank())
    }

    // --- Happy path sanity check: confirms the harness itself is wired correctly ---

    @Test
    fun sendTurn_successfulResponse_parsesTextAndFunctionCalls() = runTest {
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "در حال بررسی هستم"},
                      {"functionCall": {"name": "get_today_tasks", "args": {}}}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = provider.sendTurn(
            apiKey = "fake-key",
            systemInstruction = "test",
            history = listOf("user" to "کارهای امروزم چیه؟"),
            functionDeclarations = JSONArray()
        )

        assertEquals("در حال بررسی هستم", result.text)
        assertEquals(1, result.functionCalls.size)
        assertEquals("get_today_tasks", result.functionCalls.first().name)

        val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)
        assertTrue(recordedRequest?.path?.contains("key=fake-key") == true)
    }

    // --- parseErrorMessage edge cases ---

    @Test
    fun parseErrorMessage_missingErrorField_returnsNull() {
        assertEquals(null, provider.parseErrorMessage("""{"foo":"bar"}"""))
    }

    @Test
    fun parseErrorMessage_invalidJson_returnsNullInsteadOfThrowing() {
        assertEquals(null, provider.parseErrorMessage("not json"))
    }
}
