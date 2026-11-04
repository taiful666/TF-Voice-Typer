package com.taiful.tfvoicetyper.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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

/**
 * Free backup engine: Groq (free plan, no card).
 * Step 1 — Whisper turns the Bangla speech into raw text.
 * Step 2 — a free Groq AI model fixes spelling/punctuation and translates to English.
 * The key goes only in the Authorization header, is never logged and never leaves the phone otherwise.
 */
class GroqClient(private val baseUrl: String = "https://api.groq.com/openai/v1") {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    companion object {
        val WHISPER_MODELS = listOf("whisper-large-v3", "whisper-large-v3-turbo")
        val TEXT_MODELS = listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "openai/gpt-oss-20b", "llama-3.1-8b-instant")
        private const val CHUNK_CHARS = 1400
    }

    /** Quick check that the key works. */
    suspend fun test(key: String): String {
        val req = Request.Builder().url("$baseUrl/models").header("Authorization", "Bearer $key").get().build()
        val body = execute(req)
        val ids = JSONObject(body).optJSONArray("data") ?: JSONArray()
        var hasWhisper = false
        for (i in 0 until ids.length()) if (ids.getJSONObject(i).optString("id").startsWith("whisper")) hasWhisper = true
        return if (hasWhisper) "Groq সংযোগ ঠিক আছে ✓" else "Groq সংযোগ হয়েছে, কিন্তু Whisper মডেল পাওয়া যায়নি"
    }

    /** Full job for one audio part (m4a bytes). */
    suspend fun transcribe(key: String, m4a: ByteArray, previousTail: String, translate: Boolean): Transcript {
        val raw = speechToText(key, m4a, previousTail).trim()
        if (raw.isBlank()) return Transcript("", "")
        return try {
            polish(key, raw, translate)
        } catch (e: GeminiException) {
            if (!e.retryable && e.code == 401) throw e
            Transcript(raw, "") // Whisper text is still useful; English is filled by the phone translator later
        }
    }

    private suspend fun speechToText(key: String, m4a: ByteArray, previousTail: String): String {
        var firstError: GeminiException? = null
        models@ for (model in WHISPER_MODELS) {
            for (attempt in 0..1) {
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", "audio.m4a", m4a.toRequestBody("audio/mp4".toMediaType()))
                    .addFormDataPart("model", model)
                    .addFormDataPart("language", "bn")
                    .addFormDataPart("temperature", "0")
                    .addFormDataPart("response_format", "json")
                    .addFormDataPart("prompt", previousTail.takeLast(200).ifBlank { "এটি একটি বাংলা কথা। দাঁড়ি, কমা সহ শুদ্ধ বাংলায় লেখা।" })
                    .build()
                val req = Request.Builder().url("$baseUrl/audio/transcriptions")
                    .header("Authorization", "Bearer $key").post(form).build()
                try {
                    return JSONObject(execute(req)).optString("text")
                } catch (e: GeminiException) {
                    if (firstError == null) firstError = e
                    if (!e.retryable) throw e
                    if (e.code == 429 || e.code == 404) continue@models // try the next model
                    if (attempt == 0) delay(2500L)
                }
            }
        }
        throw firstError ?: GeminiException("Groq এখন সাড়া দিচ্ছে না।", retryable = true, code = 503)
    }

    /** Fix the raw text and translate, in small chunks so the free limit is never exceeded. */
    private suspend fun polish(key: String, raw: String, translate: Boolean): Transcript {
        val bn = StringBuilder()
        val en = StringBuilder()
        for (chunk in chunks(raw)) {
            val t = polishChunk(key, chunk, translate, bn.takeLast(200).toString())
            if (bn.isNotEmpty()) bn.append(' ')
            bn.append(t.bangla.ifBlank { chunk })
            if (t.english.isNotBlank()) { if (en.isNotEmpty()) en.append(' '); en.append(t.english) }
        }
        return Transcript(bn.toString().trim(), en.toString().trim())
    }

    private fun chunks(text: String): List<String> {
        if (text.length <= CHUNK_CHARS) return listOf(text)
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (piece in text.split(Regex("(?<=[।?!.])\\s+"))) {
            if (cur.length + piece.length > CHUNK_CHARS && cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(piece)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private suspend fun polishChunk(key: String, chunk: String, translate: Boolean, before: String): Transcript {
        val user = buildString {
            append("Raw speech-recognition text (Bangla, may contain mis-heard words and no punctuation):\n«")
            append(chunk)
            append("»")
            if (before.isNotBlank()) append("\nThe text just before this (context only, do NOT repeat): «").append(before).append("»")
            if (!translate) append("\nEnglish translation is turned off: set \"english\" to an empty string.")
            append("\nReturn the json now.")
        }
        var firstError: GeminiException? = null
        for (model in TEXT_MODELS) {
            val body = JSONObject()
                .put("model", model)
                .put("temperature", 0.2)
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", Prompt.TEXT_SYSTEM))
                    .put(JSONObject().put("role", "user").put("content", user)))
            if (model.startsWith("openai/gpt-oss")) body.put("reasoning_effort", "low")
            val req = Request.Builder().url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody(json)).build()
            try {
                val resp = JSONObject(execute(req))
                val content = resp.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
                val o = parseJson(content) ?: continue
                val b = o.optString("bangla").trim()
                if (b.isBlank()) continue
                return Transcript(b, o.optString("english").trim())
            } catch (e: GeminiException) {
                if (firstError == null) firstError = e
                if (e.code == 401) throw e
                if (e.code == 429) delay(1500L)
            }
        }
        throw firstError ?: GeminiException("Groq লেখা ঠিক করতে পারেনি।", retryable = true, code = 503)
    }

    private fun parseJson(raw: String): JSONObject? {
        val t = raw.trim()
        val s = t.indexOf('{'); val e = t.lastIndexOf('}')
        if (s < 0 || e <= s) return null
        return runCatching { JSONObject(t.substring(s, e + 1)) }.getOrNull()
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
            val apiMsg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
            throw when {
                r.code == 401 -> GeminiException("Groq API key সঠিক নয়। Settings-এ সঠিক Groq key দিন।", code = 401)
                r.code == 403 -> GeminiException("এই Groq key দিয়ে অনুমতি নেই।", code = 403)
                r.code == 404 -> GeminiException("Groq মডেল পাওয়া যায়নি।", retryable = true, code = 404)
                r.code == 413 -> GeminiException("অডিওটি Groq-এর জন্য খুব বড়।", code = 413)
                r.code == 429 -> GeminiException("Groq-এর ফ্রি ব্যবহার-সীমা এই মুহূর্তে শেষ। একটু পরে আবার চেষ্টা করুন।", retryable = true, code = 429)
                r.code >= 500 -> GeminiException("Groq সার্ভার এখন ব্যস্ত (${r.code})।", retryable = true, code = r.code)
                else -> GeminiException("Groq ত্রুটি (${r.code})${if (apiMsg.isNotBlank()) ": " + apiMsg.take(160) else ""}", code = r.code)
            }
        }
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
