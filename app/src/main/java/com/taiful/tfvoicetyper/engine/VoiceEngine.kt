package com.taiful.tfvoicetyper.engine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.taiful.tfvoicetyper.TFApp
import com.taiful.tfvoicetyper.ai.GeminiException
import com.taiful.tfvoicetyper.audio.AacRecorder
import com.taiful.tfvoicetyper.audio.partsIn
import com.taiful.tfvoicetyper.data.db.Record
import com.taiful.tfvoicetyper.util.Clip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Source { APP, BUBBLE }

sealed interface EngineState {
    data object Idle : EngineState
    data class Recording(val elapsedMs: Long, val level: Float, val source: Source) : EngineState
    data class Processing(val recordId: Long, val part: Int, val parts: Int, val source: Source) : EngineState
    data class Done(val record: Record, val source: Source) : EngineState
    data class Error(val message: String, val recordId: Long?, val source: Source) : EngineState
}

/**
 * The heart of the app: record → send to Gemini → save to history.
 * Shared by the Home screen and the floating microphone.
 */
class VoiceEngine(private val app: TFApp) {
    private val dao get() = app.db.records()
    private val scope get() = app.appScope
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state

    private var recorder: AacRecorder? = null
    private var recordId: CompletableDeferred<Long>? = null
    private var ticker: Job? = null
    private var processJob: Job? = null
    @Volatile private var level = 0f

    val isBusy: Boolean
        get() = _state.value is EngineState.Recording || _state.value is EngineState.Processing

    private val audioRoot: File get() = File(app.filesDir, "audio")

    fun toggle(source: Source) {
        when (_state.value) {
            is EngineState.Recording -> stop()
            is EngineState.Processing -> Unit
            else -> start(source)
        }
    }

