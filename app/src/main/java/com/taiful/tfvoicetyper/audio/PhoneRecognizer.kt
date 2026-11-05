package com.taiful.tfvoicetyper.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * No-key mode: the phone's own Google voice typing (free, no limit).
 * Android stops listening after a pause, so we quietly start it again until the user taps stop.
 * Everything here runs on the main thread (Android requires it).
 */
class PhoneRecognizer(private val context: Context, private val onLevel: (Float) -> Unit) {

    companion object {
        fun available(ctx: Context) = SpeechRecognizer.isRecognitionAvailable(ctx)
    }

    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private val finals = StringBuilder()
    private var partial = ""
    @Volatile private var active = false
    @Volatile var failure: String? = null
        private set
    private var netErrors = 0
    private var finished: CompletableDeferred<Unit>? = null
    private val startedAt = System.currentTimeMillis()
    val durationMs: Long get() = System.currentTimeMillis() - startedAt

    /** Text so far (shown live). */
    val text: String get() = (finals.toString() + (if (partial.isNotBlank()) " $partial" else "")).trim()

    fun start() {
        val r = SpeechRecognizer.createSpeechRecognizer(context) ?: throw IllegalStateException("no recognizer")
        sr = r
        r.setRecognitionListener(listener)
        active = true
        listen()
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
    }

    private fun listen() {
        if (!active) return
        runCatching { sr?.startListening(intent()) }.onFailure { main.postDelayed({ listen() }, 400) }
    }

    private fun addFinal(b: Bundle?) {
        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (t.isNotBlank()) { if (finals.isNotEmpty()) finals.append(' '); finals.append(t) }
        partial = ""
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(b: Bundle?) {
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
        }
        override fun onResults(b: Bundle?) {
            addFinal(b)
            netErrors = 0
            if (active) listen() else finish()
        }
        override fun onError(error: Int) {
            if (!active) { finish(); return }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listen()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    runCatching { sr?.cancel() }
                    main.postDelayed({ listen() }, 350)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    failure = "মাইক্রোফোনের অনুমতি দেওয়া নেই।"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    netErrors++
                    if (netErrors >= 3) failure = "ইন্টারনেট সংযোগ নেই বা খুব ধীর — ফোনের ভয়েস টাইপিং কাজ করছে না। নেট ছাড়া চালাতে Settings থেকে \"অফলাইন বাংলা ভয়েস\" ডাউনলোড করে নিন।"
                    else main.postDelayed({ listen() }, 800)
                }
                12, 13 -> failure = "এই ফোনের Google ভয়েস টাইপিংয়ে বাংলা নেই। Settings-এ গিয়ে \"অফলাইন বাংলা ভয়েস\" ডাউনলোড করুন (key লাগে না), অথবা ফ্রি Gemini/Groq key দিন।"
                else -> main.postDelayed({ listen() }, 400)
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
        runCatching { sr?.destroy() }
        sr = null
    }
}
