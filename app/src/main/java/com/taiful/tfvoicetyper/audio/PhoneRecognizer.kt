package com.taiful.tfvoicetyper.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Free Google voice typing — the same Google speech technology that websites like speechtexter.com use
 * inside Chrome. No API key, no limit, very good Bangla.
 *
 * - Picks the Google app's online recogniser first (it needs NO Bangla language pack). If a phone's
 *   recogniser says "Bangla not available", it quietly tries the next language tag (bn-BD → bn-IN)
 *   and then the next recogniser on the phone.
 * - Continuous: Android stops listening after a pause, so we start it again at once, until the user
 *   taps stop. The user never has to tap again in between.
 * - Words that were heard but not "finalised" when a session ended are kept, so nothing is lost.
 *
 * Everything here runs on the main thread (Android requires it).
 */
class PhoneRecognizer(
    private val context: Context,
    private val preferOffline: Boolean = false,
    private val onLevel: (Float) -> Unit,
) {

    companion object {
        private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        private const val GOOGLE_SPEECH = "com.google.android.tts"
        private val LANGS = listOf("bn-BD", "bn-IN")

        fun available(ctx: Context) = SpeechRecognizer.isRecognitionAvailable(ctx) || services(ctx).isNotEmpty()

        /** Speech services installed on the phone, best first (Google app → Google Speech Services → others). */
        fun services(ctx: Context): List<ComponentName> = try {
            @Suppress("DEPRECATION")
            ctx.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .mapNotNull { it.serviceInfo?.let { s -> ComponentName(s.packageName, s.name) } }
                .distinct()
                .sortedBy { when (it.packageName) { GOOGLE_APP -> 0; GOOGLE_SPEECH -> 1; else -> 2 } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private val finals = StringBuilder()
    private var partial = ""
    @Volatile private var active = false
    @Volatile var failure: String? = null
        private set

    /** Every (recogniser, language) pair we may try. A null recogniser = the phone's default. */
    private val candidates: List<Pair<ComponentName?, String>> =
        (services(context).map<ComponentName, ComponentName?> { it } + listOf(null)).distinct()
            .flatMap { c -> LANGS.map { l -> c to l } }
    private var cand = 0
    private var netErrors = 0
    private var quickFails = 0
    private var hardErrors = 0
    private var listenAt = 0L
    private var speechBegan = false
    private var gotSomething = false
    private var finished: CompletableDeferred<Unit>? = null
    private val startedAt = System.currentTimeMillis()
    val durationMs: Long get() = System.currentTimeMillis() - startedAt

    /** Text so far (shown live while speaking). */
    val text: String get() = (finals.toString() + (if (partial.isNotBlank()) " $partial" else "")).trim()

    fun start() {
        active = true
        create()
        listen()
    }

    private fun create() {
        runCatching { sr?.destroy() }
        val comp = candidates.getOrNull(cand)?.first
        val r = (if (comp != null) runCatching { SpeechRecognizer.createSpeechRecognizer(context, comp) }.getOrNull() else null)
            ?: SpeechRecognizer.createSpeechRecognizer(context)
            ?: throw IllegalStateException("no recognizer")
        r.setRecognitionListener(listener)
        sr = r
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        val lang = candidates.getOrNull(cand)?.second ?: LANGS[0]
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // Ask for long pauses before stopping (some phones honour it; if not, we simply restart).
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
    }

    private fun listen() {
        if (!active || failure != null) return
        speechBegan = false
        listenAt = SystemClock.elapsedRealtime()
        runCatching { sr?.startListening(intent()) }.onFailure {
            hardErrors++
            if (hardErrors > 8) { failure = "ফোনের ভয়েস টাইপিং চালু হচ্ছে না। আবার চেষ্টা করুন।"; return }
            runCatching { create() }
            main.postDelayed({ listen() }, 400)
        }
    }

    /** Move to the next recogniser / language. Returns false when there is nothing left to try. */
    private fun nextCandidate(): Boolean {
        if (gotSomething || cand + 1 >= candidates.size) return false
        cand++
        quickFails = 0
        runCatching { create() }.onFailure { return nextCandidate() }
        main.postDelayed({ listen() }, 250)
        return true
    }

    private fun keepPartial() {
        val p = partial.trim()
        if (p.isNotBlank()) { if (finals.isNotEmpty()) finals.append(' '); finals.append(p) }
        partial = ""
    }

    private fun addFinal(b: Bundle?) {
        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (t.isNotBlank()) {
            if (finals.isNotEmpty()) finals.append(' ')
            finals.append(t)
            partial = ""
            gotSomething = true
        } else {
            keepPartial()
        }
    }

    private fun noBangla() {
        failure = if (preferOffline)
            "এখন নেট নেই, আর ফোনের Google ভয়েস টাইপিংয়ে অফলাইন বাংলা নামানো নেই। নেট চালু করুন — অথবা Settings থেকে \"অফলাইন বাংলা ভয়েস\" একবার ডাউনলোড করে রাখুন।"
        else
            "এই ফোনের ভয়েস টাইপিংয়ে বাংলা পাওয়া যায়নি। Play Store থেকে \"Google\" অ্যাপ ইনস্টল/আপডেট করুন — অথবা Settings থেকে \"অফলাইন বাংলা ভয়েস\" ডাউনলোড করুন।"
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { speechBegan = true }
        override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(b: Bundle?) {
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                if (it.isNotBlank()) { partial = it; gotSomething = true }
            }
        }
        override fun onResults(b: Bundle?) {
            addFinal(b)
            netErrors = 0; quickFails = 0; hardErrors = 0
            if (active) listen() else finish()   // start again at once → no need to tap again
        }
        override fun onError(error: Int) {
            keepPartial()
            if (!active) { finish(); return }
            val quick = SystemClock.elapsedRealtime() - listenAt < 700 && !speechBegan
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Some phones answer "no match" instantly when they cannot do Bangla at all.
                    if (quick && !gotSomething) {
                        quickFails++
                        if (quickFails >= 3) { if (!nextCandidate()) noBangla(); return }
                    } else quickFails = 0
                    main.postDelayed({ listen() }, if (quick) 300L else 60L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    hardErrors++
                    if (hardErrors > 8) { if (!nextCandidate()) failure = "ফোনের ভয়েস টাইপিং সাড়া দিচ্ছে না। আবার চেষ্টা করুন।"; return }
                    runCatching { sr?.cancel() }
                    if (hardErrors >= 3) runCatching { create() }
                    main.postDelayed({ listen() }, 350)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    failure = "মাইক্রোফোনের অনুমতি দেওয়া নেই।"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    netErrors++
                    if (netErrors >= 3) failure = "ইন্টারনেট সংযোগ নেই বা খুব ধীর। নেট ছাড়া চালাতে Settings থেকে \"অফলাইন বাংলা ভয়েস\" ডাউনলোড করে নিন।"
                    else main.postDelayed({ listen() }, 800)
                }
                12, 13 -> if (!nextCandidate()) noBangla()   // language not supported / not downloaded
                else -> {
                    hardErrors++
                    if (hardErrors > 8) { failure = "ফোনের ভয়েস টাইপিং বন্ধ হয়ে গেছে। আবার চেষ্টা করুন।"; return }
                    main.postDelayed({ listen() }, 400)
                }
            }
        }
    }

    private fun finish() { finished?.complete(Unit) }

    /** Stops and returns all the text heard. */
    suspend fun stop(): String {
        if (!active) { destroy(); return text }
        active = false
        val f = CompletableDeferred<Unit>()
        finished = f
        runCatching { sr?.stopListening() }
        withTimeoutOrNull(3000) { f.await() }
        keepPartial()
        val out = text
        destroy()
        return out
    }

    fun cancel() {
        active = false
        runCatching { sr?.cancel() }
        destroy()
    }

    private fun destroy() {
        main.removeCallbacksAndMessages(null)
        runCatching { sr?.destroy() }
        sr = null
    }
}