    fun start(source: Source) {
        if (isBusy) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = EngineState.Error("মাইক্রোফোনের অনুমতি দেওয়া নেই। অ্যাপ খুলে অনুমতি দিন।", null, source)
            return
        }
        val now = System.currentTimeMillis()
        val dir = File(audioRoot, "rec_$now")
        val rec = AacRecorder(dir) { level = it }
        try {
            rec.start()
        } catch (e: Exception) {
            dir.deleteRecursively()
            _state.value = EngineState.Error(
                "মাইক্রোফোন চালু করা যায়নি। অন্য কোনো অ্যাপ (কল, ভিডিও, রেকর্ডার) মাইক ব্যবহার করছে কিনা দেখুন।", null, source
            )
            return
        }
        recorder = rec
        val idDef = CompletableDeferred<Long>()
        recordId = idDef
        scope.launch {
            val id = dao.insert(Record(createdAt = now, audioDir = dir.absolutePath, status = Record.STATUS_RECORDING))
            idDef.complete(id)
        }
        level = 0f
        _state.value = EngineState.Recording(0, 0f, source)
        ticker = scope.launch {
            while (isActive) {
                val r = recorder ?: break
                if (r.failure != null) {
                    main.post { stop() }
                    break
                }
                _state.value = EngineState.Recording(r.durationMs, level, source)
                delay(100)
            }
        }
    }

    /** Stops recording and starts creating the text. */
    fun stop() {
        val rec = recorder ?: return
        val src = (_state.value as? EngineState.Recording)?.source ?: Source.APP
        recorder = null
        ticker?.cancel()
        val idDef = recordId ?: return
        _state.value = EngineState.Processing(-1, 0, 0, src)
        scope.launch {
            val files = withContext(Dispatchers.IO) { rec.stop() }
            val id = idDef.await()
            val dur = rec.durationMs
            val r = dao.get(id) ?: return@launch
            if (files.isEmpty() || dur < 800) {
                deleteRecords(listOf(id))
                _state.value = EngineState.Error(
                    if (rec.failure != null) "মাইক্রোফোন থেকে আওয়াজ পাওয়া যায়নি। আবার চেষ্টা করুন।"
                    else "রেকর্ডিং খুব ছোট ছিল। মাইকে চাপ দিয়ে কথা বলুন, তারপর আবার চাপুন।",
                    null, src
                )
                return@launch
            }
            dao.update(r.copy(durationMs = dur, status = Record.STATUS_PROCESSING, updatedAt = System.currentTimeMillis()))
            process(id, src)
        }
    }

    /** Stops and throws the recording away. */
    fun cancel() {
        val rec = recorder ?: return
        recorder = null
        ticker?.cancel()
        val idDef = recordId
        _state.value = EngineState.Idle
        scope.launch {
            withContext(Dispatchers.IO) { rec.stop() }
            idDef?.await()?.let { deleteRecords(listOf(it)) }
        }
    }

    fun retry(id: Long, source: Source = Source.APP) {
        if (isBusy) return
        _state.value = EngineState.Processing(id, 0, 0, source)
        scope.launch {
            val r = dao.get(id) ?: run { _state.value = EngineState.Idle; return@launch }
            dao.update(r.copy(status = Record.STATUS_PROCESSING, error = null, updatedAt = System.currentTimeMillis()))
            process(id, source)
        }
    }

    fun dismiss() {
        if (_state.value is EngineState.Done || _state.value is EngineState.Error) _state.value = EngineState.Idle
    }

    private fun process(id: Long, source: Source) {
        processJob = scope.launch {
            val rec = dao.get(id) ?: return@launch
            try {
                val files = if (rec.audioDir.isNotEmpty()) partsIn(File(rec.audioDir)) else emptyList()
                if (files.isEmpty()) throw GeminiException("এই রেকর্ডিংয়ের অডিও ফাইল আর নেই, তাই আবার প্রসেস করা যাবে না।")
                val key = app.settings.apiKey()
                    ?: throw GeminiException("Gemini API key দেওয়া নেই। Settings-এ গিয়ে API key দিন, তারপর History থেকে আবার চেষ্টা করুন।")
                val s = app.settings.current()
                val bn = StringBuilder()
                val en = StringBuilder()
                files.forEachIndexed { i, f ->
                    _state.value = EngineState.Processing(id, i + 1, files.size, source)
                    val bytes = withContext(Dispatchers.IO) { f.readBytes() }
                    val t = app.gemini.transcribe(
                        key = key, model = s.model, audio = bytes, mime = AacRecorder.MIME,
                        part = i + 1, parts = files.size, previousTail = bn.takeLast(300).toString(),
                        translate = s.translate,
                    )
                    if (t.bangla.isNotBlank()) { if (bn.isNotEmpty()) bn.append(' '); bn.append(t.bangla) }
                    if (t.english.isNotBlank()) { if (en.isNotEmpty()) en.append(' '); en.append(t.english) }
                }
                if (bn.isBlank()) throw GeminiException("অডিওতে কোনো কথা শোনা যায়নি। মাইকের কাছে স্পষ্ট করে বলুন।")
                var done = rec.copy(
                    bangla = bn.toString().trim(), english = en.toString().trim(),
                    status = Record.STATUS_DONE, error = null, model = s.model, updatedAt = System.currentTimeMillis(),
                )
                if (s.audioRetentionDays == 0) {
                    withContext(Dispatchers.IO) { File(rec.audioDir).deleteRecursively() }
                    done = done.copy(audioDir = "")
                }
                dao.update(done)
                _state.value = EngineState.Done(done, source)
                if (source == Source.BUBBLE && s.autoCopy) {
                    main.post { Clip.copy(app, done.bangla, "বাংলা লেখা") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiException) {
                fail(rec, e.userMessage, source)
            } catch (e: Exception) {
                fail(rec, "লেখা তৈরি করা যায়নি: ${e.message ?: e.javaClass.simpleName}", source)
            }
        }
    }

    private suspend fun fail(rec: Record, msg: String, source: Source) {
        dao.update(rec.copy(status = Record.STATUS_FAILED, error = msg, updatedAt = System.currentTimeMillis()))
        _state.value = EngineState.Error(msg, rec.id, source)
    }

    suspend fun saveText(id: Long, bangla: String, english: String) {
        val r = dao.get(id) ?: return
        val upd = r.copy(bangla = bangla, english = english, updatedAt = System.currentTimeMillis())
        dao.update(upd)
        val st = _state.value
        if (st is EngineState.Done && st.record.id == id) _state.value = st.copy(record = upd)
    }

    suspend fun deleteRecords(ids: List<Long>) {
        if (ids.isEmpty()) return
        val list = dao.getMany(ids)
        withContext(Dispatchers.IO) {
            list.forEach { if (it.audioDir.isNotEmpty()) File(it.audioDir).deleteRecursively() }
        }
        dao.delete(ids)
        val st = _state.value
        if (st is EngineState.Done && st.record.id in ids) _state.value = EngineState.Idle
        if (st is EngineState.Error && st.recordId != null && st.recordId in ids) _state.value = EngineState.Idle
    }

    suspend fun deleteAll() {
        if (isBusy) {
            val ids = dao.everything().filter { it.status == Record.STATUS_DONE || it.status == Record.STATUS_FAILED }.map { it.id }
            deleteRecords(ids)
        } else {
            withContext(Dispatchers.IO) { audioRoot.deleteRecursively() }
            dao.deleteAll()
            _state.value = EngineState.Idle
        }
    }

    /** After a crash or phone restart: unfinished items become "failed" so they can be retried (audio is kept). */
    suspend fun recoverInterrupted() {
        if (isBusy) return
        dao.byStatus(listOf(Record.STATUS_RECORDING, Record.STATUS_PROCESSING)).forEach { r ->
            val parts = if (r.audioDir.isNotEmpty()) partsIn(File(r.audioDir)) else emptyList()
            if (parts.isEmpty()) {
                deleteRecords(listOf(r.id))
            } else {
                val dur = if (r.durationMs > 0) r.durationMs else parts.sumOf { it.length() } * 1000 / 4100
                dao.update(r.copy(
                    status = Record.STATUS_FAILED, durationMs = dur,
                    error = "অ্যাপ হঠাৎ বন্ধ হয়ে যাওয়ায় কাজ শেষ হয়নি। অডিও সংরক্ষিত আছে — \"আবার চেষ্টা\" চাপুন।",
                ))
            }
        }
    }

    /** Deletes old audio according to Settings (text in history is always kept). */
    suspend fun cleanupAudio() {
        val days = app.settings.current().audioRetentionDays
        if (days < 0) return
        val limit = System.currentTimeMillis() - days * 24L * 3600 * 1000
        dao.withAudio().filter { it.status == Record.STATUS_DONE && (days == 0 || it.createdAt < limit) }.forEach {
            withContext(Dispatchers.IO) { File(it.audioDir).deleteRecursively() }
            dao.update(it.copy(audioDir = ""))
        }
    }
}
