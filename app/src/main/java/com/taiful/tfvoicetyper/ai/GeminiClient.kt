package com.taiful.tfvoicetyper.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeminiException(val userMessage: String, val retryable: Boolean = false, val code: Int = 0) : Exception(userMessage)

data class Transcript(val bangla: String, val english: String)

/**
 * Talks to Google Gemini directly with the user's own key.
 * The key goes in a request header (never in the URL) and is never logged.
 * Later a private server (proxy) can be put in front simply by changing [baseUrl].
 */
class GeminiClient(private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta") {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /** Lists models that can turn audio into text. */
    suspend fun listModels(key: String): List<String> {
        val req = Request.Builder()
            .url("$baseUrl/models?pageSize=200")
            .header("x-goog-api-key", key)
            .get().build()
        val body = execute(req)
        val arr = JSONObject(body).optJSONArray("models") ?: JSONArray()
        val out = mutableListOf<String>()
        val skip = listOf("tts", "image", "embedding", "live", "native-audio", "veo", "imagen", "lyria", "robotics", "aqa", "learnlm", "gemma", "computer-use", "omni")
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val name = m.optString("name").removePrefix("models/")
            val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
            if (!name.startsWith("gemini")) continue
            if (!methods.contains("generateContent")) continue
            if (skip.any { name.contains(it) }) continue
            out += name
        }
        return out.distinct().sortedWith(compareByDescending<String> { it.contains("latest") }.thenByDescending { it })
    }

    /** Small request to check the key and model. Returns a short success message. */
    suspend fun test(key: String, model: String): String {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", "Reply with just: OK")))))
        val text = extractText(JSONObject(generate(key, model, body, fallback = false)))
        return if (text.isNotBlank()) "সংযোগ ঠিক আছে ✓ ($model)" else "সংযোগ হয়েছে, তবে উত্তর খালি এসেছে ($model)"
    }

    suspend fun transcribe(
        key: String,
        model: String,
        audio: ByteArray,
        mime: String,
        part: Int,
        parts: Int,
        previousTail: String,
        translate: Boolean,
        patient: Boolean = true,
    ): Transcript {
        val b64 = withContext(Dispatchers.Default) { Base64.encodeToString(audio, Base64.NO_WRAP) }
        val schema = JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject()
                .put("bangla", JSONObject().put("type", "STRING"))
                .put("english", JSONObject().put("type", "STRING")))
            .put("required", JSONArray().put("bangla").put("english"))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", Prompt.SYSTEM))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray()
                .put(JSONObject().put("inlineData", JSONObject().put("mimeType", mime).put("data", b64)))
                .put(JSONObject().put("text", Prompt.userText(part, parts, previousTail, translate))))))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json")
                .put("responseSchema", schema))

        val resp = JSONObject(generate(key, model, body, fallback = true, patient = patient))
        resp.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let {
            throw GeminiException("Gemini এই অডিওটি প্রসেস করতে রাজি হয়নি ($it)।")
        }
        val cand = resp.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw GeminiException("Gemini কোনো উত্তর দেয়নি। আবার চেষ্টা করুন।", retryable = true)
        val finish = cand.optString("finishReason")
        val text = extractText(resp)
        if (text.isBlank()) {
            when (finish) {
                "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII" ->
                    throw GeminiException("Gemini নিরাপত্তার কারণে এই অংশের লেখা দেয়নি।")
                "RECITATION" -> throw GeminiException("Gemini এই অংশের লেখা দেয়নি (কপিরাইট সতর্কতা)।")
                else -> throw GeminiException("Gemini খালি উত্তর দিয়েছে। আবার চেষ্টা করুন।", retryable = true)
            }
        }
        val parsed = parseTranscript(text)
        if (finish == "MAX_TOKENS") {
            throw GeminiException("রেকর্ডিংটি এই মডেলের জন্য খুব বড় ছিল। Settings-এ অন্য মডেল বেছে আবার চেষ্টা করুন।", retryable = false)
        }
        return parsed
    }

    /**
     * Text only (no audio): fixes the raw text from Google voice typing — spelling, দাঁড়ি-কমা — and translates.
     * Much smaller than sending audio, so it is fast and rarely "busy". Never waits long: the caller keeps the
     * raw text if this fails.
     */
    suspend fun polishText(key: String, model: String, raw: String, translate: Boolean): Transcript {
        val schema = JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject()
                .put("bangla", JSONObject().put("type", "STRING"))
                .put("english", JSONObject().put("type", "STRING")))
            .put("required", JSONArray().put("bangla").put("english"))
        val user = buildString {
            append("RAW TEXT:\n«")
            append(raw)
            append("»")
            if (!translate) append("\nEnglish translation is turned off: set \"english\" to an empty string.")
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", Prompt.TEXT_SYSTEM))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json")
                .put("responseSchema", schema))
        val text = extractText(JSONObject(generate(key, model, body, fallback = true, patient = false)))
        val t = parseTranscript(text)
        // Safety: if the answer is empty or suspiciously short, keep the user's own words.
        if (t.bangla.isBlank() || t.bangla.length < raw.length / 3) throw GeminiException("polish-empty", retryable = true)
        return t
    }

    private fun parseTranscript(raw: String): Transcript {
        var t = raw.trim()
        if (t.startsWith("```")) t = t.substringAfter('\n').substringBeforeLast("```").trim()
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start >= 0 && end > start) {
            try {
                val o = JSONObject(t.substring(start, end + 1))
                return Transcript(o.optString("bangla").trim(), o.optString("english").trim())
            } catch (_: Exception) { }
        }
        // Not JSON: treat the whole answer as the Bangla text
        return Transcript(t, "")
    }

    private fun extractText(resp: JSONObject): String {
        val parts = resp.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (p.optBoolean("thought", false)) continue
            sb.append(p.optString("text"))
        }
        return sb.toString()
    }

    /**
     * generateContent with automatic retry. If the chosen model stays busy (5xx) or its free limit is
     * used up (429), it quietly tries other Flash models, so the user still gets the text.
     */
    private suspend fun generate(key: String, model: String, body: JSONObject, fallback: Boolean = true, patient: Boolean = true): String {
        val payload = body.toString()
        val models = (listOf(model.trim()) + if (fallback) FALLBACK_MODELS else emptyList()).filter { it.isNotBlank() }.distinct()
        var firstError: GeminiException? = null
        for ((i, m) in models.withIndex()) {
            var attempt = 0
            while (true) {
                val req = Request.Builder()
                    .url("$baseUrl/models/$m:generateContent")
                    .header("x-goog-api-key", key)
                    .post(payload.toRequestBody(json))
                    .build()
                try {
                    return execute(req)
                } catch (e: GeminiException) {
                    if (i == 0 && firstError == null) firstError = e
                    val busy = e.code >= 500 || e.code == 429
                    val missing = e.code == 404 && fallback        // model not offered to this key → try the next one
                    if (missing) break
                    if (!e.retryable) throw (if (i == 0) e else firstError ?: e)
                    attempt++
                    val maxTries = if (e.code == 429 || !patient) 1 else 2      // 429 = per-model quota → move on quickly
                    if (attempt >= maxTries) {
                        if (busy && fallback) break else throw (firstError ?: e)
                    }
                    if (patient) delay(if (attempt == 1) 3000L else 7000L) else delay(800L)
                }
            }
        }
        throw firstError ?: GeminiException("Gemini সার্ভার এখন ব্যস্ত। একটু পরে আবার চেষ্টা করুন।", retryable = true, code = 503)
    }

    private suspend fun execute(req: Request): String {
        val resp = try {
            http.newCall(req).await()
        } catch (e: IOException) {
            throw GeminiException("ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না বা খুব ধীর। নেট চেক করে আবার চেষ্টা করুন।", retryable = true)
        }
        resp.use { r ->
            val text = withContext(Dispatchers.IO) { r.body?.string().orEmpty() }
            if (r.isSuccessful) return text
            val apiMsg = try {
                JSONObject(text).optJSONObject("error")?.optString("message").orEmpty()
            } catch (_: Exception) { "" }
            throw when {
                r.code == 400 && (apiMsg.contains("API key", true) || apiMsg.contains("API_KEY", true)) ->
                    GeminiException("API key সঠিক নয়। Settings-এ সঠিক Gemini API key দিন।")
                r.code == 401 || r.code == 403 ->
                    GeminiException("এই API key দিয়ে অনুমতি নেই। key ঠিক আছে কিনা Settings-এ দেখুন।")
                r.code == 404 ->
                    GeminiException(code = 404, userMessage = "মডেল \"${req.url.encodedPath.substringAfterLast('/').substringBefore(':')}\" পাওয়া যায়নি। Settings-এ অন্য মডেল বেছে নিন।")
                r.code == 413 -> GeminiException("অডিওটি খুব বড়। ছোট করে রেকর্ড করুন।")
                r.code == 429 ->
                    GeminiException("Gemini-এর ফ্রি ব্যবহার-সীমা (limit) শেষ বা খুব দ্রুত অনুরোধ। একটু পরে আবার চেষ্টা করুন।", retryable = true, code = 429)
                r.code >= 500 ->
                    GeminiException("Gemini সার্ভার এখন ব্যস্ত (${r.code})। সব মডেল চেষ্টা করা হয়েছে — কয়েক মিনিট পরে আবার চেষ্টা করুন।" +
                        if (apiMsg.isNotBlank()) "\n(${apiMsg.take(120)})" else "", retryable = true, code = r.code)
                else -> GeminiException("Gemini ত্রুটি (${r.code})${if (apiMsg.isNotBlank()) ": " + apiMsg.take(160) else ""}")
            }
        }
    }

    companion object {
        /** Tried in this order when the chosen model is busy. Missing ones are skipped. */
        val FALLBACK_MODELS = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest", "gemini-2.5-flash-lite")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}
